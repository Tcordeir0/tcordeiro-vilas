package com.tcordeiro.vilas.mixin.protect;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.tcordeiro.vilas.village.VillageManager;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.FluidState;

/**
 * Terrenos: água/lava (e fluidos de outros mods) não escorrem para dentro de um terreno vindos de fora dele ou de um terreno de
 * outro dono. {@code spreadTo} é o único sítio onde um fluido ocupa a posição vizinha (para baixo ou para os lados); a origem é
 * a posição vizinha na direção contrária.
 */
@Mixin(FlowingFluid.class)
public abstract class FlowingFluidMixin {
	@Inject(method = "spreadTo", at = @At("HEAD"), cancellable = true)
	private void tvilas$keepOutOfLots(LevelAccessor level, BlockPos pos, BlockState state, Direction direction, FluidState target, CallbackInfo ci) {
		if (level instanceof ServerLevel sl && !VillageManager.mayCross(sl, pos.relative(direction.getOpposite()), pos)) ci.cancel();
	}
}
