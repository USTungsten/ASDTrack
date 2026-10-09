package com.ellesjourney.tracker;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Space;
import android.widget.TextView;

import java.util.Locale;

final class Ui {
    static final int INK = Color.rgb(23, 34, 59);
    static final int MUTED = Color.rgb(114, 128, 154);
    static final int CREAM = Color.rgb(244, 246, 249);
    static final int WHITE = Color.WHITE;
    static final int PURPLE = Color.rgb(101, 87, 201);
    static final int PURPLE_LIGHT = Color.rgb(137, 123, 228);
    static final int LAVENDER = Color.rgb(238, 235, 255);
    static final int MINT = Color.rgb(225, 245, 236);
    static final int GREEN = Color.rgb(38, 119, 89);
    static final int PEACH = Color.rgb(255, 240, 216);
    static final int ROSE = Color.rgb(255, 240, 242);
    static final int LIGHT = Color.rgb(241, 243, 247);

    private Ui() {}

    static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    static GradientDrawable background(int color, float radiusDp, Context context) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(context, (int) radiusDp));
        return drawable;
    }

    static GradientDrawable gradient(int startColor, int endColor, float radiusDp, Context context) {
        GradientDrawable drawable = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR, new int[]{startColor, endColor});
        drawable.setCornerRadius(dp(context, (int) radiusDp));
        return drawable;
    }

    static GradientDrawable borderedBackground(int color, int borderColor, float radiusDp, Context context) {
        GradientDrawable drawable = background(color, radiusDp, context);
        drawable.setStroke(dp(context, 1), borderColor);
        return drawable;
    }

    static LinearLayout vertical(Context context) {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    static LinearLayout horizontal(Context context) {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setGravity(Gravity.CENTER_VERTICAL);
        return layout;
    }

    static TextView text(Context context, String value, int sizeSp, int color) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextSize(sizeSp);
        view.setTextColor(color);
        view.setLineSpacing(0, 1.08f);
        return view;
    }

    static TextView title(Context context, String value) {
        TextView view = text(context, value, 28, INK);
        view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    static TextView heading(Context context, String value) {
        TextView view = text(context, value, 18, INK);
        view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    static TextView section(Context context, String value) {
        TextView view = text(context, value.toUpperCase(Locale.getDefault()), 12, MUTED);
        view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        view.setLetterSpacing(.08f);
        view.setPadding(dp(context, 2), dp(context, 22), 0, dp(context, 8));
        return view;
    }

    static LinearLayout card(Context context) {
        LinearLayout card = vertical(context);
        card.setPadding(dp(context, 18), dp(context, 17), dp(context, 18), dp(context, 17));
        card.setBackground(borderedBackground(WHITE, Color.rgb(237, 240, 245), 21, context));
        card.setElevation(dp(context, 1));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 0, dp(context, 14));
        card.setLayoutParams(params);
        return card;
    }

    static Button primaryButton(Context context, String label) {
        Button button = new Button(context);
        button.setText(label);
        button.setTextColor(WHITE);
        button.setTextSize(16);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setAllCaps(false);
        button.setGravity(Gravity.CENTER);
        button.setBackground(background(PURPLE, 16, context));
        button.setMinHeight(dp(context, 54));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(context, 56));
        params.setMargins(0, dp(context, 12), 0, dp(context, 8));
        button.setLayoutParams(params);
        return button;
    }

    static Button secondaryButton(Context context, String label) {
        Button button = primaryButton(context, label);
        button.setTextColor(PURPLE);
        button.setBackground(background(LAVENDER, 16, context));
        return button;
    }

    static EditText input(Context context, String hint) {
        EditText input = new EditText(context);
        input.setHint(hint);
        input.setTextSize(16);
        input.setTextColor(INK);
        input.setHintTextColor(MUTED);
        input.setSingleLine(true);
        input.setPadding(dp(context, 14), 0, dp(context, 14), 0);
        input.setBackground(background(LIGHT, 14, context));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(context, 52));
        params.setMargins(0, dp(context, 7), 0, dp(context, 13));
        input.setLayoutParams(params);
        return input;
    }

    static EditText decimalInput(Context context, String hint) {
        EditText input = input(context, hint);
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        return input;
    }

    static Space space(Context context, int height) {
        Space space = new Space(context);
        space.setLayoutParams(new LinearLayout.LayoutParams(1, dp(context, height)));
        return space;
    }

    static void setMargins(View view, int left, int top, int right, int bottom) {
        ViewGroup.LayoutParams current = view.getLayoutParams();
        LinearLayout.LayoutParams params = current instanceof LinearLayout.LayoutParams
                ? (LinearLayout.LayoutParams) current
                : new LinearLayout.LayoutParams(current == null ? ViewGroup.LayoutParams.WRAP_CONTENT : current.width,
                current == null ? ViewGroup.LayoutParams.WRAP_CONTENT : current.height);
        params.setMargins(dp(view.getContext(), left), dp(view.getContext(), top),
                dp(view.getContext(), right), dp(view.getContext(), bottom));
        view.setLayoutParams(params);
    }
}
