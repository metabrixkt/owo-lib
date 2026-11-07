package io.wispforest.owo.mixin.braid;

import io.wispforest.owo.braid.core.BraidWindowScheduler;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class BraidWindowSchedulerMixin {
    @Inject(method = "renderFrame", at = @At("TAIL"))
    private void drawSecondaryWindows(boolean advanceGameTime, CallbackInfo ci) {
        BraidWindowScheduler.frame();
    }
}
