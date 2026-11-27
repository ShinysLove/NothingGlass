package com.nothing.blurshortcuts;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.util.Log;
import android.view.WindowManager;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;

/** Styles the two shade surfaces while SystemUI owns their background blur and transitions. */
final class PanelGlassHooks {
    private static final String TAG = "NothingBlurPanels";
    private static final ThreadLocal<Object> tileBorder = new ThreadLocal<>();
    private static volatile Context shadeContext;
    private static volatile boolean notificationTransparencyEnabled;
    private static boolean dualShadeAvailable;

    private PanelGlassHooks() {}

    static void install(ClassLoader loader) {
        try {
            loader.loadClass("com.android.systemui.notifications.ui.composable.NotificationsShadeOverlay");
            loader.loadClass("com.android.systemui.qs.ui.composable.QuickSettingsShadeOverlay");
        } catch (ClassNotFoundException exception) {
            Log.i(TAG, "Dual shade classes unavailable");
            return;
        }
        dualShadeAvailable = true;

        try {
            ShadeDepthHooks.install(loader);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            Log.w(TAG, "Shade depth hooks unavailable", exception);
        }

        try {
            enableNativeShadeBlur(loader);
            Class<?> colors = loader.loadClass("com.android.systemui.shade.ui.ShadeColors");
            hookPanelTint(colors.getDeclaredMethod("shadePanel", Context.class, boolean.class, boolean.class), true);
            hookPanelTint(colors.getDeclaredMethod("notificationScrim", Context.class, boolean.class), false);
            Class<?> overlayColors = loader.loadClass("com.android.systemui.shade.ui.composable.OverlayShade$Colors");
            Class<?> composer = loader.loadClass("androidx.compose.runtime.Composer");
            Method backdrop = overlayColors.getDeclaredMethod("panelBackgroundOpaque-XeAY9LY",
                    boolean.class, composer, int.class);
            XposedBridge.hookMethod(backdrop, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    if (!(param.getResult() instanceof Long) || !Boolean.TRUE.equals(param.args[0])
                            || !isSystemBlurEnabled(shadeContext)) return;
                    long packed = (Long) param.getResult();
                    if ((packed & 0xffffffffL) != 0L) return;
                    param.setResult((packed & 0x00ffffffffffffffL) | (56L << 56));
                }
            });
            Log.i(TAG, "Notification and Quick Settings panel tint hooks installed");
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            Log.w(TAG, "Panel tint hooks unavailable", exception);
        }

        try {
            installTileGlass(loader);
            Log.i(TAG, "Quick Settings Compose tile hooks installed");
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            Log.w(TAG, "Compose tile hooks unavailable", exception);
        }

        PanelControlGlass.install(loader);
        try {
            QuickSettingsEdgeBlur.install(loader);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            Log.w(TAG, "Quick Settings edge blur unavailable", exception);
        }
    }

    private static void enableNativeShadeBlur(ClassLoader loader) throws ReflectiveOperationException {
        Class<?> flags = loader.loadClass("com.android.systemui.Flags");
        Class<?> notifications = loader.loadClass(
                "com.android.systemui.notifications.ui.viewmodel.NotificationsShadeOverlayContentViewModel");
        Field blurInteractor = notifications.getDeclaredField("windowRootViewBlurInteractor");
        blurInteractor.setAccessible(true);
        Method blurState = blurInteractor.getType().getMethod("isBlurCurrentlySupported");
        Method stateValue = blurState.getReturnType().getMethod("getValue");

        // SystemUI still checks hardware support, blur availability and lockscreen transitions.
        XposedBridge.hookMethod(flags.getDeclaredMethod("notificationShadeBlur"), new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                param.setResult(true);
            }
        });
        XposedBridge.hookMethod(notifications.getDeclaredMethod("isTransparencyEnabled"), new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws ReflectiveOperationException {
                if (!(param.getResult() instanceof Boolean)) return;
                Object interactor = blurInteractor.get(param.thisObject);
                Object state = blurState.invoke(interactor);
                notificationTransparencyEnabled = Boolean.TRUE.equals(stateValue.invoke(state));
                if (notificationTransparencyEnabled) param.setResult(true);
            }
        });
    }

    private static void hookPanelTint(Method method, boolean shadePanel) {
        XposedBridge.hookMethod(method, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                if (!(param.getResult() instanceof Integer)) return;
                Context context = (Context) param.args[0];
                boolean transparency = Boolean.TRUE.equals(param.args[1]);
                shadeContext = context.getApplicationContext();
                if (!transparency || !isSystemBlurEnabled(context)) return;

                int color = (Integer) param.getResult();
                boolean dark = (context.getResources().getConfiguration().uiMode
                        & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
                int maxAlpha = shadePanel ? (dark ? 110 : 128) : (dark ? 106 : 137);
                param.setResult((color & 0x00ffffff) | (Math.min(Color.alpha(color), maxAlpha) << 24));
            }
        });
    }

    static boolean isNotificationBackdropEnabled(Context context) {
        return notificationTransparencyEnabled && isSystemBlurEnabled(context);
    }

    static Context getShadeContext() {
        return shadeContext;
    }

    static boolean isSystemBlurEnabled(Context context) {
        if (!dualShadeAvailable || context == null) return false;
        WindowManager windows = context.getSystemService(WindowManager.class);
        return windows != null && windows.isCrossWindowBlurEnabled();
    }

    private static void installTileGlass(ClassLoader loader) throws ReflectiveOperationException {
        Class<?> tile = loader.loadClass("com.nothing.systemui.qs.workspace.workspace.QSTileOuterShellKt");
        Class<?> controller = loader.loadClass("com.android.compose.animation.ExpandableControllerKt");
        Class<?> borderFactory = loader.loadClass("androidx.compose.foundation.BorderStrokeKt");
        Class<?> modifier = loader.loadClass("androidx.compose.ui.Modifier");
        Class<?> composer = loader.loadClass("androidx.compose.runtime.Composer");
        Class<?> function0 = loader.loadClass("kotlin.jvm.functions.Function0");
        Class<?> function1 = loader.loadClass("kotlin.jvm.functions.Function1");
        Class<?> function5 = loader.loadClass("kotlin.jvm.functions.Function5");
        Class<?> tileSize = loader.loadClass("com.nothing.systemui.qs.workspace.workspace.TileSize");
        Class<?> shape = loader.loadClass("androidx.compose.ui.graphics.Shape");
        Class<?> borderStroke = loader.loadClass("androidx.compose.foundation.BorderStroke");
        Class<?> factory = loader.loadClass("com.android.systemui.animation.ComposableControllerFactory");

        Method renderTile = tile.getDeclaredMethod("QSTileOuterShell_nPbWocQ$lambda$29",
                float.class, long.class, modifier, String.class, String.class, Boolean.class,
                boolean.class, function0, function1, function1, float.class, float.class,
                tileSize, function5, composer, int.class);
        Method rememberController = controller.getDeclaredMethod("rememberExpandableController-T042LqI",
                function0, shape, long.class, borderStroke, factory, composer, int.class, int.class);
        Method makeBorder = borderFactory.getDeclaredMethod("BorderStroke-cXLIe8U", float.class, long.class);
        Object borderOnDark = makeBorder.invoke(null, 0.75f, 0x35ffffff00000000L);
        Object borderOnLight = makeBorder.invoke(null, 0.75f, 0x69ffffff00000000L);

        XposedBridge.hookMethod(rememberController, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                Object border = tileBorder.get();
                if (border != null && param.args[3] == null) {
                    param.args[3] = border;
                    // The Kotlin default mask would otherwise replace the supplied border with null.
                    param.args[7] = (Integer) param.args[7] & ~(1 << 3);
                }
            }
        });

        XposedBridge.hookMethod(renderTile, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                param.setObjectExtra("previousTileBorder", tileBorder.get());
                if (!isSystemBlurEnabled(shadeContext)) return;

                long packed = (Long) param.args[1];
                // Compose packs sRGB as ARGB in the upper half of its 64-bit color.
                if ((packed & 0xffffffffL) != 0L) return;
                int color = (int) (packed >>> 32);
                int alpha = Color.alpha(color);
                if (alpha == 0) return;

                boolean light = Color.luminance(color) >= 0.35f;
                int newAlpha = Math.min(alpha, light ? 206 : 166);
                param.args[1] = (packed & 0x00ffffffffffffffL) | ((long) newAlpha << 56);
                tileBorder.set(light ? borderOnLight : borderOnDark);
            }

            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                Object previous = param.getObjectExtra("previousTileBorder");
                if (previous == null) {
                    tileBorder.remove();
                } else {
                    tileBorder.set(previous);
                }
            }
        });
    }
}
