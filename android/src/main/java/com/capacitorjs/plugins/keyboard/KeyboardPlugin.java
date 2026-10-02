package com.capacitorjs.plugins.keyboard;

import android.os.Handler;
import android.os.Looper;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

@CapacitorPlugin(name = "Keyboard")
public class KeyboardPlugin extends Plugin {

    private Keyboard implementation;

    @Override
    public void load() {
        execute(() -> {
            boolean resizeOnFullScreen = getConfig().getBoolean("resizeOnFullScreen", false);
            String navigationBarInsets = getConfig().getString("navigationBarInsets", "auto");
            String eventMode = getConfig().getString("eventMode", "DEFAULT");
            implementation = new Keyboard(getBridge(), resizeOnFullScreen);
            implementation.setEventMode(eventMode);
            implementation.setNavigationBarInsets(navigationBarInsets);

            implementation.setKeyboardEventListener(this::onKeyboardEvent);
        });
    }

    @PluginMethod
    public void show(final PluginCall call) {
        execute(() ->
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                implementation.show();
                call.resolve();
            }, 350)
        );
    }

    @PluginMethod
    public void hide(final PluginCall call) {
        execute(() -> {
            if (!implementation.hide()) {
                call.reject("Can't close keyboard, not currently focused");
            } else {
                call.resolve();
            }
        });
    }

    /**
     * Web side reports the focused element's input context (e.g. "text", "numeric", "email").
     * IMEs use different layouts (and heights) per input type, so the height cache is keyed by it.
     */
    @PluginMethod
    public void setInputContext(PluginCall call) {
        String context = call.getString("context", "");
        execute(() -> {
            if (implementation != null) {
                implementation.setInputContext(context);
            }
            call.resolve();
        });
    }

    @PluginMethod
    public void setAccessoryBarVisible(PluginCall call) {
        call.unimplemented();
    }

    @PluginMethod
    public void setStyle(PluginCall call) {
        call.unimplemented();
    }

    @PluginMethod
    public void setResizeMode(PluginCall call) {
        call.unimplemented();
    }

    @PluginMethod
    public void getResizeMode(PluginCall call) {
        call.unimplemented();
    }

    @PluginMethod
    public void setScroll(PluginCall call) {
        call.unimplemented();
    }

    void onKeyboardEvent(String event, int size) {
        JSObject kbData = new JSObject();
        switch (event) {
            case Keyboard.EVENT_KB_WILL_SHOW:
            case Keyboard.EVENT_KB_DID_SHOW:
                String data = "{ 'keyboardHeight': " + size + " }";
                bridge.triggerWindowJSEvent(event, data);
                kbData.put("keyboardHeight", size);
                notifyListeners(event, kbData);
                break;
            case Keyboard.EVENT_KB_WILL_HIDE:
            case Keyboard.EVENT_KB_DID_HIDE:
                bridge.triggerWindowJSEvent(event);
                notifyListeners(event, kbData);
                break;
        }
    }

    @Override
    protected void handleOnDestroy() {
        implementation.setKeyboardEventListener(null);
    }
}
