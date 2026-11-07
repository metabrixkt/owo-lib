package io.wispforest.owo.braid.core.events;

import io.wispforest.owo.braid.core.KeyModifiers;

public record CharInputEvent(int codepoint, KeyModifiers modifiers) implements UserEvent {
    public CharInputEvent(int codepoint, int modifiers) {
        this(codepoint, new KeyModifiers(modifiers));
    }
}
