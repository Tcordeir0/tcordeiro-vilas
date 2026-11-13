package com.tcordeiro.vilas.life;

import com.tcordeiro.vilas.VilasConfig;
import com.tcordeiro.vilas.village.Village;
import com.tcordeiro.vilas.village.VillageManager;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.villager.Villager;

/** 0.4.0 — Ligações da vida dos aldeões ao jogo: menu e presentes (Shift+clique), pânico e golpes, itens de cena que nunca caem. */
public final class LifeEvents {
	private LifeEvents() {}

	public static void init() {
		Balloons.init();
		LifeSeat.init();
		// registado DEPOIS dos do Prefeito e dos pedidos: o clique simples continua igual; o Shift+clique vem para aqui
		UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
			if (world instanceof ServerLevel level && player instanceof ServerPlayer sp && entity instanceof Villager vi)
				return Resident.onUse(sp, level, vi, hand);
			return InteractionResult.PASS;
		});
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
			if (blocked || !(entity instanceof Villager vi) || !(entity.level() instanceof ServerLevel level) || !VilasConfig.get().villager_life) return;
			Entity by = source.getEntity();
			if (by == null) return;
			Village v = VillageManager.villageAt(level, vi.blockPosition());
			if (v == null || v.alarm.on) return;
			if (by instanceof Enemy) Care.panic(level, v, Life.places(level, v), vi, by);
			else if (by instanceof ServerPlayer p) hit(level, v, vi, p);
		});
		ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
			if (entity instanceof Villager vi) Life.cleanup(vi);
			return true;
		});
		ServerEntityEvents.ENTITY_LOAD.register((e, level) -> {
			if (e instanceof Villager vi && Props.holding(vi)) Props.clear(vi);
			Stage.onLoad(e); // 0.5.0: boia/banca que ficou de uma sessão anterior
			Market.onLoad(level, e); // 0.5.0: o vendedor de fora vai-se embora depois do mercado
		});
	}

	/** Um jogador bateu num aldeão: os vizinhos a 8 blocos viram-se para ele com um balão zangado, e a vila fica a saber. */
	static void hit(ServerLevel level, Village v, Villager victim, ServerPlayer p) {
		Balloons.show(victim, Balloons.ANGRY, 50);
		for (Villager o : Life.villagers(level, v))
			if (o != victim && o.distanceToSqr(victim) < 64 && !o.isSleeping() && Tasks.get(o) == null) {
				Gestures.glance(o, p);
				Balloons.show(o, Balloons.ANGRY, 40);
			}
		Talk.fact(level, v, victim.blockPosition(), "hit", p.getGameProfile().name());
	}
}
