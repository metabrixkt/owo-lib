package io.wispforest.owo.braid.core.cursor;

import org.lwjgl.sdl.SDLMouse;

import java.util.HashMap;
import java.util.Map;

public class CursorController {

    private final Map<CursorStyle, Long> cursors = new HashMap<>();
    private final long windowHandle;

    private CursorStyle lastCursorStyle = CursorStyle.NONE;
    private boolean disposed = false;

    public CursorController(long windowHandle) {
        this.windowHandle = windowHandle;
    }

    public CursorStyle currentStyle() {
        return this.lastCursorStyle;
    }

    public void setStyle(CursorStyle style) {
        if (this.disposed) return;

        this.lastCursorStyle = style;
        this.apply();
    }

    /**
     * SDL cursors are global, so only the window under the mouse may select one.
     */
    public void apply() {
        if (this.disposed || SDLMouse.SDL_GetMouseFocus() != this.windowHandle) return;

        long cursor = this.lastCursorStyle == CursorStyle.NONE
            ? 0
            : this.cursors.computeIfAbsent(this.lastCursorStyle, CursorStyle::allocate);
        if (cursor == 0) cursor = SDLMouse.SDL_GetDefaultCursor();
        if (SDLMouse.SDL_GetCursor() != cursor) SDLMouse.SDL_SetCursor(cursor);
    }

    public void dispose() {
        if (this.disposed) return;

        if (this.cursors.containsValue(SDLMouse.SDL_GetCursor())) {
            SDLMouse.SDL_SetCursor(SDLMouse.SDL_GetDefaultCursor());
        }
        for (final var cursor : this.cursors.values()) {
            if (cursor != 0) SDLMouse.SDL_DestroyCursor(cursor);
        }
        this.cursors.clear();
        this.disposed = true;
    }
}
