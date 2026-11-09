/*
 * Copyright 2026 Larpfeed contributors
 * https://github.com/SysAdminDoc/larpfeed
 */
package app.morphe.extension.tiktok.inbox;

import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.ViewTreeObserver;
import android.widget.TextView;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.tiktok.settings.Settings;

/**
 * Takes the "Delete for me" button out of the menu on a message in a chat.
 *
 * <p>The patch calls {@link #watch} at the end of every TuxTextView constructor, TikTok's own
 * label view, so nothing here depends on a name R8 renamed. A label whose text reads "Delete for
 * me" (or the text typed in Larpfeed settings, for another language) takes its row with it:
 * the smallest group around it that holds no other text, and is no taller than a menu row.
 * A label TikTok reuses for something else gets its row back.
 *
 * <p>If the button isn't a TuxTextView, or its text differs, nothing happens.
 */
public final class DeleteForMe {
    private static final String ENGLISH_LABEL = "Delete for me";
    /** How many levels above the label its row can be. */
    private static final int MAX_CLIMB = 6;
    /** A row taller than this isn't a menu row, whatever it holds. */
    private static final int ROW_LIMIT_DP = 110;

    private static final Set<TextView> WATCHED =
            Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<TextView, Boolean>()));
    private static final Map<TextView, Hidden> HIDDEN = new WeakHashMap<>();

    /** A row taken out of the layout, and what it was before. */
    private static final class Hidden {
        final View row;
        final int visibility;
        final int height;

        Hidden(View row) {
            this.row = row;
            this.visibility = row.getVisibility();
            ViewGroup.LayoutParams params = row.getLayoutParams();
            this.height = params != null ? params.height : ViewGroup.LayoutParams.WRAP_CONTENT;
        }
    }

    private DeleteForMe() {}

    /** Called with each TuxTextView as its constructor ends. */
    public static void watch(TextView view) {
        try {
            if (view == null || !Settings.HIDE_DELETE_FOR_ME.get()) return;
            if (!WATCHED.add(view)) return;
            view.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence text, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence text, int start, int before, int count) {}

                @Override
                public void afterTextChanged(Editable text) {
                    update(view);
                }
            });
            update(view); // The text may come from the layout, before anything could watch.
        } catch (Throwable failure) {
            Logger.printException(() -> "Could not watch a label for Delete for me", failure);
        }
    }

    private static void update(TextView view) {
        try {
            if (Settings.HIDE_DELETE_FOR_ME.get() && isLabel(view.getText())) {
                hide(view);
            } else {
                restore(view);
            }
        } catch (Throwable failure) {
            Logger.printException(() -> "Could not update the Delete for me button", failure);
        }
    }

    /** True for the English label, or any label typed in settings. Case and spaces don't count. */
    static boolean isLabel(CharSequence text) {
        if (text == null) return false;
        String shown = normalize(text.toString());
        if (shown.isEmpty()) return false;
        if (shown.equals(normalize(ENGLISH_LABEL))) return true;
        String typed = Settings.HIDE_DELETE_FOR_ME_TEXT.get();
        if (typed == null || typed.trim().isEmpty()) return false;
        for (String label : typed.split(",")) {
            if (shown.equals(normalize(label))) return true;
        }
        return false;
    }

    private static String normalize(String text) {
        return text.replace('\u2019', '\'').trim().replaceAll("\\s+", " ").toLowerCase();
    }

    private static void hide(TextView view) {
        // After the view is attached and has been laid out, so a row's height can be told from a panel's.
        view.post(() -> {
            try {
                if (!isLabel(view.getText())) return;
                if (view.getHeight() > 0 || !view.isAttachedToWindow()) {
                    hideNow(view);
                    return;
                }
                view.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
                    @Override
                    public boolean onPreDraw() {
                        ViewTreeObserver observer = view.getViewTreeObserver();
                        if (observer.isAlive()) observer.removeOnPreDrawListener(this);
                        if (isLabel(view.getText())) hideNow(view);
                        return true;
                    }
                });
            } catch (Throwable failure) {
                Logger.printException(() -> "Could not hide the Delete for me button", failure);
            }
        });
    }

    private static void hideNow(TextView label) {
        View row = rowOf(label);
        if (row == null) {
            Logger.printDebug(() -> "Delete for me: no row of its own around the label, left alone");
            return;
        }
        synchronized (HIDDEN) {
            if (HIDDEN.containsKey(label)) return;
            HIDDEN.put(label, new Hidden(row));
        }
        row.setVisibility(View.GONE);
        ViewGroup.LayoutParams params = row.getLayoutParams();
        if (params != null) {
            // A recycler measures a GONE item from its layout params, so the row has to be no height too.
            params.height = 0;
            row.setLayoutParams(params);
        }
    }

    private static void restore(TextView label) {
        Hidden hidden;
        synchronized (HIDDEN) {
            hidden = HIDDEN.remove(label);
        }
        if (hidden == null) return;
        hidden.row.setVisibility(hidden.visibility);
        ViewGroup.LayoutParams params = hidden.row.getLayoutParams();
        if (params != null) {
            params.height = hidden.height;
            hidden.row.setLayoutParams(params);
        }
    }

    /**
     * The largest group around [label] that holds no text but its own, stays within a menu row's
     * height, and isn't the top of its window. Null when there is none.
     */
    private static View rowOf(TextView label) {
        Context context = label.getContext();
        int limit = Math.max(label.getHeight() * 4, dp(context, ROW_LIMIT_DP));
        View row = null;
        View current = label;
        for (int level = 0; level < MAX_CLIMB; level++) {
            ViewParent parent = current.getParent();
            if (!(parent instanceof ViewGroup)) break;
            ViewGroup group = (ViewGroup) parent;
            if (group.getParent() == null) break; // The window's own top: never a row.
            if (group.getHeight() > limit) break;
            if (!holdsOnly(group, label)) break;
            row = group;
            current = group;
        }
        return row;
    }

    /** Whether [only] is the one text under [group]. */
    private static boolean holdsOnly(ViewGroup group, TextView only) {
        for (int index = 0; index < group.getChildCount(); index++) {
            View child = group.getChildAt(index);
            if (child instanceof TextView) {
                if (child != only && ((TextView) child).getText().length() > 0) return false;
            } else if (child instanceof ViewGroup && !holdsOnly((ViewGroup) child, only)) {
                return false;
            }
        }
        return true;
    }

    private static int dp(Context context, int dp) {
        return (int) (dp * context.getResources().getDisplayMetrics().density + 0.5f);
    }
}
