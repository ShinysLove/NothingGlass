package com.nothing.glass.hooks

import android.app.AndroidAppHelper
import android.app.WallpaperManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import com.nothing.glass.HookEntry
import com.nothing.glass.config.ConfigManager
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import java.util.WeakHashMap

internal object LargeTileHooks {
    private const val TILE_SIZE = "com.nothing.systemui.qs.workspace.workspace.TileSize"
    private const val COMPOSER = "androidx.compose.runtime.Composer"
    private val scopes = WeakHashMap<Any, Unit>()
    private var wallpaperInitialized = false
    private var wallpaperAccent: Int? = null

    fun install(classLoader: ClassLoader) {
        val shell = XposedHelpers.findClassIfExists(
            "com.nothing.systemui.qs.workspace.workspace.QSTileOuterShellKt", classLoader
        ) ?: return
        val draw = shell.declaredMethods.firstOrNull { method ->
            method.name.startsWith("QSTileOuterShell_") &&
                method.parameterTypes.getOrNull(1) == Long::class.javaPrimitiveType &&
                method.parameterTypes.any { it.name == TILE_SIZE } &&
                method.parameterTypes.getOrNull(0) == Float::class.javaPrimitiveType
        } ?: return
        val sizeIndex = draw.parameterTypes.indexOfFirst { it.name == TILE_SIZE }
        val composerIndex = draw.parameterTypes.indexOfFirst { it.name == COMPOSER }
        if (composerIndex < 0) return

        val composer = draw.parameterTypes[composerIndex]
        val getScope = composer.methods.firstOrNull { it.name == "getRecomposeScope" && it.parameterCount == 0 }
        val recordScope = composer.methods.firstOrNull { it.name == "recordUsed" && it.parameterCount == 1 }
        XposedBridge.hookMethod(draw, object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                val size = param.args[sizeIndex] as? Enum<*> ?: return
                if (size.name != "MEDIUM" && size.name != "LARGE") return

                if (getScope != null && recordScope != null) {
                    val activeComposer = param.args[composerIndex]
                    runCatching {
                        val scope = getScope.invoke(activeComposer) ?: return@runCatching
                        recordScope.invoke(activeComposer, scope)
                        scopes[scope] = Unit
                    }
                }

                val config = ConfigManager.current
                if (!config.isEffectiveEnabled || !config.largeTileWallpaperEnabled) return
                val packed = param.args[1] as? Long ?: return
                // Compose's sRGB color packs ARGB into the upper 32 bits.
                if (packed.toInt() != 0) return
                val context = AndroidAppHelper.currentApplication() ?: return
                initializeWallpaper(context)
                val tint = FrostedBackdrop.largeTileTint(context, (packed ushr 32).toInt(), wallpaperAccent)
                param.args[1] = tint.toLong() shl 32
            }
        })
        HookEntry.log("[+] Hooked the shared Quick Settings tile background")
    }

    fun refresh() {
        for (scope in scopes.keys.toList()) {
            runCatching { XposedHelpers.callMethod(scope, "invalidate") }
        }
    }

    private fun initializeWallpaper(context: Context) {
        if (wallpaperInitialized) return
        wallpaperInitialized = true
        val wallpaper = context.getSystemService(WallpaperManager::class.java) ?: return
        runCatching {
            wallpaperAccent = wallpaper.getWallpaperColors(WallpaperManager.FLAG_SYSTEM)?.primaryColor?.toArgb()
            wallpaper.addOnColorsChangedListener({ colors, which ->
                if ((which and WallpaperManager.FLAG_SYSTEM) != 0) {
                    wallpaperAccent = colors?.primaryColor?.toArgb()
                    refresh()
                }
            }, Handler(Looper.getMainLooper()))
        }.onFailure { HookEntry.logError("[-] Could not read wallpaper colors for the Quick Settings tiles", it) }
    }
}
