package com.kfjie.chatgptnetworkbanner;

import android.app.Activity;

final class DebugTrigger {
    interface Listener {
        void onTrigger();
    }

    static void install(Activity activity, Listener listener) {
    }
}
