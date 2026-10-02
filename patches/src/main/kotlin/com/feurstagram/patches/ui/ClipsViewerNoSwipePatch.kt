package com.feurstagram.patches.ui

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import com.feurstagram.patches.shared.Constants.COMPATIBILITY_INSTAGRAM
import com.feurstagram.patches.shared.Constants.EXTENSION

private const val GUARD_CLASS = "Lcom/feurstagram/extension/SwipeGuard;"

/**
 * The reels viewer pages through androidx.viewpager.widget.ViewPager (the legacy
 * one, no setUserInputEnabled API). Rather than hunting an obfuscated IG class,
 * the patch hooks the androidx ViewPager itself and delegates the decision to the
 * extension's {@link com.feurstagram.extension.SwipeGuard}, which only says
 * "block" for the INSTANCE whose resource entry name is the reels pager
 * (`clips_viewer_view_pager`). Every other ViewPager in the app keeps its
 * behaviour (main tab pager, stories, DM media grids...).
 *
 * onInterceptTouchEvent returning false stops the pager from starting a drag, so
 * the vertical swap never advances; taps on children (like, comment, captions)
 * still work because they consume the events before the pager sees them.
 */
internal object ViewPagerOnInterceptTouchEventFingerprint : Fingerprint(
    definingClass = "Landroidx/viewpager/widget/ViewPager;",
    name = "onInterceptTouchEvent",
    parameters = listOf("Landroid/view/MotionEvent;"),
    returnType = "Z",
)

/**
 * Mirror hook for onTouchEvent: keeps the same rule when the touch is already
 * past interception (e.g. dropped by a child without consuming).
 */
internal object ViewPagerOnTouchEventFingerprint : Fingerprint(
    definingClass = "Landroidx/viewpager/widget/ViewPager;",
    name = "onTouchEvent",
    parameters = listOf("Landroid/view/MotionEvent;"),
    returnType = "Z",
)

@Suppress("unused")
val clipsViewerNoSwipePatch = bytecodePatch(
    name = "Clips viewer no-swipe",
    description = "Disables swiping between reels inside the fullscreen reels viewer " +
        "(opened from a DM or a feed post keeps working). Delegates the decision to " +
        "the extension's SwipeGuard gated on block_clip_swipe.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_INSTAGRAM)

    extendWith(EXTENSION)

    execute {
        for (fingerprint in listOf(
            ViewPagerOnInterceptTouchEventFingerprint,
            ViewPagerOnTouchEventFingerprint,
        )) {
            val method = fingerprint.method
            val registerCount = method.implementation?.registerCount
                ?: throw PatchException("ViewPager ${fingerprint.name} has no implementation")
            // p0 = this (ViewPager), p1 = MotionEvent; v0 must be free at entry.
            if (registerCount < 3) {
                throw PatchException("ViewPager ${fingerprint.name} has no free register")
            }

            method.addInstructionsWithLabels(
                0,
                """
                    invoke-static { p1 }, $GUARD_CLASS->shouldBlock(Landroid/view/View;)Z
                    move-result v0
                    if-eqz v0, :proceed
                    const/4 v0, 0x0
                    return v0
                    :proceed
                    nop
                """,
            )
        }
    }
}
