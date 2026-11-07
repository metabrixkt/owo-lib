package io.wispforest.owo.mixin.braid;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.mojang.blaze3d.platform.TextInputManager;
import io.wispforest.owo.braid.core.BraidTextInput;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.events.GuiEventListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class TextInputMixin {
    @ModifyExpressionValue(method = "textInputManager", at = @At(value = "FIELD", target = "Lnet/minecraft/client/Minecraft;textInputManager:Lcom/mojang/blaze3d/platform/TextInputManager;"))
    private TextInputManager selectSurfaceTextInputManager(final TextInputManager original) {
        final var app = BraidTextInput.currentApp();
        return app == null ? original : app.surface.textInputManager(original);
    }

    @Inject(method = "onTextInputFocusChange", at = @At("HEAD"), cancellable = true)
    private void routeEditorFocus(final GuiEventListener element, final boolean isFocused, final CallbackInfo ci) {
        var app = BraidTextInput.currentApp();
        if (app == null) return;

        app.onTextInputFocusChange(element, isFocused);
        ci.cancel();
    }
}
