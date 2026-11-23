package com.nothing.glass.hooks

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.view.View
import com.nothing.glass.HookEntry
import com.nothing.glass.config.ConfigManager
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import java.lang.reflect.Field

object ScrimViewHooks {

    // Cached Reflection Fields
    private var mScrimNameField: Field? = null
    private var mDrawableField: Field? = null
    private var mPaintField: Field? = null

    private val shadeShader = FrostedBackdrop.TintShader()
    private val notificationShader = FrostedBackdrop.TintShader()
    private const val PAINT_STATE = "nothingGlassTranslucentPaint"

    private class PaintState(val paint: Paint, val shader: Shader?)

    fun install(classLoader: ClassLoader) {
        initReflection(classLoader)
        hookScrimView(classLoader)
    }

    private fun initReflection(classLoader: ClassLoader) {
        val scrimViewClass = XposedHelpers.findClassIfExists("com.android.systemui.scrim.ScrimView", classLoader)
        if (scrimViewClass != null) {
            try { mScrimNameField = scrimViewClass.getDeclaredField("mScrimName").apply { isAccessible = true } } catch (_: Throwable) {}
            try { mDrawableField = scrimViewClass.getDeclaredField("mDrawable").apply { isAccessible = true } } catch (_: Throwable) {}
        }

        val scrimDrawableClass = XposedHelpers.findClassIfExists("com.android.systemui.scrim.ScrimDrawable", classLoader)
        if (scrimDrawableClass != null) {
            try { mPaintField = scrimDrawableClass.getDeclaredField("mPaint").apply { isAccessible = true } } catch (_: Throwable) {}
        }
    }

    private fun hookScrimView(classLoader: ClassLoader) {
        val scrimViewClass = XposedHelpers.findClassIfExists("com.android.systemui.scrim.ScrimView", classLoader) ?: return

        // 1. Hook setViewAlpha(float f)
        try {
            XposedHelpers.findAndHookMethod(
                scrimViewClass,
                "setViewAlpha",
                Float::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val config = ConfigManager.current
                        if (!config.isEffectiveEnabled) return
                        val originalAlpha = param.args[0] as Float
                        val scrimName = mScrimNameField?.get(param.thisObject) as? String

                        val targetMaxAlpha = when (scrimName) {
                            "notifications_scrim" -> if (config.notifScrimEnabled) config.notifScrimAlpha else config.shadeAlpha
                            "behind_scrim" -> QuickSettingsBackdrop.alpha(config)
                            else -> return
                        }
                        if (originalAlpha > targetMaxAlpha) param.args[0] = targetMaxAlpha
                    }
                }
            )
            HookEntry.log("[+] Hooked ScrimView.setViewAlpha(float)")
        } catch (t: Throwable) {
            HookEntry.logError("[-] Error hooking ScrimView.setViewAlpha", t)
        }

        // 2. Hook setTint(int i, boolean z)
        try {
            XposedHelpers.findAndHookMethod(
                scrimViewClass,
                "setTint",
                Int::class.javaPrimitiveType,
                Boolean::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val config = ConfigManager.current
                        if (!config.isEffectiveEnabled) return
                        val scrimName = mScrimNameField?.get(param.thisObject) as? String

                        if (scrimName == "notifications_scrim") {
                            val view = param.thisObject as? View
                            param.args[0] = if (config.notifScrimEnabled) FrostedBackdrop.notificationScrimTint(view?.context, config)
                                else FrostedBackdrop.tint(view?.context, config)
                        } else if (scrimName == "behind_scrim") {
                            val view = param.thisObject as? View
                            param.args[0] = FrostedBackdrop.quickSettingsTint(view?.context, config)
                        }
                    }
                }
            )
            HookEntry.log("[+] Hooked ScrimView.setTint(int, boolean)")
        } catch (t: Throwable) {
            HookEntry.logError("[-] Error hooking ScrimView.setTint", t)
        }

        // 3. Hook onDraw(Canvas canvas) on ScrimView
        try {
            XposedHelpers.findAndHookMethod(
                scrimViewClass,
                "onDraw",
                Canvas::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val config = ConfigManager.current
                        if (!config.isEffectiveEnabled) return
                        try {
                            val view = param.thisObject as? View ?: return
                            val scrimName = mScrimNameField?.get(view) as? String
                            val drawable = mDrawableField?.get(view) as? Drawable ?: return
                            val paint = mPaintField?.get(drawable) as? Paint ?: return
                            val canvas = param.args[0] as? Canvas ?: return
                            val bounds = drawable.bounds
                            if (bounds.height() <= 1 || drawable.alpha == 0) return

                            val shader = when (scrimName) {
                                "behind_scrim" -> shadeShader.get(
                                    bounds, FrostedBackdrop.quickSettingsTint(view.context, config),
                                    0.8f, canvas.isHardwareAccelerated, config.qsMaterial
                                )
                                "notifications_scrim" -> {
                                    if (!config.notifScrimEnabled || !config.notifScrimGradientEnabled) return
                                    notificationShader.get(
                                        bounds, FrostedBackdrop.notificationScrimTint(view.context, config),
                                        config.notifScrimGradientStrength, canvas.isHardwareAccelerated
                                    )
                                }
                                else -> return
                            }
                            param.setObjectExtra(PAINT_STATE, PaintState(paint, paint.shader))
                            paint.shader = shader
                        } catch (_: Throwable) {}
                    }

                    override fun afterHookedMethod(param: MethodHookParam) {
                        val state = param.getObjectExtra(PAINT_STATE) as? PaintState ?: return
                        state.paint.shader = state.shader
                    }
                }
            )
            HookEntry.log("[+] Hooked ScrimView.onDraw(Canvas)")
        } catch (t: Throwable) {
            HookEntry.logError("[-] Error hooking ScrimView.onDraw", t)
        }
    }
}
