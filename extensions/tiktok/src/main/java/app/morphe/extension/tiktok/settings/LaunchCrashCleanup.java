/*
 * Copyright 2026 Larpfeed contributors
 * https://github.com/SysAdminDoc/larpfeed
 */
package app.morphe.extension.tiktok.settings;

import app.morphe.extension.shared.settings.Setting;
import app.morphe.extension.tiktok.featuregatelab.FeatureGateLabStore;
import app.morphe.extension.tiktok.privacy.DeviceIdentity;
import app.morphe.extension.tiktok.settings.preference.ReleaseNotes;

import java.util.Arrays;

/**
 * TikTok's own recovery from crashes at launch. On the third launch crash in a row it deletes
 * every preferences file whose name isn't on its keep-list, and Larpfeed's weren't on it: a crash
 * loop erased every setting and the Lab's rules one start before Larpfeed's safe mode could
 * pause anything.
 *
 * <p>This runs where TikTok builds that list, which can be inside its crash handler and before
 * Larpfeed has a context, so it touches nothing but the array it is given. The names are
 * compile-time constants, and reading them loads none of the classes they come from.
 */
public final class LaunchCrashCleanup {
    /** Every preferences file Larpfeed writes. A new one belongs here as well. */
    static final String[] LARPFEED_FILES = {
            Setting.PREFERENCES_NAME,
            FeatureGateLabStore.PREFS_NAME,
            ReleaseNotes.PREFS_NAME,
            DeviceIdentity.PREFERENCES,
            app.morphe.extension.tiktok.inbox.GroupChats.PREFS_NAME,
            app.morphe.extension.tiktok.comment.StickerRecents.PREFS_NAME,
            app.morphe.extension.tiktok.settings.preference.NewSettingDots.FILE,
    };

    private LaunchCrashCleanup() {
    }

    /** TikTok's keep-list with Larpfeed's files after it. */
    public static String[] keepLarpfeedFiles(String[] kept) {
        if (kept == null) return LARPFEED_FILES.clone();
        String[] joined = Arrays.copyOf(kept, kept.length + LARPFEED_FILES.length);
        System.arraycopy(LARPFEED_FILES, 0, joined, kept.length, LARPFEED_FILES.length);
        return joined;
    }
}
