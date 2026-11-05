package com.nothing.glass.hooks

import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.view.View
import com.nothing.glass.HookEntry
import com.nothing.glass.config.ConfigManager
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers

object KeyguardGlassHooks {
    fun install(loader: ClassLoader) {
        val clazz = XposedHelpers.findClassIfExists("com.android.systemui.animation.view.LaunchableImageView", loader) ?: return
        XposedBridge.hookAllConstructors(clazz, object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                val view = param.thisObject as? View ?: return
                if (XposedHelpers.getAdditionalInstanceField(view, "nothingGlassQuickAction") != null) return
                XposedHelpers.setAdditionalInstanceField(view, "nothingGlassQuickAction", true)
                view.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                    private var listener: android.view.ViewTreeObserver.OnPreDrawListener? = null
                    override fun onViewAttachedToWindow(v: View) {
                        val name = try { v.resources.getResourceEntryName(v.id) } catch (_: Throwable) { "" }
                        if (name != "start_button" && name != "end_button") return
                        var installed: Drawable? = null
                        var original: Drawable? = null
                        var originalTint = v.backgroundTintList
                        listener = android.view.ViewTreeObserver.OnPreDrawListener {
                            val config = ConfigManager.current
                            if (!config.isEffectiveEnabled) {
                                if (v.background === installed && original != null) {
                                    v.background = original
                                    v.backgroundTintList = originalTint
                                }
                                installed = null
                            } else if (v.background !== installed || installed == null) {
                                try {
                                    val stock = v.background
                                    val root = XposedHelpers.callMethod(v, "getViewRootImpl")
                                    val blur = XposedHelpers.callMethod(root, "createBackgroundBlurDrawable") as Drawable
                                    XposedHelpers.callMethod(blur, "setBlurRadius", config.notifCardBlurRadius.coerceAtLeast(60))
                                    XposedHelpers.callMethod(blur, "setCornerRadius", minOf(v.width, v.height) / 2f)
                                    XposedHelpers.callMethod(blur, "setColor", androidx.core.graphics.ColorUtils.setAlphaComponent(config.tintColorInt, 150))
                                    original = stock
                                    originalTint = v.backgroundTintList ?: originalTint
                                    v.backgroundTintList = null
                                    // Keep the native stateful tint for an active flashlight/DND shortcut.
                                    stock?.mutate()?.apply { setTintList(originalTint); alpha = 100 }
                                    installed = if (stock != null) LayerDrawable(arrayOf(blur, stock)) else blur
                                    v.background = installed
                                } catch (t: Throwable) {
                                    HookEntry.logError("Quick action blur unavailable", t)
                                }
                            } else if (v.backgroundTintList != null) {
                                originalTint = v.backgroundTintList
                                original?.setTintList(originalTint)
                                v.backgroundTintList = null
                            }
                            true
                        }
                        v.viewTreeObserver.addOnPreDrawListener(listener)
                    }
                    override fun onViewDetachedFromWindow(v: View) {
                        listener?.let { if (v.viewTreeObserver.isAlive) v.viewTreeObserver.removeOnPreDrawListener(it) }
                        listener = null
                    }
                })
            }
        })
        HookEntry.log("[+] Hooked lockscreen quick action glass")
    }
}
