package com.nothing.glass.config

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import de.robv.android.xposed.XSharedPreferences
import de.robv.android.xposed.XposedBridge
import java.io.File

object ConfigManager {
    private const val TAG = "NothingGlass.Config"

    @Volatile
    var current = GlassConfig()
        private set

    private var xPrefs: XSharedPreferences? = null
    private var isReceiverRegistered = false

    fun initXSharedPreferences() {
        try {
            xPrefs = XSharedPreferences("com.nothing.glass", GlassConfig.PREFS_NAME).apply {
                makeWorldReadable()
                reload()
            }
            loadFromXPrefs()
        } catch (t: Throwable) {
            XposedBridge.log("$TAG: Failed to load XSharedPreferences: ${t.message}")
        }
    }

    private fun loadFromXPrefs() {
        val xp = xPrefs ?: return
        xp.reload()
        current = GlassConfig(
            // A
            enabled = xp.getBoolean(GlassConfig.KEY_ENABLED, GlassConfig.DEFAULT_ENABLED),
            safeMode = xp.getBoolean(GlassConfig.KEY_SAFE_MODE, GlassConfig.DEFAULT_SAFE_MODE),
            debugLogging = xp.getBoolean(GlassConfig.KEY_DEBUG_LOGGING, GlassConfig.DEFAULT_DEBUG_LOGGING),
            debugOverlayBounds = xp.getBoolean(GlassConfig.KEY_DEBUG_OVERLAY_BOUNDS, GlassConfig.DEFAULT_DEBUG_OVERLAY_BOUNDS),

            // B
            blurRadius = xp.getInt(GlassConfig.KEY_BLUR_RADIUS, GlassConfig.DEFAULT_BLUR_RADIUS),
            shadeOpacityPercent = xp.getInt(GlassConfig.KEY_SHADE_OPACITY, GlassConfig.DEFAULT_SHADE_OPACITY),
            tintHex = xp.getString(GlassConfig.KEY_TINT_HEX, GlassConfig.DEFAULT_TINT_HEX) ?: GlassConfig.DEFAULT_TINT_HEX,
            qsBackgroundTintOpacityPercent = xp.getInt(GlassConfig.KEY_QS_BACKGROUND_TINT_OPACITY, GlassConfig.DEFAULT_QS_BACKGROUND_TINT_OPACITY),
            qsExpandedAlphaPercent = xp.getInt(GlassConfig.KEY_QS_EXPANDED_ALPHA, GlassConfig.DEFAULT_QS_EXPANDED_ALPHA),
            qsCollapsedAlphaPercent = xp.getInt(GlassConfig.KEY_QS_COLLAPSED_ALPHA, GlassConfig.DEFAULT_QS_COLLAPSED_ALPHA),
            animationEnabled = xp.getBoolean(GlassConfig.KEY_ANIMATION_ENABLED, GlassConfig.DEFAULT_ANIMATION_ENABLED),
            animationIntensityPercent = xp.getInt(GlassConfig.KEY_ANIMATION_INTENSITY, GlassConfig.DEFAULT_ANIMATION_INTENSITY),

            // C
            notifScrimEnabled = xp.getBoolean(GlassConfig.KEY_NOTIF_SCRIM_ENABLED, GlassConfig.DEFAULT_NOTIF_SCRIM_ENABLED),
            notifScrimOpacityPercent = xp.getInt(GlassConfig.KEY_NOTIF_SCRIM_OPACITY, GlassConfig.DEFAULT_NOTIF_SCRIM_OPACITY),
            notifScrimTintHex = xp.getString(GlassConfig.KEY_NOTIF_SCRIM_TINT, GlassConfig.DEFAULT_NOTIF_SCRIM_TINT_HEX) ?: GlassConfig.DEFAULT_NOTIF_SCRIM_TINT_HEX,
            notifScrimGradientEnabled = xp.getBoolean(GlassConfig.KEY_NOTIF_SCRIM_GRADIENT_ENABLED, GlassConfig.DEFAULT_NOTIF_SCRIM_GRADIENT_ENABLED),
            notifScrimGradientStrengthPercent = xp.getInt(GlassConfig.KEY_NOTIF_SCRIM_GRADIENT_STRENGTH, GlassConfig.DEFAULT_NOTIF_SCRIM_GRADIENT_STRENGTH),

            // D
            notifGlassEnabled = xp.getBoolean(GlassConfig.KEY_NOTIF_GLASS_ENABLED, GlassConfig.DEFAULT_NOTIF_GLASS_ENABLED),
            notifCardAlphaPercent = xp.getInt(GlassConfig.KEY_NOTIF_CARD_ALPHA, GlassConfig.DEFAULT_NOTIF_CARD_ALPHA),
            notifCardTintOpacityPercent = xp.getInt(GlassConfig.KEY_NOTIF_CARD_TINT_OPACITY, GlassConfig.DEFAULT_NOTIF_CARD_TINT_OPACITY),
            notifCardTintHex = xp.getString(GlassConfig.KEY_NOTIF_CARD_TINT, GlassConfig.DEFAULT_NOTIF_CARD_TINT_HEX) ?: GlassConfig.DEFAULT_NOTIF_CARD_TINT_HEX,
            notifCardBlurEnabled = xp.getBoolean(GlassConfig.KEY_NOTIF_CARD_BLUR_ENABLED, GlassConfig.DEFAULT_NOTIF_CARD_BLUR_ENABLED),
            notifCardBlurRadius = xp.getInt(GlassConfig.KEY_NOTIF_CARD_BLUR_RADIUS, GlassConfig.DEFAULT_NOTIF_CARD_BLUR_RADIUS),
            notifCardCornerRadiusDp = xp.getInt(GlassConfig.KEY_NOTIF_CARD_CORNER_RADIUS, GlassConfig.DEFAULT_NOTIF_CARD_CORNER_RADIUS_DP),
            unifiedNotifBackdropBlur = xp.getBoolean(GlassConfig.KEY_UNIFIED_NOTIF_BACKDROP_BLUR, GlassConfig.DEFAULT_UNIFIED_NOTIF_BACKDROP_BLUR),
            notifCornerTreatment = xp.getInt(GlassConfig.KEY_NOTIF_CORNER_TREATMENT, GlassConfig.DEFAULT_NOTIF_CORNER_TREATMENT),
            notifBorderEnabled = xp.getBoolean(GlassConfig.KEY_NOTIF_BORDER_ENABLED, GlassConfig.DEFAULT_NOTIF_BORDER_ENABLED),
            notifBorderAlphaPercent = xp.getInt(GlassConfig.KEY_NOTIF_BORDER_ALPHA, GlassConfig.DEFAULT_NOTIF_BORDER_ALPHA),

            // E
            headsUpGlassEnabled = xp.getBoolean(GlassConfig.KEY_HEADSUP_GLASS_ENABLED, GlassConfig.DEFAULT_HEADSUP_GLASS_ENABLED),
            headsUpAlphaPercent = xp.getInt(GlassConfig.KEY_HEADSUP_ALPHA, GlassConfig.DEFAULT_HEADSUP_ALPHA),
            headsUpTintOpacityPercent = xp.getInt(GlassConfig.KEY_HEADSUP_TINT_OPACITY, GlassConfig.DEFAULT_HEADSUP_TINT_OPACITY),
            headsUpBlurEnabled = xp.getBoolean(GlassConfig.KEY_HEADSUP_BLUR_ENABLED, GlassConfig.DEFAULT_HEADSUP_BLUR_ENABLED),
            headsUpBlurRadius = xp.getInt(GlassConfig.KEY_HEADSUP_BLUR_RADIUS, GlassConfig.DEFAULT_HEADSUP_BLUR_RADIUS),
            headsUpTintHex = xp.getString(GlassConfig.KEY_HEADSUP_TINT, GlassConfig.DEFAULT_HEADSUP_TINT_HEX) ?: GlassConfig.DEFAULT_HEADSUP_TINT_HEX,

            // F
            tileOpacityPercent = xp.getInt(GlassConfig.KEY_TILE_OPACITY, GlassConfig.DEFAULT_TILE_OPACITY),
            qsTileBlurEnabled = xp.getBoolean(GlassConfig.KEY_QS_TILE_BLUR_ENABLED, GlassConfig.DEFAULT_QS_TILE_BLUR_ENABLED),
            qsTileBlurRadius = xp.getInt(GlassConfig.KEY_QS_TILE_BLUR_RADIUS, GlassConfig.DEFAULT_QS_TILE_BLUR_RADIUS),
            activeTileAlphaPercent = xp.getInt(GlassConfig.KEY_ACTIVE_TILE_ALPHA, GlassConfig.DEFAULT_ACTIVE_TILE_ALPHA),
            inactiveTileAlphaPercent = xp.getInt(GlassConfig.KEY_INACTIVE_TILE_ALPHA, GlassConfig.DEFAULT_INACTIVE_TILE_ALPHA),
            disabledTileAlphaPercent = xp.getInt(GlassConfig.KEY_DISABLED_TILE_ALPHA, GlassConfig.DEFAULT_DISABLED_TILE_ALPHA),
            tileTintHex = xp.getString(GlassConfig.KEY_TILE_TINT, GlassConfig.DEFAULT_TILE_TINT_HEX) ?: GlassConfig.DEFAULT_TILE_TINT_HEX,
            tileCornerRadiusOverrideDp = xp.getInt(GlassConfig.KEY_TILE_CORNER_OVERRIDE, GlassConfig.DEFAULT_TILE_CORNER_OVERRIDE),

            // G
            qsIconAlphaActivePercent = xp.getInt(GlassConfig.KEY_QS_ICON_ACTIVE, GlassConfig.DEFAULT_QS_ICON_ACTIVE_PERCENT),
            qsIconAlphaInactivePercent = xp.getInt(GlassConfig.KEY_QS_ICON_INACTIVE, GlassConfig.DEFAULT_QS_ICON_INACTIVE_PERCENT),
            qsIconAlphaDisabledPercent = xp.getInt(GlassConfig.KEY_QS_ICON_DISABLED, GlassConfig.DEFAULT_QS_ICON_DISABLED_PERCENT),
            notifIconAlphaPercent = xp.getInt(GlassConfig.KEY_NOTIF_ICON, GlassConfig.DEFAULT_NOTIF_ICON_PERCENT),
            textAlphaPercent = xp.getInt(GlassConfig.KEY_TEXT_ALPHA, GlassConfig.DEFAULT_TEXT_ALPHA_PERCENT),

            // I (Volume)
            volumeGlassEnabled = xp.getBoolean(GlassConfig.KEY_VOLUME_GLASS_ENABLED, GlassConfig.DEFAULT_VOLUME_GLASS_ENABLED),
            volumeSliderAlphaPercent = xp.getInt(GlassConfig.KEY_VOLUME_SLIDER_ALPHA, GlassConfig.DEFAULT_VOLUME_SLIDER_ALPHA_PERCENT),
            volumeButtonsAlphaPercent = xp.getInt(GlassConfig.KEY_VOLUME_BUTTONS_ALPHA, GlassConfig.DEFAULT_VOLUME_BUTTONS_ALPHA_PERCENT),
            volumeBlurRadius = xp.getInt(GlassConfig.KEY_VOLUME_BLUR_RADIUS, GlassConfig.DEFAULT_VOLUME_BLUR_RADIUS),

            // H
            blurQualityPreset = xp.getInt(GlassConfig.KEY_BLUR_QUALITY_PRESET, GlassConfig.DEFAULT_BLUR_QUALITY_PRESET),
            performanceMode = xp.getInt(GlassConfig.KEY_PERFORMANCE_MODE, GlassConfig.DEFAULT_PERFORMANCE_MODE),
            limitBlurUpdates = xp.getBoolean(GlassConfig.KEY_LIMIT_BLUR_UPDATES, GlassConfig.DEFAULT_LIMIT_BLUR_UPDATES),
            reduceRerenders = xp.getBoolean(GlassConfig.KEY_REDUCE_RERENDERS, GlassConfig.DEFAULT_REDUCE_RERENDERS),
            cacheResources = xp.getBoolean(GlassConfig.KEY_CACHE_RESOURCES, GlassConfig.DEFAULT_CACHE_RESOURCES),
            disablePerCardBlurFallback = xp.getBoolean(GlassConfig.KEY_DISABLE_PER_CARD_FALLBACK, GlassConfig.DEFAULT_DISABLE_PER_CARD_FALLBACK)
        )
        checkEmergencyKillSwitch()
    }

    fun syncFromProvider(context: Context) {
        try {
            val uri = Uri.parse("content://${GlassConfig.AUTHORITY}")
            val bundle = context.contentResolver.call(uri, GlassConfigProvider.METHOD_GET_CONFIG, null, null)
            if (bundle != null) {
                applyBundle(bundle)
                if (current.debugLogging) {
                    XposedBridge.log("$TAG: Config synced from provider: $current")
                }
                return
            }
        } catch (t: Throwable) {
            XposedBridge.log("$TAG: Provider sync failed, falling back to XSharedPreferences: ${t.message}")
        }
        loadFromXPrefs()
    }

    fun applyBundle(b: Bundle) {
        current = GlassConfig(
            // A
            enabled = b.getBoolean(GlassConfig.KEY_ENABLED, current.enabled),
            safeMode = b.getBoolean(GlassConfig.KEY_SAFE_MODE, current.safeMode),
            debugLogging = b.getBoolean(GlassConfig.KEY_DEBUG_LOGGING, current.debugLogging),
            debugOverlayBounds = b.getBoolean(GlassConfig.KEY_DEBUG_OVERLAY_BOUNDS, current.debugOverlayBounds),

            // B
            blurRadius = b.getInt(GlassConfig.KEY_BLUR_RADIUS, current.blurRadius),
            shadeOpacityPercent = b.getInt(GlassConfig.KEY_SHADE_OPACITY, current.shadeOpacityPercent),
            tintHex = b.getString(GlassConfig.KEY_TINT_HEX, current.tintHex) ?: current.tintHex,
            qsBackgroundTintOpacityPercent = b.getInt(GlassConfig.KEY_QS_BACKGROUND_TINT_OPACITY, current.qsBackgroundTintOpacityPercent),
            qsExpandedAlphaPercent = b.getInt(GlassConfig.KEY_QS_EXPANDED_ALPHA, current.qsExpandedAlphaPercent),
            qsCollapsedAlphaPercent = b.getInt(GlassConfig.KEY_QS_COLLAPSED_ALPHA, current.qsCollapsedAlphaPercent),
            animationEnabled = b.getBoolean(GlassConfig.KEY_ANIMATION_ENABLED, current.animationEnabled),
            animationIntensityPercent = b.getInt(GlassConfig.KEY_ANIMATION_INTENSITY, current.animationIntensityPercent),

            // C
            notifScrimEnabled = b.getBoolean(GlassConfig.KEY_NOTIF_SCRIM_ENABLED, current.notifScrimEnabled),
            notifScrimOpacityPercent = b.getInt(GlassConfig.KEY_NOTIF_SCRIM_OPACITY, current.notifScrimOpacityPercent),
            notifScrimTintHex = b.getString(GlassConfig.KEY_NOTIF_SCRIM_TINT, current.notifScrimTintHex) ?: current.notifScrimTintHex,
            notifScrimGradientEnabled = b.getBoolean(GlassConfig.KEY_NOTIF_SCRIM_GRADIENT_ENABLED, current.notifScrimGradientEnabled),
            notifScrimGradientStrengthPercent = b.getInt(GlassConfig.KEY_NOTIF_SCRIM_GRADIENT_STRENGTH, current.notifScrimGradientStrengthPercent),

            // D
            notifGlassEnabled = b.getBoolean(GlassConfig.KEY_NOTIF_GLASS_ENABLED, current.notifGlassEnabled),
            notifCardAlphaPercent = b.getInt(GlassConfig.KEY_NOTIF_CARD_ALPHA, current.notifCardAlphaPercent),
            notifCardTintOpacityPercent = b.getInt(GlassConfig.KEY_NOTIF_CARD_TINT_OPACITY, current.notifCardTintOpacityPercent),
            notifCardTintHex = b.getString(GlassConfig.KEY_NOTIF_CARD_TINT, current.notifCardTintHex) ?: current.notifCardTintHex,
            notifCardBlurEnabled = b.getBoolean(GlassConfig.KEY_NOTIF_CARD_BLUR_ENABLED, current.notifCardBlurEnabled),
            notifCardBlurRadius = b.getInt(GlassConfig.KEY_NOTIF_CARD_BLUR_RADIUS, current.notifCardBlurRadius),
            notifCardCornerRadiusDp = b.getInt(GlassConfig.KEY_NOTIF_CARD_CORNER_RADIUS, current.notifCardCornerRadiusDp),
            unifiedNotifBackdropBlur = b.getBoolean(GlassConfig.KEY_UNIFIED_NOTIF_BACKDROP_BLUR, current.unifiedNotifBackdropBlur),
            notifCornerTreatment = b.getInt(GlassConfig.KEY_NOTIF_CORNER_TREATMENT, current.notifCornerTreatment),
            notifBorderEnabled = b.getBoolean(GlassConfig.KEY_NOTIF_BORDER_ENABLED, current.notifBorderEnabled),
            notifBorderAlphaPercent = b.getInt(GlassConfig.KEY_NOTIF_BORDER_ALPHA, current.notifBorderAlphaPercent),

            // E
            headsUpGlassEnabled = b.getBoolean(GlassConfig.KEY_HEADSUP_GLASS_ENABLED, current.headsUpGlassEnabled),
            headsUpAlphaPercent = b.getInt(GlassConfig.KEY_HEADSUP_ALPHA, current.headsUpAlphaPercent),
            headsUpTintOpacityPercent = b.getInt(GlassConfig.KEY_HEADSUP_TINT_OPACITY, current.headsUpTintOpacityPercent),
            headsUpBlurEnabled = b.getBoolean(GlassConfig.KEY_HEADSUP_BLUR_ENABLED, current.headsUpBlurEnabled),
            headsUpBlurRadius = b.getInt(GlassConfig.KEY_HEADSUP_BLUR_RADIUS, current.headsUpBlurRadius),
            headsUpTintHex = b.getString(GlassConfig.KEY_HEADSUP_TINT, current.headsUpTintHex) ?: current.headsUpTintHex,

            // F
            tileOpacityPercent = b.getInt(GlassConfig.KEY_TILE_OPACITY, current.tileOpacityPercent),
            qsTileBlurEnabled = b.getBoolean(GlassConfig.KEY_QS_TILE_BLUR_ENABLED, current.qsTileBlurEnabled),
            qsTileBlurRadius = b.getInt(GlassConfig.KEY_QS_TILE_BLUR_RADIUS, current.qsTileBlurRadius),
            activeTileAlphaPercent = b.getInt(GlassConfig.KEY_ACTIVE_TILE_ALPHA, current.activeTileAlphaPercent),
            inactiveTileAlphaPercent = b.getInt(GlassConfig.KEY_INACTIVE_TILE_ALPHA, current.inactiveTileAlphaPercent),
            disabledTileAlphaPercent = b.getInt(GlassConfig.KEY_DISABLED_TILE_ALPHA, current.disabledTileAlphaPercent),
            tileTintHex = b.getString(GlassConfig.KEY_TILE_TINT, current.tileTintHex) ?: current.tileTintHex,
            tileCornerRadiusOverrideDp = b.getInt(GlassConfig.KEY_TILE_CORNER_OVERRIDE, current.tileCornerRadiusOverrideDp),

            // G
            qsIconAlphaActivePercent = b.getInt(GlassConfig.KEY_QS_ICON_ACTIVE, current.qsIconAlphaActivePercent),
            qsIconAlphaInactivePercent = b.getInt(GlassConfig.KEY_QS_ICON_INACTIVE, current.qsIconAlphaInactivePercent),
            qsIconAlphaDisabledPercent = b.getInt(GlassConfig.KEY_QS_ICON_DISABLED, current.qsIconAlphaDisabledPercent),
            notifIconAlphaPercent = b.getInt(GlassConfig.KEY_NOTIF_ICON, current.notifIconAlphaPercent),
            textAlphaPercent = b.getInt(GlassConfig.KEY_TEXT_ALPHA, current.textAlphaPercent),

            // I (Volume)
            volumeGlassEnabled = b.getBoolean(GlassConfig.KEY_VOLUME_GLASS_ENABLED, current.volumeGlassEnabled),
            volumeSliderAlphaPercent = b.getInt(GlassConfig.KEY_VOLUME_SLIDER_ALPHA, current.volumeSliderAlphaPercent),
            volumeButtonsAlphaPercent = b.getInt(GlassConfig.KEY_VOLUME_BUTTONS_ALPHA, current.volumeButtonsAlphaPercent),
            volumeBlurRadius = b.getInt(GlassConfig.KEY_VOLUME_BLUR_RADIUS, current.volumeBlurRadius),

            // H
            blurQualityPreset = b.getInt(GlassConfig.KEY_BLUR_QUALITY_PRESET, current.blurQualityPreset),
            performanceMode = b.getInt(GlassConfig.KEY_PERFORMANCE_MODE, current.performanceMode),
            limitBlurUpdates = b.getBoolean(GlassConfig.KEY_LIMIT_BLUR_UPDATES, current.limitBlurUpdates),
            reduceRerenders = b.getBoolean(GlassConfig.KEY_REDUCE_RERENDERS, current.reduceRerenders),
            cacheResources = b.getBoolean(GlassConfig.KEY_CACHE_RESOURCES, current.cacheResources),
            disablePerCardBlurFallback = b.getBoolean(GlassConfig.KEY_DISABLE_PER_CARD_FALLBACK, current.disablePerCardBlurFallback)
        )
        checkEmergencyKillSwitch()
    }

    fun registerBroadcastReceiver(context: Context) {
        if (isReceiverRegistered) return
        try {
            val filter = IntentFilter(GlassConfig.ACTION_CONFIG_CHANGED)
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(ctx: Context?, intent: Intent?) {
                    val extras = intent?.extras
                    if (extras != null) {
                        applyBundle(extras)
                        if (current.debugLogging) {
                            XposedBridge.log("$TAG: Live update applied: $current")
                        }
                    }
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                context.registerReceiver(receiver, filter)
            }
            isReceiverRegistered = true
            XposedBridge.log("$TAG: BroadcastReceiver registered successfully for live updates")
        } catch (t: Throwable) {
            XposedBridge.log("$TAG: Failed to register BroadcastReceiver: ${t.message}")
        }
    }

    private fun checkEmergencyKillSwitch() {
        try {
            if (File(GlassConfig.DISABLE_FLAG_FILE).exists()) {
                current = current.copy(enabled = false)
                XposedBridge.log("$TAG: Emergency killswitch file detected at ${GlassConfig.DISABLE_FLAG_FILE}. Module disabled.")
            }
        } catch (_: Throwable) {}
    }
}
