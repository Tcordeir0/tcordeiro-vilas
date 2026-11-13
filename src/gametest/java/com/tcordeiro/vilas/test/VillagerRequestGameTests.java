package com.tcordeiro.vilas.test;

import com.tcordeiro.vilas.logic.GrowthRules;
import com.tcordeiro.vilas.logic.MissionRules;
import com.tcordeiro.vilas.village.Batch;
import com.tcordeiro.vilas.village.Growth;
import com.tcordeiro.vilas.village.Mission;
import com.tcordeiro.vilas.village.Village;
import com.tcordeiro.vilas.village.VillageManager;
import com.tcordeiro.vilas.village.VillagerRequests;

import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.ItemStack;

/** 0.3.0 — pedidos dos aldeões comuns («!», uma vez por jogador em cada lote, pagos pela vila) e a vila a crescer com as missões. */
public class VillagerRequestGameTests {
	static java.util.List<Villager> armorers(GameTestHelper h, int n) {
		java.util.List<Villager> out = new java.util.ArrayList<>();
		for (int i = 0; i < n; i++) {
			Villager v = h.spawn(EntityTypes.VILLAGER, new BlockPos(1 + 2 * i, 1, 2));
			v.setNoAi(true);
			v.setVillagerData(v.getVillagerData().withProfession(h.getLevel().registryAccess(), VillagerProfession.ARMORER).withLevel(1));
			out.add(v);
		}
		return out;
	}

	static Villager requester(java.util.List<Villager> all) {
		for (Villager v : all) if (v.entityTags().contains(VillagerRequests.TAG)) return v;
		return null;
	}

	@GameTest
	public void oneInFourAsksAndTheVillagePays(GameTestHelper h) {
		Village v = TestUtil.richVillage(h);
		var level = h.getLevel();
		var all = armorers(h, 4);
		VillagerRequests.tick(level, v);
		h.assertValueEqual(v.civic.batch.requests.size(), 1, "1 em cada 4 aldeões com profissão faz um pedido");
		Villager r = requester(all);
		h.assertTrue(r != null && r.hasCustomName() && r.getCustomName().getString().equals("!"), "mostra um «!» por cima da cabeça");
		Mission m = v.civic.batch.requests.get(r.getUUID());
		h.assertTrue(m.target().equals("minecraft:iron_ingot") || m.target().equals("minecraft:coal"), "o Armeiro pede ferro ou carvão: " + m.target());
		ServerPlayer p = TestUtil.playerAt(h, new BlockPos(4, 1, 3));
		p.getInventory().clearContent();
		p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		h.assertValueEqual(UseEntityCallback.EVENT.invoker().interact(p, level, InteractionHand.MAIN_HAND, r, null), InteractionResult.SUCCESS,
				"clicar abre o pedido");
		p.setShiftKeyDown(true);
		h.assertValueEqual(UseEntityCallback.EVENT.invoker().interact(p, level, InteractionHand.MAIN_HAND, r, null), InteractionResult.PASS,
				"Shift+clique: as trocas normais");
		p.setShiftKeyDown(false);
		h.assertTrue(!VillagerRequests.deliver(p, r.getUUID()).ok(), "sem os itens não entrega");
		p.getInventory().add(new ItemStack(VillageManager.item(m.target()), 64));
		long before = v.economy.treasury + v.civic.missionFund;
		long reward = MissionRules.rewardCents(m.emeralds(), com.tcordeiro.vilas.VilasConfig.get().emerald_value_cents);
		h.assertTrue(VillagerRequests.deliver(p, r.getUUID()).ok(), "entrega o pedido");
		h.assertValueEqual(v.economy.treasury + v.civic.missionFund, before - reward, "pago pela vila (fundo e tesouraria)");
		h.assertValueEqual(TestUtil.money(p), reward, "o jogador recebe o mesmo (nada criado)");
		h.assertValueEqual(VillageManager.count(p.getInventory(), VillageManager.item(m.target())), 64 - m.count(), "os itens foram entregues");
		h.assertTrue(!VillagerRequests.deliver(p, r.getUUID()).ok(), "uma vez por jogador neste lote");
		h.assertValueEqual(UseEntityCallback.EVENT.invoker().interact(p, level, InteractionHand.MAIN_HAND, r, null), InteractionResult.PASS,
				"quem já fez troca normalmente");
		ServerPlayer other = TestUtil.playerAt(h, new BlockPos(5, 1, 3));
		other.getInventory().clearContent();
		other.getInventory().add(new ItemStack(VillageManager.item(m.target()), 64));
		h.assertTrue(VillagerRequests.deliver(other, r.getUUID()).ok(), "outro jogador também pode");
		h.assertTrue(v.civic.batch.done.contains(Batch.key(p.getUUID(), r.getUUID())), "registado no lote");
		all.forEach(Villager::discard);
		h.succeed();
	}

	@GameTest
	public void bangOnlyWithMoneyAndRenewsWithTheBatch(GameTestHelper h) {
		Village v = TestUtil.village(h);
		var level = h.getLevel();
		var all = armorers(h, 4);
		v.economy.treasury = 0;
		v.civic.missionFund = 0;
		VillagerRequests.tick(level, v);
		h.assertValueEqual(v.civic.batch.requests.size(), 1, "o pedido existe");
		h.assertTrue(requester(all) == null, "sem dinheiro na vila, sem «!»");
		v.economy.treasury = 1_000_000;
		VillagerRequests.tick(level, v);
		Villager r = requester(all);
		h.assertTrue(r != null, "com dinheiro, aparece o «!»");
		long id = v.civic.batch.requests.get(r.getUUID()).id();
		v.civic.batch.id--; // passaram 4 dias: lote novo
		VillagerRequests.tick(level, v);
		h.assertValueEqual(v.civic.batch.requests.size(), 1, "um pedido novo");
		h.assertTrue(v.civic.batch.requests.values().stream().noneMatch(m -> m.id() == id), "o pedido antigo acabou");
		int named = 0;
		for (Villager a : all) if (a.entityTags().contains(VillagerRequests.TAG)) named++;
		h.assertValueEqual(named, 1, "só o aldeão do lote novo tem o «!»");
		all.forEach(Villager::discard);
		h.succeed();
	}

	/** Cada missão dá prestígio; a cada 3 chega um morador novo, a pé, pela borda — se houver cama livre; nunca acima do teto. */
	@GameTest
	public void missionsBringNewResidents(GameTestHelper h) {
		Village v = TestUtil.richVillage(h);
		var level = h.getLevel();
		EconomyGameTests.bed(h, new BlockPos(2, 1, 6));
		ServerPlayer p = TestUtil.playerAt(h, new BlockPos(4, 1, 4));
		p.getInventory().clearContent();
		p.getInventory().add(new ItemStack(net.minecraft.world.item.Items.WHEAT, 64));
		int per = com.tcordeiro.vilas.VilasConfig.get().prestige_per_resident;
		for (int i = 0; i < per; i++) {
			VillageManager.setMission(level, v, 0, new MissionRules.Template(MissionRules.Kind.DELIVER, "minecraft:wheat", 2, 1, 1, 1));
			h.assertTrue(VillageManager.deliver(p, v, 0).ok(), "missão " + (i + 1));
			if (i < per - 1) h.assertValueEqual(v.civic.prestige, i + 1, "prestígio sobe");
		}
		var newcomers = level.getEntitiesOfClass(Villager.class, new net.minecraft.world.phys.AABB(v.center).inflate(12), e -> e.entityTags().contains(Growth.TAG));
		h.assertValueEqual(newcomers.size(), 1, "à 3.ª missão chega um morador novo");
		h.assertValueEqual(v.civic.prestige, 0, "o prestígio foi gasto");
		// teto de população: com a vila cheia não chega ninguém
		var cfg = com.tcordeiro.vilas.VilasConfig.get();
		int cap = cfg.population_cap;
		try {
			cfg.population_cap = 0;
			v.civic.prestige = per;
			h.assertValueEqual(Growth.step(level, v), GrowthRules.Step.FULL, "vila cheia: ninguém chega");
		} finally {
			cfg.population_cap = cap;
		}
		newcomers.forEach(net.minecraft.world.entity.Entity::discard);
		h.succeed();
	}
}
