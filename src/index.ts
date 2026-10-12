import { Capacitor, registerPlugin } from '@capacitor/core';

import type { KeyboardPlugin } from './definitions';

const Keyboard = registerPlugin<KeyboardPlugin>('Keyboard');

if (Capacitor.isNativePlatform() && Capacitor.getPlatform() === 'android') {
  let lastContext: string | null = null;

  const inputContextOf = (el: HTMLElement): string | null => {
    // Only care about actual text-receiving elements
    const isInput = el instanceof HTMLInputElement || el instanceof HTMLTextAreaElement || el.isContentEditable;

    if (!isInput) {
      return null;
    }

    const inputMode = el.getAttribute('inputmode')?.toLowerCase();
    if (inputMode) {
      return inputMode;
    }
    if (el instanceof HTMLTextAreaElement) {
      return 'text';
    }
    if (el instanceof HTMLInputElement) {
      switch (el.type) {
        case 'number':
          return 'numeric';
        case 'tel':
          return 'tel';
        case 'email':
          return 'email';
        case 'url':
          return 'url';
        case 'search':
          return 'search';
        case 'password':
          return 'password';
        default:
          return 'text';
      }
    }
    return el.isContentEditable ? 'text' : null;
  };

  if (typeof document !== 'undefined') {
    document.addEventListener(
      'focusin',
      (event) => {
        // Use composedPath() to pierce open ShadowDOM (e.g., Ionic web components)
        const target = event.composedPath?.()[0] || event.target;

        if (target instanceof HTMLElement) {
          const context = inputContextOf(target);

          // Strictly filter out non-inputs (buttons, links) to prevent UI glitches,
          // and deduplicate bridge traffic if the context hasn't changed.
          if (context !== null && context !== lastContext) {
            lastContext = context;
            Keyboard.setInputContext({ context }).catch(() => undefined);
          }
        }
      },
      true,
    );
  }
}

export * from './definitions';
export { Keyboard };
