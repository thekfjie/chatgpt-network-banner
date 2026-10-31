package com.kfjie.chatgptnetworkbanner;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;

public final class ChatGptNetworkBannerModule extends XposedModule {
    private static final String TAG = "ChatGptNetworkBanner";
    private static final String TARGET_PACKAGE = "com.openai.chatgpt";
    private static final String MAIN_ACTIVITY = "com.openai.chatgpt.MainActivity";
    private static final String ROUTE_KEY = "com.openai.platform.navigation.Route";
    private static final String ROUTE_SEMANTIC_NAME = "Untrusted Network Certificate";
    private static final String KEEP_PREVIOUS_VISIBLE =
            "com.openai.feature.navigation.keepPreviousVisible";

    // Every candidate must pass the complete runtime shape checks below.
    private static final TargetSignature[] TARGETS = {
            new TargetSignature(
                    "1.2026.293",
                    "p000.xnv",
                    "p000.vqq0",
                    "p000.uqq0",
                    "p000.dlr",
                    "p000.a011",
                    "p000.w52",
                    "f277677c",
                    "f47371b"
            )
    };

    private final AtomicBoolean lateHooksInstalled = new AtomicBoolean();
    private volatile BannerController bannerController;
    private volatile TargetBinding targetBinding;
    private volatile boolean sidePanelOpen;
    private volatile boolean certificateCycleActive;
    private volatile boolean bannerShownForCycle;
    private volatile long bannerGeneration;

    @Override
    public void onPackageReady(XposedModuleInterface.PackageReadyParam param) {
        if (!TARGET_PACKAGE.equals(param.getPackageName())) {
            return;
        }
        try {
            installMainActivityHook(param.getClassLoader());
        } catch (Throwable throwable) {
            log(Log.ERROR, TAG, "MainActivity hook unavailable", throwable);
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
                        installLateHooks(activity.getClass().getClassLoader());
                        createBannerController((Activity) activity);
                    }
                    return result;
                });
    }

    private void installLateHooks(ClassLoader classLoader) {
        if (!lateHooksInstalled.compareAndSet(false, true)) {
            return;
        }
        TargetBinding binding = findTarget(classLoader);
        if (binding == null) {
            if (BuildConfig.DEBUG) {
                log(Log.WARN, TAG, "no verified target signature; leaving UI untouched");
            }
            return;
        }
        targetBinding = binding;
        try {
            installSceneSelectorHook(binding);
            installDestinationContentHook(binding);
            installDrawerStateHook(binding);
            if (BuildConfig.DEBUG) {
                log(Log.INFO, TAG, "UI hooks installed for " + binding.signature.label);
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
            } catch (Throwable ignored) {
                // Obfuscated names may be reused with another structure. No hook
                // is installed unless every shape check succeeds.
            }
        }
        return null;
    }

    private void installSceneSelectorHook(TargetBinding binding)
            throws ReflectiveOperationException {
        Method select = binding.sceneSelector;
        Constructor<?> retainedScene = binding.retainedSceneConstructor;
        Class<?> routeClass = binding.routeClass;
        Field entryRouteField = binding.entryRouteField;
        hook(select)
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(chain -> {
                    Object[] args = chain.getArgs().toArray();
                    if (args.length != 2 || !(args[1] instanceof List)) {
                        return chain.proceed();
                    }
                    List<?> entries = (List<?>) args[1];
                    Object last = entries.size() < 2 ? null : entries.get(entries.size() - 1);
                    Object route = readMapValue(readField(last, entryRouteField), ROUTE_KEY);
                    if (!routeClass.isInstance(route)) {
                        clearCertificateCycle();
                        return chain.proceed();
                    }
                    Object scene = chain.proceed(args);
                    if (scene == null
                            || !binding.singlePaneScene.isInstance(scene)
                            || !binding.isMarkedKeepPreviousVisible(last)) {
                        clearCertificateCycle();
                        return scene;
                    }

                    List<?> previous = entries.subList(0, entries.size() - 1);
                    markCertificateCycle();
                    return retainedScene.newInstance(
                            last,
                            previous,
                            readField(args[0], "f2680a")
                    );
                });
    }

    private void installDestinationContentHook(TargetBinding binding)
            throws ReflectiveOperationException {
        Method invoke = binding.destinationInvoke;
        Field destinationField = binding.destinationField;
        Field unitField = binding.unitField;
        Class<?> routeClass = binding.routeClass;
        hook(invoke)
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(chain -> {
                    Object destination = readField(chain.getThisObject(), destinationField);
                    if (routeClass.isInstance(destination)
                            && binding.isStandardCertificateState(chain.getArg(0))) {
                        markCertificateCycle();
                        return unitField.get(null);
                    }
                    return chain.proceed();
                });
    }

    private void installDrawerStateHook(TargetBinding binding)
            throws ReflectiveOperationException {
        Method setState = binding.drawerStateSetter;
        Object closedState = readDrawerValue(binding, "Closed");
        Object openState = readDrawerValue(binding, "Open");
        if (closedState == null || openState == null) {
            throw new NoSuchFieldException("DrawerState values");
        }
        hook(setState)
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(chain -> {
                    Object value = chain.getArg(0);
                    if (value == openState) {
                        sidePanelOpen = true;
                        dismissBanner();
                    } else if (value == closedState) {
                        sidePanelOpen = false;
                    }
                    return chain.proceed();
                });
    }

    private static Object readDrawerValue(TargetBinding binding, String name) {
        try {
            Field field = binding.drawerStateClass.getField(name);
            if (!Modifier.isStatic(field.getModifiers())) {
                return null;
            }
            field.setAccessible(true);
            return field.get(null);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private void createBannerController(Activity activity) {
        BannerController previous = bannerController;
        if (previous != null) {
            previous.destroy();
        }
        sidePanelOpen = false;
        BannerController controller = new BannerController(
                activity,
                () -> bannerGeneration,
                () -> !sidePanelOpen
        );
        controller.setOnDestroyed(() -> {
            if (bannerController == controller) {
                bannerController = null;
            }
        });
        bannerController = controller;
        if (BuildConfig.DEBUG) {
            DebugTrigger.install(activity, controller::showDebugPreview);
        }
    }

    private void markCertificateCycle() {
        if (!certificateCycleActive) {
            certificateCycleActive = true;
            bannerShownForCycle = false;
        }
        if (bannerShownForCycle) {
            return;
        }
        bannerShownForCycle = true;
        long generation = ++bannerGeneration;
        BannerController controller = bannerController;
        if (controller != null) {
            controller.showForCertificateCycle(generation);
        }
    }

    private void clearCertificateCycle() {
        certificateCycleActive = false;
        bannerShownForCycle = false;
        BannerController controller = bannerController;
        if (controller != null) {
            controller.clearStaleBanner();
        }
    }

    private void dismissBanner() {
        BannerController controller = bannerController;
        if (controller != null) {
            controller.dismissForPageChange();
        }
    }

    private static Object readMapValue(Object target, String key) {
        return target instanceof Map ? ((Map<?, ?>) target).get(key) : null;
    }

    private static Object readField(Object target, Field field) {
        if (target == null) {
            return null;
        }
        try {
            return field.get(target);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static Object readField(Object target, String name) {
        if (target == null) {
            return null;
        }
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Field found = type.getDeclaredField(name);
                found.setAccessible(true);
                return found.get(target);
            } catch (NoSuchFieldException ignored) {
                // Continue through the small target hierarchy only.
            } catch (ReflectiveOperationException ignored) {
                return null;
            }
        }
        return null;
    }

    private static Field field(Class<?> owner, String name, Class<?> type)
            throws NoSuchFieldException {
        Field found = owner.getDeclaredField(name);
        if (!type.isAssignableFrom(found.getType())) {
            throw new NoSuchFieldException(owner.getName() + "." + name);
        }
        found.setAccessible(true);
        return found;
    }

    private static final class TargetSignature {
        final String label;
        final String route;
        final String sceneSelector;
        final String retainedScene;
        final String destinationContent;
        final String unitClass;
        final String drawerState;
        final String entryRouteField;
        final String destinationField;

        TargetSignature(
                String label,
                String route,
                String sceneSelector,
                String retainedScene,
                String destinationContent,
                String unitClass,
                String drawerState,
                String entryRouteField,
                String destinationField
        ) {
            this.label = label;
            this.route = route;
            this.sceneSelector = sceneSelector;
            this.retainedScene = retainedScene;
            this.destinationContent = destinationContent;
            this.unitClass = unitClass;
            this.drawerState = drawerState;
            this.entryRouteField = entryRouteField;
            this.destinationField = destinationField;
        }
    }

    private static final class TargetBinding {
        final TargetSignature signature;
        final Class<?> routeClass;
        final Method sceneSelector;
        final Constructor<?> retainedSceneConstructor;
        final Class<?> singlePaneScene;
        final Method destinationInvoke;
        final Field destinationField;
        final Field unitField;
        final Field entryRouteField;
        final Method drawerStateSetter;
        final Class<?> drawerStateClass;
        final Class<?> certificateStateClass;
        final Field certificatePrimaryField;
        final Field certificateSecondaryField;
        final Field certificateTertiaryField;

        private TargetBinding(
                TargetSignature signature,
                Class<?> routeClass,
                Method sceneSelector,
                Constructor<?> retainedSceneConstructor,
                Class<?> singlePaneScene,
                Method destinationInvoke,
                Field destinationField,
                Field unitField,
                Field entryRouteField,
                Method drawerStateSetter,
                Class<?> drawerStateClass,
                Class<?> certificateStateClass,
                Field certificatePrimaryField,
                Field certificateSecondaryField,
                Field certificateTertiaryField
        ) {
            this.signature = signature;
            this.routeClass = routeClass;
            this.sceneSelector = sceneSelector;
            this.retainedSceneConstructor = retainedSceneConstructor;
            this.singlePaneScene = singlePaneScene;
            this.destinationInvoke = destinationInvoke;
            this.destinationField = destinationField;
            this.unitField = unitField;
            this.entryRouteField = entryRouteField;
            this.drawerStateSetter = drawerStateSetter;
            this.drawerStateClass = drawerStateClass;
            this.certificateStateClass = certificateStateClass;
            this.certificatePrimaryField = certificatePrimaryField;
            this.certificateSecondaryField = certificateSecondaryField;
            this.certificateTertiaryField = certificateTertiaryField;
        }

        static TargetBinding inspect(ClassLoader classLoader, TargetSignature signature)
                throws ReflectiveOperationException {
            Class<?> route = Class.forName(signature.route, false, classLoader);
            Object routeInstance = tryReadStaticInstance(route);
            if (routeInstance == null || !hasSemanticName(routeInstance, ROUTE_SEMANTIC_NAME)) {
                return null;
            }

            Class<?> selector = Class.forName(signature.sceneSelector, false, classLoader);
            Method select = findSceneSelector(selector);
            if (select == null) {
                return null;
            }
            Class<?> retained = Class.forName(signature.retainedScene, false, classLoader);
            Constructor<?> retainedConstructor = findRetainedConstructor(retained);
            if (retainedConstructor == null) {
                return null;
            }
            Class<?> singlePane = Class.forName("fpu0", false, classLoader);
            inspectSinglePane(singlePane, select.getReturnType());

            Class<?> destination = Class.forName(
                    signature.destinationContent,
                    false,
                    classLoader
            );
            Method invoke = findDestinationInvoke(destination);
            if (invoke == null) {
                return null;
            }
            Field destinationField = field(
                    destination,
                    signature.destinationField,
                    Object.class
            );
            if (!Modifier.isFinal(destinationField.getModifiers())) {
                return null;
            }

            Class<?> unit = Class.forName(signature.unitClass, false, classLoader);
            Object unitInstance = tryReadStaticInstance(unit);
            Field unitField = field(unit, "f37a", unit);
            if (unitInstance == null || unitField.get(null) != unitInstance) {
                return null;
            }

            Class<?> entry = Class.forName("zee0", false, classLoader);
            Field entryRouteField = field(entry, signature.entryRouteField, Map.class);
            Class<?> drawer = Class.forName(signature.drawerState, false, classLoader);
            Method drawerSetter = drawer.getDeclaredMethod("m56566g", Object.class);
            if (drawerSetter.getReturnType() != Void.TYPE) {
                return null;
            }
            drawerSetter.setAccessible(true);
            Class<?> certificateState = Class.forName("hle0", false, classLoader);
            Field certificatePrimary = field(certificateState, "f88049a", Boolean.TYPE);
            Field certificateSecondary = field(certificateState, "f88050b", Boolean.TYPE);
            Field certificateTertiary = field(certificateState, "f88051c", Boolean.TYPE);

            return new TargetBinding(
                    signature,
                    route,
                    select,
                    retainedConstructor,
                    singlePane,
                    invoke,
                    destinationField,
                    unitField,
                    entryRouteField,
                    drawerSetter,
                    drawer,
                    certificateState,
                    certificatePrimary,
                    certificateSecondary,
                    certificateTertiary
            );
        }

        private static Method findSceneSelector(Class<?> selector) {
            Method found = null;
            for (Method method : selector.getDeclaredMethods()) {
                if (!"a".equals(method.getName())
                        || method.getParameterCount() != 2
                        || method.getReturnType() != Object.class
                        || method.getParameterTypes()[1] != List.class
                        || !method.getParameterTypes()[0].getName().equals("a96")) {
                    continue;
                }
                if (found != null) {
                    return null;
                }
                found = method;
            }
            if (found != null) {
                found.setAccessible(true);
            }
            return found;
        }

        private static Constructor<?> findRetainedConstructor(Class<?> retained) {
            Constructor<?> found = null;
            for (Constructor<?> constructor : retained.getDeclaredConstructors()) {
                Class<?>[] types = constructor.getParameterTypes();
                if (types.length != 3
                        || !types[0].getName().equals("zee0")
                        || types[1] != List.class
                        || !types[2].getName().equals("vjr")) {
                    continue;
                }
                if (found != null) {
                    return null;
                }
                found = constructor;
            }
            if (found != null) {
                found.setAccessible(true);
            }
            return found;
        }

        private static void inspectSinglePane(Class<?> singlePane, Class<?> sceneType)
                throws NoSuchMethodException {
            Constructor<?>[] constructors = singlePane.getDeclaredConstructors();
            if (constructors.length == 0) {
                throw new NoSuchMethodException(singlePane.getName());
            }
            Class<?>[] types = constructors[0].getParameterTypes();
            if (types.length != 3
                    || types[0] != sceneType
                    || !types[1].getName().equals("zee0")
                    || types[2] != List.class) {
                throw new NoSuchMethodException(singlePane.getName());
            }
        }

        private static Method findDestinationInvoke(Class<?> destination) {
            Method found = null;
            for (Method method : destination.getDeclaredMethods()) {
                if (!"invoke".equals(method.getName())
                        || method.getParameterCount() != 3
                        || method.getReturnType() != Object.class) {
                    continue;
                }
                if (found != null) {
                    return null;
                }
                found = method;
            }
            if (found != null) {
                found.setAccessible(true);
            }
            return found;
        }

        static Object tryReadStaticInstance(Class<?> type) {
            try {
                Field instance = type.getField("INSTANCE");
                if (Modifier.isStatic(instance.getModifiers())
                        && type.isAssignableFrom(instance.getType())) {
                    instance.setAccessible(true);
                    return instance.get(null);
                }
            } catch (ReflectiveOperationException ignored) {
                // Only a stable singleton route is accepted.
            }
            return null;
        }

        private static boolean hasSemanticName(Object instance, String expected) {
            try {
                Method name = instance.getClass().getMethod("a");
                if (name.getParameterCount() != 0 || name.getReturnType() != String.class) {
                    return false;
                }
                name.setAccessible(true);
                return expected.equals(name.invoke(instance));
            } catch (ReflectiveOperationException ignored) {
                return false;
            }
        }

        boolean isMarkedKeepPreviousVisible(Object entry) {
            Object metadata = readField(entry, entryRouteField);
            return metadata instanceof Map
                    && Boolean.TRUE.equals(((Map<?, ?>) metadata).get(KEEP_PREVIOUS_VISIBLE));
        }

        boolean isStandardCertificateState(Object state) {
            if (!certificateStateClass.isInstance(state)) {
                return false;
            }
            try {
                return certificatePrimaryField.getBoolean(state)
                        && !certificateSecondaryField.getBoolean(state)
                        && !certificateTertiaryField.getBoolean(state);
            } catch (IllegalAccessException ignored) {
                return false;
            }
        }
    }
}
