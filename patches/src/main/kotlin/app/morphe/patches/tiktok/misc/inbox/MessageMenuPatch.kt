/*
 * Copyright 2026 Larpfeed contributors
 * https://github.com/SysAdminDoc/larpfeed
 */
package app.morphe.patches.tiktok.misc.inbox

import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patches.shared.compat.AppCompatibilities
import app.morphe.patches.tiktok.misc.extension.sharedExtensionPatch
import app.morphe.patches.tiktok.misc.settings.SettingsStatusLoadFingerprint
import app.morphe.patches.tiktok.misc.settings.configurableBytecodePatch as bytecodePatch
import app.morphe.patches.tiktok.misc.settings.settingsPatch
import app.morphe.util.addInstruction
import com.android.tools.smali.dexlib2.Opcode

private const val MESSAGE_MENU_EXTENSION = "Lapp/morphe/extension/tiktok/inbox/MessageMenu;"

/** TikTok's own text label. Tux keeps its real names, so this one doesn't depend on a renamed class. */
private const val TUX_TEXT_VIEW_CLASS = "Lcom/bytedance/tux/input/TuxTextView;"

/**
 * Hands `this` to the extension just before each return of this constructor, so the label is
 * built by the time the extension looks at it. Returns are done last to first, so the indexes
 * of the ones still to do don't move.
 */
internal fun MutableMethod.watchBeforeReturns() {
    val returns = implementation!!.instructions.withIndex()
        .filter { it.value.opcode == Opcode.RETURN_VOID }
        .map { it.index }
    if (returns.isEmpty()) {
        throw PatchException("Message menu controls: a TuxTextView constructor has no return to hook.")
    }
    returns.asReversed().forEach { index ->
        addInstruction(
            index,
            "invoke-static/range { p0 .. p0 }, $MESSAGE_MENU_EXTENSION->watch(Landroid/widget/TextView;)V",
        )
    }
}

/**
 * The buttons in the menu on a message in a chat: hides Delete for me, or asks for a second tap
 * before Unsend or Delete for me goes through. It watches every TuxTextView, TikTok's label
 * view, for a button's text and acts on the row the label sits in, so it needs no knowledge of
 * the menu's own, renamed, classes. If a button isn't drawn by a TuxTextView, or its text isn't
 * the one in Larpfeed settings, nothing changes.
 */
@Suppress("unused")
val messageMenuPatch = bytecodePatch(
    name = "Message menu controls",
    description = "Hides the Delete for me button in the menu on a message in a chat, or asks for a " +
        "second tap before Unsend or Delete for me goes through. Starts off. Turn them on in " +
        "Larpfeed settings.",
) {
    category("Inbox")
    dependsOn(settingsPatch, sharedExtensionPatch)
    compatibleWith(*AppCompatibilities.tiktok())

    execute {
        SettingsStatusLoadFingerprint.method.addInstruction(
            0,
            "invoke-static {}, Lapp/morphe/extension/tiktok/settings/SettingsStatus;->enableMessageMenu()V",
        )
        val constructors = mutableClassDefBy(TUX_TEXT_VIEW_CLASS).methods
            .filter { it.name == "<init>" && it.implementation != null }
        if (constructors.isEmpty()) {
            throw PatchException("Message menu controls: TuxTextView has no constructor with code.")
        }
        constructors.forEach { it.watchBeforeReturns() }
    }
}
