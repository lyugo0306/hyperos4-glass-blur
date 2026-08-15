package dev.codex.os4glassblur;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;

import java.lang.reflect.Method;
import java.util.ArrayList;

import io.github.libxposed.api.XposedModule;

/**
 * OS4 Glass Mode - unified low blur (release build).
 *
 * Control center + notification center unified glass material:
 *  - card / row / container / modal glass blur radius: forced 40/40
 *  - window-level glass blur animation: forced 40/40 (both pull gestures)
 *  - whole-panel background blur: 50% of stock (ShadeBlurProvider)
 *  - glass material deltas: brightness -0.05, darker -0.08, IOR +0.20,
 *    burn -0.25 (floored at 0), applied to control-center cards and every
 *    notification-path glass array (stock arrays are replaced with the
 *    absolute tuned recipe, idempotent)
 *
 * Notification rows use the SYSTEM's own GLASS pipeline
 * (NotificationRowGlassEffect.apply -> RowBlurEffect + RowGlassEffect:
 * blur mode, blend colors, round outline, material type), invoked by the
 * module because the global material style is BLUR, not GLASS:
 *  - setMiViewMaterialType pinned to 1 (glass) for row backgrounds
 *  - glass-outline enhance flag (8192) pinned on for rows
 *  - setMiGlassSdfMaxSize clamped to the visible content height (the
 *    system only shrinks the SDF under GLASS style; the last row's
 *    background view is stretched to the bottom of the shade)
 *
 * v38: release build - diagnostic logging removed.
 */
public final class MainHook extends XposedModule {
    private static final String TAG = "OS4GlassBlur";
    private static final int GLASS_RADIUS = 40;
    private static final int PANEL_BLUR_PERCENT = 50;

    private boolean hooksInstalled;
    private boolean glassLogged;
    private boolean panelLogged;
    private boolean controlCenterMaterialLogged;
    private boolean notificationMaterialLogged;
    private boolean forceLogged;

    private Method viewSetMiGlass;
    private Method viewSetMaterialType;
    private Method viewSetGlassRadius;
    private Method viewSetSdfMaxSize;
    private Method viewSetBlurEnhanceFlag;
    private float[] notificationRecipe;
    private Object rowGlassEffectInstance;
    private Method rowGlassEffectApply;
    private final ThreadLocal<Boolean> forcingState = new ThreadLocal<Boolean>();
    private final ThreadLocal<Boolean> applyingGlass = new ThreadLocal<Boolean>();
    private final java.util.Set<String> clipHooksInstalled =
            java.util.concurrent.ConcurrentHashMap.<String>newKeySet();

    private final View.OnLayoutChangeListener layoutListener = new View.OnLayoutChangeListener() {
        @Override
        public void onLayoutChange(View v, int l, int t, int r, int b,
                int ol, int ot, int or, int ob) {
            try {
                if (!v.isAttachedToWindow()) {
                    return;
                }
                // re-run the system's own GLASS pipeline while the row
                // animates (expand, collapse, add/remove); the SDF hook keeps
                // the glass layer at the content height
                if (!Boolean.TRUE.equals(forcingState.get())) {
                    applySystemRowGlass(v, "layout");
                }
            } catch (Throwable ignored) {
            }
        }
    };

    @Override
    public void onModuleLoaded(ModuleLoadedParam param) {
        Log.i(TAG, "module loaded in " + param.getProcessName());
        installHooks();
    }

    private static String viewClass(Object v) {
        return v == null ? "null" : v.getClass().getName();
    }

    private void installHooks() {
        if (hooksInstalled) {
            return;
        }
        try {
            viewSetMiGlass = android.view.View.class.getMethod("setMiGlass", float[].class);
            viewSetMaterialType = android.view.View.class.getMethod("setMiViewMaterialType", int.class);
            viewSetGlassRadius = android.view.View.class.getMethod(
                    "setMiGlassBlurRadius", int.class, int.class);
            try {
                viewSetSdfMaxSize = android.view.View.class.getMethod(
                        "setMiGlassSdfMaxSize", float.class, float.class);
            } catch (Throwable t) {
                Log.i(TAG, "setMiGlassSdfMaxSize unavailable: " + t);
            }
            try {
                viewSetBlurEnhanceFlag = android.view.View.class.getMethod(
                        "setMiBackgroundBlurEnhanceFlag", int.class, int.class);
            } catch (Throwable t) {
                Log.i(TAG, "setMiBackgroundBlurEnhanceFlag unavailable: " + t);
            }
            notificationRecipe = buildNotificationRecipe();

            // ---------- force glass on notification row backgrounds ----------
            try {
                final Method onAttach = android.view.View.class.getDeclaredMethod(
                        "onAttachedToWindow");
                onAttach.setAccessible(true);
                hook(onAttach).intercept(chain -> {
                    try {
                        Object target = chain.getThisObject();
                        if (!Boolean.TRUE.equals(forcingState.get())
                                && isRowBackground(target)) {
                            forceGlass(target, "attach");
                        }
                    } catch (Throwable ignored) {
                    }
                    return chain.proceed();
                });
            } catch (Throwable ignored) {
            }

            try {
                final Method setBg = android.view.View.class.getMethod(
                        "setBackground", Drawable.class);
                hook(setBg).intercept(chain -> {
                    try {
                        Object target = chain.getThisObject();
                        if (!Boolean.TRUE.equals(forcingState.get())
                                && isRowBackground(target)) {
                            forceGlass(target, "setbg");
                        }
                    } catch (Throwable ignored) {
                    }
                    return chain.proceed();
                });
            } catch (Throwable ignored) {
            }

            // ---------- setMiGlass: tune control-center + notification glass ----------
            hook(viewSetMiGlass).intercept(chain -> {
                try {
                    if (!Boolean.TRUE.equals(forcingState.get())) {
                        boolean cc = isControlCenterCall();
                        boolean notif = isNotificationCenterCall();
                        float[] original = (float[]) chain.getArg(0);
                        if (original != null && original.length >= 36) {
                            if (notif && isNotificationRecipe(original)) {
                                return chain.proceed(); // already our recipe
                            }
                            if (isStockNormalArray(original) || cc || notif) {
                                float[] adjusted = original.clone();
                                applySharedMaterialDelta(adjusted);
                                boolean shouldLogMat = cc ? !controlCenterMaterialLogged
                                        : !notificationMaterialLogged;
                                if (shouldLogMat) {
                                    if (cc) {
                                        controlCenterMaterialLogged = true;
                                    } else {
                                        notificationMaterialLogged = true;
                                    }
                                    Log.i(TAG, (cc ? "CC" : "notif")
                                            + " material: brightness " + original[6] + " -> "
                                            + adjusted[6] + ", darker " + original[7] + " -> "
                                            + adjusted[7] + ", IOR " + original[32] + " -> "
                                            + adjusted[32] + ", burn " + original[35] + " -> "
                                            + adjusted[35]);
                                }
                                return chain.proceed(new Object[] { adjusted });
                            }
                        }
                    }
                } catch (Throwable ignored) {
                    // never break the original call
                }
                return chain.proceed();
            });

            // ---------- glass blur radius: unify every layer at 40/40 ----------
            hook(viewSetGlassRadius).intercept(chain -> {
                Object target = chain.getThisObject();
                String cls = viewClass(target);
                if (isControlCenterCall()) {
                    if (!glassLogged) {
                        glassLogged = true;
                        Log.i(TAG, "glass-card radius " + chain.getArg(0) + "/"
                                + chain.getArg(1) + " -> " + GLASS_RADIUS + "/" + GLASS_RADIUS
                                + " view=" + cls);
                    }
                    return chain.proceed(new Object[] { GLASS_RADIUS, GLASS_RADIUS });
                }
                if (stackContains("NotificationUtil", "applyContainerViewBlur")
                        || stackContains("BlurUtilsImpl", "applyModalContainerBlur")
                        || stackContains("ShadeBlendBlurController", "applyGlassBlurBlurRatio")) {
                    // notification container / long-press modal / window animation
                    return chain.proceed(new Object[] { GLASS_RADIUS, GLASS_RADIUS });
                }
                return chain.proceed();
            });

            // ---------- keep rows in GLASS material + glass-outline ----------
            // The system's global material style is BLUR (material_style=1):
            // NotificationRowBlurEffect.apply() calls setMiViewMaterialType(0)
            // and ensureOutlineProvider() clears the glass-outline flag
            // (setGlassOutlineEnable(false) -> flag 8192 off, 4096 on). Both
            // would undo our forced glass on the row. Intercept and pin the
            // row to glass material with the glass-outline flag always on.
            hook(viewSetMaterialType).intercept(chain -> {
                try {
                    Object target = chain.getThisObject();
                    if (!Boolean.TRUE.equals(forcingState.get())
                            && isRowBackground(target)) {
                        int arg = ((Number) chain.getArg(0)).intValue();
                        if (arg != 1) {
                            // NOTE: no refreshRowGlass here - the caller is
                            // already inside the system effect pipeline
                            // (RowBlurEffect -> ... -> this hook); refreshing
                            // re-enters applySystemRowGlass and recurses
                            // infinitely (RowBlurEffect -> pin hook -> ...).
                            return chain.proceed(new Object[] { 1 });
                        }
                    }
                } catch (Throwable ignored) {
                }
                return chain.proceed();
            });

            if (viewSetBlurEnhanceFlag != null) {
                hook(viewSetBlurEnhanceFlag).intercept(chain -> {
                    try {
                        Object target = chain.getThisObject();
                        if (!Boolean.TRUE.equals(forcingState.get())
                                && isRowBackground(target)) {
                            int flags = ((Number) chain.getArg(0)).intValue();
                            int mask = ((Number) chain.getArg(1)).intValue();
                            // never let the system clear the glass-outline flag
                            if ((flags & 8192) != 8192 || (mask & 8192) != 8192) {
                                return chain.proceed(new Object[] {
                                        flags | 8192, mask | 8192 });
                            }
                        }
                    } catch (Throwable ignored) {
                    }
                    return chain.proceed();
                });
            }

            // ---------- background blur radius: panel 50%, modal 40 ----------
            try {
                final Method setMiBackgroundBlurRadius = android.view.View.class.getMethod(
                        "setMiBackgroundBlurRadius", int.class);
                hook(setMiBackgroundBlurRadius).intercept(chain -> {
                    if (isShadeBlurProviderCall()) {
                        int original = ((Number) chain.getArg(0)).intValue();
                        int adjusted = Math.round(original * (PANEL_BLUR_PERCENT / 100.0f));
                        if (!panelLogged && original > 0) {
                            panelLogged = true;
                            Log.i(TAG, "whole-panel blur " + original + " -> " + adjusted
                                    + " (" + PANEL_BLUR_PERCENT + "%)");
                        }
                        return chain.proceed(new Object[] { adjusted });
                    }
                    if (stackContains("BlurUtilsImpl", "applyModalContainerBlur")) {
                        return chain.proceed(new Object[] { GLASS_RADIUS });
                    }
                    return chain.proceed();
                });
            } catch (Throwable ignored) {
            }

            // ---------- SDF size: keep glass layer at content height ----------
            // Under global style != GLASS the system never shrinks the SDF
            // layer (NotificationBackgroundViewInjectorImpl.updateActualHeight
            // only does it for GLASS). The last row's background view is
            // stretched to the bottom of the shade, so its full-height SDF
            // would draw glass below the content. Clamp every SDF write on
            // row backgrounds to the visible content height.
            if (viewSetSdfMaxSize != null) {
                hook(viewSetSdfMaxSize).intercept(chain -> {
                    try {
                        Object target = chain.getThisObject();
                        if (!Boolean.TRUE.equals(forcingState.get())
                                && isRowBackground(target)) {
                            int visibleH = getVisibleHeight((View) target);
                            float wantH = ((Number) chain.getArg(1)).floatValue();
                            if (visibleH > 0 && wantH > visibleH) {
                                return chain.proceed(new Object[] {
                                        chain.getArg(0), (float) visibleH });
                            }
                        }
                    } catch (Throwable ignored) {
                    }
                    return chain.proceed();
                });
            }

            hooksInstalled = true;
            Log.i(TAG, "v38 release hooks installed (unified glass 40 / system row glass)");
        } catch (Throwable error) {
            Log.e(TAG, "failed to install hooks", error);
        }
    }

    // ------------------------------------------------------------------
    // Force glass onto notification row background views
    // ------------------------------------------------------------------

    private boolean isRowBackground(Object v) {
        if (!(v instanceof View)) {
            return false;
        }
        View view = (View) v;
        String cls = view.getClass().getName();
        if (!cls.contains("NotificationBackground")) {
            return false;
        }
        if (view.getId() != 0) {
            try {
                String resName = view.getResources().getResourceEntryName(view.getId());
                if (!"backgroundNormal".equals(resName)
                        && !"backgroundDimmed".equals(resName)) {
                    return false;
                }
            } catch (Throwable ignored) {
                // id not in resources; still treat as row background by class name
            }
        }
        return true;
    }

    private void forceGlass(Object view, String via) {
        try {
            final View v = (View) view;
            applySystemRowGlass(v, via);
            if (viewSetGlassRadius != null && isRowBackground(v)) {
                viewSetGlassRadius.invoke(v, GLASS_RADIUS, GLASS_RADIUS);
            }
            // layout may be pending at attach time; re-apply after layout
            v.post(() -> {
                try {
                    if (!v.isAttachedToWindow()) {
                        return;
                    }
                    applySystemRowGlass(v, "post");
                    if (viewSetGlassRadius != null && isRowBackground(v)) {
                        viewSetGlassRadius.invoke(v, GLASS_RADIUS, GLASS_RADIUS);
                    }
                } catch (Throwable ignored) {
                }
            });
            // keep syncing while the row animates; addOnLayoutChangeListener is
            // idempotent for the same listener instance (contains check), so
            // rows that get recycled keep the sync without piling up listeners
            v.addOnLayoutChangeListener(layoutListener);
            lazyInstallClipHooks(v);
            if (!forceLogged) {
                forceLogged = true;
                Log.i(TAG, "row glass applied via system RowGlassEffect on "
                        + viewClass(v) + " via " + via);
            }
        } catch (Throwable t) {
            Log.e(TAG, "force-glass failed", t);
        }
    }

    /**
     * Reuse the system's OWN notification GLASS pipeline instead of writing
     * our own material logic:
     *
     *   NotificationRowGlassEffect.apply(row, context)
     *     -> NotificationRowBlurEffect.apply   (blur mode, blend colors,
     *                                            round outline via setRoundRect,
     *                                            transparent custom background)
     *     -> MiGlassCompat.setMiGlassCompat    (glass params array)
     *     -> MiGlassCompat.setMiViewMaterialTypeCompat(1) (SDF size + type)
     *
     * The system only runs this pipeline when the global material style is
     * GLASS; we invoke it for every notification row so all the system logic
     * (blend, outline, shadows, SDF-on-layout) applies, with zero custom
     * rendering code. The only thing the system would additionally do under
     * GLASS (shrinking the SDF to the content height in updateActualHeight)
     * is compensated by the setMiGlassSdfMaxSize hook, because the global
     * style is not GLASS here.
     */
    private void applySystemRowGlass(View bgView, String via) {
        if (Boolean.TRUE.equals(applyingGlass.get())) {
            return; // re-entrancy guard: we are already inside the pipeline
        }
        applyingGlass.set(Boolean.TRUE);
        try {
            Object row = findRow(bgView);
            if (row == null) {
                return;
            }
            Class<?> effectCls = row.getClass().getClassLoader().loadClass(
                    "com.android.systemui.statusbar.notification.style"
                            + ".vieweffect.NotificationRowGlassEffect");
            if (rowGlassEffectInstance == null) {
                rowGlassEffectInstance = effectCls.getField("INSTANCE").get(null);
                rowGlassEffectApply = effectCls.getMethod(
                        "apply", Object.class, Context.class);
            }
            rowGlassEffectApply.invoke(rowGlassEffectInstance, row, bgView.getContext());
        } catch (Throwable t) {
            Log.e(TAG, "applySystemRowGlass failed", t);
        } finally {
            applyingGlass.remove();
        }
    }

    private Object findRow(View v) {
        android.view.ViewParent p = v.getParent();
        while (p instanceof View) {
            if (p.getClass().getName().contains("ExpandableNotificationRow")) {
                return p;
            }
            p = p.getParent();
        }
        return null;
    }

    /**
     * Visible height of a notification row background: the actual row height
     * minus the bottom clip amount (fold-stacked rows clip their bottom).
     * Mirrors the system outline formula in
     * NotificationBackgroundViewInjectorImpl$outlineProvider$1.
     * Reflection is based on the runtime class of v (host class loader), so
     * it works even though the module class loader cannot see SystemUI
     * classes at compile time.
     */
    private int getVisibleHeight(View v) {
        try {
            Class<?> cls = v.getClass();
            if (!cls.getName().contains("NotificationBackgroundView")) {
                return v.getHeight();
            }
            // The last row's NotificationBackgroundView is stretched to the
            // bottom of the shade. Its sibling `expanded` view still carries
            // the real content height; use that as the SDF/outline boundary.
            int actual = getContentHeightCompat(v);
            if (actual <= 0) {
                actual = ((Number) cls.getMethod("getActualHeight").invoke(v)).intValue();
            }
            Object injector = cls.getField("mNotificationBackgroundViewInjector").get(v);
            int clipBottom = ((Number) injector.getClass()
                    .getField("clipBottomAmount").get(injector)).intValue();
            int extClipBottom = ((Number) injector.getClass()
                    .getField("extClipBottomAmount").get(injector)).intValue();
            return Math.max(0, actual - Math.max(extClipBottom, clipBottom));
        } catch (Throwable t) {
            return v.getHeight();
        }
    }

    /**
     * Return the real notification content height rather than the stretched
     * background container height. In the SystemUI row hierarchy the sibling
     * with resource name `expanded` bounds the visible notification content.
     */
    private int getContentHeightCompat(View v) {
        try {
            ViewGroup parent = v.getParent() instanceof ViewGroup
                    ? (ViewGroup) v.getParent() : null;
            if (parent == null) {
                return 0;
            }
            View expanded = findChildByResourceName(parent, "expanded", 2);
            if (expanded != null && expanded.getHeight() > 0) {
                return expanded.getHeight();
            }
        } catch (Throwable ignored) {
        }
        return 0;
    }

    private View findChildByResourceName(ViewGroup root, String wanted, int depth) {
        if (depth < 0) {
            return null;
        }
        for (int i = 0; i < root.getChildCount(); i++) {
            View child = root.getChildAt(i);
            try {
                if (child.getId() != View.NO_ID
                        && wanted.equals(child.getResources().getResourceEntryName(child.getId()))) {
                    return child;
                }
            } catch (Throwable ignored) {
            }
            if (depth > 0 && child instanceof ViewGroup) {
                View found = findChildByResourceName((ViewGroup) child, wanted, depth - 1);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    /**
     * Hook the row's clip setters so the glass SDF layer follows the bottom
     * clip amount even when the system does not update it (global material
     * style != GLASS). Installed lazily on the concrete class the first time
     * we force glass on a row, then reused for every subsequent row.
     */
    private void lazyInstallClipHooks(final View v) {
        try {
            final Class<?> cls = v.getClass();
            if (!cls.getName().contains("NotificationBackgroundView")) {
                return;
            }
            if (!clipHooksInstalled.add(cls.getName())) {
                return;
            }
            Log.i(TAG, "installing clip hooks on " + cls.getName());
            try {
                hook(cls.getDeclaredMethod("setClipBottomAmount", int.class))
                        .intercept(chain -> {
                            try {
                                Object result = chain.proceed();
                                refreshRowGlass(chain.getThisObject());
                                return result;
                            } catch (Throwable t) {
                                Log.e(TAG, "setClipBottomAmount hook failed", t);
                                return chain.proceed();
                            }
                        });
            } catch (Throwable t) {
                Log.e(TAG, "cannot hook setClipBottomAmount", t);
            }
            try {
                hook(cls.getDeclaredMethod("setActualHeight", int.class))
                        .intercept(chain -> {
                            try {
                                Object result = chain.proceed();
                                refreshRowGlass(chain.getThisObject());
                                return result;
                            } catch (Throwable t) {
                                Log.e(TAG, "setActualHeight hook failed", t);
                                return chain.proceed();
                            }
                        });
            } catch (Throwable t) {
                Log.e(TAG, "cannot hook setActualHeight", t);
            }
        } catch (Throwable t) {
            Log.e(TAG, "lazyInstallClipHooks failed", t);
        }
    }

    private void refreshRowGlass(Object target) {
        try {
            if (!(target instanceof View)) {
                return;
            }
            final View v = (View) target;
            if (!Boolean.TRUE.equals(forcingState.get()) && v.isAttachedToWindow()) {
                applySystemRowGlass(v, "clip");
            }
        } catch (Throwable ignored) {
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private boolean isControlCenterCall() {
        for (StackTraceElement frame : Thread.currentThread().getStackTrace()) {
            if (frame.getClassName().startsWith("miui.systemui.controlcenter.")) {
                return true;
            }
        }
        return false;
    }

    private boolean isShadeBlurProviderCall() {
        for (StackTraceElement frame : Thread.currentThread().getStackTrace()) {
            if (frame.getClassName().startsWith(
                    "com.miui.systemui.shade.blur.ShadeBlendBlurController$BlurProvider")) {
                return true;
            }
        }
        return false;
    }

    private boolean isNotificationCenterCall() {
        for (StackTraceElement frame : Thread.currentThread().getStackTrace()) {
            if (frame.getClassName().startsWith(
                    "com.android.systemui.statusbar.notification.")) {
                return true;
            }
        }
        return false;
    }

    private static boolean stackContains(String classNamePart, String methodName) {
        StackTraceElement[] st = Thread.currentThread().getStackTrace();
        for (StackTraceElement f : st) {
            if (f.getClassName().contains(classNamePart)
                    && f.getMethodName().contains(methodName)) {
                return true;
            }
        }
        return false;
    }

    private static void applySharedMaterialDelta(float[] params) {
        params[32] += 0.20f;                            // a touch more refraction
        params[7] = Math.max(0.0f, params[7] - 0.08f);  // less darkening
        params[35] = Math.max(0.0f, params[35] - 0.25f);// less burn
        params[6] -= 0.05f;                             // slightly dimmer card
    }

    /** Stock normal-notification glass array (R.array.notification_glass_params_normal). */
    private static boolean isStockNormalArray(float[] p) {
        return Math.abs(p[6] - (-0.02f)) < 0.01f
                && Math.abs(p[7] - 0.30f) < 0.01f
                && Math.abs(p[32] - 4.0f) < 0.05f
                && Math.abs(p[35] - 0.0f) < 0.01f;
    }

    /** Absolute tuned recipe: stock normal-notification array + shared delta. */
    private static float[] buildNotificationRecipe() {
        float[] params = new float[] {
                0.67f, 0.16f, 0.09f, 0.0f, 0.24f, 1.4f, -0.02f, 0.30f,
                0.6f, 1.0f, 0.03f, 1.0f, 1.0f, 1.0f, 0.10f, 0.2f,
                0.3f, 1.0f, 1.0f, 72.0f, 3.8f, 80.0f, 800.0f, 1.2f,
                1.0f, -0.4f, 0.6f, -0.8f, 1.4f, 0.7f, 0.8f, 1.15f,
                4.0f, 2.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f,
                0.0f, 0.0f
        };
        applySharedMaterialDelta(params);
        return params;
    }

    private static boolean isNotificationRecipe(float[] p) {
        return Math.abs(p[6] - (-0.07f)) < 0.001f
                && Math.abs(p[7] - 0.22f) < 0.001f
                && Math.abs(p[32] - 4.20f) < 0.001f
                && Math.abs(p[35] - 0.00f) < 0.001f;
    }
}
