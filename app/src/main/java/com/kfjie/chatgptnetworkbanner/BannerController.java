package com.kfjie.chatgptnetworkbanner;

import android.app.Activity;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

final class BannerController {
    private static final long VISIBLE_MS = 15_000L;
    private static final String MESSAGE =
            "\u7f51\u7edc\u8fde\u63a5\u5f02\u5e38\uff0c\u8bf7\u68c0\u67e5\u7f51\u7edc";
    private static final long ATTACH_RETRY_MS = 300L;

    private final Activity activity;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable hideRunnable = this::dismiss;
    private final Runnable attachRunnable = this::attach;

    private FloatingBanner banner;
    private View decorView;
    private int bannerLeft = -1;
    private int bannerTop = -1;
    private boolean pending;
    private boolean handledThisCycle;
    private boolean destroyed;
    private Runnable onDestroyed;

    BannerController(Activity activity) {
        this.activity = activity;
        decorView = activity.getWindow().getDecorView();
        decorView.post(attachRunnable);
    }

    void setOnDestroyed(Runnable callback) {
        onDestroyed = callback;
    }

    void showCertificateNoticeIfChat() {
        handler.post(() -> {
            if (destroyed || handledThisCycle) {
                return;
            }
            pending = true;
            showIfReady();
        });
    }

    void showDebugPreview() {
        handler.post(() -> {
            if (destroyed) {
                return;
            }
            pending = true;
            showIfReady();
        });
    }

    void destroy() {
        if (destroyed) {
            return;
        }
        destroyed = true;
        handler.removeCallbacksAndMessages(null);
        if (banner != null && banner.getParent() instanceof ViewGroup) {
            ((ViewGroup) banner.getParent()).removeView(banner);
        }
        banner = null;
        decorView = null;
        Runnable callback = onDestroyed;
        if (callback != null) {
            callback.run();
        }
    }

    private void attach() {
        if (destroyed || banner != null) {
            return;
        }
        View root = activity.getWindow().getDecorView();
        if (!(root instanceof ViewGroup)) {
            handler.postDelayed(attachRunnable, ATTACH_RETRY_MS);
            return;
        }
        decorView = root;
        banner = new FloatingBanner(activity);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                dp(42)
        );
        params.gravity = Gravity.TOP | Gravity.LEFT;
        params.leftMargin = dp(16);
        banner.setLayoutParams(params);
        banner.setVisibility(View.GONE);
        ((ViewGroup) root).addView(banner, params);
        if (pending) {
            showIfReady();
        }
    }

    private void showIfReady() {
        if (destroyed || banner == null) {
            return;
        }
        if (!pending || !decorView.isShown()) {
            banner.setVisibility(View.GONE);
            return;
        }
        handledThisCycle = true;
        banner.refreshTheme(isDark());
        positionBanner();
        banner.setVisibility(View.VISIBLE);
        banner.bringToFront();
        scheduleDismissal();
        if (BuildConfig.DEBUG) {
            android.util.Log.i("ChatGptNetworkBanner", "banner visible");
        }
    }

    private void dismiss() {
        if (destroyed) {
            return;
        }
        pending = false;
        handler.removeCallbacks(hideRunnable);
        if (banner != null) {
            banner.setVisibility(View.GONE);
        }
    }

    private void scheduleDismissal() {
        handler.removeCallbacks(hideRunnable);
        handler.postDelayed(hideRunnable, VISIBLE_MS);
    }

    private void positionBanner() {
        if (destroyed || banner == null || decorView == null) {
            return;
        }
        int statusBar = 0;
        WindowInsets insets = banner.getRootWindowInsets();
        if (insets != null) {
            statusBar = insets.getInsets(WindowInsets.Type.statusBars()).top;
        }
        int nextLeft = dp(16);
        int nextTop = statusBar + dp(56) + dp(10);
        if (nextLeft != bannerLeft || nextTop != bannerTop) {
            bannerLeft = nextLeft;
            bannerTop = nextTop;
            FrameLayout.LayoutParams params =
                    (FrameLayout.LayoutParams) banner.getLayoutParams();
            params.leftMargin = nextLeft;
            params.topMargin = nextTop;
            banner.setLayoutParams(params);
        }
        int maxWidth = Math.max(dp(160), decorView.getWidth() - dp(32));
        banner.setMaxMessageWidth(Math.max(dp(68), maxWidth - dp(92)));
    }

    private boolean isDark() {
        return (activity.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
    }

    private int dp(int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }

    private final class FloatingBanner extends LinearLayout {
        private final NetworkIcon icon;
        private final TextView message;
        private final TextView close;
        private boolean dark;
        private int maxMessageWidth = -1;

        FloatingBanner(Activity context) {
            super(context);
            setOrientation(HORIZONTAL);
            setGravity(Gravity.CENTER_VERTICAL);
            setPadding(dp(12), dp(7), dp(6), dp(7));
            setElevation(dp(4));
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
            setContentDescription(MESSAGE);

            dark = isDark();
            setBackground(background(dark));
            icon = new NetworkIcon(context);
            addView(icon, new LayoutParams(dp(18), dp(18)) {{
                setMarginEnd(dp(8));
            }});

            message = new TextView(context);
            message.setText(MESSAGE);
            message.setTextSize(14);
            message.setSingleLine(true);
            message.setEllipsize(TextUtils.TruncateAt.END);
            addView(message, new LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            ));

            close = new TextView(context);
            close.setText("\u00d7");
            close.setTextSize(22);
            close.setGravity(Gravity.CENTER);
            close.setContentDescription("\u5173\u95ed");
            close.setClickable(true);
            close.setFocusable(true);
            close.setPadding(dp(8), 0, dp(8), 0);
            TypedValue ripple = new TypedValue();
            if (context.getTheme().resolveAttribute(
                    android.R.attr.selectableItemBackgroundBorderless, ripple, true)) {
                close.setBackgroundResource(ripple.resourceId);
            }
            close.setOnClickListener(view -> dismiss());
            LayoutParams closeParams = new LayoutParams(dp(36), dp(36));
            closeParams.setMarginStart(dp(4));
            addView(close, closeParams);
            setMinimumWidth(dp(160));
            refreshTheme(dark);
        }

        void setMaxMessageWidth(int width) {
            if (width == maxMessageWidth) {
                return;
            }
            maxMessageWidth = width;
            message.setMaxWidth(width);
        }

        void refreshTheme(boolean nextDark) {
            if (dark == nextDark) {
                return;
            }
            dark = nextDark;
            setBackground(background(dark));
            message.setTextColor(textColor(dark));
            close.setTextColor(secondaryTextColor(dark));
            icon.setDark(dark);
        }
    }

    private GradientDrawable background(boolean dark) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setCornerRadius(dp(12));
        drawable.setColor(dark ? Color.rgb(48, 48, 48) : Color.rgb(249, 249, 249));
        drawable.setStroke(dp(1), dark ? Color.rgb(72, 72, 72) : Color.rgb(225, 225, 225));
        return drawable;
    }

    private int textColor(boolean dark) {
        return dark ? Color.rgb(240, 240, 240) : Color.rgb(32, 32, 32);
    }

    private int secondaryTextColor(boolean dark) {
        return dark ? Color.rgb(184, 184, 184) : Color.rgb(96, 96, 96);
    }

    private final class NetworkIcon extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();

        NetworkIcon(Activity context) {
            super(context);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(1.9f));
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
            setDark(isDark());
        }

        void setDark(boolean dark) {
            paint.setColor(textColor(dark));
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float width = getWidth();
            float height = getHeight();
            float centerX = width / 2f;
            float centerY = height * 0.62f;
            canvas.drawCircle(centerX, centerY, height * 0.12f, paint);
            path.reset();
            path.moveTo(width * 0.18f, height * 0.42f);
            path.quadTo(centerX, height * 0.17f, width * 0.82f, height * 0.42f);
            canvas.drawPath(path, paint);
            path.reset();
            path.moveTo(width * 0.05f, height * 0.14f);
            path.lineTo(width * 0.95f, height * 0.86f);
            canvas.drawPath(path, paint);
        }

        private int dp(float value) {
            return Math.round(value * getResources().getDisplayMetrics().density);
        }
    }
}
