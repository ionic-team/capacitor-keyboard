package com.capacitorjs.plugins.keyboard;

import org.junit.Test;
import static org.junit.Assert.*;

public class KeyboardHeightFilterTest {

    private static final String PORTRAIT_KEY = "1080x1920|default";
    private static final String LANDSCAPE_KEY = "1920x1080|default";

    @Test
    public void testNormalOpen() {
        KeyboardHeightFilter filter = new KeyboardHeightFilter();
        filter.setEventMode(Keyboard.EventMode.LAST_KNOWN);
        
        filter.onPrepareAnimation();
        
        KeyboardHeightFilter.FilterResult startResult = filter.filterOnStart(true, 941, PORTRAIT_KEY);
        assertTrue(startResult.shouldEmit);
        assertEquals(941, startResult.emitHeight);
        
        KeyboardHeightFilter.FilterResult endResult = filter.filterOnEnd(true, 941, PORTRAIT_KEY);
        assertTrue(endResult.shouldEmit); // DID_SHOW must fire unconditionally on animation end
        assertEquals(941, endResult.emitHeight);
        
        // Stabilize after animation
        KeyboardHeightFilter.FilterResult applyResult = filter.filterOnApplyWindowInsets(true, 941, PORTRAIT_KEY);
        assertFalse(applyResult.shouldEmit);
    }
    
    @Test
    public void testNormalClose() {
        KeyboardHeightFilter filter = new KeyboardHeightFilter();
        filter.setEventMode(Keyboard.EventMode.LAST_KNOWN);
        
        // Open first to establish height
        filter.filterOnApplyWindowInsets(true, 941, PORTRAIT_KEY);
        
        filter.onPrepareAnimation();
        
        // Close animation starts
        KeyboardHeightFilter.FilterResult startResult = filter.filterOnStart(false, 0, PORTRAIT_KEY);
        assertTrue(startResult.shouldEmit);
        assertEquals(0, startResult.emitHeight);
        
        // Close animation ends
        KeyboardHeightFilter.FilterResult endResult = filter.filterOnEnd(false, 0, PORTRAIT_KEY);
        assertTrue(endResult.shouldEmit);
        assertEquals(0, endResult.emitHeight);
    }

    @Test
    public void testRapidAbortIsCapped() {
        KeyboardHeightFilter filter = new KeyboardHeightFilter();
        filter.setEventMode(Keyboard.EventMode.LAST_KNOWN);
        
        // Establish resting height via successful animation
        filter.onPrepareAnimation();
        filter.filterOnStart(true, 941, PORTRAIT_KEY);
        filter.filterOnEnd(true, 941, PORTRAIT_KEY);
        
        filter.onPrepareAnimation();
        
        // OS gets confused and fires layout pass mid-abort BEFORE onStart
        KeyboardHeightFilter.FilterResult earlyApply = filter.filterOnApplyWindowInsets(true, 1034, PORTRAIT_KEY);
        assertFalse(earlyApply.shouldEmit); // Suppressed because isAnimating is true!
        
        // Rapid abort sends spurious 1034px onStart
        KeyboardHeightFilter.FilterResult startResult = filter.filterOnStart(true, 1034, PORTRAIT_KEY);
        
        // It MUST cap downwards! The spurious 1034 is crushed to the verified 941 ceiling!
        assertFalse(startResult.shouldEmit); // 941 == 941
        assertEquals(941, startResult.emitHeight);
        
        // Animation ends with 1034px
        KeyboardHeightFilter.FilterResult endResult = filter.filterOnEnd(true, 1034, PORTRAIT_KEY);
        assertTrue(endResult.shouldEmit);
        assertEquals(1034, endResult.emitHeight);
    }

    @Test
    public void testSuggestionBarLegitimateGrowth() {
        KeyboardHeightFilter filter = new KeyboardHeightFilter();
        filter.setEventMode(Keyboard.EventMode.LAST_KNOWN);
        
        // Establish resting height
        filter.onPrepareAnimation();
        filter.filterOnStart(true, 941, PORTRAIT_KEY);
        filter.filterOnEnd(true, 941, PORTRAIT_KEY);
        
        // Suggestion bar opens (no animation, OS layout change to 1000)
        // Notice we do NOT call onPrepareAnimation() here
        KeyboardHeightFilter.FilterResult applyResult = filter.filterOnApplyWindowInsets(true, 1000, PORTRAIT_KEY);
        
        // It must NOT be capped! Legitimate growth must be allowed to pass through
        assertTrue(applyResult.shouldEmit);
        assertEquals(1000, applyResult.emitHeight);
    }
    
    @Test
    public void testKeyboardDidShowAlwaysFires() {
        KeyboardHeightFilter filter = new KeyboardHeightFilter();
        filter.setEventMode(Keyboard.EventMode.LAST_KNOWN);
        
        filter.onPrepareAnimation();
        filter.filterOnStart(true, 941, PORTRAIT_KEY); // emitHeight = 941, lastImeHeight = 941
        
        // onEnd fires with the EXACT same height
        KeyboardHeightFilter.FilterResult endResult = filter.filterOnEnd(true, 941, PORTRAIT_KEY);
        
        // It must return shouldEmit = true to trigger DID_SHOW
        assertTrue(endResult.shouldEmit);
    }

    @Test
    public void testApplyWindowInsetsWhenKeyboardHidden() {
        KeyboardHeightFilter filter = new KeyboardHeightFilter();
        
        // First open it
        filter.filterOnApplyWindowInsets(true, 941, PORTRAIT_KEY);
        
        // OS hides keyboard abruptly outside of an animation
        KeyboardHeightFilter.FilterResult hideResult = filter.filterOnApplyWindowInsets(false, 0, PORTRAIT_KEY);
        
        assertFalse(hideResult.shouldEmit); // should Emit is false because onApplyWindowInsets does not force emit on hide
        assertEquals(0, hideResult.emitHeight);
    }

    @Test
    public void testDefaultModeDoesNotCapHeights() {
        KeyboardHeightFilter filter = new KeyboardHeightFilter();
        filter.setEventMode(Keyboard.EventMode.DEFAULT);
        
        // Establish resting height
        filter.onPrepareAnimation();
        filter.filterOnStart(true, 941, PORTRAIT_KEY);
        filter.filterOnEnd(true, 941, PORTRAIT_KEY);
        
        filter.onPrepareAnimation();
        
        // Rapid abort sends spurious 1034px onStart
        KeyboardHeightFilter.FilterResult startResult = filter.filterOnStart(true, 1034, PORTRAIT_KEY);
        
        // Because mode is DEFAULT, it MUST NOT cap!
        assertTrue(startResult.shouldEmit); // 1034 != 941
        assertEquals(1034, startResult.emitHeight);
    }
    @Test
    public void testRapidAbortFromZeroState() {
        KeyboardHeightFilter filter = new KeyboardHeightFilter();
        filter.setEventMode(Keyboard.EventMode.LAST_KNOWN);
        
        // 1. Initial Open (Pure animation lifecycle, no resting state first)
        filter.onPrepareAnimation();
        filter.filterOnStart(true, 369, PORTRAIT_KEY);
        filter.filterOnEnd(true, 369, PORTRAIT_KEY);
        
        // 2. Rapid Abort close
        filter.onPrepareAnimation();
        filter.filterOnStart(false, 0, PORTRAIT_KEY);
        filter.filterOnEnd(false, 0, PORTRAIT_KEY);
        
        // 3. Rapid Abort open (Taller height due to OS glitch)
        filter.onPrepareAnimation();
        KeyboardHeightFilter.FilterResult startResult = filter.filterOnStart(true, 405, PORTRAIT_KEY);
        
        // It MUST cap downwards to the verified ceiling of 369.
        assertTrue(startResult.shouldEmit); // 369 != 0
        assertEquals(369, startResult.emitHeight);
    }
    
    @Test
    public void testRotationLearnsNewCeiling() {
        KeyboardHeightFilter filter = new KeyboardHeightFilter();
        filter.setEventMode(Keyboard.EventMode.LAST_KNOWN);
        
        // 1. Open in Landscape (short)
        filter.onPrepareAnimation();
        filter.filterOnStart(true, 224, LANDSCAPE_KEY);
        filter.filterOnEnd(true, 224, LANDSCAPE_KEY);
        
        // 2. Rotate to Portrait
        // OS sends showing=false layout pass outside of an animation
        filter.filterOnApplyWindowInsets(false, 0, PORTRAIT_KEY);
        
        // 3. Open in Portrait (tall)
        filter.onPrepareAnimation();
        KeyboardHeightFilter.FilterResult startResult = filter.filterOnStart(true, 369, PORTRAIT_KEY);
        filter.filterOnEnd(true, 369, PORTRAIT_KEY);
        
        // It must NOT cap to 224! It should allow 369 because the map uses the PORTRAIT_KEY key.
        assertTrue(startResult.shouldEmit);
        assertEquals(369, startResult.emitHeight);
        
        // 4. Rotate BACK to landscape
        filter.filterOnApplyWindowInsets(false, 0, LANDSCAPE_KEY);
        
        // 5. Glitched open in landscape (taller than 224)
        filter.onPrepareAnimation();
        KeyboardHeightFilter.FilterResult glitchResult = filter.filterOnStart(true, 300, LANDSCAPE_KEY);
        
        // Downward clamping is active! It should emit 224!
        assertTrue(glitchResult.shouldEmit); // 224 != 0
        assertEquals(224, glitchResult.emitHeight);
    }
    
    @Test
    public void testRotationPoisoning() {
        KeyboardHeightFilter filter = new KeyboardHeightFilter();
        filter.setEventMode(Keyboard.EventMode.LAST_KNOWN);
        
        // 1. Establish Portrait
        filter.onPrepareAnimation();
        filter.filterOnStart(true, 369, PORTRAIT_KEY);
        filter.filterOnEnd(true, 369, PORTRAIT_KEY);
        
        // 2. Rotate to Landscape (transitional layout pass showing=true, height=0)
        filter.filterOnApplyWindowInsets(true, 0, LANDSCAPE_KEY);
        
        // 3. Open Landscape
        filter.onPrepareAnimation();
        KeyboardHeightFilter.FilterResult startResult = filter.filterOnStart(true, 224, LANDSCAPE_KEY);
        KeyboardHeightFilter.FilterResult endResult = filter.filterOnEnd(true, 224, LANDSCAPE_KEY);
        
        // Assert the BUG: we expect it to FAIL because it wrongly capped to 0!
        // To make it a RED test, we assert what SHOULD happen (it should emit 224).
        assertEquals(224, startResult.emitHeight);
        assertEquals(224, endResult.emitHeight);
    }
    
    @Test
    public void testPhantomRotationIsClamped() {
        KeyboardHeightFilter filter = new KeyboardHeightFilter();
        filter.setEventMode(Keyboard.EventMode.LAST_KNOWN);
        
        // 1. Establish Portrait
        filter.onPrepareAnimation();
        filter.filterOnStart(true, 369, PORTRAIT_KEY);
        filter.filterOnEnd(true, 369, PORTRAIT_KEY);
        
        // 2. Open Landscape
        filter.onPrepareAnimation();
        filter.filterOnStart(true, 224, LANDSCAPE_KEY);
        filter.filterOnEnd(true, 224, LANDSCAPE_KEY);
        
        // 3. Rotate back to Portrait. OS fires 650 phantom glitch!
        // Because the screenKey changed from LANDSCAPE to PORTRAIT, justEndedAnimation must NOT trust this.
        KeyboardHeightFilter.FilterResult glitchResult = filter.filterOnApplyWindowInsets(true, 650, PORTRAIT_KEY);
        
        // The glitch must be clamped downwards to the established Portrait cache (369)!
        assertEquals(369, glitchResult.emitHeight);
        // It must NOT emit to JS, because it's a transient clamped hallucination.
        assertFalse(glitchResult.shouldEmit);
    }
    
    @Test
    public void testOnEndPoisoning() {
        KeyboardHeightFilter filter = new KeyboardHeightFilter();
        filter.setEventMode(Keyboard.EventMode.LAST_KNOWN);
        
        // 1. Establish Portrait
        filter.onPrepareAnimation();
        filter.filterOnStart(true, 369, PORTRAIT_KEY);
        filter.filterOnEnd(true, 369, PORTRAIT_KEY);
        
        // 2. Glitch: OS sends onEnd with showing=true and height=0
        filter.onPrepareAnimation();
        filter.filterOnEnd(true, 0, PORTRAIT_KEY);
        
        // 3. Open Portrait again
        filter.onPrepareAnimation();
        KeyboardHeightFilter.FilterResult startResult = filter.filterOnStart(true, 369, PORTRAIT_KEY);
        
        // It should emit 369 because the glitchy 0 onEnd reset lastImeHeight, but the stable 369 cache protected the layout from being zeroed out.
        assertTrue(startResult.shouldEmit);
        assertEquals(369, startResult.emitHeight);
    }
    
    @Test
    public void testCalculateImeHeightAccountsForNavBar() {
        KeyboardHeightFilter filter = new KeyboardHeightFilter();
        
        // Edge-to-edge mode (ignoreNavBar = true)
        // Webview draws under nav bar, so it needs the full raw height to escape the keyboard
        assertEquals(900, filter.calculateImeHeight(900, 100, true));
        
        // Standard mode (ignoreNavBar = false)
        // Webview stops at nav bar, so the keyboard overlaps it by exactly (raw - navBar)
        assertEquals(800, filter.calculateImeHeight(900, 100, false));
        
        // Safety check for negative values
        assertEquals(0, filter.calculateImeHeight(50, 100, false));
    }

    @Test
    public void testSwiftKeyDoubleKeyboardWillShow() {
        KeyboardHeightFilter filter = new KeyboardHeightFilter();
        filter.setEventMode(Keyboard.EventMode.LAST_KNOWN);
        
        // 1. Initial Open (Resting state is 316)
        filter.onPrepareAnimation();
        filter.filterOnStart(true, 316, PORTRAIT_KEY);
        filter.filterOnEnd(true, 316, PORTRAIT_KEY);
        filter.filterOnApplyWindowInsets(true, 316, PORTRAIT_KEY);
        
        // 2. Background tap close
        filter.onPrepareAnimation();
        filter.filterOnApplyWindowInsets(false, 0, PORTRAIT_KEY);
        filter.filterOnStart(false, 0, PORTRAIT_KEY);
        filter.filterOnEnd(false, 0, PORTRAIT_KEY);
        
        // 3. Quick retrigger open (OS misses suggestion bar, reports 272)
        filter.onPrepareAnimation();
        filter.filterOnApplyWindowInsets(true, 272, PORTRAIT_KEY);
        KeyboardHeightFilter.FilterResult startResult = filter.filterOnStart(true, 272, PORTRAIT_KEY);
        KeyboardHeightFilter.FilterResult endResult = filter.filterOnEnd(true, 272, PORTRAIT_KEY);
        
        // 4. OS catches up, reports 316 in ApplyWindowInsets
        KeyboardHeightFilter.FilterResult applyResult = filter.filterOnApplyWindowInsets(true, 316, PORTRAIT_KEY);
        
        // Assert the FIX: It should emit 316 to avoid the double trigger shimmering!
        assertEquals(316, startResult.emitHeight); 
    }

    @Test
    public void testPostAnimationLayoutCorrection() {
        KeyboardHeightFilter filter = new KeyboardHeightFilter();
        filter.setEventMode(Keyboard.EventMode.LAST_KNOWN);
        
        // 1. Hallucinated OS animation of 405
        filter.onPrepareAnimation();
        filter.filterOnStart(true, 405, PORTRAIT_KEY);
        filter.filterOnEnd(true, 405, PORTRAIT_KEY);
        
        // 2. Immediate layout correction (justEndedAnimation = true)
        filter.filterOnApplyWindowInsets(true, 369, PORTRAIT_KEY);
        
        // 3. User closes
        filter.onPrepareAnimation();
        filter.filterOnStart(false, 0, PORTRAIT_KEY);
        filter.filterOnEnd(false, 0, PORTRAIT_KEY);
        
        // 4. Next open
        filter.onPrepareAnimation();
        KeyboardHeightFilter.FilterResult startResult = filter.filterOnStart(true, 405, PORTRAIT_KEY);
        
        // The cache MUST have elevated the 369 static pass to Verified, clamping the 405 to 369!
        assertEquals(369, startResult.emitHeight);
    }

    @Test
    public void testSplitScreenVerticalResizeLearnsNewCeiling() {
        KeyboardHeightFilter filter = new KeyboardHeightFilter();
        filter.setEventMode(Keyboard.EventMode.LAST_KNOWN);
        
        // 1. Initial Open in Full Screen (1080x1920, Keyboard is 941px)
        filter.onPrepareAnimation();
        filter.filterOnStart(true, 941, "1080x1920|default");
        filter.filterOnEnd(true, 941, "1080x1920|default");
        filter.filterOnApplyWindowInsets(true, 941, "1080x1920|default");
        
        // 2. User enters split screen, halving the vertical space (1080x960)
        filter.onPrepareAnimation();
        KeyboardHeightFilter.FilterResult startResult = filter.filterOnStart(true, 400, "1080x960|default");
        
        assertEquals(400, startResult.emitHeight);
        
        filter.filterOnEnd(true, 400, "1080x960|default");
        
        // 3. Spurious glitch on the split screen
        filter.onPrepareAnimation();
        KeyboardHeightFilter.FilterResult glitchResult = filter.filterOnStart(true, 1034, "1080x960|default");
        
        // It should emit 400 because downward clamping is active!
        // The spurious glitch of 1034 is capped back to the verified 400 height of the split screen.
        assertEquals(400, glitchResult.emitHeight);
    }

    @Test
    public void testKeyboardSwitchGboardToSwiftKey() {
        KeyboardHeightFilter filter = new KeyboardHeightFilter();
        filter.setEventMode(Keyboard.EventMode.LAST_KNOWN);
        
        // 1. Initial Open with SwiftKey (316)
        filter.onPrepareAnimation();
        filter.filterOnApplyWindowInsets(true, 316, "1080x1920|swiftkey");
        filter.filterOnStart(true, 316, "1080x1920|swiftkey");
        filter.filterOnEnd(true, 316, "1080x1920|swiftkey");
        
        // 2. Close
        filter.onPrepareAnimation();
        filter.filterOnApplyWindowInsets(false, 0, "1080x1920|swiftkey");
        filter.filterOnStart(false, 0, "1080x1920|swiftkey");
        filter.filterOnEnd(false, 0, "1080x1920|swiftkey");
        
        // 3. User switches to Gboard (358) and opens
        // Because the map key changed to "1080x1920|gboard", it yields a cache miss!
        filter.onPrepareAnimation();
        filter.filterOnApplyWindowInsets(true, 358, "1080x1920|gboard");
        KeyboardHeightFilter.FilterResult startResult = filter.filterOnStart(true, 358, "1080x1920|gboard");
        
        // Assert: It MUST emit 358 because the physical keyboard has changed!
        assertEquals(358, startResult.emitHeight);
    }

    @Test
    public void testSwiftKeyMissingSuggestionBar() {
        KeyboardHeightFilter filter = new KeyboardHeightFilter();
        filter.setEventMode(Keyboard.EventMode.LAST_KNOWN);
        
        // 1. Initial stable open (316)
        filter.onPrepareAnimation();
        filter.filterOnStart(true, 316, "1080x1920|swiftkey");
        filter.filterOnEnd(true, 316, "1080x1920|swiftkey");
        
        // 2. Keyboard hides
        filter.onPrepareAnimation();
        filter.filterOnEnd(false, 0, "1080x1920|swiftkey");
        
        // 3. Keyboard opens again, but OS reports missing suggestion bar (272)
        filter.onPrepareAnimation();
        KeyboardHeightFilter.FilterResult startResult = filter.filterOnStart(true, 272, "1080x1920|swiftkey");
        
        // Assert: It MUST clamp upwards to 316 to prevent the webview from crushing!
        assertEquals(316, startResult.emitHeight);
    }
    
    @Test
    public void testPhantomRotationGlitch() {
        KeyboardHeightFilter filter = new KeyboardHeightFilter();
        filter.setEventMode(Keyboard.EventMode.LAST_KNOWN);
        
        // 1. Phantom OS layout pass with garbage bounds (650) outside of an animation
        filter.filterOnApplyWindowInsets(true, 650, "1080x1920|default");
        filter.filterOnApplyWindowInsets(false, 0, "1080x1920|default");
        
        // 2. User opens the keyboard for real (369)
        filter.onPrepareAnimation();
        KeyboardHeightFilter.FilterResult startResult = filter.filterOnStart(true, 369, "1080x1920|default");
        
        // Assert: It MUST emit 369 because the phantom 650 should NOT have poisoned the cache!
        assertEquals(369, startResult.emitHeight);
    }

    @Test
    public void testEdgeToEdgeDetection() {
        // 1. Lincoln's App (navigationBarInsets = 'ignore')
        // If the developer explicitly ignores the insets, it ALWAYS returns true (edge-to-edge), regardless of SDK version.
        assertTrue(Keyboard.isWindowEdgeToEdge("ignore", 33, 33));
        assertTrue(Keyboard.isWindowEdgeToEdge("ignore", 35, 35));

        // 2. Explicit 'subtract' App
        // The developer explicitly forces subtraction.
        assertFalse(Keyboard.isWindowEdgeToEdge("subtract", 36, 36));

        // 3. Standard Capacitor App on Android 13 (SDK 33)
        // The developer has omitted the config, defaulting to "auto".
        // Since it's older than API 35, it is NOT forced into edge-to-edge.
        assertFalse(Keyboard.isWindowEdgeToEdge("auto", 33, 33));

        // 4. Standard Capacitor App on Android 16 (SDK 36)
        // The developer has omitted the config, defaulting to "auto".
        // Since both the device OS and the target SDK are >= 35, it IS forced into edge-to-edge.
        assertTrue(Keyboard.isWindowEdgeToEdge("auto", 36, 35));

        // 5. Legacy App on Android 16 (SDK 36, Target 33)
        // If an old app is installed on a new device, it is NOT forced into edge-to-edge.
        assertFalse(Keyboard.isWindowEdgeToEdge("auto", 36, 33));
    }
}
