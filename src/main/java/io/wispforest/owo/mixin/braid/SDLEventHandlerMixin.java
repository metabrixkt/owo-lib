package io.wispforest.owo.mixin.braid;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.platform.SDLEventHandler;
import io.wispforest.owo.braid.core.BraidWindowScheduler;
import org.jspecify.annotations.NullMarked;
import org.lwjgl.sdl.SDL_Event;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@NullMarked
@Mixin(SDLEventHandler.class)
public class SDLEventHandlerMixin {
    @WrapOperation(method = "pollEvents", at = @At(value = "INVOKE", target = "Lorg/lwjgl/sdl/SDLEvents;SDL_PollEvent(Lorg/lwjgl/sdl/SDL_Event;)Z"))
    private boolean routeBraidWindowEvents(final SDL_Event event, final Operation<Boolean> original) {
        while (original.call(event)) {
            if (!BraidWindowScheduler.dispatchEvent(event)) return true;
        }
        return false;
    }
}
