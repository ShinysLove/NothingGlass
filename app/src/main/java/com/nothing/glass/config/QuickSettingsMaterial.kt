package com.nothing.glass.config

enum class QuickSettingsMaterial(
    val blurScale: Float,
    val tintScale: Float,
    val highlight: Float,
    val shadow: Float,
    val grain: Float,
    val sheen: Float
) {
    TRANSLUCENT(1f, 1f, 0.23f, 0.16f, 0.018f, 0f),
    LIQUID_GLASS(0.72f, 0.72f, 0.36f, 0.26f, 0.006f, 0.23f),
    ACRYLIC(1.42f, 1.27f, 0.12f, 0.12f, 0.068f, 0f),
    FROSTED_GLASS(1.18f, 1.1f, 0.31f, 0.19f, 0.032f, 0.03f);

    companion object {
        fun fromValue(value: Int): QuickSettingsMaterial = entries.getOrNull(value) ?: TRANSLUCENT
    }
}
