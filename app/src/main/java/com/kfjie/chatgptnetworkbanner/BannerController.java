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

import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;

final class BannerController {
    private static final long VISIBLE_MS = 15_000L;
    private static final String MESSAGE =
            "\u7f51\u7edc\u8fde\u63a5\u5f02\u5e38\uff0c\u8bf7\u68c0\u67e5\u7f51\u7edc";

    private final Activity activity;
    private final LongSupplier generation;
    private final BooleanSupplier canShow;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable hideRunnable = this::dismiss;

    private FloatingBanner banner;
    private View decorView;
    private long shownGeneration = -1L;
    private long shownAt;
    private long remainingMs = VISIBLE_MS;
    private boolean pending;
    private boolean dismissed;
    private boolean destroyed;
    private Runnable onDestroyed;

    BannerController(
            Activity activity,
            LongSupplier generation,
            BooleanSupplier canShow
    ) {
        this.activity = activity;
        this.generation = generation;
        this.canShow = canShow;
        decorView = activity.getWindow().getDecorView();
        decorView.post(this::attach);
    }

    void setOnDestroyed(Runnable callback) {
        onDestroyed = callback;
    }

    void showForCertificateCycle(long currentGeneration) {
        handler.post(() -> {
            if (destroyed || currentGeneration <= 0L || shownGeneration == currentGeneration) {
                return;
            }
            shownGeneration = currentGeneration;
            shownAt = 0L;
            remainingMs = VISIBLE_MS;
            pending = true;
            dismissed = false;
            updateVisibility();
        });
    }

    void dismissForPageChange() {
        handler.post(this::dismiss);
    }

    void showDebugPreview() {
        handler.post(() -> {
            if (destroyed) {
                return;
            }
            pending = true;
            dismissed = false;
            shownAt = 0L;
            remainingMs = VISIBLE_MS;
            updateVisibility();
        });
    }

    void clearStaleBanner() {
        handler.post(() -> {
            if (destroyed) {
                return;
            }
            pending = false;
            dismissed = true;
            handler.removeCallbacks(hideRunnable);
            if (banner != null) {
                banner.setVisibility(View.GONE);
            }
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
        root.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, orValue, ob) ->
                updateVisibility());
    }

    private void updateVisibility() {
        if (destroyed || banner == null) {
            return;
        }
        banner.refreshTheme(isDark());
        long currentGeneration = generation.getAsLong();
        if (shownGeneration != currentGeneration) {
            shownGeneration = currentGeneration;
            shownAt = 0L;
            remainingMs = VISIBLE_MS;
            pending = currentGeneration > 0L;
            dismissed = false;
        }
        boolean visible = pending && !dismissed && canShow.getAsBoolean()
                && activity.hasWindowFocus() && decorView.isShown();
        if (!visible) {
            pauseTimer();
            banner.setVisibility(View.GONE);
            return;
        }
        if (shownAt == 0L) {
            shownAt = System.currentTimeMillis();
            handler.removeCallbacks(hideRunnable);
            handler.postDelayed(hideRunnable, remainingMs);
        }
        positionBanner();
        banner.setVisibility(View.VISIBLE);
        banner.bringToFront();
    }

    private void dismiss() {
        if (destroyed) {
            return;
        }
        dismissed = true;
        pending = false;
        handler.removeCallbacks(hideRunnable);
        if (banner != null) {
            banner.setVisibility(View.GONE);
        }
    }

    private void pauseTimer() {
        if (shownAt == 0L) {
            return;
        }
        remainingMs = Math.max(0L, remainingMs - (System.currentTimeMillis() - shownAt));
        shownAt = 0L;
        handler.removeCallbacks(hideRunnable);
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
        int top = statusBar + dp(56);
        View menu = findMenuButton(decorView);
        if (menu != null) {
            int[] location = new int[2];
            menu.getLocationInWindow(location);
            top = location[1] + menu.getHeight() + dp(10);
        }
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) banner.getLayoutParams();
        params.leftMargin = dp(16);
        params.topMargin = Math.max(top, statusBar + dp(8));
        banner.setLayoutParams(params);
        int maxWidth = Math.max(dp(160), decorView.getWidth() - dp(32));
        banner.setMaxMessageWidth(Math.max(dp(68), maxWidth - dp(92)));
    }

    private View findMenuButton(View view) {
        if (view == banner) {
            return null;
        }
        CharSequence description = view.getContentDescription();
        if (description != null && menuDescription().contentEquals(description)) {
            return view;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                View result = findMenuButton(group.getChildAt(i));
                if (result != null) {
                    return result;
                }
            }
        }
        return null;
    }

    private String menuDescription() {
        int id = activity.getResources().getIdentifier(
                "f40", "string", "com.openai.chatgpt"
        );
        return id == 0 ? "Navigation menu" : activity.getString(id);
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
