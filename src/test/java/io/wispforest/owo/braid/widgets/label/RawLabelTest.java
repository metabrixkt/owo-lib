package io.wispforest.owo.braid.widgets.label;

import com.mojang.blaze3d.platform.InputConstants;
import io.wispforest.owo.braid.core.KeyModifiers;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RawLabelTest {
    @Test
    void primaryClickActivatesTheStyleUnderThePointer() {
        var label = new ClickableLabel();

        assertTrue(label.onMouseDown(4, 8, InputConstants.MOUSE_BUTTON_LEFT, KeyModifiers.NONE));
        assertEquals(List.of(label.style), label.clickedStyles);
    }

    @Test
    void secondaryAndMiddleClicksDoNotActivateText() {
        var label = new ClickableLabel();

        assertFalse(label.onMouseDown(4, 8, InputConstants.MOUSE_BUTTON_RIGHT, KeyModifiers.NONE));
        assertFalse(label.onMouseDown(4, 8, InputConstants.MOUSE_BUTTON_MIDDLE, KeyModifiers.NONE));
        assertTrue(label.clickedStyles.isEmpty());
    }

    @Test
    void unhandledPrimaryClickCanPropagate() {
        var label = new ClickableLabel();
        label.handled = false;

        assertFalse(label.onMouseDown(4, 8, InputConstants.MOUSE_BUTTON_LEFT, KeyModifiers.NONE));
        assertEquals(List.of(label.style), label.clickedStyles);
    }

    private static class ClickableLabel extends RawLabel.Instance {
        private final Style style = Style.EMPTY.withBold(true);
        private final List<Style> clickedStyles = new ArrayList<>();
        private boolean handled = true;

        private ClickableLabel() {
            super(new RawLabel(LabelStyle.EMPTY, false, false, Component.literal("Clickable")));
            this.textClickHandler = clickedStyle -> {
                this.clickedStyles.add(clickedStyle);
                return this.handled;
            };
        }

        @Override
        public Style getStyleAt(double x, double y) {
            return this.style;
        }
    }
}
