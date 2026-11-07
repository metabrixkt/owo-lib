package io.wispforest.owo.braid.core.cursor;

import org.lwjgl.sdl.SDLMouse;

public final class SystemCursorStyle implements CursorStyle {
    public final int sdlId;

    SystemCursorStyle(int sdlId) {
        this.sdlId = sdlId;
    }

    @Override
    public long allocate() {
        return this.sdlId < 0 ? 0 : SDLMouse.SDL_CreateSystemCursor(this.sdlId);
    }
}
