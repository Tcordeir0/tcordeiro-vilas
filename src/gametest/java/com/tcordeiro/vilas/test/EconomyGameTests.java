package com.tcordeiro.vilas.test;

import com.tcordeiro.vilas.TVilas;
import com.tcordeiro.vilas.VilasConfig;
import com.tcordeiro.vilas.logic.TreasuryRules;
import com.tcordeiro.vilas.logic.VillageRules;
import com.tcordeiro.vilas.registry.VilasBlocks;
import com.tcordeiro.vilas.village.Village;
import com.tcordeiro.vilas.village.VillageData;
import com.tcordeiro.vilas.village.VillageEconomy;
import com.tcordeiro.vilas.village.VillageManager;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BedPart;

/**
 * Impostos e tesouraria: cama/dia nos terrenos, 2 % nas trocas (aldeões e entre jogadores), a tesouraria paga melhorias, o
 * território de 400 blocos e uma vila sem aldeões que renasce. Com o CubCoin paga-se em moedas; sem ele fica em dívida até
 * dar uma esmeralda (./gradlew runGametest -PnoCubcoin corre o outro ramo).
 */
public class EconomyGameTests {
	/** Cama (cabeceira + pés), virada a norte. */
	static void bed(GameTestHelper h, BlockPos head) {
		var s = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getValue(net.minecraft.resources.Identifier.withDefaultNamespace("red_bed")).defaultBlockState().setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH);
		h.setBlock(head, s.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.BED_PART, BedPart.HEAD));
		h.setBlock(head.south(), s.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.BED_PART, BedPart.FOOT));
	}

	/** O imposto entrou na tesouraria (CubCoin) ou ficou em dívida (sem CubCoin: menos de uma esmeralda). */
	static void assertCharged(GameTestHelper h, Village v, ServerPlayer p, long before, long cents) {
		if (TVilas.CUBCOIN) {
			h.assertValueEqual(v.economy.treasury - before, cents, "entrou na tesouraria");
			h.assertValueEqual(VillageEconomy.debt(v, p.getUUID()), 0L, "sem dívida");
		} else {
			h.assertValueEqual(VillageEconomy.debt(v, p.getUUID()), cents, "fica em dívida até dar uma esmeralda");
		}
	}

	@GameTest
	public void bedTaxPerBedPerDay(GameTestHelper h) {
		Village v = TestUtil.village(h);
		ServerPlayer owner = TestUtil.playerAt(h, new BlockPos(4, 1, 4));
		owner.getInventory().clearContent();
		if (TVilas.CUBCOIN) CubCoinTrade.give(owner, 100);
		TestUtil.lotOverTest(h, v, owner);
		bed(h, new BlockPos(2, 1, 2));
		bed(h, new BlockPos(6, 1, 2));
		h.assertValueEqual(VillageEconomy.bedsIn(h.getLevel(), VillageManager.lotsOf(h.getLevel(), v).get(0)), 2, "2 camas no terreno");
		long day = VillageEconomy.day(h.getLevel()) + 1000;
		VillageEconomy.daily(h.getLevel(), v, day);
		long before = v.economy.treasury;
		VillageEconomy.daily(h.getLevel(), v, day + 3);
		assertCharged(h, v, owner, before, 2 * 3 * VilasConfig.get().bed_tax_cents);
		if (TVilas.CUBCOIN) h.assertValueEqual(CubCoinTrade.balance(owner), 100 - 6L, "pagou 6¢ (2 camas × 3 dias)");
		h.killAllEntitiesOfClass(Villager.class);
		VillageData.get(h.getLevel()).lots.removeIf(l -> l.village.equals(v.id));
		h.succeed();
	}

	@GameTest
	public void villagerTradePaysTwoPercent(GameTestHelper h) {
		Village v = TestUtil.village(h);
		ServerPlayer p = TestUtil.playerAt(h, new BlockPos(4, 1, 4));
		p.getInventory().clearContent();
		if (TVilas.CUBCOIN) CubCoinTrade.give(p, 1000);
		Villager baker = h.spawn(EntityTypes.VILLAGER, new BlockPos(2, 1, 2));
		baker.setNoAi(true);
		baker.setTradingPlayer(p);
		long before = v.economy.treasury;
		baker.notifyTrade(new MerchantOffer(new ItemCost(Items.EMERALD, 5), new ItemStack(Items.BREAD, 6), 12, 1, 0.05f));
		long expected = TreasuryRules.tradeTax(5 * VilasConfig.get().emerald_value_cents, 2.0);
		h.assertValueEqual(expected, 400L, "2 % de 5 esmeraldas (200 CB) = 4 CB");
		assertCharged(h, v, p, before, expected);
		baker.setTradingPlayer(null);
		h.killAllEntitiesOfClass(Villager.class);
		h.succeed();
	}

	@GameTest
	public void playerSaleInTerritoryPaysTwoPercent(GameTestHelper h) {
		if (!TVilas.CUBCOIN) {
			h.succeed(); // sem o CubCoin não há vendas entre jogadores
			return;
		}
		Village v = TestUtil.village(h);
		ServerPlayer seller = TestUtil.playerAt(h, new BlockPos(3, 1, 4));
		ServerPlayer buyer = TestUtil.playerAt(h, new BlockPos(5, 1, 4));
		seller.getInventory().clearContent();
		buyer.getInventory().clearContent();
		CubCoinTrade.give(buyer, 20_000);
		long before = v.economy.treasury;
		h.assertTrue(CubCoinTrade.sell(seller, buyer, new ItemStack(Items.DIAMOND, 3), 10_000), "venda de 100 CB aceite");
		h.assertValueEqual(v.economy.treasury - before, 200L, "a vila cobrou 2 % (2 CB)");
		h.assertValueEqual(CubCoinTrade.balance(seller), 10_000L - 200, "o vendedor recebeu 100 CB e pagou 2 CB");
		h.succeed();
	}

	@GameTest
	public void treasuryPaysAnUpgrade(GameTestHelper h) {
		Village v = TestUtil.village(h);
		for (int i = 0; i < 3; i++) h.spawn(EntityTypes.VILLAGER, new BlockPos(1 + 3 * i, 1, 7)).setNoAi(true);
		for (int i = 0; i < 4; i++) bed(h, new BlockPos(1 + 2 * i, 1, 1));
		long day = VillageEconomy.day(h.getLevel()) + 2000;
		VillageEconomy.daily(h.getLevel(), v, day);
		v.economy.treasury = 10_000;
		VillageEconomy.daily(h.getLevel(), v, day + 1);
		var cost = TreasuryRules.Upgrade.RESIDENT.cost;
		h.assertValueEqual(v.economy.treasury, 10_000 - cost, "pagou um morador novo (camas livres)");
		h.assertValueEqual(v.economy.expenses.get(v.economy.expenses.size() - 1).what(), TreasuryRules.Upgrade.RESIDENT.key(), "gasto registado");
		h.assertValueEqual(VillageManager.countPopulation(h.getLevel(), v), 4, "um morador a mais");
		// todas as camas ocupadas: o próximo gasto é um golem da vila
		VillageEconomy.daily(h.getLevel(), v, day + 2);
		h.assertValueEqual(v.economy.treasury, 10_000 - cost - TreasuryRules.Upgrade.GOLEM.cost, "pagou um golem");
		h.assertTrue(VillageEconomy.homeGolems(h.getLevel(), v) >= 1, "há golem da vila");
		h.killAllEntitiesOfClass(Villager.class);
		h.killAllEntitiesOfClass(IronGolem.class);
		h.succeed();
	}

	@GameTest
	public void territoryIs400Blocks(GameTestHelper h) {
		h.assertValueEqual(VilasConfig.get().radius(), VillageRules.DEFAULT_RADIUS, "raio da configuração por omissão");
		h.assertTrue(VilasConfig.get().radius() >= 400, "pelo menos 400 blocos");
		BlockPos center = h.absolutePos(new BlockPos(4, 1, 4)).offset(-40000, 0, 0);
		VillageData d = VillageData.get(h.getLevel());
		d.villages.removeIf(x -> x.center.distManhattan(center) < 2000);
		Village v = VillageManager.found(h.getLevel(), center, VilasConfig.get().radius());
		h.assertTrue(v.covers(center.getX() + 400, center.getZ()), "a 400 blocos ainda é a vila");
		h.assertTrue(!v.covers(center.getX() + 401, center.getZ()), "a 401 já não");
		ServerPlayer p = h.makeMockServerPlayerInLevel();
		p.getInventory().clearContent();
		p.getInventory().add(new ItemStack(Items.EMERALD, 64));
		p.setPos(center.getX() + 380.5, center.getY(), center.getZ() + 0.5);
		h.assertTrue(VillageManager.buyLot(p).ok(), "compra um terreno a 380 blocos da Prefeitura");
		d.lots.removeIf(l -> l.village.equals(v.id));
		d.villages.remove(v);
		h.succeed();
	}

	@GameTest
	public void emptyVillageIsReborn(GameTestHelper h) {
		TestUtil.clean(h);
		TestUtil.floor(h, 4);
		h.setBlock(new BlockPos(4, 1, 7), Blocks.BELL);
		bed(h, new BlockPos(1, 1, 1));
		bed(h, new BlockPos(7, 1, 1));
		BlockPos desk = new BlockPos(4, 1, 4);
		h.setBlock(desk, VilasBlocks.MAYOR_DESK);
		Village v = VillageManager.found(h.getLevel(), h.absolutePos(desk), TestUtil.RADIUS);
		h.assertValueEqual(VillageManager.countPopulation(h.getLevel(), v), 0, "vila sem aldeões");
		long now = h.getLevel().getGameTime();
		Villager mayor = VillageEconomy.ensureMayor(h.getLevel(), v, now);
		h.assertTrue(mayor != null && VillageManager.isMayor(mayor), "chegou um Prefeito");
		h.assertTrue(VillageEconomy.ensureMayor(h.getLevel(), v, now + 10) == null, "só um Prefeito");
		long day = VillageEconomy.day(h.getLevel()) + 3000;
		VillageEconomy.daily(h.getLevel(), v, day);
		VillageEconomy.daily(h.getLevel(), v, day + 1);
		h.assertValueEqual(VillageManager.countPopulation(h.getLevel(), v), 2, "chegou um morador (havia camas livres)");
		h.assertValueEqual(v.economy.expenses.get(v.economy.expenses.size() - 1).what(), "tvilas.upgrade.immigrant", "morador grátis");
		VillageEconomy.daily(h.getLevel(), v, day + 2);
		h.assertValueEqual(VillageManager.countPopulation(h.getLevel(), v), 2, "2 camas: não chegam mais de graça");
		h.setBlock(desk, Blocks.AIR);
		h.killAllEntitiesOfClass(Villager.class);
		h.succeed();
	}
}
