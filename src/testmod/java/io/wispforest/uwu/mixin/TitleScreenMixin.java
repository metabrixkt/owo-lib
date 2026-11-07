package io.wispforest.uwu.mixin;

import io.wispforest.owo.config.ui.ConfigScreen;
import io.wispforest.uwu.Uwu;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.components.Button;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin extends Screen {

    protected TitleScreenMixin(Component title) {
        super(title);
    }

    // The first directly-created button in init is Options; avoid compiler-generated lambda names.
    @ModifyArg(method = "init", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/Button;builder(Lnet/minecraft/network/chat/Component;Lnet/minecraft/client/gui/components/Button$OnPress;)Lnet/minecraft/client/gui/components/Button$Builder;", ordinal = 0), index = 1)
    private Button.OnPress injectUwuConfigScreen(Button.OnPress original) {
        return button -> {
            var alt = InputConstants.isKeyDown(InputConstants.KEY_LALT)
                || InputConstants.isKeyDown(InputConstants.KEY_RALT);
            if (alt) Minecraft.getInstance().setScreenAndShow(ConfigScreen.create(Uwu.BRUHHHHH, this));
            else original.onPress(button);
        };
    }

}
