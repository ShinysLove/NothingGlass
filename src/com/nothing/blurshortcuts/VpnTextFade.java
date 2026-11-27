package com.nothing.blurshortcuts;

import android.graphics.RenderEffect;
import android.graphics.RuntimeShader;
import android.util.Log;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/** Adds a soft crop to the moving label while keeping the VPN icon and pill separate. */
final class VpnTextFade {
    private static final String TAG = "NothingBlurPanels";
    private static final String SHADER = """
            uniform shader label;
            uniform float width;
            uniform float band;

            half4 main(float2 p) {
                float edge = min(p.x, width - p.x);
                if (edge <= 0.0) return half4(0.0);
                half4 original = label.eval(p);
                if (edge >= band) return original;

                float visibility = smoothstep(0.0, band, edge);
                float offset = (1.0 - visibility) * band * 0.13;
                float2 left = float2(max(0.5, p.x - offset), p.y);
                float2 right = float2(min(width - 0.5, p.x + offset), p.y);
                half4 softened = original * 0.54 + (label.eval(left) + label.eval(right)) * 0.23;
                return softened * half(visibility);
            }
            """;

    private final Method graphicsLayer;
    private final Object configureLayer;

    VpnTextFade(ClassLoader loader, Class<?> modifier) throws ReflectiveOperationException {
        Class<?> function = loader.loadClass("kotlin.jvm.functions.Function1");
        Class<?> layer = loader.loadClass("androidx.compose.ui.graphics.GraphicsLayerScope");
        Class<?> composeEffect = loader.loadClass("androidx.compose.ui.graphics.RenderEffect");
        Method getSize = layer.getMethod("getSize-NH-jbRc");
        Method getDensity = layer.getMethod("getDensity");
        Method setEffect = layer.getMethod("setRenderEffect", composeEffect);
        Constructor<?> wrapEffect = loader.loadClass("androidx.compose.ui.graphics.AndroidRenderEffect")
                .getConstructor(RenderEffect.class);
        Object unit = loader.loadClass("kotlin.Unit").getField("INSTANCE").get(null);
        graphicsLayer = loader.loadClass("androidx.compose.ui.graphics.GraphicsLayerModifierKt")
                .getMethod("graphicsLayer", modifier, function);
        configureLayer = Proxy.newProxyInstance(loader, new Class<?>[] { function },
                new java.lang.reflect.InvocationHandler() {
                    private RuntimeShader shader;
                    private Object effect;
                    private float cachedWidth;
                    private float cachedBand;
                    private boolean failed;
                    private boolean logged;

                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args) {
                        switch (method.getName()) {
                            case "hashCode": return System.identityHashCode(proxy);
                            case "equals": return proxy == args[0];
                            case "toString": return "VPN label edge fade";
                            case "invoke": break;
                            default: return unit;
                        }
                        if (failed) return unit;
                        try {
                            Object layerScope = args[0];
                            long size = (Long) getSize.invoke(layerScope);
                            float width = Float.intBitsToFloat((int) (size >>> 32));
                            if (!Float.isFinite(width) || width < 2f) return unit;
                            float band = Math.min(width / 4f, 11f * (Float) getDensity.invoke(layerScope));
                            if (effect == null || width != cachedWidth || band != cachedBand) {
                                if (shader == null) shader = new RuntimeShader(SHADER);
                                shader.setFloatUniform("width", width);
                                shader.setFloatUniform("band", band);
                                effect = wrapEffect.newInstance(RenderEffect.createRuntimeShaderEffect(shader, "label"));
                                cachedWidth = width;
                                cachedBand = band;
                            }
                            setEffect.invoke(layerScope, effect);
                            if (!logged) {
                                logged = true;
                                Log.i(TAG, "Quick Settings VPN label edge fade applied");
                            }
                        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
                            failed = true;
                            Log.w(TAG, "Quick Settings VPN label edge fade failed", exception);
                        }
                        return unit;
                    }
                });
    }

    Object addTo(Object modifier) throws ReflectiveOperationException {
        return graphicsLayer.invoke(null, modifier, configureLayer);
    }
}
