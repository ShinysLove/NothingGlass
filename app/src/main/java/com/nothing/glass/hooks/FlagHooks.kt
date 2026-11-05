package com.nothing.glass.hooks

import com.nothing.glass.HookEntry
import com.nothing.glass.config.ConfigManager
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XposedHelpers

object FlagHooks {

    fun install(classLoader: ClassLoader) {
        val flagsClass = XposedHelpers.findClassIfExists("com.android.systemui.Flags", classLoader)
        if (flagsClass == null) {
            HookEntry.log("[-] Class NOT found: com.android.systemui.Flags")
            return
        }
        HookEntry.log("[+] Found class: com.android.systemui.Flags")

        // 1. notificationShadeBlur()
        hookBooleanFlag(flagsClass, "notificationShadeBlur") {
            ConfigManager.current.isEffectiveEnabled
        }

        // 2. lockscreenBlurForNotifications()
        hookBooleanFlag(flagsClass, "lockscreenBlurForNotifications") {
            ConfigManager.current.isEffectiveEnabled && ConfigManager.current.notifCardBlurEnabled
        }

        // 3. blurOnMoreSurfaces()
        hookBooleanFlag(flagsClass, "blurOnMoreSurfaces") {
            ConfigManager.current.isEffectiveEnabled
        }

        // 4. enableLockscreenBlur()
        hookBooleanFlag(flagsClass, "enableLockscreenBlur") {
            ConfigManager.current.isEffectiveEnabled
        }
    }

    private fun hookBooleanFlag(clazz: Class<*>, methodName: String, condition: () -> Boolean) {
        try {
            XposedHelpers.findAndHookMethod(
                clazz,
                methodName,
                object : XC_MethodReplacement() {
                    override fun replaceHookedMethod(param: MethodHookParam): Any {
                        return condition()
                    }
                }
            )
            HookEntry.log("[+] Hooked Flags.$methodName()")
        } catch (t: Throwable) {
            HookEntry.logError("[-] Error hooking Flags.$methodName", t)
        }
    }
}
