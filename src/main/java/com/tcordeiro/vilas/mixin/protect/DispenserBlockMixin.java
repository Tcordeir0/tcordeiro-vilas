package com.tcordeiro.vilas.mixin.protect;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.tcordeiro.vilas.village.VillageManager;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Terrenos e ejetores: um ejetor fora de um terreno (ou num terreno de outro dono) virado para dentro dele não dispara (baldes
 * de lava/água, isqueiro, TNT, cargas de fogo...). O caso dos líquidos que um ejetor põe FORA e que depois escorrem para dentro
 * já é tratado pelo {@link FlowingFluidMixin}.
 */
@Mixin(DispenserBlock.class)
public abstract class DispenserBlockMixin {
	@Inject(method = "dispenseFrom", at = @At("HEAD"), cancellable = true)
	private void tvilas$notIntoLots(ServerLevel level, BlockState state, BlockPos pos, CallbackInfo ci) {
		if (!VillageManager.mayCross(level, pos, pos.relative(state.getValue(DispenserBlock.FACING)))) ci.cancel();
	}
}
