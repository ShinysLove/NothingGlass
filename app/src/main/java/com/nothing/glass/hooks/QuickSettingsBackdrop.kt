package com.nothing.glass.hooks

import android.view.View
import com.nothing.glass.config.GlassConfig
import de.robv.android.xposed.XposedHelpers
import java.lang.ref.WeakReference

internal object QuickSettingsBackdrop {
    var expansion: Float = 0f
        private set

    private var controller = WeakReference<Any>(null)
    private var backdrop = WeakReference<View>(null)

    fun updateExpansion(fraction: Float) {
        if (fraction.isFinite()) expansion = fraction.coerceIn(0f, 1f)
    }

    fun alpha(config: GlassConfig): Float {
        val panel = config.qsCollapsedAlpha + (config.qsExpandedAlpha - config.qsCollapsedAlpha) * expansion
        val shade = config.shadeAlpha * panel
        val tint = (config.qsBackgroundAlpha * config.qsMaterial.tintScale).coerceIn(0f, 1f)
        return shade + (1f - shade) * tint
    }

    fun track(scrimController: Any, behind: View?) {
        if (controller.get() !== scrimController) controller = WeakReference(scrimController)
        if (backdrop.get() !== behind) backdrop = WeakReference(behind)
    }

    fun refresh() {
        val view = backdrop.get() ?: return
        view.post {
            controller.get()?.let { runCatching { XposedHelpers.callMethod(it, "scheduleUpdate") } }
            view.invalidate()
        }
    }
}
