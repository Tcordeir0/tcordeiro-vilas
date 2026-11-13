package com.tcordeiro.vilas.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.tcordeiro.vilas.life.LifeVillager;
import com.tcordeiro.vilas.life.Task;
import com.tcordeiro.vilas.life.Tasks;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.npc.villager.Villager;

/**
 * 0.4.0 — A vida dos aldeões: enquanto o Vilas dá uma tarefa a um aldeão (ir à Praça, sentar, abrigar-se da chuva, fugir…), o
 * cérebro vanilla dele fica em pausa (a navegação vanilla continua a andar). Sem tarefa, nada muda: o cérebro corre como sempre.
 */
@Mixin(Villager.class)
abstract class VillagerLifeMixin implements LifeVillager {
	@Unique
	private Task tvilas$task;
	@Unique
	private final Memo tvilas$memo = new Memo();

	@Override
	public Task tvilas$task() {
		return tvilas$task;
	}

	@Override
	public void tvilas$setTask(Task task) {
		tvilas$task = task;
	}

	@Override
	public Memo tvilas$memo() {
		return tvilas$memo;
	}

	@WrapWithCondition(method = "customServerAiStep", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/entity/ai/Brain;tick(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/LivingEntity;)V"))
	private boolean tvilas$brainUnlessTask(Brain<?> brain, ServerLevel level, LivingEntity body) {
		return tvilas$task == null || Tasks.tick(level, (Villager) (Object) this, tvilas$task);
	}
}
