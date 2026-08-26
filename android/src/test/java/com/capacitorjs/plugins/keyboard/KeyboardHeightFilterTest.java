package com.capacitorjs.plugins.keyboard;

import org.junit.Test;
import static org.junit.Assert.*;

public class KeyboardHeightFilterTest {

    private static final int PORTRAIT_WIDTH = 1080;
    private static final int LANDSCAPE_WIDTH = 1920;

    @Test
    public void testNormalOpen() {
        KeyboardHeightFilter filter = new KeyboardHeightFilter();
        filter.setEventMode(Keyboard.EventMode.LAST_KNOWN);
        
        filter.onPrepareAnimation();
        
        KeyboardHeightFilter.FilterResult startResult = filter.filterOnStart(true, 941, PORTRAIT_WIDTH);
        assertTrue(startResult.shouldEmit);
        assertEquals(941, startResult.emitHeight);
        
        KeyboardHeightFilter.FilterResult endResult = filter.filterOnEnd(true, 941, PORTRAIT_WIDTH);
        assertTrue(endResult.shouldEmit); // DID_SHOW must fire unconditionally on animation end
        assertEquals(941, endResult.emitHeight);
        
        // Stabilize after animation
        KeyboardHeightFilter.FilterResult applyResult = filter.filterOnApplyWindowInsets(true, 941, PORTRAIT_WIDTH);
        assertFalse(applyResult.shouldEmit);
    }
    
    @Test
    public void testNormalClose() {
        KeyboardHeightFilter filter = new KeyboardHeightFilter();
        filter.setEventMode(Keyboard.EventMode.LAST_KNOWN);
        
        // Open first to establish height
        filter.filterOnApplyWindowInsets(true, 941, PORTRAIT_WIDTH);
        
        filter.onPrepareAnimation();
        
        // Close animation starts
        KeyboardHeightFilter.FilterResult startResult = filter.filterOnStart(false, 0, PORTRAIT_WIDTH);
        assertTrue(startResult.shouldEmit);
        assertEquals(0, startResult.emitHeight);
        
        // Close animation ends
        KeyboardHeightFilter.FilterResult endResult = filter.filterOnEnd(false, 0, PORTRAIT_WIDTH);
        assertTrue(endResult.shouldEmit);
        assertEquals(0, endResult.emitHeight);
    }

    @Test
    public void testRapidAbortIsCapped() {
        KeyboardHeightFilter filter = new KeyboardHeightFilter();
        filter.setEventMode(Keyboard.EventMode.LAST_KNOWN);
        
        // Establish resting height
        filter.filterOnApplyWindowInsets(true, 941, PORTRAIT_WIDTH);
        
        filter.onPrepareAnimation();
        
        // OS gets confused and fires layout pass mid-abort BEFORE onStart
        KeyboardHeightFilter.FilterResult earlyApply = filter.filterOnApplyWindowInsets(true, 1034, PORTRAIT_WIDTH);
        assertFalse(earlyApply.shouldEmit); // Suppressed because isAnimating is true!
        
        // Rapid abort sends spurious 1034px onStart
        KeyboardHeightFilter.FilterResult startResult = filter.filterOnStart(true, 1034, PORTRAIT_WIDTH);
        
        // The emitted height MUST be capped to the previous stable height (941)
        assertFalse(startResult.shouldEmit); // 941 == 941 (previous lastImeHeight), so WILL_SHOW is suppressed to avoid redundant dispatch
        assertEquals(941, startResult.emitHeight);
        
        // Animation ends with spurious 1034px
        KeyboardHeightFilter.FilterResult endResult = filter.filterOnEnd(true, 1034, PORTRAIT_WIDTH);
        assertTrue(endResult.shouldEmit); // DID_SHOW unconditionally fires because animation ended
        assertEquals(941, endResult.emitHeight); // Still capped to 941
    }

    @Test
    public void testSuggestionBarLegitimateGrowth() {
        KeyboardHeightFilter filter = new KeyboardHeightFilter();
        filter.setEventMode(Keyboard.EventMode.LAST_KNOWN);
        
        // Establish resting height
        filter.filterOnApplyWindowInsets(true, 941, PORTRAIT_WIDTH);
        
        // Suggestion bar opens (no animation, OS layout change to 1000)
        // Notice we do NOT call onPrepareAnimation() here
        KeyboardHeightFilter.FilterResult applyResult = filter.filterOnApplyWindowInsets(true, 1000, PORTRAIT_WIDTH);
        
        // It must NOT be capped! Legitimate growth must be allowed to pass through
        assertTrue(applyResult.shouldEmit);
        assertEquals(1000, applyResult.emitHeight);
    }
    
    @Test
    public void testKeyboardDidShowAlwaysFires() {
        KeyboardHeightFilter filter = new KeyboardHeightFilter();
        filter.setEventMode(Keyboard.EventMode.LAST_KNOWN);
        
        filter.onPrepareAnimation();
        filter.filterOnStart(true, 941, PORTRAIT_WIDTH); // emitHeight = 941, lastImeHeight = 941
        
        // onEnd fires with the EXACT same height
        KeyboardHeightFilter.FilterResult endResult = filter.filterOnEnd(true, 941, PORTRAIT_WIDTH);
        
        // It must return shouldEmit = true to trigger DID_SHOW
        assertTrue(endResult.shouldEmit);
    }

    @Test
    public void testApplyWindowInsetsWhenKeyboardHidden() {
        KeyboardHeightFilter filter = new KeyboardHeightFilter();
        
        // First open it
        filter.filterOnApplyWindowInsets(true, 941, PORTRAIT_WIDTH);
        
        // OS hides keyboard abruptly outside of an animation
        KeyboardHeightFilter.FilterResult hideResult = filter.filterOnApplyWindowInsets(false, 0, PORTRAIT_WIDTH);
        
        assertFalse(hideResult.shouldEmit); // should Emit is false because onApplyWindowInsets does not force emit on hide
        assertEquals(0, hideResult.emitHeight);
    }

    @Test
    public void testDefaultModeDoesNotCapHeights() {
        KeyboardHeightFilter filter = new KeyboardHeightFilter();
        filter.setEventMode(Keyboard.EventMode.DEFAULT);
        
        // Establish resting height
        filter.filterOnApplyWindowInsets(true, 941, PORTRAIT_WIDTH);
        
        filter.onPrepareAnimation();
        
        // Rapid abort sends spurious 1034px onStart
        KeyboardHeightFilter.FilterResult startResult = filter.filterOnStart(true, 1034, PORTRAIT_WIDTH);
        
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
        filter.filterOnStart(true, 369, PORTRAIT_WIDTH);
        filter.filterOnEnd(true, 369, PORTRAIT_WIDTH);
        
        // 2. Rapid Abort close
        filter.onPrepareAnimation();
        filter.filterOnStart(false, 0, PORTRAIT_WIDTH);
        filter.filterOnEnd(false, 0, PORTRAIT_WIDTH);
        
        // 3. Rapid Abort open (Taller height due to OS glitch)
        filter.onPrepareAnimation();
        KeyboardHeightFilter.FilterResult startResult = filter.filterOnStart(true, 405, PORTRAIT_WIDTH);
        
        // The emitted height MUST be capped to the previous stable height (369)
        assertTrue(startResult.shouldEmit); // 369 != 0 (previous lastImeHeight was 0 from close)
        assertEquals(369, startResult.emitHeight);
    }
    
    @Test
    public void testRotationLearnsNewCeiling() {
        KeyboardHeightFilter filter = new KeyboardHeightFilter();
        filter.setEventMode(Keyboard.EventMode.LAST_KNOWN);
        
        // 1. Open in Landscape (short)
        filter.onPrepareAnimation();
        filter.filterOnStart(true, 224, LANDSCAPE_WIDTH);
        filter.filterOnEnd(true, 224, LANDSCAPE_WIDTH);
        
        // 2. Rotate to Portrait
        // OS sends showing=false layout pass outside of an animation
        filter.filterOnApplyWindowInsets(false, 0, PORTRAIT_WIDTH);
        
        // 3. Open in Portrait (tall)
        filter.onPrepareAnimation();
        KeyboardHeightFilter.FilterResult startResult = filter.filterOnStart(true, 369, PORTRAIT_WIDTH);
        filter.filterOnEnd(true, 369, PORTRAIT_WIDTH);
        
        // It must NOT cap to 224! It should allow 369 because the map uses the PORTRAIT_WIDTH key.
        assertTrue(startResult.shouldEmit);
        assertEquals(369, startResult.emitHeight);
        
        // 4. Rotate BACK to landscape
        filter.filterOnApplyWindowInsets(false, 0, LANDSCAPE_WIDTH);
        
        // 5. Glitched open in landscape (taller than 224)
        filter.onPrepareAnimation();
        KeyboardHeightFilter.FilterResult glitchResult = filter.filterOnStart(true, 300, LANDSCAPE_WIDTH);
        
        // It SHOULD cap to 224 because the map remembers 224 for LANDSCAPE_WIDTH!
        assertEquals(224, glitchResult.emitHeight);
    }
    
    @Test
    public void testRotationPoisoning() {
        KeyboardHeightFilter filter = new KeyboardHeightFilter();
        filter.setEventMode(Keyboard.EventMode.LAST_KNOWN);
        
        // 1. Establish Portrait
        filter.onPrepareAnimation();
        filter.filterOnStart(true, 369, PORTRAIT_WIDTH);
        filter.filterOnEnd(true, 369, PORTRAIT_WIDTH);
        
        // 2. Rotate to Landscape (transitional layout pass showing=true, height=0)
        filter.filterOnApplyWindowInsets(true, 0, LANDSCAPE_WIDTH);
        
        // 3. Open Landscape
        filter.onPrepareAnimation();
        KeyboardHeightFilter.FilterResult startResult = filter.filterOnStart(true, 224, LANDSCAPE_WIDTH);
        KeyboardHeightFilter.FilterResult endResult = filter.filterOnEnd(true, 224, LANDSCAPE_WIDTH);
        
        // Assert the BUG: we expect it to FAIL because it wrongly capped to 0!
        // To make it a RED test, we assert what SHOULD happen (it should emit 224).
        assertEquals(224, startResult.emitHeight);
        assertEquals(224, endResult.emitHeight);
    }
    
    @Test
    public void testOnEndPoisoning() {
        KeyboardHeightFilter filter = new KeyboardHeightFilter();
        filter.setEventMode(Keyboard.EventMode.LAST_KNOWN);
        
        // 1. Establish Portrait
        filter.onPrepareAnimation();
        filter.filterOnStart(true, 369, PORTRAIT_WIDTH);
        filter.filterOnEnd(true, 369, PORTRAIT_WIDTH);
        
        // 2. Glitch: OS sends onEnd with showing=true and height=0
        filter.onPrepareAnimation();
        filter.filterOnEnd(true, 0, PORTRAIT_WIDTH);
        
        // 3. Open Portrait again
        filter.onPrepareAnimation();
        KeyboardHeightFilter.FilterResult startResult = filter.filterOnStart(true, 369, PORTRAIT_WIDTH);
        
        // It should still be allowed, because the 0 didn't overwrite the 369 ceiling!
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
}
