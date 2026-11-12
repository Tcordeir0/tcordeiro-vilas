package com.tcordeiro.vilas.village;

import java.util.ArrayList;
import java.util.List;

import com.tcordeiro.vilas.VilasConfig;
import com.tcordeiro.vilas.logic.MerchantRules;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.util.SpawnUtil;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.equine.Donkey;
import net.minecraft.world.entity.npc.wanderingtrader.WanderingTrader;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;

/**
 * O mercador viajante DA VILA (não é o Mercador Ambulante vanilla):
 * <ul>
 * <li>vem por calendário (de {@code merchant_every_days} em {@code merchant_every_days} dias) e aparece na PRAÇA, ao pé da Mesa
 * do Prefeito, com um burro de carga — o vanilla aparece ao acaso perto de um jogador;</li>
 * <li>o Prefeito anuncia quando chega e quando parte; fica {@code merchant_stay_days} dias (o vanilla fica 40 a 60 minutos);</li>
 * <li>stock raro e diferente: etiquetas, selas, discos, frascos de experiência, chave de provação, ovo de farejador, moldes de
 * armadura… e coisas dos mods irmãos instalados — nada do stock do vanilla (plantas, corais, corantes);</li>
 * <li>as trocas com ele pagam a taxa da vila, como as dos aldeões.</li>
 * </ul>
 */
public final class TravelingMerchant {
	private TravelingMerchant() {}

	public static final String TAG = "tvilas.merchant", DONKEY_TAG = "tvilas.merchant_donkey";
	/** Quantas trocas leva de cada vez (escolhidas do stock). */
	public static final int OFFERS = 6;

	/** Um artigo: id, quantidade, preço em esmeraldas, usos. Os ids «mod:» só entram se o mod estiver instalado. */
	public record Stock(String item, int count, int emeralds, int uses) {}

	public static final List<Stock> STOCK = List.of(
			new Stock("minecraft:name_tag", 1, 5, 3),
			new Stock("minecraft:saddle", 1, 6, 2),
			new Stock("minecraft:experience_bottle", 4, 3, 6),
			new Stock("minecraft:music_disc_mellohi", 1, 8, 1),
			new Stock("minecraft:music_disc_wait", 1, 8, 1),
			new Stock("minecraft:music_disc_otherside", 1, 12, 1),
			new Stock("minecraft:spyglass", 1, 4, 2),
			new Stock("minecraft:brush", 1, 3, 2),
			new Stock("minecraft:armadillo_scute", 2, 3, 4),
			new Stock("minecraft:sniffer_egg", 1, 16, 1),
			new Stock("minecraft:trial_key", 1, 8, 2),
			new Stock("minecraft:glow_ink_sac", 3, 2, 6),
			new Stock("minecraft:echo_shard", 1, 10, 2),
			new Stock("minecraft:wild_armor_trim_smithing_template", 1, 14, 1),
			new Stock("tforja:raw_titanium", 4, 6, 4),
			new Stock("tforja:raw_chromite", 4, 5, 4),
			new Stock("tmoveis:varnish_brush_honey", 1, 4, 2),
			new Stock("tindustrial:speed_upgrade", 1, 10, 1));

	static Item item(String id) {
		return BuiltInRegistries.ITEM.getOptional(Identifier.parse(id)).orElse(null);
	}

	/** As trocas desta visita: {@link #OFFERS} artigos ao acaso do stock que existe neste jogo. */
	public static List<MerchantOffer> offers(RandomSource random) {
		List<Stock> pool = new ArrayList<>();
		for (Stock s : STOCK) if (item(s.item()) != null && item(s.item()) != Items.AIR) pool.add(s);
		List<MerchantOffer> out = new ArrayList<>();
		while (out.size() < OFFERS && !pool.isEmpty()) {
			Stock s = pool.remove(random.nextInt(pool.size()));
			out.add(new MerchantOffer(new ItemCost(Items.EMERALD, s.emeralds()), new ItemStack(item(s.item()), s.count()), s.uses(), 5, 0.05f));
		}
		return out;
	}

	public static boolean present(Village v) {
		return v.extra.merchant != null;
	}

	/** Uma vez por dia: chega, ou vai-se embora (público para os testes poderem avançar os dias). */
	public static void daily(ServerLevel level, Village v, long day) {
		Village.Extra x = v.extra;
		VilasConfig cfg = VilasConfig.get();
		if (x.merchantNext < 0 && !present(v)) x.merchantNext = MerchantRules.nextVisit(day, cfg.merchant_every_days);
		if (MerchantRules.leaves(present(v), day, x.merchantArrived, cfg.merchant_stay_days)) leave(level, v, day);
		else if (MerchantRules.arrives(present(v), day, x.merchantNext)) arrive(level, v, day);
		VillageData.get(level).changed();
	}

	static void arrive(ServerLevel level, Village v, long day) {
		Village.Extra x = v.extra;
		BlockPos at = v.center;
		WanderingTrader t = SpawnUtil.trySpawnMob(EntityTypes.WANDERING_TRADER, EntitySpawnReason.EVENT, level, at, 10,
				Math.max(1, Math.min(6, v.radius / 2)), 4, SpawnUtil.Strategy.ON_TOP_OF_COLLIDER, false).orElse(null);
		if (t == null) return; // sem sítio na praça: tenta amanhã
		t.addTag(TAG);
		t.setCustomName(Component.translatable("entity.tvilas.village_merchant"));
		t.setPersistenceRequired();
		t.setDespawnDelay((int) ((VilasConfig.get().merchant_stay_days + 1) * VillageEconomy.DAY));
		t.setWanderTarget(at);
		var offers = t.getOffers(); // gera as do vanilla…
		offers.clear(); // …e troca-as pelas nossas
		offers.addAll(offers(level.getRandom()));
		Donkey d = EntityTypes.DONKEY.create(level, EntitySpawnReason.EVENT);
		if (d != null) {
			d.snapTo(t.getX() + 1, t.getY(), t.getZ(), 0, 0);
			d.setChest(true);
			d.setTamed(true);
			d.addTag(DONKEY_TAG);
			d.setPersistenceRequired();
			level.addFreshEntity(d);
			d.setLeashedTo(t, true);
			x.donkey = d.getUUID();
		}
		x.merchant = t.getUUID();
		x.merchantArrived = day;
		x.merchantNext = -1;
		VillageNews.announce(level, v, VillageNews.MERCHANT, "tvilas.news.merchant_arrived", VilasConfig.get().merchant_stay_days);
	}

	static void leave(ServerLevel level, Village v, long day) {
		Village.Extra x = v.extra;
		for (var id : new java.util.UUID[] {x.merchant, x.donkey}) {
			if (id == null) continue;
			Entity e = level.getEntity(id);
			if (e != null) e.discard();
		}
		x.merchant = null;
		x.donkey = null;
		x.merchantArrived = -1;
		x.merchantNext = MerchantRules.nextVisit(day, VilasConfig.get().merchant_every_days);
		VillageNews.announce(level, v, VillageNews.MERCHANT, "tvilas.news.merchant_left", x.merchantNext);
	}

	/** O mercador desta vila, se estiver carregado (para os testes e o quadro). */
	public static WanderingTrader entity(ServerLevel level, Village v) {
		return v.extra.merchant != null && level.getEntity(v.extra.merchant) instanceof WanderingTrader t ? t : null;
	}
}
