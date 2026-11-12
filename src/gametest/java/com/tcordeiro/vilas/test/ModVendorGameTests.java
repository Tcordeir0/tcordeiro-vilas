package com.tcordeiro.vilas.test;

import java.io.Reader;
import java.util.HashMap;
import java.util.Map;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.tcordeiro.vilas.TVilas;
import com.tcordeiro.vilas.compat.Mods;
import com.tcordeiro.vilas.logic.MissionRules;
import com.tcordeiro.vilas.registry.VilasProfessions;
import com.tcordeiro.vilas.village.TravelingMerchant;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.npc.villager.Villager;

/**
 * 0.2.0 — vendedores dos mods irmãos: cada profissão (e o seu posto) só existe com o seu mod; as trocas de TODOS os vendedores
 * deste mod e do mercador da vila nunca tornam uma missão lucrativa; o Cambista troca ao preço do Balcão, sem margem para
 * ganhar dinheiro a ir e vir.
 * <p>
 * Correr com e sem os mods: {@code ./gradlew runGametest} (todos), {@code -PnoCubcoin}, {@code -PnoMods} (nenhum).
 */
public class ModVendorGameTests {
	@GameTest
	public void modVendorsExistOnlyWithTheirMod(GameTestHelper h) {
		var tradeSets = h.getLevel().registryAccess().lookupOrThrow(Registries.TRADE_SET);
		String[][] blocks = {{"mechanic", "mechanic_bench", Mods.INDUSTRIAL}, {"alloy_smith", "alloy_anvil", Mods.FORJA},
				{"carpenter", "carpenter_bench", Mods.MOVEIS}, {"money_changer", "changer_counter", Mods.CUBCOIN}};
		for (String[] b : blocks) {
			boolean mod = Mods.loaded(b[2]);
			var prof = ResourceKey.create(Registries.VILLAGER_PROFESSION, TVilas.id(b[0]));
			h.assertValueEqual(VilasProfessions.MOD_VENDORS.get(prof), b[2], "profissão de mod: " + b[0]);
			h.assertValueEqual(BuiltInRegistries.VILLAGER_PROFESSION.containsKey(prof), mod, b[0] + " registado ⇔ " + b[2]);
			h.assertValueEqual(VilasProfessions.registered(prof), mod, b[0] + " com posto ⇔ " + b[2]);
			h.assertValueEqual(BuiltInRegistries.BLOCK.containsKey(TVilas.id(b[1])), mod, "posto " + b[1] + " ⇔ " + b[2]);
			h.assertValueEqual(BuiltInRegistries.ITEM.containsKey(TVilas.id(b[1])), mod, "item " + b[1] + " ⇔ " + b[2]);
			h.assertValueEqual(VilasProfessions.VENDORS.containsKey(prof), mod, "na lista dos vendedores ⇔ " + b[2]);
			for (int lvl = 1; lvl <= 5; lvl++)
				h.assertValueEqual(tradeSets.get(VilasProfessions.tradeSet(prof, lvl)).isPresent(), mod, "trocas " + b[0] + " nível " + lvl + " ⇔ " + b[2]);
			var recipe = h.getLevel().getServer().getRecipeManager()
					.byKey(ResourceKey.create(Registries.RECIPE, TVilas.id(b[1])));
			h.assertValueEqual(recipe.isPresent(), mod, "receita do posto " + b[1] + " ⇔ " + b[2]);
		}
		h.succeed();
	}

	@GameTest
	public void modVendorsTradeAtEveryLevel(GameTestHelper h) {
		TestUtil.clean(h);
		TestUtil.floor(h, 4);
		int i = 0;
		for (var prof : VilasProfessions.MOD_VENDORS.keySet()) {
			if (!VilasProfessions.registered(prof)) continue;
			for (int lvl = 1; lvl <= 5; lvl++) {
				Villager v = h.spawn(EntityTypes.VILLAGER, new BlockPos(1 + (i++ % 7), 1, 4));
				v.setNoAi(true);
				v.setVillagerData(v.getVillagerData().withProfession(h.getLevel().registryAccess(), prof).withLevel(lvl));
				h.assertTrue(v.getOffers().size() >= 2, "trocas de " + prof.identifier() + " no nível " + lvl + ": " + v.getOffers().size());
				v.discard();
			}
		}
		h.succeed();
	}

	/** item → o máximo que se compra por 1 esmeralda nas trocas carregadas deste mod (e no stock do mercador da vila). */
	static Map<String, Double> perEmerald(GameTestHelper h) {
		var trades = h.getLevel().registryAccess().lookupOrThrow(Registries.VILLAGER_TRADE);
		var rm = h.getLevel().getServer().getResourceManager();
		Map<String, Double> best = new HashMap<>();
		for (var e : rm.listResources("villager_trade", id -> id.getNamespace().equals(TVilas.MOD_ID) && id.getPath().endsWith(".json")).entrySet()) {
			String path = e.getKey().getPath();
			Identifier tradeId = TVilas.id(path.substring("villager_trade/".length(), path.length() - 5));
			if (trades.get(ResourceKey.create(Registries.VILLAGER_TRADE, tradeId)).isEmpty()) continue; // sem o mod: não carregou
			try (Reader r = e.getValue().openAsReader()) {
				JsonObject o = JsonParser.parseReader(r).getAsJsonObject();
				JsonObject wants = o.getAsJsonObject("wants"), gives = o.getAsJsonObject("gives");
				if (!wants.get("id").getAsString().equals("minecraft:emerald") || o.has("additional_wants")) continue;
				int em = wants.has("count") ? wants.get("count").getAsInt() : 1;
				int n = gives.has("count") ? gives.get("count").getAsInt() : 1;
				best.merge(gives.get("id").getAsString(), (double) n / Math.max(1, em), Math::max);
			} catch (java.io.IOException ex) {
				throw new RuntimeException(ex);
			}
		}
		for (var s : TravelingMerchant.STOCK) best.merge(s.item(), (double) s.count() / s.emeralds(), Math::max);
		return best;
	}

	@GameTest
	public void noMissionProfitBuyingFromAnyVendor(GameTestHelper h) {
		Map<String, Double> best = perEmerald(h);
		h.assertTrue(best.containsKey("minecraft:bread"), "lê as trocas do Padeiro");
		if (Mods.loaded(Mods.MOVEIS)) h.assertTrue(best.containsKey("tmoveis:oak_chair"), "lê as trocas do Marceneiro");
		for (MissionRules.Template t : MissionRules.TEMPLATES) {
			if (t.kind() == MissionRules.Kind.DEFEND) continue;
			Double per = best.get(t.target());
			if (per == null) continue;
			int cost = (int) Math.ceil(t.count() / per);
			h.assertTrue(t.emeralds() <= cost, "missão " + t + " dá lucro: custa " + cost + " esmeraldas aos vendedores");
			// a regra pura conhece os mesmos preços (ou mais baratos)
			h.assertTrue(MissionRules.VILLAGER_SALES.getOrDefault(t.target(), 0) >= Math.floor(per), "VILLAGER_SALES desatualizado: " + t.target());
		}
		h.succeed();
	}

	@GameTest
	public void moneyChangerHasNoArbitrage(GameTestHelper h) {
		if (!TVilas.CUBCOIN) {
			h.succeed();
			return;
		}
		var trades = h.getLevel().registryAccess().lookupOrThrow(Registries.VILLAGER_TRADE);
		var rm = h.getLevel().getServer().getResourceManager();
		long emerald = CubCoinTrade.emeraldPrice();
		int checked = 0;
		for (var e : rm.listResources("villager_trade/money_changer", id -> id.getNamespace().equals(TVilas.MOD_ID)).entrySet()) {
			String path = e.getKey().getPath();
			h.assertTrue(trades.get(ResourceKey.create(Registries.VILLAGER_TRADE, TVilas.id(path.substring(15, path.length() - 5)))).isPresent(),
					"troca carregada: " + path);
			try (Reader r = e.getValue().openAsReader()) {
				JsonObject o = JsonParser.parseReader(r).getAsJsonObject();
				h.assertValueEqual(o.get("reputation_discount").getAsDouble(), 0.0, "Cambista sem descontos: " + path);
				JsonObject wants = o.getAsJsonObject("wants");
				h.assertValueEqual(wants.has("count") ? wants.get("count").getAsInt() : 1, 1, "«wants» de 1 item (herói da aldeia não desce de 1): " + path);
				long in = CubCoinTrade.value(wants, emerald) + (o.has("additional_wants") ? CubCoinTrade.value(o.getAsJsonObject("additional_wants"), emerald) : 0);
				long out = CubCoinTrade.value(o.getAsJsonObject("gives"), emerald);
				h.assertValueEqual(in, out, "ao preço do Balcão (" + emerald + "¢ a esmeralda): " + path);
				checked++;
			} catch (java.io.IOException ex) {
				throw new RuntimeException(ex);
			}
		}
		h.assertTrue(checked >= 10, "trocas do Cambista vistas: " + checked);
		h.succeed();
	}
}
