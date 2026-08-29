package com.focuslock.mdm

import android.view.View
import android.widget.LinearLayout

/**
 * Places and networks.
 *
 * "Social off at school, maps always on" is the classic version of this, and it
 * works because context does most of the work that willpower otherwise has to.
 *
 * Two deliberate limits: location is only ever read from the last known fix, so
 * FocusLock never runs a location stream, and a place whose condition cannot be
 * determined blocks nothing. Uncertainty must never turn into a lockout.
 */
class PlaceRulesActivity : FocusScreenActivity() {

    override fun screenTitle(): String = "Places and networks"

    override fun screenSubtitle(): String =
        "Rules that follow where you are, or which Wi-Fi you are on."

    override fun buildContent(column: LinearLayout) {
        column.addView(buildToggles())
        column.addView(sectionLabel("Saved places"))
        column.addView(buildPlaceList())
        column.addView(sectionLabel("Right now"))
        column.addView(buildStatusCard())
    }

    private fun buildToggles(): View = card { card ->
        card.addView(
            FocusUi.toggleRow(
                this,
                tokens,
                "Place rules",
                "Uses your last known location. FocusLock never tracks you continuously.",
                CapabilityRegistry.isEnabled(this, Capabilities.LOCATION_BLOCK)
            ) { value ->
                CapabilityRegistry.setEnabled(this, Capabilities.LOCATION_BLOCK, value)
                if (value && !PlaceRules.hasLocationPermission(this)) requestLocation()
                refresh()
            }
        )
        card.addView(FocusUi.divider(this, tokens))
        card.addView(
            FocusUi.toggleRow(
                this,
                tokens,
                "Network rules",
                "Uses the Wi-Fi name. Works indoors where location does not, and needs no tracking.",
                CapabilityRegistry.isEnabled(this, Capabilities.WIFI_CONDITIONS)
            ) { value ->
                CapabilityRegistry.setEnabled(this, Capabilities.WIFI_CONDITIONS, value)
                refresh()
            }
        )

        if (CapabilityRegistry.isEnabled(this, Capabilities.LOCATION_BLOCK) &&
            !PlaceRules.hasLocationPermission(this)
        ) {
            card.addView(FocusUi.spacer(this, 10))
            card.addView(
                FocusUi.primaryButton(this, tokens, "Grant location permission") { requestLocation() }
            )
        }
    }

    private fun requestLocation() {
        requestPermissions(
            arrayOf(
                android.Manifest.permission.ACCESS_FINE_LOCATION,
                android.Manifest.permission.ACCESS_COARSE_LOCATION
            ),
            REQUEST_LOCATION
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_LOCATION) refresh()
    }

    private fun buildPlaceList(): View = card { card ->
        val places = PlaceRules.all(this)
        val active = PlaceRules.activePlaces(this).map { it.id }.toSet()

        if (places.isEmpty()) {
            card.addView(
                FocusUi.emptyState(
                    this,
                    tokens,
                    "No places yet. The easiest first one is wherever you are meant to be working."
                )
            )
        } else {
            places.forEachIndexed { index, place ->
                card.addView(
                    FocusUi.listRow(
                        this,
                        tokens,
                        place.label,
                        describePlace(place),
                        trailing = if (place.id in active) {
                            FocusUi.pill(this, tokens, "Active", tokens.accent)
                        } else {
                            FocusUi.chevron(this, tokens)
                        }
                    ) { editPlace(place) }
                )
                if (index < places.size - 1) card.addView(FocusUi.divider(this, tokens))
            }
        }

        card.addView(FocusUi.spacer(this, 12))
        card.addView(FocusUi.primaryButton(this, tokens, "Add where I am now") { addHere() })
        card.addView(FocusUi.spacer(this, 8))
        card.addView(FocusUi.secondaryButton(this, tokens, "Add this Wi-Fi network") { addWifi() })
    }

    private fun describePlace(place: Place): String {
        val where = when {
            place.hasWifi && place.hasCoordinates ->
                "Wi-Fi " + place.wifiSsid + " or within " + place.radiusMeters + "m"
            place.hasWifi -> "Wi-Fi " + place.wifiSsid
            place.hasCoordinates -> "Within " + place.radiusMeters + "m"
            else -> "Nothing to match on yet"
        }
        val what = if (place.blockedCategories.isEmpty() && place.blockedPackages.isEmpty()) {
            "nothing blocked"
        } else {
            place.blockedCategories.size.toString() + " categories, " +
                place.blockedPackages.size + " apps"
        }
        val trigger = if (place.trigger == PlaceTrigger.INSIDE) "here" else "away"
        return where + " - blocks " + what + " when " + trigger
    }

    private fun addHere() {
        if (!PlaceRules.hasLocationPermission(this)) {
            requestLocation()
            return
        }
        val here = PlaceRules.lastKnownLocation(this)
        if (here == null) {
            FocusDialog.info(
                this,
                "No recent location",
                "Android has no recent fix to work from. Open a maps app for a moment, then try again."
            )
            return
        }
        FocusDialog.textInput(
            this,
            title = "Name this place",
            subtitle = "School, library, the gym: whatever you will recognise later.",
            hint = "Name"
        ) { name ->
            if (name.isBlank()) return@textInput
            val place = PlaceRules.newPlace(
                label = name,
                latitude = here.latitude,
                longitude = here.longitude,
                wifiSsid = PlaceRules.currentWifiSsid(this).orEmpty()
            )
            PlaceRules.add(this, place)
            refresh()
            editPlace(place)
        }
    }

    private fun addWifi() {
        val ssid = PlaceRules.currentWifiSsid(this)
        if (ssid == null) {
            FocusDialog.info(
                this,
                "Not on Wi-Fi",
                "Connect to the network you want to use as a condition, then try again."
            )
            return
        }
        FocusDialog.textInput(
            this,
            title = "Name this network",
            subtitle = "Connected to " + ssid,
            hint = "Name",
            value = ssid
        ) { name ->
            if (name.isBlank()) return@textInput
            val place = PlaceRules.newPlace(label = name, wifiSsid = ssid)
            PlaceRules.add(this, place)
            refresh()
            editPlace(place)
        }
    }

    private fun editPlace(place: Place) {
        var working = place

        FocusDialog.custom(
            this,
            title = place.label,
            subtitle = describePlace(place),
            confirmLabel = "Save",
            cancelLabel = "Cancel",
            onConfirm = {
                PlaceRules.update(this, working)
                refresh()
            }
        ) { body, dialogTokens ->
            body.addView(FocusUi.caption(this, dialogTokens, "WHEN IT APPLIES"))
            PlaceTrigger.values().forEach { trigger ->
                val marker = FocusUi.pill(
                    this,
                    dialogTokens,
                    if (trigger == working.trigger) "Now" else "Set",
                    if (trigger == working.trigger) dialogTokens.accent else dialogTokens.textMuted
                )
                body.addView(
                    FocusUi.listRow(this, dialogTokens, trigger.label, null, trailing = marker) {
                        working = working.copy(trigger = trigger)
                        PlaceRules.update(this, working)
                    }
                )
            }

            body.addView(FocusUi.divider(this, dialogTokens, 8))
            body.addView(FocusUi.caption(this, dialogTokens, "WHAT IT BLOCKS"))
            AppCategory.ruleTargets.forEach { category ->
                body.addView(
                    FocusUi.toggleRow(
                        this,
                        dialogTokens,
                        category.label,
                        null,
                        category in working.blockedCategories
                    ) { checked ->
                        val next = if (checked) {
                            working.blockedCategories + category
                        } else {
                            working.blockedCategories - category
                        }
                        working = working.copy(blockedCategories = next)
                        PlaceRules.update(this, working)
                    }
                )
            }

            body.addView(
                FocusUi.listRow(
                    this,
                    dialogTokens,
                    "Specific apps",
                    working.blockedPackages.size.toString() + " chosen",
                    trailing = FocusUi.chevron(this, dialogTokens)
                ) {
                    pickApps(
                        title = "Block at " + working.label,
                        subtitle = null,
                        selected = working.blockedPackages
                    ) { selected ->
                        working = working.copy(blockedPackages = selected)
                        PlaceRules.update(this, working)
                    }
                }
            )

            if (working.hasCoordinates) {
                body.addView(
                    FocusUi.sliderRow(
                        this,
                        dialogTokens,
                        "How close counts as here",
                        50,
                        1_000,
                        working.radiusMeters,
                        { it.toString() + "m" }
                    ) { value ->
                        working = working.copy(radiusMeters = value)
                    }
                )
            }

            body.addView(FocusUi.divider(this, dialogTokens, 8))
            body.addView(
                FocusUi.dangerButton(this, dialogTokens, "Delete this place") {
                    PlaceRules.remove(this, working.id)
                    refresh()
                }
            )
        }
    }

    private fun buildStatusCard(): View = card { card ->
        val ssid = PlaceRules.currentWifiSsid(this)
        card.addView(
            FocusUi.listRow(
                this,
                tokens,
                "Wi-Fi",
                ssid ?: "Not connected to Wi-Fi"
            )
        )
        val here = PlaceRules.lastKnownLocation(this)
        card.addView(
            FocusUi.listRow(
                this,
                tokens,
                "Location",
                when {
                    !PlaceRules.hasLocationPermission(this) -> "Permission not granted"
                    here == null -> "No recent fix from Android"
                    else -> "Known to about " + here.accuracy.toInt() + "m"
                }
            )
        )
        val active = PlaceRules.activePlaces(this)
        card.addView(
            FocusUi.listRow(
                this,
                tokens,
                "Matching places",
                if (active.isEmpty()) "None right now" else active.joinToString { it.label }
            )
        )
    }

    companion object {
        private const val REQUEST_LOCATION = 4712
    }
}
