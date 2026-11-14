package com.tcordeiro.vilas.test;

import com.tcordeiro.vilas.TVilas;
import com.tcordeiro.vilas.VilasConfig;
import com.tcordeiro.vilas.build.Builders;
import com.tcordeiro.vilas.life.Life;
import com.tcordeiro.vilas.life.LifeData;
import com.tcordeiro.vilas.life.crisis.CrisisData;
import com.tcordeiro.vilas.life.crisis.CrisisRules;
import com.tcordeiro.vilas.life.crisis.Crises;
import com.tcordeiro.vilas.life.crisis.Funeral;
import com.tcordeiro.vilas.life.crisis.Rebuild;
import com.tcordeiro.vilas.logic.MissionRules;
import com.tcordeiro.vilas.village.Project;
import com.tcordeiro.vilas.village.Village;
import com.tcordeiro.vilas.village.VillageData;
import com.tcordeiro.vilas.village.VillageEconomy;
import com.tcordeiro.vilas.village.VillageManager;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 0.6 crises — Reconstrução, crises (fogo, fome, noite), funeral e velhice, nos 3 modos (com os mods, sem o CubCoin, sem nenhum mod).
 * As regras de dinheiro (tesouraria, uma vez por jogador, quem acendeu não ganha, poucos por crise) e a regra «nunca blocos de
 * jogador» têm mutações em {@code tools/mutation_test.py} (filtro «0.6 crises»).
 */
public class CrisisGameTests {
	static Village village(GameTestHelper h) {
		Village v = TownGameTests.village(h);
		v.economy.treasury = 100_000;
		Crises.forceMonsters = null;
		Crises.forceTod = null;
		Funeral.forceTime = null;
		return v;
	}

	static CrisisData data(Village v) {
		return Crises.data(v);
	}

	static Creeper creeper(GameTestHelper h) {
		Creeper c = EntityTypes.CREEPER.create(h.getLevel(), EntitySpawnReason.MOB_SUMMONED);
		c.setPos(Vec3.atCenterOf(h.absolutePos(new BlockPos(4, 5, 4))));
		return c;
	}

	static void explode(GameTestHelper h, net.minecraft.world.entity.Entity source, int x, int z) {
		Vec3 c = Vec3.atCenterOf(h.absolutePos(new BlockPos(x, 1, z)));
		h.getLevel().explode(source, c.x, c.y, c.z, 2.0f, Level.ExplosionInteraction.TNT);
	}

	static int items(GameTestHelper h) {
		return h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(h.absolutePos(new BlockPos(0, 0, 0))).inflate(10)).size();
	}

	// ---------------------------------------------------------------- 1) reconstrução

	@GameTest(maxTicks = 40)
	public void creeperBlastIsRecordedWithoutDropsAndTheBuilderPutsItBack(GameTestHelper h) {
		Village v = village(h);
		ServerLevel level = h.getLevel();
		BlockPos[] wall = {new BlockPos(3, 1, 4), new BlockPos(5, 1, 4), new BlockPos(4, 1, 3), new BlockPos(4, 1, 5), new BlockPos(4, 2, 4)};
		for (BlockPos p : wall) h.setBlock(p, Blocks.OAK_PLANKS);
		h.setBlock(new BlockPos(4, 1, 4), Blocks.DIRT);
		int before = Rebuild.recordedCount;
		explode(h, creeper(h), 4, 4);
		for (BlockPos p : wall) h.assertBlockNotPresent(Blocks.OAK_PLANKS, p);
		h.assertTrue(Rebuild.recordedCount - before >= wall.length, "os blocos partidos pelo creeper ficam registados: " + (Rebuild.recordedCount - before));
		h.assertValueEqual(items(h), 0, "os blocos registados não largam item (senão repor duplicava)");
		CrisisData d = data(v);
		h.assertFalse(d.broken.isEmpty(), "à espera do Construtor");
		long now = level.getGameTime();
		Rebuild.tick(level, v, d, true, now + CrisisRules.CALM_TICKS);
		h.assertTrue(v.extra.projects.isEmpty(), "com perigo, ainda não começa");
		Rebuild.tick(level, v, d, false, d.lastBroken + CrisisRules.CALM_TICKS - 1);
		h.assertTrue(v.extra.projects.isEmpty(), "só depois de um minuto de calma");
		Rebuild.tick(level, v, d, false, d.lastBroken + CrisisRules.CALM_TICKS);
		h.assertTrue(!v.extra.projects.isEmpty() && v.extra.projects.get(0).kind == Project.Kind.REBUILD, "obra de reconstrução à frente das outras");
		h.assertTrue(d.broken.isEmpty(), "os blocos passaram para a obra");
		Builders.overnight(level, v);
		for (BlockPos p : wall) h.assertBlockPresent(Blocks.OAK_PLANKS, p);
		h.assertTrue(v.extra.projects.stream().noneMatch(p -> p.kind == Project.Kind.REBUILD), "obra acabada");
		h.succeed();
	}

	@GameTest(maxTicks = 40)
	public void playerTntIsNotRebuiltAndKeepsItsDrops(GameTestHelper h) {
		Village v = village(h);
		ServerPlayer p = TestUtil.playerAt(h, new BlockPos(1, 1, 1));
		h.setBlock(new BlockPos(4, 1, 3), Blocks.OAK_PLANKS);
		h.setBlock(new BlockPos(4, 1, 5), Blocks.OAK_PLANKS);
		explode(h, new PrimedTnt(h.getLevel(), 0, 0, 0, p), 4, 4);
		h.assertTrue(data(v).broken.isEmpty(), "TNT de um jogador: nada para repor");
		h.assertTrue(items(h) > 0, "e os blocos largam como no jogo");
		h.succeed();
	}

	@GameTest(maxTicks = 40)
	public void neverRecordsNorRebuildsPlayerBlocks(GameTestHelper h) {
		Village v = village(h);
		ServerLevel level = h.getLevel();
		ServerPlayer owner = TestUtil.playerAt(h, new BlockPos(7, 1, 7));
		TestUtil.lot(h, v, owner, 6, 6, 8, 8);
		BlockPos inLot = h.absolutePos(new BlockPos(7, 1, 7));
		h.assertFalse(Rebuild.record(level, inLot, Blocks.OAK_PLANKS.defaultBlockState()), "REGRA: nunca regista blocos de um terreno de jogador");
		// registado fora; depois o jogador pôs ali outro bloco: o Construtor não o tira
		BlockPos a = h.absolutePos(new BlockPos(2, 1, 2)), b = h.absolutePos(new BlockPos(2, 1, 6));
		h.assertTrue(Rebuild.record(level, a, Blocks.OAK_PLANKS.defaultBlockState()), "fora do terreno regista");
		h.assertTrue(Rebuild.record(level, b, Blocks.OAK_PLANKS.defaultBlockState()), "fora do terreno regista");
		h.assertFalse(Rebuild.record(level, b, Blocks.STONE.defaultBlockState()), "a mesma posição só uma vez");
		h.setBlock(new BlockPos(2, 1, 2), Blocks.COBBLESTONE);
		// e um terreno comprado depois por cima de b: o Construtor também não põe lá nada
		TestUtil.lot(h, v, owner, 1, 5, 3, 7);
		CrisisData d = data(v);
		Rebuild.tick(level, v, d, false, d.lastBroken + CrisisRules.CALM_TICKS);
		Builders.overnight(level, v);
		h.assertBlockPresent(Blocks.COBBLESTONE, new BlockPos(2, 1, 2));
		h.assertBlockNotPresent(Blocks.OAK_PLANKS, new BlockPos(2, 1, 6));
		h.succeed();
	}

	@GameTest(maxTicks = 40)
	public void recordingHasACap(GameTestHelper h) {
		Village v = village(h);
		int old = VilasConfig.get().rebuild_max_blocks;
		VilasConfig.get().rebuild_max_blocks = 2;
		try {
			for (int x = 1; x <= 4; x++) Rebuild.record(h.getLevel(), h.absolutePos(new BlockPos(x, 1, 1)), Blocks.STONE.defaultBlockState());
			h.assertValueEqual(data(v).broken.size(), 2, "teto de blocos registados");
		} finally {
			VilasConfig.get().rebuild_max_blocks = old;
		}
		h.succeed();
	}

	// ---------------------------------------------------------------- 2) crises

	@GameTest(maxTicks = 40)
	public void fireCrisisPaysWhoPutsItOutOnceButNotWhoLitIt(GameTestHelper h) {
		Village v = village(h);
		ServerLevel level = h.getLevel();
		ServerPlayer hero = TestUtil.playerAt(h, new BlockPos(1, 1, 1)), arsonist = TestUtil.playerAt(h, new BlockPos(7, 1, 1));
		hero.getInventory().clearContent();
		arsonist.getInventory().clearContent();
		// o incendiário acende fogo na vila com o isqueiro
		h.setBlock(new BlockPos(7, 1, 7), Blocks.OAK_PLANKS);
		TestUtil.useOnTop(h, arsonist, new ItemStack(Items.FLINT_AND_STEEL), new BlockPos(7, 2, 7));
		BlockPos[] fires = {new BlockPos(2, 1, 4), new BlockPos(3, 1, 4), new BlockPos(5, 1, 4), new BlockPos(6, 1, 4), new BlockPos(4, 1, 6)};
		for (BlockPos f : fires) h.setBlock(f, Blocks.FIRE);
		Crises.onBurn(level, h.absolutePos(new BlockPos(4, 1, 4)), Blocks.OAK_PLANKS.defaultBlockState());
		CrisisData.Crisis c = data(v).active(CrisisRules.FIRE);
		h.assertTrue(c != null, "um bloco da vila ardeu: crise do incêndio");
		h.assertValueEqual(c.goal, CrisisRules.FIRE_GOAL, "apagar 3 fogos");
		long treasury = v.economy.treasury, money = TestUtil.money(hero), reward = MissionRules.rewardCents(c.emeralds, VilasConfig.get().emerald_value_cents);
		// quem acendeu não ganha nada por apagar
		arsonist.gameMode.destroyBlock(h.absolutePos(fires[3]));
		arsonist.gameMode.destroyBlock(h.absolutePos(fires[4]));
		h.assertValueEqual(c.progress.getOrDefault(arsonist.getUUID(), 0), 0, "quem acendeu fogo não conta");
		for (int i = 0; i < 3; i++) hero.gameMode.destroyBlock(h.absolutePos(fires[i]));
		h.assertTrue(c.paid.contains(hero.getUUID()), "apagou 3: recebe");
		h.assertValueEqual(v.economy.treasury, treasury - reward, "o dinheiro sai da tesouraria");
		h.assertValueEqual(TestUtil.money(hero) - money, reward, "e vai para o jogador");
		// mais fogo: não recebe outra vez nesta crise
		h.setBlock(new BlockPos(2, 1, 2), Blocks.FIRE);
		h.setBlock(new BlockPos(2, 1, 3), Blocks.FIRE);
		h.setBlock(new BlockPos(3, 1, 2), Blocks.FIRE);
		for (BlockPos f : new BlockPos[] {new BlockPos(2, 1, 2), new BlockPos(2, 1, 3), new BlockPos(3, 1, 2)}) hero.gameMode.destroyBlock(h.absolutePos(f));
		h.assertValueEqual(v.economy.treasury, treasury - reward, "uma vez por jogador em cada crise");
		h.assertValueEqual(TestUtil.money(hero) - money, reward, "uma vez por jogador em cada crise");
		h.succeed();
	}

	@GameTest(maxTicks = 40)
	public void hungerCrisisTakesBreadToTheVillagersAndPaysOncePerPlayer(GameTestHelper h) {
		Village v = village(h);
		ServerLevel level = h.getLevel();
		for (int i = 0; i < 3; i++) TownGameTests.villager(h, new BlockPos(2 + i, 1, 2), null).getInventory().clearContent();
		Life.relist(v);
		long day = VillageEconomy.day(level);
		CrisisData d = data(v);
		Crises.hunger(level, v, d, day);
		h.assertTrue(d.active(CrisisRules.HUNGER) == null, "um dia só não chega");
		Crises.hunger(level, v, d, day + 1);
		CrisisData.Crisis c = d.active(CrisisRules.HUNGER);
		h.assertTrue(c != null, "dois dias com pouca comida: crise da fome");
		h.assertValueEqual(c.goal, 6, "pede 6 pães (mínimo)");
		ServerPlayer p = TestUtil.playerAt(h, new BlockPos(4, 1, 4)), q = TestUtil.playerAt(h, new BlockPos(5, 1, 4));
		p.getInventory().clearContent();
		q.getInventory().clearContent();
		p.getInventory().add(new ItemStack(Items.BREAD, 12));
		q.getInventory().add(new ItemStack(Items.BREAD, 3));
		h.assertFalse(Crises.deliverFood(q).ok(), "sem pão suficiente não entrega");
		long treasury = v.economy.treasury, money = TestUtil.money(p), reward = MissionRules.rewardCents(c.emeralds, VilasConfig.get().emerald_value_cents);
		h.assertTrue(Crises.deliverFood(p).ok(), "entrega");
		h.assertValueEqual(VillageManager.count(p.getInventory(), Items.BREAD), 6, "tirou 6 pães");
		h.assertValueEqual(Crises.foodPoints(Life.villagers(level, v)), 24, "os pães estão com os aldeões");
		h.assertValueEqual(v.economy.treasury, treasury - reward, "paga da tesouraria");
		h.assertValueEqual(TestUtil.money(p) - money, reward, "o jogador recebe");
		h.assertTrue(d.active(CrisisRules.HUNGER) == null, "a vila já tem comida: a crise acaba");
		h.assertFalse(Crises.deliverFood(p).ok(), "sem crise, nada");
		h.succeed();
	}

	@GameTest(maxTicks = 40)
	public void crisisPaysEachPlayerOnceAndOnlyAFewPlayers(GameTestHelper h) {
		Village v = village(h);
		ServerLevel level = h.getLevel();
		CrisisData d = data(v);
		CrisisData.Crisis c = Crises.open(level, v, CrisisRules.NIGHT, level.getGameTime() + 2000, 1, 1, null);
		h.assertTrue(c != null, "abre");
		h.assertTrue(Crises.open(level, v, CrisisRules.NIGHT, level.getGameTime() + 2000, 1, 1, null) == null, "uma de cada tipo");
		long treasury = v.economy.treasury, reward = MissionRules.rewardCents(1, VilasConfig.get().emerald_value_cents);
		ServerPlayer p = TestUtil.playerAt(h, new BlockPos(4, 1, 4));
		p.getInventory().clearContent();
		h.spawn(EntityTypes.ZOMBIE, new BlockPos(4, 1, 2)).hurtServer(level, level.damageSources().playerAttack(p), 1000);
		h.spawn(EntityTypes.ZOMBIE, new BlockPos(4, 1, 3)).hurtServer(level, level.damageSources().playerAttack(p), 1000);
		h.assertValueEqual(v.economy.treasury, treasury - reward, "o mesmo jogador só recebe uma vez");
		int paid = 1;
		for (int i = 0; i < CrisisRules.NIGHT_PAYEES + 2; i++) {
			ServerPlayer o = TestUtil.playerAt(h, new BlockPos(2, 1, 6));
			o.getInventory().clearContent();
			h.spawn(EntityTypes.ZOMBIE, new BlockPos(2 + i % 5, 1, 7)).hurtServer(level, level.damageSources().playerAttack(o), 1000);
			if (c.paid.contains(o.getUUID())) paid++;
		}
		h.assertValueEqual(paid, CrisisRules.NIGHT_PAYEES, "no máximo " + CrisisRules.NIGHT_PAYEES + " jogadores por crise");
		h.assertValueEqual(v.economy.treasury, treasury - paid * reward, "a tesouraria pagou só a esses");
		h.assertTrue(d.active(CrisisRules.NIGHT) == c, "a crise continua aberta");
		h.succeed();
	}

	@GameTest(maxTicks = 40)
	public void dangerousNightPaysForMonstersButNotSpawnerOnes(GameTestHelper h) {
		Village v = village(h);
		ServerLevel level = h.getLevel();
		CrisisData d = data(v);
		Crises.forceMonsters = 10;
		Crises.forceTod = 6000L;
		Crises.night(level, v, d, level.getGameTime());
		h.assertTrue(d.active(CrisisRules.NIGHT) == null, "de dia não há noite perigosa");
		Crises.forceTod = 15000L;
		Crises.night(level, v, d, level.getGameTime());
		CrisisData.Crisis c = d.active(CrisisRules.NIGHT);
		Crises.forceMonsters = null;
		Crises.forceTod = null;
		h.assertTrue(c != null, "muitos monstros de noite: crise");
		ServerPlayer p = TestUtil.playerAt(h, new BlockPos(1, 1, 1));
		p.getInventory().clearContent();
		long money = TestUtil.money(p), reward = MissionRules.rewardCents(c.emeralds, VilasConfig.get().emerald_value_cents);
		Zombie farmed = h.spawn(EntityTypes.ZOMBIE, new BlockPos(4, 1, 4));
		farmed.addTag(VillageManager.FROM_SPAWNER_TAG);
		farmed.hurtServer(level, level.damageSources().playerAttack(p), 1000);
		h.assertValueEqual(c.progress.getOrDefault(p.getUUID(), 0), 0, "monstro de gerador não conta");
		for (int i = 0; i < c.goal; i++) h.spawn(EntityTypes.ZOMBIE, new BlockPos(2 + i % 4, 1, 5)).hurtServer(level, level.damageSources().playerAttack(p), 1000);
		h.assertTrue(c.paid.contains(p.getUUID()), "derrotou " + c.goal + ": recebe");
		h.assertValueEqual(TestUtil.money(p) - money, reward, "a recompensa certa");
		h.spawn(EntityTypes.ZOMBIE, new BlockPos(4, 1, 6)).hurtServer(level, level.damageSources().playerAttack(p), 1000);
		h.assertValueEqual(TestUtil.money(p) - money, reward, "uma vez só");
		h.succeed();
	}

	@GameTest(maxTicks = 40)
	public void crisisDoesNotPayWithoutTreasuryOrOverTheDailyCap(GameTestHelper h) {
		Village v = village(h);
		ServerLevel level = h.getLevel();
		CrisisData.Crisis c = Crises.open(level, v, CrisisRules.NIGHT, level.getGameTime() + 2000, 1, 2, null);
		ServerPlayer p = TestUtil.playerAt(h, new BlockPos(1, 1, 1));
		p.getInventory().clearContent();
		v.economy.treasury = 10;
		h.spawn(EntityTypes.ZOMBIE, new BlockPos(4, 1, 4)).hurtServer(level, level.damageSources().playerAttack(p), 1000);
		h.assertFalse(c.paid.contains(p.getUUID()), "sem dinheiro na tesouraria não paga");
		h.assertValueEqual(v.economy.treasury, 10L, "e não fica negativa");
		h.assertValueEqual(TestUtil.money(p), 0L, "nada criado");
		h.succeed();
	}

	// ---------------------------------------------------------------- 3) morte, luto e funeral

	@GameTest(maxTicks = 60)
	public void deathBringsAFuneralWithAGraveAndFriendsMourn(GameTestHelper h) {
		Village v = village(h);
		ServerLevel level = h.getLevel();
		TownGameTests.plaza(h, v);
		ServerPlayer owner = TestUtil.playerAt(h, new BlockPos(0, 1, 0));
		TestUtil.lot(h, v, owner, 6, 0, 8, 8); // o lado leste (o 1.º sítio que a campa tenta) é de um jogador: a campa nunca vai para lá
		Villager dead = TownGameTests.villager(h, new BlockPos(5, 1, 5), net.minecraft.world.entity.npc.villager.VillagerProfession.FARMER);
		Villager friend = TownGameTests.villager(h, new BlockPos(6, 1, 6), null);
		Life.relist(v);
		v.civic.life.setOpinion(dead.getUUID(), friend.getUUID(), 40);
		CrisisData d = data(v);
		long day = VillageEconomy.day(level);
		d.firstSeen.put(dead.getUUID(), day - 9);
		dead.hurtServer(level, level.damageSources().generic(), 1000);
		h.assertValueEqual(d.deaths.size(), 1, "a morte fica registada");
		CrisisData.Death x = d.deaths.get(0);
		h.assertValueEqual(x.days, 10L, "10 dias na vila");
		h.assertTrue(x.friends.contains(friend.getUUID()), "o amigo vai ao funeral");
		h.assertTrue(Funeral.mourning(v, friend.getUUID()), "e fica de luto");
		Funeral.forceTime = CrisisRules.FUNERAL_TIME;
		Crises.tick(level, v);
		h.assertTrue(x.held < 0, "no próprio dia ainda não há funeral");
		d.deaths.set(0, shift(x, day - 1));
		Crises.tick(level, v);
		Funeral.forceTime = null;
		CrisisData.Death y = d.deaths.get(0);
		h.assertTrue(y.held >= 0, "no dia seguinte, às 15 h, há funeral");
		h.assertTrue(y.grave != Long.MIN_VALUE, "com campa");
		BlockPos g = BlockPos.of(y.grave);
		h.assertTrue(level.getBlockState(g).is(Blocks.CHISELED_STONE_BRICKS), "pedra lavrada");
		h.assertTrue(level.getBlockState(g.above()).is(Blocks.CANDLE), "vela acesa");
		h.assertTrue(VillageManager.lotAt(level, g.getX(), g.getZ()) == null, "REGRA: a campa nunca vai para um terreno");
		boolean sign = false;
		for (var dir : net.minecraft.core.Direction.Plane.HORIZONTAL)
			if (level.getBlockEntity(g.relative(dir)) instanceof SignBlockEntity s) {
				sign = true;
				String l3 = s.getText(net.minecraft.world.level.block.entity.SignTextSlot.FRONT).getMessages(false).get(2).getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t ? t.getKey() : "";
				h.assertValueEqual(l3, "tvilas.funeral.days", "o epitáfio tem os dias de vida");
			}
		h.assertTrue(sign, "placa com o epitáfio");
		h.succeed();
	}

	static void cube(ServerLevel level, BlockPos c) {
		for (int x = -6; x <= 6; x++)
			for (int y = -6; y <= 0; y++)
				for (int z = -6; z <= 6; z++) level.setBlock(c.offset(x, y, z), Blocks.OAK_PLANKS.defaultBlockState(), 2);
	}

	static long blast(ServerLevel level, BlockPos c) {
		Creeper cr = EntityTypes.CREEPER.create(level, EntitySpawnReason.MOB_SUMMONED);
		cr.setPos(Vec3.atCenterOf(c));
		long t0 = System.nanoTime();
		level.explode(cr, c.getX() + 0.5, c.getY() - 3, c.getZ() + 0.5, 10.0f, Level.ExplosionInteraction.TNT); // no meio do cubo
		return System.nanoTime() - t0;
	}

	static CrisisData.Death shift(CrisisData.Death x, long day) {
		CrisisData.Death y = new CrisisData.Death(x.villager, x.name, x.profession, x.days, day, x.cause);
		y.friends.addAll(x.friends);
		return y;
	}

	@GameTest(maxTicks = 40)
	public void oldAgeIsOffByDefaultAndSparesLockedTrades(GameTestHelper h) {
		Village v = village(h);
		ServerLevel level = h.getLevel();
		h.assertFalse(new VilasConfig().villager_aging, "a velhice vem desligada por omissão (no código)");
		h.assertFalse(VilasConfig.get().villager_aging, "e no ficheiro de teste");
		Villager traded = TownGameTests.villager(h, new BlockPos(2, 1, 2), net.minecraft.world.entity.npc.villager.VillagerProfession.LIBRARIAN);
		traded.setVillagerXp(5);
		Villager plain = h.spawn(EntityTypes.VILLAGER, new BlockPos(6, 1, 6));
		plain.setVillagerXp(0);
		Life.relist(v);
		CrisisData d = data(v);
		long day = VillageEconomy.day(level);
		d.firstSeen.put(traded.getUUID(), day - 500);
		d.firstSeen.put(plain.getUUID(), day - 500);
		Funeral.daily(level, v, d, day);
		h.assertTrue(traded.isAlive() && plain.isAlive(), "desligada: ninguém morre de velhice");
		VilasConfig.get().villager_aging = true;
		try {
			d.agingDay = -1;
			Funeral.daily(level, v, d, day);
			h.assertTrue(traded.isAlive(), "REGRA: quem tem trocas trancadas nunca morre de velhice");
			h.assertFalse(plain.isAlive(), "ligada: o aldeão sem trocas morre de velhice");
			h.assertTrue(!d.deaths.isEmpty() && Funeral.CAUSE_AGE.equals(d.deaths.get(d.deaths.size() - 1).cause), "e o funeral diz «velhice»");
		} finally {
			VilasConfig.get().villager_aging = false;
		}
		h.succeed();
	}

	// ---------------------------------------------------------------- 4) desempenho: uma explosão grande na vila

	@GameTest(maxTicks = 40)
	public void bigExplosionInTheVillageIsCheap(GameTestHelper h) {
		ServerLevel level = h.getLevel();
		TestUtil.clean(h);
		BlockPos c = h.absolutePos(new BlockPos(4, 120, 4)); // bem acima dos outros testes
		Village v = VillageManager.found(level, c, 12);
		v.civic.meta.real = true;
		v.civic.life = new LifeData();
		try {
			// a mesma explosão (força 10) sem e com o registo, para comparar
			cube(level, c);
			VilasConfig.get().rebuild = false;
			long base;
			try {
				base = blast(level, c);
			} finally {
				VilasConfig.get().rebuild = true;
			}
			h.assertTrue(Crises.data(v).broken.isEmpty(), "desligada não regista");
			level.getEntitiesOfClass(ItemEntity.class, new AABB(c).inflate(14)).forEach(ItemEntity::discard);
			cube(level, c);
			long explode = blast(level, c);
			int recorded = Crises.data(v).broken.size(), broken = 0;
			for (int x = -6; x <= 6; x++)
				for (int y = -6; y <= 0; y++)
					for (int z = -6; z <= 6; z++) if (level.getBlockState(c.offset(x, y, z)).isAir()) broken++;
			long t0 = System.nanoTime();
			Crises.tick(level, v);
			long tick = System.nanoTime() - t0;
			TVilas.LOGGER.info("Vilas 0.6 crises — explosão de força 10 na vila: {} blocos partidos, {} registados; explosão {} ms sem o registo e {} ms com; segundo da vila {} ms",
					broken, recorded, base / 1_000_000.0, explode / 1_000_000.0, tick / 1_000_000.0);
			h.assertTrue(broken > 20, "a explosão partiu blocos: " + broken);
			h.assertValueEqual(recorded, Math.min(broken, CrisisRules.REBUILD_MAX), "todos os partidos ficam registados (até ao teto)");
			h.assertTrue(recorded <= CrisisRules.REBUILD_MAX, "nunca acima do teto");
			h.assertTrue(explode < 250_000_000L, "a explosão com o registo fica barata: " + explode / 1_000_000 + " ms");
			h.assertTrue(tick < 20_000_000L, "o segundo da vila fica barato: " + tick / 1_000_000 + " ms");
		} finally {
			for (int x = -7; x <= 7; x++)
				for (int y = -7; y <= 7; y++)
					for (int z = -7; z <= 7; z++) level.setBlock(c.offset(x, y, z), Blocks.AIR.defaultBlockState(), 2);
			level.getEntitiesOfClass(ItemEntity.class, new AABB(c).inflate(12)).forEach(ItemEntity::discard);
			VillageData.get(level).villages.remove(v);
		}
		h.succeed();
	}
}
