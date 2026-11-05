package com.nothing.glass.config

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Bundle

class GlassConfigProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        val ctx = context ?: return null
        val prefs = ctx.getSharedPreferences(GlassConfig.PREFS_NAME, Context.MODE_PRIVATE)

        return when (method) {
            METHOD_GET_CONFIG -> {
                Bundle().apply {
                    // A. General
                    putBoolean(GlassConfig.KEY_ENABLED, prefs.getBoolean(GlassConfig.KEY_ENABLED, GlassConfig.DEFAULT_ENABLED))
                    putBoolean(GlassConfig.KEY_SAFE_MODE, prefs.getBoolean(GlassConfig.KEY_SAFE_MODE, GlassConfig.DEFAULT_SAFE_MODE))
                    putBoolean(GlassConfig.KEY_DEBUG_LOGGING, prefs.getBoolean(GlassConfig.KEY_DEBUG_LOGGING, GlassConfig.DEFAULT_DEBUG_LOGGING))
                    putBoolean(GlassConfig.KEY_DEBUG_OVERLAY_BOUNDS, prefs.getBoolean(GlassConfig.KEY_DEBUG_OVERLAY_BOUNDS, GlassConfig.DEFAULT_DEBUG_OVERLAY_BOUNDS))

                    // B. Main Shade
                    putInt(GlassConfig.KEY_BLUR_RADIUS, prefs.getInt(GlassConfig.KEY_BLUR_RADIUS, GlassConfig.DEFAULT_BLUR_RADIUS))
                    putInt(GlassConfig.KEY_SHADE_OPACITY, prefs.getInt(GlassConfig.KEY_SHADE_OPACITY, GlassConfig.DEFAULT_SHADE_OPACITY))
                    putString(GlassConfig.KEY_TINT_HEX, prefs.getString(GlassConfig.KEY_TINT_HEX, GlassConfig.DEFAULT_TINT_HEX))
                    putInt(GlassConfig.KEY_QS_BACKGROUND_TINT_OPACITY, prefs.getInt(GlassConfig.KEY_QS_BACKGROUND_TINT_OPACITY, GlassConfig.DEFAULT_QS_BACKGROUND_TINT_OPACITY))
                    putInt(GlassConfig.KEY_QS_EXPANDED_ALPHA, prefs.getInt(GlassConfig.KEY_QS_EXPANDED_ALPHA, GlassConfig.DEFAULT_QS_EXPANDED_ALPHA))
                    putInt(GlassConfig.KEY_QS_COLLAPSED_ALPHA, prefs.getInt(GlassConfig.KEY_QS_COLLAPSED_ALPHA, GlassConfig.DEFAULT_QS_COLLAPSED_ALPHA))
                    putBoolean(GlassConfig.KEY_ANIMATION_ENABLED, prefs.getBoolean(GlassConfig.KEY_ANIMATION_ENABLED, GlassConfig.DEFAULT_ANIMATION_ENABLED))
                    putInt(GlassConfig.KEY_ANIMATION_INTENSITY, prefs.getInt(GlassConfig.KEY_ANIMATION_INTENSITY, GlassConfig.DEFAULT_ANIMATION_INTENSITY))

                    // C. Notification Area
                    putBoolean(GlassConfig.KEY_NOTIF_SCRIM_ENABLED, prefs.getBoolean(GlassConfig.KEY_NOTIF_SCRIM_ENABLED, GlassConfig.DEFAULT_NOTIF_SCRIM_ENABLED))
                    putInt(GlassConfig.KEY_NOTIF_SCRIM_OPACITY, prefs.getInt(GlassConfig.KEY_NOTIF_SCRIM_OPACITY, GlassConfig.DEFAULT_NOTIF_SCRIM_OPACITY))
                    putString(GlassConfig.KEY_NOTIF_SCRIM_TINT, prefs.getString(GlassConfig.KEY_NOTIF_SCRIM_TINT, GlassConfig.DEFAULT_NOTIF_SCRIM_TINT_HEX))
                    putBoolean(GlassConfig.KEY_NOTIF_SCRIM_GRADIENT_ENABLED, prefs.getBoolean(GlassConfig.KEY_NOTIF_SCRIM_GRADIENT_ENABLED, GlassConfig.DEFAULT_NOTIF_SCRIM_GRADIENT_ENABLED))
                    putInt(GlassConfig.KEY_NOTIF_SCRIM_GRADIENT_STRENGTH, prefs.getInt(GlassConfig.KEY_NOTIF_SCRIM_GRADIENT_STRENGTH, GlassConfig.DEFAULT_NOTIF_SCRIM_GRADIENT_STRENGTH))

                    // D. Notification Cards
                    putBoolean(GlassConfig.KEY_NOTIF_GLASS_ENABLED, prefs.getBoolean(GlassConfig.KEY_NOTIF_GLASS_ENABLED, GlassConfig.DEFAULT_NOTIF_GLASS_ENABLED))
                    putInt(GlassConfig.KEY_NOTIF_CARD_ALPHA, prefs.getInt(GlassConfig.KEY_NOTIF_CARD_ALPHA, GlassConfig.DEFAULT_NOTIF_CARD_ALPHA))
                    putInt(GlassConfig.KEY_NOTIF_CARD_TINT_OPACITY, prefs.getInt(GlassConfig.KEY_NOTIF_CARD_TINT_OPACITY, GlassConfig.DEFAULT_NOTIF_CARD_TINT_OPACITY))
                    putString(GlassConfig.KEY_NOTIF_CARD_TINT, prefs.getString(GlassConfig.KEY_NOTIF_CARD_TINT, GlassConfig.DEFAULT_NOTIF_CARD_TINT_HEX))
                    putBoolean(GlassConfig.KEY_NOTIF_CARD_BLUR_ENABLED, prefs.getBoolean(GlassConfig.KEY_NOTIF_CARD_BLUR_ENABLED, GlassConfig.DEFAULT_NOTIF_CARD_BLUR_ENABLED))
                    putInt(GlassConfig.KEY_NOTIF_CARD_BLUR_RADIUS, prefs.getInt(GlassConfig.KEY_NOTIF_CARD_BLUR_RADIUS, GlassConfig.DEFAULT_NOTIF_CARD_BLUR_RADIUS))
                    putInt(GlassConfig.KEY_NOTIF_CARD_CORNER_RADIUS, prefs.getInt(GlassConfig.KEY_NOTIF_CARD_CORNER_RADIUS, GlassConfig.DEFAULT_NOTIF_CARD_CORNER_RADIUS_DP))
                    putBoolean(GlassConfig.KEY_UNIFIED_NOTIF_BACKDROP_BLUR, prefs.getBoolean(GlassConfig.KEY_UNIFIED_NOTIF_BACKDROP_BLUR, GlassConfig.DEFAULT_UNIFIED_NOTIF_BACKDROP_BLUR))
                    putInt(GlassConfig.KEY_NOTIF_CORNER_TREATMENT, prefs.getInt(GlassConfig.KEY_NOTIF_CORNER_TREATMENT, GlassConfig.DEFAULT_NOTIF_CORNER_TREATMENT))
                    putBoolean(GlassConfig.KEY_NOTIF_BORDER_ENABLED, prefs.getBoolean(GlassConfig.KEY_NOTIF_BORDER_ENABLED, GlassConfig.DEFAULT_NOTIF_BORDER_ENABLED))
                    putInt(GlassConfig.KEY_NOTIF_BORDER_ALPHA, prefs.getInt(GlassConfig.KEY_NOTIF_BORDER_ALPHA, GlassConfig.DEFAULT_NOTIF_BORDER_ALPHA))

                    // E. Heads-up Notifications
                    putBoolean(GlassConfig.KEY_HEADSUP_GLASS_ENABLED, prefs.getBoolean(GlassConfig.KEY_HEADSUP_GLASS_ENABLED, GlassConfig.DEFAULT_HEADSUP_GLASS_ENABLED))
                    putInt(GlassConfig.KEY_HEADSUP_ALPHA, prefs.getInt(GlassConfig.KEY_HEADSUP_ALPHA, GlassConfig.DEFAULT_HEADSUP_ALPHA))
                    putInt(GlassConfig.KEY_HEADSUP_TINT_OPACITY, prefs.getInt(GlassConfig.KEY_HEADSUP_TINT_OPACITY, GlassConfig.DEFAULT_HEADSUP_TINT_OPACITY))
                    putBoolean(GlassConfig.KEY_HEADSUP_BLUR_ENABLED, prefs.getBoolean(GlassConfig.KEY_HEADSUP_BLUR_ENABLED, GlassConfig.DEFAULT_HEADSUP_BLUR_ENABLED))
                    putInt(GlassConfig.KEY_HEADSUP_BLUR_RADIUS, prefs.getInt(GlassConfig.KEY_HEADSUP_BLUR_RADIUS, GlassConfig.DEFAULT_HEADSUP_BLUR_RADIUS))
                    putString(GlassConfig.KEY_HEADSUP_TINT, prefs.getString(GlassConfig.KEY_HEADSUP_TINT, GlassConfig.DEFAULT_HEADSUP_TINT_HEX))

                    // F. QS Tiles
                    putInt(GlassConfig.KEY_TILE_OPACITY, prefs.getInt(GlassConfig.KEY_TILE_OPACITY, GlassConfig.DEFAULT_TILE_OPACITY))
                    putBoolean(GlassConfig.KEY_QS_TILE_BLUR_ENABLED, prefs.getBoolean(GlassConfig.KEY_QS_TILE_BLUR_ENABLED, GlassConfig.DEFAULT_QS_TILE_BLUR_ENABLED))
                    putInt(GlassConfig.KEY_QS_TILE_BLUR_RADIUS, prefs.getInt(GlassConfig.KEY_QS_TILE_BLUR_RADIUS, GlassConfig.DEFAULT_QS_TILE_BLUR_RADIUS))
                    putInt(GlassConfig.KEY_ACTIVE_TILE_ALPHA, prefs.getInt(GlassConfig.KEY_ACTIVE_TILE_ALPHA, GlassConfig.DEFAULT_ACTIVE_TILE_ALPHA))
                    putInt(GlassConfig.KEY_INACTIVE_TILE_ALPHA, prefs.getInt(GlassConfig.KEY_INACTIVE_TILE_ALPHA, GlassConfig.DEFAULT_INACTIVE_TILE_ALPHA))
                    putInt(GlassConfig.KEY_DISABLED_TILE_ALPHA, prefs.getInt(GlassConfig.KEY_DISABLED_TILE_ALPHA, GlassConfig.DEFAULT_DISABLED_TILE_ALPHA))
                    putString(GlassConfig.KEY_TILE_TINT, prefs.getString(GlassConfig.KEY_TILE_TINT, GlassConfig.DEFAULT_TILE_TINT_HEX))
                    putInt(GlassConfig.KEY_TILE_CORNER_OVERRIDE, prefs.getInt(GlassConfig.KEY_TILE_CORNER_OVERRIDE, GlassConfig.DEFAULT_TILE_CORNER_OVERRIDE))

                    // G. Icons & Text
                    putInt(GlassConfig.KEY_QS_ICON_ACTIVE, prefs.getInt(GlassConfig.KEY_QS_ICON_ACTIVE, GlassConfig.DEFAULT_QS_ICON_ACTIVE_PERCENT))
                    putInt(GlassConfig.KEY_QS_ICON_INACTIVE, prefs.getInt(GlassConfig.KEY_QS_ICON_INACTIVE, GlassConfig.DEFAULT_QS_ICON_INACTIVE_PERCENT))
                    putInt(GlassConfig.KEY_QS_ICON_DISABLED, prefs.getInt(GlassConfig.KEY_QS_ICON_DISABLED, GlassConfig.DEFAULT_QS_ICON_DISABLED_PERCENT))
                    putInt(GlassConfig.KEY_NOTIF_ICON, prefs.getInt(GlassConfig.KEY_NOTIF_ICON, GlassConfig.DEFAULT_NOTIF_ICON_PERCENT))
                    putInt(GlassConfig.KEY_TEXT_ALPHA, prefs.getInt(GlassConfig.KEY_TEXT_ALPHA, GlassConfig.DEFAULT_TEXT_ALPHA_PERCENT))

                    // H. Performance
                    putInt(GlassConfig.KEY_BLUR_QUALITY_PRESET, prefs.getInt(GlassConfig.KEY_BLUR_QUALITY_PRESET, GlassConfig.DEFAULT_BLUR_QUALITY_PRESET))
                    putInt(GlassConfig.KEY_PERFORMANCE_MODE, prefs.getInt(GlassConfig.KEY_PERFORMANCE_MODE, GlassConfig.DEFAULT_PERFORMANCE_MODE))
                    putBoolean(GlassConfig.KEY_LIMIT_BLUR_UPDATES, prefs.getBoolean(GlassConfig.KEY_LIMIT_BLUR_UPDATES, GlassConfig.DEFAULT_LIMIT_BLUR_UPDATES))
                    putBoolean(GlassConfig.KEY_REDUCE_RERENDERS, prefs.getBoolean(GlassConfig.KEY_REDUCE_RERENDERS, GlassConfig.DEFAULT_REDUCE_RERENDERS))
                    putBoolean(GlassConfig.KEY_CACHE_RESOURCES, prefs.getBoolean(GlassConfig.KEY_CACHE_RESOURCES, GlassConfig.DEFAULT_CACHE_RESOURCES))
                    putBoolean(GlassConfig.KEY_DISABLE_PER_CARD_FALLBACK, prefs.getBoolean(GlassConfig.KEY_DISABLE_PER_CARD_FALLBACK, GlassConfig.DEFAULT_DISABLE_PER_CARD_FALLBACK))
                }
            }
            METHOD_SET_CONFIG -> {
                extras?.let { b ->
                    prefs.edit().apply {
                        // A
                        if (b.containsKey(GlassConfig.KEY_ENABLED)) putBoolean(GlassConfig.KEY_ENABLED, b.getBoolean(GlassConfig.KEY_ENABLED))
                        if (b.containsKey(GlassConfig.KEY_SAFE_MODE)) putBoolean(GlassConfig.KEY_SAFE_MODE, b.getBoolean(GlassConfig.KEY_SAFE_MODE))
                        if (b.containsKey(GlassConfig.KEY_DEBUG_LOGGING)) putBoolean(GlassConfig.KEY_DEBUG_LOGGING, b.getBoolean(GlassConfig.KEY_DEBUG_LOGGING))
                        if (b.containsKey(GlassConfig.KEY_DEBUG_OVERLAY_BOUNDS)) putBoolean(GlassConfig.KEY_DEBUG_OVERLAY_BOUNDS, b.getBoolean(GlassConfig.KEY_DEBUG_OVERLAY_BOUNDS))

                        // B
                        if (b.containsKey(GlassConfig.KEY_BLUR_RADIUS)) putInt(GlassConfig.KEY_BLUR_RADIUS, b.getInt(GlassConfig.KEY_BLUR_RADIUS))
                        if (b.containsKey(GlassConfig.KEY_SHADE_OPACITY)) putInt(GlassConfig.KEY_SHADE_OPACITY, b.getInt(GlassConfig.KEY_SHADE_OPACITY))
                        if (b.containsKey(GlassConfig.KEY_TINT_HEX)) putString(GlassConfig.KEY_TINT_HEX, b.getString(GlassConfig.KEY_TINT_HEX))
                        if (b.containsKey(GlassConfig.KEY_QS_BACKGROUND_TINT_OPACITY)) putInt(GlassConfig.KEY_QS_BACKGROUND_TINT_OPACITY, b.getInt(GlassConfig.KEY_QS_BACKGROUND_TINT_OPACITY))
                        if (b.containsKey(GlassConfig.KEY_QS_EXPANDED_ALPHA)) putInt(GlassConfig.KEY_QS_EXPANDED_ALPHA, b.getInt(GlassConfig.KEY_QS_EXPANDED_ALPHA))
                        if (b.containsKey(GlassConfig.KEY_QS_COLLAPSED_ALPHA)) putInt(GlassConfig.KEY_QS_COLLAPSED_ALPHA, b.getInt(GlassConfig.KEY_QS_COLLAPSED_ALPHA))
                        if (b.containsKey(GlassConfig.KEY_ANIMATION_ENABLED)) putBoolean(GlassConfig.KEY_ANIMATION_ENABLED, b.getBoolean(GlassConfig.KEY_ANIMATION_ENABLED))
                        if (b.containsKey(GlassConfig.KEY_ANIMATION_INTENSITY)) putInt(GlassConfig.KEY_ANIMATION_INTENSITY, b.getInt(GlassConfig.KEY_ANIMATION_INTENSITY))

                        // C
                        if (b.containsKey(GlassConfig.KEY_NOTIF_SCRIM_ENABLED)) putBoolean(GlassConfig.KEY_NOTIF_SCRIM_ENABLED, b.getBoolean(GlassConfig.KEY_NOTIF_SCRIM_ENABLED))
                        if (b.containsKey(GlassConfig.KEY_NOTIF_SCRIM_OPACITY)) putInt(GlassConfig.KEY_NOTIF_SCRIM_OPACITY, b.getInt(GlassConfig.KEY_NOTIF_SCRIM_OPACITY))
                        if (b.containsKey(GlassConfig.KEY_NOTIF_SCRIM_TINT)) putString(GlassConfig.KEY_NOTIF_SCRIM_TINT, b.getString(GlassConfig.KEY_NOTIF_SCRIM_TINT))
                        if (b.containsKey(GlassConfig.KEY_NOTIF_SCRIM_GRADIENT_ENABLED)) putBoolean(GlassConfig.KEY_NOTIF_SCRIM_GRADIENT_ENABLED, b.getBoolean(GlassConfig.KEY_NOTIF_SCRIM_GRADIENT_ENABLED))
                        if (b.containsKey(GlassConfig.KEY_NOTIF_SCRIM_GRADIENT_STRENGTH)) putInt(GlassConfig.KEY_NOTIF_SCRIM_GRADIENT_STRENGTH, b.getInt(GlassConfig.KEY_NOTIF_SCRIM_GRADIENT_STRENGTH))

                        // D
                        if (b.containsKey(GlassConfig.KEY_NOTIF_GLASS_ENABLED)) putBoolean(GlassConfig.KEY_NOTIF_GLASS_ENABLED, b.getBoolean(GlassConfig.KEY_NOTIF_GLASS_ENABLED))
                        if (b.containsKey(GlassConfig.KEY_NOTIF_CARD_ALPHA)) putInt(GlassConfig.KEY_NOTIF_CARD_ALPHA, b.getInt(GlassConfig.KEY_NOTIF_CARD_ALPHA))
                        if (b.containsKey(GlassConfig.KEY_NOTIF_CARD_TINT_OPACITY)) putInt(GlassConfig.KEY_NOTIF_CARD_TINT_OPACITY, b.getInt(GlassConfig.KEY_NOTIF_CARD_TINT_OPACITY))
                        if (b.containsKey(GlassConfig.KEY_NOTIF_CARD_TINT)) putString(GlassConfig.KEY_NOTIF_CARD_TINT, b.getString(GlassConfig.KEY_NOTIF_CARD_TINT))
                        if (b.containsKey(GlassConfig.KEY_NOTIF_CARD_BLUR_ENABLED)) putBoolean(GlassConfig.KEY_NOTIF_CARD_BLUR_ENABLED, b.getBoolean(GlassConfig.KEY_NOTIF_CARD_BLUR_ENABLED))
                        if (b.containsKey(GlassConfig.KEY_NOTIF_CARD_BLUR_RADIUS)) putInt(GlassConfig.KEY_NOTIF_CARD_BLUR_RADIUS, b.getInt(GlassConfig.KEY_NOTIF_CARD_BLUR_RADIUS))
                        if (b.containsKey(GlassConfig.KEY_NOTIF_CARD_CORNER_RADIUS)) putInt(GlassConfig.KEY_NOTIF_CARD_CORNER_RADIUS, b.getInt(GlassConfig.KEY_NOTIF_CARD_CORNER_RADIUS))
                        if (b.containsKey(GlassConfig.KEY_UNIFIED_NOTIF_BACKDROP_BLUR)) putBoolean(GlassConfig.KEY_UNIFIED_NOTIF_BACKDROP_BLUR, b.getBoolean(GlassConfig.KEY_UNIFIED_NOTIF_BACKDROP_BLUR))
                        if (b.containsKey(GlassConfig.KEY_NOTIF_CORNER_TREATMENT)) putInt(GlassConfig.KEY_NOTIF_CORNER_TREATMENT, b.getInt(GlassConfig.KEY_NOTIF_CORNER_TREATMENT))
                        if (b.containsKey(GlassConfig.KEY_NOTIF_BORDER_ENABLED)) putBoolean(GlassConfig.KEY_NOTIF_BORDER_ENABLED, b.getBoolean(GlassConfig.KEY_NOTIF_BORDER_ENABLED))
                        if (b.containsKey(GlassConfig.KEY_NOTIF_BORDER_ALPHA)) putInt(GlassConfig.KEY_NOTIF_BORDER_ALPHA, b.getInt(GlassConfig.KEY_NOTIF_BORDER_ALPHA))

                        // E
                        if (b.containsKey(GlassConfig.KEY_HEADSUP_GLASS_ENABLED)) putBoolean(GlassConfig.KEY_HEADSUP_GLASS_ENABLED, b.getBoolean(GlassConfig.KEY_HEADSUP_GLASS_ENABLED))
                        if (b.containsKey(GlassConfig.KEY_HEADSUP_ALPHA)) putInt(GlassConfig.KEY_HEADSUP_ALPHA, b.getInt(GlassConfig.KEY_HEADSUP_ALPHA))
                        if (b.containsKey(GlassConfig.KEY_HEADSUP_TINT_OPACITY)) putInt(GlassConfig.KEY_HEADSUP_TINT_OPACITY, b.getInt(GlassConfig.KEY_HEADSUP_TINT_OPACITY))
                        if (b.containsKey(GlassConfig.KEY_HEADSUP_BLUR_ENABLED)) putBoolean(GlassConfig.KEY_HEADSUP_BLUR_ENABLED, b.getBoolean(GlassConfig.KEY_HEADSUP_BLUR_ENABLED))
                        if (b.containsKey(GlassConfig.KEY_HEADSUP_BLUR_RADIUS)) putInt(GlassConfig.KEY_HEADSUP_BLUR_RADIUS, b.getInt(GlassConfig.KEY_HEADSUP_BLUR_RADIUS))
                        if (b.containsKey(GlassConfig.KEY_HEADSUP_TINT)) putString(GlassConfig.KEY_HEADSUP_TINT, b.getString(GlassConfig.KEY_HEADSUP_TINT))

                        // F
                        if (b.containsKey(GlassConfig.KEY_TILE_OPACITY)) putInt(GlassConfig.KEY_TILE_OPACITY, b.getInt(GlassConfig.KEY_TILE_OPACITY))
                        if (b.containsKey(GlassConfig.KEY_QS_TILE_BLUR_ENABLED)) putBoolean(GlassConfig.KEY_QS_TILE_BLUR_ENABLED, b.getBoolean(GlassConfig.KEY_QS_TILE_BLUR_ENABLED))
                        if (b.containsKey(GlassConfig.KEY_QS_TILE_BLUR_RADIUS)) putInt(GlassConfig.KEY_QS_TILE_BLUR_RADIUS, b.getInt(GlassConfig.KEY_QS_TILE_BLUR_RADIUS))
                        if (b.containsKey(GlassConfig.KEY_ACTIVE_TILE_ALPHA)) putInt(GlassConfig.KEY_ACTIVE_TILE_ALPHA, b.getInt(GlassConfig.KEY_ACTIVE_TILE_ALPHA))
                        if (b.containsKey(GlassConfig.KEY_INACTIVE_TILE_ALPHA)) putInt(GlassConfig.KEY_INACTIVE_TILE_ALPHA, b.getInt(GlassConfig.KEY_INACTIVE_TILE_ALPHA))
                        if (b.containsKey(GlassConfig.KEY_DISABLED_TILE_ALPHA)) putInt(GlassConfig.KEY_DISABLED_TILE_ALPHA, b.getInt(GlassConfig.KEY_DISABLED_TILE_ALPHA))
                        if (b.containsKey(GlassConfig.KEY_TILE_TINT)) putString(GlassConfig.KEY_TILE_TINT, b.getString(GlassConfig.KEY_TILE_TINT))
                        if (b.containsKey(GlassConfig.KEY_TILE_CORNER_OVERRIDE)) putInt(GlassConfig.KEY_TILE_CORNER_OVERRIDE, b.getInt(GlassConfig.KEY_TILE_CORNER_OVERRIDE))

                        // G
                        if (b.containsKey(GlassConfig.KEY_QS_ICON_ACTIVE)) putInt(GlassConfig.KEY_QS_ICON_ACTIVE, b.getInt(GlassConfig.KEY_QS_ICON_ACTIVE))
                        if (b.containsKey(GlassConfig.KEY_QS_ICON_INACTIVE)) putInt(GlassConfig.KEY_QS_ICON_INACTIVE, b.getInt(GlassConfig.KEY_QS_ICON_INACTIVE))
                        if (b.containsKey(GlassConfig.KEY_QS_ICON_DISABLED)) putInt(GlassConfig.KEY_QS_ICON_DISABLED, b.getInt(GlassConfig.KEY_QS_ICON_DISABLED))
                        if (b.containsKey(GlassConfig.KEY_NOTIF_ICON)) putInt(GlassConfig.KEY_NOTIF_ICON, b.getInt(GlassConfig.KEY_NOTIF_ICON))
                        if (b.containsKey(GlassConfig.KEY_TEXT_ALPHA)) putInt(GlassConfig.KEY_TEXT_ALPHA, b.getInt(GlassConfig.KEY_TEXT_ALPHA))

                        // H
                        if (b.containsKey(GlassConfig.KEY_BLUR_QUALITY_PRESET)) putInt(GlassConfig.KEY_BLUR_QUALITY_PRESET, b.getInt(GlassConfig.KEY_BLUR_QUALITY_PRESET))
                        if (b.containsKey(GlassConfig.KEY_PERFORMANCE_MODE)) putInt(GlassConfig.KEY_PERFORMANCE_MODE, b.getInt(GlassConfig.KEY_PERFORMANCE_MODE))
                        if (b.containsKey(GlassConfig.KEY_LIMIT_BLUR_UPDATES)) putBoolean(GlassConfig.KEY_LIMIT_BLUR_UPDATES, b.getBoolean(GlassConfig.KEY_LIMIT_BLUR_UPDATES))
                        if (b.containsKey(GlassConfig.KEY_REDUCE_RERENDERS)) putBoolean(GlassConfig.KEY_REDUCE_RERENDERS, b.getBoolean(GlassConfig.KEY_REDUCE_RERENDERS))
                        if (b.containsKey(GlassConfig.KEY_CACHE_RESOURCES)) putBoolean(GlassConfig.KEY_CACHE_RESOURCES, b.getBoolean(GlassConfig.KEY_CACHE_RESOURCES))
                        if (b.containsKey(GlassConfig.KEY_DISABLE_PER_CARD_FALLBACK)) putBoolean(GlassConfig.KEY_DISABLE_PER_CARD_FALLBACK, b.getBoolean(GlassConfig.KEY_DISABLE_PER_CARD_FALLBACK))

                        apply()
                    }
                }
                Bundle().apply { putBoolean("success", true) }
            }
            else -> null
        }
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0

    companion object {
        const val METHOD_GET_CONFIG = "getConfig"
        const val METHOD_SET_CONFIG = "setConfig"
    }
}
