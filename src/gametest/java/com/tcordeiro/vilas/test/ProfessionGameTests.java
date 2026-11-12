package com.tcordeiro.vilas.test;

import com.tcordeiro.vilas.registry.VilasProfessions;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.tags.PoiTypeTags;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;

/** Cada vendedor novo: profissão registada, posto de trabalho com 1 vaga que os desempregados procuram, trocas nos 5 níveis. */
public class ProfessionGameTests {
	@GameTest
	public void everyVendorHasJobSiteAndTradesAtEveryLevel(GameTestHelper h) {
		var tradeSets = h.getLevel().registryAccess().lookupOrThrow(Registries.TRADE_SET);
		for (var prof : VilasProfessions.VENDORS.keySet()) {
			h.assertTrue(BuiltInRegistries.VILLAGER_PROFESSION.getValue(prof) != null, "profissão " + prof.identifier());
			var poi = VilasProfessions.POIS.get(prof);
			h.assertValueEqual(BuiltInRegistries.POINT_OF_INTEREST_TYPE.getValue(poi).maxTickets(), 1, "1 vaga em " + poi.identifier());
			h.assertTrue(BuiltInRegistries.POINT_OF_INTEREST_TYPE.getOrThrow(poi).is(PoiTypeTags.ACQUIRABLE_JOB_SITE), "procurado: " + poi.identifier());
			for (int lvl = 1; lvl <= 5; lvl++) {
				var key = VilasProfessions.tradeSet(prof, lvl);
				h.assertTrue(tradeSets.get(key).isPresent(), "trocas " + key.identifier());
				h.assertTrue(tradeSets.getValue(key).trades().size() >= 2, "pelo menos 2 trocas em " + key.identifier());
			}
		}
		h.succeed();
	}

	@GameTest
	public void vendorVillagersOfferTrades(GameTestHelper h) {
		TestUtil.clean(h);
		TestUtil.floor(h, 4);
		int i = 0;
		for (var prof : VilasProfessions.VENDORS.keySet()) {
			Villager v = h.spawn(EntityTypes.VILLAGER, new BlockPos(1 + 2 * i++, 1, 4));
			v.setNoAi(true);
			v.setVillagerData(v.getVillagerData().withProfession(h.getLevel().registryAccess(), prof).withLevel(1));
			var offers = v.getOffers();
			h.assertValueEqual(offers.size(), 2, "2 trocas no nível 1 de " + prof.identifier());
			for (MerchantOffer o : offers)
				h.assertTrue(o.getResult().is(Items.EMERALD) || o.getBaseCostA().is(Items.EMERALD), "troca com esmeraldas: " + prof.identifier());
		}
		h.killAllEntitiesOfClass(Villager.class);
		h.succeed();
	}
}
