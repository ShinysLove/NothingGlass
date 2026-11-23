package com.nothing.glass.hooks

import android.content.Context
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Rect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.core.graphics.ColorUtils
import com.nothing.glass.config.GlassConfig
import java.lang.reflect.Method
import kotlin.math.roundToInt

internal object FrostedBackdrop {
    private class LucentPalette(val receiver: Any, val resolve: Method, val backdropName: String)

    private var palette: LucentPalette? = null

    fun bindPalette(classLoader: ClassLoader) {
        palette = runCatching {
            val type = classLoader.loadClass("com.nothing.commBase.lucent.LucentColorUtils")
            val receiver = type.getField("INSTANCE").get(null) ?: return
            val backdrop = type.getField("CONTAINER_FROSTED_BACKDROP").get(null) as String
            val resolve = type.getMethod("getLucentColor", Context::class.java, String::class.java)
            LucentPalette(receiver, resolve, backdrop)
        }.getOrNull()
    }

    fun tint(context: Context?, config: GlassConfig): Int {
        val fallback = config.tintColorInt
        val lucent = palette ?: return fallback
        if (context == null || config.tintHex != GlassConfig.WALLPAPER_MONET) return fallback
        val color = runCatching {
            lucent.resolve.invoke(lucent.receiver, context, lucent.backdropName) as Int
        }.getOrDefault(Color.TRANSPARENT)
        if (Color.alpha(color) == 0) return fallback
        return ColorUtils.blendARGB(fallback, ColorUtils.setAlphaComponent(color, 255), 0.55f)
    }

    fun radius(config: GlassConfig, expansion: Float): Int {
        val progress = if (config.animationEnabled) {
            (expansion * config.animationIntensityPercent / 100f).coerceIn(0f, 1f)
        } else {
            1f
        }
        val eased = progress * progress * (3f - 2f * progress)
        val quality = if (config.blurQualityPreset == 1) 0.85f else 1f
        return (config.blurRadius.coerceIn(0, 120) * eased * quality).roundToInt()
    }

    class TintShader {
        private var cached: Shader? = null
        private var top = 0
        private var bottom = 0
        private var lastTint = 0
        private var lastStrength = 0f
        private var lastHardware = false
        private var runtimeShaderAvailable = true

        fun get(bounds: Rect, tint: Int, strength: Float, hardware: Boolean): Shader {
            val previous = cached
            if (previous != null && top == bounds.top && bottom == bounds.bottom &&
                lastTint == tint && lastStrength == strength && lastHardware == hardware
            ) return previous

            top = bounds.top
            bottom = bounds.bottom
            lastTint = tint
            lastStrength = strength
            lastHardware = hardware
            val base = ColorUtils.setAlphaComponent(tint, 255)
            val lit = ColorUtils.blendARGB(base, Color.WHITE, 0.23f * strength)
            val shadow = ColorUtils.blendARGB(base, Color.BLACK, 0.16f * strength)
            val gpuShader = if (Build.VERSION.SDK_INT >= 33 && hardware && runtimeShaderAvailable) {
                try {
                    RuntimeShader(TINT_PROGRAM).apply {
                        setColorUniform("lit", lit)
                        setColorUniform("base", base)
                        setColorUniform("shadow", shadow)
                        setFloatUniform("edges", top.toFloat(), bottom.toFloat())
                        setFloatUniform("grainStrength", 0.018f * strength)
                    }
                } catch (_: IllegalArgumentException) {
                    runtimeShaderAvailable = false
                    null
                }
            } else {
                null
            }
            return (gpuShader ?: LinearGradient(
                0f, top.toFloat(),
                0f, bottom.toFloat(),
                intArrayOf(lit, base, shadow), floatArrayOf(0f, 0.45f, 1f),
                Shader.TileMode.CLAMP
            )).also { cached = it }
        }
    }

    // Draw the tint over the live backdrop blurred by the window compositor.
    private const val TINT_PROGRAM = """
        layout(color) uniform half4 lit;
        layout(color) uniform half4 base;
        layout(color) uniform half4 shadow;
        uniform float2 edges;
        uniform float grainStrength;

        half4 main(float2 p) {
            float y = clamp((p.y - edges.x) / (edges.y - edges.x), 0.0, 1.0);
            half3 rgb = mix(lit.rgb, base.rgb, smoothstep(0.0, 0.55, y));
            rgb = mix(rgb, shadow.rgb, smoothstep(0.38, 1.0, y));
            float grain = fract(dot(floor(p), float2(0.06711056, 0.00583715)));
            grain = fract(grain * 52.9829189) - 0.5;
            return half4(clamp(rgb + half3(grain * grainStrength), 0.0, 1.0), 1.0);
        }
    """
}
