/*
 * Copyright 2026 Larpfeed contributors
 * https://github.com/SysAdminDoc/larpfeed
 */
package app.morphe.extension.tiktok.inbox;

import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.ViewTreeObserver;
import android.widget.TextView;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.tiktok.interaction.TapConfirmation;
import app.morphe.extension.tiktok.settings.Settings;

/**
 * The buttons in the menu on a message in a chat: hides Delete for me, or asks for a second tap
 * before Unsend or Delete for me goes through.
 *
 * <p>The patch calls {@link #watch} at the end of every TuxTextView constructor, TikTok's own
 * label view, so nothing here depends on a name R8 renamed. A label whose text reads like one
 * of those buttons (in English, or as typed in Larpfeed settings for another language) takes
 * its row with it: the smallest group around the label that holds no other text and is no
 * taller than a menu row. A label TikTok reuses for something else gets its row back.
 *
 * <p>If a button isn't drawn by a TuxTextView, or reads differently, nothing happens.
 */
public final class MessageMenu {
    enum Kind { NONE, DELETE_FOR_ME, UNSEND }

    private static final String[] DELETE_FOR_ME_LABELS = {"delete for me"};
    private static final String[] UNSEND_LABELS = {"unsend", "unsend message"};
    /** How many levels above the label its row can be. */
    private static final int MAX_CLIMB = 6;
    /** A row taller than this isn't a menu row, whatever it holds. */
    private static final int ROW_LIMIT_DP = 110;

    private static final Set<TextView> WATCHED =
            Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<TextView, Boolean>()));
    private static final Map<TextView, Hidden> HIDDEN = new WeakHashMap<>();
    private static final Map<TextView, List<Guard>> GUARDED = new WeakHashMap<>();

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

    /** A view whose touches wait for a second tap, and the touch listener it had before. */
    private static final class Guard {
        final View view;
        final View.OnTouchListener previous;

        Guard(View view, View.OnTouchListener previous) {
            this.view = view;
            this.previous = previous;
        }
    }

    /** Lets the first tap on a button through as a warning only, and the second as the real one. */
    private static final class ConfirmTouch implements View.OnTouchListener {
        private final View row;
        private final String action;
        private final View.OnTouchListener previous;

        ConfirmTouch(View row, String action, View.OnTouchListener previous) {
            this.row = row;
            this.action = action;
            this.previous = previous;
        }

        @Override
        public boolean onTouch(View view, MotionEvent event) {
            try {
                if (previous != null && previous.onTouch(view, event)) return true;
                if (event.getActionMasked() != MotionEvent.ACTION_UP) return false;
                if (TapConfirmation.messageMenu(row, action)) return false; // The second tap: it goes through.
                // The first: end the touch as a cancel, so the button never counts it as a click.
                MotionEvent cancel = MotionEvent.obtain(event);
                cancel.setAction(MotionEvent.ACTION_CANCEL);
                view.onTouchEvent(cancel);
                cancel.recycle();
                return true;
            } catch (Throwable failure) {
                Logger.printException(() -> "Could not ask before a message button", failure);
                return false;
            }
        }
    }

    private MessageMenu() {}

    /** Called with each TuxTextView as its constructor ends. */
    public static void watch(TextView view) {
        try {
            if (view == null || !anythingOn()) return;
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
            Logger.printException(() -> "Could not watch a label in the message menu", failure);
        }
    }

    private static boolean anythingOn() {
        return Settings.HIDE_DELETE_FOR_ME.get() || Settings.CONFIRM_UNSEND.get()
                || Settings.CONFIRM_DELETE_FOR_ME.get();
    }

    private static void update(TextView view) {
        try {
            Kind kind = kindOf(view.getText());
            boolean hide = kind == Kind.DELETE_FOR_ME && Settings.HIDE_DELETE_FOR_ME.get();
            boolean confirm = !hide && ((kind == Kind.UNSEND && Settings.CONFIRM_UNSEND.get())
                    || (kind == Kind.DELETE_FOR_ME && Settings.CONFIRM_DELETE_FOR_ME.get()));
            if (hide) {
                unguard(view);
                hide(view);
            } else {
                restore(view);
                if (confirm) guard(view, kind == Kind.UNSEND ? "unsend" : "delete");
                else unguard(view);
            }
        } catch (Throwable failure) {
            Logger.printException(() -> "Could not update a button in the message menu", failure);
        }
    }

    /** Which button a label is, or none. Case and spaces don't count. */
    static Kind kindOf(CharSequence text) {
        if (text == null) return Kind.NONE;
        String shown = normalize(text.toString());
        if (shown.isEmpty()) return Kind.NONE;
        if (matches(shown, DELETE_FOR_ME_LABELS, Settings.HIDE_DELETE_FOR_ME_TEXT.get())) return Kind.DELETE_FOR_ME;
        if (matches(shown, UNSEND_LABELS, Settings.UNSEND_LABEL_TEXT.get())) return Kind.UNSEND;
        return Kind.NONE;
    }

    private static boolean matches(String shown, String[] builtIn, String typed) {
        for (String label : builtIn) {
            if (shown.equals(label)) return true;
        }
        if (typed == null || typed.trim().isEmpty()) return false;
        for (String label : typed.split(",")) {
            if (shown.equals(normalize(label))) return true;
        }
        return false;
    }

    private static String normalize(String text) {
        return text.replace('\u2019', '\'').trim().replaceAll("\\s+", " ").toLowerCase();
    }

    /** Runs [action] once the label is attached and laid out, so a row's height can be told from a panel's. */
    private static void whenLaidOut(TextView view, Runnable action) {
        view.post(() -> {
            try {
                if (view.getHeight() > 0 || !view.isAttachedToWindow()) {
                    action.run();
                    return;
                }
                view.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
                    @Override
                    public boolean onPreDraw() {
                        ViewTreeObserver observer = view.getViewTreeObserver();
                        if (observer.isAlive()) observer.removeOnPreDrawListener(this);
                        action.run();
                        return true;
                    }
                });
            } catch (Throwable failure) {
                Logger.printException(() -> "Could not wait for the message menu to lay out", failure);
            }
        });
    }

    private static void hide(TextView view) {
        whenLaidOut(view, () -> {
            if (kindOf(view.getText()) == Kind.DELETE_FOR_ME && Settings.HIDE_DELETE_FOR_ME.get()) hideNow(view);
        });
    }

    private static void hideNow(TextView label) {
        View row = rowOf(label);
        if (row == null) {
            Logger.printDebug(() -> "Message menu: no row of its own around the label, left alone");
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

    private static void guard(TextView label, String action) {
        whenLaidOut(label, () -> {
            Kind kind = kindOf(label.getText());
            if (kind == Kind.NONE) return;
            synchronized (GUARDED) {
                if (GUARDED.containsKey(label)) return;
            }
            View row = rowOf(label);
            if (row == null) row = label;
            // Every view from the label up to its row: whichever one the touch is delivered to asks.
            List<Guard> guards = new ArrayList<>();
            for (View view = label; view != null; ) {
                View.OnTouchListener previous = existingTouchListener(view);
                view.setOnTouchListener(new ConfirmTouch(row, action, previous));
                guards.add(new Guard(view, previous));
                if (view == row) break;
                ViewParent parent = view.getParent();
                view = parent instanceof View ? (View) parent : null;
            }
            synchronized (GUARDED) {
                GUARDED.put(label, guards);
            }
        });
    }

    private static void unguard(TextView label) {
        List<Guard> guards;
        synchronized (GUARDED) {
            guards = GUARDED.remove(label);
        }
        if (guards == null) return;
        for (Guard guard : guards) guard.view.setOnTouchListener(guard.previous);
    }

    /** The touch listener a view already has, or null: the field is TikTok's platform's, and may be out of reach. */
    private static View.OnTouchListener existingTouchListener(View view) {
        try {
            Field info = View.class.getDeclaredField("mListenerInfo");
            info.setAccessible(true);
            Object listeners = info.get(view);
            if (listeners == null) return null;
            Field touch = listeners.getClass().getDeclaredField("mOnTouchListener");
            touch.setAccessible(true);
            return (View.OnTouchListener) touch.get(listeners);
        } catch (Throwable unreachable) {
            return null;
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
