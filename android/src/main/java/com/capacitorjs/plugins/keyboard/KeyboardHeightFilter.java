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

        public FilterResult(int emitHeight, boolean shouldEmit) {
            this.emitHeight = emitHeight;
            this.shouldEmit = shouldEmit;
        }
    }

    public void onPrepareAnimation() {
        this.isAnimating = true;
    }

    public FilterResult filterOnApplyWindowInsets(boolean showingKeyboard, int currentImeHeight, String screenKey) {
        if (isAnimating) {
            return new FilterResult(0, false);
        }

        if (!showingKeyboard) {
            lastImeHeight = 0;
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
        if (!isVerified && currentImeHeight != emitHeight) {
            // We just clamped a massive OS hallucination. 
            // Do not emit JS events for this transient glitch!
            shouldEmit = false;
        }
        
        if (shouldEmit) {
            lastImeHeight = emitHeight;
        }
        return new FilterResult(emitHeight, shouldEmit);
    }

    public FilterResult filterOnStart(boolean showingKeyboard, int currentImeHeight, String screenKey) {
        this.isAnimating = true;
        this.justEndedAnimation = false;

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
