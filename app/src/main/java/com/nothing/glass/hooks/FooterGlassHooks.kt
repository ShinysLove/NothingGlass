package com.nothing.glass.hooks

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.IBinder
import android.view.View
import com.nothing.glass.HookEntry
import com.nothing.glass.config.ConfigManager
import com.nothing.glass.config.GlassConfig
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers

object FooterGlassHooks {
    private const val SYSTEM_UI = "com.android.systemui"
    private val buttonNames = listOf("dismiss_text", "history_button", "settings_button")

    fun install(loader: ClassLoader) {
        val footer = XposedHelpers.findClassIfExists(
            "$SYSTEM_UI.statusbar.notification.footer.ui.view.FooterView", loader
        ) ?: return
        val inflateHooks = XposedBridge.hookAllMethods(footer, "onFinishInflate", object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                (param.thisObject as? View)?.let(::styleButtons)
            }
        })
        val binder = XposedHelpers.findClassIfExists(
            "$SYSTEM_UI.statusbar.notification.footer.ui.viewbinder.FooterViewBinder", loader
        )
        val bindHooks = if (binder != null) {
            XposedBridge.hookAllMethods(binder, "bind", object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    (param.args.firstOrNull { footer.isInstance(it) } as? View)?.let(::styleButtons)
                }
            }).size
        } else 0
        HookEntry.log("[+] Notification footer hooks: ${inflateHooks.size} inflate / $bindHooks bind")
    }

    private fun styleButtons(footer: View) {
        for (name in buttonNames) {
            try {
                val id = footer.resources.getIdentifier(name, "id", SYSTEM_UI)
                if (id == 0) continue
                val button = footer.findViewById<View>(id) ?: continue
                val ripple = button.background as? RippleDrawable ?: continue
                if (ripple.numberOfLayers == 0) continue
                val stock = ripple.getDrawable(0) as? GradientDrawable ?: continue
                ripple.mutate()
                ripple.setDrawable(0, GlassPlate(button, stock))
                button.invalidate()
            } catch (error: Exception) {
                HookEntry.logError("[-] Could not style notification footer button $name", error)
            }
        }
    }

    private class GlassPlate(private val button: View, private val stock: GradientDrawable) : Drawable() {
        private val shader = FrostedBackdrop.TintShader()
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private var window: IBinder? = null
        private var blur: Drawable? = null
        private var blurRadius = -1
        private var blurCorner = -1f
        private var opacity = stock.alpha

        override fun draw(canvas: Canvas) {
            val config = ConfigManager.current
            if (!config.isEffectiveEnabled || !config.notifGlassEnabled || bounds.isEmpty || opacity == 0) {
                hideBlur()
                stock.draw(canvas)
                return
            }

            val corner = stock.cornerRadius.coerceIn(0f, minOf(bounds.width(), bounds.height()) / 2f)
            val radius = FrostedBackdrop.radius(config, 1f).coerceAtMost(70)
            val hasBlur = canvas.isHardwareAccelerated && radius > 0 &&
                drawBlur(canvas, corner, radius)
            if (!hasBlur) hideBlur()

            paint.shader = shader.get(
                bounds, FrostedBackdrop.notificationTint(button.context, config),
                0.8f, canvas.isHardwareAccelerated
            )
            paint.alpha = (opacity * if (hasBlur) 0.56f else 0.75f).toInt()
            canvas.drawRoundRect(
                bounds.left.toFloat(), bounds.top.toFloat(),
                bounds.right.toFloat(), bounds.bottom.toFloat(), corner, corner, paint
            )
        }

        private fun drawBlur(canvas: Canvas, corner: Float, radius: Int): Boolean {
            val token = button.windowToken ?: return false
            return try {
                if (window !== token) {
                    hideBlur()
                    blur = null
                    window = token
                }
                val backdrop = blur ?: run {
                    val root = XposedHelpers.callMethod(button, "getViewRootImpl") ?: return false
                    val created = XposedHelpers.callMethod(root, "createBackgroundBlurDrawable")
                        as? Drawable ?: return false
                    XposedHelpers.callMethod(created, "setColor", Color.TRANSPARENT)
                    blurRadius = -1
                    blurCorner = -1f
                    blur = created
                    created
                }
                if (radius != blurRadius) {
                    XposedHelpers.callMethod(backdrop, "setBlurRadius", radius)
                    blurRadius = radius
                }
                if (corner != blurCorner) {
                    XposedHelpers.callMethod(backdrop, "setCornerRadius", corner)
                    blurCorner = corner
                }
                backdrop.bounds = bounds
                backdrop.alpha = opacity
                backdrop.draw(canvas)
                true
            } catch (_: Exception) {
                false
            }
        }

        private fun hideBlur() {
            val backdrop = blur ?: return
            if (backdrop.alpha != 0) backdrop.alpha = 0
        }

        override fun onBoundsChange(bounds: Rect) { stock.bounds = bounds }
        override fun getOutline(outline: Outline) { stock.getOutline(outline) }
        override fun getPadding(padding: Rect): Boolean = stock.getPadding(padding)
        override fun getIntrinsicWidth(): Int = stock.intrinsicWidth
        override fun getIntrinsicHeight(): Int = stock.intrinsicHeight
        override fun getMinimumWidth(): Int = stock.minimumWidth
        override fun getMinimumHeight(): Int = stock.minimumHeight
        override fun isStateful(): Boolean = stock.isStateful
        override fun onStateChange(state: IntArray): Boolean = stock.setState(state)
        override fun getAlpha(): Int = opacity
        override fun setAlpha(alpha: Int) {
            if (opacity == alpha) return
            opacity = alpha
            stock.alpha = alpha
            invalidateSelf()
        }
        override fun setColorFilter(filter: ColorFilter?) {
            paint.colorFilter = filter
            stock.colorFilter = filter
            invalidateSelf()
        }
        @Deprecated("Drawable opacity is no longer used by the system")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }
}
