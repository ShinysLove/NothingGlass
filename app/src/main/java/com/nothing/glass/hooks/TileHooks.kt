package com.nothing.glass.hooks

import android.content.res.ColorStateList
import android.content.res.Resources
import android.view.View
import android.view.ViewGroup
import androidx.core.graphics.ColorUtils
import com.nothing.glass.HookEntry
import com.nothing.glass.config.ConfigManager
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers

object TileHooks {

    private var idActiveBg: Int = 0
    private var idSpecialBg: Int = 0
    private var idIconActive: Int = 0
    private var idIconInactive: Int = 0
    private var idsResolved: Boolean = false

    fun install(classLoader: ClassLoader) {
        hookResources(classLoader)
        hookTileViews(classLoader)
        hookComposeTileWorkspace(classLoader)
    }

    private fun hookTileViews(classLoader: ClassLoader) {
        // 1. QSTileViewImpl & QSTileViewImplEx
        val tileViewClasses = listOf(
            "com.nothing.systemui.qs.tileimpl.QSTileViewImplEx",
            "com.android.systemui.qs.tileimpl.QSTileViewImpl"
        )
        for (className in tileViewClasses) {
            val clazz = XposedHelpers.findClassIfExists(className, classLoader) ?: continue
            try {
                // Hook handleStateChanged
                val stateMethods = clazz.declaredMethods.filter { it.name == "handleStateChanged" }
                for (method in stateMethods) {
                    XposedBridge.hookMethod(method, object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            try {
                                val config = ConfigManager.current
                                if (!config.isEffectiveEnabled) return

                                val tileView = param.thisObject as? View ?: return
                                val targetAlpha = (config.qsIconAlphaActivePercent / 100f).coerceIn(0.2f, 1f)
                                val targetAlphaInt = (targetAlpha * 255).toInt().coerceIn(0, 255)

                                val iconView = try {
                                    XposedHelpers.callMethod(tileView, "getIcon") as? ViewGroup
                                        ?: XposedHelpers.getObjectField(tileView, "_icon") as? ViewGroup
                                } catch (_: Throwable) { null }

                                if (iconView != null) {
                                    if (Math.abs(iconView.alpha - targetAlpha) > 0.02f) {
                                        iconView.alpha = targetAlpha
                                    }
                                    iconView.background?.alpha = targetAlphaInt
                                    for (i in 0 until iconView.childCount) {
                                        val child = iconView.getChildAt(i)
                                        child.background?.alpha = targetAlphaInt
                                    }
                                }

                                val iconWithBg = try {
                                    XposedHelpers.callMethod(tileView, "getIconWithBackground") as? View
                                } catch (_: Throwable) { null }

                                if (iconWithBg != null) {
                                    iconWithBg.background?.alpha = targetAlphaInt
                                    if (Math.abs(iconWithBg.alpha - targetAlpha) > 0.02f) {
                                        iconWithBg.alpha = targetAlpha
                                    }
                                }
                            } catch (_: Throwable) {}
                        }
                    })
                }

                // Hook dispatchDraw on TileView
                val dispatchDrawMethods = clazz.declaredMethods.filter { it.name == "dispatchDraw" }
                for (method in dispatchDrawMethods) {
                    XposedBridge.hookMethod(method, object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            try {
                                val config = ConfigManager.current
                                if (!config.isEffectiveEnabled) return
                                val tileView = param.thisObject as? ViewGroup ?: return
                                val targetAlpha = (config.qsIconAlphaActivePercent / 100f).coerceIn(0.2f, 1f)
                                val targetAlphaInt = (targetAlpha * 255).toInt().coerceIn(0, 255)

                                val iconView = try {
                                    XposedHelpers.callMethod(tileView, "getIcon") as? ViewGroup
                                        ?: XposedHelpers.getObjectField(tileView, "_icon") as? ViewGroup
                                } catch (_: Throwable) { null }

                                if (iconView != null) {
                                    if (Math.abs(iconView.alpha - targetAlpha) > 0.02f) {
                                        iconView.alpha = targetAlpha
                                    }
                                    iconView.background?.alpha = targetAlphaInt
                                    for (i in 0 until iconView.childCount) {
                                        val child = iconView.getChildAt(i)
                                        child.background?.alpha = targetAlphaInt
                                    }
                                }

                                val iconWithBg = try {
                                    XposedHelpers.callMethod(tileView, "getIconWithBackground") as? View
                                } catch (_: Throwable) { null }
                                if (iconWithBg != null) {
                                    iconWithBg.background?.alpha = targetAlphaInt
                                    if (Math.abs(iconWithBg.alpha - targetAlpha) > 0.02f) {
                                        iconWithBg.alpha = targetAlpha
                                    }
                                }
                            } catch (_: Throwable) {}
                        }
                    })
                }

                HookEntry.log("[+] Hooked $className for QS tile icon translucency")
            } catch (t: Throwable) {
                HookEntry.logError("[-] Error hooking $className", t)
            }
        }

        // 2. QSIconViewImpl
        val iconViewClass = XposedHelpers.findClassIfExists(
            "com.android.systemui.qs.tileimpl.QSIconViewImpl",
            classLoader
        )
        if (iconViewClass != null) {
            try {
                // Hook setIcon
                val setIconMethods = iconViewClass.declaredMethods.filter { it.name == "setIcon" }
                for (method in setIconMethods) {
                    XposedBridge.hookMethod(method, object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            try {
                                val config = ConfigManager.current
                                if (!config.isEffectiveEnabled) return
                                val iconGroup = param.thisObject as? ViewGroup ?: return
                                val targetAlpha = (config.qsIconAlphaActivePercent / 100f).coerceIn(0.2f, 1f)
                                val targetAlphaInt = (targetAlpha * 255).toInt().coerceIn(0, 255)
                                iconGroup.background?.alpha = targetAlphaInt
                                if (Math.abs(iconGroup.alpha - targetAlpha) > 0.02f) {
                                    iconGroup.alpha = targetAlpha
                                }
                                for (i in 0 until iconGroup.childCount) {
                                    val child = iconGroup.getChildAt(i)
                                    child.background?.alpha = targetAlphaInt
                                }
                            } catch (_: Throwable) {}
                        }
                    })
                }

                // Hook dispatchDraw on QSIconViewImpl
                val drawMethods = iconViewClass.declaredMethods.filter { it.name == "dispatchDraw" || it.name == "onDraw" }
                for (method in drawMethods) {
                    XposedBridge.hookMethod(method, object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            try {
                                val config = ConfigManager.current
                                if (!config.isEffectiveEnabled) return
                                val iconGroup = param.thisObject as? ViewGroup ?: return
                                val targetAlpha = (config.qsIconAlphaActivePercent / 100f).coerceIn(0.2f, 1f)
                                val targetAlphaInt = (targetAlpha * 255).toInt().coerceIn(0, 255)
                                iconGroup.background?.alpha = targetAlphaInt
                                if (Math.abs(iconGroup.alpha - targetAlpha) > 0.02f) {
                                    iconGroup.alpha = targetAlpha
                                }
                                for (i in 0 until iconGroup.childCount) {
                                    val child = iconGroup.getChildAt(i)
                                    child.background?.alpha = targetAlphaInt
                                }
                            } catch (_: Throwable) {}
                        }
                    })
                }

                HookEntry.log("[+] Hooked QSIconViewImpl for white circle translucency")
            } catch (t: Throwable) {
                HookEntry.logError("[-] Error hooking QSIconViewImpl", t)
            }
        }
    }

    private fun hookResources(classLoader: ClassLoader) {
        try {
            val resourcesClass = XposedHelpers.findClassIfExists("android.content.res.Resources", classLoader)
                ?: Resources::class.java

            // 1. Hook getColor
            XposedBridge.hookAllMethods(resourcesClass, "getColor", object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    try {
                        val config = ConfigManager.current
                        if (!config.isEffectiveEnabled) return

                        val resId = param.args.getOrNull(0) as? Int ?: return
                        if (resId <= 0) return

                        val res = param.thisObject as? Resources ?: return
                        if (!idsResolved) resolveIds(res)

                        if (resId != idActiveBg && resId != idSpecialBg && resId != idIconActive && resId != idIconInactive) {
                            return
                        }

                        val origColor = param.result as? Int ?: return

                        when (resId) {
                            idActiveBg -> {
                                val targetAlpha = (config.qsIconAlphaActivePercent * 255 / 100).coerceIn(0, 255)
                                param.result = ColorUtils.setAlphaComponent(origColor, targetAlpha)
                            }
                            idSpecialBg -> {
                                val targetAlpha = (config.inactiveTileAlpha * 0.75f * 255).toInt().coerceIn(0, 255)
                                param.result = ColorUtils.setAlphaComponent(origColor, targetAlpha)
                            }
                            idIconActive -> {
                                val targetAlpha = (config.qsIconAlphaActivePercent * 255 / 100).coerceIn(0, 255)
                                param.result = ColorUtils.setAlphaComponent(origColor, targetAlpha)
                            }
                            idIconInactive -> {
                                val targetAlpha = (config.qsIconAlphaInactivePercent * 255 / 100).coerceIn(0, 255)
                                param.result = ColorUtils.setAlphaComponent(origColor, targetAlpha)
                            }
                        }
                    } catch (_: Throwable) {}
                }
            })

            // 2. Hook getColorStateList
            XposedBridge.hookAllMethods(resourcesClass, "getColorStateList", object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    try {
                        val config = ConfigManager.current
                        if (!config.isEffectiveEnabled) return

                        val resId = param.args.getOrNull(0) as? Int ?: return
                        if (resId <= 0) return

                        val res = param.thisObject as? Resources ?: return
                        if (!idsResolved) resolveIds(res)

                        if (resId != idActiveBg && resId != idIconActive) return

                        val csl = param.result as? ColorStateList ?: return
                        val origColor = csl.defaultColor
                        val targetAlpha = (config.qsIconAlphaActivePercent * 255 / 100).coerceIn(0, 255)
                        param.result = ColorStateList.valueOf(ColorUtils.setAlphaComponent(origColor, targetAlpha))
                    } catch (_: Throwable) {}
                }
            })

            HookEntry.log("[+] Hooked Resources.getColor() & getColorStateList() safely")
        } catch (t: Throwable) {
            HookEntry.logError("[-] Error hooking Resources methods", t)
        }
    }

    private fun resolveIds(res: Resources) {
        try {
            val pkg = "com.android.systemui"
            idActiveBg = res.getIdentifier("qs_tile_active_background_color", "color", pkg)
            idSpecialBg = res.getIdentifier("qs_special_tile_background_color", "color", pkg)
            idIconActive = res.getIdentifier("qs_tile_icon_active_color", "color", pkg)
            idIconInactive = res.getIdentifier("qs_tile_icon_inactive_color", "color", pkg)
            idsResolved = true
            HookEntry.log("[+] Resolved tile color IDs: activeBg=$idActiveBg, specialBg=$idSpecialBg, iconActive=$idIconActive, iconInactive=$idIconInactive")
        } catch (_: Throwable) {}
    }

    private fun hookComposeTileWorkspace(classLoader: ClassLoader) {
        val visualStateClass = XposedHelpers.findClassIfExists(
            "com.nothing.systemui.qs.workspace.workspace.TileVisualStateKt",
            classLoader
        )
        if (visualStateClass != null) {
            try {
                val discMethods = visualStateClass.declaredMethods.filter { it.name.startsWith("discColorForState") }
                for (method in discMethods) {
                    XposedBridge.hookMethod(method, object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            try {
                                val config = ConfigManager.current
                                if (!config.isEffectiveEnabled) return
                                val origColorLong = param.result as? Long ?: return
                                val targetAlphaInt = (config.qsIconAlphaActivePercent * 255 / 100).toLong().coerceIn(0L, 255L)
                                // In Compose Color (sRGB), bits 56..63 represent alpha
                                val newColorLong = (origColorLong and 0x00FFFFFFFFFFFFFFL) or (targetAlphaInt shl 56)
                                param.result = newColorLong
                            } catch (_: Throwable) {}
                        }
                    })
                }
                HookEntry.log("[+] Hooked TileVisualStateKt.discColorForState for Compose QS tile white disc translucency")
            } catch (t: Throwable) {
                HookEntry.logError("[-] Error hooking TileVisualStateKt", t)
            }
        }
    }
}
