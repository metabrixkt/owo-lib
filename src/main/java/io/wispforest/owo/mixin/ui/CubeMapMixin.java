package io.wispforest.owo.mixin.ui;

import io.wispforest.owo.ui.renderstate.CubeMapElementRenderState;
import net.minecraft.client.renderer.CubeMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

import java.util.Optional;
import org.joml.Vector4f;

@Mixin(CubeMap.class)
public class CubeMapMixin {

    @ModifyArgs(method = "render", at = @At(value = "INVOKE", target = "Lcom/mojang/renderpearl/api/commands/CommandEncoder;createRenderPass(Ljava/util/function/Supplier;Lcom/mojang/renderpearl/api/textures/GpuTextureView;Ljava/util/Optional;Lcom/mojang/renderpearl/api/textures/GpuTextureView;Ljava/util/OptionalDouble;)Lcom/mojang/renderpearl/api/commands/RenderPass;"))
    private void injectOutputTextures(Args args) {
        if (CubeMapElementRenderState.outputOverride == null) return;

        args.set(1, CubeMapElementRenderState.outputOverride.color());
        int color = CubeMapElementRenderState.outputOverride.resetColor();
        args.set(2, Optional.of(new Vector4f(
            ((color >> 16) & 255) / 255f,
            ((color >> 8) & 255) / 255f,
            (color & 255) / 255f,
            ((color >>> 24) & 255) / 255f
        )));
        args.set(3, CubeMapElementRenderState.outputOverride.depth());
    }

    @ModifyArgs(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/Projection;setupPerspective(FFFFF)V"))
    private void injectOutputDimensions(Args args) {
        if (CubeMapElementRenderState.outputOverride == null) return;

        var color = CubeMapElementRenderState.outputOverride.color();
        args.set(3, (float) color.getWidth(0));
        args.set(4, (float) color.getHeight(0));
    }
}
