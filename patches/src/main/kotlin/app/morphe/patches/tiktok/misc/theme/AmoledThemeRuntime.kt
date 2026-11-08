/*
 * Copyright 2026 Larpfeed contributors
 * https://github.com/SysAdminDoc/larpfeed
 */
package app.morphe.patches.tiktok.misc.theme

import app.morphe.patches.tiktok.interaction.authorregion.DetailActivityOnCreateFingerprint
import app.morphe.patches.tiktok.misc.extension.sharedExtensionPatch
import app.morphe.patches.tiktok.misc.inbox.MainActivityOnCreateFingerprint
import app.morphe.patches.tiktok.misc.settings.SettingsStatusLoadFingerprint
import app.morphe.patches.tiktok.misc.settings.configurableBytecodePatch as bytecodePatch
import app.morphe.patches.tiktok.misc.settings.settingsPatch
import app.morphe.util.addInstruction

/**
 * The "Dark theme" row in Larpfeed settings, and the code that carries out its Standard dark choice.
 *
 * AMOLED dark theme recolors TikTok's dark surfaces while patching, and also leaves a theme
 * overlay with their original values. This switches the row on and has each activity ask, as it
 * is created, whether to apply that overlay. It runs only as part of AMOLED dark theme.
 */
internal fun amoledThemeRuntimePatch() = bytecodePatch {
    dependsOn(settingsPatch, sharedExtensionPatch)
    execute {
        SettingsStatusLoadFingerprint.method.addInstruction(
            0,
            "invoke-static {}, Lapp/morphe/extension/tiktok/settings/SettingsStatus;->enableAmoledTheme()V",
        )
        // The detail page as well, for a process a deep link starts with no MainActivity.
        listOf(MainActivityOnCreateFingerprint.method, DetailActivityOnCreateFingerprint.method).forEach {
            it.addInstruction(
                0,
                "invoke-static/range { p0 .. p0 }, $AMOLED_THEME_EXTENSION->install(Landroid/app/Activity;)V",
            )
        }
    }
}
