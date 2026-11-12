package com.tcordeiro.vilas.test;

import com.tcordeiro.vilas.TVilas;
import com.tcordeiro.vilas.VilasConfig;
import com.tcordeiro.vilas.economy.CoinTrades;
import com.tcordeiro.vilas.registry.VilasProfessions;
import com.tcordeiro.vilas.village.Village;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;

/**
 * 0.3.0 — Com o CubCoin, todos os aldeões trocam em CubCoin (1 esmeralda = 40 CB, o preço do Balcão), com troco e sem criar
 * dinheiro; o Cambista não muda. Sem o CubCoin (-PnoCubcoin) tudo fica em esmeraldas. Os testes correm nos dois casos.
 */
public class CoinTradeGameTests {
	static long emerald() {
		return VilasConfig.get().emerald_value_cents;
	}

	static Villager trader(GameTestHelper h, net.minecraft.resources.ResourceKey<VillagerProfession> prof, BlockPos at) {
		Villager v = h.spawn(EntityTypes.VILLAGER, at);
		v.setNoAi(true);
		v.setVillagerData(v.getVillagerData().withProfession(h.getLevel().registryAccess(), prof).withLevel(1));
		return v;
	}

	static MerchantOffer offer(Villager v, boolean buysWithEmeralds) {
		for (MerchantOffer o : v.getOffers()) {
			var m = CoinTrades.of(o);
			if (buysWithEmeralds ? m.tvilas$emeraldsA() > 0 : m.tvilas$emeraldsResult() > 0) return o;
		}
		throw new AssertionError("sem troca " + (buysWithEmeralds ? "a pagar" : "a receber") + " esmeraldas");
	}

	@GameTest
	public void villagersShowCubCoinOnlyWithCubCoin(GameTestHelper h) {
		TestUtil.clean(h);
		TestUtil.floor(h, 4);
		h.assertValueEqual(CoinTrades.active(), TVilas.CUBCOIN, "a conversão só existe com o CubCoin");
		// um aldeão do jogo (Agricultor) e um deste mod (Padeiro)
		for (var prof : java.util.List.of(VillagerProfession.FARMER, VilasProfessions.BAKER)) {
			Villager v = trader(h, prof, new BlockPos(prof == VillagerProfession.FARMER ? 2 : 6, 1, 4));
			for (MerchantOffer o : v.getOffers()) {
				var m = CoinTrades.of(o);
				if (m.tvilas$emeraldsResult() > 0) {
					ItemStack r = o.getResult();
					if (TVilas.CUBCOIN) {
						h.assertTrue(CubCoinTrade.isMoney(r), "o aldeão paga em CubCoin: " + prof.identifier());
						h.assertValueEqual(CubCoinTrade.value(r), m.tvilas$emeraldsResult() * emerald(), "paga o valor das esmeraldas ao preço do Balcão");
					} else h.assertTrue(r.is(Items.EMERALD), "sem CubCoin paga esmeraldas");
				}
				if (m.tvilas$emeraldsA() > 0) {
					ItemStack a = o.getCostA();
					if (TVilas.CUBCOIN) {
						long shown = CubCoinTrade.value(a) + (CubCoinTrade.isMoney(o.getCostB()) ? CubCoinTrade.value(o.getCostB()) : 0);
						h.assertTrue(CubCoinTrade.isMoney(a), "o aldeão pede CubCoin");
						h.assertTrue(shown == m.tvilas$emeraldsA() * emerald() || a.has(net.minecraft.core.component.DataComponents.CUSTOM_NAME),
								"mostra o preço exato (ou escrito no nome)");
					} else h.assertTrue(a.is(Items.EMERALD), "sem CubCoin pede esmeraldas");
				}
			}
		}
		if (TVilas.CUBCOIN) h.assertValueEqual(emerald(), CubCoinTrade.emeraldPrice(), "1 esmeralda = o preço do Balcão de Câmbio");
		h.killAllEntitiesOfClass(Villager.class);
		h.succeed();
	}

	@GameTest
	public void moneyChangerIsNotConverted(GameTestHelper h) {
		if (!TVilas.CUBCOIN) {
			h.succeed(); // o Cambista só existe com o CubCoin
			return;
		}
		TestUtil.clean(h);
		TestUtil.floor(h, 4);
		Villager v = trader(h, VilasProfessions.MONEY_CHANGER, new BlockPos(4, 1, 4));
		h.assertTrue(!v.getOffers().isEmpty(), "o Cambista tem trocas");
		for (MerchantOffer o : v.getOffers()) h.assertTrue(!CoinTrades.of(o).tvilas$money(), "a troca do Cambista fica esmeralda ↔ notas");
		h.killAllEntitiesOfClass(Villager.class);
		h.succeed();
	}

	/** Compra no ecrã de trocas a sério: uma nota de 100 CB paga um pão de 40 CB; voltam 60 CB de troco; nada se cria. */
	@GameTest
	public void buyWithChangeNeverCreatesMoney(GameTestHelper h) {
		if (!TVilas.CUBCOIN) {
			h.succeed();
			return;
		}
		Village village = TestUtil.village(h);
		Villager v = trader(h, VilasProfessions.BAKER, new BlockPos(4, 1, 2));
		ServerPlayer p = TestUtil.playerAt(h, new BlockPos(4, 1, 4));
		p.getInventory().clearContent();
		MerchantOffer o = offer(v, true);
		long price = CoinTrades.of(o).tvilas$emeraldsA() * emerald();
		v.setTradingPlayer(p);
		MerchantMenu menu = new MerchantMenu(1, p.getInventory(), v);
		menu.setSelectionHint(v.getOffers().indexOf(o));
		menu.getSlot(0).set(CubCoinTrade.money(10000, 1));
		h.assertTrue(menu.getSlot(2).hasItem(), "uma nota de 100 CB chega para a troca");
		ItemStack got = menu.getSlot(2).remove(64);
		menu.getSlot(2).onTake(p, got);
		long left = menu.getSlot(0).getItem().isEmpty() ? 0 : CubCoinTrade.value(menu.getSlot(0).getItem());
		h.assertTrue(got.is(o.getResult().getItem()), "recebeu o que comprou");
		h.assertValueEqual(CubCoinTrade.balance(p) + left, 10000 - price, "sai o preço e volta o troco (nem mais, nem menos)");
		h.assertValueEqual(o.getUses(), 1, "contou como uma troca");
		h.assertTrue(village.economy.collected + com.tcordeiro.vilas.village.VillageEconomy.debt(village, p.getUUID()) > 0,
				"a troca em CubCoin paga a taxa da vila");
		// sem dinheiro bastante não há troca
		menu.getSlot(0).set(CubCoinTrade.money(1, 3));
		h.assertTrue(!menu.getSlot(2).hasItem() || !menu.getSlot(2).mayPickup(p), "3 cêntimos não pagam");
		menu.getSlot(0).set(ItemStack.EMPTY);
		// esmeraldas já não servem: o aldeão quer CubCoin
		menu.getSlot(0).set(new ItemStack(Items.EMERALD, 64));
		h.assertTrue(!menu.getSlot(2).hasItem(), "com o CubCoin, esmeraldas não pagam a troca");
		menu.getSlot(0).set(ItemStack.EMPTY);
		v.setTradingPlayer(null);
		h.killAllEntitiesOfClass(Villager.class);
		h.succeed();
	}

	/** Vender ao aldeão: 20 trigos dão o valor de 1 esmeralda em CubCoins. */
	@GameTest
	public void sellToVillagerPaysCubCoin(GameTestHelper h) {
		if (!TVilas.CUBCOIN) {
			h.succeed();
			return;
		}
		TestUtil.clean(h);
		TestUtil.floor(h, 4);
		Villager v = trader(h, VilasProfessions.BAKER, new BlockPos(4, 1, 2));
		ServerPlayer p = TestUtil.playerAt(h, new BlockPos(4, 1, 4));
		p.getInventory().clearContent();
		MerchantOffer o = offer(v, false);
		v.setTradingPlayer(p);
		MerchantMenu menu = new MerchantMenu(1, p.getInventory(), v);
		menu.setSelectionHint(v.getOffers().indexOf(o));
		menu.getSlot(0).set(o.getCostA().copy());
		h.assertTrue(menu.getSlot(2).hasItem(), "o trigo paga");
		ItemStack got = menu.getSlot(2).remove(64);
		menu.getSlot(2).onTake(p, got);
		h.assertValueEqual(CubCoinTrade.value(got) + CubCoinTrade.balance(p), CoinTrades.of(o).tvilas$emeraldsResult() * emerald(),
				"recebe o valor das esmeraldas em CubCoins");
		h.assertTrue(menu.getSlot(0).getItem().isEmpty(), "o trigo foi entregue");
		v.setTradingPlayer(null);
		h.killAllEntitiesOfClass(Villager.class);
		h.succeed();
	}

	/** Escolher a troca na lista põe nas casas as notas do inventário que a pagam (só move: o total não muda). */
	@GameTest
	public void pickingATradeMovesTheMoney(GameTestHelper h) {
		if (!TVilas.CUBCOIN) {
			h.succeed();
			return;
		}
		TestUtil.clean(h);
		TestUtil.floor(h, 4);
		Villager v = trader(h, VilasProfessions.BAKER, new BlockPos(4, 1, 2));
		ServerPlayer p = TestUtil.playerAt(h, new BlockPos(4, 1, 4));
		p.getInventory().clearContent();
		p.getInventory().add(CubCoinTrade.money(2000, 5));
		p.getInventory().add(CubCoinTrade.money(5, 3));
		long before = CubCoinTrade.balance(p);
		MerchantOffer o = offer(v, true);
		v.setTradingPlayer(p);
		MerchantMenu menu = new MerchantMenu(1, p.getInventory(), v);
		int idx = v.getOffers().indexOf(o);
		menu.setSelectionHint(idx);
		menu.tryMoveItems(idx);
		ItemStack a = menu.getSlot(0).getItem(), b = menu.getSlot(1).getItem();
		long inSlots = (a.isEmpty() ? 0 : CubCoinTrade.value(a)) + (b.isEmpty() ? 0 : CubCoinTrade.value(b));
		h.assertTrue(inSlots >= CoinTrades.of(o).tvilas$emeraldsA() * emerald(), "as casas pagam o preço");
		h.assertValueEqual(CubCoinTrade.balance(p) + inSlots, before, "só moveu dinheiro (nada criado nem apagado)");
		h.assertTrue(menu.getSlot(2).hasItem(), "a troca fica pronta");
		menu.getSlot(0).set(ItemStack.EMPTY);
		menu.getSlot(1).set(ItemStack.EMPTY);
		v.setTradingPlayer(null);
		h.killAllEntitiesOfClass(Villager.class);
		h.succeed();
	}
}
