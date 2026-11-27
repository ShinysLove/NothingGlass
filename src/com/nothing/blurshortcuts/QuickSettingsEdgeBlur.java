package com.nothing.blurshortcuts;

import android.graphics.RenderEffect;
import android.graphics.RuntimeShader;
import android.util.Log;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

final class QuickSettingsEdgeBlur {
    private static final String TAG = "NothingBlurPanels";
    private static final String SHADE = "com.android.systemui.shade.ui.composable.";
    private static final String SCENE = "com.android.compose.animation.scene.";
    private static final String MECHANICS = "com.android.mechanics.behavior.";
    private static final String SPEC_STATE = "nothing.blurpanels.qsEdgeState";
    private static final String LAYER_EFFECT = "nothing.blurpanels.qsEdgeEffect";

    private static final String SHADER = """
            uniform shader panel;
            uniform float2 viewport;
            uniform float band;
            uniform float strength;

            half4 samplePanel(float2 p) {
                return panel.eval(clamp(p, float2(0.5), viewport - float2(0.5)));
            }

            half4 main(float2 p) {
                float distance = viewport.y - p.y;
                if (distance <= 0.0) return half4(0.0);
                half4 original = samplePanel(p);
                if (distance >= band) return original;

                float edge = 1.0 - smoothstep(0.0, band, distance);
                float radius = (band * 0.30) * edge * strength;
                float2 x = float2(radius, 0.0);
                float2 y = float2(0.0, radius);
                half4 softened = original * 0.20;
                softened += (samplePanel(p + x) + samplePanel(p - x)
                            + samplePanel(p + y) + samplePanel(p - y)) * 0.12;
                softened += (samplePanel(p + x + y) + samplePanel(p + x - y)
                            + samplePanel(p - x + y) + samplePanel(p - x - y)) * 0.08;
                float opacity = 1.0 - strength * (1.0 - smoothstep(0.0, band * 0.62, distance));
                return mix(original, softened, half(edge * strength)) * half(opacity);
            }
            """;

    private QuickSettingsEdgeBlur() {}

    static void install(ClassLoader loader) throws ReflectiveOperationException {
        Class<?> overlay = loader.loadClass(SHADE + "OverlayShade");
        Class<?> overlayKt = loader.loadClass(SHADE + "OverlayShadeKt");
        Class<?> panelElements = loader.loadClass("com.android.systemui.qs.ui.composable.QuickSettingsShade$Elements");
        Object qsPanel = panelElements.getMethod("getPanel").invoke(panelElements.getField("INSTANCE").get(null));
        Class<?> overlays = loader.loadClass("com.android.systemui.scene.shared.model.Overlays");
        Object qsOverlay = XposedHelpers.getStaticObjectField(overlays, "QuickSettingsShade");
        Class<?> scope = loader.loadClass(SCENE + "BaseContentScope");
        Method getLayoutState = scope.getMethod("getLayoutState");
        Method getTransition = getLayoutState.getReturnType().getMethod("getTransitionState");
        Class<?> transition = loader.loadClass(SCENE + "content.state.TransitionState$Transition");
        Method getFrom = transition.getMethod("getFromContent");
        Method getTo = transition.getMethod("getToContent");
        Method getProgress = transition.getMethod("getProgress");
        Class<?> composer = loader.loadClass("androidx.compose.runtime.Composer");
        Method rememberSpec = overlay.getDeclaredMethod("rememberShadeExpansionMotion-ziNgDLE",
                boolean.class, float.class, composer, int.class);
        Method renderOverlay = namedMethod(overlayKt, "OverlayShade");

        Class<?> edgeNode = loader.loadClass(MECHANICS + "EdgeContainerExpansionBackgroundNode");
        Field nodeSpec = edgeNode.getDeclaredField("spec");
        Field nodeLayer = edgeNode.getDeclaredField("graphicsLayer");
        nodeSpec.setAccessible(true);
        nodeLayer.setAccessible(true);
        Class<?> drawScope = loader.loadClass("androidx.compose.ui.graphics.drawscope.ContentDrawScope");
        Method getSize = drawScope.getMethod("getSize-NH-jbRc");
        Method getDensity = drawScope.getMethod("getDensity");
        Class<?> composeEffect = loader.loadClass("androidx.compose.ui.graphics.RenderEffect");
        Constructor<?> wrapEffect = loader.loadClass("androidx.compose.ui.graphics.AndroidRenderEffect")
                .getConstructor(RenderEffect.class);
        Method setEffect = nodeLayer.getType().getMethod("setRenderEffect", composeEffect);
        Method drawEdge = edgeNode.getDeclaredMethod("draw", drawScope);
        ThreadLocal<Object> layoutState = new ThreadLocal<>();

        XposedBridge.deoptimizeMethod(renderOverlay);
        XposedBridge.hookMethod(renderOverlay, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws ReflectiveOperationException {
                param.setObjectExtra("previousQsLayoutState", layoutState.get());
                if (qsPanel.equals(param.args[1])) {
                    layoutState.set(getLayoutState.invoke(param.args[0]));
                } else {
                    layoutState.remove();
                }
            }

            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                Object previous = param.getObjectExtra("previousQsLayoutState");
                if (previous == null) layoutState.remove();
                else layoutState.set(previous);
            }
        });
        XposedBridge.hookMethod(rememberSpec, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                Object spec = param.getResult();
                Object state = layoutState.get();
                if (spec != null && state != null) {
                    XposedHelpers.setAdditionalInstanceField(spec, SPEC_STATE, state);
                }
            }
        });
        XposedBridge.hookMethod(drawEdge, new XC_MethodHook() {
            private boolean logged;
            private boolean failed;

            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (failed) return;
                try {
                    Object layer = nodeLayer.get(param.thisObject);
                    if (layer == null) return;
                    Object spec = nodeSpec.get(param.thisObject);
                    Object state = XposedHelpers.getAdditionalInstanceField(spec, SPEC_STATE);
                    EdgeEffect effect = (EdgeEffect) XposedHelpers.getAdditionalInstanceField(layer, LAYER_EFFECT);
                    float strength = 0f;
                    if (state != null) {
                        Object current = getTransition.invoke(state);
                        if (transition.isInstance(current)
                                && (qsOverlay.equals(getFrom.invoke(current)) || qsOverlay.equals(getTo.invoke(current)))) {
                            float progress = (Float) getProgress.invoke(current);
                            strength = Math.max(0f, Math.min(1f, Math.min(progress, 1f - progress) / 0.13f));
                        }
                    }
                    if (strength <= 0f) {
                        if (effect != null && effect.enabled) {
                            setEffect.invoke(layer, (Object) null);
                            effect.enabled = false;
                        }
                        return;
                    }

                    Object drawing = param.args[0];
                    long size = (Long) getSize.invoke(drawing);
                    float width = Float.intBitsToFloat((int) (size >>> 32));
                    float height = Float.intBitsToFloat((int) size);
                    if (width < 1f || height < 1f || !Float.isFinite(width) || !Float.isFinite(height)) return;
                    if (effect == null) {
                        effect = new EdgeEffect();
                        XposedHelpers.setAdditionalInstanceField(layer, LAYER_EFFECT, effect);
                    }
                    float band = Math.min(height, 56f * (Float) getDensity.invoke(drawing));
                    int alphaStep = Math.max(1, Math.round(strength * 16f));
                    if (effect.enabled && effect.size == size && effect.band == band && effect.alphaStep == alphaStep) return;
                    effect.shader.setFloatUniform("viewport", width, height);
                    effect.shader.setFloatUniform("band", band);
                    effect.shader.setFloatUniform("strength", alphaStep / 16f);
                    Object renderEffect = wrapEffect.newInstance(RenderEffect.createRuntimeShaderEffect(effect.shader, "panel"));
                    setEffect.invoke(layer, renderEffect);
                    effect.enabled = true;
                    effect.size = size;
                    effect.band = band;
                    effect.alphaStep = alphaStep;
                    if (!logged) {
                        logged = true;
                        Log.i(TAG, "Quick Settings edge blur rendered at panel height " + (int) height);
                    }
                } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
                    failed = true;
                    Log.w(TAG, "Quick Settings edge blur failed", exception);
                }
            }
        });
        Log.i(TAG, "Quick Settings edge blur hooks installed");
    }

    private static Method namedMethod(Class<?> type, String name) throws NoSuchMethodException {
        for (Method method : type.getDeclaredMethods()) {
            if (method.getName().equals(name)) return method;
        }
        throw new NoSuchMethodException(name);
    }

    private static final class EdgeEffect {
        final RuntimeShader shader = new RuntimeShader(SHADER);
        boolean enabled;
        long size;
        float band;
        int alphaStep;
    }
}
