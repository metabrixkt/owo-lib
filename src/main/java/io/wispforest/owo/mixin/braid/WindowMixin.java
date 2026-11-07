package io.wispforest.owo.mixin.braid;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.platform.cursor.CursorType;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.lwjgl.sdl.SDLEvents;
import org.lwjgl.sdl.SDLMouse;
import org.lwjgl.sdl.SDLVideo;
import org.lwjgl.sdl.SDL_Event;

@Mixin(Window.class)
public class WindowMixin {
    @Shadow private CursorType currentCursor;

    @WrapOperation(method = "selectCursor", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/cursor/CursorType;select()V"))
    private void selectCursorForMouseWindow(CursorType cursor, Operation<Void> original) {
        if (SDLMouse.SDL_GetMouseFocus() == ((Window) (Object) this).handle()) original.call(cursor);
    }

    @Inject(method = "handleEvent", at = @At("TAIL"))
    private void restoreCursorOnMouseEnter(SDL_Event event, CallbackInfo ci) {
        if (event.type() == SDLEvents.SDL_EVENT_WINDOW_MOUSE_ENTER
            && SDLMouse.SDL_GetMouseFocus() == ((Window) (Object) this).handle()) this.currentCursor.select();
    }

    @Inject(method = "handleEvent", at = @At("HEAD"), cancellable = true)
    private void ignoreOtherWindows(SDL_Event event, CallbackInfo ci) {
        if (event.type() >= SDLEvents.SDL_EVENT_WINDOW_FIRST && event.type() <= SDLEvents.SDL_EVENT_WINDOW_LAST
            && event.window().windowID() != SDLVideo.SDL_GetWindowID(((Window) (Object) this).handle())) {
            ci.cancel();
        }
    }

    // Window.onResize uses the main client's grab state for every Window instance.
    // A secondary window must never inherit that relative-mouse mode.
    @WrapOperation(method = "onResize", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/InputConstants;grabMouse(Lcom/mojang/blaze3d/platform/Window;DD)V"))
    private void keepMainWindowMouseGrab(Window window, double x, double y, Operation<Void> original) {
        if (window == Minecraft.getInstance().getWindow()) original.call(window, x, y);
    }
}
