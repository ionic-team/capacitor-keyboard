package com.capacitorjs.plugins.keyboard;

import java.util.HashMap;
import java.util.Map;

public class KeyboardHeightFilter {

    private Keyboard.EventMode eventMode = Keyboard.EventMode.DEFAULT;
    private final Map<String, VerifiedHeight> knownHeights = new HashMap<>();
    private int lastImeHeight = 0;
    private boolean isAnimating = false;
    private boolean justEndedAnimation = false;
    private String lastAnimationScreenKey = "";
    // Non-animated height change (e.g. IME type-mode switch) awaiting confirmation that it persists.
    private int pendingHeight = 0;
    private String pendingScreenKey = "";

    private static class VerifiedHeight {

        final int height;
        final boolean isVerified;

        VerifiedHeight(int height, boolean isVerified) {
            this.height = height;
            this.isVerified = isVerified;
        }
    }

    public void setEventMode(Keyboard.EventMode eventMode) {
        this.eventMode = eventMode;
    }

    public static class FilterResult {

        public final int emitHeight;
        public final boolean shouldEmit;
        // When > 0, the caller should re-check the IME height after a short delay and call confirmPendingHeight().
        public final int pendingHeight;

        public FilterResult(int emitHeight, boolean shouldEmit) {
            this(emitHeight, shouldEmit, 0);
        }

        public FilterResult(int emitHeight, boolean shouldEmit, int pendingHeight) {
            this.emitHeight = emitHeight;
            this.shouldEmit = shouldEmit;
            this.pendingHeight = pendingHeight;
        }
    }

    public void onPrepareAnimation() {
        this.isAnimating = true;
        clearPending();
    }

    private void clearPending() {
        this.pendingHeight = 0;
        this.pendingScreenKey = "";
    }

    /**
     * Called after a short delay with the height the OS still reports. If it matches the pending
     * (previously clamped) height, the change was real (e.g. IME switched type mode without an
     * animation) and we promote it to the verified cache and emit.
     */
    public FilterResult confirmPendingHeight(boolean showingKeyboard, int currentImeHeight, String screenKey) {
        int pending = pendingHeight;
        String pendingKey = pendingScreenKey;
        clearPending();
        if (pending <= 0 || isAnimating || !showingKeyboard) {
            return new FilterResult(0, false);
        }
        if (currentImeHeight != pending || !screenKey.equals(pendingKey)) {
            // Height moved on again or keyboard changed: it really was transient.
            return new FilterResult(currentImeHeight, false);
        }
        knownHeights.put(screenKey, new VerifiedHeight(pending, true));
        boolean shouldEmit = pending != lastImeHeight;
        lastImeHeight = pending;
        return new FilterResult(pending, shouldEmit);
    }

    public FilterResult filterOnApplyWindowInsets(boolean showingKeyboard, int currentImeHeight, String screenKey) {
        if (isAnimating) {
            return new FilterResult(0, false);
        }

        if (!showingKeyboard) {
            lastImeHeight = 0;
            clearPending();
            return new FilterResult(0, false);
        }

        int emitHeight = currentImeHeight;

        // If this layout pass fired immediately after a show animation finished on the SAME screen key,
        // it is the OS correcting its own hallucinated animation bounds. We MUST trust it.
        boolean isVerified = false;
        if (justEndedAnimation) {
            if (screenKey.equals(lastAnimationScreenKey)) {
                isVerified = true;
            }
            justEndedAnimation = false;
        }

        VerifiedHeight known = knownHeights.get(screenKey);

        if (eventMode == Keyboard.EventMode.LAST_KNOWN && known != null && known.isVerified && known.height > 0) {
            // Clamp static glitches (like 650px phantom rotations) to the verified cache!
            // But if it's a verified post-animation correction, we trust the new height.
            if (!isVerified) {
                emitHeight = known.height;
            }
        }

        if (eventMode == Keyboard.EventMode.LAST_KNOWN && emitHeight > 0) {
            if (known == null || known.height != emitHeight || !known.isVerified || isVerified) {
                knownHeights.put(screenKey, new VerifiedHeight(emitHeight, isVerified));
            }
        }
        boolean shouldEmit = emitHeight != lastImeHeight;
        int pending = 0;
        if (!isVerified && currentImeHeight != emitHeight) {
            // We just clamped an unverified height. It is either a transient OS glitch (phantom
            // rotation, aborted animation) or a real non-animated change such as the IME switching
            // type mode (text -> numeric). Do not emit now; ask the caller to re-check shortly.
            shouldEmit = false;
            if (currentImeHeight > 0) {
                pending = currentImeHeight;
                pendingHeight = currentImeHeight;
                pendingScreenKey = screenKey;
            }
        } else {
            clearPending();
        }

        if (shouldEmit) {
            lastImeHeight = emitHeight;
        }
        return new FilterResult(emitHeight, shouldEmit, pending);
    }

    public FilterResult filterOnStart(boolean showingKeyboard, int currentImeHeight, String screenKey) {
        this.isAnimating = true;
        this.justEndedAnimation = false;
        clearPending();

        if (!showingKeyboard) {
            lastImeHeight = 0;
            return new FilterResult(0, true);
        }

        int emitHeight = currentImeHeight;
        VerifiedHeight known = knownHeights.get(screenKey);

        // Only enforce the ceiling/floor if the cache was verified by a stable animation endpoint.
        // This flawlessly protects against Rapid Aborts (glitchy ceiling) and missing suggestion bars (glitchy floor).
        if (eventMode == Keyboard.EventMode.LAST_KNOWN && known != null && known.isVerified && known.height > 0) {
            emitHeight = known.height;
        }

        boolean shouldEmit = emitHeight != lastImeHeight;
        if (shouldEmit) {
            lastImeHeight = emitHeight;
        }
        return new FilterResult(emitHeight, shouldEmit);
    }

    /**
     * The web side reported a new input context after a show animation had already started under
     * the previous context's key. This happens when focus moves between inputs while the IME is
     * open: the OS runs hide -> show back to back and the bridge call carrying the new context
     * loses the race against onStart, so onStart clamped to the OLD context's verified height.
     * Re-evaluate for the new key and let the caller re-emit WILL_SHOW if the height differs.
     */
    public FilterResult filterOnInputContextChanged(boolean showingKeyboard, int currentImeHeight, String screenKey) {
        if (!isAnimating || !showingKeyboard) {
            return new FilterResult(0, false);
        }
        int emitHeight = currentImeHeight;
        VerifiedHeight known = knownHeights.get(screenKey);
        if (eventMode == Keyboard.EventMode.LAST_KNOWN && known != null && known.isVerified && known.height > 0) {
            emitHeight = known.height;
        }
        boolean shouldEmit = emitHeight > 0 && emitHeight != lastImeHeight;
        if (shouldEmit) {
            lastImeHeight = emitHeight;
        }
        return new FilterResult(emitHeight, shouldEmit);
    }

    public FilterResult filterOnEnd(boolean showingKeyboard, int currentImeHeight, String screenKey) {
        this.isAnimating = false;

        if (!showingKeyboard) {
            this.justEndedAnimation = false;
            lastImeHeight = 0;
            return new FilterResult(0, true);
        }

        this.justEndedAnimation = true;
        this.lastAnimationScreenKey = screenKey;
        int emitHeight = currentImeHeight;
        lastImeHeight = emitHeight;

        // The OS animation has concluded. Whatever the true currentImeHeight is natively,
        // we lock it in as our formally verified cache.
        if (eventMode == Keyboard.EventMode.LAST_KNOWN) {
            if (currentImeHeight > 0) {
                knownHeights.put(screenKey, new VerifiedHeight(currentImeHeight, true));
            }
        }

        // The DID_SHOW event must unconditionally fire when the animation finishes
        return new FilterResult(emitHeight, true);
    }

    public int calculateImeHeight(int rawImeHeight, int navBarHeight, boolean ignoreNavBar) {
        if (!ignoreNavBar) {
            return Math.max(0, rawImeHeight - navBarHeight);
        }
        return rawImeHeight;
    }
}
