package com.nothing.blurshortcuts;

import android.util.Log;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;

final class ShadeDepthHooks {
    private static final String TAG = "NothingBlurPanels";
    private static final int MAX_SHADE_BLUR_PX = 165;

    private ShadeDepthHooks() {}

    static void install(ClassLoader loader) throws ReflectiveOperationException {
        Class<?> depth = loader.loadClass("com.android.systemui.statusbar.NotificationShadeDepthController");
        Field blurUtils = depth.getDeclaredField("blurUtils");
        blurUtils.setAccessible(true);
        Field shadeExpansion = depth.getDeclaredField("shadeExpansion");
        shadeExpansion.setAccessible(true);
        Field qsExpansion = depth.getDeclaredField("qsPanelExpansion");
        qsExpansion.setAccessible(true);

        Method supportsBlur = blurUtils.getType().getMethod("supportsBlursOnWindows");
        Method getMaxRadius = blurUtils.getType().getMethod("getMaxBlurRadius");
        Method opaqueShade = depth.getDeclaredMethod("getNtOpaqueShadeCoversScreen");
        Method computeDepth = depth.getDeclaredMethod("computeBlurAndZoomOut");
        Class<?> pair = computeDepth.getReturnType();
        Method getRadius = pair.getMethod("getFirst");
        Method getZoom = pair.getMethod("getSecond");
        Constructor<?> newDepth = pair.getConstructor(Object.class, Object.class);

        // NOS normally discards both blur and background scale at the end of a dual-shade swipe.
        // Deoptimize the callers so their compiled copies of this gate cannot bypass the hook.
        XposedBridge.deoptimizeMethod(depth.getDeclaredMethod("blurRadiusToZoomOut", float.class));
        XposedBridge.deoptimizeMethod(depth.getDeclaredMethod("getShouldBlurBeOpaque"));
        XposedBridge.deoptimizeMethod(computeDepth);
        XposedBridge.deoptimizeMethod(depth.getDeclaredMethod("scheduleUpdate"));
        XposedBridge.hookMethod(opaqueShade, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws ReflectiveOperationException {
                if (Boolean.TRUE.equals(param.getResult())
                        && Boolean.TRUE.equals(supportsBlur.invoke(blurUtils.get(param.thisObject)))) {
                    param.setResult(false);
                }
            }
        });

        XposedBridge.hookMethod(computeDepth, new XC_MethodHook() {
            private boolean loggedOpen;

            @Override
            protected void afterHookedMethod(MethodHookParam param) throws ReflectiveOperationException {
                Object stock = param.getResult();
                if (stock == null) return;
                float shade = shadeExpansion.getFloat(param.thisObject);
                float qs = qsExpansion.getFloat(param.thisObject);
                if (Math.max(shade, qs) < 0.01f) {
                    loggedOpen = false;
                    return;
                }

                int radius = (Integer) getRadius.invoke(stock);
                // The native calculation already fades out blur for app launches, AOD and the brightness mirror.
                if (radius <= 0) return;
                float maxStockRadius = (Float) getMaxRadius.invoke(blurUtils.get(param.thisObject));
                if (maxStockRadius <= 0f) return;
                int enhancedRadius = Math.max(radius, Math.min(MAX_SHADE_BLUR_PX,
                        Math.round(radius * MAX_SHADE_BLUR_PX / maxStockRadius)));
                Object zoom = getZoom.invoke(stock);
                param.setResult(newDepth.newInstance(enhancedRadius, zoom));
                if (!loggedOpen && shade >= 0.999f) {
                    loggedOpen = true;
                    Log.i(TAG, "Open shade depth: radius=" + enhancedRadius + "px zoom=" + zoom
                            + " qs=" + qs);
                }
            }
        });
        Log.i(TAG, "Shade depth hooks installed");
    }
}
