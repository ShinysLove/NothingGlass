package com.nothing.glass.ui

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.nothing.glass.R
import com.nothing.glass.config.GlassConfig
import com.nothing.glass.config.GlassConfigProvider
import com.nothing.glass.databinding.ActivityMainBinding
import java.io.DataOutputStream
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: SharedPreferences

    override fun attachBaseContext(newBase: Context) {
        val language = newBase.getSharedPreferences("nothing_glass_ui", Context.MODE_PRIVATE)
            .getString("app_language", "en") ?: "en"
        val config = android.content.res.Configuration(newBase.resources.configuration)
        config.setLocale(Locale.forLanguageTag(language))
        super.attachBaseContext(newBase.createConfigurationContext(config))
    }

    private fun setupLanguagePicker() {
        binding.btnLanguage.setOnClickListener {
            val uiState = getSharedPreferences("nothing_glass_ui", Context.MODE_PRIVATE)
            val current = if (uiState.getString("app_language", "en") == "ru") 1 else 0
            com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle(R.string.language_title)
                .setSingleChoiceItems(arrayOf("English", "Русский"), current) { dialog, which ->
                    val language = if (which == 1) "ru" else "en"
                    uiState.edit().putString("app_language", language).apply()
                    dialog.dismiss()
                    if (which != current) recreate()
                }.show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = getSharedPreferences(GlassConfig.PREFS_NAME, Context.MODE_PRIVATE)

        setupDeviceInfo()
        loadPreferences()
        setupListeners()
        setupCollapsibleSections()
        setupLanguagePicker()

        // Ensure config is written to disk with proper permissions on first launch
        if (!prefs.contains(GlassConfig.KEY_ENABLED)) {
            saveAndBroadcastConfig()
        }
    }

    private fun setupCollapsibleSections() {
        val uiState = getSharedPreferences("nothing_glass_ui", Context.MODE_PRIVATE)
        val sections = listOf(
            Triple(R.id.sectionAHeader, R.id.sectionABody, R.id.sectionAChevron),
            Triple(R.id.sectionBHeader, R.id.sectionBBody, R.id.sectionBChevron),
            Triple(R.id.sectionCHeader, R.id.sectionCBody, R.id.sectionCChevron),
            Triple(R.id.sectionDHeader, R.id.sectionDBody, R.id.sectionDChevron),
            Triple(R.id.sectionEHeader, R.id.sectionEBody, R.id.sectionEChevron),
            Triple(R.id.sectionFHeader, R.id.sectionFBody, R.id.sectionFChevron),
            Triple(R.id.sectionGHeader, R.id.sectionGBody, R.id.sectionGChevron),
            Triple(R.id.sectionIHeader, R.id.sectionIBody, R.id.sectionIChevron),
            Triple(R.id.sectionHHeader, R.id.sectionHBody, R.id.sectionHChevron),
            Triple(R.id.sectionActionsHeader, R.id.sectionActionsBody, R.id.sectionActionsChevron),
            Triple(R.id.sectionDeviceHeader, R.id.sectionDeviceBody, R.id.sectionDeviceChevron)
        )
        sections.forEach { (headerId, bodyId, chevronId) ->
            val header = findViewById<android.view.View>(headerId)
            val body = findViewById<android.view.View>(bodyId)
            val chevron = findViewById<android.view.View>(chevronId)
            val key = resources.getResourceEntryName(headerId)
            fun show(expanded: Boolean, animate: Boolean) {
                if (animate) {
                    android.transition.TransitionManager.beginDelayedTransition(
                        body.parent as android.view.ViewGroup,
                        android.transition.AutoTransition().apply { duration = 180 }
                    )
                }
                body.visibility = if (expanded) android.view.View.VISIBLE else android.view.View.GONE
                if (animate) chevron.animate().rotation(if (expanded) 180f else 0f).setDuration(180).start()
                else chevron.rotation = if (expanded) 180f else 0f
                androidx.core.view.ViewCompat.setStateDescription(header, getString(if (expanded) R.string.expanded else R.string.collapsed))
            }
            show(uiState.getBoolean(key, false), false)
            header.setOnClickListener {
                val expanded = body.visibility != android.view.View.VISIBLE
                show(expanded, true)
                uiState.edit().putBoolean(key, expanded).apply()
            }
        }
    }

    private fun setupDeviceInfo() {
        val model = Build.MODEL
        val device = Build.DEVICE
        val display = Build.DISPLAY
        val osVersion = Build.VERSION.RELEASE
        binding.tvDeviceInfo.text = getString(R.string.device_details, model, device, osVersion, display)
    }

    private fun loadPreferences() {
        // A. General
        val enabled = prefs.getBoolean(GlassConfig.KEY_ENABLED, GlassConfig.DEFAULT_ENABLED)
        val safeMode = prefs.getBoolean(GlassConfig.KEY_SAFE_MODE, GlassConfig.DEFAULT_SAFE_MODE)
        val debugLogging = prefs.getBoolean(GlassConfig.KEY_DEBUG_LOGGING, GlassConfig.DEFAULT_DEBUG_LOGGING)
        val debugOverlay = prefs.getBoolean(GlassConfig.KEY_DEBUG_OVERLAY_BOUNDS, GlassConfig.DEFAULT_DEBUG_OVERLAY_BOUNDS)

        binding.switchMaster.isChecked = enabled
        binding.switchSafeMode.isChecked = safeMode
        binding.switchDebugLogging.isChecked = debugLogging
        binding.switchDebugOverlayBounds.isChecked = debugOverlay

        // B. Main Shade
        val blurRadius = prefs.getInt(GlassConfig.KEY_BLUR_RADIUS, GlassConfig.DEFAULT_BLUR_RADIUS)
        val shadeOpacity = prefs.getInt(GlassConfig.KEY_SHADE_OPACITY, GlassConfig.DEFAULT_SHADE_OPACITY)
        val qsBgTintOpacity = prefs.getInt(GlassConfig.KEY_QS_BACKGROUND_TINT_OPACITY, GlassConfig.DEFAULT_QS_BACKGROUND_TINT_OPACITY)
        val qsExpandedOpacity = prefs.getInt(GlassConfig.KEY_QS_EXPANDED_ALPHA, GlassConfig.DEFAULT_QS_EXPANDED_ALPHA)
        val qsCollapsedOpacity = prefs.getInt(GlassConfig.KEY_QS_COLLAPSED_ALPHA, GlassConfig.DEFAULT_QS_COLLAPSED_ALPHA)
        val animationEnabled = prefs.getBoolean(GlassConfig.KEY_ANIMATION_ENABLED, GlassConfig.DEFAULT_ANIMATION_ENABLED)
        val tintHex = prefs.getString(GlassConfig.KEY_TINT_HEX, GlassConfig.DEFAULT_TINT_HEX) ?: GlassConfig.DEFAULT_TINT_HEX

        binding.sliderBlurRadius.value = blurRadius.toFloat().coerceIn(0f, 100f)
        binding.tvBlurRadiusVal.text = "$blurRadius px"

        binding.sliderShadeOpacity.value = shadeOpacity.toFloat().coerceIn(0f, 100f)
        binding.tvShadeOpacityVal.text = "$shadeOpacity%"

        binding.sliderQsBackgroundTintOpacity.value = qsBgTintOpacity.toFloat().coerceIn(10f, 100f)
        binding.tvQsBackgroundTintOpacityVal.text = "$qsBgTintOpacity%"

        binding.sliderQsExpandedOpacity.value = qsExpandedOpacity.toFloat().coerceIn(0f, 100f)
        binding.tvQsExpandedOpacityVal.text = "$qsExpandedOpacity%"

        binding.sliderQsCollapsedOpacity.value = qsCollapsedOpacity.toFloat().coerceIn(0f, 100f)
        binding.tvQsCollapsedOpacityVal.text = "$qsCollapsedOpacity%"

        binding.switchAnimation.isChecked = animationEnabled

        when (tintHex.uppercase()) {
            GlassConfig.WALLPAPER_MONET.uppercase() -> binding.rbWallpaperMonet.isChecked = true
            "#08080C" -> binding.rbPureDark.isChecked = true
            "#070C18" -> binding.rbMidnightNavy.isChecked = true
            "#121214" -> binding.rbDeepCharcoal.isChecked = true
            else -> binding.rbColdSlate.isChecked = true
        }

        if (prefs.getBoolean("pref_wallpaper_generated_selected", false)) binding.rbWallpaperGenerated.isChecked = true

        // C. Notification Scrim
        val notifScrimEnabled = prefs.getBoolean(GlassConfig.KEY_NOTIF_SCRIM_ENABLED, GlassConfig.DEFAULT_NOTIF_SCRIM_ENABLED)
        val notifScrimOpacity = prefs.getInt(GlassConfig.KEY_NOTIF_SCRIM_OPACITY, GlassConfig.DEFAULT_NOTIF_SCRIM_OPACITY)
        val notifScrimGradient = prefs.getBoolean(GlassConfig.KEY_NOTIF_SCRIM_GRADIENT_ENABLED, GlassConfig.DEFAULT_NOTIF_SCRIM_GRADIENT_ENABLED)
        val notifScrimTint = prefs.getString(GlassConfig.KEY_NOTIF_SCRIM_TINT, GlassConfig.DEFAULT_NOTIF_SCRIM_TINT_HEX) ?: GlassConfig.DEFAULT_NOTIF_SCRIM_TINT_HEX

        binding.switchNotifScrim.isChecked = notifScrimEnabled
        binding.sliderNotifScrimOpacity.value = notifScrimOpacity.toFloat().coerceIn(0f, 100f)
        binding.tvNotifScrimOpacityVal.text = "$notifScrimOpacity%"
        binding.switchNotifScrimGradient.isChecked = notifScrimGradient

        when (notifScrimTint.uppercase()) {
            GlassConfig.WALLPAPER_MONET.uppercase() -> binding.rbNotifWallpaperMonet.isChecked = true
            "#060A10" -> binding.rbNotifMidnight.isChecked = true
            "#000000" -> binding.rbNotifPureBlack.isChecked = true
            else -> binding.rbNotifDarkObsidian.isChecked = true
        }

        // D. Notification Cards
        val unifiedBlur = prefs.getBoolean(GlassConfig.KEY_UNIFIED_NOTIF_BACKDROP_BLUR, GlassConfig.DEFAULT_UNIFIED_NOTIF_BACKDROP_BLUR)
        val cardTintOpacity = prefs.getInt(GlassConfig.KEY_NOTIF_CARD_TINT_OPACITY, GlassConfig.DEFAULT_NOTIF_CARD_TINT_OPACITY)
        val cardCornerRadius = prefs.getInt(GlassConfig.KEY_NOTIF_CARD_CORNER_RADIUS, GlassConfig.DEFAULT_NOTIF_CARD_CORNER_RADIUS_DP)
        val notifCardBlur = prefs.getBoolean(GlassConfig.KEY_NOTIF_CARD_BLUR_ENABLED, GlassConfig.DEFAULT_NOTIF_CARD_BLUR_ENABLED)
        val notifCardBlurRadius = prefs.getInt(GlassConfig.KEY_NOTIF_CARD_BLUR_RADIUS, GlassConfig.DEFAULT_NOTIF_CARD_BLUR_RADIUS)
        val notifCardOpacity = prefs.getInt(GlassConfig.KEY_NOTIF_CARD_ALPHA, GlassConfig.DEFAULT_NOTIF_CARD_ALPHA)
        val notifBorder = prefs.getBoolean(GlassConfig.KEY_NOTIF_BORDER_ENABLED, GlassConfig.DEFAULT_NOTIF_BORDER_ENABLED)

        binding.switchUnifiedNotifBackdropBlur.isChecked = unifiedBlur
        binding.sliderNotifCardTintOpacity.value = cardTintOpacity.toFloat().coerceIn(10f, 100f)
        binding.tvNotifCardTintOpacityVal.text = "$cardTintOpacity%"

        binding.sliderNotifCornerRadius.value = cardCornerRadius.toFloat().coerceIn(12f, 40f)
        binding.tvNotifCornerRadiusVal.text = "$cardCornerRadius dp"

        binding.switchNotifCardBlur.isChecked = notifCardBlur
        binding.sliderNotifCardBlurRadius.value = notifCardBlurRadius.toFloat().coerceIn(10f, 60f)
        binding.tvNotifCardBlurRadiusVal.text = "$notifCardBlurRadius px"

        binding.sliderNotifOpacity.value = notifCardOpacity.toFloat().coerceIn(20f, 100f)
        binding.tvNotifOpacityVal.text = "$notifCardOpacity%"
        binding.switchNotifBorder.isChecked = notifBorder

        // E. Heads-Up
        val headsUpGlass = prefs.getBoolean(GlassConfig.KEY_HEADSUP_GLASS_ENABLED, GlassConfig.DEFAULT_HEADSUP_GLASS_ENABLED)
        val headsUpBlurEnabled = prefs.getBoolean(GlassConfig.KEY_HEADSUP_BLUR_ENABLED, GlassConfig.DEFAULT_HEADSUP_BLUR_ENABLED)
        val headsUpBlurRadius = prefs.getInt(GlassConfig.KEY_HEADSUP_BLUR_RADIUS, GlassConfig.DEFAULT_HEADSUP_BLUR_RADIUS)
        val headsUpOpacity = prefs.getInt(GlassConfig.KEY_HEADSUP_TINT_OPACITY, GlassConfig.DEFAULT_HEADSUP_TINT_OPACITY)

        binding.switchHeadsUpGlass.isChecked = headsUpGlass
        binding.switchHeadsUpBlur.isChecked = headsUpBlurEnabled
        binding.sliderHeadsUpBlurRadius.value = headsUpBlurRadius.toFloat().coerceIn(10f, 60f)
        binding.tvHeadsUpBlurRadiusVal.text = "$headsUpBlurRadius px"

        binding.sliderHeadsUpOpacity.value = headsUpOpacity.toFloat().coerceIn(20f, 100f)
        binding.tvHeadsUpOpacityVal.text = "$headsUpOpacity%"

        // F. QS Tiles
        val activeTileOpacity = prefs.getInt(GlassConfig.KEY_ACTIVE_TILE_ALPHA, GlassConfig.DEFAULT_ACTIVE_TILE_ALPHA)
        val inactiveTileOpacity = prefs.getInt(GlassConfig.KEY_INACTIVE_TILE_ALPHA, GlassConfig.DEFAULT_INACTIVE_TILE_ALPHA)

        binding.sliderActiveTileOpacity.value = activeTileOpacity.toFloat().coerceIn(30f, 100f)
        binding.tvActiveTileOpacityVal.text = "$activeTileOpacity%"

        binding.sliderInactiveTileOpacity.value = inactiveTileOpacity.toFloat().coerceIn(20f, 100f)
        binding.tvInactiveTileOpacityVal.text = "$inactiveTileOpacity%"

        // G. Icons & Text
        val qsIconActive = prefs.getInt(GlassConfig.KEY_QS_ICON_ACTIVE, GlassConfig.DEFAULT_QS_ICON_ACTIVE_PERCENT)
        val qsIconInactive = prefs.getInt(GlassConfig.KEY_QS_ICON_INACTIVE, GlassConfig.DEFAULT_QS_ICON_INACTIVE_PERCENT)
        val qsIconDisabled = prefs.getInt(GlassConfig.KEY_QS_ICON_DISABLED, GlassConfig.DEFAULT_QS_ICON_DISABLED_PERCENT)
        val notifIcon = prefs.getInt(GlassConfig.KEY_NOTIF_ICON, GlassConfig.DEFAULT_NOTIF_ICON_PERCENT)

        binding.sliderQsIconActive.value = qsIconActive.toFloat().coerceIn(50f, 100f)
        binding.tvQsIconActiveVal.text = String.format(Locale.US, "%.2f", qsIconActive / 100f)

        binding.sliderQsIconInactive.value = qsIconInactive.toFloat().coerceIn(30f, 100f)
        binding.tvQsIconInactiveVal.text = String.format(Locale.US, "%.2f", qsIconInactive / 100f)

        binding.sliderQsIconDisabled.value = qsIconDisabled.toFloat().coerceIn(20f, 90f)
        binding.tvQsIconDisabledVal.text = String.format(Locale.US, "%.2f", qsIconDisabled / 100f)

        binding.sliderNotifIcon.value = notifIcon.toFloat().coerceIn(40f, 100f)
        binding.tvNotifIconVal.text = String.format(Locale.US, "%.2f", notifIcon / 100f)

        // I. Volume Panel
        val volumeGlass = prefs.getBoolean(GlassConfig.KEY_VOLUME_GLASS_ENABLED, GlassConfig.DEFAULT_VOLUME_GLASS_ENABLED)
        val volumeSlider = prefs.getInt(GlassConfig.KEY_VOLUME_SLIDER_ALPHA, GlassConfig.DEFAULT_VOLUME_SLIDER_ALPHA_PERCENT)
        val volumeButtons = prefs.getInt(GlassConfig.KEY_VOLUME_BUTTONS_ALPHA, GlassConfig.DEFAULT_VOLUME_BUTTONS_ALPHA_PERCENT)
        val volumeBlur = prefs.getInt(GlassConfig.KEY_VOLUME_BLUR_RADIUS, GlassConfig.DEFAULT_VOLUME_BLUR_RADIUS)

        binding.switchVolumeGlass.isChecked = volumeGlass
        binding.sliderVolumeSliderOpacity.value = volumeSlider.toFloat().coerceIn(20f, 100f)
        binding.tvVolumeSliderOpacityVal.text = "$volumeSlider%"
        binding.sliderVolumeButtonsOpacity.value = volumeButtons.toFloat().coerceIn(20f, 100f)
        binding.tvVolumeButtonsOpacityVal.text = "$volumeButtons%"
        binding.sliderVolumeBlurRadius.value = volumeBlur.toFloat().coerceIn(10f, 60f)
        binding.tvVolumeBlurRadiusVal.text = "$volumeBlur px"

        // H. Performance
        val perfMode = prefs.getInt(GlassConfig.KEY_PERFORMANCE_MODE, GlassConfig.DEFAULT_PERFORMANCE_MODE)
        when (perfMode) {
            1 -> binding.rbPerfBalanced.isChecked = true
            2 -> binding.rbPerfEfficiency.isChecked = true
            else -> binding.rbPerfQuality.isChecked = true
        }

        updateStatusIndicator(enabled && !safeMode)
    }

    private fun setupListeners() {
        binding.sliderBlurRadius.addOnChangeListener { _, value, _ ->
            binding.tvBlurRadiusVal.text = "${value.toInt()} px"
        }

        binding.sliderShadeOpacity.addOnChangeListener { _, value, _ ->
            binding.tvShadeOpacityVal.text = "${value.toInt()}%"
        }

        binding.sliderQsBackgroundTintOpacity.addOnChangeListener { _, value, _ ->
            binding.tvQsBackgroundTintOpacityVal.text = "${value.toInt()}%"
        }

        binding.sliderQsExpandedOpacity.addOnChangeListener { _, value, _ ->
            binding.tvQsExpandedOpacityVal.text = "${value.toInt()}%"
        }

        binding.sliderQsCollapsedOpacity.addOnChangeListener { _, value, _ ->
            binding.tvQsCollapsedOpacityVal.text = "${value.toInt()}%"
        }

        binding.sliderNotifScrimOpacity.addOnChangeListener { _, value, _ ->
            binding.tvNotifScrimOpacityVal.text = "${value.toInt()}%"
        }

        binding.sliderNotifCardTintOpacity.addOnChangeListener { _, value, _ ->
            binding.tvNotifCardTintOpacityVal.text = "${value.toInt()}%"
        }

        binding.sliderNotifCornerRadius.addOnChangeListener { _, value, _ ->
            binding.tvNotifCornerRadiusVal.text = "${value.toInt()} dp"
        }

        binding.sliderNotifCardBlurRadius.addOnChangeListener { _, value, _ ->
            binding.tvNotifCardBlurRadiusVal.text = "${value.toInt()} px"
        }

        binding.sliderNotifOpacity.addOnChangeListener { _, value, _ ->
            binding.tvNotifOpacityVal.text = "${value.toInt()}%"
        }

        binding.sliderHeadsUpBlurRadius.addOnChangeListener { _, value, _ ->
            binding.tvHeadsUpBlurRadiusVal.text = "${value.toInt()} px"
        }

        binding.sliderHeadsUpOpacity.addOnChangeListener { _, value, _ ->
            binding.tvHeadsUpOpacityVal.text = "${value.toInt()}%"
        }

        binding.sliderActiveTileOpacity.addOnChangeListener { _, value, _ ->
            binding.tvActiveTileOpacityVal.text = "${value.toInt()}%"
        }

        binding.sliderInactiveTileOpacity.addOnChangeListener { _, value, _ ->
            binding.tvInactiveTileOpacityVal.text = "${value.toInt()}%"
        }

        binding.sliderQsIconActive.addOnChangeListener { _, value, _ ->
            binding.tvQsIconActiveVal.text = String.format(Locale.US, "%.2f", value / 100f)
        }

        binding.sliderQsIconInactive.addOnChangeListener { _, value, _ ->
            binding.tvQsIconInactiveVal.text = String.format(Locale.US, "%.2f", value / 100f)
        }

        binding.sliderQsIconDisabled.addOnChangeListener { _, value, _ ->
            binding.tvQsIconDisabledVal.text = String.format(Locale.US, "%.2f", value / 100f)
        }

        binding.sliderNotifIcon.addOnChangeListener { _, value, _ ->
            binding.tvNotifIconVal.text = String.format(Locale.US, "%.2f", value / 100f)
        }

        binding.sliderVolumeSliderOpacity.addOnChangeListener { _, value, _ ->
            binding.tvVolumeSliderOpacityVal.text = "${value.toInt()}%"
        }

        binding.sliderVolumeButtonsOpacity.addOnChangeListener { _, value, _ ->
            binding.tvVolumeButtonsOpacityVal.text = "${value.toInt()}%"
        }

        binding.sliderVolumeBlurRadius.addOnChangeListener { _, value, _ ->
            binding.tvVolumeBlurRadiusVal.text = "${value.toInt()} px"
        }

        // Auto-apply on slider release
        val autoApply = object : com.google.android.material.slider.Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: com.google.android.material.slider.Slider) {}
            override fun onStopTrackingTouch(slider: com.google.android.material.slider.Slider) {
                saveAndBroadcastConfig()
            }
        }
        binding.sliderBlurRadius.addOnSliderTouchListener(autoApply)
        binding.sliderShadeOpacity.addOnSliderTouchListener(autoApply)
        binding.sliderQsBackgroundTintOpacity.addOnSliderTouchListener(autoApply)
        binding.sliderQsExpandedOpacity.addOnSliderTouchListener(autoApply)
        binding.sliderQsCollapsedOpacity.addOnSliderTouchListener(autoApply)
        binding.sliderNotifScrimOpacity.addOnSliderTouchListener(autoApply)
        binding.sliderNotifCardTintOpacity.addOnSliderTouchListener(autoApply)
        binding.sliderNotifCornerRadius.addOnSliderTouchListener(autoApply)
        binding.sliderNotifCardBlurRadius.addOnSliderTouchListener(autoApply)
        binding.sliderNotifOpacity.addOnSliderTouchListener(autoApply)
        binding.sliderHeadsUpBlurRadius.addOnSliderTouchListener(autoApply)
        binding.sliderHeadsUpOpacity.addOnSliderTouchListener(autoApply)
        binding.sliderActiveTileOpacity.addOnSliderTouchListener(autoApply)
        binding.sliderInactiveTileOpacity.addOnSliderTouchListener(autoApply)
        binding.sliderQsIconActive.addOnSliderTouchListener(autoApply)
        binding.sliderQsIconInactive.addOnSliderTouchListener(autoApply)
        binding.sliderQsIconDisabled.addOnSliderTouchListener(autoApply)
        binding.sliderNotifIcon.addOnSliderTouchListener(autoApply)
        binding.sliderVolumeSliderOpacity.addOnSliderTouchListener(autoApply)
        binding.sliderVolumeButtonsOpacity.addOnSliderTouchListener(autoApply)
        binding.sliderVolumeBlurRadius.addOnSliderTouchListener(autoApply)

        binding.switchMaster.setOnCheckedChangeListener { _, isChecked ->
            updateStatusIndicator(isChecked && !binding.switchSafeMode.isChecked)
            saveAndBroadcastConfig()
        }

        binding.switchSafeMode.setOnCheckedChangeListener { _, isChecked ->
            updateStatusIndicator(binding.switchMaster.isChecked && !isChecked)
            saveAndBroadcastConfig()
        }

        binding.switchAnimation.setOnCheckedChangeListener { _, _ -> saveAndBroadcastConfig() }
        binding.switchNotifScrim.setOnCheckedChangeListener { _, _ -> saveAndBroadcastConfig() }
        binding.switchNotifScrimGradient.setOnCheckedChangeListener { _, _ -> saveAndBroadcastConfig() }
        binding.switchUnifiedNotifBackdropBlur.setOnCheckedChangeListener { _, _ -> saveAndBroadcastConfig() }
        binding.switchNotifCardBlur.setOnCheckedChangeListener { _, _ -> saveAndBroadcastConfig() }
        binding.switchNotifBorder.setOnCheckedChangeListener { _, _ -> saveAndBroadcastConfig() }
        binding.switchHeadsUpGlass.setOnCheckedChangeListener { _, _ -> saveAndBroadcastConfig() }
        binding.switchHeadsUpBlur.setOnCheckedChangeListener { _, _ -> saveAndBroadcastConfig() }
        binding.switchVolumeGlass.setOnCheckedChangeListener { _, _ -> saveAndBroadcastConfig() }
        binding.btnGenerateWallpaperColors.setOnClickListener { generateWallpaperColors() }
        binding.rgTint.setOnCheckedChangeListener { _, _ -> saveAndBroadcastConfig() }
        binding.rgNotifScrimTint.setOnCheckedChangeListener { _, _ -> saveAndBroadcastConfig() }
        binding.rgPerfMode.setOnCheckedChangeListener { _, _ -> saveAndBroadcastConfig() }

        binding.btnApplyChanges.setOnClickListener {
            saveAndBroadcastConfig()
            Toast.makeText(this, getString(R.string.settings_synced), Toast.LENGTH_SHORT).show()
        }

        binding.btnRestartSystemUI.setOnClickListener {
            saveAndBroadcastConfig()
            restartSystemUIRoot()
        }

        binding.btnResetDefaults.setOnClickListener {
            resetToDefaults()
        }

        binding.btnResetStablePreset.setOnClickListener {
            resetToStablePreset()
        }
    }

    private fun updateStatusIndicator(active: Boolean) {
        binding.statusIndicator.setBackgroundResource(
            if (active) R.drawable.bg_status_active else R.drawable.bg_status_inactive
        )
    }

    private fun generateWallpaperColors() {
        binding.btnGenerateWallpaperColors.isEnabled = false
        Thread {
            val generated = runCatching {
                val manager = android.app.WallpaperManager.getInstance(this)
                var colors = runCatching { manager.getWallpaperColors(android.app.WallpaperManager.FLAG_SYSTEM) }.getOrNull()
                if (colors == null) {
                    val imageFile = java.io.File(cacheDir, "palette-wallpaper")
                    try {
                        val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "cat /data/system/users/0/wallpaper"))
                        process.inputStream.use { input -> imageFile.outputStream().use { input.copyTo(it) } }
                        check(process.waitFor() == 0)
                        val options = android.graphics.BitmapFactory.Options().apply { inSampleSize = 8 }
                        val bitmap = android.graphics.BitmapFactory.decodeFile(imageFile.absolutePath, options)
                            ?: error("Wallpaper unavailable")
                        colors = android.app.WallpaperColors.fromBitmap(bitmap)
                        bitmap.recycle()
                    } finally { imageFile.delete() }
                }
                val seed = com.google.android.material.color.utilities.Hct.fromInt(colors!!.primaryColor.toArgb())
                val dark = com.google.android.material.color.utilities.Hct.from(seed.hue, seed.chroma.coerceIn(12.0, 28.0), 8.0).toInt()
                String.format(java.util.Locale.US, "#%06X", dark and 0xFFFFFF)
            }
            runOnUiThread {
                binding.btnGenerateWallpaperColors.isEnabled = true
                generated.onSuccess { hex ->
                    prefs.edit().putString("pref_wallpaper_generated_tint", hex).apply()
                    binding.rbWallpaperGenerated.text = getString(R.string.wallpaper_generated_label, hex)
                    binding.rbWallpaperGenerated.isChecked = true
                    saveAndBroadcastConfig()
                    Toast.makeText(this, getString(R.string.wallpaper_applied, hex), Toast.LENGTH_SHORT).show()
                }.onFailure {
                    Toast.makeText(this, getString(R.string.wallpaper_failed), Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    private fun saveAndBroadcastConfig() {
        prefs.edit().putBoolean("pref_wallpaper_generated_selected", binding.rbWallpaperGenerated.isChecked).apply()
        val tintHex = when {
            binding.rbWallpaperGenerated.isChecked -> prefs.getString("pref_wallpaper_generated_tint", GlassConfig.DEFAULT_TINT_HEX) ?: GlassConfig.DEFAULT_TINT_HEX
            binding.rbWallpaperMonet.isChecked -> GlassConfig.WALLPAPER_MONET
            binding.rbPureDark.isChecked -> "#08080C"
            binding.rbMidnightNavy.isChecked -> "#070C18"
            binding.rbDeepCharcoal.isChecked -> "#121214"
            else -> "#0D111A"
        }

        val notifScrimTintHex = when {
            binding.rbNotifWallpaperMonet.isChecked -> GlassConfig.WALLPAPER_MONET
            binding.rbNotifPureBlack.isChecked -> "#000000"
            binding.rbNotifMidnight.isChecked -> "#060A10"
            else -> "#0D111A"
        }

        val perfMode = when {
            binding.rbPerfBalanced.isChecked -> 1
            binding.rbPerfEfficiency.isChecked -> 2
            else -> 0
        }

        val enabled = binding.switchMaster.isChecked
        val safeMode = binding.switchSafeMode.isChecked
        val debugLogging = binding.switchDebugLogging.isChecked
        val debugOverlay = binding.switchDebugOverlayBounds.isChecked

        val blurRadius = binding.sliderBlurRadius.value.toInt()
        val shadeOpacity = binding.sliderShadeOpacity.value.toInt()
        val qsBgTintOpacity = binding.sliderQsBackgroundTintOpacity.value.toInt()
        val qsExpandedOpacity = binding.sliderQsExpandedOpacity.value.toInt()
        val qsCollapsedOpacity = binding.sliderQsCollapsedOpacity.value.toInt()
        val animationEnabled = binding.switchAnimation.isChecked

        val notifScrimEnabled = binding.switchNotifScrim.isChecked
        val notifScrimOpacity = binding.sliderNotifScrimOpacity.value.toInt()
        val notifScrimGradient = binding.switchNotifScrimGradient.isChecked

        val unifiedBlur = binding.switchUnifiedNotifBackdropBlur.isChecked
        val notifCardTintOpacity = binding.sliderNotifCardTintOpacity.value.toInt()
        val notifCardCornerRadius = binding.sliderNotifCornerRadius.value.toInt()
        val notifCardBlur = binding.switchNotifCardBlur.isChecked
        val notifCardBlurRadius = binding.sliderNotifCardBlurRadius.value.toInt()
        val notifOpacity = binding.sliderNotifOpacity.value.toInt()
        val notifBorder = binding.switchNotifBorder.isChecked

        val headsUpGlass = binding.switchHeadsUpGlass.isChecked
        val headsUpBlurEnabled = binding.switchHeadsUpBlur.isChecked
        val headsUpBlurRadius = binding.sliderHeadsUpBlurRadius.value.toInt()
        val headsUpOpacity = binding.sliderHeadsUpOpacity.value.toInt()

        val activeTileOpacity = binding.sliderActiveTileOpacity.value.toInt()
        val inactiveTileOpacity = binding.sliderInactiveTileOpacity.value.toInt()

        val qsIconActive = binding.sliderQsIconActive.value.toInt()
        val qsIconInactive = binding.sliderQsIconInactive.value.toInt()
        val qsIconDisabled = binding.sliderQsIconDisabled.value.toInt()
        val notifIcon = binding.sliderNotifIcon.value.toInt()

        val volumeGlassEnabled = binding.switchVolumeGlass.isChecked
        val volumeSliderAlpha = binding.sliderVolumeSliderOpacity.value.toInt()
        val volumeButtonsAlpha = binding.sliderVolumeButtonsOpacity.value.toInt()
        val volumeBlurRadius = binding.sliderVolumeBlurRadius.value.toInt()

        prefs.edit().apply {
            putBoolean(GlassConfig.KEY_ENABLED, enabled)
            putBoolean(GlassConfig.KEY_SAFE_MODE, safeMode)
            putBoolean(GlassConfig.KEY_DEBUG_LOGGING, debugLogging)
            putBoolean(GlassConfig.KEY_DEBUG_OVERLAY_BOUNDS, debugOverlay)

            putInt(GlassConfig.KEY_BLUR_RADIUS, blurRadius)
            putInt(GlassConfig.KEY_SHADE_OPACITY, shadeOpacity)
            putInt(GlassConfig.KEY_QS_BACKGROUND_TINT_OPACITY, qsBgTintOpacity)
            putInt(GlassConfig.KEY_QS_EXPANDED_ALPHA, qsExpandedOpacity)
            putInt(GlassConfig.KEY_QS_COLLAPSED_ALPHA, qsCollapsedOpacity)
            putBoolean(GlassConfig.KEY_ANIMATION_ENABLED, animationEnabled)
            putString(GlassConfig.KEY_TINT_HEX, tintHex)

            putBoolean(GlassConfig.KEY_NOTIF_SCRIM_ENABLED, notifScrimEnabled)
            putInt(GlassConfig.KEY_NOTIF_SCRIM_OPACITY, notifScrimOpacity)
            putBoolean(GlassConfig.KEY_NOTIF_SCRIM_GRADIENT_ENABLED, notifScrimGradient)
            putString(GlassConfig.KEY_NOTIF_SCRIM_TINT, notifScrimTintHex)

            putBoolean(GlassConfig.KEY_UNIFIED_NOTIF_BACKDROP_BLUR, unifiedBlur)
            putInt(GlassConfig.KEY_NOTIF_CARD_TINT_OPACITY, notifCardTintOpacity)
            putString(GlassConfig.KEY_NOTIF_CARD_TINT, tintHex)
            putInt(GlassConfig.KEY_NOTIF_CARD_CORNER_RADIUS, notifCardCornerRadius)
            putBoolean(GlassConfig.KEY_NOTIF_CARD_BLUR_ENABLED, notifCardBlur)
            putInt(GlassConfig.KEY_NOTIF_CARD_BLUR_RADIUS, notifCardBlurRadius)
            putInt(GlassConfig.KEY_NOTIF_CARD_ALPHA, notifOpacity)
            putBoolean(GlassConfig.KEY_NOTIF_BORDER_ENABLED, notifBorder)

            putBoolean(GlassConfig.KEY_HEADSUP_GLASS_ENABLED, headsUpGlass)
            putBoolean(GlassConfig.KEY_HEADSUP_BLUR_ENABLED, headsUpBlurEnabled)
            putInt(GlassConfig.KEY_HEADSUP_BLUR_RADIUS, headsUpBlurRadius)
            putInt(GlassConfig.KEY_HEADSUP_TINT_OPACITY, headsUpOpacity)
            putString(GlassConfig.KEY_HEADSUP_TINT, tintHex)

            putInt(GlassConfig.KEY_ACTIVE_TILE_ALPHA, activeTileOpacity)
            putInt(GlassConfig.KEY_INACTIVE_TILE_ALPHA, inactiveTileOpacity)

            putInt(GlassConfig.KEY_QS_ICON_ACTIVE, qsIconActive)
            putInt(GlassConfig.KEY_QS_ICON_INACTIVE, qsIconInactive)
            putInt(GlassConfig.KEY_QS_ICON_DISABLED, qsIconDisabled)
            putInt(GlassConfig.KEY_NOTIF_ICON, notifIcon)

            putBoolean(GlassConfig.KEY_VOLUME_GLASS_ENABLED, volumeGlassEnabled)
            putInt(GlassConfig.KEY_VOLUME_SLIDER_ALPHA, volumeSliderAlpha)
            putInt(GlassConfig.KEY_VOLUME_BUTTONS_ALPHA, volumeButtonsAlpha)
            putInt(GlassConfig.KEY_VOLUME_BLUR_RADIUS, volumeBlurRadius)

            putInt(GlassConfig.KEY_PERFORMANCE_MODE, perfMode)
            putBoolean(GlassConfig.KEY_DISABLE_PER_CARD_FALLBACK, unifiedBlur)
            apply()
        }

        // Send IPC broadcast
        val intent = Intent(GlassConfig.ACTION_CONFIG_CHANGED).apply {
            putExtra(GlassConfig.KEY_ENABLED, enabled)
            putExtra(GlassConfig.KEY_SAFE_MODE, safeMode)
            putExtra(GlassConfig.KEY_DEBUG_LOGGING, debugLogging)
            putExtra(GlassConfig.KEY_DEBUG_OVERLAY_BOUNDS, debugOverlay)

            putExtra(GlassConfig.KEY_BLUR_RADIUS, blurRadius)
            putExtra(GlassConfig.KEY_SHADE_OPACITY, shadeOpacity)
            putExtra(GlassConfig.KEY_QS_BACKGROUND_TINT_OPACITY, qsBgTintOpacity)
            putExtra(GlassConfig.KEY_QS_EXPANDED_ALPHA, qsExpandedOpacity)
            putExtra(GlassConfig.KEY_QS_COLLAPSED_ALPHA, qsCollapsedOpacity)
            putExtra(GlassConfig.KEY_ANIMATION_ENABLED, animationEnabled)
            putExtra(GlassConfig.KEY_TINT_HEX, tintHex)

            putExtra(GlassConfig.KEY_NOTIF_SCRIM_ENABLED, notifScrimEnabled)
            putExtra(GlassConfig.KEY_NOTIF_SCRIM_OPACITY, notifScrimOpacity)
            putExtra(GlassConfig.KEY_NOTIF_SCRIM_GRADIENT_ENABLED, notifScrimGradient)
            putExtra(GlassConfig.KEY_NOTIF_SCRIM_TINT, notifScrimTintHex)

            putExtra(GlassConfig.KEY_UNIFIED_NOTIF_BACKDROP_BLUR, unifiedBlur)
            putExtra(GlassConfig.KEY_NOTIF_CARD_TINT_OPACITY, notifCardTintOpacity)
            putExtra(GlassConfig.KEY_NOTIF_CARD_TINT, tintHex)
            putExtra(GlassConfig.KEY_NOTIF_CARD_CORNER_RADIUS, notifCardCornerRadius)
            putExtra(GlassConfig.KEY_NOTIF_CARD_BLUR_ENABLED, notifCardBlur)
            putExtra(GlassConfig.KEY_NOTIF_CARD_BLUR_RADIUS, notifCardBlurRadius)
            putExtra(GlassConfig.KEY_NOTIF_CARD_ALPHA, notifOpacity)
            putExtra(GlassConfig.KEY_NOTIF_BORDER_ENABLED, notifBorder)

            putExtra(GlassConfig.KEY_HEADSUP_GLASS_ENABLED, headsUpGlass)
            putExtra(GlassConfig.KEY_HEADSUP_BLUR_ENABLED, headsUpBlurEnabled)
            putExtra(GlassConfig.KEY_HEADSUP_BLUR_RADIUS, headsUpBlurRadius)
            putExtra(GlassConfig.KEY_HEADSUP_TINT_OPACITY, headsUpOpacity)
            putExtra(GlassConfig.KEY_HEADSUP_TINT, tintHex)

            putExtra(GlassConfig.KEY_ACTIVE_TILE_ALPHA, activeTileOpacity)
            putExtra(GlassConfig.KEY_INACTIVE_TILE_ALPHA, inactiveTileOpacity)

            putExtra(GlassConfig.KEY_QS_ICON_ACTIVE, qsIconActive)
            putExtra(GlassConfig.KEY_QS_ICON_INACTIVE, qsIconInactive)
            putExtra(GlassConfig.KEY_QS_ICON_DISABLED, qsIconDisabled)
            putExtra(GlassConfig.KEY_NOTIF_ICON, notifIcon)

            putExtra(GlassConfig.KEY_VOLUME_GLASS_ENABLED, volumeGlassEnabled)
            putExtra(GlassConfig.KEY_VOLUME_SLIDER_ALPHA, volumeSliderAlpha)
            putExtra(GlassConfig.KEY_VOLUME_BUTTONS_ALPHA, volumeButtonsAlpha)
            putExtra(GlassConfig.KEY_VOLUME_BLUR_RADIUS, volumeBlurRadius)

            putExtra(GlassConfig.KEY_PERFORMANCE_MODE, perfMode)
            putExtra(GlassConfig.KEY_DISABLE_PER_CARD_FALLBACK, unifiedBlur)

            setPackage("com.android.systemui")
            addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
            addFlags(0x01000000) // Intent.FLAG_RECEIVER_INCLUDE_BACKGROUND
        }
        sendBroadcast(intent)

        // Fallback broadcast without explicit package
        try {
            val fallback = Intent(GlassConfig.ACTION_CONFIG_CHANGED).apply {
                addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
                putExtras(intent)
            }
            sendBroadcast(fallback)
        } catch (_: Throwable) {}

        // ContentProvider call
        try {
            val uri = Uri.parse("content://${GlassConfig.AUTHORITY}")
            contentResolver.call(uri, GlassConfigProvider.METHOD_SET_CONFIG, null, intent.extras)
        } catch (_: Throwable) {}

        // Fix file permissions for XSharedPreferences
        Thread {
            try {
                val p = Runtime.getRuntime().exec("su")
                val os = DataOutputStream(p.outputStream)
                os.writeBytes("chmod 755 /data/data/com.nothing.glass\n")
                os.writeBytes("chmod 777 /data/data/com.nothing.glass/shared_prefs\n")
                os.writeBytes("chmod 666 /data/data/com.nothing.glass/shared_prefs/nothing_glass_prefs.xml\n")
                os.writeBytes("exit\n")
                os.flush()
                p.waitFor()
            } catch (_: Throwable) {}
        }.start()
    }

    private fun resetToDefaults() {
        prefs.edit().clear().apply()
        loadPreferences()
        saveAndBroadcastConfig()
        Toast.makeText(this, getString(R.string.settings_reset), Toast.LENGTH_SHORT).show()
    }

    private fun resetToStablePreset() {
        val stable = GlassConfig.getStablePreset()
        prefs.edit().apply {
            putBoolean(GlassConfig.KEY_ENABLED, stable.enabled)
            putBoolean(GlassConfig.KEY_SAFE_MODE, stable.safeMode)
            putBoolean(GlassConfig.KEY_DEBUG_LOGGING, stable.debugLogging)
            putBoolean(GlassConfig.KEY_DEBUG_OVERLAY_BOUNDS, stable.debugOverlayBounds)

            putInt(GlassConfig.KEY_BLUR_RADIUS, stable.blurRadius)
            putInt(GlassConfig.KEY_SHADE_OPACITY, stable.shadeOpacityPercent)
            putString(GlassConfig.KEY_TINT_HEX, stable.tintHex)
            putInt(GlassConfig.KEY_QS_BACKGROUND_TINT_OPACITY, stable.qsBackgroundTintOpacityPercent)
            putInt(GlassConfig.KEY_QS_EXPANDED_ALPHA, stable.qsExpandedAlphaPercent)
            putInt(GlassConfig.KEY_QS_COLLAPSED_ALPHA, stable.qsCollapsedAlphaPercent)
            putBoolean(GlassConfig.KEY_ANIMATION_ENABLED, stable.animationEnabled)

            putBoolean(GlassConfig.KEY_NOTIF_SCRIM_ENABLED, stable.notifScrimEnabled)
            putInt(GlassConfig.KEY_NOTIF_SCRIM_OPACITY, stable.notifScrimOpacityPercent)
            putString(GlassConfig.KEY_NOTIF_SCRIM_TINT, stable.notifScrimTintHex)
            putBoolean(GlassConfig.KEY_NOTIF_SCRIM_GRADIENT_ENABLED, stable.notifScrimGradientEnabled)

            putBoolean(GlassConfig.KEY_NOTIF_GLASS_ENABLED, stable.notifGlassEnabled)
            putInt(GlassConfig.KEY_NOTIF_CARD_ALPHA, stable.notifCardAlphaPercent)
            putInt(GlassConfig.KEY_NOTIF_CARD_TINT_OPACITY, stable.notifCardTintOpacityPercent)
            putString(GlassConfig.KEY_NOTIF_CARD_TINT, stable.notifCardTintHex)
            putBoolean(GlassConfig.KEY_NOTIF_CARD_BLUR_ENABLED, stable.notifCardBlurEnabled)
            putInt(GlassConfig.KEY_NOTIF_CARD_BLUR_RADIUS, stable.notifCardBlurRadius)
            putInt(GlassConfig.KEY_NOTIF_CARD_CORNER_RADIUS, stable.notifCardCornerRadiusDp)
            putBoolean(GlassConfig.KEY_UNIFIED_NOTIF_BACKDROP_BLUR, stable.unifiedNotifBackdropBlur)
            putBoolean(GlassConfig.KEY_NOTIF_BORDER_ENABLED, stable.notifBorderEnabled)

            putBoolean(GlassConfig.KEY_HEADSUP_GLASS_ENABLED, stable.headsUpGlassEnabled)
            putBoolean(GlassConfig.KEY_HEADSUP_BLUR_ENABLED, stable.headsUpBlurEnabled)
            putInt(GlassConfig.KEY_HEADSUP_BLUR_RADIUS, stable.headsUpBlurRadius)
            putInt(GlassConfig.KEY_HEADSUP_TINT_OPACITY, stable.headsUpTintOpacityPercent)
            putString(GlassConfig.KEY_HEADSUP_TINT, stable.headsUpTintHex)

            putInt(GlassConfig.KEY_ACTIVE_TILE_ALPHA, stable.activeTileAlphaPercent)
            putInt(GlassConfig.KEY_INACTIVE_TILE_ALPHA, stable.inactiveTileAlphaPercent)

            putInt(GlassConfig.KEY_QS_ICON_ACTIVE, stable.qsIconAlphaActivePercent)
            putInt(GlassConfig.KEY_QS_ICON_INACTIVE, stable.qsIconAlphaInactivePercent)
            putInt(GlassConfig.KEY_QS_ICON_DISABLED, stable.qsIconAlphaDisabledPercent)
            putInt(GlassConfig.KEY_NOTIF_ICON, stable.notifIconAlphaPercent)

            putBoolean(GlassConfig.KEY_VOLUME_GLASS_ENABLED, stable.volumeGlassEnabled)
            putInt(GlassConfig.KEY_VOLUME_SLIDER_ALPHA, stable.volumeSliderAlphaPercent)
            putInt(GlassConfig.KEY_VOLUME_BUTTONS_ALPHA, stable.volumeButtonsAlphaPercent)
            putInt(GlassConfig.KEY_VOLUME_BLUR_RADIUS, stable.volumeBlurRadius)

            putInt(GlassConfig.KEY_BLUR_QUALITY_PRESET, stable.blurQualityPreset)
            putInt(GlassConfig.KEY_PERFORMANCE_MODE, stable.performanceMode)
            putBoolean(GlassConfig.KEY_DISABLE_PER_CARD_FALLBACK, stable.disablePerCardBlurFallback)
            apply()
        }
        loadPreferences()
        saveAndBroadcastConfig()
        Toast.makeText(this, getString(R.string.stable_reset), Toast.LENGTH_SHORT).show()
    }

    private fun restartSystemUIRoot() {
        Toast.makeText(this, getString(R.string.restarting_ui), Toast.LENGTH_SHORT).show()
        Thread {
            try {
                val p = Runtime.getRuntime().exec("su")
                val os = DataOutputStream(p.outputStream)
                os.writeBytes("kill -9 \$(pidof com.android.systemui) || killall -9 com.android.systemui || pkill -9 -f com.android.systemui\n")
                os.writeBytes("exit\n")
                os.flush()
                p.waitFor()
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this, getString(R.string.root_failed, e.message ?: ""), Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }
}
