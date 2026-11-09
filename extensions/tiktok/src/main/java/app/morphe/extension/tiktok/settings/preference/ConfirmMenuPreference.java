/*
 * Copyright 2026 Larpfeed contributors
 * https://github.com/SysAdminDoc/larpfeed
 */
package app.morphe.extension.tiktok.settings.preference;

import android.content.Context;
import android.graphics.Typeface;
import android.os.Bundle;
import android.preference.DialogPreference;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import app.morphe.extension.shared.Utils;
import app.morphe.extension.shared.settings.BooleanSetting;
import app.morphe.extension.tiktok.settings.L10n;

/**
 * One row, "Confirm before...", that opens a menu of toggles: each one is a tap that has to be
 * made twice, within a few seconds, before it goes through. The toggles are the same settings
 * the patches read, so nothing about how they work changed when they moved in here.
 */
@SuppressWarnings("deprecation")
public final class ConfirmMenuPreference extends DialogPreference {
    /** One toggle in the menu. {@code title} is the English key; {@code caption} is already in the phone's language. */
    public static final class Entry {
        final String title;
        final String caption;
        final BooleanSetting setting;

        public Entry(String title, String caption, BooleanSetting setting) {
            this.title = title;
            this.caption = caption;
            this.setting = setting;
        }
    }

    private final List<Entry> entries;
    private final List<CheckBox> boxes = new ArrayList<>();

    public ConfirmMenuPreference(Context context, List<Entry> entries) {
        super(context);
        this.entries = new ArrayList<>(entries);
        setKey("confirm_menu");
        setTitle("Confirm before\u2026");
        setSummary("Pick the taps that need a second tap before they go through.");
        setPositiveButtonText(L10n.t(context, "Save"));
        setNegativeButtonText(L10n.t(context, "Cancel"));
    }

    @Override
    public void setTitle(CharSequence title) {
        super.setTitle(L10n.t(getContext(), title));
    }

    @Override
    public void setSummary(CharSequence summary) {
        super.setSummary(L10n.t(getContext(), summary));
    }

    @Override
    protected View onCreateDialogView() {
        Context context = getContext();
        boxes.clear();

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        int padding = SettingsUi.dp(context, 22);
        root.setPadding(padding, padding, padding, SettingsUi.dp(context, 8));

        TextView title = SettingsUi.text(context, getTitle().toString(), SettingsUi.TEXT_HEADLINE,
                SettingsUi.textPrimary(), Typeface.BOLD);
        SettingsUi.markDialogHeading(title);
        root.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView summary = SettingsUi.text(context, getSummary().toString(), SettingsUi.TEXT_BODY_SMALL,
                SettingsUi.textSecondary(), Typeface.NORMAL);
        LinearLayout.LayoutParams summaryParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        summaryParams.setMargins(0, SettingsUi.dp(context, 14), 0, SettingsUi.dp(context, 10));
        root.addView(summary, summaryParams);

        ScrollView scroll = new ScrollView(context);
        LinearLayout rows = new LinearLayout(context);
        rows.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(rows, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, SettingsUi.dialogListHeight(context, 360));
        scrollParams.topMargin = SettingsUi.dp(context, 10);
        root.addView(scroll, scrollParams);

        for (Entry entry : entries) {
            CheckBox check = new CheckBox(context);
            check.setText(L10n.t(context, entry.title));
            SettingsUi.styleCheckBoxRow(check);
            check.setTag("confirm_" + entry.setting.key);
            // The saved value, not get(): paused, get() answers the default, and a save would wipe it.
            check.setChecked(Boolean.TRUE.equals(entry.setting.savedValue()));
            boxes.add(check);
            rows.addView(check, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            TextView caption = SettingsUi.text(context, entry.caption, SettingsUi.TEXT_CAPTION,
                    SettingsUi.textSecondary(), Typeface.NORMAL);
            caption.setPadding(SettingsUi.dp(context, 52), 0, SettingsUi.dp(context, 8), SettingsUi.dp(context, 10));
            rows.addView(caption, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        return root;
    }

    @Override
    protected void showDialog(Bundle state) {
        super.showDialog(state);
        SettingsUi.styleFramedDialog(getDialog());
    }

    @Override
    protected void onDialogClosed(boolean positiveResult) {
        super.onDialogClosed(positiveResult);
        if (!positiveResult) return;
        boolean failed = false;
        for (int index = 0; index < entries.size() && index < boxes.size(); index++) {
            Entry entry = entries.get(index);
            boolean wanted = boxes.get(index).isChecked();
            if (wanted == Boolean.TRUE.equals(entry.setting.savedValue())) continue;
            if (!entry.setting.save(wanted)) failed = true;
        }
        notifyChanged();
        if (failed) Utils.showToastShort(L10n.t(getContext(), "Couldn't save these choices. Try again."));
    }
}
