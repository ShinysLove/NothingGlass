package com.nothing.glass

import android.app.Application
import android.util.Log
import com.nothing.glass.config.ConfigManager
import com.nothing.glass.config.GlassConfig
import com.nothing.glass.hooks.BlurHooks
import com.nothing.glass.hooks.FlagHooks
import com.nothing.glass.hooks.NotificationHooks
import com.nothing.glass.hooks.ScrimHooks
import com.nothing.glass.hooks.ScrimViewHooks
import com.nothing.glass.hooks.TileHooks
import com.nothing.glass.hooks.KeyguardGlassHooks
import com.nothing.glass.hooks.VolumeHooks
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.IXposedHookZygoteInit
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.io.File

class HookEntry : IXposedHookLoadPackage, IXposedHookZygoteInit {

    @Volatile
    private var hooksInstalled = false

    override fun initZygote(startupParam: IXposedHookZygoteInit.StartupParam?) {
        try {
            log("=== Zygote Initialization ===")
            ConfigManager.initXSharedPreferences()
        } catch (t: Throwable) {
            logError("[-] Error in initZygote", t)
        }
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName != TARGET_PACKAGE) return

        try {
            // Safety check 1: Emergency disable flag on filesystem
            val disableFile = File(GlassConfig.DISABLE_FLAG_FILE)
            if (disableFile.exists()) {
                log("[!] EMERGENCY DISABLE FLAG FOUND (${GlassConfig.DISABLE_FLAG_FILE}) - Nothing Glass is deactivated.")
                return
            }

            // Safety check 2: Safe mode or disabled in config
            if (!ConfigManager.current.isEffectiveEnabled) {
                log("[*] Nothing Glass is disabled in config, skipping hook installation.")
                return
            }

            log("================================================================")
            log("NOTHING GLASS: Target package matched: ${lpparam.packageName}")
            log("Process: ${lpparam.processName}, ClassLoader: ${lpparam.classLoader}")
            log("Config state: ${ConfigManager.current}")
            log("================================================================")

            // Ensure SystemUI APK is in the ClassLoader
            ensureSystemUiDexLoaded(lpparam)

            // Hook Application lifecycle for config syncing
            hookApplicationLifecycle(lpparam.classLoader)

            // Install all hooks immediately
            installAllHooks(lpparam.classLoader)
        } catch (t: Throwable) {
            logError("[-] FATAL: Error in handleLoadPackage", t)
        }
    }

    private fun ensureSystemUiDexLoaded(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            var testClass = XposedHelpers.findClassIfExists("com.android.systemui.shade.ui.ShadeColors", lpparam.classLoader)
            if (testClass == null) {
                val apkPath = lpparam.appInfo?.sourceDir
                log("[*] SystemUI classes missing from ClassLoader, injecting sourceDir: $apkPath")
                if (apkPath != null) {
                    try {
                        XposedHelpers.callMethod(lpparam.classLoader, "addDexPath", apkPath)
                        lpparam.appInfo?.splitSourceDirs?.forEach { split ->
                            XposedHelpers.callMethod(lpparam.classLoader, "addDexPath", split)
                        }
                        testClass = XposedHelpers.findClassIfExists("com.android.systemui.shade.ui.ShadeColors", lpparam.classLoader)
                        log("[+] After addDexPath, ShadeColors found = ${testClass != null}")
                    } catch (t: Throwable) {
                        logError("[-] Error adding dex path", t)
                    }
                }
            }
        } catch (t: Throwable) {
            logError("[-] Error in ensureSystemUiDexLoaded", t)
        }
    }

    private fun hookApplicationLifecycle(classLoader: ClassLoader) {
        val appCandidates = listOf(
            "com.android.systemui.application.impl.SystemUIApplicationImpl",
            "com.android.systemui.application.SystemUIApplication",
            "android.app.Application"
        )

        for (candidate in appCandidates) {
            val appClass = XposedHelpers.findClassIfExists(candidate, classLoader) ?: continue
            try {
                XposedBridge.hookAllMethods(appClass, "onCreate", object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        try {
                            val app = param.thisObject as? Application ?: return
                            val appClassLoader = app.classLoader ?: classLoader
                            log("[LIFECYCLE] $candidate.onCreate() fired, synchronizing config...")
                            installAllHooks(appClassLoader)
                            ConfigManager.syncFromProvider(app)
                            ConfigManager.registerBroadcastReceiver(app)
                        } catch (t: Throwable) {
                            logError("[-] Error in Application.onCreate hook", t)
                        }
                    }
                })
                log("[+] Hooked onCreate on $candidate")
                break
            } catch (t: Throwable) {
                logError("[-] Error hooking onCreate on $candidate", t)
            }
        }

        // Also hook LoadedApk.makeApplication as fallback
        val loadedApkClass = XposedHelpers.findClassIfExists("android.app.LoadedApk", classLoader)
        if (loadedApkClass != null) {
            try {
                XposedBridge.hookAllMethods(loadedApkClass, "makeApplication", object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        try {
                            val app = param.result as? Application ?: return
                            val appClassLoader = app.classLoader ?: classLoader
                            installAllHooks(appClassLoader)
                        } catch (t: Throwable) {
                            logError("[-] Error in makeApplication hook", t)
                        }
                    }
                })
                log("[+] Hooked LoadedApk.makeApplication fallback")
            } catch (t: Throwable) {
                logError("[-] Error hooking LoadedApk.makeApplication", t)
            }
        }
    }

    @Synchronized
    fun installAllHooks(classLoader: ClassLoader) {
        if (hooksInstalled) return

        try {
            val disableFile = File(GlassConfig.DISABLE_FLAG_FILE)
            if (disableFile.exists()) {
                log("[!] EMERGENCY DISABLE FLAG FOUND - Aborting hook installation.")
                return
            }

            if (!ConfigManager.current.isEffectiveEnabled) {
                log("[*] Nothing Glass disabled, skipping installAllHooks.")
                return
            }

            val testClass = XposedHelpers.findClassIfExists("com.android.systemui.shade.ui.ShadeColors", classLoader)
            if (testClass == null) {
                log("[-] SystemUI classes not yet present in ClassLoader: $classLoader")
                return
            }

            hooksInstalled = true
            log("================================================================")
            log("[+] Installing all Nothing Glass hook groups now on $classLoader...")
            log("================================================================")

            installHookGroup("FlagHooks") { FlagHooks.install(classLoader) }
            installHookGroup("BlurHooks") { BlurHooks.install(classLoader) }
            installHookGroup("ScrimHooks") { ScrimHooks.install(classLoader) }
            installHookGroup("ScrimViewHooks") { ScrimViewHooks.install(classLoader) }
            installHookGroup("TileHooks") { TileHooks.install(classLoader) }
            installHookGroup("NotificationHooks") { NotificationHooks.install(classLoader) }
            installHookGroup("VolumeHooks") { VolumeHooks.install(classLoader) }
            installHookGroup("KeyguardGlassHooks") { KeyguardGlassHooks.install(classLoader) }

            log("=== All Nothing Glass hook groups registered successfully ===")
        } catch (t: Throwable) {
            logError("[-] FATAL: Error in installAllHooks", t)
        }
    }

    private inline fun installHookGroup(name: String, block: () -> Unit) {
        try {
            log("[GROUP-START] Installing $name...")
            block()
            log("[GROUP-SUCCESS] $name installed successfully")
        } catch (t: Throwable) {
            logError("[GROUP-FAIL] Error installing $name: ${t.message}", t)
        }
    }

    companion object {
        const val TAG = "NothingGlass"
        const val TARGET_PACKAGE = "com.android.systemui"

        fun log(msg: String) {
            Log.i(TAG, msg)
            XposedBridge.log("$TAG: $msg")
        }

        fun logError(msg: String, throwable: Throwable?) {
            Log.e(TAG, msg, throwable)
            XposedBridge.log("$TAG: $msg")
            throwable?.let {
                val sw = java.io.StringWriter()
                it.printStackTrace(java.io.PrintWriter(sw))
                XposedBridge.log(sw.toString())
            }
        }
    }
}
