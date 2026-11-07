package io.wispforest.owo.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import io.wispforest.owo.util.Maldenhagen;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.feature.OreFeature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.HashMap;
import java.util.Map;

// welcome to maldenhagen, it moved
// it originally lived in things, but it was malding too hard there
// see Maldenhagen for how this is used
@Mixin(OreFeature.class)
public class Copenhagen {

    // this map contains the seethe'd orr blocks. its quite important
    @Unique private final ThreadLocal<Map<BlockPos, BlockState>> COPING = ThreadLocal.withInitial(HashMap::new);

    // Chunk sections stay locked during placement, so lighting updates must wait until doPlace returns.
    @WrapOperation(method = "doPlace", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/chunk/LevelChunkSection;setBlockState(IIILnet/minecraft/world/level/block/state/BlockState;Z)Lnet/minecraft/world/level/block/state/BlockState;"))
    private BlockState malding(LevelChunkSection section, int x, int y, int z, BlockState state, boolean acquire,
                               Operation<BlockState> original, @Local BlockPos.MutableBlockPos pos) {
        var previousState = original.call(section, x, y, z, state, acquire);
        if (Maldenhagen.isOnCopium(state.getBlock())) {
            COPING.get().put(pos.immutable(), state);
        }
        return previousState;
    }

    // now in here we read all the gleaming ore spots from our cache and actually cause a block update so that the
    // lighting calculations happen. all of this just so that some dumb orr block can glow.
    @Inject(method = "doPlace", at = @At("TAIL"))
    private void coping(WorldGenLevel world, RandomSource random, double startX, double endX,
                        double startZ, double endZ, double startY, double endY, int x, int y, int z, int horizontalSize,
                        int verticalSize, CallbackInfoReturnable<Boolean> cir) {

        COPING.get().forEach((blockPos, state) -> {
            world.setBlock(blockPos, state, Block.UPDATE_ALL);
        });
        COPING.get().clear();
    }

}
