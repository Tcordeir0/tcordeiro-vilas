package com.tcordeiro.vilas.registry;

import java.util.LinkedHashMap;
import java.util.Map;

import com.google.common.collect.ImmutableSet;
import com.tcordeiro.vilas.TVilas;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.trading.TradeSet;
import net.minecraft.world.level.block.Block;

/**
 * Profissões novas de aldeão, cada uma com o seu posto de trabalho (ponto de interesse com 1 vaga) — o mesmo mecanismo das
 * profissões vanilla: um aldeão desempregado que encontre o posto livre passa a ter a profissão.
 * <ul>
 * <li><b>Prefeito</b> (Mesa do Prefeito): não faz trocas; falar com ele abre a Prefeitura.</li>
 * <li><b>Padeiro</b>, <b>Florista</b>, <b>Taberneiro</b>, <b>Apicultor</b>: trocas por nível (1–5), em data-pack
 * ({@code data/tvilas/trade_set/<profissão>/level_N.json} e {@code data/tvilas/villager_trade/...}), como as vanilla no 26.3.</li>
 * </ul>
 * O «Açougueiro» pedido já existe no vanilla (Butcher = «Açougueiro» em pt-BR) e o Charcuteiro/Peixeiro repetiriam as trocas do
 * Açougueiro/Pescador vanilla (o jogo não tem enchidos); por isso o quarto vendedor é o Apicultor (mel, favos, velas, colmeias).
 */
public final class VilasProfessions {
	private VilasProfessions() {}

	public static final ResourceKey<VillagerProfession> MAYOR = key("mayor");
	public static final ResourceKey<VillagerProfession> BAKER = key("baker");
	public static final ResourceKey<VillagerProfession> FLORIST = key("florist");
	public static final ResourceKey<VillagerProfession> INNKEEPER = key("innkeeper");
	public static final ResourceKey<VillagerProfession> BEEKEEPER = key("beekeeper");

	/** Os vendedores (profissão → posto de trabalho), pela ordem do guia. */
	public static final Map<ResourceKey<VillagerProfession>, Block> VENDORS = new LinkedHashMap<>();
	/** Ponto de interesse de cada profissão (inclui o Prefeito). */
	public static final Map<ResourceKey<VillagerProfession>, ResourceKey<PoiType>> POIS = new LinkedHashMap<>();

	static {
		VENDORS.put(BAKER, VilasBlocks.BAKER_OVEN);
		VENDORS.put(FLORIST, VilasBlocks.FLOWER_STALL);
		VENDORS.put(INNKEEPER, VilasBlocks.TAVERN_COUNTER);
		VENDORS.put(BEEKEEPER, VilasBlocks.APIARY_BENCH);
	}

	private static ResourceKey<VillagerProfession> key(String name) {
		return ResourceKey.create(Registries.VILLAGER_PROFESSION, TVilas.id(name));
	}

	public static ResourceKey<TradeSet> tradeSet(ResourceKey<VillagerProfession> prof, int level) {
		return ResourceKey.create(Registries.TRADE_SET, TVilas.id(prof.identifier().getPath() + "/level_" + level));
	}

	private static void register(ResourceKey<VillagerProfession> prof, Block jobSite, SoundEvent sound, boolean trades) {
		ResourceKey<PoiType> poi = ResourceKey.create(Registries.POINT_OF_INTEREST_TYPE, prof.identifier());
		// registo vanilla (PoiTypes.register, aberto pelos access wideners da Fabric API): 1 vaga, todos os estados do bloco
		PoiTypes.register(BuiltInRegistries.POINT_OF_INTEREST_TYPE, poi, ImmutableSet.copyOf(jobSite.getStateDefinition().getPossibleStates()), 1, 1);
		POIS.put(prof, poi);
		Int2ObjectMap<ResourceKey<TradeSet>> byLevel = new Int2ObjectOpenHashMap<>();
		if (trades) for (int lvl = 1; lvl <= 5; lvl++) byLevel.put(lvl, tradeSet(prof, lvl));
		Registry.register(BuiltInRegistries.VILLAGER_PROFESSION, prof, new VillagerProfession(
				Component.translatable("entity." + TVilas.MOD_ID + ".villager." + prof.identifier().getPath()),
				h -> h.is(poi), h -> h.is(poi), ImmutableSet.of(), ImmutableSet.of(), sound, byLevel));
	}

	public static void init() {
		register(MAYOR, VilasBlocks.MAYOR_DESK, SoundEvents.VILLAGER_WORK_CARTOGRAPHER, false);
		register(BAKER, VilasBlocks.BAKER_OVEN, SoundEvents.VILLAGER_WORK_BUTCHER, true);
		register(FLORIST, VilasBlocks.FLOWER_STALL, SoundEvents.VILLAGER_WORK_FARMER, true);
		register(INNKEEPER, VilasBlocks.TAVERN_COUNTER, SoundEvents.VILLAGER_WORK_FISHERMAN, true);
		register(BEEKEEPER, VilasBlocks.APIARY_BENCH, SoundEvents.VILLAGER_WORK_LEATHERWORKER, true);
	}
}
