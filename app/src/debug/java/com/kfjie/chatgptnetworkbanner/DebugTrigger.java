package com.kfjie.chatgptnetworkbanner;

import android.app.Activity;
import android.app.Application;
import android.database.ContentObserver;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;

final class DebugTrigger {
    interface Listener {
        void onTrigger();
    }

    private static final String TAG = "ChatGptNetworkBanner";
    private static final String KEY = "chatgpt_banner_debug_trigger";

    static void install(Activity activity, Listener listener) {
        Application application = activity.getApplication();
        Handler handler = new Handler(Looper.getMainLooper());
        String initialValue = Settings.Global.getString(
                application.getContentResolver(),
                KEY
        );
        application.getContentResolver().registerContentObserver(
                Settings.Global.getUriFor(KEY),
                false,
                new ContentObserver(handler) {
                    private String lastValue = initialValue;

                    @Override
                    public void onChange(boolean selfChange) {
                        String value = Settings.Global.getString(
                                application.getContentResolver(),
                                KEY
                        );
                        Log.i(TAG, "Debug trigger setting changed: " + value);
                        boolean risingEdge = "1".equals(value) && !"1".equals(lastValue);
                        if (!risingEdge) {
                            lastValue = value;
                            return;
                        }
                        lastValue = value;
                        activity.runOnUiThread(() -> {
                            try {
                                Log.i(TAG, "Dispatching debug trigger");
                                listener.onTrigger();
                            } catch (Throwable throwable) {
                                Log.w(TAG, "Debug trigger callback failed", throwable);
                            }
                        });
                    }
                }
        );
        Log.i(TAG, "Debug trigger observer registered");
    }

    private DebugTrigger() {
    }
}
