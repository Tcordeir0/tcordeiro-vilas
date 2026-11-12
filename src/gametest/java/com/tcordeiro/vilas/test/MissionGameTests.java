package com.tcordeiro.vilas.test;

import com.tcordeiro.vilas.TVilas;
import com.tcordeiro.vilas.logic.MissionRules;
import com.tcordeiro.vilas.village.Mission;
import com.tcordeiro.vilas.village.Village;
import com.tcordeiro.vilas.village.VillageManager;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/** Missões: aceitar e entregar paga e dá reputação; decorar só conta depois de aceitar e com os blocos lá; defender só no alarme. */
public class MissionGameTests {
	static int count(ServerPlayer p, net.minecraft.world.item.Item item) {
		return VillageManager.count(p.getInventory(), item);
	}

	static boolean hasCubCoins(ServerPlayer p) {
		for (int i = 0; i < p.getInventory().getContainerSize(); i++)
			if (BuiltInRegistries.ITEM.getKey(p.getInventory().getItem(i).getItem()).getNamespace().equals("cubcoin")) return true;
		return false;
	}

	@GameTest
	public void deliverPaysAndGivesReputation(GameTestHelper h) {
		Village v = TestUtil.village(h);
		ServerPlayer p = TestUtil.playerAt(h, new BlockPos(4, 1, 4));
		p.getInventory().clearContent();
		Mission m = VillageManager.setMission(h.getLevel(), v, 0, new MissionRules.Template(MissionRules.Kind.DELIVER, "minecraft:wheat", 5, 3, 10, 1));
		p.getInventory().add(new ItemStack(Items.WHEAT, 3));
		h.assertTrue(!VillageManager.deliver(p, v, 0).ok(), "com 3 de 5 não entrega");
		h.assertValueEqual(count(p, Items.WHEAT), 3, "não tira nada se falhar");
		p.getInventory().add(new ItemStack(Items.WHEAT, 4));
		var r = VillageManager.deliver(p, v, 0);
		h.assertTrue(r.ok(), "entrega com 7 de 5");
		h.assertValueEqual(count(p, Items.WHEAT), 2, "tira só os 5 pedidos");
		h.assertValueEqual(count(p, Items.EMERALD), 3, "paga 3 esmeraldas");
		h.assertValueEqual(v.reputationOf(p.getUUID()), 10, "dá 10 de reputação nesta vila");
		h.assertTrue(v.missions.get(0).id() != m.id(), "a missão entregue é trocada por outra");
		h.assertValueEqual(v.missions.get(0).kind(), MissionRules.Kind.DELIVER, "a casa 1 continua a ser de trazer");
		h.assertValueEqual(hasCubCoins(p), TVilas.CUBCOIN, "CubCoins só com o CubCoin instalado");
		h.succeed();
	}

	@GameTest
	public void decorateCountsOnlyAfterAcceptAndWhileThere(GameTestHelper h) {
		Village v = TestUtil.village(h);
		ServerPlayer p = TestUtil.playerAt(h, new BlockPos(4, 1, 4));
		p.getInventory().clearContent();
		VillageManager.setMission(h.getLevel(), v, 1, new MissionRules.Template(MissionRules.Kind.DECORATE, "minecraft:lantern", 2, 2, 8, 1));
		BlockPos a = new BlockPos(2, 1, 2), b = new BlockPos(6, 1, 2), c = new BlockPos(2, 1, 6);
		TestUtil.placeWithItem(h, p, new ItemStack(Items.LANTERN), a);
		h.assertBlockPresent(Blocks.LANTERN, a);
		h.assertTrue(!VillageManager.deliver(p, v, 1).ok(), "sem aceitar não entrega");
		h.assertTrue(VillageManager.accept(p, v, 1).ok(), "aceita");
		h.assertTrue(!VillageManager.accept(p, v, 1).ok(), "não aceita duas vezes");
		h.assertValueEqual(VillageManager.progress(p, v, v.missions.get(1)), 0, "a lanterna posta antes de aceitar não conta");
		TestUtil.placeWithItem(h, p, new ItemStack(Items.LANTERN), b);
		TestUtil.placeWithItem(h, p, new ItemStack(Items.LANTERN), c);
		h.assertValueEqual(VillageManager.progress(p, v, v.missions.get(1)), 2, "duas postas depois de aceitar");
		h.setBlock(c, Blocks.AIR);
		h.assertValueEqual(VillageManager.progress(p, v, v.missions.get(1)), 1, "partida uma, conta uma");
		h.assertTrue(!VillageManager.deliver(p, v, 1).ok(), "1 de 2 não entrega");
		TestUtil.placeWithItem(h, p, new ItemStack(Items.LANTERN), c);
		h.assertTrue(VillageManager.deliver(p, v, 1).ok(), "entrega com 2 de 2");
		h.assertValueEqual(count(p, Items.EMERALD), 2, "paga 2 esmeraldas");
		h.assertValueEqual(v.reputationOf(p.getUUID()), 8, "dá 8 de reputação");
		h.succeed();
	}

	@GameTest
	public void defendCountsOnlyDuringAlarm(GameTestHelper h) {
		Village v = TestUtil.village(h);
		ServerPlayer p = TestUtil.playerAt(h, new BlockPos(4, 1, 4));
		p.getInventory().clearContent();
		VillageManager.setMission(h.getLevel(), v, 2, new MissionRules.Template(MissionRules.Kind.DEFEND, MissionRules.INVADERS, 1, 5, 20, 1));
		VillageManager.accept(p, v, 2);
		Husk calm = h.spawn(EntityTypes.HUSK, new BlockPos(2, 1, 2));
		calm.hurtServer(h.getLevel(), h.getLevel().damageSources().playerAttack(p), 1000);
		h.assertValueEqual(VillageManager.progress(p, v, v.missions.get(2)), 0, "sem alarme não conta");
		v.alarm.on = true;
		Husk raider = h.spawn(EntityTypes.HUSK, new BlockPos(6, 1, 6));
		raider.hurtServer(h.getLevel(), h.getLevel().damageSources().playerAttack(p), 1000);
		h.assertValueEqual(VillageManager.progress(p, v, v.missions.get(2)), 1, "durante o alarme conta");
		v.alarm.on = false;
		h.assertTrue(VillageManager.deliver(p, v, 2).ok(), "entrega a defesa");
		h.assertValueEqual(count(p, Items.EMERALD), 5, "paga 5 esmeraldas");
		h.assertValueEqual(v.reputationOf(p.getUUID()), 20, "dá 20 de reputação");
		h.succeed();
	}
}
