package com.focuslock.mdm

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class PersonalizationActivity : AppCompatActivity() {

    private lateinit var root: View
    private lateinit var content: View

    private lateinit var spinnerTheme: Spinner
    private lateinit var spinnerFont: Spinner
    private lateinit var spinnerDensity: Spinner
    private lateinit var spinnerWallpaper: Spinner
    private lateinit var spinnerAccent: Spinner
    private lateinit var spinnerBackground: Spinner

    private lateinit var seekCardRadius: SeekBar
    private lateinit var seekTextScale: SeekBar
    private lateinit var tvCardRadiusValue: TextView
    private lateinit var tvTextScaleValue: TextView

    private lateinit var switchShowKiosk: Switch
    private lateinit var switchShowQuick: Switch
    private lateinit var switchShowAllowedApps: Switch
    private lateinit var switchShowWebButton: Switch
    private lateinit var switchShowVideo: Switch
    private lateinit var switchShowEditButtons: Switch
    private lateinit var switchShowSchedule: Switch

    private lateinit var btnReset: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_personalization)

        root = findViewById(R.id.personalizeRoot)
        content = findViewById(R.id.personalizeContent)

        spinnerTheme = findViewById(R.id.spinnerTheme)
        spinnerFont = findViewById(R.id.spinnerFont)
        spinnerDensity = findViewById(R.id.spinnerDensity)
        spinnerWallpaper = findViewById(R.id.spinnerWallpaper)
        spinnerAccent = findViewById(R.id.spinnerAccent)
        spinnerBackground = findViewById(R.id.spinnerBackground)

        seekCardRadius = findViewById(R.id.seekCardRadius)
        seekTextScale = findViewById(R.id.seekTextScale)
        tvCardRadiusValue = findViewById(R.id.tvCardRadiusValue)
        tvTextScaleValue = findViewById(R.id.tvTextScaleValue)

        switchShowKiosk = findViewById(R.id.switchShowKiosk)
        switchShowQuick = findViewById(R.id.switchShowQuick)
        switchShowAllowedApps = findViewById(R.id.switchShowAllowedApps)
        switchShowWebButton = findViewById(R.id.switchShowWebButton)
        switchShowVideo = findViewById(R.id.switchShowVideo)
        switchShowEditButtons = findViewById(R.id.switchShowEditButtons)
        switchShowSchedule = findViewById(R.id.switchShowSchedule)

        btnReset = findViewById(R.id.btnResetPersonalization)

        bindSpinners()
        bindSwitches()
        bindSliders()
        bindReset()
    }

    override fun onResume() {
        super.onResume()
        syncSelectionState()
        applyPersonalizationTheme()
    }

    private fun bindSpinners() {
        val themeLabels = UiPrefs.themes.map { it.label }
        spinnerTheme.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, themeLabels)

        val fontLabels = UiPrefs.fonts.map { it.label }
        spinnerFont.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, fontLabels)

        val densityLabels = UiPrefs.densities.map { it.label }
        spinnerDensity.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, densityLabels)

        val wallpaperLabels = UiPrefs.wallpapers.map { it.label }
        spinnerWallpaper.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, wallpaperLabels)

        val accentLabels = UiPrefs.accents.map { it.label }
        spinnerAccent.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, accentLabels)

        val backgroundLabels = UiPrefs.backgrounds.map { it.label }
        spinnerBackground.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, backgroundLabels)

        spinnerTheme.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selected = UiPrefs.themes[position]
                UiPrefs.setThemeId(this@PersonalizationActivity, selected.id)
                applyPersonalizationTheme()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }

        spinnerFont.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selected = UiPrefs.fonts[position]
                UiPrefs.setFontId(this@PersonalizationActivity, selected.id)
                applyPersonalizationTheme()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }

        spinnerDensity.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selected = UiPrefs.densities[position]
                UiPrefs.setDensityId(this@PersonalizationActivity, selected.id)
                applyPersonalizationTheme()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }

        spinnerWallpaper.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selected = UiPrefs.wallpapers[position]
                UiPrefs.setWallpaperId(this@PersonalizationActivity, selected.id)
                applyPersonalizationTheme()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }

        spinnerAccent.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selected = UiPrefs.accents[position]
                UiPrefs.setAccentId(this@PersonalizationActivity, selected.id)
                applyPersonalizationTheme()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }

        spinnerBackground.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selected = UiPrefs.backgrounds[position]
                UiPrefs.setBackgroundId(this@PersonalizationActivity, selected.id)
                applyPersonalizationTheme()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
    }

    private fun bindSliders() {
        seekCardRadius.max = 32
        seekCardRadius.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                UiPrefs.setCardRadiusDp(this@PersonalizationActivity, progress)
                tvCardRadiusValue.text = "${progress}dp"
                applyPersonalizationTheme()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        seekTextScale.max = 40
        seekTextScale.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val scale = 0.8f + (progress / 100f)
                UiPrefs.setTextScale(this@PersonalizationActivity, scale)
                tvTextScaleValue.text = "${(scale * 100).toInt()}%"
                applyPersonalizationTheme()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun bindSwitches() {
        switchShowKiosk.setOnCheckedChangeListener { _, isChecked ->
            UiPrefs.setShowKiosk(this, isChecked)
        }
        switchShowQuick.setOnCheckedChangeListener { _, isChecked ->
            UiPrefs.setShowQuickSettings(this, isChecked)
        }
        switchShowAllowedApps.setOnCheckedChangeListener { _, isChecked ->
            UiPrefs.setShowAllowedApps(this, isChecked)
        }
        switchShowWebButton.setOnCheckedChangeListener { _, isChecked ->
            UiPrefs.setShowWebButton(this, isChecked)
        }
        switchShowVideo.setOnCheckedChangeListener { _, isChecked ->
            UiPrefs.setShowVideoButton(this, isChecked)
        }
        switchShowEditButtons.setOnCheckedChangeListener { _, isChecked ->
            UiPrefs.setShowEditButtons(this, isChecked)
        }
        switchShowSchedule.setOnCheckedChangeListener { _, isChecked ->
            UiPrefs.setShowSchedule(this, isChecked)
        }
    }

    private fun bindReset() {
        btnReset.setOnClickListener {
            UiPrefs.resetToDefaults(this)
            syncSelectionState()
            applyPersonalizationTheme()
        }
    }

    private fun syncSelectionState() {
        val themeIndex = UiPrefs.themes.indexOf(UiPrefs.getTheme(this))
        val fontIndex = UiPrefs.fonts.indexOf(UiPrefs.getFont(this))
        val densityIndex = UiPrefs.densities.indexOf(UiPrefs.getDensity(this))
        val wallpaperIndex = UiPrefs.wallpapers.indexOf(UiPrefs.getWallpaper(this))
        val accentIndex = UiPrefs.accents.indexOf(UiPrefs.getAccent(this))
        val backgroundIndex = UiPrefs.backgrounds.indexOf(UiPrefs.getBackground(this))

        spinnerTheme.setSelection(themeIndex.coerceAtLeast(0))
        spinnerFont.setSelection(fontIndex.coerceAtLeast(0))
        spinnerDensity.setSelection(densityIndex.coerceAtLeast(0))
        spinnerWallpaper.setSelection(wallpaperIndex.coerceAtLeast(0))
        spinnerAccent.setSelection(accentIndex.coerceAtLeast(0))
        spinnerBackground.setSelection(backgroundIndex.coerceAtLeast(0))

        val radius = UiPrefs.getCardRadiusDp(this).coerceIn(0, 32)
        seekCardRadius.progress = radius
        tvCardRadiusValue.text = "${radius}dp"

        val scale = UiPrefs.getTextScale(this).coerceIn(0.8f, 1.2f)
        val scaleProgress = ((scale - 0.8f) * 100).toInt().coerceIn(0, 40)
        seekTextScale.progress = scaleProgress
        tvTextScaleValue.text = "${(scale * 100).toInt()}%"

        switchShowKiosk.isChecked = UiPrefs.showKiosk(this)
        switchShowQuick.isChecked = UiPrefs.showQuickSettings(this)
        switchShowAllowedApps.isChecked = UiPrefs.showAllowedApps(this)
        switchShowWebButton.isChecked = UiPrefs.showWebButton(this)
        switchShowVideo.isChecked = UiPrefs.showVideoButton(this)
        switchShowEditButtons.isChecked = UiPrefs.showEditButtons(this)
        switchShowSchedule.isChecked = UiPrefs.showSchedule(this)
    }

    private fun applyPersonalizationTheme() {
        val theme = UiPrefs.getTheme(this)
        val font = UiPrefs.getFont(this)
        val density = UiPrefs.getDensity(this)
        val wallpaper = UiPrefs.getWallpaper(this)
        val accent = UiPrefs.getAccent(this).color
        val background = UiPrefs.getBackground(this).color
        val radiusDp = UiPrefs.getCardRadiusDp(this)
        val textScale = UiPrefs.getTextScale(this)

        val effectiveTheme = if (background == android.graphics.Color.TRANSPARENT) {
            theme.copy(accent = accent)
        } else {
            theme.copy(accent = accent, background = background)
        }

        UiStyler.applyWallpaperOrColor(root, effectiveTheme, wallpaper)
        UiStyler.applyTypefaceRecursive(root, font.typeface)

        val padding = UiStyler.dpToPx(this, density.contentPaddingDp)
        content.setPadding(padding, padding, padding, padding)

        window.statusBarColor = effectiveTheme.background
        window.navigationBarColor = effectiveTheme.background

        val primaryIds = listOf(
            R.id.tvPersonalizeTitle,
            R.id.switchShowKiosk,
            R.id.switchShowQuick,
            R.id.switchShowAllowedApps,
            R.id.switchShowWebButton,
            R.id.switchShowVideo,
            R.id.switchShowEditButtons,
            R.id.switchShowSchedule,
            R.id.tvCardRadiusValue,
            R.id.tvTextScaleValue
        )
        primaryIds.forEach { id ->
            findViewById<TextView>(id).setTextColor(effectiveTheme.textPrimary)
        }

        val secondaryIds = listOf(
            R.id.tvPersonalizeSubtitle,
            R.id.tvThemeLabel,
            R.id.tvFontLabel,
            R.id.tvDensityLabel,
            R.id.tvWallpaperLabel,
            R.id.tvAccentLabel,
            R.id.tvBackgroundLabel,
            R.id.tvCardRadiusLabel,
            R.id.tvTextScaleLabel,
            R.id.tvDashboardLabel
        )
        secondaryIds.forEach { id ->
            findViewById<TextView>(id).setTextColor(effectiveTheme.textSecondary)
        }

        listOf(spinnerTheme, spinnerFont, spinnerDensity, spinnerWallpaper, spinnerAccent, spinnerBackground)
            .forEach { spinner ->
                spinner.setBackgroundColor(effectiveTheme.card)
        }

        btnReset.backgroundTintList = ColorStateList.valueOf(effectiveTheme.card)
        btnReset.setTextColor(effectiveTheme.textPrimary)

        content.scaleX = textScale
        content.scaleY = textScale

        UiStyler.applyCardRadius(btnReset, UiStyler.dpToPx(this, radiusDp).toFloat())
    }
}
