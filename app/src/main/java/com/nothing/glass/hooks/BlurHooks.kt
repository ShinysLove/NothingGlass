package com.nothing.glass.hooks

import android.util.Pair
import com.nothing.glass.HookEntry
import com.nothing.glass.config.ConfigManager
import com.nothing.glass.config.QuickSettingsMaterial
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import java.lang.reflect.Field

object BlurHooks {

    private var shadeExpansionField: Field? = null
    private var qsPanelExpansionField: Field? = null

    fun install(classLoader: ClassLoader) {
        FrostedBackdrop.bindPalette(classLoader)
        hookBlurUtils(classLoader)
        hookDepthController(classLoader)
        hookWindowRootViewBlurInteractor(classLoader)
    }

    private fun hookBlurUtils(classLoader: ClassLoader) {
        val blurUtilsClass = XposedHelpers.findClassIfExists("com.android.systemui.statusbar.BlurUtils", classLoader)
        if (blurUtilsClass == null) {
            HookEntry.log("[-] Class NOT found: com.android.systemui.statusbar.BlurUtils")
            return
        }
        HookEntry.log("[+] Found class: com.android.systemui.statusbar.BlurUtils")

        // 1. supportsBlursOnWindows()
        try {
            XposedHelpers.findAndHookMethod(
                blurUtilsClass,
                "supportsBlursOnWindows",
                object : XC_MethodReplacement() {
                    override fun replaceHookedMethod(param: MethodHookParam): Any {
                        val isEnabled = ConfigManager.current.isEffectiveEnabled
                        return if (isEnabled) true else {
                            XposedBridge.invokeOriginalMethod(param.method, param.thisObject, param.args)
                        }
                    }
                }
            )
            HookEntry.log("[+] Hooked BlurUtils.supportsBlursOnWindows()")
        } catch (t: Throwable) {
            HookEntry.logError("[-] Error hooking BlurUtils.supportsBlursOnWindows", t)
        }

        // 2. supportsBlursOnWindowsBase()
        try {
            XposedHelpers.findAndHookMethod(
                blurUtilsClass,
                "supportsBlursOnWindowsBase",
                object : XC_MethodReplacement() {
                    override fun replaceHookedMethod(param: MethodHookParam): Any {
                        val isEnabled = ConfigManager.current.isEffectiveEnabled
                        return if (isEnabled) true else {
                            XposedBridge.invokeOriginalMethod(param.method, param.thisObject, param.args)
                        }
                    }
                }
            )
            HookEntry.log("[+] Hooked BlurUtils.supportsBlursOnWindowsBase()")
        } catch (t: Throwable) {
            HookEntry.logError("[-] Error hooking BlurUtils.supportsBlursOnWindowsBase", t)
        }

        // 3. getMaxBlurRadius()
        try {
            XposedHelpers.findAndHookMethod(
                blurUtilsClass,
                "getMaxBlurRadius",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        if (ConfigManager.current.isEffectiveEnabled) {
                            param.result = ConfigManager.current.blurRadius.toFloat()
                        }
                    }
                }
            )
            HookEntry.log("[+] Hooked BlurUtils.getMaxBlurRadius()")
        } catch (t: Throwable) {
            HookEntry.logError("[-] Error hooking BlurUtils.getMaxBlurRadius", t)
        }
    }

    private fun hookDepthController(classLoader: ClassLoader) {
        val depthControllerClass = XposedHelpers.findClassIfExists(
            "com.android.systemui.statusbar.NotificationShadeDepthController",
            classLoader
        )
        if (depthControllerClass == null) {
            HookEntry.log("[-] Class NOT found: com.android.systemui.statusbar.NotificationShadeDepthController")
            return
        }
        HookEntry.log("[+] Found class: com.android.systemui.statusbar.NotificationShadeDepthController")

        XposedBridge.hookAllMethods(depthControllerClass, "setQsPanelExpansion", object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                (param.args.firstOrNull() as? Float)?.let(QuickSettingsBackdrop::updateExpansion)
            }
        })

        // Cache reflection fields for 120Hz performance
        try {
            shadeExpansionField = depthControllerClass.getDeclaredField("shadeExpansion").apply { isAccessible = true }
        } catch (_: Throwable) {}
        try {
            qsPanelExpansionField = depthControllerClass.getDeclaredField("qsPanelExpansion").apply { isAccessible = true }
        } catch (_: Throwable) {}

        // 1. getShouldBlurBeOpaque()
        try {
            XposedHelpers.findAndHookMethod(
                depthControllerClass,
                "getShouldBlurBeOpaque",
                object : XC_MethodReplacement() {
                    override fun replaceHookedMethod(param: MethodHookParam): Any {
                        if (ConfigManager.current.isEffectiveEnabled) {
                            return false
                        }
                        return XposedBridge.invokeOriginalMethod(param.method, param.thisObject, param.args)
                    }
                }
            )
            HookEntry.log("[+] Hooked NotificationShadeDepthController.getShouldBlurBeOpaque()")
        } catch (t: Throwable) {
            HookEntry.logError("[-] Error hooking NotificationShadeDepthController.getShouldBlurBeOpaque", t)
        }

        // 2. getNtOpaqueShadeCoversScreen()
        try {
            XposedHelpers.findAndHookMethod(
                depthControllerClass,
                "getNtOpaqueShadeCoversScreen",
                object : XC_MethodReplacement() {
                    override fun replaceHookedMethod(param: MethodHookParam): Any {
                        if (ConfigManager.current.isEffectiveEnabled) {
                            return false
                        }
                        return XposedBridge.invokeOriginalMethod(param.method, param.thisObject, param.args)
                    }
                }
            )
            HookEntry.log("[+] Hooked NotificationShadeDepthController.getNtOpaqueShadeCoversScreen()")
        } catch (t: Throwable) {
            HookEntry.logError("[-] Error hooking NotificationShadeDepthController.getNtOpaqueShadeCoversScreen", t)
        }

        // 3. computeBlurAndZoomOut()
        try {
            val computation = depthControllerClass.getDeclaredMethod("computeBlurAndZoomOut")
            val resultType = computation.returnType
            XposedBridge.hookMethod(
                computation,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val config = ConfigManager.current
                        if (!config.isEffectiveEnabled) return

                        val result = param.result ?: return
                        val shadeExp = shadeExpansionField?.getFloat(param.thisObject) ?: 0f
                        val qsExp = qsPanelExpansionField?.getFloat(param.thisObject) ?: 0f
                        val expansion = maxOf(shadeExp, qsExp)

                        if (expansion > 0.01f) {
                            val material = if (qsExp > 0.01f) config.qsMaterial else QuickSettingsMaterial.TRANSLUCENT
                            val radius = FrostedBackdrop.radius(config, expansion, material)
                            when {
                                result is Pair<*, *> && result.first != radius -> {
                                    param.result = Pair(radius, result.second)
                                }
                                resultType.name == "kotlin.Pair" -> {
                                    if (XposedHelpers.callMethod(result, "getFirst") == radius) return
                                    val zoom = XposedHelpers.callMethod(result, "getSecond")
                                    param.result = XposedHelpers.newInstance(resultType, radius, zoom)
                                }
                            }
                        }
                    }
                }
            )
            HookEntry.log("[+] Hooked NotificationShadeDepthController.computeBlurAndZoomOut()")
        } catch (t: Throwable) {
            HookEntry.logError("[-] Error hooking NotificationShadeDepthController.computeBlurAndZoomOut", t)
        }
    }

    private fun hookWindowRootViewBlurInteractor(classLoader: ClassLoader) {
        val interactorClass = XposedHelpers.findClassIfExists(
            "com.android.systemui.window.domain.interactor.WindowRootViewBlurInteractor",
            classLoader
        )
        if (interactorClass == null) {
            HookEntry.log("[-] Class NOT found: com.android.systemui.window.domain.interactor.WindowRootViewBlurInteractor")
            return
        }
        HookEntry.log("[+] Found class: com.android.systemui.window.domain.interactor.WindowRootViewBlurInteractor")

        try {
            XposedHelpers.findAndHookMethod(
                interactorClass,
                "isBlurredWallpaperOnShadeSupported",
                object : XC_MethodReplacement() {
                    override fun replaceHookedMethod(param: MethodHookParam): Any {
                        return if (ConfigManager.current.isEffectiveEnabled) true else {
                            XposedBridge.invokeOriginalMethod(param.method, param.thisObject, param.args)
                        }
                    }
                }
            )
            HookEntry.log("[+] Hooked WindowRootViewBlurInteractor.isBlurredWallpaperOnShadeSupported()")
        } catch (t: Throwable) {
            HookEntry.logError("[-] Error hooking WindowRootViewBlurInteractor.isBlurredWallpaperOnShadeSupported", t)
        }
    }
}
