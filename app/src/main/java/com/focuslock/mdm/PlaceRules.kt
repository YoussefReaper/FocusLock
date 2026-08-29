package com.focuslock.mdm

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Whether a place rule fires when you are there, or when you are anywhere else. */
enum class PlaceTrigger(val id: String, val label: String) {
    INSIDE("inside", "When I am here"),
    OUTSIDE("outside", "When I am anywhere else");

    companion object {
        fun fromId(id: String?): PlaceTrigger = values().firstOrNull { it.id == id } ?: INSIDE
    }
}

/**
 * A place, defined either by coordinates or by the Wi-Fi network it has.
 *
 * Wi-Fi is the cheaper and more reliable signal indoors, and it does not need
 * background location, so a place can be created from network alone.
 */
data class Place(
    val id: String,
    val label: String,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Int,
    val wifiSsid: String,
    val trigger: PlaceTrigger,
    val blockedCategories: Set<AppCategory>,
    val blockedPackages: Set<String>,
    val enabled: Boolean
) {
    val hasCoordinates: Boolean get() = latitude != 0.0 || longitude != 0.0
    val hasWifi: Boolean get() = wifiSsid.isNotBlank()
}

/**
 * Rules that follow you around.
 *
 * Location is checked from the last known fix only: FocusLock never asks for a
 * continuous location stream, because a focus app that tracks you all day is a
 * worse trade than the feature is worth.
 */
object PlaceRules {

    private const val KEY_PLACES = "place_rules_json"
    private const val LOCATION_MAX_AGE_MS = 15L * 60 * 1000

    fun all(context: Context): List<Place> {
        val array = FocusStore.getJsonArray(context, KEY_PLACES)
        val out = ArrayList<Place>(array.length())
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val categories = FocusStore.jsonArrayToStringList(
                obj.optJSONArray("categories") ?: JSONArray()
            ).map { AppCategory.fromId(it) }.toSet()
            out.add(
                Place(
                    id = obj.optString("id", UUID.randomUUID().toString()),
                    label = obj.optString("label", "Place"),
                    latitude = obj.optDouble("lat", 0.0),
                    longitude = obj.optDouble("lon", 0.0),
                    radiusMeters = obj.optInt("radius", 150),
                    wifiSsid = obj.optString("ssid", ""),
                    trigger = PlaceTrigger.fromId(obj.optString("trigger", "")),
                    blockedCategories = categories,
                    blockedPackages = FocusStore.jsonArrayToStringList(
                        obj.optJSONArray("packages") ?: JSONArray()
                    ).toSet(),
                    enabled = obj.optBoolean("enabled", true)
                )
            )
        }
        return out
    }

    fun save(context: Context, places: List<Place>) {
        val array = JSONArray()
        places.forEach { place ->
            val obj = JSONObject()
            obj.put("id", place.id)
            obj.put("label", place.label)
            obj.put("lat", place.latitude)
            obj.put("lon", place.longitude)
            obj.put("radius", place.radiusMeters)
            obj.put("ssid", place.wifiSsid)
            obj.put("trigger", place.trigger.id)
            obj.put("categories", FocusStore.stringListToJsonArray(place.blockedCategories.map { it.id }))
            obj.put("packages", FocusStore.stringListToJsonArray(place.blockedPackages))
            obj.put("enabled", place.enabled)
            array.put(obj)
        }
        FocusStore.setJsonArray(context, KEY_PLACES, array)
        PolicySync.request(context, "places")
    }

    fun add(context: Context, place: Place) = save(context, all(context) + place)

    fun update(context: Context, place: Place) =
        save(context, all(context).map { if (it.id == place.id) place else it })

    fun remove(context: Context, id: String) =
        save(context, all(context).filterNot { it.id == id })

    fun newPlace(
        label: String,
        latitude: Double = 0.0,
        longitude: Double = 0.0,
        radiusMeters: Int = 150,
        wifiSsid: String = "",
        trigger: PlaceTrigger = PlaceTrigger.INSIDE,
        blockedCategories: Set<AppCategory> = setOf(AppCategory.SOCIAL, AppCategory.VIDEO),
        blockedPackages: Set<String> = emptySet()
    ): Place = Place(
        id = UUID.randomUUID().toString(),
        label = label.ifBlank { "Place" },
        latitude = latitude,
        longitude = longitude,
        radiusMeters = radiusMeters.coerceIn(50, 5_000),
        wifiSsid = wifiSsid.trim(),
        trigger = trigger,
        blockedCategories = blockedCategories,
        blockedPackages = blockedPackages,
        enabled = true
    )

    // ── Evaluation ────────────────────────────────────────────────

    /** The places whose condition is satisfied right now. */
    fun activePlaces(context: Context): List<Place> {
        val locationOn = CapabilityRegistry.isEnabled(context, Capabilities.LOCATION_BLOCK)
        val wifiOn = CapabilityRegistry.isEnabled(context, Capabilities.WIFI_CONDITIONS)
        if (!locationOn && !wifiOn) return emptyList()

        val here = if (locationOn) lastKnownLocation(context) else null
        val ssid = if (wifiOn) currentWifiSsid(context) else null

        return all(context).filter { place ->
            if (!place.enabled) return@filter false
            val present = isPresent(place, here, ssid, locationOn, wifiOn) ?: return@filter false
            when (place.trigger) {
                PlaceTrigger.INSIDE -> present
                PlaceTrigger.OUTSIDE -> !present
            }
        }
    }

    /** Null when we genuinely cannot tell, so an unknown place never blocks. */
    private fun isPresent(
        place: Place,
        here: Location?,
        ssid: String?,
        locationOn: Boolean,
        wifiOn: Boolean
    ): Boolean? {
        if (wifiOn && place.hasWifi && ssid != null) {
            if (ssid.equals(place.wifiSsid, ignoreCase = true)) return true
            if (!place.hasCoordinates) return false
        }
        if (locationOn && place.hasCoordinates && here != null) {
            val target = Location("focuslock")
            target.latitude = place.latitude
            target.longitude = place.longitude
            return here.distanceTo(target) <= place.radiusMeters.toFloat()
        }
        return null
    }

    fun blocks(context: Context, packageName: String): Place? {
        if (AppRules.isAlwaysAllowed(context, packageName)) return null
        val category = AppCatalog.categoryOf(context, packageName)
        return activePlaces(context).firstOrNull { place ->
            packageName in place.blockedPackages || category in place.blockedCategories
        }
    }

    // ── Signals ───────────────────────────────────────────────────

    fun hasLocationPermission(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    fun lastKnownLocation(context: Context): Location? {
        if (!hasLocationPermission(context)) return null
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        val now = System.currentTimeMillis()
        return try {
            manager.getProviders(true)
                .mapNotNull { provider ->
                    try {
                        manager.getLastKnownLocation(provider)
                    } catch (_: SecurityException) {
                        null
                    }
                }
                .filter { now - it.time <= LOCATION_MAX_AGE_MS }
                .maxByOrNull { it.time }
        } catch (_: Exception) {
            null
        }
    }

    @Suppress("DEPRECATION")
    fun currentWifiSsid(context: Context): String? {
        val connectivity = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val network = connectivity?.activeNetwork ?: return null
        val capabilities = connectivity.getNetworkCapabilities(network) ?: return null
        if (!capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return null

        val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            ?: return null
        val info = try {
            wifi.connectionInfo
        } catch (_: Exception) {
            null
        } ?: return null

        val raw = info.ssid ?: return null
        val cleaned = raw.trim().removePrefix("\"").removeSuffix("\"")
        if (cleaned.isBlank() || cleaned == "<unknown ssid>") return null
        return cleaned
    }

    fun isOnWifi(context: Context): Boolean = currentWifiSsid(context) != null

    fun exportJson(context: Context): JSONArray = FocusStore.getJsonArray(context, KEY_PLACES)

    fun importJson(context: Context, array: JSONArray) {
        FocusStore.setJsonArray(context, KEY_PLACES, array)
        PolicySync.request(context, "places:import")
    }
}
