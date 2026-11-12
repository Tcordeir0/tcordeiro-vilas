package com.tcordeiro.vilas.mixin.protect;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.tcordeiro.vilas.village.VillageManager;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.BlockAttachedEntity;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ServerExplosion;

/**
 * Terrenos e explosões (TNT, creepers, cristais, camas, ...): a lista de blocos a partir é filtrada — saem os que estão em
 * terrenos onde esta explosão não pode mexer ({@link VillageManager#explosionMayBreak}). A mesma lista serve para o fogo da
 * explosão, por isso também não acende fogo lá dentro. As entidades continuam a ser feridas e empurradas como no vanilla
 * (jogadores e monstros não são «o terreno»), menos as decorações (molduras, quadros, suportes de armadura), que contam como
 * blocos.
 */
@Mixin(ServerExplosion.class)
public abstract class ServerExplosionMixin {
	private LivingEntity tvilas$cause() {
		ServerExplosion self = (ServerExplosion) (Object) this;
		LivingEntity cause = self.getIndirectSourceEntity();
		if (cause == null && self.getDamageSource().getEntity() instanceof LivingEntity attacker) cause = attacker; // cristal do End
		return cause;
	}

	@Inject(method = "calculateExplodedPositions", at = @At("RETURN"))
	private void tvilas$sparelots(CallbackInfoReturnable<List<BlockPos>> cir) {
		ServerExplosion self = (ServerExplosion) (Object) this;
		if (VillageManager.data(self.level()).lots.isEmpty()) return;
		LivingEntity cause = tvilas$cause();
		BlockPos origin = BlockPos.containing(self.center());
		cir.getReturnValue().removeIf(pos -> !VillageManager.explosionMayBreak(self.level(), cause, origin, pos));
	}

	@WrapOperation(method = "hurtEntities", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/entity/Entity;ignoreExplosion(Lnet/minecraft/world/level/Explosion;)Z"))
	private boolean tvilas$spareDecorations(Entity entity, Explosion explosion, Operation<Boolean> original) {
		if (original.call(entity, explosion)) return true;
		if (!(entity instanceof BlockAttachedEntity) && !(entity instanceof ArmorStand)) return false;
		ServerExplosion self = (ServerExplosion) (Object) this;
		return !VillageManager.explosionMayBreak(self.level(), tvilas$cause(), BlockPos.containing(self.center()), entity.blockPosition());
	}
}
