package com.tcordeiro.vilas.worldgen;

import java.util.ArrayList;
import java.util.List;

import com.mojang.datafixers.util.Pair;
import com.tcordeiro.vilas.TVilas;
import com.tcordeiro.vilas.mixin.StructureTemplatePoolAccessor;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.ProcessorLists;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorList;

/**
 * Põe a Prefeitura e o Mercado nas vilas vanilla (planícies, savana, deserto, taiga e neve), sem Lithostitched nem outro mod:
 * quando o servidor arranca (registos de worldgen já carregados dos data-packs), acrescenta elementos às template pools
 * vanilla {@code minecraft:village/<bioma>/town_centers} e {@code .../houses} pelas listas internas da pool (mixin accessor).
 * <ul>
 * <li>Prefeitura: centro da vila, com peso igual à soma dos centros normais do bioma → cerca de metade das vilas novas nasce com
 * Prefeitura (as outras podem ganhar uma pondo a Mesa do Prefeito à mão).</li>
 * <li>Mercado: casa da vila, peso {@link #MARKET_WEIGHT}.</li>
 * </ul>
 * Só afeta chunks gerados depois de instalar o mod. Os ficheiros NBT vêm de tools/gen_structures.py.
 */
public final class VillagePoolInjector {
	private VillagePoolInjector() {}

	public static final List<String> BIOMES = List.of("plains", "savanna", "desert", "taiga", "snowy");
	public static final int MARKET_WEIGHT = 4;

	public static void init() {
		FurnitureProcessor.init();
		ServerLifecycleEvents.SERVER_STARTING.register(VillagePoolInjector::inject);
	}

	public static Identifier townHall(String biome) {
		return TVilas.id("village/" + biome + "/town_hall");
	}

	public static Identifier market(String biome) {
		return TVilas.id("village/" + biome + "/market");
	}

	/** 0.2.0: Taverna e pousada (camas, balcão do Taberneiro, mesas). */
	public static Identifier tavern(String biome) {
		return TVilas.id("village/" + biome + "/tavern");
	}

	/** 0.2.0: Praça com fonte e poço. */
	public static Identifier plaza(String biome) {
		return TVilas.id("village/" + biome + "/plaza");
	}

	public static final int TAVERN_WEIGHT = 3, PLAZA_WEIGHT = 3;

	/** Os móveis do Móveis (quando instalado) entram pela lista de processadores destas peças: {@link FurnitureProcessor}. */
	public static Holder<StructureProcessorList> furniture() {
		return Holder.direct(new StructureProcessorList(List.of(FurnitureProcessor.INSTANCE)));
	}

	public static ResourceKey<StructureTemplatePool> pool(String biome, String kind) {
		return ResourceKey.create(Registries.TEMPLATE_POOL, Identifier.withDefaultNamespace("village/" + biome + "/" + kind));
	}

	static void inject(MinecraftServer server) {
		Registry<StructureTemplatePool> pools = server.registryAccess().lookupOrThrow(Registries.TEMPLATE_POOL);
		Holder<StructureProcessorList> empty = server.registryAccess().lookupOrThrow(Registries.PROCESSOR_LIST).getOrThrow(ProcessorLists.EMPTY);
		int added = 0;
		for (String biome : BIOMES) {
			StructureTemplatePool centers = pools.getValue(pool(biome, "town_centers"));
			StructureTemplatePool houses = pools.getValue(pool(biome, "houses"));
			if (centers == null || houses == null) {
				TVilas.LOGGER.warn("Vilas: pools de {} não encontradas, a Prefeitura/Mercado não entram aí", biome);
				continue;
			}
			int normal = 0;
			for (Pair<StructurePoolElement, Integer> p : ((StructureTemplatePoolAccessor) centers).tvilas$rawTemplates())
				if (!p.getFirst().toString().contains("zombie")) normal += p.getSecond();
			added += add(centers, townHall(biome), Math.max(1, Math.min(150, normal)), empty);
			added += add(houses, market(biome), MARKET_WEIGHT, empty);
			added += add(houses, tavern(biome), TAVERN_WEIGHT, furniture());
			added += add(houses, plaza(biome), PLAZA_WEIGHT, furniture());
		}
		TVilas.LOGGER.info("Vilas: {} peças acrescentadas às vilas vanilla", added);
	}

	/** Acrescenta uma peça à pool (se ainda não estiver lá). */
	static int add(StructureTemplatePool pool, Identifier location, int weight, Holder<StructureProcessorList> processors) {
		StructureTemplatePoolAccessor acc = (StructureTemplatePoolAccessor) pool;
		for (Pair<StructurePoolElement, Integer> p : acc.tvilas$rawTemplates())
			if (p.getFirst().toString().contains(location.toString())) return 0;
		StructurePoolElement element = StructurePoolElement.single(location.toString(), processors).apply(StructureTemplatePool.Projection.RIGID);
		List<Pair<StructurePoolElement, Integer>> raw = new ArrayList<>(acc.tvilas$rawTemplates());
		raw.add(Pair.of(element, weight));
		acc.tvilas$setRawTemplates(raw);
		for (int i = 0; i < weight; i++) acc.tvilas$templates().add(element);
		return 1;
	}

	/** A pool contém esta peça? (para os testes) */
	public static boolean contains(StructureTemplatePool pool, Identifier location) {
		for (Pair<StructurePoolElement, Integer> p : pool.getTemplates())
			if (p.getFirst().toString().contains(location.toString())) return true;
		return false;
	}
}
