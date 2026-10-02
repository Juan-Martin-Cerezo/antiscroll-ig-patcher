package com.feurstagram.extension;

import android.content.Context;
import android.view.View;

/**
 * Plan-B anti-doomscroll hook: called from injected code in
 * androidx.viewpager.widget.ViewPager's touch dispatch (see
 * patches/ui/ClipsViewerNoSwipePatch.kt). Returns true when the touched pager
 * is the fullscreen reels viewer, so its swipe-to-next-reel is disabled while
 * single-reel screens (opened from a DM or a feed post) still work.
 *
 * Keeps everything reflective so this compiles against no IG/androidx types.
 */
public final class SwipeGuard {

    private SwipeGuard() {}

    /** Resource entry names that identify the reels viewer pager. */
    private static final String[] VIEWER_PAGER_NAMES = {
            "clips_viewer_view_pager",
            "clips_swipe_refresh_container",
    };

    public static boolean shouldBlock(View view) {
        try {
            if (!Config.isClipsSwipeBlocked()) return false;
            if (view == null) return false;
            int id = view.getId();
            if (id <= 0) return false;
            Context context = view.getContext();
            if (context == null) return false;
            String name = context.getResources().getResourceEntryName(id);
            for (String target : VIEWER_PAGER_NAMES) {
                if (target.equals(name)) return true;
            }
        } catch (Throwable t) {
            // Entry-name lookup fails for anonymous views; not blocking is safe.
        }
        return false;
    }
}
