package com.capacitorjs.plugins.keyboard;

import java.util.HashMap;
import java.util.Map;

public class KeyboardHeightFilter {
    private Keyboard.EventMode eventMode = Keyboard.EventMode.DEFAULT;
    private final Map<Integer, Integer> knownHeights = new HashMap<>();
    private int lastImeHeight = 0;
    private boolean isAnimating = false;

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

    public FilterResult filterOnApplyWindowInsets(boolean showingKeyboard, int currentImeHeight, int screenWidth) {
        if (isAnimating) {
            return new FilterResult(0, false);
        }

        if (!showingKeyboard) {
            lastImeHeight = 0;
            return new FilterResult(0, false);
        }

        if (currentImeHeight > 0) {
            knownHeights.put(screenWidth, currentImeHeight);
        }
        int emitHeight = currentImeHeight;

        boolean shouldEmit = emitHeight != lastImeHeight;
        if (shouldEmit) {
            lastImeHeight = emitHeight;
        }
        return new FilterResult(emitHeight, shouldEmit);
    }

    public FilterResult filterOnStart(boolean showingKeyboard, int currentImeHeight, int screenWidth) {
        this.isAnimating = true;

        if (!showingKeyboard) {
            lastImeHeight = 0;
            return new FilterResult(0, true);
        }

        int emitHeight = currentImeHeight;
        Integer knownHeight = knownHeights.get(screenWidth);
        
        if (eventMode == Keyboard.EventMode.LAST_KNOWN && knownHeight != null && knownHeight > 0 && currentImeHeight > knownHeight) {
            emitHeight = knownHeight;
        }

        boolean shouldEmit = emitHeight != lastImeHeight;
        if (shouldEmit) {
            lastImeHeight = emitHeight;
        }
        return new FilterResult(emitHeight, shouldEmit);
    }

    public FilterResult filterOnEnd(boolean showingKeyboard, int currentImeHeight, int screenWidth) {
        this.isAnimating = false;

        if (!showingKeyboard) {
            lastImeHeight = 0;
            return new FilterResult(0, true);
        }

        int emitHeight = currentImeHeight;
        Integer knownHeight = knownHeights.get(screenWidth);
        
        if (eventMode == Keyboard.EventMode.LAST_KNOWN && knownHeight != null && knownHeight > 0 && currentImeHeight > knownHeight) {
            emitHeight = knownHeight;
        }

        lastImeHeight = emitHeight;
        
        // Lock in the ceiling height after the animation finishes.
        if (eventMode == Keyboard.EventMode.LAST_KNOWN) {
            if (emitHeight > 0) {
                if (knownHeight == null || emitHeight <= knownHeight) {
                    knownHeights.put(screenWidth, emitHeight);
                }
            }
        }
        
        // The DID_SHOW event must unconditionally fire when the animation finishes
        return new FilterResult(emitHeight, true);
    }

    public int calculateImeHeight(int rawImeHeight, int navBarHeight, boolean resizeOnFullScreen) {
        if (!resizeOnFullScreen) {
            return Math.max(0, rawImeHeight - navBarHeight);
        }
        return rawImeHeight;
    }
}
