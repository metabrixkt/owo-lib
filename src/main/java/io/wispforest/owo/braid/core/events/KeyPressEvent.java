package io.wispforest.owo.braid.core.events;

import io.wispforest.owo.braid.core.KeyModifiers;

/// @param keyCode the physical SDL scancode, matching Minecraft's key bindings
/// @param nativeKeyCode the layout-dependent SDL keycode
public record KeyPressEvent(int keyCode, int nativeKeyCode, KeyModifiers modifiers) implements UserEvent {
    public KeyPressEvent(int keyCode, int nativeKeyCode, int modifiers) {
        this(keyCode, nativeKeyCode, new KeyModifiers(modifiers));
    }

    /// @deprecated This legacy accessor returns the layout-dependent keycode. Use [#nativeKeyCode()].
    @Deprecated
    public int scancode() { return this.nativeKeyCode; }
}
