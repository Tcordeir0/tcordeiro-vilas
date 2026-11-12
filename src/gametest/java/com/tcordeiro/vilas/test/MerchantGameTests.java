package com.tcordeiro.vilas.test;

import java.util.HashSet;
import java.util.Set;

import com.tcordeiro.vilas.VilasConfig;
import com.tcordeiro.vilas.village.TravelingMerchant;
import com.tcordeiro.vilas.village.Village;
import com.tcordeiro.vilas.village.VillageEconomy;
import com.tcordeiro.vilas.village.VillageManager;
import com.tcordeiro.vilas.village.VillageNews;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.animal.equine.Donkey;

/** 0.2.0 — o mercador viajante da vila chega à praça, fica uns dias e vai embora; os anúncios são de uma linha. */
public class MerchantGameTests {
	@GameTest
	public void merchantArrivesStaysAndLeaves(GameTestHelper h) {
		Village v = TestUtil.village(h);
		long day = VillageEconomy.day(h.getLevel()) + 7000;
		v.extra.merchantNext = day;
		TravelingMerchant.daily(h.getLevel(), v, day - 1);
		h.assertTrue(!TravelingMerchant.present(v), "ainda não é o dia");
		TravelingMerchant.daily(h.getLevel(), v, day);
		var t = TravelingMerchant.entity(h.getLevel(), v);
		h.assertTrue(t != null && t.entityTags().contains(TravelingMerchant.TAG), "chegou à praça");
		h.assertTrue(t.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(v.center)) < 10 * 10, "perto da Mesa/centro");
		Set<String> stock = new HashSet<>();
		for (var s : TravelingMerchant.STOCK) stock.add(s.item());
		h.assertTrue(!t.getOffers().isEmpty(), "tem trocas");
		for (var o : t.getOffers())
			h.assertTrue(stock.contains(BuiltInRegistries.ITEM.getKey(o.getResult().getItem()).toString()), "stock da vila, não o vanilla: " + o.getResult());
		h.assertTrue(v.extra.donkey != null && h.getLevel().getEntity(v.extra.donkey) instanceof Donkey, "traz o burro de carga");
		int stay = VilasConfig.get().merchant_stay_days;
		TravelingMerchant.daily(h.getLevel(), v, day + stay - 1);
		h.assertTrue(TravelingMerchant.present(v) && t.isAlive(), "fica " + stay + " dias");
		var donkey = h.getLevel().getEntity(v.extra.donkey);
		TravelingMerchant.daily(h.getLevel(), v, day + stay);
		h.assertTrue(!TravelingMerchant.present(v) && !t.isAlive(), "foi embora");
		h.assertTrue(donkey == null || !donkey.isAlive(), "levou o burro");
		h.assertValueEqual(v.extra.merchantNext, day + stay + VilasConfig.get().merchant_every_days, "volta daqui a uns dias");
		h.succeed();
	}

	@GameTest
	public void newsAreOneLineWithAnIcon(GameTestHelper h) {
		Village v = TestUtil.village(h);
		ServerPlayer p = TestUtil.playerAt(h, new BlockPos(4, 1, 4));
		h.assertTrue(VillageManager.playersIn(h.getLevel(), v).contains(p), "quem está no território recebe");
		Component msg = VillageNews.announce(h.getLevel(), v, VillageNews.BUILD, "tvilas.news.building", Component.translatable("tvilas.project.wall"));
		String text = msg.getString();
		h.assertTrue(text.startsWith(VillageNews.BUILD + " "), "começa pelo ícone: " + text);
		h.assertTrue(!text.contains("\n") && text.length() <= 100, "uma linha curta: " + text);
		for (String key : new String[] {"building", "done", "overnight", "merchant_arrived", "merchant_left", "tax_up", "tax_down", "debt_warning",
				"debt_frozen", "debt_unfrozen", "wall_cancelled"}) {
			String line = VillageNews.line(VillageNews.WARN, "tvilas.news." + key, "x", "y").getString();
			h.assertTrue(!line.contains("\n") && line.length() <= 100, "anúncio numa linha: " + key + " → " + line);
			h.assertTrue(!line.contains("tvilas.news"), "tradução existe: " + key);
		}
		p.discard();
		h.succeed();
	}
}
