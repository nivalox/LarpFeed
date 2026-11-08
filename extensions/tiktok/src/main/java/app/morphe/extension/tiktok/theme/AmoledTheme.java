/*
 * Copyright 2026 Larpfeed contributors
 * https://github.com/SysAdminDoc/larpfeed
 */
package app.morphe.extension.tiktok.theme;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.tiktok.settings.Settings;
import app.morphe.extension.tiktok.settings.preference.SettingsUi;

/**
 * The choice between AMOLED dark and TikTok's standard dark, made in Larpfeed settings.
 *
 * <p>The AMOLED dark theme patch paints TikTok's dark backgrounds black while patching, so
 * "AMOLED dark" is what the resources already are. "Standard dark" puts TikTok's own grays
 * back: the patch writes a theme overlay (style {@value #STYLE_NAME}) holding the original
 * values of every dark surface it recolored, and a dark-mode activity applies it when the
 * choice says standard. The parts drawn in code ask {@link #isAmoled()} too. Light mode is
 * never touched.
 */
public final class AmoledTheme {
    static final String STANDARD = "standard";
    static final String STYLE_NAME = "larpfeed_standard_dark";

    private static final Application.ActivityLifecycleCallbacks CALLBACKS =
            new Application.ActivityLifecycleCallbacks() {
                // API 29 and up call this before the activity's own onCreate, ahead of its layout.
                public void onActivityPreCreated(Activity activity, Bundle savedInstanceState) {
                    apply(activity);
                }

                @Override
                public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
                    apply(activity);
                }

                @Override public void onActivityStarted(Activity activity) {}
                @Override public void onActivityResumed(Activity activity) {}
                @Override public void onActivityPaused(Activity activity) {}
                @Override public void onActivityStopped(Activity activity) {}
                @Override public void onActivitySaveInstanceState(Activity activity, Bundle outState) {}
                @Override public void onActivityDestroyed(Activity activity) {}
            };

    private static boolean installed;
    /** The overlay's id once looked up: zero when this build has none, -1 before the lookup. */
    private static int styleId = -1;

    private AmoledTheme() {}

    /** True for AMOLED dark, the default. Anything that can't be read counts as AMOLED. */
    public static boolean isAmoled() {
        try {
            return !STANDARD.equals(Settings.AMOLED_THEME.get());
        } catch (Throwable failure) {
            return true;
        }
    }

    /**
     * Called first thing in the main and detail activities' onCreate: watches every activity
     * from here on, and applies the choice to this one, which was created before it could watch.
     */
    public static void install(Activity activity) {
        try {
            if (activity == null) return;
            Application application = activity.getApplication();
            synchronized (AmoledTheme.class) {
                if (!installed && application != null) {
                    application.registerActivityLifecycleCallbacks(CALLBACKS);
                    installed = true;
                }
            }
            apply(activity);
        } catch (Throwable failure) {
            Logger.printException(() -> "Could not install the dark theme choice", failure);
        }
    }

    static void apply(Activity activity) {
        try {
            // AMOLED is how the resources were patched. Only the standard choice has work to do.
            if (isAmoled() || activity == null) return;
            // The context carries the night mode TikTok applied; the system's doesn't.
            if (!SettingsUi.isDarkContext(activity)) return;
            int id = styleId(activity);
            if (id != 0) activity.getTheme().applyStyle(id, true);
        } catch (Throwable failure) {
            Logger.printException(() -> "Could not apply standard dark", failure);
        }
    }

    private static synchronized int styleId(Activity activity) {
        if (styleId == -1) {
            styleId = activity.getResources().getIdentifier(STYLE_NAME, "style", activity.getPackageName());
        }
        return styleId;
    }

    /**
     * Called by the patched Compose palette with each dark card color TikTok sets, a color as the
     * ARGB long the palette goes on to pack. Gives the patch's AMOLED color back for AMOLED dark
     * and TikTok's own for standard dark.
     */
    public static long card(long original) {
        if (isAmoled()) {
            int argb = AmoledWebPages.color();
            if (argb != 0) return 0xFF000000L | (argb & 0xFFFFFFL);
        }
        return original;
    }
}
