package com.tcordeiro.vilas.mixin.protect;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.tcordeiro.vilas.village.VillageManager;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BaseSpawner;
import net.minecraft.world.level.block.entity.trialspawner.TrialSpawner;

/**
 * Missões de defesa: os monstros nascidos de um gerador (spawner das masmorras e trial spawner) ficam com a etiqueta
 * {@link VillageManager#FROM_SPAWNER_TAG} e não contam — senão um gerador ao pé da vila seria uma quinta de esmeraldas
 * (o alarme também soa com monstros perto).
 */
@Mixin({BaseSpawner.class, TrialSpawner.class})
public abstract class SpawnerMobsMixin {
	@WrapOperation(method = {"serverTick", "spawnMob"}, require = 1, at = @At(value = "INVOKE",
			target = "Lnet/minecraft/server/level/ServerLevel;tryAddFreshEntityWithPassengers(Lnet/minecraft/world/entity/Entity;)Z"))
	private boolean tvilas$markSpawnerMob(ServerLevel level, Entity entity, Operation<Boolean> original) {
		entity.addTag(VillageManager.FROM_SPAWNER_TAG);
		return original.call(level, entity);
	}
}
