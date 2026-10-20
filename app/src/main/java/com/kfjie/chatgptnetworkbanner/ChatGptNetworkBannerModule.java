package com.kfjie.chatgptnetworkbanner;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;

public final class ChatGptNetworkBannerModule extends XposedModule {
    private static final String TAG = "ChatGptNetworkBanner";
    private static final String TARGET_PACKAGE = "com.openai.chatgpt";
    private static final String MAIN_ACTIVITY = "com.openai.chatgpt.MainActivity";
    private static final String ROOT_RENDERER = "s511";
    private static final String CHAT_STATE = "p8p0";
    private static final String CERTIFICATE_STATE = "a9p0";
    private static final int ROOT_AUTH_ARG = 0;
    private static final int ROOT_STATE_ARG = 1;
    private volatile BannerController bannerController;
    private volatile Object lastChatState;
    private volatile String lastChatSessionId;
    private volatile String authSessionId;
    private volatile Object authSessionRef;
    private volatile boolean chatPageVisible;
    private volatile Application application;
    private volatile boolean certificateCycle;
    private volatile boolean bannerShownForCycle;
    private volatile boolean bannerEventPending;
    private volatile long bannerGeneration;
    private volatile boolean rootRendererHooked;
    private final AtomicBoolean debugTriggerRegistered = new AtomicBoolean();

    private Class<?> chatStateClass;
    private Object certificateState;
    private Field authIdField;

    @Override
    public void onPackageReady(XposedModuleInterface.PackageReadyParam param) {
        if (!TARGET_PACKAGE.equals(param.getPackageName())) {
            detach();
            return;
        }

        try {
            installMainActivityHook(param.getClassLoader());
        } catch (Throwable throwable) {
            log(Log.ERROR, TAG, "MainActivity hook failed; leaving ChatGPT untouched", throwable);
        }

    }

    private void installMainActivityHook(ClassLoader classLoader) throws ReflectiveOperationException {
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

    private synchronized void installLateHooks(ClassLoader classLoader) {
        if (rootRendererHooked) {
            return;
        }
        rootRendererHooked = true;
        try {
            installRootRendererHook(classLoader);
            if (BuildConfig.DEBUG) {
                log(Log.INFO, TAG, "Root renderer hook installed");
            }
        } catch (Throwable throwable) {
            log(Log.ERROR, TAG, "Root renderer hook failed; leaving ChatGPT untouched", throwable);
            return;
        }
    }

    private void installRootRendererHook(ClassLoader classLoader) throws ReflectiveOperationException {
        Class<?> root = Class.forName(ROOT_RENDERER, false, classLoader);
        chatStateClass = Class.forName(CHAT_STATE, false, classLoader);
        Class<?> certificateClass = Class.forName(CERTIFICATE_STATE, false, classLoader);
        certificateState = certificateClass.getField("a").get(null);

        Method render = findRootRenderer(root);
        hook(render)
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(chain -> {
                    Object[] args = chain.getArgs().toArray();
                    if (args.length <= ROOT_STATE_ARG) {
                        return chain.proceed();
                    }

                    Object auth = args[ROOT_AUTH_ARG];
                    Object state = args[ROOT_STATE_ARG];
                    if (state == certificateState) {
                        if (canProjectCertificateAsChat(auth)) {
                            chatPageVisible = true;
                            markCertificateEvent();
                            args[ROOT_STATE_ARG] = lastChatState;
                            return chain.proceed(args);
                        }
                        chatPageVisible = false;
                        dismissForPageChange();
                        return chain.proceed();
                    }

                    if (!sameAuthSession(auth)) {
                        resetForAuth(auth);
                    }
                    boolean nextChatVisible = chatStateClass.isInstance(state);
                    if (BuildConfig.DEBUG) {
                        log(Log.INFO, TAG, "Root state " + state.getClass().getName()
                                + " chat=" + nextChatVisible);
                    }
                    lastChatState = nextChatVisible ? state : null;
                    lastChatSessionId = authSessionId;
                    boolean wasChatVisible = chatPageVisible;
                    chatPageVisible = nextChatVisible && sameAuthSession(auth);
                    if (wasChatVisible && !chatPageVisible) {
                        dismissForPageChange();
                    }
                    if (certificateCycle) {
                        certificateCycle = false;
                        bannerShownForCycle = false;
                    }
                    bannerEventPending = false;
                    return chain.proceed();
                });
    }

    private Method findRootRenderer(Class<?> root) throws NoSuchMethodException {
        for (Method method : root.getDeclaredMethods()) {
            Class<?>[] types = method.getParameterTypes();
            if ("f".equals(method.getName())
                    && types.length == 10
                    && "z8r0".equals(types[0].getName())
                    && "c9p0".equals(types[1].getName())
                    && "s2n".equals(types[8].getName())
                    && types[9] == Integer.TYPE) {
                method.setAccessible(true);
                return method;
            }
        }
        throw new NoSuchMethodException(ROOT_RENDERER + ".f");
    }


    private void createBannerController(Activity activity) {
        chatPageVisible = false;
        Application currentApplication = activity.getApplication();
        if (currentApplication != application) {
            application = currentApplication;
            if (BuildConfig.DEBUG && debugTriggerRegistered.compareAndSet(false, true)) {
                log(Log.INFO, TAG, "Installing debug trigger");
                DebugTrigger.install(activity, this::showTriggeredBanner);
            }
        }
        BannerController previous = bannerController;
        if (previous != null) {
            previous.destroy();
        }

        BannerController controller = new BannerController(
                activity,
                () -> bannerGeneration,
                this::isBannerPageVisible
        );
        controller.setOnDestroyed(() -> {
            if (bannerController == controller) {
                bannerController = null;
            }
        });
        bannerController = controller;
        if (bannerEventPending) {
            bannerEventPending = false;
            controller.showForCertificateCycle(bannerGeneration);
        }
    }

    private void showTriggeredBanner() {
        BannerController controller = bannerController;
        if (controller != null) {
            controller.showTriggeredBanner();
        }
    }

    private boolean canProjectCertificateAsChat(Object auth) {
        Object chat = lastChatState;
        String chatSession = lastChatSessionId;
        return chat != null
                && chatStateClass.isInstance(chat)
                && chatSession != null
                && sameAuthSession(auth)
                && chatSession.equals(authSessionId);
    }

    private void resetForAuth(Object auth) {
        authIdField = findAuthIdField(auth);
        authSessionRef = auth;
        authSessionId = readAuthId(auth, authIdField);
        lastChatState = null;
        lastChatSessionId = null;
        chatPageVisible = false;
        certificateCycle = false;
        bannerShownForCycle = false;
        bannerEventPending = false;
    }

    private boolean sameAuthSession(Object current) {
        if (authSessionRef == current) {
            return true;
        }
        String remembered = authSessionId;
        if (remembered == null || current == null) {
            return false;
        }

        Field idField = authIdField;
        if (idField == null || idField.getDeclaringClass() != current.getClass()) {
            idField = findAuthIdField(current);
            authIdField = idField;
        }
        if (idField == null) {
            return false;
        }
        return remembered.equals(readAuthId(current, idField));
    }

    private Field findAuthIdField(Object auth) {
        if (auth == null) {
            return null;
        }
        try {
            return auth.getClass().getField("d");
        } catch (NoSuchFieldException ignored) {
            return null;
        }
    }

    private String readAuthId(Object auth, Field idField) {
        if (auth == null || idField == null) {
            return null;
        }
        try {
            Object value = idField.get(auth);
            return value instanceof String ? (String) value : null;
        } catch (IllegalAccessException ignored) {
            return null;
        }
    }

    private void markCertificateEvent() {
        if (!certificateCycle) {
            certificateCycle = true;
            bannerShownForCycle = false;
        }
        if (bannerShownForCycle) {
            return;
        }

        bannerShownForCycle = true;
        long generation = ++bannerGeneration;
        BannerController controller = bannerController;
        if (controller == null) {
            bannerEventPending = true;
        } else {
            controller.showForCertificateCycle(generation);
        }
    }

    private void dismissForPageChange() {
        BannerController controller = bannerController;
        if (controller != null) {
            controller.dismissForPageChange();
        }
    }

    private boolean isBannerPageVisible() {
        return chatPageVisible;
    }

}
