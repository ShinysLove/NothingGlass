package com.nothing.glass.config

import android.graphics.Color

data class GlassConfig(
    // A. General
    val enabled: Boolean = DEFAULT_ENABLED,
    val safeMode: Boolean = DEFAULT_SAFE_MODE,
    val debugLogging: Boolean = DEFAULT_DEBUG_LOGGING,
    val debugOverlayBounds: Boolean = DEFAULT_DEBUG_OVERLAY_BOUNDS,

    // B. Main Shade & Hardware Backdrop Blur
    val blurRadius: Int = DEFAULT_BLUR_RADIUS,
    val shadeOpacityPercent: Int = DEFAULT_SHADE_OPACITY,
    val tintHex: String = DEFAULT_TINT_HEX,
    val qsBackgroundTintOpacityPercent: Int = DEFAULT_QS_BACKGROUND_TINT_OPACITY,
    val qsExpandedAlphaPercent: Int = DEFAULT_QS_EXPANDED_ALPHA,
    val qsCollapsedAlphaPercent: Int = DEFAULT_QS_COLLAPSED_ALPHA,
    val animationEnabled: Boolean = DEFAULT_ANIMATION_ENABLED,
    val animationIntensityPercent: Int = DEFAULT_ANIMATION_INTENSITY,

    // C. Notification Area Scrim
    val notifScrimEnabled: Boolean = DEFAULT_NOTIF_SCRIM_ENABLED,
    val notifScrimOpacityPercent: Int = DEFAULT_NOTIF_SCRIM_OPACITY,
    val notifScrimTintHex: String = DEFAULT_NOTIF_SCRIM_TINT_HEX,
    val notifScrimGradientEnabled: Boolean = DEFAULT_NOTIF_SCRIM_GRADIENT_ENABLED,
    val notifScrimGradientStrengthPercent: Int = DEFAULT_NOTIF_SCRIM_GRADIENT_STRENGTH,

    // D. Notification Cards (Frosted Glass & Substrate)
    val notifGlassEnabled: Boolean = DEFAULT_NOTIF_GLASS_ENABLED,
    val notifCardAlphaPercent: Int = DEFAULT_NOTIF_CARD_ALPHA,
    val notifCardTintOpacityPercent: Int = DEFAULT_NOTIF_CARD_TINT_OPACITY,
    val notifCardTintHex: String = DEFAULT_NOTIF_CARD_TINT_HEX,
    val notifCardBlurEnabled: Boolean = DEFAULT_NOTIF_CARD_BLUR_ENABLED,
    val notifCardBlurRadius: Int = DEFAULT_NOTIF_CARD_BLUR_RADIUS,
    val notifCardCornerRadiusDp: Int = DEFAULT_NOTIF_CARD_CORNER_RADIUS_DP,
    val unifiedNotifBackdropBlur: Boolean = DEFAULT_UNIFIED_NOTIF_BACKDROP_BLUR,
    val notifCornerTreatment: Int = DEFAULT_NOTIF_CORNER_TREATMENT,
    val notifBorderEnabled: Boolean = DEFAULT_NOTIF_BORDER_ENABLED,
    val notifBorderAlphaPercent: Int = DEFAULT_NOTIF_BORDER_ALPHA,

    // E. Heads-up Notifications
    val headsUpGlassEnabled: Boolean = DEFAULT_HEADSUP_GLASS_ENABLED,
    val headsUpAlphaPercent: Int = DEFAULT_HEADSUP_ALPHA,
    val headsUpTintOpacityPercent: Int = DEFAULT_HEADSUP_TINT_OPACITY,
    val headsUpBlurEnabled: Boolean = DEFAULT_HEADSUP_BLUR_ENABLED,
    val headsUpBlurRadius: Int = DEFAULT_HEADSUP_BLUR_RADIUS,
    val headsUpTintHex: String = DEFAULT_HEADSUP_TINT_HEX,

    // F. QS Tiles
    val tileOpacityPercent: Int = DEFAULT_TILE_OPACITY,
    val qsTileBlurEnabled: Boolean = DEFAULT_QS_TILE_BLUR_ENABLED,
    val qsTileBlurRadius: Int = DEFAULT_QS_TILE_BLUR_RADIUS,
    val activeTileAlphaPercent: Int = DEFAULT_ACTIVE_TILE_ALPHA,
    val inactiveTileAlphaPercent: Int = DEFAULT_INACTIVE_TILE_ALPHA,
    val disabledTileAlphaPercent: Int = DEFAULT_DISABLED_TILE_ALPHA,
    val tileTintHex: String = DEFAULT_TILE_TINT_HEX,
    val tileCornerRadiusOverrideDp: Int = DEFAULT_TILE_CORNER_OVERRIDE,

    // G. Icons & Text
    val qsIconAlphaActivePercent: Int = DEFAULT_QS_ICON_ACTIVE_PERCENT,
    val qsIconAlphaInactivePercent: Int = DEFAULT_QS_ICON_INACTIVE_PERCENT,
    val qsIconAlphaDisabledPercent: Int = DEFAULT_QS_ICON_DISABLED_PERCENT,
    val notifIconAlphaPercent: Int = DEFAULT_NOTIF_ICON_PERCENT,
    val textAlphaPercent: Int = DEFAULT_TEXT_ALPHA_PERCENT,

    // I. Volume Panel
    val volumeGlassEnabled: Boolean = DEFAULT_VOLUME_GLASS_ENABLED,
    val volumeSliderAlphaPercent: Int = DEFAULT_VOLUME_SLIDER_ALPHA_PERCENT,
    val volumeButtonsAlphaPercent: Int = DEFAULT_VOLUME_BUTTONS_ALPHA_PERCENT,
    val volumeBlurRadius: Int = DEFAULT_VOLUME_BLUR_RADIUS,

    // H. Performance & Presets
    val blurQualityPreset: Int = DEFAULT_BLUR_QUALITY_PRESET, // 0=High Quality, 1=Balanced
    val performanceMode: Int = DEFAULT_PERFORMANCE_MODE, // 0=Quality, 1=Balanced, 2=Efficiency
    val limitBlurUpdates: Boolean = DEFAULT_LIMIT_BLUR_UPDATES,
    val reduceRerenders: Boolean = DEFAULT_REDUCE_RERENDERS,
    val cacheResources: Boolean = DEFAULT_CACHE_RESOURCES,
    val disablePerCardBlurFallback: Boolean = DEFAULT_DISABLE_PER_CARD_FALLBACK
) {
    val isEffectiveEnabled: Boolean
        get() = enabled && !safeMode

    // Main shade alphas
    val shadeAlpha: Float
        get() = (shadeOpacityPercent.coerceIn(0, 100) / 100f)

    val qsBackgroundAlpha: Float
        get() = (qsBackgroundTintOpacityPercent.coerceIn(0, 100) / 100f)

    val qsExpandedAlpha: Float
        get() = (qsExpandedAlphaPercent.coerceIn(0, 100) / 100f)

    val qsCollapsedAlpha: Float
        get() = (qsCollapsedAlphaPercent.coerceIn(0, 100) / 100f)

    val tintColorInt: Int
        get() = safeParseColor(tintHex, DEFAULT_TINT_HEX)

    // Notification Scrim alphas
    val notifScrimAlpha: Float
        get() = (notifScrimOpacityPercent.coerceIn(0, 100) / 100f)

    val notifScrimTintColorInt: Int
        get() = safeParseColor(notifScrimTintHex, DEFAULT_NOTIF_SCRIM_TINT_HEX)

    val notifScrimGradientStrength: Float
        get() = (notifScrimGradientStrengthPercent.coerceIn(10, 100) / 100f)

    // Notification Cards alphas
    val notifCardAlpha: Float
        get() = (notifCardAlphaPercent.coerceIn(0, 100) / 100f)

    val notifCardTintAlpha: Float
        get() = (notifCardTintOpacityPercent.coerceIn(0, 100) / 100f)

    val notifCardTintColorInt: Int
        get() = safeParseColor(notifCardTintHex, DEFAULT_NOTIF_CARD_TINT_HEX)

    val notifBorderAlpha: Float
        get() = (notifBorderAlphaPercent.coerceIn(0, 100) / 100f)

    // Heads-up alphas
    val headsUpAlpha: Float
        get() = (headsUpAlphaPercent.coerceIn(0, 100) / 100f)

    val headsUpTintAlpha: Float
        get() = (headsUpTintOpacityPercent.coerceIn(0, 100) / 100f)

    val headsUpTintColorInt: Int
        get() = safeParseColor(headsUpTintHex, DEFAULT_HEADSUP_TINT_HEX)

    // QS Tile alphas
    val tileAlpha: Float
        get() = (tileOpacityPercent.coerceIn(0, 100) / 100f)

    val activeTileAlpha: Float
        get() = (activeTileAlphaPercent.coerceIn(0, 100) / 100f)

    val inactiveTileAlpha: Float
        get() = (inactiveTileAlphaPercent.coerceIn(0, 100) / 100f)

    val disabledTileAlpha: Float
        get() = (disabledTileAlphaPercent.coerceIn(0, 100) / 100f)

    val tileTintColorInt: Int
        get() = safeParseColor(tileTintHex, DEFAULT_TILE_TINT_HEX)

    // QS Icon computed alphas
    val qsIconAlphaActive: Float
        get() = (qsIconAlphaActivePercent.coerceIn(0, 100) / 100f)

    val qsIconAlphaInactive: Float
        get() = (qsIconAlphaInactivePercent.coerceIn(0, 100) / 100f)

    val qsIconAlphaDisabled: Float
        get() = (qsIconAlphaDisabledPercent.coerceIn(0, 100) / 100f)

    // Notification Icon computed alpha
    val notifIconAlpha: Float
        get() = (notifIconAlphaPercent.coerceIn(0, 100) / 100f)

    val textAlpha: Float
        get() = (textAlphaPercent.coerceIn(50, 100) / 100f)

    companion object {
        const val PREFS_NAME = "nothing_glass_prefs"
        const val AUTHORITY = "com.nothing.glass.provider"
        const val ACTION_CONFIG_CHANGED = "com.nothing.glass.ACTION_CONFIG_CHANGED"
        const val DISABLE_FLAG_FILE = "/data/local/tmp/nothing_glass_disable"

        // Keys A
        const val KEY_ENABLED = "pref_enabled"
        const val KEY_SAFE_MODE = "pref_safe_mode"
        const val KEY_DEBUG_LOGGING = "pref_debug_logging"
        const val KEY_DEBUG_OVERLAY_BOUNDS = "pref_debug_overlay_bounds"

        // Keys B
        const val KEY_BLUR_RADIUS = "pref_blur_radius"
        const val KEY_SHADE_OPACITY = "pref_shade_opacity"
        const val KEY_TINT_HEX = "pref_tint_hex"
        const val KEY_QS_BACKGROUND_TINT_OPACITY = "pref_qs_background_tint_opacity"
        const val KEY_QS_EXPANDED_ALPHA = "pref_qs_expanded_alpha"
        const val KEY_QS_COLLAPSED_ALPHA = "pref_qs_collapsed_alpha"
        const val KEY_ANIMATION_ENABLED = "pref_animation_enabled"
        const val KEY_ANIMATION_INTENSITY = "pref_animation_intensity"

        // Keys C
        const val KEY_NOTIF_SCRIM_ENABLED = "pref_notif_scrim_enabled"
        const val KEY_NOTIF_SCRIM_OPACITY = "pref_notif_scrim_opacity"
        const val KEY_NOTIF_SCRIM_TINT = "pref_notif_scrim_tint"
        const val KEY_NOTIF_SCRIM_GRADIENT_ENABLED = "pref_notif_scrim_gradient_enabled"
        const val KEY_NOTIF_SCRIM_GRADIENT_STRENGTH = "pref_notif_scrim_gradient_strength"

        // Keys D
        const val KEY_NOTIF_GLASS_ENABLED = "pref_notif_glass_enabled"
        const val KEY_NOTIF_CARD_ALPHA = "pref_notif_card_alpha"
        const val KEY_NOTIF_CARD_TINT_OPACITY = "pref_notif_card_tint_opacity"
        const val KEY_NOTIF_CARD_TINT = "pref_notif_card_tint"
        const val KEY_NOTIF_CARD_BLUR_ENABLED = "pref_notif_card_blur_enabled"
        const val KEY_NOTIF_CARD_BLUR_RADIUS = "pref_notif_card_blur_radius"
        const val KEY_NOTIF_CARD_CORNER_RADIUS = "pref_notif_card_corner_radius"
        const val KEY_UNIFIED_NOTIF_BACKDROP_BLUR = "pref_unified_notif_backdrop_blur"
        const val KEY_NOTIF_CORNER_TREATMENT = "pref_notif_corner_treatment"
        const val KEY_NOTIF_BORDER_ENABLED = "pref_notif_border_enabled"
        const val KEY_NOTIF_BORDER_ALPHA = "pref_notif_border_alpha"

        // Keys E
        const val KEY_HEADSUP_GLASS_ENABLED = "pref_headsup_glass_enabled"
        const val KEY_HEADSUP_ALPHA = "pref_headsup_alpha"
        const val KEY_HEADSUP_TINT_OPACITY = "pref_headsup_tint_opacity"
        const val KEY_HEADSUP_BLUR_ENABLED = "pref_headsup_blur_enabled"
        const val KEY_HEADSUP_BLUR_RADIUS = "pref_headsup_blur_radius"
        const val KEY_HEADSUP_TINT = "pref_headsup_tint"

        // Keys F
        const val KEY_TILE_OPACITY = "pref_tile_opacity"
        const val KEY_QS_TILE_BLUR_ENABLED = "pref_qs_tile_blur_enabled"
        const val KEY_QS_TILE_BLUR_RADIUS = "pref_qs_tile_blur_radius"
        const val KEY_ACTIVE_TILE_ALPHA = "pref_active_tile_alpha"
        const val KEY_INACTIVE_TILE_ALPHA = "pref_inactive_tile_alpha"
        const val KEY_DISABLED_TILE_ALPHA = "pref_disabled_tile_alpha"
        const val KEY_TILE_TINT = "pref_tile_tint"
        const val KEY_TILE_CORNER_OVERRIDE = "pref_tile_corner_override"

        // Keys G
        const val KEY_QS_ICON_ACTIVE = "pref_qs_icon_active"
        const val KEY_QS_ICON_INACTIVE = "pref_qs_icon_inactive"
        const val KEY_QS_ICON_DISABLED = "pref_qs_icon_disabled"
        const val KEY_NOTIF_ICON = "pref_notif_icon"
        const val KEY_TEXT_ALPHA = "pref_text_alpha"

        // Keys I (Volume)
        const val KEY_VOLUME_GLASS_ENABLED = "pref_volume_glass_enabled"
        const val KEY_VOLUME_SLIDER_ALPHA = "pref_volume_slider_alpha"
        const val KEY_VOLUME_BUTTONS_ALPHA = "pref_volume_buttons_alpha"
        const val KEY_VOLUME_BLUR_RADIUS = "pref_volume_blur_radius"

        // Keys H
        const val KEY_BLUR_QUALITY_PRESET = "pref_blur_quality_preset"
        const val KEY_PERFORMANCE_MODE = "pref_perf_mode"
        const val KEY_LIMIT_BLUR_UPDATES = "pref_limit_blur_updates"
        const val KEY_REDUCE_RERENDERS = "pref_reduce_rerenders"
        const val KEY_CACHE_RESOURCES = "pref_cache_resources"
        const val KEY_DISABLE_PER_CARD_FALLBACK = "pref_disable_per_card_fallback"

        // Defaults A
        const val DEFAULT_ENABLED = true
        const val DEFAULT_SAFE_MODE = false
        const val DEFAULT_DEBUG_LOGGING = false
        const val DEFAULT_DEBUG_OVERLAY_BOUNDS = false

        // Defaults B
        const val DEFAULT_BLUR_RADIUS = 45 // 30-45 px
        const val DEFAULT_SHADE_OPACITY = 35
        const val DEFAULT_TINT_HEX = "#0D111A"
        const val DEFAULT_QS_BACKGROUND_TINT_OPACITY = 35 // 35-45%
        const val DEFAULT_QS_EXPANDED_ALPHA = 35
        const val DEFAULT_QS_COLLAPSED_ALPHA = 25
        const val DEFAULT_ANIMATION_ENABLED = true
        const val DEFAULT_ANIMATION_INTENSITY = 100

        // Defaults C
        const val DEFAULT_NOTIF_SCRIM_ENABLED = true
        const val DEFAULT_NOTIF_SCRIM_OPACITY = 28 // Translucent scrim allowing backdrop blur to shine through
        const val DEFAULT_NOTIF_SCRIM_TINT_HEX = "#0D111A"
        const val DEFAULT_NOTIF_SCRIM_GRADIENT_ENABLED = true
        const val DEFAULT_NOTIF_SCRIM_GRADIENT_STRENGTH = 50

        // Defaults D
        const val DEFAULT_NOTIF_GLASS_ENABLED = true
        const val DEFAULT_NOTIF_CARD_ALPHA = 50 // Translucent frosted glass (50%)
        const val DEFAULT_NOTIF_CARD_TINT_OPACITY = 20 // Soft dark tint substrate (20%)
        const val DEFAULT_NOTIF_CARD_TINT_HEX = "#0D111A"
        const val DEFAULT_NOTIF_CARD_BLUR_ENABLED = false // Unified Backdrop Blur used by default
        const val DEFAULT_NOTIF_CARD_BLUR_RADIUS = 35 // 30-45 px
        const val DEFAULT_NOTIF_CARD_CORNER_RADIUS_DP = 28 // 28 dp standard Nothing OS pill
        const val DEFAULT_UNIFIED_NOTIF_BACKDROP_BLUR = true // Prevents rectangular boundary artifacts & double-blur
        const val DEFAULT_NOTIF_CORNER_TREATMENT = 0
        const val DEFAULT_NOTIF_BORDER_ENABLED = false // Disabled completely to avoid strange outlines
        const val DEFAULT_NOTIF_BORDER_ALPHA = 0

        // Defaults E
        const val DEFAULT_HEADSUP_GLASS_ENABLED = true
        const val DEFAULT_HEADSUP_ALPHA = 76
        const val DEFAULT_HEADSUP_TINT_OPACITY = 55 // 55%
        const val DEFAULT_HEADSUP_BLUR_ENABLED = true
        const val DEFAULT_HEADSUP_BLUR_RADIUS = 35 // 30-45 px
        const val DEFAULT_HEADSUP_TINT_HEX = "#0D111A"

        // Defaults F
        const val DEFAULT_TILE_OPACITY = 70
        const val DEFAULT_QS_TILE_BLUR_ENABLED = true
        const val DEFAULT_QS_TILE_BLUR_RADIUS = 25
        const val DEFAULT_ACTIVE_TILE_ALPHA = 90
        const val DEFAULT_INACTIVE_TILE_ALPHA = 60
        const val DEFAULT_DISABLED_TILE_ALPHA = 40
        const val DEFAULT_TILE_TINT_HEX = "#0D111A"
        const val DEFAULT_TILE_CORNER_OVERRIDE = 0

        // Defaults G
        const val DEFAULT_QS_ICON_ACTIVE_PERCENT = 65 // Translucent icon badges on tiles
        const val DEFAULT_QS_ICON_INACTIVE_PERCENT = 50
        const val DEFAULT_QS_ICON_DISABLED_PERCENT = 40
        const val DEFAULT_NOTIF_ICON_PERCENT = 65 // Translucent notification icon badges
        const val DEFAULT_TEXT_ALPHA_PERCENT = 100 // Crisp 100% text

        // Defaults I (Volume)
        const val DEFAULT_VOLUME_GLASS_ENABLED = true
        const val DEFAULT_VOLUME_SLIDER_ALPHA_PERCENT = 50
        const val DEFAULT_VOLUME_BUTTONS_ALPHA_PERCENT = 50
        const val DEFAULT_VOLUME_BLUR_RADIUS = 30

        // Defaults H
        const val DEFAULT_BLUR_QUALITY_PRESET = 0 // 0=High Quality, 1=Balanced
        const val DEFAULT_PERFORMANCE_MODE = 0 // Quality
        const val DEFAULT_LIMIT_BLUR_UPDATES = false
        const val DEFAULT_REDUCE_RERENDERS = true
        const val DEFAULT_CACHE_RESOURCES = true
        const val DEFAULT_DISABLE_PER_CARD_FALLBACK = true

        const val WALLPAPER_MONET = "@wallpaper_monet"

        private fun safeParseColor(colorStr: String, fallback: String): Int {
            if (colorStr == WALLPAPER_MONET) {
                // Android's Monet tonal palette is generated from the selected wallpaper.
                val resources = android.content.res.Resources.getSystem()
                val id = resources.getIdentifier("system_accent2_900", "color", "android")
                if (id != 0) return resources.getColor(id, null)
            }
            return try {
                Color.parseColor(colorStr)
            } catch (_: Throwable) {
                Color.parseColor(fallback)
            }
        }

        fun getStablePreset(): GlassConfig {
            return GlassConfig(
                enabled = true,
                safeMode = false,
                debugLogging = false,
                debugOverlayBounds = false,

                blurRadius = DEFAULT_BLUR_RADIUS,
                shadeOpacityPercent = DEFAULT_SHADE_OPACITY,
                tintHex = DEFAULT_TINT_HEX,
                qsBackgroundTintOpacityPercent = DEFAULT_QS_BACKGROUND_TINT_OPACITY,
                qsExpandedAlphaPercent = DEFAULT_QS_EXPANDED_ALPHA,
                qsCollapsedAlphaPercent = DEFAULT_QS_COLLAPSED_ALPHA,
                animationEnabled = true,
                animationIntensityPercent = 100,

                notifScrimEnabled = true,
                notifScrimOpacityPercent = 28,
                notifScrimTintHex = DEFAULT_NOTIF_SCRIM_TINT_HEX,
                notifScrimGradientEnabled = true,
                notifScrimGradientStrengthPercent = 50,

                notifGlassEnabled = true,
                notifCardAlphaPercent = 50,
                notifCardTintOpacityPercent = 20,
                notifCardTintHex = DEFAULT_NOTIF_CARD_TINT_HEX,
                notifCardBlurEnabled = false,
                notifCardBlurRadius = DEFAULT_NOTIF_CARD_BLUR_RADIUS,
                notifCardCornerRadiusDp = DEFAULT_NOTIF_CARD_CORNER_RADIUS_DP,
                unifiedNotifBackdropBlur = true,
                notifCornerTreatment = 0,
                notifBorderEnabled = false,
                notifBorderAlphaPercent = 0,

                headsUpGlassEnabled = true,
                headsUpAlphaPercent = 76,
                headsUpTintOpacityPercent = DEFAULT_HEADSUP_TINT_OPACITY,
                headsUpBlurEnabled = true,
                headsUpBlurRadius = DEFAULT_HEADSUP_BLUR_RADIUS,
                headsUpTintHex = DEFAULT_HEADSUP_TINT_HEX,

                tileOpacityPercent = 70,
                qsTileBlurEnabled = true,
                qsTileBlurRadius = 25,
                activeTileAlphaPercent = 90,
                inactiveTileAlphaPercent = 60,
                disabledTileAlphaPercent = 40,
                tileTintHex = DEFAULT_TILE_TINT_HEX,
                tileCornerRadiusOverrideDp = 0,

                qsIconAlphaActivePercent = 65,
                qsIconAlphaInactivePercent = 50,
                qsIconAlphaDisabledPercent = 40,
                notifIconAlphaPercent = 65,
                textAlphaPercent = 100,

                volumeGlassEnabled = true,
                volumeSliderAlphaPercent = 50,
                volumeButtonsAlphaPercent = 50,
                volumeBlurRadius = 30,

                blurQualityPreset = 0,
                performanceMode = 0,
                limitBlurUpdates = false,
                reduceRerenders = true,
                cacheResources = true,
                disablePerCardBlurFallback = true
            )
        }
    }
}
