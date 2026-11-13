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
		Village v = TestUtil.richVillage(h);
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
		h.assertValueEqual(TestUtil.moneyInEmeralds(p), 3L, "paga 3 esmeraldas");
		h.assertValueEqual(v.reputationOf(p.getUUID()), 10, "dá 10 de reputação nesta vila");
		h.assertTrue(v.missions.get(0).id() == m.id(), "0.3.0: a missão fica no quadro até ao lote seguinte");
		h.assertValueEqual(v.missions.get(0).kind(), MissionRules.Kind.DELIVER, "a casa 1 continua a ser de trazer");
		h.assertValueEqual(hasCubCoins(p), TVilas.CUBCOIN, "CubCoins só com o CubCoin instalado");
		h.succeed();
	}

	@GameTest
	public void decorateCountsOnlyAfterAcceptAndWhileThere(GameTestHelper h) {
		Village v = TestUtil.richVillage(h);
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
		h.assertValueEqual(TestUtil.moneyInEmeralds(p), 2L, "paga 2 esmeraldas");
		h.assertValueEqual(v.reputationOf(p.getUUID()), 8, "dá 8 de reputação");
		h.succeed();
	}

	/** Um saqueador de uma invasão a sério nesta vila (a invasão não está nos registos do mundo: só lhe dá o «raid»). */
	static net.minecraft.world.entity.monster.illager.Pillager raider(GameTestHelper h, BlockPos rel) {
		var raid = new net.minecraft.world.entity.raid.Raid(h.absolutePos(new BlockPos(4, 1, 4)), net.minecraft.world.Difficulty.NORMAL);
		var p = h.spawn(EntityTypes.PILLAGER, rel);
		p.setCurrentRaid(raid);
		return p;
	}

	@GameTest
	public void defendCountsOnlyInARealRaid(GameTestHelper h) {
		Village v = TestUtil.richVillage(h);
		ServerPlayer p = TestUtil.playerAt(h, new BlockPos(4, 1, 4));
		p.getInventory().clearContent();
		VillageManager.setMission(h.getLevel(), v, 2, new MissionRules.Template(MissionRules.Kind.DEFEND, MissionRules.INVADERS, 1, 5, 20, 1));
		VillageManager.accept(p, v, 2);
		Husk calm = h.spawn(EntityTypes.HUSK, new BlockPos(2, 1, 2));
		calm.hurtServer(h.getLevel(), h.getLevel().damageSources().playerAttack(p), 1000);
		h.assertValueEqual(VillageManager.progress(p, v, v.missions.get(2)), 0, "sem invasão não conta");
		// A1 (0.3.0): o alarme sozinho (monstros por perto) já não chega — uma quinta de monstros dava missões infinitas
		v.alarm.on = true;
		Husk husk = h.spawn(EntityTypes.HUSK, new BlockPos(6, 1, 6));
		husk.hurtServer(h.getLevel(), h.getLevel().damageSources().playerAttack(p), 1000);
		var loose = h.spawn(EntityTypes.PILLAGER, new BlockPos(6, 1, 2));
		loose.hurtServer(h.getLevel(), h.getLevel().damageSources().playerAttack(p), 1000);
		h.assertValueEqual(VillageManager.progress(p, v, v.missions.get(2)), 0, "alarme com monstros e um saqueador solto: não conta");
		v.alarm.on = false;
		raider(h, new BlockPos(2, 1, 6)).hurtServer(h.getLevel(), h.getLevel().damageSources().playerAttack(p), 1000);
		h.assertValueEqual(VillageManager.progress(p, v, v.missions.get(2)), 1, "invasor de uma invasão nesta vila conta");
		h.assertTrue(VillageManager.deliver(p, v, 2).ok(), "entrega a defesa");
		h.assertValueEqual(TestUtil.moneyInEmeralds(p), 5L, "paga 5 esmeraldas (ou 200 CB)");
		h.assertValueEqual(v.reputationOf(p.getUUID()), 20, "dá 20 de reputação");
		h.succeed();
	}
	static final MissionRules.Template LANTERNS_2 = new MissionRules.Template(MissionRules.Kind.DECORATE, "minecraft:lantern", 2, 2, 8, 1);

	@GameTest
	public void decorateBreakAndReplaceDoesNotPayAgain(GameTestHelper h) {
		Village v = TestUtil.richVillage(h);
		ServerPlayer p = TestUtil.playerAt(h, new BlockPos(4, 1, 4));
		p.getInventory().clearContent();
		BlockPos a = new BlockPos(2, 1, 2), b = new BlockPos(6, 1, 2), c = new BlockPos(2, 1, 6), d = new BlockPos(6, 1, 6);
		VillageManager.setMission(h.getLevel(), v, 1, LANTERNS_2);
		VillageManager.accept(p, v, 1);
		TestUtil.placeWithItem(h, p, new ItemStack(Items.LANTERN), a);
		TestUtil.placeWithItem(h, p, new ItemStack(Items.LANTERN), b);
		h.assertTrue(VillageManager.deliver(p, v, 1).ok(), "1.ª entrega");
		h.assertValueEqual(TestUtil.moneyInEmeralds(p), 2L, "pagou 2");

		// a mesma missão outra vez: partir e repor as lanternas nos mesmos sítios não conta
		VillageManager.setMission(h.getLevel(), v, 1, LANTERNS_2);
		VillageManager.accept(p, v, 1);
		h.setBlock(a, Blocks.AIR);
		h.setBlock(b, Blocks.AIR);
		TestUtil.placeWithItem(h, p, new ItemStack(Items.LANTERN), a);
		TestUtil.placeWithItem(h, p, new ItemStack(Items.LANTERN), b);
		h.assertBlockPresent(Blocks.LANTERN, a);
		h.assertValueEqual(VillageManager.progress(p, v, v.missions.get(1)), 0, "posições já pagas não contam");
		h.assertTrue(!VillageManager.deliver(p, v, 1).ok(), "partir e repor não paga outra vez");
		h.assertValueEqual(TestUtil.moneyInEmeralds(p), 2L, "continua com 2");
		// em sítios novos conta normalmente
		TestUtil.placeWithItem(h, p, new ItemStack(Items.LANTERN), c);
		TestUtil.placeWithItem(h, p, new ItemStack(Items.LANTERN), d);
		h.assertTrue(VillageManager.deliver(p, v, 1).ok(), "sítios novos pagam");
		h.assertValueEqual(TestUtil.moneyInEmeralds(p), 4L, "agora 4");
		h.succeed();
	}

	@GameTest
	public void samePositionDoesNotCountInTwoMissions(GameTestHelper h) {
		Village v = TestUtil.richVillage(h);
		ServerPlayer first = TestUtil.playerAt(h, new BlockPos(3, 1, 4));
		ServerPlayer second = TestUtil.playerAt(h, new BlockPos(5, 1, 4));
		first.getInventory().clearContent();
		second.getInventory().clearContent();
		BlockPos a = new BlockPos(2, 1, 2), b = new BlockPos(6, 1, 2), c = new BlockPos(2, 1, 6), d = new BlockPos(6, 1, 6);
		VillageManager.setMission(h.getLevel(), v, 1, LANTERNS_2);
		VillageManager.accept(first, v, 1);
		TestUtil.placeWithItem(h, first, new ItemStack(Items.LANTERN), a);
		TestUtil.placeWithItem(h, first, new ItemStack(Items.LANTERN), b);
		h.assertTrue(VillageManager.deliver(first, v, 1).ok(), "o primeiro entrega");

		// noutra missão, outro jogador reaproveita as posições pagas (parte e volta a pôr): não conta
		VillageManager.setMission(h.getLevel(), v, 1, LANTERNS_2);
		VillageManager.accept(second, v, 1);
		h.setBlock(a, Blocks.AIR);
		h.setBlock(b, Blocks.AIR);
		TestUtil.placeWithItem(h, second, new ItemStack(Items.LANTERN), a);
		TestUtil.placeWithItem(h, second, new ItemStack(Items.LANTERN), b);
		TestUtil.placeWithItem(h, second, new ItemStack(Items.LANTERN), c);
		h.assertValueEqual(VillageManager.progress(second, v, v.missions.get(1)), 1, "só a posição nova conta");
		h.assertTrue(!VillageManager.deliver(second, v, 1).ok(), "1 de 2");
		TestUtil.placeWithItem(h, second, new ItemStack(Items.LANTERN), d);
		h.assertTrue(VillageManager.deliver(second, v, 1).ok(), "com 2 posições novas entrega");
		h.assertValueEqual(TestUtil.moneyInEmeralds(second), 2L, "paga 2 ao segundo");
		h.succeed();
	}

	@GameTest
	public void spawnerMobsDoNotCountForDefense(GameTestHelper h) {
		Village v = TestUtil.richVillage(h);
		ServerPlayer p = TestUtil.playerAt(h, new BlockPos(4, 1, 4));
		VillageManager.setMission(h.getLevel(), v, 2, new MissionRules.Template(MissionRules.Kind.DEFEND, MissionRules.INVADERS, 2, 5, 20, 1));
		VillageManager.accept(p, v, 2);
		var farmed = raider(h, new BlockPos(2, 1, 2));
		farmed.addTag(VillageManager.FROM_SPAWNER_TAG);
		farmed.hurtServer(h.getLevel(), h.getLevel().damageSources().playerAttack(p), 1000);
		h.assertValueEqual(VillageManager.progress(p, v, v.missions.get(2)), 0, "monstro de gerador não conta");
		raider(h, new BlockPos(6, 1, 6)).hurtServer(h.getLevel(), h.getLevel().damageSources().playerAttack(p), 1000);
		h.assertValueEqual(VillageManager.progress(p, v, v.missions.get(2)), 1, "invasor normal conta");
		h.succeed();
	}
	@GameTest
	public void spawnerMarksItsMobs(GameTestHelper h) {
		// a etiqueta vem do gerador, seja qual for o monstro: um gerador de porcos (nascem à luz do dia, em relva) chega para o testar
		TestUtil.clean(h);
		for (int x = 0; x <= 8; x++)
			for (int z = 0; z <= 8; z++) h.setBlock(new BlockPos(x, 0, z), Blocks.GRASS_BLOCK);
		TestUtil.playerAt(h, new BlockPos(4, 1, 1)); // o gerador só trabalha com um jogador perto
		var level = h.getLevel();
		var rules = level.getGameRules();
		boolean enabled = rules.get(net.minecraft.world.level.gamerules.GameRules.SPAWNER_BLOCKS_WORK);
		rules.set(net.minecraft.world.level.gamerules.GameRules.SPAWNER_BLOCKS_WORK, true, level.getServer());
		BlockPos spawner = new BlockPos(4, 2, 4);
		h.setBlock(new BlockPos(4, 1, 4), Blocks.STONE);
		h.setBlock(spawner, Blocks.SPAWNER);
		BlockPos abs = h.absolutePos(spawner);
		var be = (net.minecraft.world.level.block.entity.SpawnerBlockEntity) level.getBlockEntity(abs);
		be.setEntityId(EntityTypes.PIG, level.getRandom());
		try {
			h.assertTrue(level.hasNearbyAlivePlayer(abs.getX(), abs.getY(), abs.getZ(), 16), "há um jogador perto do gerador");
			// os ticks do gerador, à mão (o primeiro lote sai ao fim de 20)
			for (int i = 0; i < 400 && h.getEntities(EntityTypes.PIG).isEmpty(); i++) be.getSpawner().serverTick(level, abs);
			var pigs = h.getEntities(EntityTypes.PIG);
			h.assertTrue(!pigs.isEmpty(), "o gerador não fez porcos");
			for (var pig : pigs) h.assertTrue(pig.entityTags().contains(VillageManager.FROM_SPAWNER_TAG), "bicho do gerador marcado");
			for (var pig : pigs) pig.discard();
		} finally {
			rules.set(net.minecraft.world.level.gamerules.GameRules.SPAWNER_BLOCKS_WORK, enabled, level.getServer());
			h.setBlock(spawner, Blocks.AIR);
		}
		h.succeed();
	}
}
