package com.nothing.glass.hooks

import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.Color
import android.app.KeyguardManager
import android.content.Context
import androidx.core.graphics.ColorUtils
import android.graphics.drawable.Drawable
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import com.nothing.glass.HookEntry
import com.nothing.glass.config.ConfigManager
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import java.lang.reflect.Field
import java.lang.reflect.Method

object NotificationHooks {

    // Cached Reflection Fields for 120Hz Zero-Allocation Draw
    private var mBackgroundField: Field? = null
    private var mBackgroundBlurDrawableField: Field? = null
    private var mCornerRadiiField: Field? = null
    private var mClipTopAmountField: Field? = null
    private var mClipBottomAmountField: Field? = null
    private var mActualHeightField: Field? = null
    private var mActualWidthField: Field? = null
    private var mNormalColorField: Field? = null
    private var mTintColorField: Field? = null
    private var mCornerAnomalyOwnerField: Field? = null

    // ExpandableNotificationRow check
    private var expandableRowClass: Class<*>? = null
    private var isHeadsUpMethod: Method? = null
    private var isPinnedMethod: Method? = null

    // BackgroundBlurDrawable reflection
    private var createBlurDrawableMethod: Method? = null
    private var setBlurRadiusMethod: Method? = null
    private var setBlurColorMethod: Method? = null
    private var setBlurCornerRadiusMethod: Method? = null

    fun install(classLoader: ClassLoader) {
        initReflection(classLoader)
        hookNotificationBackgroundView(classLoader)
        hookActivatableNotificationView(classLoader)
        hookStatusBarIconView(classLoader)
        hookNotificationIcons(classLoader)
        hookExpandableNotificationRow(classLoader)
        hookEssentialSections(classLoader)
        hookExpandedGroupHeader(classLoader)
    }

    private fun initReflection(classLoader: ClassLoader) {
        val bgViewClass = XposedHelpers.findClassIfExists(
            "com.android.systemui.statusbar.notification.row.NotificationBackgroundView",
            classLoader
        )
        if (bgViewClass != null) {
            mBackgroundField = bgViewClass.findDeclaredFieldSafe("mBackground")
            mBackgroundBlurDrawableField = bgViewClass.findDeclaredFieldSafe("mBackgroundBlurDrawable")
            mCornerRadiiField = bgViewClass.findDeclaredFieldSafe("mCornerRadii")
            mClipTopAmountField = bgViewClass.findDeclaredFieldSafe("mClipTopAmount")
            mClipBottomAmountField = bgViewClass.findDeclaredFieldSafe("mClipBottomAmount")
            mActualHeightField = bgViewClass.findDeclaredFieldSafe("mActualHeight")
            mActualWidthField = bgViewClass.findDeclaredFieldSafe("mActualWidth")
            mNormalColorField = bgViewClass.findDeclaredFieldSafe("mNormalColor")
            mTintColorField = bgViewClass.findDeclaredFieldSafe("mTintColor")
            mCornerAnomalyOwnerField = bgViewClass.findDeclaredFieldSafe("mCornerAnomalyOwner")
        }

        expandableRowClass = XposedHelpers.findClassIfExists(
            "com.android.systemui.statusbar.notification.row.ExpandableNotificationRow",
            classLoader
        )
        expandableRowClass?.let { clazz ->
            try { isHeadsUpMethod = clazz.getMethod("isHeadsUp").apply { isAccessible = true } } catch (_: Throwable) {}
            try { isPinnedMethod = clazz.getMethod("isPinned").apply { isAccessible = true } } catch (_: Throwable) {}
        }
    }

    private fun Class<*>.findDeclaredFieldSafe(fieldName: String): Field? {
        return try {
            getDeclaredField(fieldName).apply { isAccessible = true }
        } catch (_: Throwable) {
            null
        }
    }

    private fun hookNotificationBackgroundView(classLoader: ClassLoader) {
        val bgViewClass = XposedHelpers.findClassIfExists(
            "com.android.systemui.statusbar.notification.row.NotificationBackgroundView",
            classLoader
        ) ?: return

        // 1. onDraw(Canvas canvas)
        try {
            XposedHelpers.findAndHookMethod(
                bgViewClass,
                "onDraw",
                Canvas::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        try {
                            val config = ConfigManager.current
                            if (!config.isEffectiveEnabled) return

                            val bgView = param.thisObject as? View ?: return
                            // Legacy XML inflates an unused backgroundDimmed. Do not synthesize a second plate.
                            if (mBackgroundField?.get(bgView) == null && mCornerAnomalyOwnerField?.get(bgView) == null) {
                                val stale = mBackgroundBlurDrawableField?.get(bgView) as? Drawable
                                stale?.alpha = 0
                                stale?.callback = null
                                mBackgroundBlurDrawableField?.set(bgView, null)
                                param.result = null
                                return
                            }
                            val isHeadsUp = checkIfHeadsUp(bgView)

                            val isGlassEnabled = if (isHeadsUp) config.headsUpGlassEnabled else config.notifGlassEnabled
                            if (!isGlassEnabled) return

                            if (isHeadsUp || isKeyguard(bgView)) {
                                if ((!isHeadsUp || config.headsUpBlurEnabled) &&
                                    drawLocalGlass(bgView, param.args[0] as Canvas)) {
                                    param.result = null
                                    return
                                }
                            }

                            // 1. Hardware blur management
                            if (isHeadsUp || isKeyguard(bgView)) {
                                // Heads-Up notifications floating over apps/launcher MUST have real hardware background blur
                                if (!isHeadsUp || config.headsUpBlurEnabled) {
                                    val clipTop = mClipTopAmountField?.getInt(bgView) ?: 0
                                    val clipBottom = mClipBottomAmountField?.getInt(bgView) ?: 0
                                    val actualH = mActualHeightField?.getInt(bgView) ?: bgView.height
                                    val actualW = mActualWidthField?.getInt(bgView) ?: bgView.width
                                    val left = 0
                                    val right = if (actualW > 0) actualW else bgView.width
                                    val top = clipTop
                                    val bottom = (if (actualH > 0) actualH else bgView.height) - clipBottom
                                    val density = bgView.resources.displayMetrics.density
                                    val defaultRadiusPx = (config.notifCardCornerRadiusDp * density).coerceIn(16f, 120f)
                                    val radii = mCornerRadiiField?.get(bgView) as? FloatArray
                                    val rTop = if (radii != null && radii.size >= 8) radii[0] else defaultRadiusPx
                                    val rBottom = if (radii != null && radii.size >= 8) radii[4] else defaultRadiusPx
                                    ensureBlurDrawable(bgView, config.notifCardBlurRadius.coerceAtLeast(60), 255, rTop, rBottom, left, top, right, bottom)
                                }
                            } else if (config.unifiedNotifBackdropBlur) {
                                // In Unified mode, window backdrop blur behind shade handles blurring natively.
                                // Clear any attached mBackgroundBlurDrawable to prevent sharp rectangular edges
                                try {
                                    val curBlur = mBackgroundBlurDrawableField?.get(bgView)
                                    if (curBlur != null) {
                                        mBackgroundBlurDrawableField?.set(bgView, null)
                                    }
                                } catch (_: Throwable) {}
                            } else {
                                val blurEnabled = config.notifCardBlurEnabled
                                val blurRadius = config.notifCardBlurRadius
                                val targetAlphaInt = (config.notifCardAlpha * 255).toInt().coerceIn(0, 255)
                                if (blurEnabled) {
                                    val clipTop = mClipTopAmountField?.getInt(bgView) ?: 0
                                    val clipBottom = mClipBottomAmountField?.getInt(bgView) ?: 0
                                    val actualH = mActualHeightField?.getInt(bgView) ?: bgView.height
                                    val actualW = mActualWidthField?.getInt(bgView) ?: bgView.width
                                    val left = 0
                                    val right = if (actualW > 0) actualW else bgView.width
                                    val top = clipTop
                                    val bottom = (if (actualH > 0) actualH else bgView.height) - clipBottom
                                    val density = bgView.resources.displayMetrics.density
                                    val defaultRadiusPx = (config.notifCardCornerRadiusDp * density).coerceIn(16f, 120f)
                                    val radii = mCornerRadiiField?.get(bgView) as? FloatArray
                                    val rTop = if (radii != null && radii.size >= 8) radii[0] else defaultRadiusPx
                                    val rBottom = if (radii != null && radii.size >= 8) radii[4] else defaultRadiusPx
                                    ensureBlurDrawable(bgView, blurRadius, targetAlphaInt, rTop, rBottom, left, top, right, bottom)
                                }
                            }

                            // 2. Modulate stock background drawable directly.
                            // NEVER draw manual substrates or custom paths on canvas!
                            // Nothing OS's mBackground already handles the exact squircle/pill geometry,
                            // grouped notification clipping, dividers, and ripple effects naturally.
                            val targetAlphaFloat = config.notifCardAlpha
                            val targetAlphaInt = (targetAlphaFloat * 255).toInt().coerceIn(0, 255)
                            try {
                                val bg = mBackgroundField?.get(bgView) as? Drawable
                                bg?.clearColorFilter()
                                val owner = mCornerAnomalyOwnerField?.get(bgView) ?: bgView.parent
                                val essential = try { XposedHelpers.getBooleanField(owner, "mIsEssentialBackground") }
                                    catch (_: Throwable) { false }
                                // The outlined section owns this background, avoiding double tint.
                                bg?.alpha = if (essential && !isHeadsUp && !isKeyguard(bgView)) 0 else targetAlphaInt
                            } catch (_: Throwable) {}

                            // 3. Modulate the notification icon's white circular background badge!
                            try {
                                val row = bgView.parent as? ViewGroup
                                if (row != null) {
                                    val targetIconAlpha = (config.notifIconAlphaPercent / 100f).coerceIn(0.2f, 1f)
                                    val targetIconAlphaInt = (targetIconAlpha * 255).toInt().coerceIn(0, 255)

                                    val iconView = row.findViewById<View>(android.R.id.icon)
                                    if (iconView != null) {
                                        iconView.background?.alpha = targetIconAlphaInt
                                        if (Math.abs(iconView.alpha - targetIconAlpha) > 0.02f) {
                                            iconView.alpha = targetIconAlpha
                                        }
                                    }

                                    // Also check conversation icon container if present
                                    val convIconId = row.resources.getIdentifier("conversation_icon", "id", "com.android.systemui")
                                    if (convIconId > 0) {
                                        val convIcon = row.findViewById<View>(convIconId)
                                        convIcon?.background?.alpha = targetIconAlphaInt
                                        if (convIcon != null && Math.abs(convIcon.alpha - targetIconAlpha) > 0.02f) {
                                            convIcon.alpha = targetIconAlpha
                                        }
                                    }
                                }
                            } catch (_: Throwable) {}
                        } catch (_: Throwable) {}
                    }
                }
            )
            HookEntry.log("[+] Hooked NotificationBackgroundView.onDraw(Canvas)")
        } catch (t: Throwable) {
            HookEntry.logError("[-] Error hooking NotificationBackgroundView.onDraw", t)
        }

        // 2. setDrawableAlpha(int i)
        try {
            XposedHelpers.findAndHookMethod(
                bgViewClass,
                "setDrawableAlpha",
                Int::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val config = ConfigManager.current
                        if (!config.isEffectiveEnabled) return

                        val bgView = param.thisObject as? View ?: return
                        val isHeadsUp = checkIfHeadsUp(bgView)
                        val ratio = config.notifCardAlpha

                        val originalAlpha = param.args[0] as Int
                        val adjustedAlpha = (originalAlpha * ratio).toInt().coerceIn(0, 255)
                        param.args[0] = adjustedAlpha
                    }
                }
            )
            HookEntry.log("[+] Hooked NotificationBackgroundView.setDrawableAlpha()")
        } catch (t: Throwable) {
            HookEntry.logError("[-] Error hooking NotificationBackgroundView.setDrawableAlpha", t)
        }

        // 3. setBlurBackgroundEnabled(boolean z)
        try {
            XposedHelpers.findAndHookMethod(
                bgViewClass,
                "setBlurBackgroundEnabled",
                Boolean::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val config = ConfigManager.current
                        if (config.isEffectiveEnabled) {
                            val bgView = param.thisObject as? View
                            val isHeadsUp = if (bgView != null) checkIfHeadsUp(bgView) else false
                            if (isHeadsUp) {
                                param.args[0] = config.headsUpBlurEnabled
                            } else if (bgView != null && isKeyguard(bgView)) {
                                param.args[0] = config.notifGlassEnabled
                            } else if (config.unifiedNotifBackdropBlur) {
                                // Unified backdrop blur handles blur natively, no per-card blur drawable needed!
                                param.args[0] = false
                            } else if (config.notifCardBlurEnabled) {
                                param.args[0] = true
                            }
                        }
                    }
                }
            )
            HookEntry.log("[+] Hooked NotificationBackgroundView.setBlurBackgroundEnabled()")
        } catch (t: Throwable) {
            HookEntry.logError("[-] Error hooking NotificationBackgroundView.setBlurBackgroundEnabled", t)
        }
    }

    private fun hookActivatableNotificationView(classLoader: ClassLoader) {
        val activatableClass = XposedHelpers.findClassIfExists(
            "com.android.systemui.statusbar.notification.row.ActivatableNotificationView",
            classLoader
        ) ?: return

        // usesBlurredBackground()
        try {
            XposedHelpers.findAndHookMethod(
                activatableClass,
                "usesBlurredBackground",
                object : XC_MethodReplacement() {
                    override fun replaceHookedMethod(param: MethodHookParam): Any {
                        val config = ConfigManager.current
                        if (!config.isEffectiveEnabled) {
                            return XposedBridge.invokeOriginalMethod(param.method, param.thisObject, param.args)
                        }
                        val view = param.thisObject as? View
                        val isHeadsUp = try {
                            if (view != null && expandableRowClass?.isInstance(view) == true) {
                                (isHeadsUpMethod?.invoke(view) as? Boolean ?: false) || (isPinnedMethod?.invoke(view) as? Boolean ?: false)
                            } else false
                        } catch (_: Throwable) { false }

                        return if (isHeadsUp) {
                            config.headsUpBlurEnabled
                        } else if (view != null && isKeyguard(view)) {
                            config.notifGlassEnabled
                        } else if (config.unifiedNotifBackdropBlur) {
                            false
                        } else {
                            config.notifCardBlurEnabled
                        }
                    }
                }
            )
            HookEntry.log("[+] Hooked ActivatableNotificationView.usesBlurredBackground()")
        } catch (t: Throwable) {
            HookEntry.logError("[-] Error hooking ActivatableNotificationView.usesBlurredBackground", t)
        }
    }

    private fun hookStatusBarIconView(classLoader: ClassLoader) {
        val sbIconClass = XposedHelpers.findClassIfExists(
            "com.android.systemui.statusbar.StatusBarIconView",
            classLoader
        ) ?: return

        try {
            XposedHelpers.findAndHookMethod(
                sbIconClass,
                "onDraw",
                Canvas::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val config = ConfigManager.current
                        if (!config.isEffectiveEnabled) return
                        try {
                            val iv = param.thisObject as? ImageView ?: return
                            val d = iv.drawable ?: return
                            // Keep status-bar glyphs crisp; SystemUI still controls view transition alpha.
                            d.alpha = 255
                        } catch (_: Throwable) {}
                    }
                }
            )
            HookEntry.log("[+] Hooked StatusBarIconView.onDraw(Canvas)")
        } catch (t: Throwable) {
            HookEntry.logError("[-] Error hooking StatusBarIconView.onDraw", t)
        }
    }

    private fun isKeyguard(view: View): Boolean =
        (view.context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager)?.isKeyguardLocked == true

    private fun checkIfHeadsUp(bgView: View): Boolean {
        return try {
            val owner = (mCornerAnomalyOwnerField?.get(bgView) ?: bgView.parent) as? View ?: return false
            if (expandableRowClass?.isInstance(owner) == true) {
                val isHeadsUp = isHeadsUpMethod?.invoke(owner) as? Boolean ?: false
                val isPinned = isPinnedMethod?.invoke(owner) as? Boolean ?: false
                isHeadsUp || isPinned
            } else {
                val grandParent = owner.parent as? View
                if (grandParent != null && expandableRowClass?.isInstance(grandParent) == true) {
                    val isHeadsUp = isHeadsUpMethod?.invoke(grandParent) as? Boolean ?: false
                    val isPinned = isPinnedMethod?.invoke(grandParent) as? Boolean ?: false
                    isHeadsUp || isPinned
                } else false
            }
        } catch (_: Throwable) {
            false
        }
    }

    private fun hookExpandableNotificationRow(classLoader: ClassLoader) {
        val rowClass = expandableRowClass ?: return
        try {
            val dispatchDraw = rowClass.declaredMethods.find { it.name == "dispatchDraw" }
            if (dispatchDraw != null) {
                XposedBridge.hookMethod(dispatchDraw, object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        try {
                            val config = ConfigManager.current
                            if (!config.isEffectiveEnabled) return
                            val row = param.thisObject as? ViewGroup ?: return
                            val targetAlpha = (config.notifIconAlphaPercent / 100f).coerceIn(0.2f, 1f)
                            val targetAlphaInt = (targetAlpha * 255).toInt().coerceIn(0, 255)

                            // Modulate white circular badge and icon
                            val iconView = row.findViewById<View>(android.R.id.icon)
                            if (iconView != null) {
                                if (Math.abs(iconView.alpha - targetAlpha) > 0.02f) {
                                    iconView.alpha = targetAlpha
                                }
                                iconView.background?.alpha = targetAlphaInt
                                (iconView as? ImageView)?.drawable?.alpha = targetAlphaInt
                            }

                            val iconContainer = iconView?.parent as? View
                            if (iconContainer != null && iconContainer.id != View.NO_ID) {
                                val name = try { iconContainer.resources.getResourceEntryName(iconContainer.id) } catch (_: Throwable) { null }
                                if (name?.contains("icon") == true) {
                                    iconContainer.background?.alpha = targetAlphaInt
                                }
                            }
                        } catch (_: Throwable) {}
                    }
                })
                HookEntry.log("[+] Hooked ExpandableNotificationRow.dispatchDraw for icon translucency")
            }
        } catch (t: Throwable) {
            HookEntry.logError("[-] Error hooking ExpandableNotificationRow.dispatchDraw", t)
        }
    }

    private fun hookExpandedGroupHeader(loader: ClassLoader) {
        val clazz = XposedHelpers.findClassIfExists(
            "com.android.systemui.statusbar.notification.stack.NotificationChildrenContainer", loader
        ) ?: return
        // Both classic and bundle headers pass their expanded background through here.
        // Stock injects an opaque calculateBgColor() even when the cards are translucent.
        XposedBridge.hookAllMethods(clazz, "ntEssentialHeaderBackground", object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                val config = ConfigManager.current
                if (!config.isEffectiveEnabled || !config.notifGlassEnabled) return
                val background = param.args.firstOrNull() as? android.graphics.drawable.ColorDrawable ?: return
                val essential = try { XposedHelpers.getBooleanField(param.thisObject, "mIsEssentialBackground") }
                    catch (_: Throwable) { false }
                background.color = ColorUtils.setAlphaComponent(config.notifCardTintColorInt,
                    if (essential) 0 else (config.notifCardAlpha * 255).toInt())
            }
        })
        HookEntry.log("[+] Matched expanded group header opacity to notification glass")
    }

    private fun hookEssentialSections(loader: ClassLoader) {
        val clazz = XposedHelpers.findClassIfExists(
            "com.nothing.systemui.statusbar.notification.stack.SectionHeaderViewWithBackground", loader
        ) ?: return
        val refresh = { view: View ->
            val config = ConfigManager.current
            if (config.isEffectiveEnabled && config.notifGlassEnabled) {
                val plate = XposedHelpers.getObjectField(view, "drawable") as? android.graphics.drawable.GradientDrawable
                val color = ColorUtils.setAlphaComponent(config.notifCardTintColorInt, (config.notifCardAlpha * 255).toInt())
                if (plate != null && (plate.color?.defaultColor != color ||
                    XposedHelpers.getAdditionalInstanceField(view, "nothingGlassEssentialStyled") != true)) {
                    plate.setColor(color)
                    plate.alpha = 255
                    plate.setStroke(view.resources.displayMetrics.density.toInt().coerceAtLeast(1), Color.argb(200, 255, 255, 255))
                    XposedHelpers.setAdditionalInstanceField(view, "nothingGlassEssentialStyled", true)
                }
            }
        }
        XposedBridge.hookAllMethods(clazz, "onFinishInflate", object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                val view = param.thisObject as? View ?: return
                refresh(view)
                view.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                    private var listener: android.view.ViewTreeObserver.OnPreDrawListener? = null
                    override fun onViewAttachedToWindow(v: View) {
                        listener = android.view.ViewTreeObserver.OnPreDrawListener { refresh(v); true }
                        v.viewTreeObserver.addOnPreDrawListener(listener)
                    }
                    override fun onViewDetachedFromWindow(v: View) {
                        listener?.let { if (v.viewTreeObserver.isAlive) v.viewTreeObserver.removeOnPreDrawListener(it) }
                        listener = null
                    }
                })
            }
        })
        XposedBridge.hookAllMethods(clazz, "setForegroundColors", object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) { refresh(param.thisObject as View) }
        })
        HookEntry.log("[+] Styled Essential sections with translucent glass and white outlines")
    }

    private fun drawLocalGlass(view: View, canvas: Canvas): Boolean {
        val config = ConfigManager.current
        val owner = (mCornerAnomalyOwnerField?.get(view) as? View) ?: (view.parent as? View)
        val height = try { (owner?.let { XposedHelpers.callMethod(it, "getActualHeight") } as? Int) }
            catch (_: Throwable) { null }
        val actualHeight = height ?: (mActualHeightField?.getInt(view) ?: view.height)
        val actualWidth = mActualWidthField?.getInt(view)?.takeIf { it > 0 } ?: view.width
        val top = maxOf(mClipTopAmountField?.getInt(view) ?: 0,
            try { XposedHelpers.getIntField(view, "mTopOverlap") } catch (_: Throwable) { 0 })
        val bottomInset = maxOf(mClipBottomAmountField?.getInt(view) ?: 0,
            try { XposedHelpers.getIntField(view, "mBottomOverlap") } catch (_: Throwable) { 0 })
        var rect = XposedHelpers.getAdditionalInstanceField(view, "nothingGlassVisibleBounds") as? Rect
        if (rect == null) {
            rect = Rect()
            XposedHelpers.setAdditionalInstanceField(view, "nothingGlassVisibleBounds", rect)
        }
        canvas.getClipBounds(rect)
        val rightAligned = try { XposedHelpers.callMethod(view, "isAlignedToRight") as Boolean }
            catch (_: Throwable) { view.layoutDirection == View.LAYOUT_DIRECTION_RTL }
        val left = if (rightAligned) view.width - actualWidth else 0
        if (actualHeight <= top + bottomInset ||
            !rect.intersect(left, top, left + actualWidth, minOf(actualHeight, view.height) - bottomInset)) {
            (mBackgroundBlurDrawableField?.get(view) as? Drawable)?.setBounds(0, 0, 0, 0)
            return true
        }
        val radii = mCornerRadiiField?.get(view) as? FloatArray
        val nativeRadius = radii?.maxOrNull()?.takeIf { it > 0f }
            ?: (config.notifCardCornerRadiusDp * view.resources.displayMetrics.density)
        val radius = nativeRadius.coerceAtMost(minOf(rect.width(), rect.height()) / 2f)
        ensureBlurDrawable(view, config.notifCardBlurRadius.coerceAtLeast(60), 255,
            radius, radius, rect.left, rect.top, rect.right, rect.bottom)
        val blur = mBackgroundBlurDrawableField?.get(view) as? Drawable ?: return false
        // The local surface itself has the visible bounds and rounded corners;
        // do not let stock onDraw replace its bounds with the expanded layout height.
        blur.draw(canvas)
        return true
    }

    private fun ensureBlurDrawable(
        bgView: View,
        targetRadius: Int,
        targetAlpha: Int,
        rTop: Float,
        rBottom: Float,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int
    ) {
        try {
            var blurDrawable = mBackgroundBlurDrawableField?.get(bgView) as? Drawable
            if (blurDrawable == null) {
                val vri = XposedHelpers.callMethod(bgView, "getViewRootImpl") ?: return
                if (createBlurDrawableMethod == null) {
                    createBlurDrawableMethod = vri.javaClass.getMethod("createBackgroundBlurDrawable")
                }
                blurDrawable = createBlurDrawableMethod?.invoke(vri) as? Drawable
                if (blurDrawable != null) {
                    mBackgroundBlurDrawableField?.set(bgView, blurDrawable)
                    blurDrawable.callback = bgView

                    if (setBlurRadiusMethod == null) {
                        val bbdClass = blurDrawable.javaClass
                        try { setBlurRadiusMethod = bbdClass.getMethod("setBlurRadius", Int::class.javaPrimitiveType) } catch (_: Throwable) {}
                        try { setBlurColorMethod = bbdClass.getMethod("setColor", Int::class.javaPrimitiveType) } catch (_: Throwable) {}
                        try {
                            setBlurCornerRadiusMethod = bbdClass.getMethod(
                                "setCornerRadius",
                                Float::class.javaPrimitiveType, Float::class.javaPrimitiveType,
                                Float::class.javaPrimitiveType, Float::class.javaPrimitiveType,
                                Float::class.javaPrimitiveType, Float::class.javaPrimitiveType,
                                Float::class.javaPrimitiveType, Float::class.javaPrimitiveType
                            )
                        } catch (_: Throwable) {}
                    }
                    bgView.invalidate()
                }
            }

            if (blurDrawable != null) {
                if (setBlurRadiusMethod == null) {
                    setBlurRadiusMethod = blurDrawable.javaClass.getMethod("setBlurRadius", Int::class.javaPrimitiveType)
                    setBlurColorMethod = blurDrawable.javaClass.getMethod("setColor", Int::class.javaPrimitiveType)
                    setBlurCornerRadiusMethod = blurDrawable.javaClass.getMethod("setCornerRadius", *Array(8) { Float::class.javaPrimitiveType!! })
                }
                XposedHelpers.setAdditionalInstanceField(blurDrawable, "nothingGlassOwner", java.lang.ref.WeakReference(bgView))
                blurDrawable.setBounds(left, top, right, bottom)
                setBlurRadiusMethod?.invoke(blurDrawable, targetRadius)
                val config = ConfigManager.current
                val backdropColor = if (checkIfHeadsUp(bgView)) {
                    // Match the shade scrim plus the unchanged stock card drawn on top.
                    ColorUtils.setAlphaComponent(config.notifScrimTintColorInt, (config.notifScrimAlpha * 255).toInt())
                } else if (isKeyguard(bgView)) {
                    ColorUtils.setAlphaComponent(config.notifCardTintColorInt, (config.notifCardTintAlpha * 255).toInt())
                } else Color.TRANSPARENT
                setBlurColorMethod?.invoke(blurDrawable, backdropColor)
                blurDrawable.alpha = targetAlpha

                // Strictly enforce corner radius so SurfaceFlinger never renders sharp 90-degree boxes
                setBlurCornerRadiusMethod?.invoke(
                    blurDrawable,
                    rTop, rTop, rTop, rTop,
                    rBottom, rBottom, rBottom, rBottom
                )
            }
        } catch (_: Throwable) {}
    }

    private fun hookNotificationIcons(classLoader: ClassLoader) {
        val iconClassNames = listOf(
            "com.android.internal.widget.NotificationRowIconView",
            "com.android.internal.widget.CachingIconView"
        )

        for (className in iconClassNames) {
            val iconClass = XposedHelpers.findClassIfExists(className, classLoader) ?: continue
            try {
                XposedHelpers.findAndHookMethod(
                    iconClass,
                    "onDraw",
                    Canvas::class.java,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            val config = ConfigManager.current
                            if (!config.isEffectiveEnabled) return
                            try {
                                val iv = param.thisObject as? View ?: return
                                val targetAlpha = (config.notifIconAlphaPercent / 100f).coerceIn(0.2f, 1f)
                                if (Math.abs(iv.alpha - targetAlpha) > 0.02f) {
                                    iv.alpha = targetAlpha
                                }
                                val parent = iv.parent as? View
                                if (parent != null && parent.id != View.NO_ID) {
                                    val parentName = try { parent.resources.getResourceEntryName(parent.id) } catch (_: Throwable) { null }
                                    if (parentName?.contains("icon") == true && Math.abs(parent.alpha - targetAlpha) > 0.02f) {
                                        parent.alpha = targetAlpha
                                    }
                                }
                            } catch (_: Throwable) {}
                        }
                    }
                )
                HookEntry.log("[+] Hooked $className.onDraw(Canvas)")
            } catch (t: Throwable) {
                HookEntry.logError("[-] Error hooking $className.onDraw", t)
            }
        }
    }
}
