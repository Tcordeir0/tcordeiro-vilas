package com.tcordeiro.vilas.mixin.crisis;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.tcordeiro.vilas.life.crisis.Rebuild;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.BreakDoorGoal;
import net.minecraft.world.level.Level;

/** 0.6 crises — Um zombie partiu uma porta (as duas metades): fica registada para o Construtor a repor ({@link Rebuild#record}). */
@Mixin(BreakDoorGoal.class)
public abstract class BreakDoorRebuildMixin {
	@WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;removeBlock(Lnet/minecraft/core/BlockPos;Z)Z"))
	private boolean tvilas$recordDoor(Level level, BlockPos pos, boolean moving, Operation<Boolean> original) {
		if (level instanceof ServerLevel sl) Rebuild.record(sl, pos, level.getBlockState(pos));
		return original.call(level, pos, moving);
	}
}
