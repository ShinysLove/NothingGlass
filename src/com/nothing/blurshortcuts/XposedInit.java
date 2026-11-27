package com.nothing.blurshortcuts;

import android.animation.ValueAnimator;
import android.app.Application;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.RuntimeShader;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.ViewGroup;
import android.view.ViewParent;

import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class XposedInit implements IXposedHookLoadPackage {

    private static final String TAG = "NothingBlurPanels";

    private static Class<?> drawableClass;
    private static Method setClipRadiusMethod;
    private static Method updateOffsetMethod;
    private static Method setAnimateEntranceMethod;
    private static Method completeTransitionNowMethod;
    private static Method onThemeChangedMethod;

    private static Class<?> providerClass;
    private static Object backdropProvider;

    // Cache drawables per shortcut slot
    private static Drawable leftDrawable;
    private static Drawable rightDrawable;
    private static volatile int lastUiMode = -1;

    // Status Bar & Shade State tracking
    private static volatile Context sAppContext = null;
    private static volatile View sNsslView = null;
    private static volatile View sKeyguardRootView = null;
    private static volatile float sKeyguardAlpha = 1.0f;
    private static volatile View sRootWindowView = null;
    private static volatile Object sStatusBarStateController = null;
    private static volatile Object sAmbientState = null;
    private static volatile Object sStatusBarKeyguardViewManager = null;
    private static volatile Object sKeyguardStateController = null;
    private static volatile Object sPrimaryBouncerInteractor = null;
    private static volatile Object sSysUiState = null;
    private static volatile int sStatusBarState = 1; // 1 = KEYGUARD, 0 = SHADE, 2 = SHADE_LOCKED
    private static volatile float sLsToShadeProgress = 0.0f;
    private static volatile float sFractionToShade = 0.0f;
    private static volatile boolean sScreenAwake = true;
    private static volatile boolean sBouncerShowing = false;
    private static volatile boolean sBouncerFullyShown = false;
    private static volatile boolean sInitialized = false;

    private static View getKeyguardRootView(View anyView) {
        if (sKeyguardRootView != null) return sKeyguardRootView;
        if (anyView != null) {
            try {
                View root = anyView.getRootView();
                if (root != null) {
                    int id = root.getResources().getIdentifier("keyguard_root_view", "id", "com.android.systemui");
                    if (id != 0) {
                        View krv = root.findViewById(id);
                        if (krv != null) {
                            sKeyguardRootView = krv;
                            return krv;
                        }
                    }
                }
            } catch (Throwable ignored) {}
        }
        return null;
    }

    private static void syncNotificationAlpha(float alpha) {
        if (sStatusBarState == 1 && sNsslView != null) {
            float current = sNsslView.getAlpha();
            if (Math.abs(current - alpha) > 0.005f) {
                sNsslView.setAlpha(alpha);
            }
            if (alpha < 0.03f) {
                if (!sNotificationBlursCleared) {
                    sNotificationBlursCleared = true;
                    clearAllNotificationBlurs();
                }
            } else if (alpha > 0.05f && sScreenAwake && !sBouncerFullyShown) {
                if (sNotificationBlursCleared) {
                    sNotificationBlursCleared = false;
                    invalidateNotificationStack();
                }
            }
        }
    }

    // Global tracking of all notification background views for instant zero-latency blur sleep/wake
    private static final Set<View> sAllNotificationBackgrounds = Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<View, Boolean>()));
    private static volatile Handler sMainHandler = null;

    private static Handler getMainHandler() {
        if (sMainHandler == null) {
            synchronized (XposedInit.class) {
                if (sMainHandler == null) {
                    try {
                        sMainHandler = new Handler(Looper.getMainLooper());
                    } catch (Throwable ignored) {}
                }
            }
        }
        return sMainHandler;
    }

    // Fast reusable draw objects (UI Thread only)
    private static final RectF sDrawRectF = new RectF();
    private static final Path sClipPath = new Path();
    private static final float[] sCornerRadii = new float[8];
    private static final Matrix sShortcutMatrix = new Matrix();
    private static final float[] sShortcutMatrixValues = new float[9];

    // Pre-reflected fast fields & methods for 120 FPS notification rendering
    private static final Map<View, ViewTreeObserver.OnPreDrawListener> sHunPreDrawListeners = new WeakHashMap<>();
    private static volatile boolean sNotificationBlursCleared = false;
    private static volatile boolean sAmbientStateLookupAttempted = false;

    private static Field sStateField = null;
    private static Field sLStoShadeProgressField = null;
    private static Field sFractionToShadeField = null;
    private static Field sBackgroundTopField = null;
    private static Field sActualWidthField = null;
    private static Field sActualHeightField = null;
    private static Field sClipTopAmountField = null;
    private static Field sClipBottomAmountField = null;
    private static Field sTopOverlapField = null;
    private static Field sBottomOverlapField = null;
    private static Field sExpandAnimationRunningField = null;
    private static Field sDrawDismissButtonCutoutField = null;
    private static Field sBackgroundBlurDrawableField = null;
    private static Field sBackgroundNormalField = null;
    private static Field sBackgroundDimmedField = null;
    private static Field sShowNoBackgroundField = null;
    private static Field sTintColorField = null;
    private static Field sNormalColorField = null;
    private static Field sOnKeyguardField = null;
    private static Field sBackgroundField = null;
    private static Field sCornerRadiiField = null;

    private static Method sIsShowNoBackgroundMethod = null;
    private static Method sGetActualHeightMethod = null;
    private static Method sCreateBackgroundBlurDrawableMethod = null;
    private static Method sSetBlurRadiusMethod = null;
    private static Method sSetCornerRadiusMethod = null;
    private static Method sSetCornerRadiusSingleMethod = null;
    private static Method sGetNormalColorMethod = null;
    private static Method sGetViewRootImplMethod = null;
    private static Method sGetFlagsMethod = null;
    private static Method sNsslIsExpandedMethod = null;

    private static Method sIsPinnedMethod = null;
    private static Method sIsPinnedAndExpandedMethod = null;
    private static Method sIsHeadsUpMethod = null;
    private static Method sIsHeadsUpStateMethod = null;
    private static Method sIsHeadsUpAnimatingAwayMethod = null;
    private static Method sIsOnKeyguardMethod = null;
    private static Method sIsColorizedMethod = null;
    private static Method sGetTopCornerRadiusMethod = null;
    private static Method sGetBottomCornerRadiusMethod = null;
    private static Method sSetColorMethod = null;
    private static Method sGetOutlineRectMethod = null;
    private static Method sGetOutlineAlphaMethod = null;
    private static Method sHasCustomOutlineMethod = null;
    private static Method sGetAppearAnimationFractionMethod = null;
    private static Method sIsDrawingAppearAnimationMethod = null;
    private static Method sIsChildInGroupMethod = null;
    private static Method sIsGroupExpandedMethod = null;
    private static Field sShelfActualWidthField = null;

    private static Object sLucentColorUtilsInstance = null;
    private static Method sGetLucentColorMethod = null;

    // Fast dynamic frosted color cache (invalidated on theme changes)
    private static volatile int sCachedFrostedColorDark = 0;
    private static volatile int sCachedFrostedColorLight = 0;
    private static volatile int sLastColorCacheUiMode = -1;

    // Fast Tag IDs for notification caching (application-specific range 0x7f0b91xx)
    private static final int TAG_KEY_ROW = 0x7f0b9101;
    private static final int TAG_KEY_IS_HUN = 0x7f0b9102;
    private static final int TAG_KEY_IS_COLORIZED = 0x7f0b9103;
    private static final int TAG_KEY_RIPPLE = 0x7f0b9104;
    private static final int TAG_KEY_BLUR_ACTIVE = 0x7f0b9105;
    private static final int TAG_KEY_LAST_BLUR_RADIUS = 0x7f0b9106;
    private static final int TAG_KEY_BLUR_DRAWABLE = 0x7f0b9107;
    private static final int TAG_KEY_FROSTED_DRAWABLE = 0x7f0b9108;
    private static final int TAG_KEY_LAST_UI_MODE = 0x7f0b9109;
    private static final int TAG_KEY_HUN_VELOCITY_Y = 0x7f0b910a;
    private static final int TAG_KEY_HUN_VELOCITY_X = 0x7f0b910b;
    private static final int TAG_KEY_ROW_BG = 0x7f0b910c;
    private static final int TAG_KEY_WAS_HUN = 0x7f0b910d;
    private static final int TAG_KEY_HUN_VELOCITY_TOP = 0x7f0b910e;
    private static final int TAG_KEY_HUN_VELOCITY_BOT = 0x7f0b910f;
    private static final int TAG_KEY_VOL_PREDRAW = 0x7f0b9110;

    // Cached Volume Dialog resource IDs for zero-overhead styling
    private static int sIdExpandContainer = -1;
    private static int sIdVolumeBg = -1;
    private static int sIdRingerBg = -1;
    private static int sIdCaptions = -1;
    private static int sIdA11y = -1;
    private static int sIdAssistant = -1;
    private static int sIdBackgroundDimmed = -1;
    private static int sIdChildrenContainer = -1;
    private static final int[] sScreenLocation = new int[2];

    // Cached Compose native canvas reflection
    private static Method sComposeGetInternalCanvasMethod = null;
    private static Field sComposeInternalCanvasField = null;
    private static volatile boolean sComposeCanvasLookupDone = false;

    private static final Paint glassTintPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Paint glassBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Paint solidBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Paint sHunGlassPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Paint sHunBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    // AGSL RuntimeShader for authentic Nothing OS frosted glass micro-grain & specular sheen
    private static final String HUN_FROSTED_AGSL =
            "uniform float2 uOrigin;\n" +
            "uniform float2 uSize;\n" +
            "uniform float uAlpha;\n" +
            "uniform float uIsDark;\n" +
            "half4 main(float2 fragCoord) {\n" +
            "    float2 uv = clamp((fragCoord - uOrigin) / max(uSize, float2(1.0)), 0.0, 1.0);\n" +
            "    float sheen = smoothstep(0.30, 0.0, uv.y) * mix(0.085, 0.055, uIsDark);\n" +
            "    float n = fract(sin(dot(floor(fragCoord), float2(12.9898, 78.233))) * 43758.5453);\n" +
            "    float grain = n - 0.5;\n" +
            "    float whiteA = (sheen + max(0.0, grain) * mix(0.048, 0.036, uIsDark)) * uAlpha;\n" +
            "    float darkA = max(0.0, -grain) * mix(0.018, 0.030, uIsDark) * uAlpha;\n" +
            "    float outA = clamp(whiteA + darkA, 0.0, 1.0);\n" +
            "    return half4(half3(clamp(whiteA, 0.0, 1.0)), half(outA));\n" +
            "}\n";
    private static Object sHunFrostedShader = null;
    private static boolean sHunShaderInitFailed = false;
    private static LinearGradient sCachedHunBorderGrad = null;
    private static int sCachedBorderL = -1, sCachedBorderT = -1, sCachedBorderR = -1, sCachedBorderB = -1;
    private static boolean sCachedBorderDark = false;

    static {
        glassTintPaint.setStyle(Paint.Style.FILL);
        glassBorderPaint.setStyle(Paint.Style.STROKE);
        glassBorderPaint.setStrokeWidth(2.0f); // 2px crisp specular border
        solidBgPaint.setStyle(Paint.Style.FILL);
        sHunGlassPaint.setStyle(Paint.Style.FILL);
        sHunBorderPaint.setStyle(Paint.Style.STROKE);
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (!lpparam.packageName.equals("com.android.systemui")) {
            return;
        }

        Log.i(TAG, "Loading in com.android.systemui");

        try {
            XposedBridge.hookAllMethods(Application.class, "onCreate", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    Application app = (Application) param.thisObject;
                    if (app != null && "com.android.systemui".equals(app.getPackageName())) {
                        sAppContext = app;
                        Log.e(TAG, "Application.onCreate called! Initializing with app ClassLoader.");
                        initSystemUI(app.getClassLoader());
                    }
                }
            });
        } catch (Throwable t) {
            Log.e(TAG, "Failed to hook Application.onCreate: " + t.getMessage());
        }

        initSystemUI(lpparam.classLoader);
    }

    private static synchronized void initSystemUI(ClassLoader cl) {
        if (cl == null || sInitialized) return;
        try {
            cl.loadClass("com.android.systemui.statusbar.notification.row.NotificationBackgroundView");
            sInitialized = true;
        } catch (Throwable ignored) {
            return;
        }

        PanelGlassHooks.install(cl);

        // 1. Enable SystemUI Lockscreen Blur Feature Flags
        try {
            Class<?> flagsClass = cl.loadClass("com.android.systemui.Flags");

            XposedBridge.hookAllMethods(flagsClass, "enableLockscreenBlur", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    param.setResult(true);
                }
            });

            Log.e(TAG, "Successfully enabled Flags.enableLockscreenBlur!");
        } catch (Throwable t) {
            Log.e(TAG, "Flags hook check: " + t.getMessage());
        }

        // 2. Discover FrostedBackdropDrawable and FrostedBackdropProvider
        try {
            providerClass = cl.loadClass("com.nothing.cardhost.frosted.FrostedBackdropProvider");
            XposedBridge.hookAllConstructors(providerClass, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    backdropProvider = param.thisObject;
                    Log.e(TAG, "Captured FrostedBackdropProvider via constructor: " + backdropProvider);
                }
            });
            try {
                Object companion = XposedHelpers.getStaticObjectField(providerClass, "Companion");
                backdropProvider = XposedHelpers.callMethod(companion, "getInstance");
            } catch (Throwable ignored) {}
            Log.e(TAG, "FrostedBackdropProvider instance: " + backdropProvider);

            drawableClass = cl.loadClass("com.nothing.commBase.frosted.stdview.FrostedBackdropDrawable");
            for (Method m : drawableClass.getDeclaredMethods()) {
                String name = m.getName();
                if (name.equals("setClipRadius")) setClipRadiusMethod = m;
                else if (name.equals("updateOffset")) updateOffsetMethod = m;
                else if (name.equals("setAnimateEntrance")) setAnimateEntranceMethod = m;
                else if (name.equals("completeTransitionNow")) completeTransitionNowMethod = m;
                else if (name.equals("onThemeChanged")) onThemeChangedMethod = m;
            }
            if (setClipRadiusMethod != null) setClipRadiusMethod.setAccessible(true);
            if (updateOffsetMethod != null) updateOffsetMethod.setAccessible(true);
            if (setAnimateEntranceMethod != null) setAnimateEntranceMethod.setAccessible(true);
            if (completeTransitionNowMethod != null) completeTransitionNowMethod.setAccessible(true);
            if (onThemeChangedMethod != null) onThemeChangedMethod.setAccessible(true);
            Log.e(TAG, "FrostedBackdropDrawable methods reflected successfully!");
        } catch (Throwable t) {
            Log.e(TAG, "Frosted classes check: " + t.getMessage());
        }

        try {
            Class<?> lucentCls = cl.loadClass("com.nothing.commBase.lucent.LucentColorUtils");
            sLucentColorUtilsInstance = XposedHelpers.getStaticObjectField(lucentCls, "INSTANCE");
            sGetLucentColorMethod = lucentCls.getMethod("getLucentColor", Context.class, String.class);
            sGetLucentColorMethod.setAccessible(true);
            Log.e(TAG, "LucentColorUtils reflected successfully!");
        } catch (Throwable t) {
            Log.e(TAG, "LucentColorUtils check: " + t.getMessage());
        }

        // 3. Hook Jetpack Compose Lockscreen Shortcuts: ShortcutElementProvider
        try {
            Class<?> shortcutProviderClass = cl.loadClass("com.android.systemui.keyguard.ui.composable.elements.ShortcutElementProvider");
            ClassLoader composeCl = shortcutProviderClass.getClassLoader();

            // Strip solid background plate from BackgroundKt
            try {
                Class<?> bgKtClass = composeCl.loadClass("androidx.compose.foundation.BackgroundKt");
                XC_MethodHook stripBgHook = new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        StackTraceElement[] st = Thread.currentThread().getStackTrace();
                        int maxCheck = Math.min(st.length, 12);
                        for (int i = 0; i < maxCheck; i++) {
                            if (st[i].getClassName().contains("ShortcutElementProvider")) {
                                param.setResult(param.args[0]);
                                return;
                            }
                        }
                    }
                };

                for (Method m : bgKtClass.getDeclaredMethods()) {
                    if (m.getName().startsWith("background")) {
                        try {
                            XposedBridge.hookMethod(m, stripBgHook);
                            Log.e(TAG, "Hooked BackgroundKt." + m.getName() + " to strip solid plate!");
                        } catch (Throwable t) {
                            Log.e(TAG, "Failed hooking BackgroundKt." + m.getName() + ": " + t);
                        }
                    }
                }
            } catch (Throwable t) {
                Log.e(TAG, "BackgroundKt hook error: " + t);
            }

            // Hook shortcutBackground directly as a second line of defense
            for (Method m : shortcutProviderClass.getDeclaredMethods()) {
                if (m.getName().equals("shortcutBackground")) {
                    try {
                        m.setAccessible(true);
                        XposedBridge.hookMethod(m, new XC_MethodHook() {
                            @Override
                            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                                param.setResult(param.args[0]);
                            }
                        });
                        Log.e(TAG, "Successfully hooked shortcutBackground method!");
                    } catch (Throwable t) {
                        Log.e(TAG, "Failed hooking shortcutBackground: " + t);
                    }
                }
            }

            // Force ONLY boolean isBlurCurrentlySupported state lambdas to return true
            for (Method m : shortcutProviderClass.getDeclaredMethods()) {
                if (m.getName().contains("lambda") && m.getReturnType() == boolean.class &&
                    (m.getName().contains("shortcutBackground") || m.getName().contains("drawBlurBehind"))) {
                    try {
                        XposedBridge.hookMethod(m, new XC_MethodHook() {
                            @Override
                            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                                param.setResult(true);
                            }
                        });
                        Log.e(TAG, "Hooked blur state boolean lambda: " + m.getName());
                    } catch (Throwable t) {
                        Log.e(TAG, "Failed hooking lambda " + m.getName() + ": " + t.getMessage());
                    }
                }
            }

            // Hook drawBlurBehind$lambda$49$lambda$48 (Compose background draw callback)
            for (Method m : shortcutProviderClass.getDeclaredMethods()) {
                if (m.getName().contains("lambda$49$lambda$48")) {
                    try {
                        XposedBridge.hookMethod(m, new XC_MethodHook() {
                            @Override
                            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                                try {
                                    Object drawScope = param.args[5];
                                    Object viewModel = param.args[3];
                                    
                                    // Extract native Canvas from Compose DrawScope
                                    Object drawContext = XposedHelpers.callMethod(drawScope, "getDrawContext");
                                    Object composeCanvas = XposedHelpers.callMethod(drawContext, "getCanvas");
                                    Canvas nativeCanvas = getNativeCanvas(composeCanvas);

                                    if (nativeCanvas == null) {
                                        param.setResult(null);
                                        return;
                                    }

                                    // Context & UI Theme Mode (Light vs Dark) detection
                                    Context ctx = sAppContext;
                                    if (ctx == null) {
                                        try {
                                            ctx = (Context) XposedHelpers.callStaticMethod(
                                                XposedHelpers.findClass("android.app.ActivityThread", null),
                                                "currentApplication"
                                            );
                                            if (ctx != null) sAppContext = ctx;
                                        } catch (Throwable ignored) {}
                                    }

                                    int currentUiMode = Configuration.UI_MODE_NIGHT_NO;
                                    if (ctx != null) {
                                        currentUiMode = ctx.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
                                    }
                                    boolean isDark = (currentUiMode == Configuration.UI_MODE_NIGHT_YES);

                                    // Respond to theme change in real-time
                                    if (lastUiMode != -1 && lastUiMode != currentUiMode) {
                                        Log.e(TAG, "Theme mode changed to " + (isDark ? "DARK" : "LIGHT") + "! Updating drawables.");
                                        if (onThemeChangedMethod != null) {
                                            try {
                                                if (leftDrawable != null) onThemeChangedMethod.invoke(leftDrawable);
                                                if (rightDrawable != null) onThemeChangedMethod.invoke(rightDrawable);
                                            } catch (Throwable ignored) {}
                                        }
                                        leftDrawable = null;
                                        rightDrawable = null;
                                    }
                                    lastUiMode = currentUiMode;

                                    long sizeBits = (Long) XposedHelpers.callMethod(drawScope, "getSize-NH-jbRc");
                                    float width = Float.intBitsToFloat((int) (sizeBits >> 32));
                                    float height = Float.intBitsToFloat((int) (sizeBits & 0xFFFFFFFFL));
                                    float radius = Math.min(width, height) / 2.0f;
                                    float cx = width / 2.0f;
                                    float cy = height / 2.0f;

                                    nativeCanvas.getMatrix(sShortcutMatrix);
                                    sShortcutMatrix.getValues(sShortcutMatrixValues);
                                    float screenX = sShortcutMatrixValues[Matrix.MTRANS_X];
                                    float screenY = sShortcutMatrixValues[Matrix.MTRANS_Y];

                                    Rect clip = nativeCanvas.getClipBounds();
                                    if (screenX == 0f && clip != null && clip.left > 0) {
                                        screenX = clip.left;
                                        screenY = clip.top;
                                    }

                                    boolean isLeftSlot = true;
                                    String vmStr = String.valueOf(viewModel);
                                    if (screenX > 500f || vmStr.contains("bottom_end") || vmStr.contains("end") || vmStr.contains("camera") || vmStr.contains("qr")) {
                                        isLeftSlot = false;
                                    }

                                    if (screenX <= 0f) {
                                        screenX = isLeftSlot ? 100f : 980f;
                                        screenY = 2550f;
                                    }

                                    // Refresh provider if needed
                                    if (backdropProvider == null && providerClass != null) {
                                        try {
                                            Object companion = XposedHelpers.getStaticObjectField(providerClass, "Companion");
                                            backdropProvider = XposedHelpers.callMethod(companion, "getInstance");
                                        } catch (Throwable ignored) {}
                                    }

                                    // Get or create FrostedBackdropDrawable per slot
                                    Drawable frosted = isLeftSlot ? leftDrawable : rightDrawable;
                                    if (frosted == null && backdropProvider != null && drawableClass != null && ctx != null) {
                                        try {
                                            frosted = (Drawable) XposedHelpers.newInstance(drawableClass, ctx, backdropProvider);
                                            if (frosted != null) {
                                                frosted.setCallback(null);
                                                if (setAnimateEntranceMethod != null) {
                                                    setAnimateEntranceMethod.invoke(frosted, false);
                                                }
                                                if (completeTransitionNowMethod != null) {
                                                    completeTransitionNowMethod.invoke(frosted);
                                                }
                                                if (onThemeChangedMethod != null) {
                                                    onThemeChangedMethod.invoke(frosted);
                                                }
                                                if (isLeftSlot) {
                                                    leftDrawable = frosted;
                                                } else {
                                                    rightDrawable = frosted;
                                                }
                                                Log.e(TAG, "Created FrostedBackdropDrawable for slot: " + (isLeftSlot ? "LEFT" : "RIGHT") + " (isDark=" + isDark + ")");
                                            }
                                        } catch (Throwable t) {
                                            Log.e(TAG, "Failed creating FrostedBackdropDrawable: " + t);
                                        }
                                    }

                                    // 1. Render authentic blurred wallpaper backdrop (exact same drawable used by lockscreen widgets)
                                    if (frosted != null) {
                                        if (setClipRadiusMethod != null) {
                                            setClipRadiusMethod.invoke(frosted, radius);
                                        }
                                        if (updateOffsetMethod != null) {
                                            updateOffsetMethod.invoke(frosted, screenX, screenY);
                                        }
                                        frosted.setBounds(0, 0, (int) width, (int) height);
                                        frosted.draw(nativeCanvas);
                                    } else {
                                        // Fallback if FrostedBackdropDrawable is not available
                                        int widgetColor = getFrostedBackdropColor(ctx, isDark);
                                        glassTintPaint.setColor(widgetColor);
                                        nativeCanvas.drawCircle(cx, cy, radius, glassTintPaint);
                                    }

                                } catch (Throwable t) {
                                    Log.e(TAG, "Error in drawBlurBehind lambda: " + t, t);
                                } finally {
                                    // CANCEL original method so BackgroundBlurDrawable does not draw opaque plate
                                    param.setResult(null);
                                }
                            }
                        });
                        Log.e(TAG, "Successfully hooked drawBehind draw lambda: " + m.getName());
                    } catch (Throwable t) {
                        Log.e(TAG, "Failed hooking drawBehind draw lambda: " + t);
                    }
                }
            }

            Log.e(TAG, "Successfully hooked Compose ShortcutElementProvider!");
        } catch (Throwable t) {
            Log.e(TAG, "ShortcutElementProvider hook check: " + t.getMessage());
        }

        // 4. Hook Volume Dialog (Android 17 / Nothing OS 5.0 volume architecture)
        try {
            ensureBlurReflection(cl);

            Class<?> volDialogClass = cl.loadClass("com.android.systemui.volume.dialog.VolumeDialog");
            Log.e(TAG, "Found VolumeDialog class: " + volDialogClass);

            XposedBridge.hookAllConstructors(volDialogClass, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    Log.e(TAG, "VolumeDialog constructor called!");
                }
            });

            XC_MethodHook volDecorHook = new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    android.app.Dialog dialog = null;
                    if (param.thisObject instanceof android.app.Dialog) {
                        dialog = (android.app.Dialog) param.thisObject;
                    } else if (param.thisObject != null) {
                        try {
                            Field dField = param.thisObject.getClass().getDeclaredField("mDialog");
                            dField.setAccessible(true);
                            Object dObj = dField.get(param.thisObject);
                            if (dObj instanceof android.app.Dialog) dialog = (android.app.Dialog) dObj;
                        } catch (Throwable ignored) {}
                        if (dialog == null) {
                            try {
                                Field dField = param.thisObject.getClass().getDeclaredField("dialog");
                                dField.setAccessible(true);
                                Object dObj = dField.get(param.thisObject);
                                if (dObj instanceof android.app.Dialog) dialog = (android.app.Dialog) dObj;
                            } catch (Throwable ignored) {}
                        }
                    }
                    if (dialog != null && dialog.getWindow() != null) {
                        final View decor = dialog.getWindow().getDecorView();
                        if (decor != null) {
                            styleVolumeDialog(decor);
                            if (decor.getTag(0x7f0b0b6a) == null) {
                                decor.setTag(0x7f0b0b6a, Boolean.TRUE);
                                decor.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
                                    @Override
                                    public void onLayoutChange(View v, int left, int top, int right, int bottom,
                                                               int oldLeft, int oldTop, int oldRight, int oldBottom) {
                                        styleVolumeDialog(v);
                                    }
                                });
                            }
                        }
                    }
                }
            };
            try { XposedBridge.hookAllMethods(volDialogClass, "onCreate", volDecorHook); } catch (Throwable ignored) {}
            try { XposedBridge.hookAllMethods(volDialogClass, "show", volDecorHook); } catch (Throwable ignored) {}
        } catch (Throwable t) {
            Log.e(TAG, "VolumeDialog class hook check: " + t.getMessage());
        }

        // Hook VolumeDialogImpl (handles mDialog in SystemUI)
        try {
            Class<?> volDialogImplClass = cl.loadClass("com.android.systemui.volume.VolumeDialogImpl");
            XC_MethodHook vdiHook = new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    try {
                        Field dField = param.thisObject.getClass().getDeclaredField("mDialog");
                        dField.setAccessible(true);
                        Object dObj = dField.get(param.thisObject);
                        if (dObj instanceof android.app.Dialog) {
                            android.app.Dialog dialog = (android.app.Dialog) dObj;
                            if (dialog.getWindow() != null) {
                                final View decor = dialog.getWindow().getDecorView();
                                if (decor != null) {
                                    styleVolumeDialog(decor);
                                    if (decor.getTag(0x7f0b0b6a) == null) {
                                        decor.setTag(0x7f0b0b6a, Boolean.TRUE);
                                        decor.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
                                            @Override
                                            public void onLayoutChange(View v, int left, int top, int right, int bottom,
                                                                       int oldLeft, int oldTop, int oldRight, int oldBottom) {
                                                styleVolumeDialog(v);
                                            }
                                        });
                                    }
                                }
                            }
                        }
                    } catch (Throwable ignored) {}
                }
            };
            try { XposedBridge.hookAllMethods(volDialogImplClass, "initDialog", vdiHook); } catch (Throwable ignored) {}
            try { XposedBridge.hookAllMethods(volDialogImplClass, "showH", vdiHook); } catch (Throwable ignored) {}
        } catch (Throwable t) {
            Log.e(TAG, "VolumeDialogImpl hook check: " + t.getMessage());
        }

        // Hook NTVolumeDialogSlidersViewBinder (Nothing OS custom slider view binder)
        try {
            Class<?> ntSlidersBinderCls = cl.loadClass("com.nothing.systemui.volume.dialog.sliders.ui.NTVolumeDialogSlidersViewBinder");
            for (Method m : ntSlidersBinderCls.getDeclaredMethods()) {
                m.setAccessible(true);
                XposedBridge.hookMethod(m, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        for (Object arg : param.args) {
                            if (arg instanceof View) {
                                styleVolumeDialog((View) arg);
                            }
                        }
                    }
                });
            }
            Log.e(TAG, "NTVolumeDialogSlidersViewBinder hooked for volume blur!");
        } catch (Throwable t) {
            Log.e(TAG, "NTVolumeDialogSlidersViewBinder hook check: " + t.getMessage());
        }

        // Hook VolumeDialogViewBinder to apply authentic Frosted Glass styling
        try {
            Class<?> binderCls = cl.loadClass("com.android.systemui.volume.dialog.ui.binder.VolumeDialogViewBinder");
            for (Method m : binderCls.getDeclaredMethods()) {
                m.setAccessible(true);
                if (m.getName().equals("applyAnimationProgress") || m.getName().equals("animateVisibility") || m.getName().equals("bind")) {
                    XposedBridge.hookMethod(m, new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            for (Object arg : param.args) {
                                if (arg instanceof View) {
                                    styleVolumeDialog((View) arg);
                                } else if (arg instanceof android.app.Dialog) {
                                    android.app.Dialog d = (android.app.Dialog) arg;
                                    if (d.getWindow() != null) {
                                        final View decor = d.getWindow().getDecorView();
                                        if (decor != null) {
                                            styleVolumeDialog(decor);
                                            if (decor.getTag(0x7f0b0b6a) == null) {
                                                decor.setTag(0x7f0b0b6a, Boolean.TRUE);
                                                decor.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
                                                    @Override
                                                    public void onLayoutChange(View v, int left, int top, int right, int bottom,
                                                                               int oldLeft, int oldTop, int oldRight, int oldBottom) {
                                                        styleVolumeDialog(v);
                                                    }
                                                });
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    });
                }
            }
            Log.e(TAG, "VolumeDialogViewBinder hooked for frosted glass styling!");
        } catch (Throwable t) {
            Log.e(TAG, "VolumeDialogViewBinder hook check: " + t.getMessage());
        }

        // Hook VolumeDialogRingerViewBinder to style ringer drawer
        try {
            Class<?> ringerBinderCls = cl.loadClass("com.android.systemui.volume.dialog.ringer.ui.binder.VolumeDialogRingerViewBinder");
            for (Method m : ringerBinderCls.getDeclaredMethods()) {
                m.setAccessible(true);
                if (m.getName().equals("updateBackground") || m.getName().equals("bind") || m.getName().equals("setIsBlurSupported")) {
                    XposedBridge.hookMethod(m, new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            for (Object arg : param.args) {
                                if (arg instanceof View) {
                                    styleVolumeDialog((View) arg);
                                }
                            }
                        }
                    });
                }
            }
            Log.e(TAG, "VolumeDialogRingerViewBinder hooked for frosted glass styling!");
        } catch (Throwable t) {
            Log.e(TAG, "VolumeDialogRingerViewBinder hook check: " + t.getMessage());
        }

        // Hook VolumeDialogSettingsButtonViewBinder
        try {
            Class<?> settingsBinderCls = cl.loadClass("com.android.systemui.volume.dialog.settings.ui.binder.VolumeDialogSettingsButtonViewBinder");
            for (Method m : settingsBinderCls.getDeclaredMethods()) {
                m.setAccessible(true);
                XposedBridge.hookMethod(m, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        for (Object arg : param.args) {
                            if (arg instanceof View) {
                                styleVolumeDialog((View) arg);
                            }
                        }
                    }
                });
            }
            Log.e(TAG, "VolumeDialogSettingsButtonViewBinder hooked for frosted glass styling!");
        } catch (Throwable t) {
            Log.e(TAG, "VolumeDialogSettingsButtonViewBinder hook check: " + t.getMessage());
        }

        // Hook VolumeDialogCaptionsButtonViewBinder to cleanly hide the Live Captions button
        try {
            Class<?> captionsBinderCls = cl.loadClass("com.android.systemui.volume.dialog.captions.ui.binder.VolumeDialogCaptionsButtonViewBinder");
            for (Method m : captionsBinderCls.getDeclaredMethods()) {
                m.setAccessible(true);
                if (m.getName().equals("bind$updateCaptionsContainer")) {
                    XposedBridge.hookMethod(m, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            if (param.args.length >= 4) {
                                param.args[2] = false; // isExpanded = false
                                param.args[3] = false; // isVisible = false
                            }
                        }
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            if (param.args.length > 0 && param.args[0] instanceof View) {
                                View v = (View) param.args[0];
                                v.setVisibility(View.GONE);
                            }
                        }
                    });
                }
            }
            Log.e(TAG, "VolumeDialogCaptionsButtonViewBinder hooked to hide Live Captions button!");
        } catch (Throwable t) {
            Log.e(TAG, "VolumeDialogCaptionsButtonViewBinder hook check: " + t.getMessage());
        }

        // Hook CaptionsToggleImageButton directly to keep it hidden
        try {
            Class<?> captionsBtnCls = cl.loadClass("com.android.systemui.volume.CaptionsToggleImageButton");
            XC_MethodHook hideBtnHook = new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    if (param.thisObject instanceof View) {
                        View btn = (View) param.thisObject;
                        btn.setVisibility(View.GONE);
                        ViewParent parent = btn.getParent();
                        if (parent instanceof View) {
                            ((View) parent).setVisibility(View.GONE);
                        }
                    }
                }
            };
            try { XposedBridge.hookAllMethods(captionsBtnCls, "onAttachedToWindow", hideBtnHook); } catch (Throwable ignored) {}
            Log.e(TAG, "CaptionsToggleImageButton hooked to hide captions button!");
        } catch (Throwable t) {
            Log.e(TAG, "CaptionsToggleImageButton hook check: " + t.getMessage());
        }

        // 5. Hook Notifications for Lockscreen & Heads-Up Frosted Blur Style
        try {
            // Instant zero-latency sleep/wake response
            try {
                Class<?> kvmCls = cl.loadClass("com.android.systemui.keyguard.KeyguardViewMediator");
                XC_MethodHook sleepKvmHook = new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        handleGoingToSleep();
                    }
                };
                XC_MethodHook wakeKvmHook = new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        handleWakingUp();
                    }
                };
                try { XposedBridge.hookAllMethods(kvmCls, "onStartedGoingToSleep", sleepKvmHook); } catch (Throwable ignored) {}
                try { XposedBridge.hookAllMethods(kvmCls, "handleNotifyStartedGoingToSleep", sleepKvmHook); } catch (Throwable ignored) {}
                try { XposedBridge.hookAllMethods(kvmCls, "onFinishedGoingToSleep", sleepKvmHook); } catch (Throwable ignored) {}
                try { XposedBridge.hookAllMethods(kvmCls, "onStartedWakingUp", wakeKvmHook); } catch (Throwable ignored) {}
                try { XposedBridge.hookAllMethods(kvmCls, "handleNotifyWakingUp", wakeKvmHook); } catch (Throwable ignored) {}
                try { XposedBridge.hookAllMethods(kvmCls, "onScreenTurningOn", wakeKvmHook); } catch (Throwable ignored) {}
                try { XposedBridge.hookAllMethods(kvmCls, "onScreenTurnedOn", wakeKvmHook); } catch (Throwable ignored) {}
                try { XposedBridge.hookAllMethods(kvmCls, "onScreenTurningOff", sleepKvmHook); } catch (Throwable ignored) {}
                try { XposedBridge.hookAllMethods(kvmCls, "onScreenTurnedOff", sleepKvmHook); } catch (Throwable ignored) {}
                XC_MethodHook bouncerDoneHook = new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        onBouncerShowingChanged(false);
                    }
                };
                try { XposedBridge.hookAllMethods(kvmCls, "sendUserPresent", bouncerDoneHook); } catch (Throwable ignored) {}
                try { XposedBridge.hookAllMethods(kvmCls, "keyguardDone", bouncerDoneHook); } catch (Throwable ignored) {}
                try { XposedBridge.hookAllMethods(kvmCls, "handleKeyguardDone", bouncerDoneHook); } catch (Throwable ignored) {}
                Log.e(TAG, "KeyguardViewMediator hooked for instant sleep/wake response!");
            } catch (Throwable t) {
                Log.e(TAG, "KeyguardViewMediator hook check: " + t.getMessage());
            }

            try {
                Class<?> kumCls = cl.loadClass("com.android.keyguard.KeyguardUpdateMonitor");
                XC_MethodHook sleepKumHook = new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        handleGoingToSleep();
                    }
                };
                XC_MethodHook wakeKumHook = new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        handleWakingUp();
                    }
                };
                try { XposedBridge.hookAllMethods(kumCls, "dispatchStartedGoingToSleep", sleepKumHook); } catch (Throwable ignored) {}
                try { XposedBridge.hookAllMethods(kumCls, "dispatchFinishedGoingToSleep", sleepKumHook); } catch (Throwable ignored) {}
                try { XposedBridge.hookAllMethods(kumCls, "dispatchStartedWakingUp", wakeKumHook); } catch (Throwable ignored) {}
                try { XposedBridge.hookAllMethods(kumCls, "dispatchScreenTurningOn", wakeKumHook); } catch (Throwable ignored) {}
                try { XposedBridge.hookAllMethods(kumCls, "dispatchScreenTurnedOn", wakeKumHook); } catch (Throwable ignored) {}
                try { XposedBridge.hookAllMethods(kumCls, "dispatchScreenTurningOff", sleepKumHook); } catch (Throwable ignored) {}
                try { XposedBridge.hookAllMethods(kumCls, "dispatchScreenTurnedOff", sleepKumHook); } catch (Throwable ignored) {}
            } catch (Throwable ignored) {}

            try {
                Class<?> slCls = cl.loadClass("com.android.systemui.keyguard.ScreenLifecycle");
                XC_MethodHook sleepSlHook = new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        handleGoingToSleep();
                    }
                };
                XC_MethodHook wakeSlHook = new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        handleWakingUp();
                    }
                };
                try { XposedBridge.hookAllMethods(slCls, "dispatchScreenTurningOff", sleepSlHook); } catch (Throwable ignored) {}
                try { XposedBridge.hookAllMethods(slCls, "dispatchScreenTurnedOff", sleepSlHook); } catch (Throwable ignored) {}
                try { XposedBridge.hookAllMethods(slCls, "dispatchScreenTurningOn", wakeSlHook); } catch (Throwable ignored) {}
                try { XposedBridge.hookAllMethods(slCls, "dispatchScreenTurnedOn", wakeSlHook); } catch (Throwable ignored) {}
            } catch (Throwable ignored) {}

            try {
                Class<?> wlCls = cl.loadClass("com.android.systemui.keyguard.WakefulnessLifecycle");
                XC_MethodHook sleepWlHook = new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        handleGoingToSleep();
                    }
                };
                XC_MethodHook wakeWlHook = new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        handleWakingUp();
                    }
                };
                try { XposedBridge.hookAllMethods(wlCls, "dispatchStartedGoingToSleep", sleepWlHook); } catch (Throwable ignored) {}
                try { XposedBridge.hookAllMethods(wlCls, "dispatchFinishedGoingToSleep", sleepWlHook); } catch (Throwable ignored) {}
                try { XposedBridge.hookAllMethods(wlCls, "dispatchStartedWakingUp", wakeWlHook); } catch (Throwable ignored) {}
                try { XposedBridge.hookAllMethods(wlCls, "dispatchFinishedWakingUp", wakeWlHook); } catch (Throwable ignored) {}
            } catch (Throwable ignored) {}

            // Track Bouncer (PIN/password screen) state to instantly remove blur when bouncer is active
            String[] sysUiStateClassNames = {
                "com.android.systemui.model.SysUiStateImpl",
                "com.android.systemui.model.SysUiState"
            };
            for (String clsName : sysUiStateClassNames) {
                try {
                    Class<?> targetCls = cl.loadClass(clsName);
                    try {
                        sGetFlagsMethod = targetCls.getMethod("getFlags");
                        sGetFlagsMethod.setAccessible(true);
                    } catch (Throwable ignored) {}
                    XposedBridge.hookAllConstructors(targetCls, new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            sSysUiState = param.thisObject;
                            Log.e(TAG, "SysUiState constructor captured: " + param.thisObject.getClass().getName());
                        }
                    });
                    XposedBridge.hookAllMethods(targetCls, "setFlag", new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            sSysUiState = param.thisObject;
                            if (param.args.length >= 2 && param.args[0] instanceof Long && param.args[1] instanceof Boolean) {
                                long flag = (Long) param.args[0];
                                boolean enabled = (Boolean) param.args[1];
                                if ((flag & 8L) != 0) {
                                    Log.e(TAG, "SysUiState.setFlag(8L, " + enabled + ")");
                                    onBouncerShowingChanged(enabled, "SysUiState.setFlag");
                                }
                            }
                        }
                    });
                    XposedBridge.hookAllMethods(targetCls, "commitUpdate", new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            sSysUiState = param.thisObject;
                            try {
                                long flags = (sGetFlagsMethod != null) ? (Long) sGetFlagsMethod.invoke(param.thisObject) : (Long) XposedHelpers.callMethod(param.thisObject, "getFlags");
                                boolean bouncer = (flags & 8L) != 0;
                                if (bouncer != sBouncerShowing) {
                                    Log.e(TAG, "SysUiState.commitUpdate bouncer=" + bouncer);
                                    onBouncerShowingChanged(bouncer, "SysUiState.commitUpdate");
                                }
                            } catch (Throwable ignored) {}
                        }
                    });
                } catch (Throwable ignored) {}
            }

            try {
                Class<?> scrimCls = cl.loadClass("com.android.systemui.statusbar.phone.ScrimController");
                XposedBridge.hookAllMethods(scrimCls, "transitionTo", new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        if (param.args.length > 0 && param.args[0] != null) {
                            String stateName = param.args[0].toString();
                            if (stateName.contains("BOUNCER")) {
                                Log.e(TAG, "ScrimController transitionTo BOUNCER: " + stateName);
                                onBouncerShowingChanged(true);
                            } else if (stateName.equals("KEYGUARD")) {
                                Log.e(TAG, "ScrimController transitionTo KEYGUARD");
                                onBouncerShowingChanged(false);
                            }
                        }
                    }
                });
            } catch (Throwable ignored) {}

            try {
                Class<?> skvmCls = cl.loadClass("com.android.systemui.statusbar.phone.StatusBarKeyguardViewManager");
                XposedBridge.hookAllConstructors(skvmCls, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        sStatusBarKeyguardViewManager = param.thisObject;
                    }
                });
                XC_MethodHook bouncerShowHook = new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        Log.e(TAG, "StatusBarKeyguardViewManager show hook: " + param.method.getName());
                        onBouncerShowingChanged(true);
                    }
                };
                XC_MethodHook bouncerHideHook = new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        Log.e(TAG, "StatusBarKeyguardViewManager hide hook: " + param.method.getName());
                        onBouncerShowingChanged(false);
                    }
                };
                try { XposedBridge.hookAllMethods(skvmCls, "showPrimaryBouncer", bouncerShowHook); } catch (Throwable ignored) {}
                try { XposedBridge.hookAllMethods(skvmCls, "showBouncer", bouncerShowHook); } catch (Throwable ignored) {}
                try {
                    XposedBridge.hookAllMethods(skvmCls, "showBouncerOrKeyguard", new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            try {
                                boolean b = (Boolean) XposedHelpers.callMethod(param.thisObject, "isBouncerShowing");
                                onBouncerShowingChanged(b);
                            } catch (Throwable ignored) {}
                        }
                    });
                } catch (Throwable ignored) {}
                try { XposedBridge.hookAllMethods(skvmCls, "beginShowingBouncer", bouncerShowHook); } catch (Throwable ignored) {}
                try { XposedBridge.hookAllMethods(skvmCls, "hideBouncer", bouncerHideHook); } catch (Throwable ignored) {}
                try { XposedBridge.hookAllMethods(skvmCls, "hideAlternateBouncer", bouncerHideHook); } catch (Throwable ignored) {}
                try {
                    XposedBridge.hookAllMethods(skvmCls, "setBouncerShowing", new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            if (param.args.length > 0 && param.args[0] instanceof Boolean) {
                                onBouncerShowingChanged((Boolean) param.args[0]);
                            }
                        }
                    });
                } catch (Throwable ignored) {}
                try {
                    XposedBridge.hookAllMethods(skvmCls, "onBouncerVisibilityChanged", new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            try {
                                boolean b = (Boolean) XposedHelpers.callMethod(param.thisObject, "isBouncerShowing");
                                onBouncerShowingChanged(b);
                            } catch (Throwable ignored) {}
                        }
                    });
                } catch (Throwable ignored) {}
            } catch (Throwable ignored) {}

            // Android 17 PrimaryBouncerInteractor & BouncerInteractor
            try {
                Class<?> pbiCls = cl.loadClass("com.android.systemui.bouncer.domain.interactor.PrimaryBouncerInteractor");
                XposedBridge.hookAllConstructors(pbiCls, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        sPrimaryBouncerInteractor = param.thisObject;
                    }
                });
                XposedBridge.hookAllMethods(pbiCls, "show", new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        Log.e(TAG, "PrimaryBouncerInteractor.show called!");
                        onBouncerShowingChanged(true);
                    }
                });
                XposedBridge.hookAllMethods(pbiCls, "hide", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        Log.e(TAG, "PrimaryBouncerInteractor.hide called!");
                        onBouncerShowingChanged(false);
                    }
                });
            } catch (Throwable ignored) {}

            try {
                Class<?> bouncerRepoCls = cl.loadClass("com.android.systemui.bouncer.data.repository.KeyguardBouncerRepositoryImpl");
                XC_MethodHook bouncerRepoShowHook = new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        Log.e(TAG, "KeyguardBouncerRepositoryImpl show: " + param.method.getName());
                        onBouncerShowingChanged(true, "KeyguardBouncerRepositoryImpl." + param.method.getName());
                    }
                };
                XC_MethodHook bouncerRepoHideHook = new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        Log.e(TAG, "KeyguardBouncerRepositoryImpl hide: " + param.method.getName());
                        onBouncerShowingChanged(false, "KeyguardBouncerRepositoryImpl." + param.method.getName());
                    }
                };
                try { XposedBridge.hookAllMethods(bouncerRepoCls, "setPrimaryShow", bouncerRepoShowHook); } catch (Throwable ignored) {}
                try { XposedBridge.hookAllMethods(bouncerRepoCls, "setPrimaryShowingSoon", bouncerRepoShowHook); } catch (Throwable ignored) {}
                try { XposedBridge.hookAllMethods(bouncerRepoCls, "setPrimaryStartingToHide", bouncerRepoHideHook); } catch (Throwable ignored) {}
                try { XposedBridge.hookAllMethods(bouncerRepoCls, "setPrimaryStartDisappearAnimation", bouncerRepoHideHook); } catch (Throwable ignored) {}
            } catch (Throwable ignored) {}

            try {
                Class<?> kscContainerCls = cl.loadClass("com.android.keyguard.KeyguardSecurityContainer");
                XposedBridge.hookAllMethods(kscContainerCls, "setVisibility", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        if (param.args.length > 0 && param.args[0] instanceof Integer) {
                            int vis = (Integer) param.args[0];
                            onBouncerShowingChanged(vis == View.VISIBLE);
                        }
                    }
                });
                XposedBridge.hookAllMethods(kscContainerCls, "onVisibilityAggregated", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        if (param.args.length > 0 && param.args[0] instanceof Boolean) {
                            onBouncerShowingChanged((Boolean) param.args[0]);
                        }
                    }
                });
                XposedBridge.hookAllMethods(kscContainerCls, "dispatchDraw", new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        if (!sBouncerShowing) {
                            onBouncerShowingChanged(true, "KeyguardSecurityContainer.dispatchDraw");
                        }
                        if (param.thisObject instanceof View) {
                            View ksc = (View) param.thisObject;
                            float kscAlpha = ksc.getAlpha();
                            float transY = Math.abs(ksc.getTranslationY());
                            if (kscAlpha >= 0.85f && transY < 50f) {
                                sBouncerFullyShown = true;
                                if (!sNotificationBlursCleared || sKeyguardAlpha > 0.01f) {
                                    sNotificationBlursCleared = true;
                                    sKeyguardAlpha = 0f;
                                    if (sNsslView != null) {
                                        sNsslView.setAlpha(0f);
                                    }
                                    clearAllNotificationBlurs();
                                }
                            }
                        }
                    }
                });
            } catch (Throwable ignored) {}

            try {
                Class<?> ksccCls = cl.loadClass("com.android.keyguard.KeyguardSecurityContainerController");
                XC_MethodHook showBouncerHook = new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        onBouncerShowingChanged(true);
                    }
                };
                try { XposedBridge.hookAllMethods(ksccCls, "show", showBouncerHook); } catch (Throwable ignored) {}
                try { XposedBridge.hookAllMethods(ksccCls, "showPrimaryBouncer", showBouncerHook); } catch (Throwable ignored) {}
                try { XposedBridge.hookAllMethods(ksccCls, "showBouncer", showBouncerHook); } catch (Throwable ignored) {}
                try {
                    XposedBridge.hookAllMethods(ksccCls, "hide", new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            onBouncerShowingChanged(false);
                        }
                    });
                } catch (Throwable ignored) {}
            } catch (Throwable ignored) {}

            // 1:1 Synchronized Fade-Out for Bouncer Transitions (matching widgets & bottom buttons)
            XC_MethodHook progressHook = new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    Object res = param.getResult();
                    if (res instanceof Float) {
                        float progress = (Float) res;
                        Object from = extractContent(param.thisObject, "fromContent");
                        Object to = extractContent(param.thisObject, "toContent");
                        if (from == null) from = extractContent(param.thisObject, "fromOrToScene");
                        if (to == null) to = extractContent(param.thisObject, "overlay");
                        handleBouncerTransitionProgress(from, to, progress, param.thisObject.getClass().getSimpleName());
                    }
                }
            };

            for (String clsName : new String[] {
                    "com.android.compose.animation.scene.SwipeAnimation",
                    "com.android.compose.animation.scene.ShowOrHideOverlaySwipeTransition",
                    "com.android.compose.animation.scene.ChangeSceneSwipeTransition",
                    "com.android.compose.animation.scene.OneOffShowOrHideOverlayTransition",
                    "com.android.compose.animation.scene.OneOffSceneTransition",
                    "com.android.compose.animation.scene.DelegatingTransition$ShowOrHideOverlay"
            }) {
                try {
                    Class<?> c = cl.loadClass(clsName);
                    if (c != null) {
                        XposedBridge.hookAllMethods(c, "getProgress", progressHook);
                        if (clsName.contains("SwipeAnimation")) {
                            XposedBridge.hookAllMethods(c, "computeProgress", progressHook);
                        }
                    }
                } catch (Throwable t) {
                    Log.e(TAG, "Failed to hook getProgress on " + clsName + ": " + t);
                }
            }

            try {
                Class<?> ktrCls = cl.loadClass("com.android.systemui.keyguard.data.repository.KeyguardTransitionRepositoryImpl");
                if (ktrCls != null) {
                    XposedBridge.hookAllMethods(ktrCls, "emitTransition", new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            if (param.args.length > 0 && param.args[0] != null) {
                                Object step = param.args[0];
                                try {
                                    Object from = XposedHelpers.callMethod(step, "getFrom");
                                    Object to = XposedHelpers.callMethod(step, "getTo");
                                    Object valObj = XposedHelpers.callMethod(step, "getValue");
                                    if (valObj instanceof Float) {
                                        handleBouncerTransitionProgress(from, to, (Float) valObj);
                                    }
                                } catch (Throwable ignored) {}
                            }
                        }
                    });
                }
            } catch (Throwable ignored) {}

            // Track Status Bar State via StatusBarStateControllerImpl
            try {
                Class<?> sscCls = cl.loadClass("com.android.systemui.statusbar.StatusBarStateControllerImpl");
                try { sStateField = sscCls.getDeclaredField("mState"); sStateField.setAccessible(true); } catch (Throwable ignored) {}
                XposedBridge.hookAllConstructors(sscCls, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        sStatusBarStateController = param.thisObject;
                        try {
                            if (sStateField != null) sStatusBarState = sStateField.getInt(param.thisObject);
                            else sStatusBarState = (Integer) XposedHelpers.getObjectField(param.thisObject, "mState");
                        } catch (Throwable ignored) {}
                    }
                });
                XposedBridge.hookAllMethods(sscCls, "setState", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        sStatusBarStateController = param.thisObject;
                        if (param.args.length > 0 && param.args[0] instanceof Integer) {
                            onStatusBarStateChanged(sStatusBarState, (Integer) param.args[0]);
                        }
                    }
                });
                XposedBridge.hookAllMethods(sscCls, "onStatusBarStateChanged", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        sStatusBarStateController = param.thisObject;
                        if (param.args.length > 0 && param.args[0] instanceof Integer) {
                            onStatusBarStateChanged(sStatusBarState, (Integer) param.args[0]);
                        }
                    }
                });
            } catch (Throwable ignored) {}

            // Track state via AmbientState
            try {
                Class<?> asCls = cl.loadClass("com.android.systemui.statusbar.notification.stack.AmbientState");
                try { sLStoShadeProgressField = asCls.getDeclaredField("mLStoShadeProgress"); sLStoShadeProgressField.setAccessible(true); } catch (Throwable ignored) {}
                try { sFractionToShadeField = asCls.getDeclaredField("mFractionToShade"); sFractionToShadeField.setAccessible(true); } catch (Throwable ignored) {}
                XposedBridge.hookAllConstructors(asCls, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        sAmbientState = param.thisObject;
                    }
                });
                XposedBridge.hookAllMethods(asCls, "setStatusBarState", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        sAmbientState = param.thisObject;
                        if (param.args.length > 0 && param.args[0] instanceof Integer) {
                            onStatusBarStateChanged(sStatusBarState, (Integer) param.args[0]);
                        }
                    }
                });
                XposedBridge.hookAllMethods(asCls, "setLStoShadeProgress", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        sAmbientState = param.thisObject;
                        if (param.args.length > 0 && param.args[0] instanceof Float) {
                            onLsToShadeProgressChanged((Float) param.args[0]);
                        }
                    }
                });
                XposedBridge.hookAllMethods(asCls, "setFractionToShade", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        sAmbientState = param.thisObject;
                        if (param.args.length > 0 && param.args[0] instanceof Float) {
                            sFractionToShade = (Float) param.args[0];
                        }
                    }
                });
            } catch (Throwable ignored) {}

            // Track state via NotificationStackScrollLayout
            try {
                Class<?> nsslCls = cl.loadClass("com.android.systemui.statusbar.notification.stack.NotificationStackScrollLayout");
                try { sNsslIsExpandedMethod = nsslCls.getMethod("isExpanded"); sNsslIsExpandedMethod.setAccessible(true); } catch (Throwable ignored) {}
                XposedBridge.hookAllConstructors(nsslCls, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        sNsslView = (View) param.thisObject;
                    }
                });
                XposedBridge.hookAllMethods(nsslCls, "setStatusBarState", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        sNsslView = (View) param.thisObject;
                        if (param.args.length > 0 && param.args[0] instanceof Integer) {
                            onStatusBarStateChanged(sStatusBarState, (Integer) param.args[0]);
                        }
                    }
                });
                XposedBridge.hookAllMethods(nsslCls, "setLStoShadeProgress", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        sNsslView = (View) param.thisObject;
                        if (param.args.length > 0 && param.args[0] instanceof Float) {
                            onLsToShadeProgressChanged((Float) param.args[0]);
                        }
                    }
                });
                XposedBridge.hookAllMethods(nsslCls, "setFractionToShade", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        sNsslView = (View) param.thisObject;
                        if (param.args.length > 0 && param.args[0] instanceof Float) {
                            sFractionToShade = (Float) param.args[0];
                        }
                    }
                });
                XposedBridge.hookAllMethods(nsslCls, "setAlpha", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        sNsslView = (View) param.thisObject;
                        if (param.args.length > 0 && param.args[0] instanceof Float) {
                            float alpha = (Float) param.args[0];
                            if (alpha < 0.05f && sScreenAwake && sStatusBarState == 1) {
                                clearAllNotificationBlurs();
                            } else if (alpha > 0.05f && sStatusBarState == 1 && sScreenAwake) {
                                invalidateNotificationStack();
                            }
                        }
                    }
                });
                XposedBridge.hookAllMethods(nsslCls, "setAlphaForLockscreenFadeIn", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        sNsslView = (View) param.thisObject;
                        if (param.args.length > 0 && param.args[0] instanceof Float) {
                            float alpha = (Float) param.args[0];
                            if (alpha < 0.05f && sScreenAwake && sStatusBarState == 1) {
                                clearAllNotificationBlurs();
                            } else if (alpha > 0.05f && sStatusBarState == 1 && sScreenAwake) {
                                invalidateNotificationStack();
                            }
                        }
                    }
                });
                XposedBridge.hookAllMethods(nsslCls, "setVisibility", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        sNsslView = (View) param.thisObject;
                        if (param.args.length > 0 && param.args[0] instanceof Integer) {
                            int vis = (Integer) param.args[0];
                            if (vis != View.VISIBLE && sScreenAwake && sStatusBarState == 1) {
                                clearAllNotificationBlurs();
                            } else if (vis == View.VISIBLE && sStatusBarState == 1 && sScreenAwake) {
                                invalidateNotificationStack();
                            }
                        }
                    }
                });
                XposedBridge.hookAllMethods(nsslCls, "onConfigurationChanged", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        sCachedFrostedColorDark = 0;
                        sCachedFrostedColorLight = 0;
                        sLastColorCacheUiMode = -1;
                        invalidateNotificationStack();
                    }
                });
            } catch (Throwable ignored) {}

            try {
                Class<?> sncCls = cl.loadClass("com.android.systemui.statusbar.notification.stack.ui.view.SharedNotificationContainer");
                XposedBridge.hookAllMethods(sncCls, "setAlpha", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        if (param.args.length > 0 && param.args[0] instanceof Float) {
                            float alpha = (Float) param.args[0];
                            if (alpha < 0.05f && sScreenAwake && sStatusBarState == 1) {
                                clearAllNotificationBlurs();
                            } else if (alpha > 0.05f && sStatusBarState == 1 && sScreenAwake) {
                                invalidateNotificationStack();
                            }
                        }
                    }
                });
                XposedBridge.hookAllMethods(sncCls, "setVisibility", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        if (param.args.length > 0 && param.args[0] instanceof Integer) {
                            int vis = (Integer) param.args[0];
                            if (vis != View.VISIBLE && sScreenAwake && sStatusBarState == 1) {
                                clearAllNotificationBlurs();
                            } else if (vis == View.VISIBLE && sStatusBarState == 1 && sScreenAwake) {
                                invalidateNotificationStack();
                            }
                        }
                    }
                });
            } catch (Throwable ignored) {}

            try {
                Class<?> krvCls = cl.loadClass("com.android.systemui.keyguard.ui.view.KeyguardRootView");
                XposedBridge.hookAllConstructors(krvCls, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        sKeyguardRootView = (View) param.thisObject;
                    }
                });
                XposedBridge.hookAllMethods(krvCls, "onAttachedToWindow", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        sKeyguardRootView = (View) param.thisObject;
                    }
                });
                XposedBridge.hookAllMethods(krvCls, "setAlpha", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        sKeyguardRootView = (View) param.thisObject;
                        if (param.args.length > 0 && param.args[0] instanceof Float) {
                            float alpha = (Float) param.args[0];
                            sKeyguardAlpha = alpha;
                            syncNotificationAlpha(alpha);
                        }
                    }
                });
                XposedBridge.hookAllMethods(krvCls, "dispatchDraw", new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        sKeyguardRootView = (View) param.thisObject;
                        float alpha = sKeyguardRootView.getAlpha();
                        if (Math.abs(sKeyguardAlpha - alpha) > 0.001f) {
                            sKeyguardAlpha = alpha;
                            syncNotificationAlpha(alpha);
                        }
                    }
                });
                XposedBridge.hookAllMethods(krvCls, "setVisibility", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        sKeyguardRootView = (View) param.thisObject;
                        if (param.args.length > 0 && param.args[0] instanceof Integer) {
                            int vis = (Integer) param.args[0];
                            if (vis != View.VISIBLE) {
                                sKeyguardAlpha = 0f;
                                syncNotificationAlpha(0f);
                            }
                        }
                    }
                });
            } catch (Throwable t) {
                Log.e(TAG, "KeyguardRootView hook error: " + t);
            }

            Class<?> notifBgClass = cl.loadClass("com.android.systemui.statusbar.notification.row.NotificationBackgroundView");
            Class<?> notifRowClass = cl.loadClass("com.android.systemui.statusbar.notification.row.ExpandableNotificationRow");
            Class<?> notifShelfClass = null;
            try {
                notifShelfClass = cl.loadClass("com.android.systemui.statusbar.NotificationShelf");
            } catch (Throwable ignored) {}

            final Class<?> shelfCls = notifShelfClass;

            if (shelfCls != null) {
                try {
                    XposedBridge.hookAllMethods(shelfCls, "setStatusBarState", new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            if (param.args.length > 0 && param.args[0] instanceof Integer) {
                                onStatusBarStateChanged(sStatusBarState, (Integer) param.args[0]);
                            }
                        }
                    });
                } catch (Throwable ignored) {}
            }

            try {
                Class<?> csCls = cl.loadClass("com.android.systemui.statusbar.phone.CentralSurfacesImpl");
                XposedBridge.hookAllMethods(csCls, "setBarState", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        if (sStatusBarStateController == null) {
                            try { sStatusBarStateController = XposedHelpers.getObjectField(param.thisObject, "mStatusBarStateController"); } catch (Throwable ignored) {}
                        }
                        if (param.args.length > 0 && param.args[0] instanceof Integer) {
                            onStatusBarStateChanged(sStatusBarState, (Integer) param.args[0]);
                        }
                    }
                });
                try {
                    XposedBridge.hookAllMethods(csCls, "showBouncerOrUnlock", new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            onBouncerShowingChanged(true);
                        }
                    });
                } catch (Throwable ignored) {}
            } catch (Throwable ignored) {}

            try {
                Class<?> nsslControllerCls = cl.loadClass("com.android.systemui.statusbar.notification.stack.NotificationStackScrollLayoutController");
                XposedBridge.hookAllMethods(nsslControllerCls, "setStatusBarState", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        if (sStatusBarStateController == null) {
                            try { sStatusBarStateController = XposedHelpers.getObjectField(param.thisObject, "mStatusBarStateController"); } catch (Throwable ignored) {}
                        }
                        if (param.args.length > 0 && param.args[0] instanceof Integer) {
                            onStatusBarStateChanged(sStatusBarState, (Integer) param.args[0]);
                        }
                    }
                });
            } catch (Throwable ignored) {}

            // Pre-reflect methods & fields on ExpandableNotificationRow and NotificationBackgroundView
            try { sIsPinnedMethod = notifRowClass.getMethod("isPinned"); sIsPinnedMethod.setAccessible(true); } catch (Throwable ignored) {}
            try { sIsPinnedAndExpandedMethod = notifRowClass.getMethod("isPinnedAndExpanded"); sIsPinnedAndExpandedMethod.setAccessible(true); } catch (Throwable ignored) {}
            try { sIsHeadsUpMethod = notifRowClass.getMethod("isHeadsUp"); sIsHeadsUpMethod.setAccessible(true); } catch (Throwable ignored) {}
            try { sIsHeadsUpStateMethod = notifRowClass.getMethod("isHeadsUpState"); sIsHeadsUpStateMethod.setAccessible(true); } catch (Throwable ignored) {}
            try { sIsHeadsUpAnimatingAwayMethod = notifRowClass.getMethod("isHeadsUpAnimatingAway"); sIsHeadsUpAnimatingAwayMethod.setAccessible(true); } catch (Throwable ignored) {}
            try {
                Class<?> anvCls = cl.loadClass("com.android.systemui.statusbar.notification.row.ActivatableNotificationView");
                sIsOnKeyguardMethod = anvCls.getMethod("isOnKeyguard");
                sIsOnKeyguardMethod.setAccessible(true);
                try { sGetAppearAnimationFractionMethod = anvCls.getMethod("getAppearAnimationFraction"); sGetAppearAnimationFractionMethod.setAccessible(true); } catch (Throwable ignored) {}
                try { sIsDrawingAppearAnimationMethod = anvCls.getMethod("isDrawingAppearAnimation"); sIsDrawingAppearAnimationMethod.setAccessible(true); } catch (Throwable ignored) {}
            } catch (Throwable ignored) {}
            try {
                Class<?> eovCls = cl.loadClass("com.android.systemui.statusbar.notification.row.ExpandableOutlineView");
                try { sGetOutlineRectMethod = eovCls.getMethod("getOutlineRect"); sGetOutlineRectMethod.setAccessible(true); } catch (Throwable ignored) {}
                try { sGetOutlineAlphaMethod = eovCls.getMethod("getOutlineAlpha"); sGetOutlineAlphaMethod.setAccessible(true); } catch (Throwable ignored) {}
                try { sHasCustomOutlineMethod = eovCls.getMethod("hasCustomOutline"); sHasCustomOutlineMethod.setAccessible(true); } catch (Throwable ignored) {}
            } catch (Throwable ignored) {}
            try { sIsColorizedMethod = notifRowClass.getMethod("isColorized"); sIsColorizedMethod.setAccessible(true); } catch (Throwable ignored) {}
            try { sGetTopCornerRadiusMethod = notifRowClass.getMethod("getTopCornerRadius"); sGetTopCornerRadiusMethod.setAccessible(true); } catch (Throwable ignored) {}
            try { sGetBottomCornerRadiusMethod = notifRowClass.getMethod("getBottomCornerRadius"); sGetBottomCornerRadiusMethod.setAccessible(true); } catch (Throwable ignored) {}
            try { sGetActualHeightMethod = notifRowClass.getMethod("getActualHeight"); sGetActualHeightMethod.setAccessible(true); } catch (Throwable ignored) {}
            try { sIsShowNoBackgroundMethod = notifRowClass.getMethod("isShowNoBackground"); sIsShowNoBackgroundMethod.setAccessible(true); } catch (Throwable ignored) {}
            try { sIsChildInGroupMethod = notifRowClass.getMethod("isChildInGroup"); sIsChildInGroupMethod.setAccessible(true); } catch (Throwable ignored) {}
            try { sIsGroupExpandedMethod = notifRowClass.getMethod("isGroupExpanded"); sIsGroupExpandedMethod.setAccessible(true); } catch (Throwable ignored) {}
            try { sGetNormalColorMethod = notifRowClass.getMethod("getNormalColor"); sGetNormalColorMethod.setAccessible(true); } catch (Throwable ignored) {}
            try { sShowNoBackgroundField = notifRowClass.getDeclaredField("mShowNoBackground"); sShowNoBackgroundField.setAccessible(true); } catch (Throwable ignored) {}
            try { sBackgroundNormalField = notifRowClass.getDeclaredField("mBackgroundNormal"); sBackgroundNormalField.setAccessible(true); } catch (Throwable ignored) {}
            try { sBackgroundDimmedField = notifRowClass.getDeclaredField("mBackgroundDimmed"); sBackgroundDimmedField.setAccessible(true); } catch (Throwable ignored) {}

            if (shelfCls != null) {
                try { sShelfActualWidthField = shelfCls.getDeclaredField("mActualWidth"); sShelfActualWidthField.setAccessible(true); } catch (Throwable ignored) {}
            }

            try { sBackgroundTopField = notifBgClass.getDeclaredField("mBackgroundTop"); sBackgroundTopField.setAccessible(true); } catch (Throwable ignored) {}
            try { sActualWidthField = notifBgClass.getDeclaredField("mActualWidth"); sActualWidthField.setAccessible(true); } catch (Throwable ignored) {}
            try { sActualHeightField = notifBgClass.getDeclaredField("mActualHeight"); sActualHeightField.setAccessible(true); } catch (Throwable ignored) {}
            try { sClipTopAmountField = notifBgClass.getDeclaredField("mClipTopAmount"); sClipTopAmountField.setAccessible(true); } catch (Throwable ignored) {}
            try { sClipBottomAmountField = notifBgClass.getDeclaredField("mClipBottomAmount"); sClipBottomAmountField.setAccessible(true); } catch (Throwable ignored) {}
            try { sTopOverlapField = notifBgClass.getDeclaredField("mTopOverlap"); sTopOverlapField.setAccessible(true); } catch (Throwable ignored) {}
            try { sBottomOverlapField = notifBgClass.getDeclaredField("mBottomOverlap"); sBottomOverlapField.setAccessible(true); } catch (Throwable ignored) {}
            try { sExpandAnimationRunningField = notifBgClass.getDeclaredField("mExpandAnimationRunning"); sExpandAnimationRunningField.setAccessible(true); } catch (Throwable ignored) {}
            try { sDrawDismissButtonCutoutField = notifBgClass.getDeclaredField("mDrawDismissButtonCutout"); sDrawDismissButtonCutoutField.setAccessible(true); } catch (Throwable ignored) {}
            try { sBackgroundBlurDrawableField = notifBgClass.getDeclaredField("mBackgroundBlurDrawable"); sBackgroundBlurDrawableField.setAccessible(true); } catch (Throwable ignored) {}
            try { sTintColorField = notifBgClass.getDeclaredField("mTintColor"); sTintColorField.setAccessible(true); } catch (Throwable ignored) {}
            try { sNormalColorField = notifBgClass.getDeclaredField("mNormalColor"); sNormalColorField.setAccessible(true); } catch (Throwable ignored) {}
            try { sOnKeyguardField = notifBgClass.getDeclaredField("mOnKeyguard"); sOnKeyguardField.setAccessible(true); } catch (Throwable ignored) {}
            try { sBackgroundField = notifBgClass.getDeclaredField("mBackground"); sBackgroundField.setAccessible(true); } catch (Throwable ignored) {}
            try { sCornerRadiiField = notifBgClass.getDeclaredField("mCornerRadii"); sCornerRadiiField.setAccessible(true); } catch (Throwable ignored) {}
            try { sGetViewRootImplMethod = View.class.getMethod("getViewRootImpl"); sGetViewRootImplMethod.setAccessible(true); } catch (Throwable ignored) {}

            try {
                Class<?> bbdCls = cl.loadClass("com.android.internal.graphics.drawable.BackgroundBlurDrawable");
                try { sSetBlurRadiusMethod = bbdCls.getMethod("setBlurRadius", int.class); sSetBlurRadiusMethod.setAccessible(true); } catch (Throwable ignored) {}
                try { sSetCornerRadiusMethod = bbdCls.getMethod("setCornerRadius", float.class, float.class, float.class, float.class); sSetCornerRadiusMethod.setAccessible(true); } catch (Throwable ignored) {}
                try { sSetCornerRadiusSingleMethod = bbdCls.getMethod("setCornerRadius", float.class); sSetCornerRadiusSingleMethod.setAccessible(true); } catch (Throwable ignored) {}
                try { sSetColorMethod = bbdCls.getMethod("setColor", int.class); sSetColorMethod.setAccessible(true); } catch (Throwable ignored) {}
            } catch (Throwable ignored) {}
            try {
                Class<?> vriCls = cl.loadClass("android.view.ViewRootImpl");
                sCreateBackgroundBlurDrawableMethod = vriCls.getMethod("createBackgroundBlurDrawable");
                sCreateBackgroundBlurDrawableMethod.setAccessible(true);
            } catch (Throwable ignored) {}

            XC_MethodHook rowExpansionHook = new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    View row = (View) param.thisObject;
                    if (row != null) {
                        row.invalidate();
                        if (sBackgroundNormalField != null) {
                            try {
                                View bgNormal = (View) sBackgroundNormalField.get(row);
                                if (bgNormal != null) bgNormal.invalidate();
                            } catch (Throwable ignored) {}
                        }
                        if (sBackgroundDimmedField != null) {
                            try {
                                View bgDimmed = (View) sBackgroundDimmedField.get(row);
                                if (bgDimmed != null) bgDimmed.invalidate();
                            } catch (Throwable ignored) {}
                        }
                    }
                }
            };
            try { XposedBridge.hookAllMethods(notifRowClass, "setUserExpanded", rowExpansionHook); } catch (Throwable ignored) {}
            try { XposedBridge.hookAllMethods(notifRowClass, "setShowingExpanded", rowExpansionHook); } catch (Throwable ignored) {}
            try { XposedBridge.hookAllMethods(notifRowClass, "setGroupExpanded", rowExpansionHook); } catch (Throwable ignored) {}

            XC_MethodHook hunStateHook = new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    if (param.thisObject instanceof View) {
                        View row = (View) param.thisObject;
                        if (sBackgroundNormalField != null) {
                            try {
                                View bgNormal = (View) sBackgroundNormalField.get(row);
                                if (bgNormal != null) {
                                    bgNormal.invalidate();
                                }
                            } catch (Throwable ignored) {}
                        }
                    }
                }
            };
            try { XposedBridge.hookAllMethods(notifRowClass, "setPinned", hunStateHook); } catch (Throwable ignored) {}
            try { XposedBridge.hookAllMethods(notifRowClass, "setHeadsUp", hunStateHook); } catch (Throwable ignored) {}
            try { XposedBridge.hookAllMethods(notifRowClass, "setHeadsUpAnimatingAway", hunStateHook); } catch (Throwable ignored) {}
            try {
                XposedBridge.hookAllMethods(notifRowClass, "setColorized", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        if (param.thisObject instanceof View && param.args.length > 0 && param.args[0] instanceof Boolean) {
                            ((View) param.thisObject).setTag(TAG_KEY_IS_COLORIZED, param.args[0]);
                        }
                    }
                });
            } catch (Throwable ignored) {}

            // PropertyAnimator, touch helper and detach tracking for Heads-Up notification rows

            // Hook PropertyAnimator only for Heads-Up notification rows to track animated motion
            try {
                Class<?> paCls = cl.loadClass("com.android.systemui.statusbar.notification.PropertyAnimator");
                Class<?> apCls = cl.loadClass("com.android.systemui.statusbar.notification.AnimatableProperty");
                final Method getAnimTagMethod = apCls.getMethod("getAnimatorTag");
                getAnimTagMethod.setAccessible(true);
                XposedBridge.hookAllMethods(paCls, "startAnimation", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        if (param.args.length > 1 && param.args[0] instanceof View && param.args[1] != null) {
                            final View targetView = (View) param.args[0];
                            if (isRowHun(targetView)) {
                                int tag = (Integer) getAnimTagMethod.invoke(param.args[1]);
                                Object anim = targetView.getTag(tag);
                                if (anim instanceof ValueAnimator) {
                                    ValueAnimator va = (ValueAnimator) anim;
                                    va.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                                        @Override
                                        public void onAnimationUpdate(ValueAnimator animation) {
                                            if (sBackgroundNormalField != null) {
                                                try {
                                                    View bgNormal = (View) sBackgroundNormalField.get(targetView);
                                                    if (bgNormal != null) bgNormal.invalidate();
                                                } catch (Throwable ignored) {}
                                            }
                                        }
                                    });
                                }
                            }
                        }
                    }
                });
            } catch (Throwable ignored) {}

            // Hook HeadsUpTouchHelper fly away and touch tracking
            try {
                Class<?> hthCls = cl.loadClass("com.android.systemui.statusbar.notification.headsup.HeadsUpTouchHelper");
                final Field snoozeFlyAnimField = hthCls.getDeclaredField("mSnoozeFlyAnimator");
                snoozeFlyAnimField.setAccessible(true);
                final Field pickedChildField = hthCls.getDeclaredField("mPickedChild");
                pickedChildField.setAccessible(true);
                XposedBridge.hookAllMethods(hthCls, "startSnoozeFly", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        ValueAnimator anim = (ValueAnimator) snoozeFlyAnimField.get(param.thisObject);
                        if (anim != null) {
                            final View row = (View) pickedChildField.get(param.thisObject);
                            anim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                                @Override
                                public void onAnimationUpdate(ValueAnimator animation) {
                                    if (row != null && sBackgroundNormalField != null) {
                                        try {
                                            View bgNormal = (View) sBackgroundNormalField.get(row);
                                            if (bgNormal != null) bgNormal.invalidate();
                                        } catch (Throwable ignored) {}
                                    }
                                }
                            });
                        }
                    }
                });
            } catch (Throwable ignored) {}

            // Hook HeadsUpTouchHelperEx for Nothing OS pop window and gesture animations
            try {
                Class<?> hthExCls = cl.loadClass("com.nothing.systemui.statusbar.phone.HeadsUpTouchHelperEx");
                XC_MethodHook exUpdateHook = new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        if (param.args.length > 0 && param.args[param.args.length - 1] instanceof View) {
                            View row = (View) param.args[param.args.length - 1];
                            if (sBackgroundNormalField != null) {
                                try {
                                    View bgNormal = (View) sBackgroundNormalField.get(row);
                                    if (bgNormal != null) bgNormal.invalidate();
                                } catch (Throwable ignored) {}
                            }
                        }
                    }
                };
                try { XposedBridge.hookAllMethods(hthExCls, "updateSourceView", exUpdateHook); } catch (Throwable ignored) {}
                try { XposedBridge.hookAllMethods(hthExCls, "updateChildView", exUpdateHook); } catch (Throwable ignored) {}
            } catch (Throwable ignored) {}

            // Cleanup blur immediately when row is detached from window
            try {
                XposedBridge.hookAllMethods(notifRowClass, "onDetachedFromWindow", new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        View row = (View) param.thisObject;
                        if (row != null) {
                            row.setTag(TAG_KEY_WAS_HUN, null);
                            if (sBackgroundNormalField != null) {
                                try {
                                    View bgNormal = (View) sBackgroundNormalField.get(row);
                                    if (bgNormal != null) cleanupBlur(bgNormal);
                                } catch (Throwable ignored) {}
                            }
                        }
                    }
                });
            } catch (Throwable ignored) {}

            if (notifShelfClass != null) {
                try {
                    XC_MethodHook shelfInvalidateHook = new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            if (sStatusBarState == 1 && sScreenAwake && !sBouncerFullyShown) {
                                View shelf = (View) param.thisObject;
                                if (shelf != null) {
                                    shelf.invalidate();
                                    if (sBackgroundNormalField != null) {
                                        try {
                                            View bg = (View) sBackgroundNormalField.get(shelf);
                                            if (bg != null) bg.invalidate();
                                        } catch (Throwable ignored) {}
                                    }
                                }
                            }
                        }
                    };
                    try { XposedBridge.hookAllMethods(notifShelfClass, "updateAppearance", shelfInvalidateHook); } catch (Throwable ignored) {}
                    try { XposedBridge.hookAllMethods(notifShelfClass, "setBackgroundWidth", shelfInvalidateHook); } catch (Throwable ignored) {}
                    try { XposedBridge.hookAllMethods(notifShelfClass, "updateState", shelfInvalidateHook); } catch (Throwable ignored) {}
                    try {
                        XposedBridge.hookAllMethods(notifShelfClass, "setHideBackground", new XC_MethodHook() {
                            @Override
                            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                                if (sStatusBarState == 1 && sScreenAwake && !sBouncerFullyShown) {
                                    param.args[0] = false;
                                }
                            }
                        });
                    } catch (Throwable ignored) {}
                } catch (Throwable ignored) {}
            }


            XC_MethodHook notifDrawHook = new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    View bgView = (View) param.thisObject;
                    View parent = (View) bgView.getParent();

                    // 1. One-time caching of sStatusBarStateController & sAmbientState if not yet set
                    if (sStatusBarStateController == null) {
                        try {
                            ClassLoader bcl = bgView.getContext().getClassLoader();
                            Class<?> depCls = bcl.loadClass("com.android.systemui.Dependency");
                            Class<?> sscInterface = bcl.loadClass("com.android.systemui.plugins.statusbar.StatusBarStateController");
                            sStatusBarStateController = XposedHelpers.callStaticMethod(depCls, "get", sscInterface);
                        } catch (Throwable ignored) {}
                        if (sStatusBarStateController == null && parent != null) {
                            try {
                                Object ssc = XposedHelpers.getObjectField(parent, "mStatusBarStateController");
                                if (ssc != null) sStatusBarStateController = ssc;
                            } catch (Throwable ignored) {}
                        }
                    }
                    if (sAmbientState == null) {
                        try {
                            ClassLoader bcl = bgView.getContext().getClassLoader();
                            Class<?> depCls = bcl.loadClass("com.android.systemui.Dependency");
                            Class<?> asClass = bcl.loadClass("com.android.systemui.statusbar.notification.stack.AmbientState");
                            sAmbientState = XposedHelpers.callStaticMethod(depCls, "get", asClass);
                        } catch (Throwable ignored) {}
                    }
                    if (sNsslView == null && parent != null) {
                        ViewParent vp = parent.getParent();
                        if (vp instanceof View) {
                            sNsslView = (View) vp;
                        }
                    }
                    if (sRootWindowView == null) {
                        try {
                            View root = bgView.getRootView();
                            if (root != null) sRootWindowView = root;
                        } catch (Throwable ignored) {}
                    }

                    // 4. Fast direct method invocation on parent row / shelf
                    View row = (View) bgView.getTag(TAG_KEY_ROW);
                    if (row == null && parent != null) {
                        if (notifRowClass != null && notifRowClass.isAssignableFrom(parent.getClass())) {
                            row = parent;
                        } else {
                            View p = parent;
                            while (p != null && notifRowClass != null && !notifRowClass.isAssignableFrom(p.getClass())
                                    && !(shelfCls != null && shelfCls.isAssignableFrom(p.getClass()))) {
                                ViewParent vp = p.getParent();
                                p = (vp instanceof View) ? (View) vp : null;
                            }
                            row = p;
                        }
                        if (row != null) {
                            bgView.setTag(TAG_KEY_ROW, row);
                        }
                    }


                    Context ctx = bgView.getContext();
                    if (ctx == null) return;

                    String bgCls = bgView.getClass().getName();
                    boolean isShelf = bgCls.contains("Shelf")
                            || (row != null && row.getClass().getName().contains("Shelf"))
                            || (shelfCls != null && row != null && shelfCls.isAssignableFrom(row.getClass()));

                    int curState = getCurrentStatusBarState();
                    boolean isHun = !isShelf && isRowHun(row);
                    boolean pureShade = curState == 0 && sStatusBarState == 0;
                    boolean canDrawShadeGlass = !isHun && isShadeExpanded()
                            && PanelGlassHooks.isNotificationBackdropEnabled(ctx);
                    if (canDrawShadeGlass) {
                        if ((sExpandAnimationRunningField != null && sExpandAnimationRunningField.getBoolean(bgView))
                                || (sDrawDismissButtonCutoutField != null && sDrawDismissButtonCutoutField.getBoolean(bgView))) {
                            canDrawShadeGlass = false;
                        }
                    }
                    if (pureShade && !isHun && !canDrawShadeGlass) {
                        if (Boolean.TRUE.equals(bgView.getTag(TAG_KEY_BLUR_ACTIVE))) cleanupBlur(bgView);
                        return;
                    }

                    boolean isChildOfGroup = false;
                    // Only mBackgroundNormal should ever draw notification card backgrounds.
                    // mBackgroundDimmed must NEVER draw on parent/single rows because it causes stacked darker layers.
                    // When a group is collapsed, child rows inside NotificationChildrenContainer must NEVER draw.
                    // When a group is expanded, each child row MUST draw its own background card!
                    if (!isShelf) {
                        int bgId = bgView.getId();
                        if (sIdBackgroundDimmed <= 0 && ctx != null && ctx.getResources() != null) {
                            sIdBackgroundDimmed = ctx.getResources().getIdentifier("backgroundDimmed", "id", ctx.getPackageName());
                            if (sIdBackgroundDimmed <= 0) sIdBackgroundDimmed = ctx.getResources().getIdentifier("backgroundDimmed", "id", "com.android.systemui");
                        }
                        if (sIdChildrenContainer <= 0 && ctx != null && ctx.getResources() != null) {
                            sIdChildrenContainer = ctx.getResources().getIdentifier("notification_children_container", "id", ctx.getPackageName());
                            if (sIdChildrenContainer <= 0) sIdChildrenContainer = ctx.getResources().getIdentifier("notification_children_container", "id", "com.android.systemui");
                        }

                        // Fast check via row.isChildInGroup() method first
                        if (row != null && sIsChildInGroupMethod != null) {
                            try { isChildOfGroup = (Boolean) sIsChildInGroupMethod.invoke(row); } catch (Throwable ignored) {}
                        }
                        if (!isChildOfGroup) {
                            ViewParent pWalker = (row != null) ? row.getParent() : bgView.getParent();
                            while (pWalker != null) {
                                if (pWalker instanceof View) {
                                    int pid = ((View) pWalker).getId();
                                    if (pid > 0 && pid == sIdChildrenContainer) {
                                        isChildOfGroup = true;
                                        break;
                                    }
                                }
                                String pName = pWalker.getClass().getName();
                                if (pName.contains("ChildrenContainer")) {
                                    isChildOfGroup = true;
                                    break;
                                }
                                pWalker = pWalker.getParent();
                            }
                        }

                        if (isChildOfGroup) {
                            boolean groupExpanded = false;
                            if (row != null) {
                                try {
                                    if (sIsGroupExpandedMethod != null) {
                                        groupExpanded = (Boolean) sIsGroupExpandedMethod.invoke(row);
                                    } else {
                                        groupExpanded = (Boolean) XposedHelpers.callMethod(row, "isGroupExpanded");
                                    }
                                } catch (Throwable ignored) {}
                                if (!groupExpanded) {
                                    try {
                                        Object parentRow = XposedHelpers.getObjectField(row, "mNotificationParent");
                                        if (parentRow != null) {
                                            if (sIsGroupExpandedMethod != null) {
                                                groupExpanded = (Boolean) sIsGroupExpandedMethod.invoke(parentRow);
                                            } else {
                                                groupExpanded = (Boolean) XposedHelpers.callMethod(parentRow, "isGroupExpanded");
                                            }
                                        }
                                    } catch (Throwable ignored) {}
                                }
                            }
                            if (!groupExpanded) {
                                ViewParent vp = (row != null) ? row.getParent() : bgView.getParent();
                                while (vp != null) {
                                    if (vp.getClass().getName().contains("ChildrenContainer")) {
                                        try {
                                            Object contNotif = XposedHelpers.getObjectField(vp, "mContainingNotification");
                                            if (contNotif != null) {
                                                if (sIsGroupExpandedMethod != null) {
                                                    groupExpanded = (Boolean) sIsGroupExpandedMethod.invoke(contNotif);
                                                } else {
                                                    groupExpanded = (Boolean) XposedHelpers.callMethod(contNotif, "isGroupExpanded");
                                                }
                                            }
                                        } catch (Throwable ignored) {}
                                        break;
                                    }
                                    vp = vp.getParent();
                                }
                            }

                            // When group is collapsed, child rows MUST NOT draw (prevents stacked darker layers).
                            // When group is expanded, child rows MUST draw their individual card backgrounds!
                            if (!groupExpanded) {
                                cleanupBlur(bgView);
                                param.setResult(null);
                                return;
                            }
                        } else {
                            // On parent summary rows and single rows, backgroundDimmed must NEVER draw
                            if (bgId > 0 && bgId == sIdBackgroundDimmed) {
                                cleanupBlur(bgView);
                                param.setResult(null);
                                return;
                            }
                        }

                        if (row != null) {
                            try {
                                Object normalBg = (sBackgroundNormalField != null) ? sBackgroundNormalField.get(row)
                                        : XposedHelpers.getObjectField(row, "mBackgroundNormal");
                                if (normalBg != null && bgView != normalBg) {
                                    cleanupBlur(bgView);
                                    param.setResult(null);
                                    return;
                                }
                            } catch (Throwable ignored) {}
                        }
                    }

                    if (row != null) {
                        row.setTag(TAG_KEY_ROW_BG, bgView);
                    }

                    // 2. Version 1 vs Version 2 Detection:
                    // Determine if row is on Lockscreen / Keyguard
                    // sStatusBarState: 1 = KEYGUARD (pure lockscreen), 2 = SHADE_LOCKED (notification shade), 0 = SHADE (notification shade)
                    boolean onKeyguard = (curState == 1 && sStatusBarState == 1);
                    if (!onKeyguard && row != null && sIsOnKeyguardMethod != null) {
                        try {
                            Boolean ok = (Boolean) sIsOnKeyguardMethod.invoke(row);
                            if (Boolean.TRUE.equals(ok) && curState == 1 && sStatusBarState == 1) {
                                onKeyguard = true;
                            }
                        } catch (Throwable ignored) {}
                    }

                    // Dynamic 120Hz Transition Architecture:
                    // On Lockscreen, dragging down opens the shade (sLsToShadeProgress: 0.0=Lockscreen -> 1.0=Shade)
                    float lsToShade = onKeyguard ? Math.max(0f, Math.min(1f, sLsToShadeProgress)) : 1.0f;
                    float blurWeight = isHun ? 1.0f : (onKeyguard ? (1.0f - lsToShade) : 0.0f);
                    float solidWeight = isHun ? 0.0f : (onKeyguard ? lsToShade : 1.0f);
                    if (!sScreenAwake || sBouncerFullyShown) {
                        blurWeight = 0f;
                        solidWeight = 0f;
                    }

                    boolean isColorized = false;
                    if (!isShelf && row != null) {
                        Object colTag = row.getTag(TAG_KEY_IS_COLORIZED);
                        if (colTag instanceof Boolean) {
                            isColorized = (Boolean) colTag;
                        } else {
                            if (sIsColorizedMethod != null) {
                                try { isColorized = (Boolean) sIsColorizedMethod.invoke(row); } catch (Throwable ignored) {}
                            }
                            row.setTag(TAG_KEY_IS_COLORIZED, isColorized ? Boolean.TRUE : Boolean.FALSE);
                        }
                    }
                    if (pureShade && !isHun && isColorized) return;
                    boolean shadeGlass = canDrawShadeGlass && !isColorized && solidWeight > 0.01f;

                    // 3. Screen awake / Sleep / AOD / Bouncer check & dynamic alpha / dismissal tracking:
                    float stackAlpha = 1.0f;
                    if (sNsslView != null) {
                        try { stackAlpha = sNsslView.getAlpha(); } catch (Throwable ignored) {}
                    }
                    float rowAlpha = 1.0f;
                    if (row != null) {
                        try { rowAlpha = row.getAlpha(); } catch (Throwable ignored) {}
                    } else if (parent != null) {
                        try { rowAlpha = parent.getAlpha(); } catch (Throwable ignored) {}
                    }
                    float bgAlpha = bgView.getAlpha();

                    float effectiveAlpha;
                    if (isHun) {
                        effectiveAlpha = Math.max(0f, Math.min(1f, rowAlpha * bgAlpha));
                    } else {
                        float keyguardFade = onKeyguard ? Math.min(stackAlpha, sKeyguardAlpha) : stackAlpha;
                        effectiveAlpha = Math.max(0f, Math.min(1f, rowAlpha * bgAlpha * keyguardFade));
                    }

                    float combinedAlpha = effectiveAlpha;

                    if (!sScreenAwake || (sBouncerFullyShown && !isHun)) {
                        if (Boolean.TRUE.equals(bgView.getTag(TAG_KEY_BLUR_ACTIVE))) {
                            cleanupBlur(bgView);
                        }
                        param.setResult(null);
                        return;
                    }

                    if (combinedAlpha < 0.01f) {
                        if (isHun) {
                            Object bdObj = bgView.getTag(TAG_KEY_BLUR_DRAWABLE);
                            if (bdObj instanceof Drawable) {
                                ((Drawable) bdObj).setAlpha(0);
                                ((Drawable) bdObj).setVisible(false, false);
                            }
                        } else if (Boolean.TRUE.equals(bgView.getTag(TAG_KEY_BLUR_ACTIVE))) {
                            cleanupBlur(bgView);
                        }
                        param.setResult(null);
                        return;
                    }

                    Canvas canvas = (Canvas) param.args[0];
                    if (canvas == null) return;


                    int uiMode = ctx.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
                    boolean isDark = (uiMode == Configuration.UI_MODE_NIGHT_YES);

                    boolean showNoBg = false;
                    if (row != null && !isShelf) {
                        if (sIsShowNoBackgroundMethod != null) {
                            try { showNoBg = (Boolean) sIsShowNoBackgroundMethod.invoke(row); } catch (Throwable ignored) {}
                        } else if (sShowNoBackgroundField != null) {
                            try { showNoBg = sShowNoBackgroundField.getBoolean(row); } catch (Throwable ignored) {}
                        }
                        if (!showNoBg) {
                            try {
                                boolean isChild = (sIsChildInGroupMethod != null) ? (Boolean) sIsChildInGroupMethod.invoke(row)
                                        : (Boolean) XposedHelpers.callMethod(row, "isChildInGroup");
                                if (isChild) {
                                    boolean groupExpanded = (sIsGroupExpandedMethod != null) ? (Boolean) sIsGroupExpandedMethod.invoke(row)
                                            : (Boolean) XposedHelpers.callMethod(row, "isGroupExpanded");
                                    if (!groupExpanded) {
                                        showNoBg = true;
                                    }
                                }
                            } catch (Throwable ignored) {}
                        }
                    }
                    if (showNoBg && !isShelf) {
                        if (Boolean.TRUE.equals(bgView.getTag(TAG_KEY_BLUR_ACTIVE))) {
                            cleanupBlur(bgView);
                        }
                        param.setResult(null);
                        return;
                    }

                    int bgTop = 0;
                    if (sBackgroundTopField != null) {
                        try { bgTop = sBackgroundTopField.getInt(bgView); } catch (Throwable ignored) {}
                    }
                    int actualWidth = 0;
                    if (sActualWidthField != null) {
                        try { actualWidth = sActualWidthField.getInt(bgView); } catch (Throwable ignored) {}
                    }
                    if (isShelf && (actualWidth <= 0 || actualWidth >= bgView.getWidth()) && row != null) {
                        if (sShelfActualWidthField != null) {
                            try {
                                float sw = sShelfActualWidthField.getFloat(row);
                                if (sw > 0f && sw < bgView.getWidth()) {
                                    actualWidth = (int) sw;
                                }
                            } catch (Throwable ignored) {}
                        }
                        if (actualWidth <= 0 || actualWidth >= bgView.getWidth()) {
                            try {
                                Object shelfIcons = XposedHelpers.getObjectField(row, "mShelfIcons");
                                if (shelfIcons != null) {
                                    int iw = (Integer) XposedHelpers.callMethod(shelfIcons, "getActualWidth");
                                    if (iw > 0 && iw < bgView.getWidth()) actualWidth = iw;
                                }
                            } catch (Throwable ignored) {}
                        }
                    }
                    if (actualWidth <= 0) actualWidth = bgView.getWidth();

                    int actualHeight = 0;
                    if (row != null) {
                        try {
                            int rh = (sGetActualHeightMethod != null) ? (Integer) sGetActualHeightMethod.invoke(row)
                                    : (Integer) XposedHelpers.callMethod(row, "getActualHeight");
                            if (rh > 0) {
                                actualHeight = rh;
                            }
                        } catch (Throwable ignored) {}
                    }
                    if (actualHeight <= 0 && sActualHeightField != null) {
                        try { actualHeight = sActualHeightField.getInt(bgView); } catch (Throwable ignored) {}
                    }
                    if (actualHeight <= 0) actualHeight = bgView.getHeight();

                    int clipTop = 0;
                    if (sClipTopAmountField != null) {
                        try { clipTop = sClipTopAmountField.getInt(bgView); } catch (Throwable ignored) {}
                    }
                    int clipBottom = 0;
                    if (sClipBottomAmountField != null) {
                        try { clipBottom = sClipBottomAmountField.getInt(bgView); } catch (Throwable ignored) {}
                    }

                    if (shadeGlass) {
                        if (sTopOverlapField != null) clipTop = Math.max(clipTop, sTopOverlapField.getInt(bgView));
                        if (sBottomOverlapField != null) clipBottom = Math.max(clipBottom, sBottomOverlapField.getInt(bgView));
                    }

                    boolean alignedRight = shadeGlass && bgView.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
                    if (isShelf) {
                        try {
                            alignedRight = (Boolean) XposedHelpers.callMethod(bgView, "isAlignedToRight");
                        } catch (Throwable ignored) {}
                    }

                    int drawLeft = alignedRight ? (bgView.getWidth() - actualWidth) : 0;
                    int drawRight = alignedRight ? bgView.getWidth() : actualWidth;
                    int drawTop = Math.max(bgTop, clipTop);
                    int drawBottom = clipBottom > 0 ? (actualHeight - clipBottom) : actualHeight;

                    if (drawRight <= drawLeft || drawBottom <= drawTop) {
                        if (isHun) {
                            Object bdObj = bgView.getTag(TAG_KEY_BLUR_DRAWABLE);
                            if (bdObj instanceof Drawable) {
                                ((Drawable) bdObj).setAlpha(0);
                                ((Drawable) bdObj).setVisible(false, false);
                            }
                        } else if (Boolean.TRUE.equals(bgView.getTag(TAG_KEY_BLUR_ACTIVE))) {
                            cleanupBlur(bgView);
                        }
                        param.setResult(null);
                        return;
                    }

                    // System corner radius detection:
                    float densityF = ctx.getResources().getDisplayMetrics().density;
                    float topRadius = 0f;
                    float bottomRadius = 0f;
                    boolean radiusDetermined = false;

                    if (isShelf) {
                        topRadius = (drawBottom - drawTop) / 2.0f;
                        bottomRadius = topRadius;
                        radiusDetermined = true;
                    } else {
                        // 1. Try to read system calculated mCornerRadii from NotificationBackgroundView
                        if (sCornerRadiiField != null) {
                            try {
                                float[] bgRadii = (float[]) sCornerRadiiField.get(bgView);
                                if (bgRadii != null && bgRadii.length >= 8) {
                                    topRadius = bgRadii[0];
                                    bottomRadius = bgRadii[4];
                                    radiusDetermined = true;
                                }
                            } catch (Throwable ignored) {}
                        }
                        // 2. Try row.getTopCornerRadius() / row.getBottomCornerRadius()
                        if (!radiusDetermined && row != null && sGetTopCornerRadiusMethod != null && sGetBottomCornerRadiusMethod != null) {
                            try {
                                Float tr = (Float) sGetTopCornerRadiusMethod.invoke(row);
                                Float br = (Float) sGetBottomCornerRadiusMethod.invoke(row);
                                if (tr != null && br != null) {
                                    topRadius = tr;
                                    bottomRadius = br;
                                    radiusDetermined = true;
                                }
                            } catch (Throwable ignored) {}
                        }
                        // 3. For single rows, if radius was 0 (e.g. uninitialized before first layout), use standard system radius
                        if (!radiusDetermined || (!isChildOfGroup && topRadius <= 0f && bottomRadius <= 0f)) {
                            topRadius = 18.0f * densityF;
                            bottomRadius = topRadius;
                        }
                    }

                    int shapeTop = shadeGlass ? bgTop : drawTop;
                    int shapeBottom = shadeGlass ? actualHeight : drawBottom;
                    float maxRadius = Math.min(drawRight - drawLeft, shapeBottom - shapeTop) / 2.0f;
                    if (topRadius > maxRadius) topRadius = maxRadius;
                    if (bottomRadius > maxRadius) bottomRadius = maxRadius;

                    // Reusable fast draw path (zero GC allocations)
                    sDrawRectF.set(drawLeft, shapeTop, drawRight, shapeBottom);
                    sCornerRadii[0] = topRadius;
                    sCornerRadii[1] = topRadius;
                    sCornerRadii[2] = topRadius;
                    sCornerRadii[3] = topRadius;
                    sCornerRadii[4] = bottomRadius;
                    sCornerRadii[5] = bottomRadius;
                    sCornerRadii[6] = bottomRadius;
                    sCornerRadii[7] = bottomRadius;

                    sClipPath.reset();
                    sClipPath.addRoundRect(sDrawRectF, sCornerRadii, Path.Direction.CW);

                    int saveCount = canvas.save();
                    try {
                        if (shadeGlass) canvas.clipRect(drawLeft, drawTop, drawRight, drawBottom);
                        canvas.clipPath(sClipPath);

                    // =========================================================
                    // 3-VERSION NOTIFICATION RENDERER DISPATCHER
                    // =========================================================
                        if (isHun) {
                            renderHeadsUpCard(canvas, bgView, row, drawLeft, drawTop, drawRight, drawBottom,
                                    topRadius, bottomRadius, sClipPath, isDark, densityF, combinedAlpha);
                        } else {
                            if (solidWeight > 0.01f) {
                                if (shadeGlass) {
                                    renderShadeGlassCard(canvas, bgView, drawLeft, shapeTop, drawRight, shapeBottom,
                                            isDark, densityF, combinedAlpha * solidWeight);
                                } else {
                                    renderShadeSystemCard(canvas, bgView, ctx, isShelf, drawLeft, drawTop, drawRight, drawBottom,
                                            sClipPath, isDark, combinedAlpha, solidWeight);
                                }
                            }
                            if (blurWeight > 0.01f) {
                                renderLockscreenCard(canvas, bgView, row, isShelf, drawLeft, drawTop, drawRight, drawBottom,
                                        topRadius, bottomRadius, sClipPath, isDark, densityF, combinedAlpha, blurWeight);
                            } else if (Boolean.TRUE.equals(bgView.getTag(TAG_KEY_BLUR_ACTIVE))) {
                                cleanupBlur(bgView);
                            }
                        }
                    } finally {
                        canvas.restoreToCount(saveCount);
                    }

                    // Cancel original onDraw because we cleanly render the chosen version!
                    param.setResult(null);
                }
            };

            XC_MethodHook registerBgViewHook = new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    if (param.thisObject instanceof View) {
                        sAllNotificationBackgrounds.add((View) param.thisObject);
                    }
                }
            };
            XC_MethodHook unregisterBgViewHook = new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    if (param.thisObject instanceof View) {
                        View v = (View) param.thisObject;
                        synchronized (sHunPreDrawListeners) {
                            ViewTreeObserver.OnPreDrawListener l = sHunPreDrawListeners.remove(v);
                            if (l != null) {
                                try {
                                    ViewTreeObserver vto = v.getViewTreeObserver();
                                    if (vto != null && vto.isAlive()) {
                                        vto.removeOnPreDrawListener(l);
                                    }
                                } catch (Throwable ignored) {}
                            }
                        }
                        cleanupBlur(v);
                        sAllNotificationBackgrounds.remove(v);
                    }
                }
            };
            try { XposedBridge.hookAllConstructors(notifBgClass, registerBgViewHook); } catch (Throwable ignored) {}
            try { XposedBridge.hookAllMethods(notifBgClass, "onAttachedToWindow", registerBgViewHook); } catch (Throwable ignored) {}
            try { XposedBridge.hookAllMethods(notifBgClass, "onDetachedFromWindow", unregisterBgViewHook); } catch (Throwable ignored) {}

            XposedBridge.hookAllMethods(notifBgClass, "onDraw", notifDrawHook);
            try {
                Class<?> notifShelfBgClass = cl.loadClass("com.android.systemui.statusbar.notification.shelf.NotificationShelfBackgroundView");
                if (notifShelfBgClass != null && notifShelfBgClass != notifBgClass) {
                    try { XposedBridge.hookAllConstructors(notifShelfBgClass, registerBgViewHook); } catch (Throwable ignored) {}
                    try { XposedBridge.hookAllMethods(notifShelfBgClass, "onAttachedToWindow", registerBgViewHook); } catch (Throwable ignored) {}
                    try { XposedBridge.hookAllMethods(notifShelfBgClass, "onDetachedFromWindow", unregisterBgViewHook); } catch (Throwable ignored) {}
                    XposedBridge.hookAllMethods(notifShelfBgClass, "onDraw", notifDrawHook);
                }
            } catch (Throwable ignored) {}

            try {
                Class<?> rivCls = cl.loadClass("com.android.systemui.statusbar.policy.RemoteInputView");
                XposedBridge.hookAllMethods(rivCls, "setBackgroundTintColor", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        View riv = (View) param.thisObject;
                        if (riv == null) return;
                        View p = (View) riv.getParent();
                        boolean isTargetParent = false;
                        while (p != null) {
                            if (p.getClass().getName().contains("ExpandableNotificationRow")) {
                                try {
                                    boolean onKg = (sStatusBarState == 1) && (sLsToShadeProgress <= 0.02f) && (sFractionToShade <= 0.02f);
                                    if (!onKg && sIsOnKeyguardMethod != null) {
                                        try { onKg = (Boolean) sIsOnKeyguardMethod.invoke(p); } catch (Throwable ignored) {}
                                    }
                                    boolean hun = false;
                                    if (sIsPinnedMethod != null) {
                                        try { hun = (Boolean) sIsPinnedMethod.invoke(p); } catch (Throwable ignored) {}
                                    }
                                    if (!hun && sIsHeadsUpMethod != null) {
                                        try { hun = (Boolean) sIsHeadsUpMethod.invoke(p); } catch (Throwable ignored) {}
                                    }
                                    if (!hun && sIsPinnedAndExpandedMethod != null) {
                                        try { hun = (Boolean) sIsPinnedAndExpandedMethod.invoke(p); } catch (Throwable ignored) {}
                                    }
                                    if (!hun && sIsHeadsUpStateMethod != null) {
                                        try { hun = (Boolean) sIsHeadsUpStateMethod.invoke(p); } catch (Throwable ignored) {}
                                    }
                                    if (onKg || hun) {
                                        isTargetParent = true;
                                    }
                                } catch (Throwable ignored) {}
                                break;
                            }
                            ViewParent vp = p.getParent();
                            p = (vp instanceof View) ? (View) vp : null;
                        }

                        if (isTargetParent) {
                            try {
                                Object bg = XposedHelpers.getObjectField(riv, "mContentBackground");
                                if (bg instanceof android.graphics.drawable.GradientDrawable) {
                                    android.graphics.drawable.GradientDrawable gd = (android.graphics.drawable.GradientDrawable) bg;
                                    Context ctx = riv.getContext();
                                    int uiMode = ctx.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
                                    boolean isDark = (uiMode == Configuration.UI_MODE_NIGHT_YES);
                                    float density = ctx.getResources().getDisplayMetrics().density;
                                    int strokeWidth = Math.max(2, Math.round(1.0f * density));

                                    int tintColor = isDark ? 0x40000000 : 0x30FFFFFF;
                                    int strokeColor = isDark ? 0x28FFFFFF : 0x45FFFFFF;

                                    gd.setColor(tintColor);
                                    gd.setStroke(strokeWidth, strokeColor);
                                }
                            } catch (Throwable ignored) {}
                        }
                    }
                });
                Log.e(TAG, "RemoteInputView hooked for frosted glass styling!");
            } catch (Throwable t) {
                Log.e(TAG, "RemoteInputView hook check: " + t.getMessage());
            }

            Log.e(TAG, "NotificationBackgroundView hooked for frosted glass blur style!");
        } catch (Throwable t) {
            Log.e(TAG, "Notification hooks check: " + t.getMessage());
        }
    }

    private static void ensureBlurReflection(ClassLoader cl) {
        if (sCreateBackgroundBlurDrawableMethod != null && sSetBlurRadiusMethod != null && sSetCornerRadiusSingleMethod != null) return;
        try {
            if (sGetViewRootImplMethod == null) {
                try {
                    sGetViewRootImplMethod = View.class.getMethod("getViewRootImpl");
                    sGetViewRootImplMethod.setAccessible(true);
                } catch (Throwable ignored) {}
            }
            if (sCreateBackgroundBlurDrawableMethod == null) {
                try {
                    Class<?> vriCls = (cl != null) ? cl.loadClass("android.view.ViewRootImpl") : Class.forName("android.view.ViewRootImpl");
                    sCreateBackgroundBlurDrawableMethod = vriCls.getMethod("createBackgroundBlurDrawable");
                    sCreateBackgroundBlurDrawableMethod.setAccessible(true);
                } catch (Throwable ignored) {}
            }
            if (sSetBlurRadiusMethod == null || sSetCornerRadiusSingleMethod == null) {
                try {
                    Class<?> bbdCls = (cl != null) ? cl.loadClass("com.android.internal.graphics.drawable.BackgroundBlurDrawable") : Class.forName("com.android.internal.graphics.drawable.BackgroundBlurDrawable");
                    try { sSetBlurRadiusMethod = bbdCls.getMethod("setBlurRadius", int.class); sSetBlurRadiusMethod.setAccessible(true); } catch (Throwable ignored) {}
                    try { sSetCornerRadiusMethod = bbdCls.getMethod("setCornerRadius", float.class, float.class, float.class, float.class); sSetCornerRadiusMethod.setAccessible(true); } catch (Throwable ignored) {}
                    try { sSetCornerRadiusSingleMethod = bbdCls.getMethod("setCornerRadius", float.class); sSetCornerRadiusSingleMethod.setAccessible(true); } catch (Throwable ignored) {}
                    try { sSetColorMethod = bbdCls.getMethod("setColor", int.class); sSetColorMethod.setAccessible(true); } catch (Throwable ignored) {}
                } catch (Throwable ignored) {}
            }
        } catch (Throwable t) {
            Log.e(TAG, "ensureBlurReflection error: " + t.getMessage());
        }
    }

    /**
     * Subclass of GradientDrawable that wraps BackgroundBlurDrawable so SystemUI binders
     * (e.g. VolumeDialogRingerViewBinder.applyCorners) can safely cast getBackground() to
     * GradientDrawable and animate corner radii without ClassCastException.
     */
    private static class BlurredCapsuleDrawable extends GradientDrawable {
        private final Drawable mBlurDrawable;

        BlurredCapsuleDrawable(Drawable blurDrawable) {
            super();
            mBlurDrawable = blurDrawable;
        }

        void updateBlur(int blurRadius, float cornerRadius) {
            if (mBlurDrawable != null) {
                if (sSetBlurRadiusMethod != null) {
                    try { sSetBlurRadiusMethod.invoke(mBlurDrawable, blurRadius); } catch (Throwable ignored) {}
                }
                if (sSetCornerRadiusSingleMethod != null) {
                    try { sSetCornerRadiusSingleMethod.invoke(mBlurDrawable, cornerRadius); } catch (Throwable ignored) {}
                }
            }
            super.setCornerRadius(cornerRadius);
        }

        void setSyncAlpha(int alpha255) {
            if (mBlurDrawable != null) {
                mBlurDrawable.setAlpha(alpha255);
                mBlurDrawable.setVisible(alpha255 > 0, false);
            }
            super.setAlpha(alpha255);
        }

        @Override
        public Drawable mutate() {
            return this;
        }

        @Override
        public void setBounds(int left, int top, int right, int bottom) {
            super.setBounds(left, top, right, bottom);
            if (mBlurDrawable != null) {
                mBlurDrawable.setBounds(left, top, right, bottom);
            }
        }

        @Override
        public void setBounds(Rect bounds) {
            super.setBounds(bounds);
            if (mBlurDrawable != null) {
                mBlurDrawable.setBounds(bounds);
            }
        }

        @Override
        public void setCornerRadius(float radius) {
            super.setCornerRadius(radius);
            if (mBlurDrawable != null && sSetCornerRadiusSingleMethod != null) {
                try { sSetCornerRadiusSingleMethod.invoke(mBlurDrawable, radius); } catch (Throwable ignored) {}
            }
        }

        @Override
        public void setCornerRadii(float[] radii) {
            super.setCornerRadii(radii);
            if (mBlurDrawable != null && radii != null && radii.length >= 8) {
                if (sSetCornerRadiusMethod != null) {
                    try {
                        sSetCornerRadiusMethod.invoke(mBlurDrawable, radii[0], radii[2], radii[4], radii[6]);
                    } catch (Throwable ignored) {
                        if (sSetCornerRadiusSingleMethod != null) {
                            try { sSetCornerRadiusSingleMethod.invoke(mBlurDrawable, radii[0]); } catch (Throwable ignored2) {}
                        }
                    }
                } else if (sSetCornerRadiusSingleMethod != null) {
                    try { sSetCornerRadiusSingleMethod.invoke(mBlurDrawable, radii[0]); } catch (Throwable ignored) {}
                }
            }
        }

        @Override
        public void setAlpha(int alpha) {
            super.setAlpha(alpha);
            if (mBlurDrawable != null) {
                mBlurDrawable.setAlpha(alpha);
                mBlurDrawable.setVisible(alpha > 0, false);
            }
        }

        @Override
        public boolean setVisible(boolean visible, boolean restart) {
            boolean changed = super.setVisible(visible, restart);
            if (mBlurDrawable != null) {
                mBlurDrawable.setVisible(visible, restart);
            }
            return changed;
        }

        @Override
        public void draw(Canvas canvas) {
            if (mBlurDrawable != null) {
                mBlurDrawable.draw(canvas);
            }
            super.draw(canvas);
        }
    }

    private static void setupVolumeCapsuleBlur(final View capsuleView, final int tintColor, final int strokeWidth, final int strokeColor) {
        if (capsuleView == null) return;
        final Context ctx = capsuleView.getContext();
        if (ctx == null) return;

        ensureBlurReflection(ctx.getClassLoader());

        final Runnable applyRunnable = new Runnable() {
            @Override
            public void run() {
                try {
                    Object vri = null;
                    if (sGetViewRootImplMethod != null) {
                        try { vri = sGetViewRootImplMethod.invoke(capsuleView); } catch (Throwable ignored) {}
                        if (vri == null && capsuleView.getRootView() != null) {
                            try { vri = sGetViewRootImplMethod.invoke(capsuleView.getRootView()); } catch (Throwable ignored) {}
                        }
                    }

                    Resources res = ctx.getResources();
                    float density = res.getDisplayMetrics().density;
                    int cornerRadius = Math.round(29.0f * density);
                    int blurRadius = Math.round(26.0f * density);
                    try {
                        int rId = res.getIdentifier("volume_dialog_panel_width_half", "dimen", "com.android.systemui");
                        if (rId != 0) cornerRadius = res.getDimensionPixelSize(rId);
                    } catch (Throwable ignored) {}
                    try {
                        int bId = res.getIdentifier("volume_dialog_background_surface_blur_radius", "dimen", "com.android.systemui");
                        if (bId != 0) blurRadius = res.getDimensionPixelSize(bId);
                    } catch (Throwable ignored) {}

                    if (capsuleView.getWidth() > 0) {
                        int halfW = capsuleView.getWidth() / 2;
                        if (halfW > 0 && halfW <= cornerRadius * 2) {
                            cornerRadius = halfW;
                        }
                    }

                    Drawable currentBg = capsuleView.getBackground();

                    // 1. If already a BlurredCapsuleDrawable, update its blur & tint parameters
                    if (currentBg instanceof BlurredCapsuleDrawable) {
                        BlurredCapsuleDrawable bcd = (BlurredCapsuleDrawable) currentBg;
                        bcd.updateBlur(blurRadius, cornerRadius);
                        bcd.setColor(tintColor);
                        bcd.setStroke(strokeWidth, strokeColor);
                        capsuleView.invalidate();
                        return;
                    }

                    // 2. If ViewRootImpl is available, create BackgroundBlurDrawable wrapped in BlurredCapsuleDrawable
                    //    (extends GradientDrawable so SystemUI binders casting background to GradientDrawable never crash!)
                    if (vri != null && sCreateBackgroundBlurDrawableMethod != null) {
                        Object blurDrawable = sCreateBackgroundBlurDrawableMethod.invoke(vri);
                        if (blurDrawable instanceof Drawable) {
                            if (sSetCornerRadiusSingleMethod != null) {
                                try { sSetCornerRadiusSingleMethod.invoke(blurDrawable, (float) cornerRadius); } catch (Throwable ignored) {}
                            }
                            if (sSetBlurRadiusMethod != null) {
                                try { sSetBlurRadiusMethod.invoke(blurDrawable, blurRadius); } catch (Throwable ignored) {}
                            }

                            BlurredCapsuleDrawable bcd = new BlurredCapsuleDrawable((Drawable) blurDrawable);
                            bcd.setCornerRadius(cornerRadius);
                            bcd.setColor(tintColor);
                            bcd.setStroke(strokeWidth, strokeColor);
                            capsuleView.setBackground(bcd);

                            // Sync blur & tint visibility/alpha with parent container reveal animation
                            // and suppress pre-layout placeholder flash (e.g. 241px button-sized state before slider expands)
                            if (!Boolean.TRUE.equals(capsuleView.getTag(TAG_KEY_VOL_PREDRAW))) {
                                capsuleView.setTag(TAG_KEY_VOL_PREDRAW, Boolean.TRUE);
                                final BlurredCapsuleDrawable bcdRef = bcd;
                                final boolean isMainVolBg = (sIdVolumeBg > 0 && capsuleView.getId() == sIdVolumeBg);
                                final int minVolHeightPx = Math.round(95.0f * density);

                                final ViewTreeObserver.OnPreDrawListener volSyncListener = new ViewTreeObserver.OnPreDrawListener() {
                                    @Override
                                    public boolean onPreDraw() {
                                        if (!capsuleView.isAttachedToWindow()) return true;
                                        int w = capsuleView.getWidth();
                                        int h = capsuleView.getHeight();
                                        boolean ready = capsuleView.isShown() && w > 0 && h > 0
                                                && (!isMainVolBg || h >= minVolHeightPx);
                                        float effAlpha = 0f;
                                        if (ready) {
                                            effAlpha = capsuleView.getAlpha();
                                            ViewParent p = capsuleView.getParent();
                                            while (p instanceof View && effAlpha > 0.01f) {
                                                effAlpha *= ((View) p).getAlpha();
                                                p = p.getParent();
                                            }
                                        }
                                        int a255 = ready ? Math.max(0, Math.min(255, Math.round(255f * effAlpha))) : 0;
                                        bcdRef.setSyncAlpha(a255);
                                        return true;
                                    }
                                };
                                ViewTreeObserver vto = capsuleView.getViewTreeObserver();
                                if (vto != null && vto.isAlive()) {
                                    vto.addOnPreDrawListener(volSyncListener);
                                }
                                volSyncListener.onPreDraw();
                            }

                            capsuleView.invalidate();
                            Log.e(TAG, "setupVolumeCapsuleBlur: Successfully attached BlurredCapsuleDrawable to " + capsuleView);
                            return;
                        }
                    }

                    // 3. ViewRootImpl not yet attached: style existing gradient and attach listener
                    if (currentBg != null) {
                        applyFrostedGlassToDrawable(currentBg, tintColor, strokeWidth, strokeColor);
                    }
                    if (!capsuleView.isAttachedToWindow()) {
                        capsuleView.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
                            @Override
                            public void onViewAttachedToWindow(View v) {
                                v.removeOnAttachStateChangeListener(this);
                                v.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        setupVolumeCapsuleBlur(capsuleView, tintColor, strokeWidth, strokeColor);
                                    }
                                });
                            }
                            @Override
                            public void onViewDetachedFromWindow(View v) {}
                        });
                    }
                } catch (Throwable t) {
                    Log.e(TAG, "setupVolumeCapsuleBlur error: " + t.getMessage(), t);
                }
            }
        };

        if (capsuleView.isAttachedToWindow()) {
            applyRunnable.run();
        } else {
            capsuleView.post(applyRunnable);
            applyRunnable.run();
        }
    }

    private static void styleVolumeDialog(View root) {
        if (root == null) return;
        Context ctx = root.getContext();
        if (ctx == null || ctx.getResources() == null) return;

        int uiMode = ctx.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        boolean isDark = (uiMode == Configuration.UI_MODE_NIGHT_YES);

        int tintColor = getFrostedBackdropColor(ctx, isDark);
        int strokeColor = isDark ? 0x28FFFFFF : 0x35FFFFFF;
        int strokeWidth = Math.max(2, Math.round(1.0f * ctx.getResources().getDisplayMetrics().density));

        if (sIdVolumeBg <= 0) {
            String pkg = ctx.getPackageName();
            Resources res = root.getResources();
            sIdExpandContainer = res.getIdentifier("volume_expand_bar_container", "id", pkg);
            if (sIdExpandContainer <= 0) sIdExpandContainer = res.getIdentifier("volume_expand_bar_container", "id", "com.android.systemui");
            if (sIdExpandContainer <= 0) sIdExpandContainer = 0x7f0b0b85;

            sIdVolumeBg = res.getIdentifier("volume_dialog_background", "id", pkg);
            if (sIdVolumeBg <= 0) sIdVolumeBg = res.getIdentifier("volume_dialog_background", "id", "com.android.systemui");
            if (sIdVolumeBg <= 0) sIdVolumeBg = 0x7f0b0b6b;

            sIdRingerBg = res.getIdentifier("ringer_buttons_background", "id", pkg);
            if (sIdRingerBg <= 0) sIdRingerBg = res.getIdentifier("ringer_buttons_background", "id", "com.android.systemui");
            if (sIdRingerBg <= 0) sIdRingerBg = 0x7f0b085a;

            sIdCaptions = res.getIdentifier("odi_captions", "id", pkg);
            if (sIdCaptions <= 0) sIdCaptions = res.getIdentifier("odi_captions", "id", "com.android.systemui");
            if (sIdCaptions <= 0) sIdCaptions = 0x7f0b0738;

            sIdA11y = res.getIdentifier("accessibility_slider_container", "id", pkg);
            if (sIdA11y <= 0) sIdA11y = res.getIdentifier("accessibility_slider_container", "id", "com.android.systemui");
            if (sIdA11y <= 0) sIdA11y = 0x7f0b006b;

            sIdAssistant = res.getIdentifier("assistant_slider_container", "id", pkg);
            if (sIdAssistant <= 0) sIdAssistant = res.getIdentifier("assistant_slider_container", "id", "com.android.systemui");
            if (sIdAssistant <= 0) sIdAssistant = 0x7f0b0129;
        }

        // 1. Remove solid opaque background plate from volume_expand_bar_container
        if (sIdExpandContainer != 0) {
            View expandContainer = root.findViewById(sIdExpandContainer);
            if (expandContainer != null && expandContainer.getBackground() != null) {
                expandContainer.setBackground(null);
            }
        }

        // 2. Style volume_dialog_background (the main volume capsule)
        if (sIdVolumeBg != 0) {
            View bgView = root.findViewById(sIdVolumeBg);
            if (bgView != null) {
                setupVolumeCapsuleBlur(bgView, tintColor, strokeWidth, strokeColor);
            }
        }

        // 3. Style ringer_buttons_background (the ringer drawer capsule)
        if (sIdRingerBg != 0) {
            View ringerBg = root.findViewById(sIdRingerBg);
            if (ringerBg != null) {
                setupVolumeCapsuleBlur(ringerBg, tintColor, strokeWidth, strokeColor);
            }
        }

        // 4. Hide odi_captions container so it does not bother the user
        if (sIdCaptions != 0) {
            View captionsView = root.findViewById(sIdCaptions);
            if (captionsView != null) {
                captionsView.setVisibility(View.GONE);
            }
        }
        if (root.getId() == sIdCaptions) {
            root.setVisibility(View.GONE);
        }

        // 5. Style accessibility & assistant sliders if present
        if (sIdA11y != 0) {
            View a11yView = root.findViewById(sIdA11y);
            if (a11yView != null) {
                setupVolumeCapsuleBlur(a11yView, tintColor, strokeWidth, strokeColor);
            }
        }

        if (sIdAssistant != 0) {
            View assistantView = root.findViewById(sIdAssistant);
            if (assistantView != null) {
                setupVolumeCapsuleBlur(assistantView, tintColor, strokeWidth, strokeColor);
            }
        }

        // 6. Deep scan and hide all Live Captions buttons and parent capsules
        styleAllCaptionsViews(root);
    }

    private static void styleAllCaptionsViews(View v) {
        if (v == null) return;
        String clsName = v.getClass().getName();
        if (clsName.contains("CaptionsToggleImageButton") || clsName.contains("CaptionsButton") || clsName.contains("odi_captions")) {
            v.setVisibility(View.GONE);
            ViewParent parent = v.getParent();
            if (parent instanceof View) {
                ((View) parent).setVisibility(View.GONE);
            }
            return;
        }
        if (v instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) v;
            int count = vg.getChildCount();
            for (int i = 0; i < count; i++) {
                styleAllCaptionsViews(vg.getChildAt(i));
            }
        }
    }

    private static void applyFrostedGlassToDrawable(Drawable bg, int tintColor, int strokeWidth, int strokeColor) {
        if (bg == null) return;
        if (bg instanceof android.graphics.drawable.LayerDrawable) {
            android.graphics.drawable.LayerDrawable ld = (android.graphics.drawable.LayerDrawable) bg;
            for (int i = 0; i < ld.getNumberOfLayers(); i++) {
                Drawable d = ld.getDrawable(i);
                if (d instanceof android.graphics.drawable.GradientDrawable) {
                    android.graphics.drawable.GradientDrawable gd = (android.graphics.drawable.GradientDrawable) d;
                    gd.setColor(tintColor);
                    gd.setStroke(strokeWidth, strokeColor);
                }
            }
        } else if (bg instanceof android.graphics.drawable.GradientDrawable) {
            android.graphics.drawable.GradientDrawable gd = (android.graphics.drawable.GradientDrawable) bg;
            gd.setColor(tintColor);
            gd.setStroke(strokeWidth, strokeColor);
        }
    }

    private static void onStatusBarStateChanged(int oldState, int newState) {
        if (sStatusBarState == newState && oldState == newState) return;
        sStatusBarState = newState;
        Log.i(TAG, "onStatusBarStateChanged: " + oldState + " -> " + newState);
        if (newState == 1) {
            sLsToShadeProgress = 0f;
            sFractionToShade = 0f;
            if (sAmbientState != null) {
                if (sLStoShadeProgressField != null) {
                    try { sLStoShadeProgressField.setFloat(sAmbientState, 0f); } catch (Throwable ignored) {}
                }
                if (sFractionToShadeField != null) {
                    try { sFractionToShadeField.setFloat(sAmbientState, 0f); } catch (Throwable ignored) {}
                }
            }
        }
        if (newState == 0 || newState == 2) {
            sLsToShadeProgress = 1f;
            sFractionToShade = 1f;
            sBouncerShowing = false;
            sBouncerFullyShown = false;
            sNotificationBlursCleared = false;
            sKeyguardAlpha = 1.0f;
            if (sNsslView != null) {
                sNsslView.setAlpha(1.0f);
            }
            invalidateNotificationStack();
        } else if (newState == 1 && sScreenAwake && !sBouncerFullyShown) {
            sKeyguardAlpha = 1.0f;
            if (sNsslView != null) {
                sNsslView.setAlpha(1.0f);
            }
            if (sNotificationBlursCleared) {
                sNotificationBlursCleared = false;
                invalidateNotificationStack();
            }
        }
    }

    // =========================================================================
    // 3 NOTIFICATION RENDERERS: LOCKSCREEN (BLUR) | SHADE (NO BLUR) | HEADS-UP
    // =========================================================================

    /**
     * Mode 1: Lockscreen Frosted Glass Card (Version 1 - Blur)
     * Authentic Nothing OS FrostedBackdropDrawable (exact widget frosted glass blur & tint)
     * Supporting grouped notifications, child rows, shelf icon collage, and smooth bouncer/shade fade.
     */
    private static void renderLockscreenCard(
            Canvas canvas, View bgView, View row, boolean isShelf,
            int drawLeft, int drawTop, int drawRight, int drawBottom,
            float topRadius, float bottomRadius, Path clipPath,
            boolean isDark, float densityF, float combinedAlpha, float blurWeight) {
        bgView.setTag(TAG_KEY_BLUR_ACTIVE, Boolean.TRUE);

        // Ensure any HUN window blur is deactivated on lockscreen
        Object existingBbd = bgView.getTag(TAG_KEY_BLUR_DRAWABLE);
        if (existingBbd instanceof Drawable) {
            try {
                Drawable d = (Drawable) existingBbd;
                d.setAlpha(0);
                d.setVisible(false, false);
            } catch (Throwable ignored) {}
        }

        Context ctx = bgView.getContext();
        if (backdropProvider == null && providerClass != null) {
            try {
                Object companion = XposedHelpers.getStaticObjectField(providerClass, "Companion");
                backdropProvider = XposedHelpers.callMethod(companion, "getInstance");
            } catch (Throwable ignored) {}
        }

        // Get or create FrostedBackdropDrawable for this lockscreen card
        Drawable frosted = (Drawable) bgView.getTag(TAG_KEY_FROSTED_DRAWABLE);
        if (frosted == null && backdropProvider != null && drawableClass != null && ctx != null) {
            try {
                frosted = (Drawable) XposedHelpers.newInstance(drawableClass, ctx, backdropProvider);
                if (frosted != null) {
                    frosted.setCallback(null);
                    if (setAnimateEntranceMethod != null) {
                        try { setAnimateEntranceMethod.invoke(frosted, false); } catch (Throwable ignored) {}
                    }
                    if (completeTransitionNowMethod != null) {
                        try { completeTransitionNowMethod.invoke(frosted); } catch (Throwable ignored) {}
                    }
                    if (onThemeChangedMethod != null) {
                        try { onThemeChangedMethod.invoke(frosted); } catch (Throwable ignored) {}
                    }
                    bgView.setTag(TAG_KEY_FROSTED_DRAWABLE, frosted);
                }
            } catch (Throwable t) {
                Log.e(TAG, "Failed creating FrostedBackdropDrawable for Lockscreen: " + t);
            }
        }

        // 1. Render authentic Nothing OS frosted glass card
        if (frosted != null) {
            if (ctx != null) {
                int currentUiMode = ctx.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
                Integer lastUi = (Integer) bgView.getTag(TAG_KEY_LAST_UI_MODE);
                if (lastUi == null || lastUi != currentUiMode) {
                    if (onThemeChangedMethod != null) {
                        try { onThemeChangedMethod.invoke(frosted); } catch (Throwable ignored) {}
                    }
                    bgView.setTag(TAG_KEY_LAST_UI_MODE, currentUiMode);
                }
            }

            if (setClipRadiusMethod != null) {
                try { setClipRadiusMethod.invoke(frosted, Math.max(topRadius, bottomRadius)); } catch (Throwable ignored) {}
            }

            int[] loc = sScreenLocation;
            bgView.getLocationOnScreen(loc);
            float screenX = loc[0] + drawLeft;
            float screenY = loc[1] + drawTop;

            if (updateOffsetMethod != null) {
                try { updateOffsetMethod.invoke(frosted, screenX, screenY); } catch (Throwable ignored) {}
            }

            frosted.setBounds(drawLeft, drawTop, drawRight, drawBottom);
            int alpha255 = Math.max(0, Math.min(255, Math.round(255 * combinedAlpha * blurWeight)));
            frosted.setAlpha(alpha255);
            frosted.draw(canvas);
        } else {
            // Fallback to widget frosted plate if FrostedBackdropDrawable is not yet ready
            int frostedColor = getFrostedBackdropColor(ctx, isDark);
            int baseTintAlpha = Color.alpha(frostedColor);
            int animTintAlpha = Math.round(baseTintAlpha * combinedAlpha * blurWeight);
            glassTintPaint.setColor((frostedColor & 0x00FFFFFF) | (animTintAlpha << 24));
            canvas.drawPath(clipPath, glassTintPaint);
        }

        drawNotificationRipple(canvas, bgView, drawLeft, drawTop, drawRight, drawBottom);
    }

    private static void renderShadeGlassCard(Canvas canvas, View bgView, int left, int top, int right,
            int bottom, boolean isDark, float density, float alpha) {
        int stockColor = resolveStockNotifColor(bgView);
        int frostedColor = getFrostedBackdropColor(bgView.getContext(), isDark);
        int color = Color.rgb(
                (Color.red(stockColor) * 3 + Color.red(frostedColor)) / 4,
                (Color.green(stockColor) * 3 + Color.green(frostedColor)) / 4,
                (Color.blue(stockColor) * 3 + Color.blue(frostedColor)) / 4);
        solidBgPaint.setColor(color);
        solidBgPaint.setAlpha(Math.round((isDark ? 140 : 174) * alpha));
        canvas.drawPath(sClipPath, solidBgPaint);

        glassBorderPaint.setStyle(Paint.Style.STROKE);
        glassBorderPaint.setStrokeWidth(density * 1.25f);
        glassBorderPaint.setColor(Color.WHITE);
        glassBorderPaint.setAlpha(Math.round((isDark ? 50 : 123) * alpha));
        canvas.drawPath(sClipPath, glassBorderPaint);
        drawNotificationRipple(canvas, bgView, left, top, right, bottom);
    }

    private static void drawNotificationRipple(Canvas canvas, View bgView, int drawLeft, int drawTop,
            int drawRight, int drawBottom) {
        Drawable ripple = (Drawable) bgView.getTag(TAG_KEY_RIPPLE);
        if (ripple == null) {
            try {
                Object bgObj = (sBackgroundField != null) ? sBackgroundField.get(bgView)
                        : XposedHelpers.getObjectField(bgView, "mBackground");
                if (bgObj instanceof android.graphics.drawable.LayerDrawable) {
                    android.graphics.drawable.LayerDrawable ld = (android.graphics.drawable.LayerDrawable) bgObj;
                    for (int i = 0; i < ld.getNumberOfLayers(); i++) {
                        Drawable layer = ld.getDrawable(i);
                        if (layer instanceof android.graphics.drawable.RippleDrawable) {
                            ripple = layer;
                            break;
                        }
                    }
                } else if (bgObj instanceof android.graphics.drawable.RippleDrawable) {
                    ripple = (Drawable) bgObj;
                }
                if (ripple != null) {
                    bgView.setTag(TAG_KEY_RIPPLE, ripple);
                }
            } catch (Throwable ignored) {}
        }
        if (ripple != null) {
            try {
                ripple.setBounds(drawLeft, drawTop, drawRight, drawBottom);
                ripple.draw(canvas);
            } catch (Throwable ignored) {}
        }
    }

    /**
     * Mode 2: Notification Shade System Card (Version 2 - No Blur / Stock Nothing OS Background)
     * 100% Authentic Nothing OS background drawable, zero blur, clean grouping
     */
    private static void renderShadeSystemCard(
            Canvas canvas, View bgView, Context ctx, boolean isShelf,
            int drawLeft, int drawTop, int drawRight, int drawBottom,
            Path clipPath, boolean isDark, float combinedAlpha, float solidWeight) {
        if (Boolean.TRUE.equals(bgView.getTag(TAG_KEY_BLUR_ACTIVE))) {
            cleanupBlur(bgView);
        }

        Drawable bg = null;
        if (sBackgroundField != null) {
            try { bg = (Drawable) sBackgroundField.get(bgView); } catch (Throwable ignored) {}
        }
        if (bg == null) {
            try { bg = (Drawable) XposedHelpers.getObjectField(bgView, "mBackground"); } catch (Throwable ignored) {}
        }

        if (bg != null) {
            bg.setBounds(drawLeft, drawTop, drawRight, drawBottom);
            int animAlpha = Math.max(0, Math.min(255, Math.round(255 * combinedAlpha * solidWeight)));
            bg.setAlpha(animAlpha);
            bg.draw(canvas);
        } else {
            int solidColor = resolveStockNotifColor(ctx, isDark);
            int baseSolidAlpha = Color.alpha(solidColor);
            int animSolidAlpha = Math.round(baseSolidAlpha * combinedAlpha * solidWeight);
            if (animSolidAlpha > 0) {
                solidBgPaint.setColor((solidColor & 0x00FFFFFF) | (animSolidAlpha << 24));
                canvas.drawPath(clipPath, solidBgPaint);
            }
        }
    }

    /**
     * Mode 3: Floating Heads-Up Notification Card (Option 1 - Predictive Dynamic Blur)
     * SurfaceFlinger BackgroundBlurDrawable with 1-VSYNC velocity prediction compensation
     * combined with authentic Nothing OS frosted Lucent tint and specular border highlight.
     */
    private static void renderHeadsUpCard(
            Canvas canvas, View bgView, View row,
            int drawLeft, int drawTop, int drawRight, int drawBottom,
            float topRadius, float bottomRadius, Path clipPath,
            boolean isDark, float densityF, float combinedAlpha) {
        bgView.setTag(TAG_KEY_BLUR_ACTIVE, Boolean.TRUE);

        // Frame-synchronized 120Hz tracking for Heads-Up notification appearance, dismissal and gestures
        synchronized (sHunPreDrawListeners) {
            if (!sHunPreDrawListeners.containsKey(bgView)) {
                ViewTreeObserver vto = bgView.getViewTreeObserver();
                if (vto != null && vto.isAlive()) {
                    HunPreDrawListener listener = new HunPreDrawListener(bgView, row);
                    vto.addOnPreDrawListener(listener);
                    sHunPreDrawListeners.put(bgView, listener);
                }
            }
        }

        Context ctx = bgView.getContext();
        ensureBlurReflection(ctx != null ? ctx.getClassLoader() : null);

        // 1. Get or create BackgroundBlurDrawable for live window background blur (SurfaceFlinger)
        Drawable blurDrawable = (Drawable) bgView.getTag(TAG_KEY_BLUR_DRAWABLE);
        if (blurDrawable == null && sCreateBackgroundBlurDrawableMethod != null) {
            Object vri = null;
            if (sGetViewRootImplMethod != null) {
                try { vri = sGetViewRootImplMethod.invoke(bgView); } catch (Throwable ignored) {}
            }
            if (vri == null && bgView.getRootView() != null && sGetViewRootImplMethod != null) {
                try { vri = sGetViewRootImplMethod.invoke(bgView.getRootView()); } catch (Throwable ignored) {}
            }
            if (vri == null && row != null && sGetViewRootImplMethod != null) {
                try { vri = sGetViewRootImplMethod.invoke(row); } catch (Throwable ignored) {}
            }
            if (vri != null) {
                try {
                    Object created = sCreateBackgroundBlurDrawableMethod.invoke(vri);
                    if (created instanceof Drawable) {
                        blurDrawable = (Drawable) created;
                        bgView.setTag(TAG_KEY_BLUR_DRAWABLE, blurDrawable);
                    }
                } catch (Throwable t) {
                    Log.e(TAG, "Failed creating BackgroundBlurDrawable for HUN: " + t);
                }
            }
        }

        // 2. Velocity-predicted bounds compensation to cancel out SurfaceFlinger's 1-VSYNC latency
        float vy = 0f;
        Object tagVy = bgView.getTag(TAG_KEY_HUN_VELOCITY_Y);
        if (tagVy instanceof Float) vy = (Float) tagVy;

        float vx = 0f;
        Object tagVx = bgView.getTag(TAG_KEY_HUN_VELOCITY_X);
        if (tagVx instanceof Float) vx = (Float) tagVx;

        // Predictive lead shift: SurfaceFlinger applies blur on the next VSYNC frame,
        // so we tell SurfaceFlinger where the moving & shrinking view will be on that next frame!
        int leadY = Math.max(-150, Math.min(150, Math.round(vy * 1.15f)));
        int leadX = Math.max(-150, Math.min(150, Math.round(vx * 1.15f)));

        float vTop = 0f;
        Object tagVTop = bgView.getTag(TAG_KEY_HUN_VELOCITY_TOP);
        if (tagVTop instanceof Float) vTop = (Float) tagVTop;

        float vBot = 0f;
        Object tagVBot = bgView.getTag(TAG_KEY_HUN_VELOCITY_BOT);
        if (tagVBot instanceof Float) vBot = (Float) tagVBot;

        int leadTop = Math.max(-120, Math.min(120, Math.round(vTop * 1.15f)));
        int leadBot = Math.max(-120, Math.min(120, Math.round(vBot * 1.15f)));

        int blurLeft = drawLeft + leadX;
        int blurTop = drawTop + leadY + leadTop;
        int blurRight = drawRight + leadX;
        int blurBottom = Math.max(blurTop, drawBottom + leadY + leadBot);

        // 3. Draw live window background blur
        int alpha255 = Math.max(0, Math.min(255, Math.round(255 * combinedAlpha)));
        if (blurDrawable != null) {
            if (blurBottom - blurTop <= 1 || blurRight - blurLeft <= 1 || alpha255 <= 0) {
                blurDrawable.setAlpha(0);
                blurDrawable.setVisible(false, false);
            } else {
                int blurRadius = Math.round(26.0f * densityF);
                float blurMaxRadius = (blurBottom - blurTop) / 2.0f;
                float effTopRadius = Math.min(topRadius, blurMaxRadius);
                float effBottomRadius = Math.min(bottomRadius, blurMaxRadius);
                if (sSetBlurRadiusMethod != null) {
                    try { sSetBlurRadiusMethod.invoke(blurDrawable, blurRadius); } catch (Throwable ignored) {}
                }
                if (sSetCornerRadiusMethod != null) {
                    try {
                        sSetCornerRadiusMethod.invoke(blurDrawable, effTopRadius, effTopRadius, effBottomRadius, effBottomRadius);
                    } catch (Throwable ignored) {
                        if (sSetCornerRadiusSingleMethod != null) {
                            try { sSetCornerRadiusSingleMethod.invoke(blurDrawable, effTopRadius); } catch (Throwable ignored2) {}
                        }
                    }
                } else if (sSetCornerRadiusSingleMethod != null) {
                    try { sSetCornerRadiusSingleMethod.invoke(blurDrawable, effTopRadius); } catch (Throwable ignored) {}
                }

                blurDrawable.setBounds(blurLeft, blurTop, blurRight, blurBottom);
                blurDrawable.setAlpha(alpha255);
                blurDrawable.setVisible(true, false);
                blurDrawable.draw(canvas);
            }
        }

        // 4. Render authentic Nothing OS translucent Lucent frosted tint over the blur
        int frostedColor = getFrostedBackdropColor(ctx, isDark);
        int baseTintAlpha = Color.alpha(frostedColor);
        int animTintAlpha = Math.round(baseTintAlpha * combinedAlpha);
        if (animTintAlpha > 0) {
            glassTintPaint.setColor((frostedColor & 0x00FFFFFF) | (animTintAlpha << 24));
            canvas.drawPath(clipPath, glassTintPaint);
        }

        // 4b. AGSL Frosted Glass micro-grain & subtle top specular sheen (Android 13+)
        if (Build.VERSION.SDK_INT >= 33 && combinedAlpha > 0.01f && !sHunShaderInitFailed) {
            try {
                if (sHunFrostedShader == null) {
                    sHunFrostedShader = new RuntimeShader(HUN_FROSTED_AGSL);
                    sHunGlassPaint.setShader((Shader) sHunFrostedShader);
                }
                RuntimeShader shader = (RuntimeShader) sHunFrostedShader;
                shader.setFloatUniform("uOrigin", (float) drawLeft, (float) drawTop);
                shader.setFloatUniform("uSize", (float) Math.max(1, drawRight - drawLeft), (float) Math.max(1, drawBottom - drawTop));
                shader.setFloatUniform("uAlpha", combinedAlpha);
                shader.setFloatUniform("uIsDark", isDark ? 1.0f : 0.0f);
                canvas.drawPath(clipPath, sHunGlassPaint);
            } catch (Throwable t) {
                sHunShaderInitFailed = true;
                Log.e(TAG, "AGSL frosted shader unavailable: " + t);
            }
        }

        // 5. Crisp directional specular glass border highlight (Nothing OS design signature)
        if (alpha255 > 0) {
            if (sCachedHunBorderGrad == null
                    || sCachedBorderL != drawLeft || sCachedBorderT != drawTop
                    || sCachedBorderR != drawRight || sCachedBorderB != drawBottom
                    || sCachedBorderDark != isDark) {
                int[] gradColors = isDark
                        ? new int[]{0x64FFFFFF, 0x20FFFFFF, 0x36FFFFFF}
                        : new int[]{0x90FFFFFF, 0x38FFFFFF, 0x52FFFFFF};
                sCachedHunBorderGrad = new LinearGradient(
                        drawLeft, drawTop, drawRight, drawBottom,
                        gradColors, new float[]{0.0f, 0.48f, 1.0f}, Shader.TileMode.CLAMP);
                sCachedBorderL = drawLeft;
                sCachedBorderT = drawTop;
                sCachedBorderR = drawRight;
                sCachedBorderB = drawBottom;
                sCachedBorderDark = isDark;
                sHunBorderPaint.setShader(sCachedHunBorderGrad);
            }
            sHunBorderPaint.setAlpha(alpha255);
            sHunBorderPaint.setStrokeWidth(Math.max(1.5f, 1.05f * densityF));
            canvas.drawPath(clipPath, sHunBorderPaint);
        }

        // 6. Ripple effect for touch feedback on floating HUN cards
        Drawable ripple = (Drawable) bgView.getTag(TAG_KEY_RIPPLE);
        if (ripple == null) {
            try {
                Object bgObj = (sBackgroundField != null) ? sBackgroundField.get(bgView)
                        : XposedHelpers.getObjectField(bgView, "mBackground");
                if (bgObj instanceof android.graphics.drawable.LayerDrawable) {
                    android.graphics.drawable.LayerDrawable ld = (android.graphics.drawable.LayerDrawable) bgObj;
                    for (int i = 0; i < ld.getNumberOfLayers(); i++) {
                        Drawable layer = ld.getDrawable(i);
                        if (layer instanceof android.graphics.drawable.RippleDrawable) {
                            ripple = layer;
                            break;
                        }
                    }
                } else if (bgObj instanceof android.graphics.drawable.RippleDrawable) {
                    ripple = (Drawable) bgObj;
                }
                if (ripple != null) {
                    bgView.setTag(TAG_KEY_RIPPLE, ripple);
                }
            } catch (Throwable ignored) {}
        }
        if (ripple != null) {
            try {
                ripple.setBounds(drawLeft, drawTop, drawRight, drawBottom);
                ripple.draw(canvas);
            } catch (Throwable ignored) {}
        }
    }

    private static int getCurrentStatusBarState() {
        if (sStatusBarStateController != null) {
            try {
                if (sStateField != null) {
                    return sStateField.getInt(sStatusBarStateController);
                } else {
                    Object state = XposedHelpers.getObjectField(sStatusBarStateController, "mState");
                    if (state instanceof Integer) return (Integer) state;
                }
            } catch (Throwable ignored) {}
        }
        return sStatusBarState;
    }

    private static boolean isShadeExpanded() {
        if (sStatusBarState == 2) return true;
        if (sNsslView != null && sNsslIsExpandedMethod != null) {
            try {
                return Boolean.TRUE.equals(sNsslIsExpandedMethod.invoke(sNsslView));
            } catch (Throwable ignored) {}
        }
        return false;
    }

    private static boolean isRowHun(View row) {
        if (row == null) return false;
        try {
            if (sIsPinnedMethod != null && Boolean.TRUE.equals(sIsPinnedMethod.invoke(row))) {
                row.setTag(TAG_KEY_WAS_HUN, Boolean.TRUE);
                return true;
            }
        } catch (Throwable ignored) {}
        try {
            if (sIsPinnedAndExpandedMethod != null && Boolean.TRUE.equals(sIsPinnedAndExpandedMethod.invoke(row))) {
                row.setTag(TAG_KEY_WAS_HUN, Boolean.TRUE);
                return true;
            }
        } catch (Throwable ignored) {}
        try {
            if (sIsHeadsUpAnimatingAwayMethod != null && Boolean.TRUE.equals(sIsHeadsUpAnimatingAwayMethod.invoke(row))) {
                row.setTag(TAG_KEY_WAS_HUN, Boolean.TRUE);
                return true;
            }
        } catch (Throwable ignored) {}

        // Keep HUN blur active throughout the entire auto-hide / disappear shrink animation
        // when the shade is closed, even if isPinned / isHeadsUpAnimatingAway flips early
        if (getCurrentStatusBarState() == 0 && !isShadeExpanded()) {
            try {
                if (sIsHeadsUpMethod != null && Boolean.TRUE.equals(sIsHeadsUpMethod.invoke(row))) {
                    row.setTag(TAG_KEY_WAS_HUN, Boolean.TRUE);
                    return true;
                }
            } catch (Throwable ignored) {}
            try {
                if (sIsHeadsUpStateMethod != null && Boolean.TRUE.equals(sIsHeadsUpStateMethod.invoke(row))) {
                    row.setTag(TAG_KEY_WAS_HUN, Boolean.TRUE);
                    return true;
                }
            } catch (Throwable ignored) {}
            if (Boolean.TRUE.equals(row.getTag(TAG_KEY_WAS_HUN))) {
                return true;
            }
        } else {
            row.setTag(TAG_KEY_WAS_HUN, null);
        }
        return false;
    }

    private static class HunPreDrawListener implements ViewTreeObserver.OnPreDrawListener {
        private final WeakReference<View> mBgViewRef;
        private final WeakReference<View> mRowRef;
        private float mLastY = Float.NaN;
        private float mLastX = Float.NaN;
        private float mLastAlpha = Float.NaN;
        private float mLastScaleX = Float.NaN;
        private float mLastScaleY = Float.NaN;
        private int mLastActualHeight = -1;
        private int mLastActualWidth = -1;
        private int mLastClipTop = -1;
        private int mLastClipBottom = -1;
        private int mLastDrawTop = Integer.MIN_VALUE;
        private int mLastDrawBottom = Integer.MIN_VALUE;

        HunPreDrawListener(View bgView, View row) {
            mBgViewRef = new WeakReference<>(bgView);
            mRowRef = new WeakReference<>(row);
        }

        @Override
        public boolean onPreDraw() {
            View bgView = mBgViewRef.get();
            if (bgView == null || !bgView.isAttachedToWindow()) return true;
            View row = mRowRef.get();
            if (row == null) {
                ViewParent p = bgView.getParent();
                if (p instanceof View) row = (View) p;
            }
            if (row != null && isRowHun(row)) {
                float y = row.getTranslationY();
                float x = row.getTranslationX();
                float alpha = row.getAlpha() * bgView.getAlpha();
                float scaleX = row.getScaleX();
                float scaleY = row.getScaleY();

                int h = 0;
                if (sGetActualHeightMethod != null) {
                    try {
                        Integer rh = (Integer) sGetActualHeightMethod.invoke(row);
                        if (rh != null && rh > 0) h = rh;
                    } catch (Throwable ignored) {}
                }
                if (h <= 0 && sActualHeightField != null) {
                    try { h = sActualHeightField.getInt(bgView); } catch (Throwable ignored) {}
                }
                if (h <= 0) h = bgView.getHeight();

                int w = 0;
                if (sActualWidthField != null) {
                    try { w = sActualWidthField.getInt(bgView); } catch (Throwable ignored) {}
                }
                if (w <= 0) w = bgView.getWidth();

                int bgTop = 0;
                if (sBackgroundTopField != null) {
                    try { bgTop = sBackgroundTopField.getInt(bgView); } catch (Throwable ignored) {}
                }
                int ct = 0;
                if (sClipTopAmountField != null) {
                    try { ct = sClipTopAmountField.getInt(bgView); } catch (Throwable ignored) {}
                }
                int cb = 0;
                if (sClipBottomAmountField != null) {
                    try { cb = sClipBottomAmountField.getInt(bgView); } catch (Throwable ignored) {}
                }

                int curTop = Math.max(bgTop, ct);
                int curBot = cb > 0 ? (h - cb) : h;

                if (y != mLastY || x != mLastX || alpha != mLastAlpha || scaleX != mLastScaleX || scaleY != mLastScaleY
                        || h != mLastActualHeight || w != mLastActualWidth || ct != mLastClipTop || cb != mLastClipBottom) {
                    float vy = !Float.isNaN(mLastY) ? (y - mLastY) : 0f;
                    float vx = !Float.isNaN(mLastX) ? (x - mLastX) : 0f;
                    float vTop = (mLastDrawTop != Integer.MIN_VALUE) ? (float) (curTop - mLastDrawTop) : 0f;
                    float vBot = (mLastDrawBottom != Integer.MIN_VALUE) ? (float) (curBot - mLastDrawBottom) : 0f;

                    bgView.setTag(TAG_KEY_HUN_VELOCITY_Y, vy);
                    bgView.setTag(TAG_KEY_HUN_VELOCITY_X, vx);
                    bgView.setTag(TAG_KEY_HUN_VELOCITY_TOP, vTop);
                    bgView.setTag(TAG_KEY_HUN_VELOCITY_BOT, vBot);

                    mLastY = y;
                    mLastX = x;
                    mLastAlpha = alpha;
                    mLastScaleX = scaleX;
                    mLastScaleY = scaleY;
                    mLastActualHeight = h;
                    mLastActualWidth = w;
                    mLastClipTop = ct;
                    mLastClipBottom = cb;
                    mLastDrawTop = curTop;
                    mLastDrawBottom = curBot;
                    bgView.invalidate();
                } else {
                    bgView.setTag(TAG_KEY_HUN_VELOCITY_Y, 0f);
                    bgView.setTag(TAG_KEY_HUN_VELOCITY_X, 0f);
                    bgView.setTag(TAG_KEY_HUN_VELOCITY_TOP, 0f);
                    bgView.setTag(TAG_KEY_HUN_VELOCITY_BOT, 0f);
                }
            }
            return true;
        }
    }

    private static void onLsToShadeProgressChanged(float progress) {
        sLsToShadeProgress = progress;
    }

    private static boolean isBouncerInTransit() {
        if (sStatusBarKeyguardViewManager != null) {
            try {
                Object res = XposedHelpers.callMethod(sStatusBarKeyguardViewManager, "isPrimaryBouncerInTransit");
                if (res instanceof Boolean) return (Boolean) res;
            } catch (Throwable ignored) {}
        }
        return false;
    }

    private static float evaluateCubicBezier(float x1, float y1, float x2, float y2, float t) {
        if (t <= 0f) return 0f;
        if (t >= 1f) return 1f;
        float u = t;
        for (int i = 0; i < 8; i++) {
            float oneMinusU = 1.0f - u;
            float u2 = u * u;
            float curX = 3.0f * oneMinusU * oneMinusU * u * x1 + 3.0f * oneMinusU * u2 * x2 + u * u2;
            float dx = 3.0f * oneMinusU * oneMinusU * x1 + 6.0f * oneMinusU * u * (x2 - x1) + 3.0f * u2 * (1.0f - x2);
            if (Math.abs(curX - t) < 1e-5f || Math.abs(dx) < 1e-6f) break;
            u -= (curX - t) / dx;
            if (u < 0.0f) { u = 0.0f; break; }
            if (u > 1.0f) { u = 1.0f; break; }
        }
        float oneMinusU = 1.0f - u;
        float u2 = u * u;
        return 3.0f * oneMinusU * oneMinusU * u * y1 + 3.0f * oneMinusU * u2 * y2 + u * u2;
    }

    // Exact easing curve used by Compose LockscreenElementKeys.Region.Lower (bottom buttons),
    // Region.Upper (widgets & clock), and Notifications.Stack:
    // fractionRange(to = 0.3f, easing = Easings.PredictiveBack = CubicBezierEasing(0.1f, 0.1f, 0.0f, 1.0f))
    private static float evaluatePredictiveBack(float fraction) {
        return evaluateCubicBezier(0.1f, 0.1f, 0.0f, 1.0f, fraction);
    }

    private static String contentToString(Object obj) {
        if (obj == null) return "";
        try {
            Object dbg = XposedHelpers.callMethod(obj, "getDebugName");
            if (dbg != null) return dbg.toString().toLowerCase();
        } catch (Throwable ignored) {}
        return obj.toString().toLowerCase();
    }

    private static Object extractContent(Object target, String fieldName) {
        if (target == null) return null;
        try {
            return XposedHelpers.getObjectField(target, fieldName);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void applyKeyguardAlpha(final float alpha, final boolean isBouncerToLs) {
        if (Math.abs(sKeyguardAlpha - alpha) < 0.003f) return;
        sKeyguardAlpha = alpha;

        Runnable r = new Runnable() {
            @Override
            public void run() {
                if (sNsslView != null) {
                    sNsslView.setAlpha(alpha);
                }
                if (alpha <= 0.01f) {
                    sBouncerShowing = true;
                    sBouncerFullyShown = true;
                    if (!sNotificationBlursCleared) {
                        sNotificationBlursCleared = true;
                        clearAllNotificationBlurs();
                    }
                } else {
                    sBouncerFullyShown = false;
                    sNotificationBlursCleared = false;
                    if (alpha >= 0.99f) {
                        sBouncerShowing = false;
                    }
                    invalidateNotificationStack();
                }
            }
        };

        if (Looper.myLooper() == Looper.getMainLooper()) {
            r.run();
        } else {
            Handler h = getMainHandler();
            if (h != null) h.post(r);
        }
    }

    private static void handleBouncerTransitionProgress(Object fromObj, Object toObj, float progress, String caller) {
        if (fromObj == null && toObj == null) return;
        String from = contentToString(fromObj);
        String to = contentToString(toObj);

        boolean isLsToBouncer = (from.contains("lockscreen") && to.contains("bouncer"));
        boolean isBouncerToLs = (from.contains("bouncer") && to.contains("lockscreen"));

        if (!isLsToBouncer && !isBouncerToLs) {
            return;
        }

        float rawProgress = Math.max(0.0f, Math.min(1.0f, progress));
        float alpha;
        if (isLsToBouncer) {
            // Lockscreen -> Bouncer:
            // 1:1 match with Compose widgets & bottom buttons:
            // fractionRange(to = 0.3f, easing = Easings.PredictiveBack)
            float fraction = Math.min(1.0f, rawProgress / 0.3f);
            float eased = evaluatePredictiveBack(fraction);
            alpha = Math.max(0.0f, Math.min(1.0f, 1.0f - eased));
        } else {
            // Bouncer -> Lockscreen:
            float distToLockscreen = 1.0f - rawProgress;
            float fraction = Math.min(1.0f, distToLockscreen / 0.3f);
            float eased = evaluatePredictiveBack(fraction);
            alpha = Math.max(0.0f, Math.min(1.0f, 1.0f - eased));
        }

        applyKeyguardAlpha(alpha, isBouncerToLs);
    }

    private static void handleBouncerTransitionProgress(Object fromObj, Object toObj, float progress) {
        handleBouncerTransitionProgress(fromObj, toObj, progress, "default");
    }

    private static final Runnable sBouncerSettleRunnable = new Runnable() {
        @Override
        public void run() {
            if (sBouncerShowing) {
                if (sKeyguardAlpha < 0.05f) {
                    sBouncerFullyShown = true;
                    sNotificationBlursCleared = true;
                    sKeyguardAlpha = 0f;
                    if (sNsslView != null) {
                        sNsslView.setAlpha(0f);
                    }
                    clearAllNotificationBlurs();
                } else if (!isBouncerInTransit()) {
                    // Still in bouncer mode but alpha hasn't zeroed out and transition ended
                    if (sKeyguardAlpha < 0.25f) {
                        sBouncerFullyShown = true;
                        sNotificationBlursCleared = true;
                        sKeyguardAlpha = 0f;
                        if (sNsslView != null) {
                            sNsslView.setAlpha(0f);
                        }
                        clearAllNotificationBlurs();
                    }
                } else {
                    Handler h = getMainHandler();
                    if (h != null) {
                        h.postDelayed(sBouncerSettleRunnable, 150);
                    }
                }
            }
        }
    };

    private static void onBouncerShowingChanged(boolean showing, String reason) {
        if (sBouncerShowing == showing) return;
        sBouncerShowing = showing;
        Log.e(TAG, "onBouncerShowingChanged: " + showing + " (" + reason + ")");
        Handler h = getMainHandler();
        if (showing) {
            sBouncerFullyShown = false;
            sNotificationBlursCleared = false;
            if (h != null) {
                h.removeCallbacks(sWakeInvalidateRunnable);
                h.removeCallbacks(sBouncerSettleRunnable);
                h.postDelayed(sBouncerSettleRunnable, 300);
            }
            // Allow smooth fade-out during gesture/transition
            invalidateNotificationStack();
        } else {
            sBouncerFullyShown = false;
            sNotificationBlursCleared = false;
            if (h != null) {
                h.removeCallbacks(sBouncerSettleRunnable);
            }
            if (sScreenAwake && sStatusBarState == 1) {
                if (sKeyguardRootView != null) {
                    float krvAlpha = sKeyguardRootView.getAlpha();
                    sKeyguardAlpha = (krvAlpha > 0.1f) ? krvAlpha : 1.0f;
                    if (sNsslView != null) sNsslView.setAlpha(sKeyguardAlpha);
                } else {
                    sKeyguardAlpha = 1.0f;
                    if (sNsslView != null) sNsslView.setAlpha(1.0f);
                }
                invalidateNotificationStack();
            }
        }
    }

    private static void onBouncerShowingChanged(boolean showing) {
        onBouncerShowingChanged(showing, "unspecified");
    }

    private static final Runnable sInvalidateNotificationStackRunnable = new Runnable() {
        @Override
        public void run() {
            try {
                synchronized (sAllNotificationBackgrounds) {
                    for (View bg : sAllNotificationBackgrounds) {
                        if (bg != null) {
                            bg.invalidate();
                            ViewParent p = bg.getParent();
                            if (p instanceof View) {
                                ((View) p).invalidate();
                            }
                        }
                    }
                }
                if (sNsslView != null) {
                    sNsslView.invalidate();
                    if (sNsslView instanceof ViewGroup) {
                        ViewGroup vg = (ViewGroup) sNsslView;
                        int count = vg.getChildCount();
                        for (int i = 0; i < count; i++) {
                            View child = vg.getChildAt(i);
                            if (child != null) {
                                child.invalidate();
                                if (child instanceof ViewGroup) {
                                    ViewGroup cvg = (ViewGroup) child;
                                    for (int j = 0; j < cvg.getChildCount(); j++) {
                                        View sub = cvg.getChildAt(j);
                                        if (sub != null) sub.invalidate();
                                    }
                                }
                            }
                        }
                    }
                    View root = sNsslView.getRootView();
                    if (root != null) root.invalidate();
                }
                if (sRootWindowView != null) {
                    sRootWindowView.invalidate();
                }
            } catch (Throwable ignored) {}
        }
    };

    private static void invalidateNotificationStack() {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            sInvalidateNotificationStackRunnable.run();
        } else {
            Handler h = getMainHandler();
            if (h != null) {
                h.removeCallbacks(sInvalidateNotificationStackRunnable);
                h.post(sInvalidateNotificationStackRunnable);
            } else if (sNsslView != null) {
                sNsslView.removeCallbacks(sInvalidateNotificationStackRunnable);
                sNsslView.post(sInvalidateNotificationStackRunnable);
            }
        }
    }

    private static final Runnable sClearAllNotificationBlursRunnable = new Runnable() {
        @Override
        public void run() {
            synchronized (sAllNotificationBackgrounds) {
                for (View bgView : sAllNotificationBackgrounds) {
                    View row = (View) bgView.getTag(TAG_KEY_ROW);
                    if (row != null && isRowHun(row)) {
                        continue;
                    }
                    cleanupBlur(bgView);
                    if (bgView != null) {
                        try {
                            bgView.invalidate();
                        } catch (Throwable ignored) {}
                    }
                }
            }
            if (sNsslView != null) {
                try {
                    sNsslView.invalidate();
                } catch (Throwable ignored) {}
            }
        }
    };

    private static void clearAllNotificationBlurs() {
        // 1. Synchronously on current thread: immediately set blur radius to 0 and hide drawable (zero latency)
        synchronized (sAllNotificationBackgrounds) {
            for (View bgView : sAllNotificationBackgrounds) {
                View row = (View) bgView.getTag(TAG_KEY_ROW);
                if (row != null && isRowHun(row)) {
                    continue;
                }
                cleanupBlur(bgView);
            }
        }

        // 2. Schedule UI thread execution to invalidate views and force ViewRootImpl Aggregator to commit to SurfaceFlinger
        if (Looper.myLooper() == Looper.getMainLooper()) {
            sClearAllNotificationBlursRunnable.run();
        } else {
            Handler h = getMainHandler();
            if (h != null) {
                h.removeCallbacks(sClearAllNotificationBlursRunnable);
                h.postAtFrontOfQueue(sClearAllNotificationBlursRunnable);
            } else if (sNsslView != null) {
                sNsslView.removeCallbacks(sClearAllNotificationBlursRunnable);
                sNsslView.post(sClearAllNotificationBlursRunnable);
            }
        }
    }

    private static void handleGoingToSleep() {
        sScreenAwake = false;
        sNotificationBlursCleared = true;
        sFractionToShade = 0f;
        sLsToShadeProgress = 0f;
        Handler h = getMainHandler();
        if (h != null) {
            h.removeCallbacks(sWakeInvalidateRunnable);
        }
        clearAllNotificationBlurs();
    }

    private static final Runnable sWakeInvalidateRunnable = new Runnable() {
        @Override
        public void run() {
            if (sSysUiState != null) {
                try {
                    long flags = (sGetFlagsMethod != null) ? (Long) sGetFlagsMethod.invoke(sSysUiState) : (Long) XposedHelpers.callMethod(sSysUiState, "getFlags");
                    if ((flags & 8L) != 0) {
                        sBouncerShowing = true;
                        sBouncerFullyShown = true;
                        clearAllNotificationBlurs();
                        return;
                    }
                } catch (Throwable ignored) {}
            }
            if (sScreenAwake && !sBouncerShowing && sStatusBarState == 1) {
                sKeyguardAlpha = 1.0f;
                if (sNsslView != null) {
                    sNsslView.setAlpha(1.0f);
                }
                invalidateNotificationStack();
            }
        }
    };

    private static void handleWakingUp() {
        sScreenAwake = true;
        sNotificationBlursCleared = false;
        sFractionToShade = 0f;
        sLsToShadeProgress = 0f;

        if (sSysUiState != null) {
            try {
                long flags = (sGetFlagsMethod != null) ? (Long) sGetFlagsMethod.invoke(sSysUiState) : (Long) XposedHelpers.callMethod(sSysUiState, "getFlags");
                sBouncerShowing = ((flags & 8L) != 0);
            } catch (Throwable ignored) {}
        }

        if (sBouncerShowing) {
            sBouncerFullyShown = true;
            clearAllNotificationBlurs();
            return;
        }

        sKeyguardAlpha = 1.0f;
        if (sNsslView != null) {
            sNsslView.setAlpha(1.0f);
        }
        invalidateNotificationStack();

        Handler h = getMainHandler();
        if (h != null) {
            h.removeCallbacks(sWakeInvalidateRunnable);
        }
    }

    private static int getFrostedBackdropColor(Context ctx, boolean isDark) {
        if (ctx == null) return isDark ? 0x44000000 : 0x38FFFFFF;

        int currentUiMode = ctx.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        if (currentUiMode != sLastColorCacheUiMode) {
            sCachedFrostedColorDark = 0;
            sCachedFrostedColorLight = 0;
            sLastColorCacheUiMode = currentUiMode;
        }

        if (isDark && sCachedFrostedColorDark != 0) {
            return sCachedFrostedColorDark;
        } else if (!isDark && sCachedFrostedColorLight != 0) {
            return sCachedFrostedColorLight;
        }

        int color = 0;
        if (sLucentColorUtilsInstance != null && sGetLucentColorMethod != null) {
            try {
                color = (Integer) sGetLucentColorMethod.invoke(sLucentColorUtilsInstance, ctx, "container_frosted_backdrop");
            } catch (Throwable ignored) {}
        }
        if (color == 0 || (color >>> 24) > 0x80) {
            color = isDark ? 0x44000000 : 0x38FFFFFF;
        }

        if (isDark) {
            sCachedFrostedColorDark = color;
        } else {
            sCachedFrostedColorLight = color;
        }
        return color;
    }

    private static int resolveStockNotifColor(Context ctx, boolean isDark) {
        if (ctx != null) {
            try {
                int resId = ctx.getResources().getIdentifier("notification_background_color", "color", "com.android.systemui");
                if (resId != 0) {
                    return ctx.getColor(resId);
                }
            } catch (Throwable ignored) {}
            try {
                int resId = isDark ? 0x01060027 : 0x0106001e;
                return ctx.getColor(resId);
            } catch (Throwable ignored) {}
        }
        return isDark ? 0xFF1C1B1B : 0xFFF3F4F1;
    }

    private static int resolveStockNotifColor(View bgView) {
        if (bgView == null) return 0xFF1C1B1B;
        Context ctx = bgView.getContext();
        int uiMode = (ctx != null) ? (ctx.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) : Configuration.UI_MODE_NIGHT_YES;
        boolean isDark = (uiMode == Configuration.UI_MODE_NIGHT_YES);
        return resolveStockNotifColor(ctx, isDark);
    }

    private static void cleanupBlur(View bgView) {
        if (bgView == null) return;
        synchronized (sHunPreDrawListeners) {
            ViewTreeObserver.OnPreDrawListener l = sHunPreDrawListeners.remove(bgView);
            if (l != null) {
                try {
                    ViewTreeObserver vto = bgView.getViewTreeObserver();
                    if (vto != null && vto.isAlive()) {
                        vto.removeOnPreDrawListener(l);
                    }
                } catch (Throwable ignored) {}
            }
        }
        try {
            Object bd = bgView.getTag(TAG_KEY_BLUR_DRAWABLE);
            if (bd == null && sBackgroundBlurDrawableField != null) {
                try { bd = sBackgroundBlurDrawableField.get(bgView); } catch (Throwable ignored) {}
            }
            if (bd == null) {
                try { bd = XposedHelpers.getObjectField(bgView, "mBackgroundBlurDrawable"); } catch (Throwable ignored) {}
            }
            if (bd instanceof Drawable) {
                Drawable d = (Drawable) bd;
                d.setAlpha(0);
                d.setVisible(false, false);
            }
        } catch (Throwable ignored) {}
        bgView.setTag(TAG_KEY_BLUR_ACTIVE, Boolean.FALSE);
    }

    private static Canvas getNativeCanvas(Object composeCanvas) {
        if (composeCanvas == null) return null;
        if (sComposeGetInternalCanvasMethod != null) {
            try {
                return (Canvas) sComposeGetInternalCanvasMethod.invoke(composeCanvas);
            } catch (Throwable ignored) {}
        }
        if (sComposeInternalCanvasField != null) {
            try {
                return (Canvas) sComposeInternalCanvasField.get(composeCanvas);
            } catch (Throwable ignored) {}
        }
        if (!sComposeCanvasLookupDone) {
            sComposeCanvasLookupDone = true;
            try {
                Method m = composeCanvas.getClass().getMethod("getInternalCanvas");
                m.setAccessible(true);
                sComposeGetInternalCanvasMethod = m;
                return (Canvas) m.invoke(composeCanvas);
            } catch (Throwable ignored) {}
            try {
                Field f = composeCanvas.getClass().getDeclaredField("_internalCanvas");
                f.setAccessible(true);
                sComposeInternalCanvasField = f;
                return (Canvas) f.get(composeCanvas);
            } catch (Throwable ignored) {}
            try {
                Field f = composeCanvas.getClass().getDeclaredField("internalCanvas");
                f.setAccessible(true);
                sComposeInternalCanvasField = f;
                return (Canvas) f.get(composeCanvas);
            } catch (Throwable ignored) {}
            for (Field f : composeCanvas.getClass().getDeclaredFields()) {
                if (Canvas.class.isAssignableFrom(f.getType())) {
                    try {
                        f.setAccessible(true);
                        sComposeInternalCanvasField = f;
                        return (Canvas) f.get(composeCanvas);
                    } catch (Throwable ignored) {}
                }
            }
        }
        return null;
    }
}
