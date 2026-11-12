package com.tcordeiro.vilas.protect;

import com.tcordeiro.vilas.village.VillageManager;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.player.Player;

/**
 * 0.3.0 (C6) — Os bichos, aldeões e decorações (molduras, suportes de armadura, barcos…) DENTRO de um terreno (ou da área
 * cívica) ficam protegidos de quem não pode mexer lá: não lhes bate (nem com flechas/poções dele), não os prende com trela, não
 * os apanha com balde, não os tosquia, não lhes dá nome, não os monta nem lhes tira itens. Os monstros não são protegidos
 * (defende-se em qualquer lado) e trocar com os aldeões continua livre.
 */
public final class EntityProtection {
	private EntityProtection() {}

	public static void init() {
		AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
			if (!(world instanceof ServerLevel level) || mayTouch(level, player, entity)) return InteractionResult.PASS;
			warn(player);
			return InteractionResult.FAIL;
		});
		UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
			if (!(world instanceof ServerLevel level) || entity instanceof AbstractVillager || mayTouch(level, player, entity))
				return InteractionResult.PASS; // trocar com os aldeões (e falar com o Prefeito) continua livre
			warn(player);
			return InteractionResult.FAIL;
		});
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			if (!(entity.level() instanceof ServerLevel level) || !(source.getEntity() instanceof Player player)) return true;
			return mayTouch(level, player, entity);
		});
	}

	/** REGRA DE SEGURANÇA: este jogador pode bater/apanhar/usar esta entidade? Fora de terrenos e da área cívica, sim. */
	public static boolean mayTouch(ServerLevel level, Player player, Entity entity) {
		if (entity instanceof Player || entity instanceof Enemy) return true;
		return VillageManager.mayTouch(level, entity.blockPosition(), player);
	}

	private static void warn(Player player) {
		if (player instanceof ServerPlayer sp && !(player instanceof net.fabricmc.fabric.api.entity.FakePlayer))
			sp.sendSystemMessage(Component.translatable("tvilas.lot.entity_protected"), true);
	}
}
