package com.nothing.glass.hooks

import android.app.Dialog
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.Drawable
import android.view.View
import android.view.ViewGroup
import com.nothing.glass.HookEntry
import com.nothing.glass.config.ConfigManager
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import java.lang.reflect.Field

object VolumeHooks {

    private var mDialogViewField: Field? = null

    fun install(classLoader: ClassLoader) {
        hookModernVolumeDialog(classLoader)
        hookVolumeBinders(classLoader)
        hookLegacyVolumeDialog(classLoader)
    }

    /**
     * Modern Android 15 / Nothing OS 5.0 ComponentDialog:
     * com.android.systemui.volume.dialog.VolumeDialog
     */
    private fun hookModernVolumeDialog(classLoader: ClassLoader) {
        val volumeDialogClass = XposedHelpers.findClassIfExists(
            "com.android.systemui.volume.dialog.VolumeDialog",
            classLoader
        ) ?: return

        try {
            val lifecycleMethods = listOf("onCreate", "show", "onStart")
            for (mName in lifecycleMethods) {
                val methods = volumeDialogClass.declaredMethods.filter { it.name == mName }
                for (method in methods) {
                    XposedBridge.hookMethod(method, object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            try {
                                val config = ConfigManager.current
                                if (!config.isEffectiveEnabled || !config.volumeGlassEnabled) return

                                val dialog = param.thisObject as? Dialog ?: return
                                val decorView = dialog.window?.decorView as? ViewGroup ?: return
                                applyFrostedGlassToVolumeView(decorView)
                            } catch (_: Throwable) {}
                        }
                    })
                }
            }
            HookEntry.log("[+] Hooked VolumeDialog lifecycle for frosted glass")
        } catch (t: Throwable) {
            HookEntry.logError("[-] Error hooking VolumeDialog", t)
        }
    }

    /**
     * ViewBinders for individual volume buttons:
     * - Settings / Expand button (bottom '>' button)
     * - Ringer button (top circular mode toggle)
     */
    private fun hookVolumeBinders(classLoader: ClassLoader) {
        val binderClasses = listOf(
            "com.android.systemui.volume.dialog.settings.ui.binder.VolumeDialogSettingsButtonViewBinder",
            "com.android.systemui.volume.dialog.ringer.ui.binder.VolumeDialogRingerViewBinder"
        )

        for (className in binderClasses) {
            val clazz = XposedHelpers.findClassIfExists(className, classLoader) ?: continue
            try {
                val bindMethods = clazz.declaredMethods.filter { it.name == "bind" }
                for (method in bindMethods) {
                    XposedBridge.hookMethod(method, object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            try {
                                val config = ConfigManager.current
                                if (!config.isEffectiveEnabled || !config.volumeGlassEnabled) return

                                val targetButton = param.args.getOrNull(0) as? View ?: return
                                val targetAlphaInt = (config.volumeButtonsAlphaPercent * 255 / 100).coerceIn(40, 255)

                                modulateButtonGlass(targetButton, targetAlphaInt)

                                // Keep translucent even if Nothing OS re-renders or changes states
                                targetButton.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
                                    val curConfig = ConfigManager.current
                                    if (curConfig.isEffectiveEnabled && curConfig.volumeGlassEnabled) {
                                        val alphaInt = (curConfig.volumeButtonsAlphaPercent * 255 / 100).coerceIn(40, 255)
                                        modulateButtonGlass(v, alphaInt)
                                    }
                                }
                            } catch (_: Throwable) {}
                        }
                    })
                }
                HookEntry.log("[+] Hooked $className.bind for volume button translucency")
            } catch (t: Throwable) {
                HookEntry.logError("[-] Error hooking $className", t)
            }
        }
    }

    private fun modulateButtonGlass(view: View, targetAlphaInt: Int) {
        applyToViewTree(view, targetAlphaInt, targetAlphaInt)
    }

    /**
     * Legacy / Extension Volume Dialog Hook:
     * com.nothing.systemui.volume.VolumeDialogImplEx
     */
    private fun hookLegacyVolumeDialog(classLoader: ClassLoader) {
        val volumeClasses = listOf(
            "com.nothing.systemui.volume.VolumeDialogImplEx",
            "com.android.systemui.volume.VolumeDialogImpl"
        )

        for (className in volumeClasses) {
            val clazz = XposedHelpers.findClassIfExists(className, classLoader) ?: continue
            try {
                if (mDialogViewField == null) {
                    mDialogViewField = clazz.findDeclaredFieldSafe("mDialogView")
                }

                val hookMethods = listOf("showH", "updateRowsH", "initDialog", "rescheduleTimeoutH")
                for (mName in hookMethods) {
                    val methods = clazz.declaredMethods.filter { it.name == mName }
                    for (method in methods) {
                        XposedBridge.hookMethod(method, object : XC_MethodHook() {
                            override fun afterHookedMethod(param: MethodHookParam) {
                                try {
                                    val config = ConfigManager.current
                                    if (!config.isEffectiveEnabled || !config.volumeGlassEnabled) return

                                    val dialogView = mDialogViewField?.get(param.thisObject) as? ViewGroup
                                        ?: XposedHelpers.getObjectField(param.thisObject, "mDialogView") as? ViewGroup
                                        ?: return

                                    applyFrostedGlassToVolumeView(dialogView)
                                } catch (_: Throwable) {}
                            }
                        })
                    }
                }
                HookEntry.log("[+] Hooked $className for legacy volume styling")
            } catch (t: Throwable) {
                HookEntry.logError("[-] Error hooking $className", t)
            }
        }
    }

    private fun applyFrostedGlassToVolumeView(rootView: ViewGroup) {
        val config = ConfigManager.current
        val sliderAlphaInt = (config.volumeSliderAlphaPercent * 255 / 100).coerceIn(40, 255)
        val buttonsAlphaInt = (config.volumeButtonsAlphaPercent * 255 / 100).coerceIn(40, 255)

        applyToViewTree(rootView, sliderAlphaInt, buttonsAlphaInt)
    }

    private fun applyToViewTree(view: View, sliderAlphaInt: Int, buttonsAlphaInt: Int) {
        val name = try { view.resources.getResourceEntryName(view.id) } catch (_: Throwable) { "" }
        if (name == "volume_expand_bar_container") view.background?.alpha = 0
        if (name in surfaceIds) {
            view.background?.let { view.background = decorate(view, it) }
        }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) applyToViewTree(view.getChildAt(i), sliderAlphaInt, buttonsAlphaInt)
        }
    }

    private val surfaceIds = setOf(
        "volume_dialog_background", "volume_dialog_top_section_container",
        "ringer_buttons_background", "accessibility_slider_container",
        "assistant_slider_container", "odi_captions"
    )

    private fun decorate(view: View, drawable: Drawable): Drawable {
        if (drawable is GlassBackground) return drawable
        if (drawable is LayerDrawable) {
            for (i in 0 until drawable.numberOfLayers) {
                val child = drawable.getDrawable(i)
                if (child is GradientDrawable && child !is GlassBackground) {
                    drawable.setDrawable(i, GlassBackground(view, child))
                }
            }
            return drawable
        }
        return if (drawable is GradientDrawable) GlassBackground(view, drawable) else drawable
    }

    // Preserve the GradientDrawable type expected by Nothing's corner animations.
    private class GlassBackground(private val view: View, private val original: GradientDrawable) : GradientDrawable() {
        private var blur: Drawable? = null
        private var materialColor = 0
        init {
            shape = original.shape
            val radii = original.cornerRadii
            if (radii != null) cornerRadii = radii else cornerRadius = original.cornerRadius
        }
        override fun draw(canvas: Canvas) {
            val config = ConfigManager.current
            if (!config.isEffectiveEnabled || !config.volumeGlassEnabled) {
                original.bounds = bounds
                original.draw(canvas)
                return
            }
            try {
                if (blur == null) {
                    val root = XposedHelpers.callMethod(view, "getViewRootImpl")
                    if (root != null) blur = XposedHelpers.callMethod(root, "createBackgroundBlurDrawable") as? Drawable
                }
                blur?.let {
                    it.bounds = bounds
                    XposedHelpers.callMethod(it, "setBlurRadius", config.volumeBlurRadius.coerceIn(60, 100))
                    val radii = cornerRadii
                    if (radii != null) {
                        XposedHelpers.callMethod(it, "setCornerRadius", *radii.toTypedArray())
                    } else XposedHelpers.callMethod(it, "setCornerRadius", cornerRadius)
                    XposedHelpers.callMethod(it, "setColor", Color.TRANSPARENT)
                    it.alpha = 255
                    it.draw(canvas)
                }
            } catch (_: Throwable) { blur = null }
            // A single dense material for the slider and every button surface.
            val tint = androidx.core.graphics.ColorUtils.setAlphaComponent(config.tintColorInt, if (blur != null) 210 else 245)
            if (materialColor != tint || color?.defaultColor != tint) {
                materialColor = tint
                setColor(tint)
            }
            if (alpha != 255) alpha = 255
            super.draw(canvas)
        }
    }

    private fun Class<*>.findDeclaredFieldSafe(fieldName: String): Field? {
        return try {
            getDeclaredField(fieldName).apply { isAccessible = true }
        } catch (_: Throwable) {
            try {
                superclass?.getDeclaredField(fieldName)?.apply { isAccessible = true }
            } catch (_: Throwable) {
                null
            }
        }
    }
}
