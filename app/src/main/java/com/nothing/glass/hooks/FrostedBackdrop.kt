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
import com.nothing.glass.config.QuickSettingsMaterial
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

    fun tint(context: Context?, config: GlassConfig): Int =
        resolveTint(context, config.tintHex, config.tintColorInt)

    fun quickSettingsTint(context: Context?, config: GlassConfig): Int =
        ColorUtils.blendARGB(tint(context, config), Color.WHITE, 0.15f * config.qsMaterial.highlight)

    fun notificationTint(context: Context, config: GlassConfig, headsUp: Boolean = false): Int {
        val spec = if (headsUp) config.headsUpTintHex else config.notifCardTintHex
        val fallback = if (headsUp) config.headsUpTintColorInt else config.notifCardTintColorInt
        return resolveTint(context, GlassConfig.normalizeNotificationTint(spec), fallback)
    }

    fun notificationScrimTint(context: Context?, config: GlassConfig): Int =
        resolveTint(
            context, GlassConfig.normalizeNotificationTint(config.notifScrimTintHex), config.notifScrimTintColorInt
        )

    private fun resolveTint(context: Context?, spec: String, fallback: Int): Int {
        val lucent = palette ?: return fallback
        if (context == null || spec != GlassConfig.WALLPAPER_MONET) return fallback
        val color = runCatching {
            lucent.resolve.invoke(lucent.receiver, context, lucent.backdropName) as Int
        }.getOrDefault(Color.TRANSPARENT)
        if (Color.alpha(color) == 0) return fallback
        return ColorUtils.blendARGB(fallback, ColorUtils.setAlphaComponent(color, 255), 0.55f)
    }

    fun radius(
        config: GlassConfig,
        expansion: Float,
        material: QuickSettingsMaterial = QuickSettingsMaterial.TRANSLUCENT
    ): Int {
        val progress = if (config.animationEnabled) {
            (expansion * config.animationIntensityPercent / 100f).coerceIn(0f, 1f)
        } else {
            1f
        }
        val eased = progress * progress * (3f - 2f * progress)
        val quality = if (config.blurQualityPreset == 1) 0.85f else 1f
        val maximum = (config.blurRadius * material.blurScale * quality).coerceIn(0f, 120f)
        return (maximum * eased).roundToInt()
    }

    class TintShader {
        private var cached: Shader? = null
        private val frame = Rect()
        private var lastTint = 0
        private var lastStrength = 0f
        private var lastHardware = false
        private var lastMaterial = QuickSettingsMaterial.TRANSLUCENT
        private var runtimeShaderAvailable = true

        fun get(
            bounds: Rect, tint: Int, strength: Float, hardware: Boolean,
            material: QuickSettingsMaterial = QuickSettingsMaterial.TRANSLUCENT
        ): Shader {
            val previous = cached
            if (previous != null && frame == bounds && lastTint == tint &&
                lastStrength == strength && lastHardware == hardware && lastMaterial == material
            ) return previous

            frame.set(bounds)
            lastTint = tint
            lastStrength = strength
            lastHardware = hardware
            lastMaterial = material
            val base = ColorUtils.setAlphaComponent(tint, 255)
            val lit = ColorUtils.blendARGB(base, Color.WHITE, material.highlight * strength)
            val shadow = ColorUtils.blendARGB(base, Color.BLACK, material.shadow * strength)
            val gpuShader = if (Build.VERSION.SDK_INT >= 33 && hardware && runtimeShaderAvailable) {
                try {
                    RuntimeShader(TINT_PROGRAM).apply {
                        setColorUniform("lit", lit)
                        setColorUniform("base", base)
                        setColorUniform("shadow", shadow)
                        setFloatUniform(
                            "frame", frame.left.toFloat(), frame.top.toFloat(),
                            frame.width().coerceAtLeast(1).toFloat(), frame.height().coerceAtLeast(1).toFloat()
                        )
                        setFloatUniform("optics", material.grain * strength, material.sheen * strength)
                    }
                } catch (_: IllegalArgumentException) {
                    runtimeShaderAvailable = false
                    null
                }
            } else {
                null
            }
            return (gpuShader ?: LinearGradient(
                0f, frame.top.toFloat(),
                0f, frame.bottom.toFloat(),
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
        uniform float4 frame;
        uniform float2 optics;

        half4 main(float2 p) {
            float2 point = clamp((p - frame.xy) / frame.zw, 0.0, 1.0);
            half3 rgb = mix(lit.rgb, base.rgb, smoothstep(0.0, 0.55, point.y));
            rgb = mix(rgb, shadow.rgb, smoothstep(0.38, 1.0, point.y));
            float rim = 1.0 - smoothstep(0.0, 0.11, min(min(point.x, 1.0 - point.x), point.y));
            float glint = 1.0 - smoothstep(0.02, 0.36, abs(point.x + point.y * 0.36 - 0.55));
            rgb += half3(0.80, 0.91, 1.0) * half(optics.y * (0.65 * rim + 0.28 * glint));
            float grain = fract(dot(floor(p), float2(0.06711056, 0.00583715)));
            grain = fract(grain * 52.9829189) - 0.5;
            return half4(clamp(rgb + half3(grain * optics.x), 0.0, 1.0), 1.0);
        }
    """
}
