package com.kfjie.chatgptnetworkbanner;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;

public final class ChatGptNetworkBannerModule extends XposedModule {
    private static final String TAG = "ChatGptNetworkBanner";
    private static final String TARGET_PACKAGE = "com.openai.chatgpt";
    private static final String MAIN_ACTIVITY = "com.openai.chatgpt.MainActivity";

    // Certificate-state signatures for supported builds.
    private static final TargetSignature[] TARGETS = {
            new TargetSignature(
                    "ChatGPT 1.2026.279-1.2026.293",
                    "h00",
                    "txq0",
                    "jle0",
                    "kle0",
                    "h",
                    "a011",
                    "a"
            ),
            new TargetSignature(
                    "ChatGPT 1.2026.293 namespaced",
                    "p000.h00",
                    "p000.txq0",
                    "p000.jle0",
                    "p000.kle0",
                    "h",
                    "p000.a011",
                    "a"
            )
    };

    private final AtomicBoolean uiHookInstalled = new AtomicBoolean();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private volatile boolean pendingCertificateNotice;
    private volatile Activity bannerActivity;
    private volatile BannerController bannerController;

    @Override
    public void onPackageReady(XposedModuleInterface.PackageReadyParam param) {
        if (!TARGET_PACKAGE.equals(param.getPackageName())) {
            return;
        }
        try {
            installUiHook(param.getClassLoader());
            installMainActivityHook(param.getClassLoader());
        } catch (Throwable throwable) {
            log(Log.ERROR, TAG, "module initialization failed", throwable);
        }
    }

    private void installMainActivityHook(ClassLoader classLoader)
            throws ReflectiveOperationException {
        Method onCreate = Class.forName(MAIN_ACTIVITY, false, classLoader)
                .getDeclaredMethod("onCreate", Bundle.class);
        onCreate.setAccessible(true);
        hook(onCreate)
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(chain -> {
                    Object result = chain.proceed();
                    Object activity = chain.getThisObject();
                    if (activity instanceof Activity) {
                        Activity current = (Activity) activity;
                        createBannerController(current);
                    }
                    return result;
                });
    }

    private void installUiHook(ClassLoader classLoader) {
        if (!uiHookInstalled.compareAndSet(false, true)) {
            return;
        }
        TargetBinding binding = findTarget(classLoader);
        if (binding == null) {
            log(Log.WARN, TAG, "no verified target signature; leaving UI untouched");
            return;
        }
        try {
            hookUiProjection(binding);
            if (BuildConfig.DEBUG) {
                log(Log.INFO, TAG, "UI projection hook installed for "
                        + binding.signature.label);
            }
        } catch (Throwable throwable) {
            log(Log.ERROR, TAG,
                    "verified signature could not be installed; original UI remains active",
                    throwable);
        }
    }

    private TargetBinding findTarget(ClassLoader classLoader) {
        for (TargetSignature signature : TARGETS) {
            try {
                TargetBinding binding = TargetBinding.inspect(classLoader, signature);
                if (binding != null) {
                    return binding;
                }
            } catch (Throwable throwable) {
                log(Log.WARN, TAG, "target inspection failed", throwable);
                // Obfuscated names may be reused with another structure. No hook
                // is installed unless every shape check succeeds.
            }
        }
        return null;
    }

    private void hookUiProjection(TargetBinding binding)
            throws ReflectiveOperationException {
        hook(binding.emit)
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(chain -> {
                    if (binding.isCertificateFailure(chain.getArg(0))) {
                        if (BuildConfig.DEBUG) {
                            log(Log.INFO, TAG, "certificate UI projection vetoed: "
                                    + binding.signature.label
                                    + " state=" + chain.getArg(0).getClass().getName());
                        }
                        pendingCertificateNotice = true;
                        BannerController controller = ensureBannerController();
                        if (BuildConfig.DEBUG) {
                            log(Log.INFO, TAG, "certificate banner request: controller="
                                    + (controller != null));
                        }
                        if (controller != null) {
                            pendingCertificateNotice = false;
                            controller.showCertificateNoticeIfChat();
                        }
                        return binding.completedUnit;
                    }
                    return chain.proceed();
                });
    }

    private void createBannerController(Activity activity) {
        if (bannerController != null && bannerActivity == activity) {
            return;
        }
        BannerController previous = bannerController;
        if (previous != null) {
            previous.destroy();
        }
        BannerController controller = new BannerController(activity);
        controller.setOnDestroyed(() -> {
            if (bannerController == controller) {
                bannerController = null;
                bannerActivity = null;
            }
        });
        bannerActivity = activity;
        bannerController = controller;
        if (pendingCertificateNotice) {
            pendingCertificateNotice = false;
            controller.showCertificateNoticeIfChat();
        }
        if (BuildConfig.DEBUG) {
            DebugTrigger.install(activity, controller::showDebugPreview);
        }
    }

    private BannerController ensureBannerController() {
        BannerController current = bannerController;
        if (current != null) {
            return current;
        }
        try {
            Class<?> activityThread = Class.forName("android.app.ActivityThread");
            Method currentThread = activityThread.getDeclaredMethod(
                    "currentActivityThread"
            );
            currentThread.setAccessible(true);
            Object thread = currentThread.invoke(null);
            Method currentActivity = activityThread.getDeclaredMethod(
                    "currentActivity"
            );
            currentActivity.setAccessible(true);
            Object value = currentActivity.invoke(thread);
            if (!(value instanceof Activity)) {
                return null;
            }
            Activity activity = (Activity) value;
            if (Looper.myLooper() == Looper.getMainLooper()) {
                createBannerController(activity);
            } else {
                mainHandler.post(() -> createBannerController(activity));
            }
            return bannerController;
        } catch (Throwable throwable) {
            log(Log.WARN, TAG, "current activity unavailable", throwable);
            return null;
        }
    }

    private static final class TargetSignature {
        final String label;
        final String emitter;
        final String state;
        final String certificateFailure;
        final String failureBase;
        final String failureField;
        final String unit;
        final String unitField;

        TargetSignature(
                String label,
                String emitter,
                String state,
                String certificateFailure,
                String failureBase,
                String failureField,
                String unit,
                String unitField
        ) {
            this.label = label;
            this.emitter = emitter;
            this.state = state;
            this.certificateFailure = certificateFailure;
            this.failureBase = failureBase;
            this.failureField = failureField;
            this.unit = unit;
            this.unitField = unitField;
        }
    }

    private static final class TargetBinding {
        final TargetSignature signature;
        final Class<?> stateClass;
        final Class<?> certificateFailureClass;
        final Class<?> failureBaseClass;
        final Field failureField;
        final Method emit;
        final Object completedUnit;

        private TargetBinding(
                TargetSignature signature,
                Class<?> stateClass,
                Class<?> certificateFailureClass,
                Class<?> failureBaseClass,
                Field failureField,
                Method emit,
                Object completedUnit
        ) {
            this.signature = signature;
            this.stateClass = stateClass;
            this.certificateFailureClass = certificateFailureClass;
            this.failureBaseClass = failureBaseClass;
            this.failureField = failureField;
            this.emit = emit;
            this.completedUnit = completedUnit;
        }

        static TargetBinding inspect(ClassLoader classLoader, TargetSignature signature)
                throws ReflectiveOperationException {
            Class<?> state = Class.forName(signature.state, false, classLoader);
            Field failureField = findField(state, signature.failureField);
            Class<?> certificateFailure =
                    Class.forName(signature.certificateFailure, false, classLoader);
            Class<?> failureBase = Class.forName(signature.failureBase, false, classLoader);
            if (failureField.getType() != failureBase
                    || !failureBase.isAssignableFrom(certificateFailure)) {
                return null;
            }

            Class<?> emitter = Class.forName(signature.emitter, false, classLoader);
            Method emit = emitter.getDeclaredMethod(
                    "emit",
                    Object.class,
                    Class.forName("twp", false, classLoader)
            );
            if (!Modifier.isPublic(emit.getModifiers())
                    || Modifier.isStatic(emit.getModifiers())
                    || emit.getReturnType() != Object.class) {
                return null;
            }
            emit.setAccessible(true);

            Class<?> unit = Class.forName(signature.unit, false, classLoader);
            Field unitField = unit.getDeclaredField(signature.unitField);
            int unitModifiers = unitField.getModifiers();
            if (unitField.getType() != unit
                    || !Modifier.isStatic(unitModifiers)
                    || !Modifier.isFinal(unitModifiers)) {
                return null;
            }
            unitField.setAccessible(true);
            Object completedUnit = unitField.get(null);
            if (!unit.isInstance(completedUnit)) {
                return null;
            }

            return new TargetBinding(
                    signature,
                    state,
                    certificateFailure,
                    failureBase,
                    failureField,
                    emit,
                    completedUnit
            );
        }

        boolean isCertificateFailure(Object state) {
            if (!stateClass.isInstance(state)) {
                return false;
            }
            try {
                return certificateFailureClass.isInstance(failureField.get(state));
            } catch (IllegalAccessException ignored) {
                return false;
            }
        }

        private static Field findField(Class<?> owner, String name)
                throws NoSuchFieldException {
            for (Class<?> type = owner; type != null; type = type.getSuperclass()) {
                try {
                    Field found = type.getDeclaredField(name);
                    found.setAccessible(true);
                    return found;
                } catch (NoSuchFieldException ignored) {
                    // State wrappers are small; continue through the hierarchy only.
                }
            }
            throw new NoSuchFieldException(owner.getName() + "." + name);
        }
    }

}
