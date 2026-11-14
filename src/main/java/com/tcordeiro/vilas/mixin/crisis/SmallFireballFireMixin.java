package com.tcordeiro.vilas.mixin.crisis;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.tcordeiro.vilas.life.crisis.Crises;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.hurtingprojectile.SmallFireball;
import net.minecraft.world.phys.BlockHitResult;

/** 0.6 crises (revisão de segurança) — O fogo de uma bola de fogo pequena só é natural se o dono for um monstro (blaze); de um jogador ou de um dispensador (sem dono) não. */
@Mixin(SmallFireball.class)
public abstract class SmallFireballFireMixin {
	@Inject(method = "onHitBlock", at = @At("HEAD"))
	private void tvilas$causeBegin(BlockHitResult hit, CallbackInfo ci) {
		Crises.begin(((SmallFireball) (Object) this).getOwner() instanceof Mob ? Crises.Cause.NATURAL : Crises.Cause.PLAYER, null);
	}

	@Inject(method = "onHitBlock", at = @At("RETURN"))
	private void tvilas$causeEnd(BlockHitResult hit, CallbackInfo ci) {
		Crises.end();
	}
}
