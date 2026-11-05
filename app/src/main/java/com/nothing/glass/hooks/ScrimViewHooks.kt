package com.nothing.glass.hooks

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.view.View
import androidx.core.graphics.ColorUtils
import com.nothing.glass.HookEntry
import com.nothing.glass.config.ConfigManager
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import java.lang.reflect.Field

object ScrimViewHooks {

    // Cached Reflection Fields
    private var mScrimNameField: Field? = null
    private var mDrawableField: Field? = null
    private var mAlphaField: Field? = null
    private var mPaintField: Field? = null

    // Cached Shader (Zero-allocation rendering)
    private var cachedGradient: LinearGradient? = null
    private var lastGradientTop = -1
    private var lastGradientHeight = -1
    private var lastColorTop = 0
    private var lastColorBottom = 0

    fun install(classLoader: ClassLoader) {
        initReflection(classLoader)
        hookScrimView(classLoader)
        hookScrimDrawable(classLoader)
    }

    private fun initReflection(classLoader: ClassLoader) {
        val scrimViewClass = XposedHelpers.findClassIfExists("com.android.systemui.scrim.ScrimView", classLoader)
        if (scrimViewClass != null) {
            try { mScrimNameField = scrimViewClass.getDeclaredField("mScrimName").apply { isAccessible = true } } catch (_: Throwable) {}
            try { mDrawableField = scrimViewClass.getDeclaredField("mDrawable").apply { isAccessible = true } } catch (_: Throwable) {}
        }

        val scrimDrawableClass = XposedHelpers.findClassIfExists("com.android.systemui.scrim.ScrimDrawable", classLoader)
        if (scrimDrawableClass != null) {
            try { mAlphaField = scrimDrawableClass.getDeclaredField("mAlpha").apply { isAccessible = true } } catch (_: Throwable) {}
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

                        if (scrimName != "front_scrim") {
                            val targetMaxAlpha = if (scrimName == "notifications_scrim") {
                                if (config.notifScrimEnabled) config.notifScrimAlpha else config.qsBackgroundAlpha
                            } else {
                                config.qsBackgroundAlpha
                            }
                            if (originalAlpha > targetMaxAlpha) {
                                param.args[0] = targetMaxAlpha
                            }
                        }
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
                            param.args[0] = if (config.notifScrimEnabled) config.notifScrimTintColorInt else config.tintColorInt
                        } else if (scrimName != "front_scrim") {
                            param.args[0] = config.tintColorInt
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
                            val isScrimDrawable = mPaintField != null

                            if (scrimName == "notifications_scrim") {
                                val targetAlpha = if (config.notifScrimEnabled) config.notifScrimAlpha else config.qsBackgroundAlpha
                                val maxAlphaInt = (targetAlpha * 255).toInt().coerceIn(0, 255)
                                val tintColor = if (config.notifScrimEnabled) config.notifScrimTintColorInt else config.tintColorInt

                                drawable.alpha = maxAlphaInt
                                if (isScrimDrawable) {
                                    mAlphaField?.setInt(drawable, maxAlphaInt)
                                    val paint = mPaintField?.get(drawable) as? Paint
                                    if (paint != null) {
                                        paint.alpha = maxAlphaInt
                                        val bounds = drawable.bounds
                                        val height = bounds.height()

                                        if (height > 50 && config.notifScrimEnabled && config.notifScrimGradientEnabled) {
                                            // Soft, gentle gradient without harsh horizontal edge
                                            val topAlpha = (maxAlphaInt * 0.85f).toInt().coerceIn(0, 255)
                                            val colorTop = ColorUtils.setAlphaComponent(tintColor, topAlpha)
                                            val colorBottom = ColorUtils.setAlphaComponent(tintColor, maxAlphaInt)

                                            // Recreate shader ONLY if dimensions or colors change
                                            if (cachedGradient == null ||
                                                bounds.top != lastGradientTop ||
                                                height != lastGradientHeight ||
                                                colorTop != lastColorTop ||
                                                colorBottom != lastColorBottom
                                            ) {
                                                lastGradientTop = bounds.top
                                                lastGradientHeight = height
                                                lastColorTop = colorTop
                                                lastColorBottom = colorBottom

                                                cachedGradient = LinearGradient(
                                                    0f, bounds.top.toFloat(),
                                                    0f, bounds.bottom.toFloat(),
                                                    colorTop, colorBottom,
                                                    Shader.TileMode.CLAMP
                                                )
                                            }
                                            paint.shader = cachedGradient
                                        } else {
                                            paint.shader = null
                                            paint.color = tintColor
                                        }
                                    }
                                }
                            }
                        } catch (_: Throwable) {}
                    }
                }
            )
            HookEntry.log("[+] Hooked ScrimView.onDraw(Canvas)")
        } catch (t: Throwable) {
            HookEntry.logError("[-] Error hooking ScrimView.onDraw", t)
        }
    }

    private fun hookScrimDrawable(classLoader: ClassLoader) {
        val scrimDrawableClass = XposedHelpers.findClassIfExists("com.android.systemui.scrim.ScrimDrawable", classLoader) ?: return

        try {
            XposedHelpers.findAndHookMethod(
                scrimDrawableClass,
                "draw",
                Canvas::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val config = ConfigManager.current
                        if (!config.isEffectiveEnabled) return
                        try {
                            val drawable = param.thisObject
                            val currentAlpha = mAlphaField?.getInt(drawable) ?: return
                            val targetMaxAlpha = (config.qsBackgroundAlpha * 255).toInt().coerceIn(0, 255)

                            if (currentAlpha > targetMaxAlpha) {
                                mAlphaField?.setInt(drawable, targetMaxAlpha)
                                val paint = mPaintField?.get(drawable) as? Paint
                                paint?.alpha = targetMaxAlpha
                            }
                        } catch (_: Throwable) {}
                    }
                }
            )
            HookEntry.log("[+] Hooked ScrimDrawable.draw(Canvas)")
        } catch (t: Throwable) {
            HookEntry.logError("[-] Error hooking ScrimDrawable.draw", t)
        }
    }
}
