package com.tcordeiro.vilas.village;

import com.tcordeiro.vilas.VilasConfig;
import com.tcordeiro.vilas.logic.GrowthRules;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.villager.Villager;

/**
 * 0.3.0 — A vila cresce com as missões ({@link GrowthRules}): prestígio por missão; com prestígio bastante chega um morador novo
 * a pé pela borda da vila (como o Mercador) se houver cama livre, senão o Prefeito encomenda uma casa simples; teto de população.
 */
public final class Growth {
	private Growth() {}

	/** Etiqueta dos moradores que chegaram pelo prestígio. */
	public static final String TAG = "tvilas.newcomer";

	/** Uma missão foi concluída nesta vila: +1 de prestígio e, se der, chega alguém. */
	public static void missionDone(ServerLevel level, Village v) {
		v.civic.prestige++;
		VillageData.get(level).changed();
		step(level, v);
	}

	/** Camas livres perto da Prefeitura. */
	public static int freeBeds(ServerLevel level, Village v) {
		return (int) level.getPoiManager().getCountInRange(h -> h.is(PoiTypes.HOME), v.center, VillageEconomy.coreRadius(v), PoiManager.Occupancy.HAS_SPACE);
	}

	static boolean houseInProgress(Village v) {
		for (Project p : v.extra.projects) if (p.kind == Project.Kind.HOUSE) return true;
		return false;
	}

	/** Faz o que o prestígio manda (também uma vez por dia: a casa acabou, já há cama). Devolve o passo dado. */
	public static GrowthRules.Step step(ServerLevel level, Village v) {
		VilasConfig cfg = VilasConfig.get();
		GrowthRules.Step s = GrowthRules.step(v.civic.prestige, cfg.prestige_per_resident, VillageManager.countPopulation(level, v), cfg.population_cap,
				freeBeds(level, v), houseInProgress(v), v.economy.treasury);
		switch (s) {
			case ARRIVE -> {
				if (arrive(level, v) != null) v.civic.prestige -= Math.max(1, cfg.prestige_per_resident);
			}
			case ORDER_HOUSE -> {
				if (com.tcordeiro.vilas.build.CivicWork.orderHouse(level, v) == null) s = GrowthRules.Step.WAIT;
			}
			default -> {}
		}
		VillageData.get(level).changed();
		return s;
	}

	/** Chega um morador novo: aparece na borda da vila e vai a pé para a praça. */
	public static Villager arrive(ServerLevel level, Village v) {
		int dist = Math.max(4, Math.min(v.radius - 2, 48));
		double a = level.getRandom().nextDouble() * Math.PI * 2;
		BlockPos edge = null;
		for (int k = 0; k < 8 && edge == null; k++, a += Math.PI / 4) {
			int x = v.center.getX() + (int) Math.round(Math.cos(a) * dist), z = v.center.getZ() + (int) Math.round(Math.sin(a) * dist);
			if (!level.isLoaded(new BlockPos(x, v.center.getY(), z))) continue;
			BlockPos top = com.tcordeiro.vilas.build.Ground.surface(level, x, z, v.center.getY());
			if (level.getBlockState(top.below()).isFaceSturdy(level, top.below(), net.minecraft.core.Direction.UP) && level.getBlockState(top).isAir()
					&& level.getBlockState(top.above()).isAir()) edge = top;
		}
		Villager n;
		if (edge != null) {
			n = EntityTypes.VILLAGER.create(level, EntitySpawnReason.EVENT);
			if (n == null) return null;
			n.snapTo(edge.getX() + 0.5, edge.getY(), edge.getZ() + 0.5, level.getRandom().nextFloat() * 360, 0);
			level.addFreshEntity(n);
		} else n = VillageEconomy.spawnVillager(level, v); // sem borda carregada: chega pela praça
		if (n == null) return null;
		n.addTag(TAG);
		n.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(v.center, 0.6f, 3));
		VillageNews.announce(level, v, VillageNews.DONE, "tvilas.news.newcomer");
		return n;
	}
}
