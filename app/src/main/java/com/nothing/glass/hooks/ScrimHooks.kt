package com.nothing.glass.hooks

import android.content.Context
import android.view.View
import androidx.core.graphics.ColorUtils
import com.nothing.glass.HookEntry
import com.nothing.glass.config.ConfigManager
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import java.lang.reflect.Field
import kotlin.math.roundToInt

object ScrimHooks {

    private var mStateField: Field? = null
    private var mPanelExpansionField: Field? = null
    private var mQsExpansionField: Field? = null
    private var mTransitionToFullShadeProgressField: Field? = null
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
        hookOverlayBackdrop(classLoader)
    }

    private fun initReflection(classLoader: ClassLoader) {
        val scrimControllerClass = XposedHelpers.findClassIfExists(
            "com.android.systemui.statusbar.phone.ScrimController",
            classLoader
        ) ?: return

        try { mStateField = scrimControllerClass.getDeclaredField("mState").apply { isAccessible = true } } catch (_: Throwable) {}
        try { mPanelExpansionField = scrimControllerClass.getDeclaredField("mPanelExpansionFraction").apply { isAccessible = true } } catch (_: Throwable) {}
        try { mQsExpansionField = scrimControllerClass.getDeclaredField("mQsExpansion").apply { isAccessible = true } } catch (_: Throwable) {}
        try { mTransitionToFullShadeProgressField = scrimControllerClass.getDeclaredField("mTransitionToFullShadeProgress").apply { isAccessible = true } } catch (_: Throwable) {}
        try { mBehindAlphaField = scrimControllerClass.getDeclaredField("mBehindAlpha").apply { isAccessible = true } } catch (_: Throwable) {}
        try { mBehindTintField = scrimControllerClass.getDeclaredField("mBehindTint").apply { isAccessible = true } } catch (_: Throwable) {}
        try { mNotificationsAlphaField = scrimControllerClass.getDeclaredField("mNotificationsAlpha").apply { isAccessible = true } } catch (_: Throwable) {}
        try { mNotificationsTintField = scrimControllerClass.getDeclaredField("mNotificationsTint").apply { isAccessible = true } } catch (_: Throwable) {}
        try { mClipsQsScrimField = scrimControllerClass.getDeclaredField("mClipsQsScrim").apply { isAccessible = true } } catch (_: Throwable) {}
        try { mScrimBehindField = scrimControllerClass.getDeclaredField("mScrimBehind").apply { isAccessible = true } } catch (_: Throwable) {}
        try { mNotificationsScrimField = scrimControllerClass.getDeclaredField("mNotificationsScrim").apply { isAccessible = true } } catch (_: Throwable) {}
    }

    internal fun isIdleKeyguard(controller: Any): Boolean {
        val state = mStateField?.get(controller) as? Enum<*>
        if (state?.name != "KEYGUARD") return false
        val qsExpansion = mQsExpansionField?.getFloat(controller) ?: QuickSettingsBackdrop.expansion
        val shadeTransition = mTransitionToFullShadeProgressField?.getFloat(controller) ?: 0f
        return qsExpansion <= 0f && shadeTransition <= 0f
    }

    private fun hookScrimController(classLoader: ClassLoader) {
        val scrimControllerClass = XposedHelpers.findClassIfExists(
            "com.android.systemui.statusbar.phone.ScrimController",
            classLoader
        ) ?: return

        XposedBridge.hookAllMethods(scrimControllerClass, "setQsPosition", object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                (param.args.firstOrNull() as? Float)?.let(QuickSettingsBackdrop::updateExpansion)
            }
        })

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
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val controller = param.thisObject
                        QuickSettingsBackdrop.track(controller, mScrimBehindField?.get(controller) as? View)
                    }

                    override fun afterHookedMethod(param: MethodHookParam) {
                        val config = ConfigManager.current
                        if (!config.isEffectiveEnabled) return

                        val scrimController = param.thisObject
                        try {
                            if (isIdleKeyguard(scrimController)) return
                            val state = mStateField?.get(scrimController)
                            val stateName = state?.toString() ?: ""

                            if (stateName.contains("UNLOCKED") || stateName.contains("SHADE") || stateName.contains("KEYGUARD")) {
                                val panelExpansion = mPanelExpansionField?.getFloat(scrimController) ?: 1f
                                val behindAlpha = QuickSettingsBackdrop.ease(panelExpansion)
                                val scrimBehind = mScrimBehindField?.get(scrimController) as? View
                                val behindTint = FrostedBackdrop.quickSettingsTint(scrimBehind?.context, config)

                                val notifAlpha = panelExpansion.coerceIn(0.0f, 1.0f)
                                val notifTint = if (config.notifScrimEnabled) {
                                    FrostedBackdrop.notificationScrimTint(scrimBehind?.context, config)
                                } else behindTint

                                mBehindAlphaField?.setFloat(scrimController, behindAlpha)
                                mBehindTintField?.setInt(scrimController, behindTint)
                                mNotificationsAlphaField?.setFloat(scrimController, notifAlpha)
                                mNotificationsTintField?.setInt(scrimController, notifTint)
                                mClipsQsScrimField?.setBoolean(scrimController, false)

                                val scrimNotif = mNotificationsScrimField?.get(scrimController) as? View
                                val notifTarget = if (config.notifScrimEnabled) config.notifScrimAlpha else config.shadeAlpha
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
                        if (!config.isEffectiveEnabled || isIdleKeyguard(param.thisObject)) return

                        val scrimView = param.args[0] as? View ?: return
                        val scrimName = try {
                            XposedHelpers.getObjectField(scrimView, "mScrimName") as? String
                        } catch (_: Throwable) { null }

                        if (scrimName == "front_scrim") return

                        val originalAlpha = param.args[1] as Float

                        if (scrimName == "notifications_scrim") {
                            val ratio = if (config.notifScrimEnabled) config.notifScrimAlpha else config.shadeAlpha
                            val notifTint = if (config.notifScrimEnabled) FrostedBackdrop.notificationScrimTint(scrimView.context, config)
                                else FrostedBackdrop.tint(scrimView.context, config)
                            param.args[1] = (originalAlpha * ratio).coerceIn(0.0f, 1.0f)
                            param.args[2] = notifTint
                        } else if (scrimName == "behind_scrim") {
                            param.args[1] = (originalAlpha * QuickSettingsBackdrop.alpha(config)).coerceIn(0f, 1f)
                            param.args[2] = FrostedBackdrop.quickSettingsTint(scrimView.context, config)
                        }
                    }
                }
            )
            HookEntry.log("[+] Hooked ScrimController.updateScrimColor()")
        } catch (t: Throwable) {
            HookEntry.logError("[-] Error hooking ScrimController.updateScrimColor", t)
        }
    }

    private fun hookOverlayBackdrop(classLoader: ClassLoader) {
        val colors = XposedHelpers.findClassIfExists(
            "com.android.systemui.shade.ui.composable.OverlayShade\$Colors", classLoader
        ) ?: return
        for (method in colors.declaredMethods) {
            if (!method.name.startsWith("panelBackgroundOpaque-") || method.returnType != Long::class.javaPrimitiveType) continue
            XposedBridge.hookMethod(method, object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val config = ConfigManager.current
                    if (!config.isEffectiveEnabled) return
                    val packed = param.result as? Long ?: return
                    if (packed.toInt() != 0) return
                    // The dual-shade backdrop forces alpha to 1 even when the compositor is still blurring.
                    val alpha = (QuickSettingsBackdrop.alpha(config) * 255f).roundToInt()
                    val argb = ColorUtils.setAlphaComponent((packed ushr 32).toInt(), alpha)
                    param.result = argb.toLong() shl 32
                }
            })
            HookEntry.log("[+] Hooked the dual-shade backdrop alpha")
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
            if (method.returnType != Int::class.javaPrimitiveType) continue
            when (method.name) {
                "shadePanel", "shadePanelScrimBehind", "shadePanelStandard", "shadePanelFallback" -> {
                    try {
                        XposedBridge.hookMethod(method, object : XC_MethodReplacement() {
                            override fun replaceHookedMethod(param: MethodHookParam): Any {
                                val config = ConfigManager.current
                                if (!config.isEffectiveEnabled || QuickSettingsBackdrop.isIdleKeyguard()) {
                                    return XposedBridge.invokeOriginalMethod(param.method, param.thisObject, param.args)
                                }
                                val targetAlpha = (QuickSettingsBackdrop.alpha(config) * 255).toInt()
                                val context = param.args.firstOrNull() as? Context
                                return ColorUtils.setAlphaComponent(FrostedBackdrop.quickSettingsTint(context, config), targetAlpha)
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
                                if (!config.isEffectiveEnabled || QuickSettingsBackdrop.isIdleKeyguard()) {
                                    return XposedBridge.invokeOriginalMethod(param.method, param.thisObject, param.args)
                                }
                                val targetAlpha = if (config.notifScrimEnabled) {
                                    (config.notifScrimAlpha * 255).toInt().coerceIn(0, 255)
                                } else {
                                    (config.shadeAlpha * 255).toInt().coerceIn(0, 255)
                                }
                                val targetTint = if (config.notifScrimEnabled) {
                                    FrostedBackdrop.notificationScrimTint(param.args.firstOrNull() as? Context, config)
                                } else {
                                    FrostedBackdrop.tint(param.args.firstOrNull() as? Context, config)
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
