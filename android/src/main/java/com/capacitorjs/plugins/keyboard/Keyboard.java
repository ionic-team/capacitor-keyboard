package com.capacitorjs.plugins.keyboard;

import android.content.Context;
import android.graphics.Rect;
import android.os.Build;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.View;
import android.view.Window;
import android.view.inputmethod.InputMethodManager;
import android.widget.FrameLayout;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsAnimationCompat;
import androidx.core.view.WindowInsetsCompat;
import com.getcapacitor.Bridge;
import java.util.List;

public class Keyboard {

    interface KeyboardEventListener {
        void onKeyboardEvent(String event, int size);
    }

    private Bridge bridge;
    private AppCompatActivity activity;
    private View rootView;
    private int usableHeightPrevious;
    private FrameLayout.LayoutParams frameLayoutParams;
    private View mChildOfContent;
    private int lastImeHeight = 0;

    public enum EventMode {
        DEFAULT,
        LAST_KNOWN
    }

    private boolean isAnimating = false;
    private boolean justEndedAnimation = false;
    private int knownKeyboardHeight = 0;
    private EventMode eventMode = EventMode.DEFAULT;

    public void setEventMode(String modeStr) {
        if (modeStr != null) {
            try {
                this.eventMode = EventMode.valueOf(modeStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                this.eventMode = EventMode.DEFAULT;
            }
        }
    }

    public void setKeyboardEventListener(@Nullable KeyboardEventListener keyboardEventListener) {
        this.keyboardEventListener = keyboardEventListener;
    }

    @Nullable
    private KeyboardEventListener keyboardEventListener;

    static final String EVENT_KB_WILL_SHOW = "keyboardWillShow";
    static final String EVENT_KB_DID_SHOW = "keyboardDidShow";
    static final String EVENT_KB_WILL_HIDE = "keyboardWillHide";
    static final String EVENT_KB_DID_HIDE = "keyboardDidHide";

    // From android 15 on, we need access to the bridge to get the config to resize the keyboard properly.
    public Keyboard(Bridge bridge, boolean resizeOnFullScreen) {
        this(bridge.getActivity(), resizeOnFullScreen);
        this.bridge = bridge;
    }

    // We may want to deprecate this constructor in the future, but we are keeping it now to keep backward compatibility with cap 7
    public Keyboard(AppCompatActivity activity, boolean resizeOnFullScreen) {
        this.activity = activity;

        //http://stackoverflow.com/a/4737265/1091751 detect if keyboard is showing
        FrameLayout content = activity.getWindow().getDecorView().findViewById(android.R.id.content);
        rootView = content.getRootView();

        ViewCompat.setOnApplyWindowInsetsListener(content, (v, insets) -> {
            WindowInsetsCompat rootInsets = ViewCompat.getRootWindowInsets(rootView);
            if (rootInsets == null) return insets;

            boolean showingKeyboard = rootInsets.isVisible(WindowInsetsCompat.Type.ime());
            int imeHeight = rootInsets.getInsets(WindowInsetsCompat.Type.ime()).bottom;
            DisplayMetrics dm = activity.getResources().getDisplayMetrics();
            final float density = dm.density;

            if (showingKeyboard) {
                int currentImeHeight = Math.round(imeHeight / density);
                if (!isAnimating) {
                    int emitHeight = currentImeHeight;
                    if (eventMode == EventMode.LAST_KNOWN) {
                        if (knownKeyboardHeight == 0) {
                            knownKeyboardHeight = currentImeHeight;
                        } else if (justEndedAnimation) {
                            knownKeyboardHeight = currentImeHeight;
                        } else if (currentImeHeight > knownKeyboardHeight) {
                            emitHeight = knownKeyboardHeight;
                        } else if (currentImeHeight < knownKeyboardHeight) {
                            knownKeyboardHeight = currentImeHeight;
                        }
                    } else {
                        knownKeyboardHeight = currentImeHeight;
                    }

                    if (emitHeight != lastImeHeight && keyboardEventListener != null) {
                        lastImeHeight = emitHeight;
                        keyboardEventListener.onKeyboardEvent(EVENT_KB_WILL_SHOW, emitHeight);
                        keyboardEventListener.onKeyboardEvent(EVENT_KB_DID_SHOW, emitHeight);
                    }
                    justEndedAnimation = false;
                } else if (eventMode == EventMode.LAST_KNOWN) {
                    if (knownKeyboardHeight == 0) {
                        knownKeyboardHeight = currentImeHeight;
                    }
                }
            } else {
                lastImeHeight = 0;
                justEndedAnimation = false;
            }



            if (showingKeyboard && resizeOnFullScreen) {
                possiblyResizeChildOfContent(true);
            } else if (!showingKeyboard && resizeOnFullScreen) {
                possiblyResizeChildOfContent(false);
            }

            WindowInsetsCompat insetsToApply = insets;
            if (!resizeOnFullScreen) {
                insetsToApply = new WindowInsetsCompat.Builder(insets)
                    .setInsets(WindowInsetsCompat.Type.ime(), androidx.core.graphics.Insets.NONE)
                    .build();
            }

            return ViewCompat.onApplyWindowInsets(v, insetsToApply);
        });

        ViewCompat.setWindowInsetsAnimationCallback(
            rootView,
            new WindowInsetsAnimationCompat.Callback(WindowInsetsAnimationCompat.Callback.DISPATCH_MODE_STOP) {
                @Override
                public void onPrepare(@NonNull WindowInsetsAnimationCompat animation) {
                    isAnimating = true;
                    super.onPrepare(animation);
                }

                @NonNull
                @Override
                public WindowInsetsCompat onProgress(
                    @NonNull WindowInsetsCompat insets,
                    @NonNull List<WindowInsetsAnimationCompat> runningAnimations
                ) {
                    if (!resizeOnFullScreen) {
                        return new WindowInsetsCompat.Builder(insets)
                            .setInsets(WindowInsetsCompat.Type.ime(), androidx.core.graphics.Insets.NONE)
                            .build();
                    }
                    return insets;
                }

                @NonNull
                @Override
                public WindowInsetsAnimationCompat.BoundsCompat onStart(
                    @NonNull WindowInsetsAnimationCompat animation,
                    @NonNull WindowInsetsAnimationCompat.BoundsCompat bounds
                ) {
                    isAnimating = true;
                    justEndedAnimation = false;
                    WindowInsetsCompat insets = ViewCompat.getRootWindowInsets(rootView);
                    if (insets == null) return super.onStart(animation, bounds);
                    boolean showingKeyboard = insets.isVisible(WindowInsetsCompat.Type.ime());
                    int imeHeight = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom;
                    DisplayMetrics dm = activity.getResources().getDisplayMetrics();
                    final float density = dm.density;

                    if (resizeOnFullScreen) {
                        possiblyResizeChildOfContent(showingKeyboard);
                    }

                    if (showingKeyboard) {
                        int currentImeHeight = Math.round(imeHeight / density);
                        int emitHeight = currentImeHeight;
                        if (eventMode == EventMode.LAST_KNOWN && knownKeyboardHeight > 0 && currentImeHeight > knownKeyboardHeight) {
                            emitHeight = knownKeyboardHeight;
                        }

                        if (emitHeight != lastImeHeight) {
                            lastImeHeight = emitHeight;
                            keyboardEventListener.onKeyboardEvent(EVENT_KB_WILL_SHOW, lastImeHeight);
                        }
                    } else {
                        lastImeHeight = 0;
                        keyboardEventListener.onKeyboardEvent(EVENT_KB_WILL_HIDE, 0);
                    }



                    return super.onStart(animation, bounds);
                }

                @Override
                public void onEnd(@NonNull WindowInsetsAnimationCompat animation) {
                    super.onEnd(animation);
                    isAnimating = false;

                    WindowInsetsCompat insets = ViewCompat.getRootWindowInsets(rootView);
                    if (insets == null) return;
                    boolean showingKeyboard = insets.isVisible(WindowInsetsCompat.Type.ime());
                    int imeHeight = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom;
                    DisplayMetrics dm = activity.getResources().getDisplayMetrics();
                    final float density = dm.density;

                    if (showingKeyboard) {
                        justEndedAnimation = true;

                        int currentImeHeight = Math.round(imeHeight / density);
                        int emitHeight = currentImeHeight;
                        if (eventMode == EventMode.LAST_KNOWN && knownKeyboardHeight > 0 && currentImeHeight > knownKeyboardHeight) {
                            emitHeight = knownKeyboardHeight;
                        }

                        if (emitHeight != lastImeHeight) {
                            lastImeHeight = emitHeight;
                            if (keyboardEventListener != null) {
                                keyboardEventListener.onKeyboardEvent(EVENT_KB_DID_SHOW, lastImeHeight);
                            }
                        }

                        // Update the known keyboard height to the final settled height after animation.
                        // We ONLY allow it to shrink here to prevent spurious tall frames (like during rapid aborts) from corrupting the ceiling.
                        // If the keyboard genuinely grew, the subsequent onApplyWindowInsets layout pass will catch it via justEndedAnimation=true.
                        if (eventMode == EventMode.LAST_KNOWN) {
                            if (knownKeyboardHeight == 0 || currentImeHeight <= knownKeyboardHeight) {
                                knownKeyboardHeight = currentImeHeight;
                            }
                        }
                    } else {
                        justEndedAnimation = false;
                        lastImeHeight = 0;
                        if (keyboardEventListener != null) {
                            keyboardEventListener.onKeyboardEvent(EVENT_KB_DID_HIDE, 0);
                        }
                    }


                }
            }
        );

        mChildOfContent = content.getChildAt(0);
        frameLayoutParams = (FrameLayout.LayoutParams) mChildOfContent.getLayoutParams();
    }

    public void show() {
        ((InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE)).showSoftInput(activity.getCurrentFocus(), 0);
    }

    public boolean hide() {
        InputMethodManager inputManager = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
        View v = activity.getCurrentFocus();
        if (v == null) {
            return false;
        } else {
            inputManager.hideSoftInputFromWindow(v.getWindowToken(), InputMethodManager.HIDE_NOT_ALWAYS);
            return true;
        }
    }

    private void possiblyResizeChildOfContent(boolean keyboardShown) {
        if (isSystemBarsPluginPresent()) {
            // SystemBars handles the inset sizing for visible keyboards
            return;
        }

        int usableHeightNow = keyboardShown ? computeUsableHeight() : -1;
        if (usableHeightPrevious != usableHeightNow) {
            frameLayoutParams.height = usableHeightNow;
            mChildOfContent.requestLayout();
            usableHeightPrevious = usableHeightNow;
        }
    }

    private int computeUsableHeight() {
        Rect r = new Rect();
        mChildOfContent.getWindowVisibleDisplayFrame(r);
        return isOverlays() ? r.bottom : r.height();
    }

    private static boolean isSystemBarsPluginPresent() {
        try {
            Class.forName("com.getcapacitor.plugin.SystemBars");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    @SuppressWarnings("deprecation")
    private boolean isOverlays() {
        final Window window = activity.getWindow();
        return (
            (window.getDecorView().getSystemUiVisibility() & View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN) == View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        );
    }
}
