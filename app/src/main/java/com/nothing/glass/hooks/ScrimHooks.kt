package com.nothing.glass.hooks

import android.view.View
import androidx.core.graphics.ColorUtils
import com.nothing.glass.HookEntry
import com.nothing.glass.config.ConfigManager
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import java.lang.reflect.Field

object ScrimHooks {

    private var mStateField: Field? = null
    private var mPanelExpansionField: Field? = null
    private var mBehindAlphaField: Field? = null
    private var mBehindTintField: Field? = null
    private var mNotificationsAlphaField: Field? = null
    private var mNotificationsTintField: Field? = null
    private var mClipsQsScrimField: Field? = null
    private var mScrimBehindField: Field? = null
    private var mNotificationsScrimField: Field? = null

    fun install(classLoader: ClassLoader) {
        initReflection(classLoader)
        hookScrimController(classLoader)
        hookShadeColors(classLoader)
    }

    private fun initReflection(classLoader: ClassLoader) {
        val scrimControllerClass = XposedHelpers.findClassIfExists(
            "com.android.systemui.statusbar.phone.ScrimController",
            classLoader
        ) ?: return

        try { mStateField = scrimControllerClass.getDeclaredField("mState").apply { isAccessible = true } } catch (_: Throwable) {}
        try { mPanelExpansionField = scrimControllerClass.getDeclaredField("mPanelExpansionFraction").apply { isAccessible = true } } catch (_: Throwable) {}
        try { mBehindAlphaField = scrimControllerClass.getDeclaredField("mBehindAlpha").apply { isAccessible = true } } catch (_: Throwable) {}
        try { mBehindTintField = scrimControllerClass.getDeclaredField("mBehindTint").apply { isAccessible = true } } catch (_: Throwable) {}
        try { mNotificationsAlphaField = scrimControllerClass.getDeclaredField("mNotificationsAlpha").apply { isAccessible = true } } catch (_: Throwable) {}
        try { mNotificationsTintField = scrimControllerClass.getDeclaredField("mNotificationsTint").apply { isAccessible = true } } catch (_: Throwable) {}
        try { mClipsQsScrimField = scrimControllerClass.getDeclaredField("mClipsQsScrim").apply { isAccessible = true } } catch (_: Throwable) {}
        try { mScrimBehindField = scrimControllerClass.getDeclaredField("mScrimBehind").apply { isAccessible = true } } catch (_: Throwable) {}
        try { mNotificationsScrimField = scrimControllerClass.getDeclaredField("mNotificationsScrim").apply { isAccessible = true } } catch (_: Throwable) {}
    }

    private fun hookScrimController(classLoader: ClassLoader) {
        val scrimControllerClass = XposedHelpers.findClassIfExists(
            "com.android.systemui.statusbar.phone.ScrimController",
            classLoader
        ) ?: return

        // 1. isBlurCurrentlySupported()
        try {
            XposedHelpers.findAndHookMethod(
                scrimControllerClass,
                "isBlurCurrentlySupported",
                object : XC_MethodReplacement() {
                    override fun replaceHookedMethod(param: MethodHookParam): Any {
                        val isEnabled = ConfigManager.current.isEffectiveEnabled
                        return if (isEnabled) true else {
                            XposedBridge.invokeOriginalMethod(param.method, param.thisObject, param.args)
                        }
                    }
                }
            )
            HookEntry.log("[+] Hooked ScrimController.isBlurCurrentlySupported()")
        } catch (t: Throwable) {
            HookEntry.logError("[-] Error hooking ScrimController.isBlurCurrentlySupported", t)
        }

        // 2. applyState()
        try {
            XposedHelpers.findAndHookMethod(
                scrimControllerClass,
                "applyState",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val config = ConfigManager.current
                        if (!config.isEffectiveEnabled) return

                        val scrimController = param.thisObject
                        try {
                            val state = mStateField?.get(scrimController)
                            val stateName = state?.toString() ?: ""

                            if (stateName.contains("UNLOCKED") || stateName.contains("SHADE") || stateName.contains("KEYGUARD")) {
                                val panelExpansion = mPanelExpansionField?.getFloat(scrimController) ?: 1f
                                val behindAlpha = panelExpansion.coerceIn(0.0f, 1.0f)
                                val behindTint = config.tintColorInt

                                val notifAlpha = panelExpansion.coerceIn(0.0f, 1.0f)
                                val notifTint = if (config.notifScrimEnabled) config.notifScrimTintColorInt else config.tintColorInt

                                mBehindAlphaField?.setFloat(scrimController, behindAlpha)
                                mBehindTintField?.setInt(scrimController, behindTint)
                                mNotificationsAlphaField?.setFloat(scrimController, notifAlpha)
                                mNotificationsTintField?.setInt(scrimController, notifTint)
                                mClipsQsScrimField?.setBoolean(scrimController, false)

                                val scrimBehind = mScrimBehindField?.get(scrimController) as? View
                                scrimBehind?.alpha = (behindAlpha * config.qsBackgroundAlpha).coerceIn(0f, 1f)

                                val scrimNotif = mNotificationsScrimField?.get(scrimController) as? View
                                val notifTarget = if (config.notifScrimEnabled) config.notifScrimAlpha else config.qsBackgroundAlpha
                                scrimNotif?.alpha = (notifAlpha * notifTarget).coerceIn(0f, 1f)
                            }
                        } catch (_: Throwable) {}
                    }
                }
            )
            HookEntry.log("[+] Hooked ScrimController.applyState()")
        } catch (t: Throwable) {
            HookEntry.logError("[-] Error hooking ScrimController.applyState", t)
        }

        // 3. updateScrimColor()
        try {
            XposedHelpers.findAndHookMethod(
                scrimControllerClass,
                "updateScrimColor",
                View::class.java,
                Float::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val config = ConfigManager.current
                        if (!config.isEffectiveEnabled) return

                        val scrimView = param.args[0] as? View ?: return
                        val scrimName = try {
                            XposedHelpers.getObjectField(scrimView, "mScrimName") as? String
                        } catch (_: Throwable) { null }

                        if (scrimName == "front_scrim") return

                        val originalAlpha = param.args[1] as Float

                        if (scrimName == "notifications_scrim") {
                            val ratio = if (config.notifScrimEnabled) config.notifScrimAlpha else config.qsBackgroundAlpha
                            val notifTint = if (config.notifScrimEnabled) config.notifScrimTintColorInt else config.tintColorInt
                            param.args[1] = (originalAlpha * ratio).coerceIn(0.0f, 1.0f)
                            param.args[2] = notifTint
                        } else {
                            param.args[1] = (originalAlpha * config.qsBackgroundAlpha).coerceIn(0.0f, 1.0f)
                            param.args[2] = config.tintColorInt
                        }
                    }
                }
            )
            HookEntry.log("[+] Hooked ScrimController.updateScrimColor()")
        } catch (t: Throwable) {
            HookEntry.logError("[-] Error hooking ScrimController.updateScrimColor", t)
        }
    }

    private fun hookShadeColors(classLoader: ClassLoader) {
        val shadeColorsClass = XposedHelpers.findClassIfExists(
            "com.android.systemui.shade.ui.ShadeColors",
            classLoader
        )
        if (shadeColorsClass == null) {
            HookEntry.log("[-] Class NOT found: com.android.systemui.shade.ui.ShadeColors")
            return
        }
        HookEntry.log("[+] Found class: com.android.systemui.shade.ui.ShadeColors")

        for (method in shadeColorsClass.declaredMethods) {
            when (method.name) {
                "shadePanel", "shadePanelScrimBehind", "shadePanelStandard", "shadePanelFallback" -> {
                    try {
                        XposedBridge.hookMethod(method, object : XC_MethodReplacement() {
                            override fun replaceHookedMethod(param: MethodHookParam): Any {
                                val config = ConfigManager.current
                                if (!config.isEffectiveEnabled) {
                                    return XposedBridge.invokeOriginalMethod(param.method, param.thisObject, param.args)
                                }
                                val targetAlpha = (config.qsBackgroundAlpha * 255).toInt().coerceIn(0, 255)
                                return ColorUtils.setAlphaComponent(config.tintColorInt, targetAlpha)
                            }
                        })
                        HookEntry.log("[+] Hooked ShadeColors.${method.name}() [${method.parameterTypes.joinToString { it.simpleName }}]")
                    } catch (t: Throwable) {
                        HookEntry.logError("[-] Error hooking ShadeColors.${method.name}", t)
                    }
                }
                "notificationScrim", "classicShadeNotificationScrimBg", "notificationScrimStandard", "notificationScrimFallback" -> {
                    try {
                        XposedBridge.hookMethod(method, object : XC_MethodReplacement() {
                            override fun replaceHookedMethod(param: MethodHookParam): Any {
                                val config = ConfigManager.current
                                if (!config.isEffectiveEnabled) {
                                    return XposedBridge.invokeOriginalMethod(param.method, param.thisObject, param.args)
                                }
                                val targetAlpha = if (config.notifScrimEnabled) {
                                    (config.notifScrimAlpha * 255).toInt().coerceIn(0, 255)
                                } else {
                                    (config.qsBackgroundAlpha * 255).toInt().coerceIn(0, 255)
                                }
                                val targetTint = if (config.notifScrimEnabled) {
                                    config.notifScrimTintColorInt
                                } else {
                                    config.tintColorInt
                                }
                                return ColorUtils.setAlphaComponent(targetTint, targetAlpha)
                            }
                        })
                        HookEntry.log("[+] Hooked ShadeColors.${method.name}() [${method.parameterTypes.joinToString { it.simpleName }}]")
                    } catch (t: Throwable) {
                        HookEntry.logError("[-] Error hooking ShadeColors.${method.name}", t)
                    }
                }
            }
        }
    }
}
