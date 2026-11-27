package com.nothing.blurshortcuts;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.util.Log;
import android.view.View;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;

final class PanelControlGlass {
    private static final String TAG = "NothingBlurPanels";
    private static final int DARK_FILL = 0x99242424;
    private static final int LIGHT_FILL = 0xb8f8f8f8;
    private static final int DARK_BORDER = 0x53ffffff;
    private static final int LIGHT_BORDER = 0xa6ffffff;

    private PanelControlGlass() {}

    static void install(ClassLoader loader) {
        try {
            hookNotificationFooter(loader);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            Log.w(TAG, "Notification footer glass unavailable", exception);
        }
        try {
            hookForegroundServicesChip(loader);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            Log.w(TAG, "Active apps chip glass unavailable", exception);
        }
        try {
            hookSecurityChip(loader);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            Log.w(TAG, "VPN and security chip glass unavailable", exception);
        }
        try {
            hookEditButton(loader);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            Log.w(TAG, "Quick Settings edit button glass unavailable", exception);
        }
    }

    private static void hookNotificationFooter(ClassLoader loader) throws ReflectiveOperationException {
        Class<?> footer = loader.loadClass("com.android.systemui.statusbar.notification.footer.ui.view.FooterView");
        Field settings = footer.getDeclaredField("mSettingsButton");
        settings.setAccessible(true);
        Field history = footer.getDeclaredField("mHistoryButton");
        history.setAccessible(true);
        Field clearAll = footer.getDeclaredField("mClearAllButton");
        clearAll.setAccessible(true);
        XposedBridge.hookMethod(footer.getDeclaredMethod("updateColors"), new XC_MethodHook() {
            private boolean loggedClearAll;

            @Override
            protected void afterHookedMethod(MethodHookParam param) throws ReflectiveOperationException {
                if (param.getThrowable() != null) return;
                View footerView = (View) param.thisObject;
                Context context = footerView.getContext();
                if (!PanelGlassHooks.isSystemBlurEnabled(context)) return;
                applyButtonGlass((View) settings.get(footerView), context);
                applyButtonGlass((View) history.get(footerView), context);
                if (applyButtonGlass((View) clearAll.get(footerView), context) && !loggedClearAll) {
                    loggedClearAll = true;
                    Log.i(TAG, "Notification clear-all glass applied");
                }
            }
        });
    }

    private static boolean applyButtonGlass(View button, Context context) {
        if (button == null || !(button.getBackground() instanceof RippleDrawable)) return false;
        RippleDrawable ripple = (RippleDrawable) button.getBackground().mutate();
        Drawable plate = ripple.getDrawable(0);
        if (!(plate instanceof GradientDrawable)) return false;

        boolean dark = isDark(context);
        ripple.clearColorFilter();
        ripple.setAlpha(255);
        GradientDrawable shape = (GradientDrawable) plate;
        shape.setColor(dark ? DARK_FILL : LIGHT_FILL);
        float density = context.getResources().getDisplayMetrics().density;
        shape.setStroke(Math.max(1, Math.round(0.75f * density)), dark ? DARK_BORDER : LIGHT_BORDER);
        button.invalidate();
        return true;
    }

    private static void hookForegroundServicesChip(ClassLoader loader) throws ReflectiveOperationException {
        Class<?> toolbar = loader.loadClass("com.android.systemui.qs.panels.ui.compose.toolbar.ToolbarKt");
        Class<?> model = loader.loadClass("com.android.systemui.qs.footer.domain.model."
                + "FooterTextButtonModel$FooterForegroundServicesButtonModel");
        Class<?> modifier = loader.loadClass("androidx.compose.ui.Modifier");
        Class<?> composer = loader.loadClass("androidx.compose.runtime.Composer");
        Class<?> function1 = loader.loadClass("kotlin.jvm.functions.Function1");
        Class<?> function3 = loader.loadClass("kotlin.jvm.functions.Function3");
        Class<?> shape = loader.loadClass("androidx.compose.ui.graphics.Shape");
        Class<?> border = loader.loadClass("androidx.compose.foundation.BorderStroke");
        Class<?> interactionSource = loader.loadClass("androidx.compose.foundation.interaction.MutableInteractionSource");
        Class<?> factory = loader.loadClass("com.android.systemui.animation.ComposableControllerFactory");
        Class<?> borderFactory = loader.loadClass("androidx.compose.foundation.BorderStrokeKt");
        Class<?> expandable = loader.loadClass("com.android.compose.animation.ExpandableKt");
        Method textPill = toolbar.getDeclaredMethod("ForegroundServicesTextPill",
                model, function1, modifier, composer, int.class, int.class);
        Method numberButton = toolbar.getDeclaredMethod("ForegroundServicesNumberButton",
                model, function1, modifier, composer, int.class, int.class);
        Method drawChip = expandable.getDeclaredMethod("Expandable-d-u11N4", long.class, shape,
                modifier, long.class, border, function1, String.class, interactionSource,
                boolean.class, boolean.class, factory, function3, composer, int.class, int.class, int.class);
        Method makeBorder = borderFactory.getDeclaredMethod("BorderStroke-cXLIe8U", float.class, long.class);
        Object darkBorder = makeBorder.invoke(null, 0.75f, packColor(DARK_BORDER));
        Object lightBorder = makeBorder.invoke(null, 0.75f, packColor(LIGHT_BORDER));
        ThreadLocal<Boolean> activeChip = new ThreadLocal<>();

        XposedBridge.deoptimizeMethod(textPill);
        XposedBridge.deoptimizeMethod(numberButton);
        XposedBridge.hookMethod(drawChip, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (!Boolean.TRUE.equals(activeChip.get())) return;
                Context context = PanelGlassHooks.getShadeContext();
                if (!PanelGlassHooks.isSystemBlurEnabled(context)) return;
                boolean dark = isDark(context);
                param.args[0] = packColor(dark ? DARK_FILL : LIGHT_FILL);
                param.args[4] = dark ? darkBorder : lightBorder;
                // Let Compose compare the replacement color and border on recomposition.
                param.args[13] = (Integer) param.args[13] & ~(0xe | (0xe << 12));
            }
        });
        XC_MethodHook markChip = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                param.setObjectExtra("previousActiveChip", activeChip.get());
                activeChip.set(true);
            }

            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                if (!Boolean.TRUE.equals(param.getObjectExtra("previousActiveChip"))) activeChip.remove();
            }
        };
        XposedBridge.hookMethod(textPill, markChip);
        XposedBridge.hookMethod(numberButton, markChip);
    }

    private static void hookSecurityChip(ClassLoader loader) throws ReflectiveOperationException {
        Class<?> security = loader.loadClass("com.android.systemui.qs.panels.ui.compose.toolbar.SecurityInfoKt");
        Class<?> model = loader.loadClass("com.android.systemui.qs.footer.ui.viewmodel.FooterActionsSecurityButtonViewModel");
        Class<?> modifier = loader.loadClass("androidx.compose.ui.Modifier");
        Class<?> composer = loader.loadClass("androidx.compose.runtime.Composer");
        Class<?> shape = loader.loadClass("androidx.compose.ui.graphics.Shape");
        Class<?> border = loader.loadClass("androidx.compose.foundation.BorderStroke");
        Class<?> expandable = loader.loadClass("com.android.systemui.animation.Expandable");
        Class<?> backgroundKt = loader.loadClass("androidx.compose.foundation.BackgroundKt");
        Class<?> borderKt = loader.loadClass("androidx.compose.foundation.BorderKt");
        Class<?> borderFactory = loader.loadClass("androidx.compose.foundation.BorderStrokeKt");
        Method fullPill = security.getDeclaredMethod("SecurityInfo$lambda$10$lambda$9",
                boolean.class, model, expandable, composer, int.class);
        Method background = backgroundKt.getDeclaredMethod("background-bw27NRU", modifier, long.class, shape);
        Method withBorder = borderKt.getDeclaredMethod("border", modifier, border, shape);
        Method makeBorder = borderFactory.getDeclaredMethod("BorderStroke-cXLIe8U", float.class, long.class);
        Object darkBorder = makeBorder.invoke(null, 0.75f, packColor(DARK_BORDER));
        Object lightBorder = makeBorder.invoke(null, 0.75f, packColor(LIGHT_BORDER));
        ThreadLocal<Boolean> activeChip = new ThreadLocal<>();
        Method marquee = loader.loadClass("androidx.compose.foundation.BasicMarqueeKt")
                .getDeclaredMethod("basicMarquee-1Mj1MLw$default", modifier, int.class, int.class,
                        int.class, int.class, loader.loadClass("androidx.compose.foundation.MarqueeSpacing"),
                        float.class, int.class, Object.class);
        VpnTextFade fade = new VpnTextFade(loader, modifier);

        XposedBridge.deoptimizeMethod(fullPill);
        XposedBridge.hookMethod(background, new XC_MethodHook() {
            private boolean logged;

            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                Context context = PanelGlassHooks.getShadeContext();
                if (Boolean.TRUE.equals(activeChip.get()) && PanelGlassHooks.isSystemBlurEnabled(context)) {
                    boolean dark = isDark(context);
                    param.setObjectExtra("securityChipBorder", dark ? darkBorder : lightBorder);
                    param.args[1] = packColor(dark ? DARK_FILL : LIGHT_FILL);
                }
            }

            @Override
            protected void afterHookedMethod(MethodHookParam param) throws ReflectiveOperationException {
                Object currentBorder = param.getObjectExtra("securityChipBorder");
                if (currentBorder != null && param.getResult() != null) {
                    param.setResult(withBorder.invoke(null, param.getResult(), currentBorder, param.args[2]));
                    if (!logged) {
                        logged = true;
                        Log.i(TAG, "Quick Settings security pill glass applied");
                    }
                }
            }
        });
        XposedBridge.hookMethod(marquee, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws ReflectiveOperationException {
                if (Boolean.TRUE.equals(activeChip.get())) param.args[0] = fade.addTo(param.args[0]);
            }
        });
        XposedBridge.hookMethod(fullPill, scoped(activeChip, 0));
    }

    private static void hookEditButton(ClassLoader loader) throws ReflectiveOperationException {
        Class<?> toolbar = loader.loadClass("com.android.systemui.qs.panels.ui.compose.toolbar.EditModeButtonKt");
        Class<?> model = loader.loadClass("com.android.systemui.qs.panels.ui.viewmodel.toolbar.EditModeButtonViewModel");
        Class<?> border = loader.loadClass("androidx.compose.foundation.BorderStroke");
        Class<?> shape = loader.loadClass("androidx.compose.ui.graphics.Shape");
        Class<?> composer = loader.loadClass("androidx.compose.runtime.Composer");
        Class<?> modifier = loader.loadClass("androidx.compose.ui.Modifier");
        Class<?> surfaceKt = loader.loadClass("androidx.compose.material3.SurfaceKt");
        Method pencil = toolbar.getDeclaredMethod("EditModeButton$lambda$20$lambda$19",
                model, border, shape, composer, int.class);
        Method surface = surfaceKt.getDeclaredMethod("Surface-o_FOJdg",
                loader.loadClass("kotlin.jvm.functions.Function0"), modifier, boolean.class, shape,
                long.class, long.class, float.class, float.class, border,
                loader.loadClass("androidx.compose.foundation.interaction.MutableInteractionSource"),
                loader.loadClass("kotlin.jvm.functions.Function2"), composer, int.class, int.class, int.class);
        ThreadLocal<Boolean> activePencil = new ThreadLocal<>();
        XposedBridge.deoptimizeMethod(pencil);
        XposedBridge.hookMethod(pencil, scoped(activePencil, -1));
        XposedBridge.hookMethod(surface, new XC_MethodHook() {
            private boolean logged;

            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (!Boolean.TRUE.equals(activePencil.get()) || param.args[8] == null) return;
                Context context = PanelGlassHooks.getShadeContext();
                if (!PanelGlassHooks.isSystemBlurEnabled(context)) return;
                param.args[4] = packColor(isDark(context) ? DARK_FILL : LIGHT_FILL);
                param.args[12] = (Integer) param.args[12] & ~(0xe << 12);
                if (!logged) {
                    logged = true;
                    Log.i(TAG, "Quick Settings pencil glass applied");
                }
            }
        });
    }

    private static XC_MethodHook scoped(ThreadLocal<Boolean> active, int collapsedArg) {
        return new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                param.setObjectExtra("previousControlScope", active.get());
                if (collapsedArg < 0 || Boolean.FALSE.equals(param.args[collapsedArg])) active.set(true);
                else active.remove();
            }

            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                if (Boolean.TRUE.equals(param.getObjectExtra("previousControlScope"))) active.set(true);
                else active.remove();
            }
        };
    }

    private static boolean isDark(Context context) {
        return (context.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
    }

    private static long packColor(int color) {
        return (long) color << 32;
    }
}
