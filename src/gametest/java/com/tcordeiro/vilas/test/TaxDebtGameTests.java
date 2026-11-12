package com.tcordeiro.vilas.test;

import com.tcordeiro.vilas.TVilas;
import com.tcordeiro.vilas.VilasConfig;
import com.tcordeiro.vilas.logic.TreasuryRules;
import com.tcordeiro.vilas.village.Debts;
import com.tcordeiro.vilas.village.Lot;
import com.tcordeiro.vilas.village.Village;
import com.tcordeiro.vilas.village.VillageData;
import com.tcordeiro.vilas.village.VillageEconomy;
import com.tcordeiro.vilas.village.VillageManager;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;

/**
 * 0.2.0 — o Prefeito ajusta o imposto (sobe com as obras pagas, até ao teto; baixa com a tesouraria cheia) e as dívidas
 * congelam o terreno sem apagar NADA; pagar no quadro descongela.
 */
public class TaxDebtGameTests {
	@GameTest
	public void taxRisesWithPaidWorksAndRespectsTheCeiling(GameTestHelper h) {
		Village v = TestUtil.village(h);
		VilasConfig c = VilasConfig.get();
		h.assertValueEqual(VillageEconomy.taxRate(v).percent(), c.trade_tax_percent, "taxa base sem obras");
		// uma melhoria paga pela tesouraria (morador novo, havia camas livres) sobe a taxa meio ponto
		for (int i = 0; i < 4; i++) EconomyGameTests.bed(h, new BlockPos(1 + 2 * i, 1, 1));
		for (int i = 0; i < 3; i++) h.spawn(EntityTypes.VILLAGER, new BlockPos(1 + 3 * i, 1, 7)).setNoAi(true);
		long day = VillageEconomy.day(h.getLevel()) + 5000;
		VillageEconomy.daily(h.getLevel(), v, day);
		v.economy.treasury = 5_000;
		VillageEconomy.daily(h.getLevel(), v, day + 1);
		h.assertValueEqual(v.extra.structures, 1, "uma obra paga");
		h.assertValueEqual(VillageEconomy.taxRate(v).percent(), c.trade_tax_percent + c.tax_per_structure_percent, "a taxa subiu");
		// muitas obras: nunca passa do teto, e as trocas cobram o teto
		v.extra.structures = 50;
		h.assertValueEqual(VillageEconomy.taxRate(v).percent(), c.tax_cap_percent, "teto");
		ServerPlayer p = TestUtil.playerAt(h, new BlockPos(4, 1, 4));
		p.getInventory().clearContent();
		if (TVilas.CUBCOIN) CubCoinTrade.give(p, 100_000);
		Villager trader = h.spawn(EntityTypes.VILLAGER, new BlockPos(6, 1, 6));
		trader.setNoAi(true);
		trader.setTradingPlayer(p);
		long before = v.economy.treasury, debtBefore = VillageEconomy.debt(v, p.getUUID());
		trader.notifyTrade(new MerchantOffer(new ItemCost(Items.EMERALD, 5), new ItemStack(Items.BREAD, 6), 12, 1, 0.05f));
		long expected = TreasuryRules.tradeTax(5 * c.emerald_value_cents, c.tax_cap_percent);
		long charged = TVilas.CUBCOIN ? v.economy.treasury - before : VillageEconomy.debt(v, p.getUUID()) - debtBefore;
		h.assertValueEqual(charged, expected, "a troca paga a taxa do teto");
		// tesouraria cheia: o Prefeito volta à base
		v.economy.treasury = c.treasury_full_cents;
		h.assertValueEqual(VillageEconomy.taxRate(v).percent(), Math.min(c.trade_tax_percent, c.tax_cap_percent), "tesouraria cheia: taxa base");
		trader.setTradingPlayer(null);
		h.killAllEntitiesOfClass(Villager.class);
		h.succeed();
	}

	@GameTest
	public void debtFreezesTheLotWithoutRemovingAnythingAndPayingUnfreezes(GameTestHelper h) {
		Village v = TestUtil.village(h);
		ServerPlayer owner = TestUtil.playerAt(h, new BlockPos(4, 1, 4));
		owner.getInventory().clearContent();
		Lot lot = TestUtil.lotOverTest(h, v, owner);
		// o que o dono tem no terreno
		BlockPos chest = new BlockPos(2, 1, 2), wall = new BlockPos(6, 1, 2);
		h.setBlock(chest, Blocks.CHEST);
		((ChestBlockEntity) h.getBlockEntity(chest, ChestBlockEntity.class)).setItem(0, new ItemStack(Items.DIAMOND, 5));
		h.setBlock(wall, Blocks.BRICKS);
		long debt = Debts.minCents() + 1000;
		v.economy.debts.put(owner.getUUID(), debt);
		long day = VillageEconomy.day(h.getLevel()) + 6000;
		int days = VilasConfig.get().debt_freeze_days;
		for (int d = 0; d < days; d++) Debts.daily(h.getLevel(), v, day + d);
		h.assertTrue(!Debts.frozen(v, owner.getUUID()), "na véspera ainda não congela");
		h.assertTrue(v.extra.warned.contains(owner.getUUID()), "mas avisa");
		Debts.daily(h.getLevel(), v, day + days);
		h.assertTrue(Debts.frozen(v, owner.getUUID()), "congelado depois de " + days + " dias");
		// nada foi apagado nem tirado
		h.assertBlockPresent(Blocks.CHEST, chest);
		h.assertValueEqual(((ChestBlockEntity) h.getBlockEntity(chest, ChestBlockEntity.class)).getItem(0).getCount(), 5, "o baú tem tudo");
		h.assertBlockPresent(Blocks.BRICKS, wall);
		h.assertTrue(VillageData.get(h.getLevel()).lots.contains(lot), "o terreno continua dele");
		h.assertValueEqual(lot.owner, owner.getUUID(), "dono");
		// mas não pode construir nem partir
		h.assertTrue(!VillageManager.mayModify(h.getLevel(), h.absolutePos(wall), owner), "congelado: não parte");
		TestUtil.useOnTop(h, owner, new ItemStack(Items.OAK_PLANKS), new BlockPos(4, 1, 6));
		h.assertBlockNotPresent(Blocks.OAK_PLANKS, new BlockPos(4, 1, 6));
		// pagar no quadro descongela
		owner.getInventory().clearContent();
		var fail = Debts.pay(owner, v);
		h.assertTrue(!fail.ok(), "sem dinheiro não paga");
		if (TVilas.CUBCOIN) CubCoinTrade.give(owner, debt);
		else owner.getInventory().add(new ItemStack(Items.EMERALD, 64));
		h.assertTrue(Debts.pay(owner, v).ok(), "pagou no quadro");
		h.assertTrue(!Debts.frozen(v, owner.getUUID()), "descongelou");
		h.assertValueEqual(VillageEconomy.debt(v, owner.getUUID()), 0L, "sem dívida");
		h.assertTrue(VillageManager.mayModify(h.getLevel(), h.absolutePos(wall), owner), "já pode construir");
		TestUtil.useOnTop(h, owner, new ItemStack(Items.OAK_PLANKS), new BlockPos(4, 1, 6));
		h.assertBlockPresent(Blocks.OAK_PLANKS, new BlockPos(4, 1, 6));
		VillageData.get(h.getLevel()).lots.remove(lot);
		owner.discard();
		h.succeed();
	}
}
