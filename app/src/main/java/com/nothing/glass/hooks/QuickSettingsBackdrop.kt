package com.nothing.glass.hooks

import android.view.View
import com.nothing.glass.config.GlassConfig
import com.nothing.glass.config.QuickSettingsMaterial
import de.robv.android.xposed.XposedHelpers
import java.lang.ref.WeakReference
import kotlin.math.roundToInt

internal object QuickSettingsBackdrop {
    var expansion: Float = 0f
        private set

    private var controller = WeakReference<Any>(null)
    private var backdrop = WeakReference<View>(null)

    fun updateExpansion(fraction: Float) {
        if (fraction.isFinite()) expansion = fraction.coerceIn(0f, 1f)
    }

    fun ease(fraction: Float): Float {
        val progress = fraction.coerceIn(0f, 1f)
        return progress * progress * (3f - 2f * progress)
    }

    fun mix(start: Float, end: Float, progress: Float): Float = start + (end - start) * progress

    fun alpha(config: GlassConfig): Float {
        val progress = ease(expansion)
        val panel = mix(config.qsCollapsedAlpha, config.qsExpandedAlpha, progress)
        val shade = config.shadeAlpha * panel
        val tintScale = mix(QuickSettingsMaterial.TRANSLUCENT.tintScale, config.qsMaterial.tintScale, progress)
        val tint = (config.qsBackgroundAlpha * tintScale).coerceIn(0f, 1f)
        return shade + (1f - shade) * tint
    }

    fun blurRadius(config: GlassConfig, stockRadius: Int, qsExpansion: Float): Int {
        if (config.blurRadius == 0) return 0
        val scale = mix(QuickSettingsMaterial.TRANSLUCENT.blurScale, config.qsMaterial.blurScale, ease(qsExpansion))
        val quality = if (config.blurQualityPreset == 1) 0.85f else 1f
        val radius = if (config.animationEnabled) {
            val intensity = (config.animationIntensityPercent / 100f).coerceIn(0f, 1f)
            stockRadius * ease(intensity)
        } else {
            config.blurRadius.toFloat()
        }
        return (radius * scale * quality).roundToInt().coerceIn(0, 120)
    }

    fun track(scrimController: Any, behind: View?) {
        if (controller.get() !== scrimController) controller = WeakReference(scrimController)
        if (backdrop.get() !== behind) backdrop = WeakReference(behind)
    }

    fun isIdleKeyguard(): Boolean = controller.get()?.let(ScrimHooks::isIdleKeyguard) == true

    fun refresh() {
        val view = backdrop.get() ?: return
        view.post {
            controller.get()?.let { runCatching { XposedHelpers.callMethod(it, "scheduleUpdate") } }
            view.invalidate()
        }
    }
}
