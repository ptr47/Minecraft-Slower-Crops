package org.ptr47.slowercrops.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import org.ptr47.slowercrops.GrowthTimers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ServerLevel.class)
public abstract class ServerLevelMixin {
    @Redirect(
            method = "tickChunk",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/block/state/BlockState;randomTick(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/util/RandomSource;)V"
            )
    )
    private void slowercrops$slowCropAndSaplingTicks(
            BlockState state,
            ServerLevel level,
            BlockPos pos,
            RandomSource random
    ) {
        if (isManagedGrowthBlock(state)) {
            GrowthTimers.get(level).tick(level, pos, state, random);
        } else {
            state.randomTick(level, pos, random);
        }
    }

    private static boolean isManagedGrowthBlock(BlockState state) {
        var block = state.getBlock();
        return block instanceof SaplingBlock
                || block instanceof CropBlock
                || block instanceof StemBlock
                || block instanceof NetherWartBlock
                || block instanceof PitcherCropBlock
                || block instanceof CocoaBlock;
    }
}
