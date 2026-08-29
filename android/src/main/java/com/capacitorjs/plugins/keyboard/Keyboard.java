package com.capacitorjs.plugins.keyboard;

import android.content.Context;
import android.util.Log;
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

    public enum EventMode {
        DEFAULT,
        LAST_KNOWN
    }

    private final KeyboardHeightFilter filter = new KeyboardHeightFilter();

    public void setNavigationBarInsets(String navigationBarInsets) {
        this.navigationBarInsets = navigationBarInsets;
    }

    public void setEventMode(String modeStr) {
        if (modeStr != null) {
            try {
                filter.setEventMode(EventMode.valueOf(modeStr.toUpperCase()));
            } catch (IllegalArgumentException e) {
                filter.setEventMode(EventMode.DEFAULT);
            }
        }
    }

    public void setKeyboardEventListener(@Nullable KeyboardEventListener keyboardEventListener) {
        this.keyboardEventListener = keyboardEventListener;
    }

    @Nullable
    private KeyboardEventListener keyboardEventListener;
    
    private String navigationBarInsets = "auto";

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
    private String getKeyboardId() {
        if (activity == null) return "unknown";
        try {
            return android.provider.Settings.Secure.getString(activity.getContentResolver(), android.provider.Settings.Secure.DEFAULT_INPUT_METHOD);
        } catch (Exception e) {
            return "unknown";
        }
    }

    static boolean isWindowEdgeToEdge(String navigationBarInsets, int sdkInt, int targetSdk) {
        if ("ignore".equalsIgnoreCase(navigationBarInsets)) {
            return true; // Never subtract (app always draws behind nav bar)
        } else if ("subtract".equalsIgnoreCase(navigationBarInsets)) {
            return false; // Always subtract (app does not draw behind nav bar)
        }
        
        // "auto" (default)
        // Starting in Android 15 (API 35), apps targeting SDK 35+ are forced into Edge-To-Edge by the OS.
        // We dynamically detect this so a single APK works perfectly on both Android 13 (subtracted) and Android 16 (forced edge-to-edge).
        if (sdkInt >= 35 && targetSdk >= 35) {
            return true;
        }
        return false;
    }

    private boolean isWindowEdgeToEdge() {
        int targetSdk = activity != null ? activity.getApplicationInfo().targetSdkVersion : 0;
        return isWindowEdgeToEdge(navigationBarInsets, Build.VERSION.SDK_INT, targetSdk);
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
            int rawImeHeight = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom;
            int navBarHeight = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom;
            boolean ignoreNavBar = resizeOnFullScreen || isWindowEdgeToEdge();
            int imeHeight = filter.calculateImeHeight(rawImeHeight, navBarHeight, ignoreNavBar);
            DisplayMetrics dm = activity.getResources().getDisplayMetrics();
            final float density = dm.density;

            int currentImeHeight = Math.round(imeHeight / density);
            String screenKey = getScreenKey();
            KeyboardHeightFilter.FilterResult result = filter.filterOnApplyWindowInsets(showingKeyboard, currentImeHeight, screenKey);
            Log.i("Capacitor/Keyboard", "onApplyWindowInsets: showing=" + showingKeyboard + " rawHeight=" + currentImeHeight + " emit=" + result.emitHeight + " shouldEmit=" + result.shouldEmit + " key=" + screenKey);

            if (result.shouldEmit && keyboardEventListener != null) {
                if (showingKeyboard) {
                    keyboardEventListener.onKeyboardEvent(EVENT_KB_WILL_SHOW, result.emitHeight);
                    keyboardEventListener.onKeyboardEvent(EVENT_KB_DID_SHOW, result.emitHeight);
                } else {
                    keyboardEventListener.onKeyboardEvent(EVENT_KB_WILL_HIDE, 0);
                    keyboardEventListener.onKeyboardEvent(EVENT_KB_DID_HIDE, 0);
                }
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
                    Log.i("Capacitor/Keyboard", "onPrepareAnimation");
                    filter.onPrepareAnimation();
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
                    WindowInsetsCompat insets = ViewCompat.getRootWindowInsets(rootView);
                    if (insets == null) return super.onStart(animation, bounds);
                    boolean showingKeyboard = insets.isVisible(WindowInsetsCompat.Type.ime());
                    int rawImeHeight = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom;
                    int navBarHeight = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom;
                    boolean ignoreNavBar = resizeOnFullScreen || isWindowEdgeToEdge();
                    int imeHeight = filter.calculateImeHeight(rawImeHeight, navBarHeight, ignoreNavBar);
                    DisplayMetrics dm = activity.getResources().getDisplayMetrics();
                    final float density = dm.density;

                    if (resizeOnFullScreen) {
                        possiblyResizeChildOfContent(showingKeyboard);
                    }

                    int currentImeHeight = Math.round(imeHeight / density);
                    String screenKey = getScreenKey();
                    KeyboardHeightFilter.FilterResult result = filter.filterOnStart(showingKeyboard, currentImeHeight, screenKey);
                    Log.i("Capacitor/Keyboard", "onStart: showing=" + showingKeyboard + " rawHeight=" + currentImeHeight + " emit=" + result.emitHeight + " shouldEmit=" + result.shouldEmit + " key=" + screenKey);

                    if (result.shouldEmit && keyboardEventListener != null) {
                        if (showingKeyboard) {
                            keyboardEventListener.onKeyboardEvent(EVENT_KB_WILL_SHOW, result.emitHeight);
                        } else {
                            keyboardEventListener.onKeyboardEvent(EVENT_KB_WILL_HIDE, 0);
                        }
                    }

                    return super.onStart(animation, bounds);
                }

                @Override
                public void onEnd(@NonNull WindowInsetsAnimationCompat animation) {
                    super.onEnd(animation);

                    WindowInsetsCompat insets = ViewCompat.getRootWindowInsets(rootView);
                    if (insets == null) return;
                    boolean showingKeyboard = insets.isVisible(WindowInsetsCompat.Type.ime());
                    int rawImeHeight = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom;
                    int navBarHeight = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom;
                    boolean ignoreNavBar = resizeOnFullScreen || isWindowEdgeToEdge();
                    int imeHeight = filter.calculateImeHeight(rawImeHeight, navBarHeight, ignoreNavBar);
                    DisplayMetrics dm = activity.getResources().getDisplayMetrics();
                    final float density = dm.density;

                    int currentImeHeight = Math.round(imeHeight / density);
                    String screenKey = getScreenKey();
                    KeyboardHeightFilter.FilterResult result = filter.filterOnEnd(showingKeyboard, currentImeHeight, screenKey);
                    Log.i("Capacitor/Keyboard", "onEnd: showing=" + showingKeyboard + " rawHeight=" + currentImeHeight + " emit=" + result.emitHeight + " shouldEmit=" + result.shouldEmit + " key=" + screenKey);

                    if (result.shouldEmit && keyboardEventListener != null) {
                        if (showingKeyboard) {
                            keyboardEventListener.onKeyboardEvent(EVENT_KB_DID_SHOW, result.emitHeight);
                        } else {
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

    private String getScreenKey() {
        DisplayMetrics dm = activity.getResources().getDisplayMetrics();
        return dm.widthPixels + "x" + dm.heightPixels + "|" + getKeyboardId();
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
