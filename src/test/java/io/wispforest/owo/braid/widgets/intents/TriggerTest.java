package io.wispforest.owo.braid.widgets.intents;

import com.mojang.blaze3d.platform.InputConstants;
import io.wispforest.owo.braid.core.KeyModifiers;
import org.junit.jupiter.api.Test;
import org.lwjgl.sdl.SDLKeycode;

import static org.junit.jupiter.api.Assertions.*;

class TriggerTest {
    @Test
    void controlShiftShortcutAcceptsEitherPhysicalModifierSide() {
        var trigger = Trigger.ofKey(InputConstants.KEY_I,
            new KeyModifiers(InputConstants.MOD_CONTROL | InputConstants.MOD_SHIFT));

        assertTrue(trigger.isTriggered(InputConstants.KEY_I,
            new KeyModifiers(SDLKeycode.SDL_KMOD_LCTRL | SDLKeycode.SDL_KMOD_LSHIFT)));
        assertTrue(trigger.isTriggered(InputConstants.KEY_I,
            new KeyModifiers(SDLKeycode.SDL_KMOD_RCTRL | SDLKeycode.SDL_KMOD_RSHIFT)));
        assertTrue(trigger.isTriggered(InputConstants.KEY_I,
            new KeyModifiers(SDLKeycode.SDL_KMOD_LCTRL | SDLKeycode.SDL_KMOD_RSHIFT)));
    }

    @Test
    void exactShortcutRejectsMissingAndExtraModifiers() {
        var trigger = Trigger.ofKey(InputConstants.KEY_I, new KeyModifiers(InputConstants.MOD_CONTROL));

        assertFalse(trigger.isTriggered(InputConstants.KEY_I, KeyModifiers.NONE));
        assertFalse(trigger.isTriggered(InputConstants.KEY_I,
            new KeyModifiers(SDLKeycode.SDL_KMOD_LCTRL | SDLKeycode.SDL_KMOD_LALT)));
        assertFalse(trigger.isTriggered(InputConstants.KEY_J, new KeyModifiers(SDLKeycode.SDL_KMOD_LCTRL)));
    }

    @Test
    void lockStateDoesNotDisableShortcuts() {
        var trigger = Trigger.ofKey(InputConstants.KEY_I, new KeyModifiers(InputConstants.MOD_CONTROL));
        assertTrue(trigger.isTriggered(InputConstants.KEY_I,
            new KeyModifiers(SDLKeycode.SDL_KMOD_LCTRL | SDLKeycode.SDL_KMOD_CAPS | SDLKeycode.SDL_KMOD_NUM)));

        var plain = Trigger.ofKey(InputConstants.KEY_I);
        assertTrue(plain.isTriggered(InputConstants.KEY_I, new KeyModifiers(SDLKeycode.SDL_KMOD_CAPS)));
    }

    @Test
    void mouseShortcutMatchesMinecraftButtonAndLogicalModifiers() {
        var trigger = Trigger.ofMouse(InputConstants.MOUSE_BUTTON_RIGHT, new KeyModifiers(InputConstants.MOD_SHIFT));

        assertTrue(trigger.isTriggered(InputConstants.MOUSE_BUTTON_RIGHT, new KeyModifiers(SDLKeycode.SDL_KMOD_RSHIFT)));
        assertFalse(trigger.isTriggered(InputConstants.MOUSE_BUTTON_LEFT, new KeyModifiers(SDLKeycode.SDL_KMOD_RSHIFT)));
        assertFalse(trigger.isTriggered(InputConstants.MOUSE_BUTTON_RIGHT, KeyModifiers.NONE));
    }

    @Test
    void modifierNormalizationPreservesLocksAndRecordEquality() {
        var left = new KeyModifiers(SDLKeycode.SDL_KMOD_LCTRL | SDLKeycode.SDL_KMOD_CAPS | SDLKeycode.SDL_KMOD_NUM);
        var right = new KeyModifiers(SDLKeycode.SDL_KMOD_RCTRL | SDLKeycode.SDL_KMOD_CAPS | SDLKeycode.SDL_KMOD_NUM);

        assertEquals(left, right);
        assertEquals(left.hashCode(), right.hashCode());
        assertTrue(left.ctrl());
        assertTrue(left.capsLock());
        assertTrue(left.numLock());
        assertFalse(left.shift());
        assertFalse(left.alt());
        assertFalse(left.meta());
    }
}
