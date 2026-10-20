package com.kfjie.chatgptnetworkbanner;

import android.app.Activity;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
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
    private static final String MESSAGE = "\u7f51\u7edc\u8fde\u63a5\u5f02\u5e38\uff0c\u8bf7\u68c0\u67e5\u7f51\u7edc";

    private final Activity activity;
    private final LongSupplier generation;
    private final BooleanSupplier chatPageVisible;
    private Runnable onDestroyed;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private final Runnable hideRunnable = this::dismiss;

    private ViewGroup decor;
    private LinearLayout banner;
    private View decorView;
    private NetworkIcon icon;
    private TextView message;
    private TextView close;
    private long shownGeneration = -1L;
    private long shownAt;
    private boolean pending;
    private boolean dismissed;
    private boolean attachedDark;
    private final View.OnLayoutChangeListener layoutListener =
            (view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
                if (banner != null && banner.getVisibility() == View.VISIBLE) {
                    positionBanner();
                }
            };
    private volatile boolean destroyed;

    BannerController(
            Activity activity,
            LongSupplier generation,
            BooleanSupplier chatPageVisible
    ) {
        this.activity = activity;
        this.generation = generation;
        this.chatPageVisible = chatPageVisible;
        decorView = activity.getWindow().getDecorView();
        decorView.post(this::attach);
    }

    void showForCertificateCycle(long currentGeneration) {
        handler.post(() -> {
            if (destroyed || currentGeneration <= 0L || shownGeneration == currentGeneration) {
                return;
            }
            shownGeneration = currentGeneration;
            shownAt = 0L;
            pending = true;
            dismissed = false;
            attach();
            updateVisibility();
            refreshWithMenuBounds();
        });
    }

    void setOnDestroyed(Runnable callback) {
        onDestroyed = callback;
    }

    void destroy() {
        if (destroyed) {
            return;
        }
        destroyed = true;
        handler.removeCallbacksAndMessages(null);
        if (decorView != null) {
            decorView.removeOnLayoutChangeListener(layoutListener);
        }
        if (banner != null && banner.getParent() instanceof ViewGroup) {
            ((ViewGroup) banner.getParent()).removeView(banner);
        }
        banner = null;
        icon = null;
        message = null;
        close = null;
        decorView = null;
        decor = null;
        Runnable callback = onDestroyed;
        if (callback != null) {
            callback.run();
        }
    }

    private void attach() {
        if (destroyed || banner != null) {
            return;
        }
        View decorView = activity.getWindow().getDecorView();
        if (!(decorView instanceof ViewGroup)) {
            return;
        }

        decor = (ViewGroup) decorView;
        this.decorView = decorView;
        banner = buildBanner();
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                dp(42)
        );
        params.gravity = Gravity.TOP | Gravity.LEFT;
        params.leftMargin = dp(16);
        params.topMargin = dp(56);
        banner.setLayoutParams(params);
        banner.setVisibility(View.GONE);
        decor.addView(banner, params);
        decorView.addOnLayoutChangeListener(layoutListener);
        attachedDark = isDark();
        positionBanner();
    }

    private LinearLayout buildBanner() {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(7), dp(6), dp(7));
        row.setElevation(dp(4));
        row.setBackground(makeBackground());
        row.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        row.setContentDescription(MESSAGE);

        icon = new NetworkIcon(activity);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(18), dp(18));
        iconParams.setMarginEnd(dp(8));
        row.addView(icon, iconParams);

        message = new TextView(activity);
        message.setText(MESSAGE);
        message.setTextSize(14);
        message.setTextColor(textColor());
        message.setSingleLine(true);
        message.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams messageParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        message.setMaxWidth(Math.max(dp(68), activity.getResources().getDisplayMetrics().widthPixels - dp(124)));
        row.addView(message, messageParams);

        close = new TextView(activity);
        close.setText("\u00d7");
        close.setTextSize(22);
        close.setTextColor(secondaryTextColor());
        close.setGravity(Gravity.CENTER);
        close.setContentDescription("\u5173\u95ed");
        close.setClickable(true);
        close.setFocusable(true);
        close.setPadding(dp(8), 0, dp(8), 0);
        TypedValue ripple = new TypedValue();
        if (activity.getTheme().resolveAttribute(
                android.R.attr.selectableItemBackgroundBorderless, ripple, true)) {
            close.setBackgroundResource(ripple.resourceId);
        }
        close.setOnClickListener(view -> dismiss());
        LinearLayout.LayoutParams closeParams = new LinearLayout.LayoutParams(dp(36), dp(36));
        closeParams.setMarginStart(dp(4));
        row.addView(close, closeParams);

        row.setMinimumWidth(dp(160));
        return row;
    }

    private void updateVisibility() {
        if (destroyed || banner == null) {
            return;
        }
        if (!activity.hasWindowFocus()) {
            banner.setVisibility(View.GONE);
            return;
        }
        if (attachedDark != isDark()) {
            refreshTheme();
        }
        long currentGeneration = generation.getAsLong();
        if (shownGeneration != currentGeneration) {
            shownGeneration = currentGeneration;
            shownAt = 0L;
            pending = currentGeneration > 0L;
            dismissed = false;
        }

        if (shownAt > 0L && System.currentTimeMillis() - shownAt >= VISIBLE_MS) {
            dismiss();
            return;
        }

        boolean chatVisible = chatPageVisible.getAsBoolean();
        boolean canShow = pending
                && !dismissed
                && chatVisible
                && decor.isShown();
        if (!canShow) {
            banner.setVisibility(View.GONE);
            return;
        }

        if (shownAt == 0L) {
            shownAt = System.currentTimeMillis();
            handler.removeCallbacks(hideRunnable);
            handler.postDelayed(hideRunnable, VISIBLE_MS);
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

    void dismissForPageChange() {
        dismiss();
    }

    void showTriggeredBanner() {
        handler.post(() -> {
            boolean chatVisible = chatPageVisible.getAsBoolean();
            if (destroyed || !chatVisible) {
                android.util.Log.i(
                        "ChatGptNetworkBanner",
                        "Debug banner skipped destroyed=" + destroyed
                                + " chatVisible=" + chatVisible
                );
                return;
            }
            attach();
            if (banner == null) {
                return;
            }
            showAndScheduleDismissal();
            refreshWithMenuBounds();
        });
    }

    private void showAndScheduleDismissal() {
        attachedDark = isDark();
        refreshTheme();
        pending = false;
        dismissed = true;
        shownAt = System.currentTimeMillis();
        handler.removeCallbacks(hideRunnable);
        positionBanner();
        banner.setVisibility(View.VISIBLE);
        banner.bringToFront();
        handler.postDelayed(hideRunnable, VISIBLE_MS);
    }

    private void refreshWithMenuBounds() {
        if (destroyed || banner == null) {
            return;
        }
        banner.post(this::positionBanner);
    }

    private void refreshTheme() {
        attachedDark = isDark();
        if (banner == null) {
            return;
        }
        banner.setBackground(makeBackground());
        message.setTextColor(textColor());
        close.setTextColor(secondaryTextColor());
        icon.invalidate();
    }

    private void positionBanner() {
        if (banner == null || decor == null) {
            return;
        }
        int statusBarTop = 0;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsets insets = banner.getRootWindowInsets();
            if (insets != null) {
                statusBarTop = insets.getInsets(WindowInsets.Type.statusBars()).top;
            }
        }
        int top = statusBarTop + dp(56);
        View menuButton = findMenuButton();
        if (menuButton != null) {
            int[] location = new int[2];
            menuButton.getLocationInWindow(location);
            int targetTop = location[1] + menuButton.getHeight() + dp(10);
            int minTop = statusBarTop + dp(8);
            if (targetTop >= minTop) {
                top = targetTop;
            }
        }
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) banner.getLayoutParams();
        params.leftMargin = dp(16);
        params.topMargin = top;
        banner.setLayoutParams(params);

        int maxWidth = Math.max(dp(160), decor.getWidth() - dp(32));
        message.setMaxWidth(Math.max(dp(68), maxWidth - dp(92)));
    }

    private View findMenuButton() {
        if (decor == null) {
            return null;
        }
        String expected = menuContentDescription();
        return findViewWithContentDescription(decor, expected);
    }

    private View findViewWithContentDescription(View view, String expected) {
        if (view == banner) {
            return null;
        }
        CharSequence description = view.getContentDescription();
        if (description != null && expected.contentEquals(description)) {
            return view;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                View result = findViewWithContentDescription(group.getChildAt(i), expected);
                if (result != null) {
                    return result;
                }
            }
        }
        return null;
    }

    private String menuContentDescription() {
        int id = activity.getResources().getIdentifier(
                "f40", "string", "com.openai.chatgpt"
        );
        if (id == 0) {
            return "Navigation menu";
        }
        return activity.getString(id);
    }

    private GradientDrawable makeBackground() {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setCornerRadius(dp(12));
        drawable.setColor(isDark() ? Color.rgb(48, 48, 48) : Color.rgb(249, 249, 249));
        drawable.setStroke(dp(1), isDark() ? Color.rgb(72, 72, 72) : Color.rgb(225, 225, 225));
        return drawable;
    }

    private boolean isDark() {
        return (activity.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
    }

    private int textColor() {
        return isDark() ? Color.rgb(240, 240, 240) : Color.rgb(32, 32, 32);
    }

    private int secondaryTextColor() {
        return isDark() ? Color.rgb(184, 184, 184) : Color.rgb(96, 96, 96);
    }

    private int dp(int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
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
            paint.setColor(textColor());
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
