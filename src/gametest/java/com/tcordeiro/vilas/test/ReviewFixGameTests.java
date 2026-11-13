package com.tcordeiro.vilas.test;

import com.tcordeiro.vilas.TVilas;
import com.tcordeiro.vilas.build.CivicWork;
import com.tcordeiro.vilas.logic.MissionRules;
import com.tcordeiro.vilas.registry.VilasBlocks;
import com.tcordeiro.vilas.village.Lot;
import com.tcordeiro.vilas.village.Village;
import com.tcordeiro.vilas.village.VillageData;
import com.tcordeiro.vilas.village.VillageEconomy;
import com.tcordeiro.vilas.village.VillageManager;

import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/** 0.3.0 — as correções da revisão: C6, A1, M3, M14, M15, M18 e o Prefeito que perdia a profissão. */
public class ReviewFixGameTests {
	// ------------------------------------------------------------------ C6: entidades nos terrenos

	@GameTest
	public void entitiesInLotsAreProtected(GameTestHelper h) {
		Village v = TestUtil.village(h);
		ServerPlayer owner = TestUtil.playerAt(h, new BlockPos(2, 1, 2));
		ServerPlayer stranger = TestUtil.playerAt(h, new BlockPos(6, 1, 6));
		Lot lot = TestUtil.lotOverTest(h, v, owner);
		var cow = h.spawn(EntityTypes.COW, new BlockPos(4, 1, 4));
		cow.setNoAi(true);
		var husk = h.spawn(EntityTypes.HUSK, new BlockPos(3, 1, 5));
		husk.setNoAi(true);
		var level = h.getLevel();
		h.assertValueEqual(AttackEntityCallback.EVENT.invoker().interact(stranger, level, InteractionHand.MAIN_HAND, cow, null), InteractionResult.FAIL,
				"um estranho não bate no bicho do terreno");
		float hp = cow.getHealth();
		cow.hurtServer(level, level.damageSources().playerAttack(stranger), 4);
		h.assertValueEqual(cow.getHealth(), hp, "nem com dano indireto (flecha, poção) dele");
		stranger.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.LEAD));
		h.assertValueEqual(UseEntityCallback.EVENT.invoker().interact(stranger, level, InteractionHand.MAIN_HAND, cow, null), InteractionResult.FAIL,
				"não o prende com trela (captura)");
		stranger.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BUCKET));
		h.assertValueEqual(UseEntityCallback.EVENT.invoker().interact(stranger, level, InteractionHand.MAIN_HAND, cow, null), InteractionResult.FAIL,
				"nem com balde");
		h.assertValueEqual(AttackEntityCallback.EVENT.invoker().interact(stranger, level, InteractionHand.MAIN_HAND, husk, null), InteractionResult.PASS,
				"monstros não são protegidos");
		h.assertValueEqual(AttackEntityCallback.EVENT.invoker().interact(owner, level, InteractionHand.MAIN_HAND, cow, null), InteractionResult.PASS,
				"o dono pode");
		lot.friends.add(stranger.getUUID());
		h.assertValueEqual(AttackEntityCallback.EVENT.invoker().interact(stranger, level, InteractionHand.MAIN_HAND, cow, null), InteractionResult.PASS,
				"um amigo pode");
		VillageData.get(level).lots.remove(lot);
		h.assertValueEqual(AttackEntityCallback.EVENT.invoker().interact(owner, level, InteractionHand.MAIN_HAND, cow, null), InteractionResult.PASS,
				"fora de terrenos, livre");
		cow.discard();
		husk.discard();
		h.succeed();
	}

	/** C6: capturado o Prefeito (levado para longe, vivo), não chega outro por dia; volta pela Mesa; morto, chega outro. */
	@GameTest(maxTicks = 200)
	public void capturedMayorIsNotReplaced(GameTestHelper h) {
		TestUtil.clean(h);
		TestUtil.floor(h, 4);
		BlockPos desk = new BlockPos(4, 1, 4);
		h.setBlock(desk, VilasBlocks.MAYOR_DESK);
		var level = h.getLevel();
		Village v = VillageManager.found(level, h.absolutePos(desk), TestUtil.RADIUS);
		Villager mayor = VillageEconomy.ensureMayor(level, v, level.getGameTime());
		h.assertTrue(mayor != null, "chega um Prefeito");
		mayor.setNoAi(true);
		h.assertValueEqual(v.civic.mayor, mayor.getUUID(), "a vila sabe quem é o Prefeito");
		// levado para longe (fora da Prefeitura), vivo
		// bem por cima da Prefeitura (fora do raio dela, no mesmo chunk carregado e sem ir parar aos testes vizinhos)
		BlockPos far = h.absolutePos(new BlockPos(4, 1, 4)).above(30);
		mayor.setNoGravity(true);
		mayor.teleportTo(far.getX() + 0.5, far.getY(), far.getZ() + 0.5);
		for (int day = 1; day <= 3; day++) {
			v.economy.lastMayorCall = -1; // como se já tivesse passado um dia
			h.assertTrue(VillageEconomy.ensureMayor(level, v, level.getGameTime() + day * VillageEconomy.DAY) == null, "não chega outro no dia " + day);
		}
		h.assertTrue(v.civic.mayorTaken, "a vila sabe que ele foi levado");
		h.assertTrue(VillageEconomy.recallMayor(level, v) != null && !v.civic.mayorTaken, "a Mesa trá-lo de volta");
		h.assertTrue(mayor.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(h.absolutePos(desk))) < 16, "está outra vez na Prefeitura");
		mayor.setNoGravity(false);
		// morto, a vila pode receber outro
		mayor.hurtServer(level, level.damageSources().generic(), 1000);
		h.assertTrue(v.civic.mayor == null, "morto, a vila esquece-o");
		v.economy.lastMayorCall = -1;
		Villager next = VillageEconomy.ensureMayor(level, v, level.getGameTime());
		h.assertTrue(next != null, "chega um Prefeito novo");
		next.discard();
		h.setBlock(desk, Blocks.AIR);
		VillageData.get(level).villages.remove(v);
		h.succeed();
	}

	/** Bug do jogo (0.3.0): o Prefeito que chega com ensureMayor não pode ficar «minecraft:none» ao fim de uns segundos. */
	@GameTest(maxTicks = 600)
	public void arrivedMayorKeepsTheJob(GameTestHelper h) {
		TestUtil.clean(h);
		TestUtil.floor(h, 4);
		BlockPos desk = new BlockPos(4, 1, 4);
		h.setBlock(desk, VilasBlocks.MAYOR_DESK);
		var level = h.getLevel();
		Village v = VillageManager.found(level, h.absolutePos(desk), TestUtil.RADIUS);
		Villager mayor = VillageEconomy.ensureMayor(level, v, level.getGameTime());
		h.assertTrue(mayor != null && VillageManager.isMayor(mayor), "chega como Prefeito");
		h.assertTrue(mayor.getVillagerXp() > 0, "com experiência (o jogo não lhe tira a profissão)");
		h.runAfterDelay(400, () -> {
			h.assertTrue(mayor.isAlive() && VillageManager.isMayor(mayor), "400 ticks depois continua Prefeito (era minecraft:none)");
			mayor.discard();
			h.setBlock(desk, Blocks.AIR);
			VillageData.get(level).villages.remove(v);
			h.succeed();
		});
	}

	// ------------------------------------------------------------------ A1: missões

	@GameTest
	public void missionsArePaidByTheTreasuryWithLimits(GameTestHelper h) {
		Village v = TestUtil.village(h);
		ServerPlayer p = TestUtil.playerAt(h, new BlockPos(4, 1, 4));
		p.getInventory().clearContent();
		var wheat = new MissionRules.Template(MissionRules.Kind.DELIVER, "minecraft:wheat", 2, 1, 1, 1);
		long reward = MissionRules.rewardCents(1, com.tcordeiro.vilas.VilasConfig.get().emerald_value_cents);
		VillageManager.setMission(h.getLevel(), v, 0, wheat);
		p.getInventory().add(new ItemStack(Items.WHEAT, 64));
		v.civic.missionFund = 0; // aqui só a tesouraria (o fundo tem o seu teste)
		v.economy.treasury = reward - 1;
		h.assertTrue(!VillageManager.deliver(p, v, 0).ok(), "tesouraria sem dinheiro: não paga");
		h.assertValueEqual(VillageManager.count(p.getInventory(), Items.WHEAT), 64, "e não tira os itens");
		h.assertValueEqual(TestUtil.money(p), 0L, "nem cria dinheiro");
		v.economy.treasury = 100 * reward;
		long mid = v.missions.get(0).id();
		h.assertTrue(VillageManager.deliver(p, v, 0).ok(), "com dinheiro, paga");
		h.assertValueEqual(v.economy.treasury, 99 * reward, "o dinheiro saiu da tesouraria");
		h.assertValueEqual(TestUtil.money(p), reward, "o jogador recebeu o mesmo (nada criado)");
		h.assertValueEqual(v.missions.get(0).id(), mid, "a missão fica no quadro até ao lote seguinte");
		h.assertTrue(!VillageManager.deliver(p, v, 0).ok(), "a mesma missão, o mesmo jogador, o mesmo lote: não");
		h.assertValueEqual(v.economy.treasury, 99 * reward, "e não paga outra vez");
		// outro jogador, mas a vila já passou o teto do dia
		ServerPlayer other = TestUtil.playerAt(h, new BlockPos(5, 1, 4));
		other.getInventory().clearContent();
		other.getInventory().add(new ItemStack(Items.WHEAT, 64));
		v.civic.paidToday = com.tcordeiro.vilas.VilasConfig.get().mission_daily_cap_cents;
		h.assertTrue(!VillageManager.deliver(other, v, 0).ok(), "passa o teto diário da tesouraria");
		v.civic.missionDay = -1; // novo dia: recomeça
		h.assertTrue(VillageManager.deliver(other, v, 0).ok(), "no dia seguinte paga (outro jogador pode fazer a mesma missão)");
		h.succeed();
	}

	/** O lote: a cada 4 dias o quadro troca TODAS as missões e quem já fez pode fazer as novas. */
	@GameTest
	public void boardRenewsEveryBatch(GameTestHelper h) {
		Village v = TestUtil.richVillage(h);
		var level = h.getLevel();
		com.tcordeiro.vilas.village.VillagerRequests.tick(level, v); // 1.º lote
		ServerPlayer p = TestUtil.playerAt(h, new BlockPos(4, 1, 4));
		p.getInventory().clearContent();
		p.getInventory().add(new ItemStack(Items.WHEAT, 64));
		VillageManager.setMission(level, v, 0, new MissionRules.Template(MissionRules.Kind.DELIVER, "minecraft:wheat", 2, 1, 1, 1));
		h.assertTrue(VillageManager.deliver(p, v, 0).ok(), "faz a missão");
		h.assertTrue(!VillageManager.deliver(p, v, 0).ok(), "uma vez por lote");
		java.util.List<Long> before = v.missions.stream().map(com.tcordeiro.vilas.village.Mission::id).toList();
		com.tcordeiro.vilas.village.VillagerRequests.tick(level, v);
		h.assertValueEqual(v.missions.stream().map(com.tcordeiro.vilas.village.Mission::id).toList(), before, "no mesmo lote nada muda");
		v.civic.batch.id--; // passaram 4 dias
		com.tcordeiro.vilas.village.VillagerRequests.tick(level, v);
		for (int i = 0; i < 3; i++) h.assertTrue(!before.contains(v.missions.get(i).id()), "lote novo: a missão " + (i + 1) + " foi trocada");
		h.assertTrue(v.civic.batch.done.isEmpty(), "e o que cada um fez recomeça");
		h.assertValueEqual(v.missions.size(), 3, "continuam 3");
		h.succeed();
	}

	/** Uma vila nova tem 200 CB no fundo das missões; +50 CB por dia até 400 CB; paga primeiro do fundo. */
	@GameTest
	public void newVillageHasAMissionFund(GameTestHelper h) {
		Village v = TestUtil.village(h);
		h.assertValueEqual(v.civic.missionFund, com.tcordeiro.vilas.logic.FundRules.START_CENTS, "200 CB no fundo ao nascer");
		h.assertValueEqual(v.economy.treasury, 0L, "a tesouraria começa vazia");
		ServerPlayer p = TestUtil.playerAt(h, new BlockPos(4, 1, 4));
		p.getInventory().clearContent();
		p.getInventory().add(new ItemStack(Items.WHEAT, 64));
		VillageManager.setMission(h.getLevel(), v, 0, new MissionRules.Template(MissionRules.Kind.DELIVER, "minecraft:wheat", 2, 1, 1, 1));
		h.assertTrue(VillageManager.deliver(p, v, 0).ok(), "uma vila nova já paga missões");
		long reward = MissionRules.rewardCents(1, com.tcordeiro.vilas.VilasConfig.get().emerald_value_cents);
		h.assertValueEqual(v.civic.missionFund, com.tcordeiro.vilas.logic.FundRules.START_CENTS - reward, "saiu do fundo");
		h.assertValueEqual(TestUtil.money(p), reward, "o jogador recebeu o mesmo");
		// dias depois o fundo enche, mas nunca passa dos 400 CB
		long day = VillageEconomy.day(h.getLevel());
		v.economy.lastDay = day;
		VillageEconomy.daily(h.getLevel(), v, day + 100);
		h.assertValueEqual(v.civic.missionFund, com.tcordeiro.vilas.logic.FundRules.CAP_CENTS, "teto de 400 CB");
		v.civic.missionFund = 0;
		VillageEconomy.daily(h.getLevel(), v, day + 101);
		h.assertValueEqual(v.civic.missionFund, com.tcordeiro.vilas.logic.FundRules.DAILY_CENTS, "+50 CB num dia");
		h.succeed();
	}

	/** Prefeito sumido (chunk por carregar) há 3 dias: chega um novo; o antigo perde a profissão quando voltar a carregar. */
	@GameTest
	public void vanishedMayorIsReplacedAfterThreeDays(GameTestHelper h) {
		TestUtil.clean(h);
		TestUtil.floor(h, 4);
		BlockPos desk = new BlockPos(4, 1, 4);
		h.setBlock(desk, VilasBlocks.MAYOR_DESK);
		var level = h.getLevel();
		Village v = VillageManager.found(level, h.absolutePos(desk), TestUtil.RADIUS);
		long now = level.getGameTime() + 100 * VillageEconomy.DAY;
		java.util.UUID gone = java.util.UUID.randomUUID(); // um Prefeito que não está carregado em lado nenhum
		v.civic.mayor = gone;
		v.civic.mayorSeen = now - 2 * VillageEconomy.DAY;
		h.assertTrue(VillageEconomy.ensureMayor(level, v, now) == null, "2 dias sumido: ainda espera");
		h.assertTrue(v.civic.mayorTaken, "conta como levado");
		v.civic.mayorSeen = now - 3 * VillageEconomy.DAY;
		Villager fresh = VillageEconomy.ensureMayor(level, v, now);
		h.assertTrue(fresh != null && VillageManager.isMayor(fresh), "3 dias sumido: chega um novo (a vila nunca fica presa)");
		h.assertTrue(v.civic.retired.contains(gone), "o antigo fica marcado");
		fresh.discard();
		// o antigo volta a carregar: deixa de ser Prefeito
		Villager old = h.spawn(EntityTypes.VILLAGER, new BlockPos(2, 1, 6));
		old.setNoAi(true);
		old.setVillagerData(old.getVillagerData().withProfession(level.registryAccess(), com.tcordeiro.vilas.registry.VilasProfessions.MAYOR));
		v.civic.retired.add(old.getUUID());
		VillageEconomy.onLoaded(level, old);
		h.assertTrue(!VillageManager.isMayor(old), "o antigo Prefeito perde a profissão");
		h.assertTrue(!v.civic.retired.contains(old.getUUID()), "e sai da lista");
		old.discard();
		h.setBlock(desk, Blocks.AIR);
		VillageData.get(level).villages.remove(v);
		h.succeed();
	}

	@GameTest
	public void paidDecorationIsConsumedWhenBroken(GameTestHelper h) {
		Village v = TestUtil.richVillage(h);
		ServerPlayer p = TestUtil.playerAt(h, new BlockPos(4, 1, 4));
		p.getInventory().clearContent();
		VillageManager.setMission(h.getLevel(), v, 1, new MissionRules.Template(MissionRules.Kind.DECORATE, "minecraft:lantern", 1, 1, 1, 1));
		VillageManager.accept(p, v, 1);
		BlockPos a = new BlockPos(2, 1, 2);
		TestUtil.placeWithItem(h, p, new ItemStack(Items.LANTERN), a);
		h.assertTrue(VillageManager.deliver(p, v, 1).ok(), "entrega");
		p.getInventory().clearContent();
		h.killAllEntitiesOfClass(ItemEntity.class);
		p.gameMode.destroyBlock(h.absolutePos(a));
		h.assertBlockNotPresent(Blocks.LANTERN, a);
		h.assertTrue(h.getEntities(EntityTypes.ITEM).isEmpty(), "a lanterna paga não sai (é da vila)");
		h.assertValueEqual(VillageManager.count(p.getInventory(), Items.LANTERN), 0, "nem vai para o inventário");
		// uma lanterna que não foi paga parte-se normalmente
		BlockPos b = new BlockPos(6, 1, 2);
		h.setBlock(b, Blocks.LANTERN);
		p.gameMode.destroyBlock(h.absolutePos(b));
		h.assertBlockNotPresent(Blocks.LANTERN, b);
		h.succeed();
	}

	// ------------------------------------------------------------------ M3: dívida congela a automação

	@GameTest
	public void frozenLotStopsAutomation(GameTestHelper h) {
		Village v = TestUtil.village(h);
		ServerPlayer owner = TestUtil.playerAt(h, new BlockPos(2, 1, 2));
		TestUtil.lotOverTest(h, v, owner);
		BlockPos in1 = h.absolutePos(new BlockPos(3, 1, 3)), in2 = h.absolutePos(new BlockPos(4, 1, 3));
		h.assertTrue(VillageManager.mayCross(h.getLevel(), in1, in2), "dentro do próprio terreno a automação trabalha");
		v.extra.frozen.add(owner.getUUID());
		h.assertTrue(!VillageManager.mayCross(h.getLevel(), in1, in2), "congelado: pistões, ejetores e líquidos param lá dentro");
		h.assertTrue(!VillageManager.automationAllowed(h.getLevel(), in2), "automação congelada");
		// uma máquina do Industrial age com um jogador falso com o UUID do dono: também não
		var fake = net.fabricmc.fabric.api.entity.FakePlayer.get(h.getLevel(), new com.mojang.authlib.GameProfile(owner.getUUID(), "maquina"));
		h.assertTrue(!VillageManager.mayModify(h.getLevel(), in2, fake), "as máquinas do dono congelam também");
		// um pistão do dono, dentro do terreno, não empurra
		h.setBlock(new BlockPos(3, 1, 5), Blocks.PISTON.defaultBlockState().setValue(net.minecraft.world.level.block.piston.PistonBaseBlock.FACING,
				net.minecraft.core.Direction.EAST));
		h.setBlock(new BlockPos(4, 1, 5), Blocks.COBBLESTONE);
		h.setBlock(new BlockPos(3, 2, 5), Blocks.REDSTONE_BLOCK);
		h.runAfterDelay(10, () -> {
			h.assertBlockPresent(Blocks.COBBLESTONE, new BlockPos(4, 1, 5));
			v.extra.frozen.remove(owner.getUUID());
			h.succeed();
		});
	}

	// ------------------------------------------------------------------ M15: área cívica

	@GameTest
	public void civicAreaIsProtected(GameTestHelper h) {
		TestUtil.clean(h);
		TestUtil.floor(h, 4);
		BlockPos desk = new BlockPos(4, 1, 4);
		h.setBlock(desk, VilasBlocks.MAYOR_DESK);
		var level = h.getLevel();
		Village v = VillageManager.found(level, h.absolutePos(desk), TestUtil.RADIUS);
		CivicWork.refresh(level, v);
		h.assertTrue(!v.civic.boxes.isEmpty(), "a praça da Mesa é área cívica");
		ServerPlayer p = TestUtil.playerAt(h, new BlockPos(2, 1, 2));
		BlockPos stone = new BlockPos(6, 1, 6);
		h.setBlock(stone, Blocks.STONE_BRICKS);
		h.assertTrue(!VillageManager.mayModify(level, h.absolutePos(stone), p), "ninguém mexe na área cívica");
		p.gameMode.destroyBlock(h.absolutePos(stone));
		h.assertBlockPresent(Blocks.STONE_BRICKS, stone);
		h.assertValueEqual(TestUtil.useOnTop(h, p, new ItemStack(Items.DIRT), new BlockPos(2, 1, 6)), InteractionResult.FAIL, "nem põe blocos");
		BlockPos outside = h.absolutePos(desk).offset(30, 0, 0);
		h.assertTrue(!VillageManager.explosionMayBreak(level, null, outside, h.absolutePos(stone)), "explosão de fora não parte");
		h.assertTrue(!VillageManager.mayCross(level, outside, h.absolutePos(stone)), "líquidos/pistões de fora não entram");
		h.assertTrue(VillageManager.mayCross(level, h.absolutePos(stone), h.absolutePos(stone).east()), "lá dentro tudo trabalha");
		// o dono da Mesa e os amigos dele podem; os outros não
		ServerPlayer owner = TestUtil.playerAt(h, new BlockPos(3, 1, 3));
		v.civic.owner = owner.getUUID();
		h.assertTrue(VillageManager.mayModify(level, h.absolutePos(stone), owner), "o dono da Mesa mexe");
		h.assertTrue(!VillageManager.mayModify(level, h.absolutePos(stone), p), "um estranho continua sem mexer");
		h.assertTrue(!VillageManager.addCivicFriend(p, v, owner).ok(), "só o dono junta amigos");
		h.assertTrue(VillageManager.addCivicFriend(owner, v, p).ok(), "o dono junta um amigo");
		h.assertTrue(VillageManager.mayModify(level, h.absolutePos(stone), p), "o amigo mexe");
		h.assertTrue(VillageManager.explosionMayBreak(level, p, h.absolutePos(stone), h.absolutePos(stone)), "a TNT do amigo também");
		h.assertTrue(VillageManager.removeCivicFriend(owner, v, p.getGameProfile().name()).ok(), "o dono tira-o pelo nome");
		h.assertTrue(!VillageManager.mayModify(level, h.absolutePos(stone), p), "tirado, já não mexe");
		var cat = h.spawn(EntityTypes.CAT, new BlockPos(5, 1, 3));
		h.assertValueEqual(AttackEntityCallback.EVENT.invoker().interact(p, level, InteractionHand.MAIN_HAND, cat, null), InteractionResult.FAIL,
				"os bichos da praça também");
		h.assertValueEqual(AttackEntityCallback.EVENT.invoker().interact(owner, level, InteractionHand.MAIN_HAND, cat, null), InteractionResult.PASS,
				"o dono da Mesa pode");
		cat.discard();
		h.setBlock(stone, Blocks.AIR);
		h.setBlock(desk, Blocks.AIR);
		VillageData.get(level).villages.remove(v);
		h.succeed();
	}

	/** Quem põe a Mesa fica dono da área cívica. */
	@GameTest
	public void whoPlacesTheDeskOwnsTheCivicArea(GameTestHelper h) {
		TestUtil.clean(h);
		TestUtil.floor(h, 4);
		ServerPlayer p = TestUtil.playerAt(h, new BlockPos(4, 1, 2));
		BlockPos desk = new BlockPos(4, 1, 4);
		TestUtil.placeWithItem(h, p, new ItemStack(VilasBlocks.MAYOR_DESK), desk);
		h.assertBlockPresent(VilasBlocks.MAYOR_DESK, desk);
		Village v = VillageManager.byDesk(h.getLevel(), h.absolutePos(desk));
		h.assertTrue(v != null && p.getUUID().equals(v.civic.owner), "o jogador que pôs a Mesa é o dono");
		h.setBlock(desk, Blocks.AIR);
		VillageData.get(h.getLevel()).villages.remove(v);
		h.succeed();
	}

	/** Revisão de segurança: Mesa num descampado não cria dinheiro (fundo 0, sem missões pagas, sem pedidos); os 200 CB só uma vez. */
	@GameTest
	public void missionFundOnlyForRealVillagesAndOnce(GameTestHelper h) {
		TestUtil.clean(h);
		TestUtil.floor(h, 4);
		var level = h.getLevel();
		BlockPos desk = new BlockPos(4, 1, 4);
		h.setBlock(desk, VilasBlocks.MAYOR_DESK);
		Village v = VillageManager.found(level, h.absolutePos(desk), TestUtil.RADIUS);
		CivicWork.refresh(level, v);
		h.assertTrue(!v.civic.meta.real, "uma Mesa no descampado não é uma vila de verdade");
		h.assertValueEqual(v.civic.missionFund, 0L, "fundo a 0");
		v.economy.treasury = 1_000_000; // nem com tesouraria: sem vila de verdade não há missões pagas
		ServerPlayer p = TestUtil.playerAt(h, new BlockPos(2, 1, 2));
		p.getInventory().clearContent();
		p.getInventory().add(new ItemStack(Items.WHEAT, 64));
		VillageManager.setMission(level, v, 0, new MissionRules.Template(MissionRules.Kind.DELIVER, "minecraft:wheat", 2, 1, 1, 1));
		h.assertTrue(!VillageManager.deliver(p, v, 0).ok(), "sem missões pagas");
		h.assertValueEqual(TestUtil.money(p), 0L, "nada pago");
		java.util.List<Villager> smiths = new java.util.ArrayList<>();
		for (int i = 0; i < 4; i++) { // 4 aldeões com profissão (ainda não chega para ser vila: são precisos 5)
			Villager e = h.spawn(EntityTypes.VILLAGER, new BlockPos(1 + 2 * i, 1, 1));
			e.setNoAi(true);
			e.setVillagerData(e.getVillagerData().withProfession(level.registryAccess(), net.minecraft.world.entity.npc.villager.VillagerProfession.ARMORER));
			smiths.add(e);
		}
		CivicWork.refresh(level, v);
		h.assertTrue(!v.civic.meta.real, "4 aldeões ainda não fazem uma vila");
		com.tcordeiro.vilas.village.VillagerRequests.tick(level, v);
		h.assertTrue(v.civic.batch.requests.isEmpty(), "sem pedidos «!»");
		smiths.forEach(Villager::discard);
		long day = VillageEconomy.day(level);
		v.economy.lastDay = day;
		VillageEconomy.daily(level, v, day + 3);
		h.assertValueEqual(v.civic.missionFund, 0L, "nem os 50 CB por dia");
		// chegam 5 moradores: agora é uma vila
		java.util.List<Villager> people = new java.util.ArrayList<>();
		for (int i = 0; i < 5; i++) {
			Villager e = h.spawn(EntityTypes.VILLAGER, new BlockPos(1 + i, 1, 7));
			e.setNoAi(true);
			people.add(e);
		}
		CivicWork.refresh(level, v);
		h.assertTrue(v.civic.meta.real, "com 5 aldeões é uma vila de verdade");
		h.assertValueEqual(v.civic.missionFund, com.tcordeiro.vilas.logic.FundRules.START_CENTS, "recebe os 200 CB");
		v.civic.missionFund = 0; // gastos
		// partir e repor a Mesa: os 200 não voltam
		h.setBlock(desk, Blocks.AIR);
		h.setBlock(desk, VilasBlocks.MAYOR_DESK);
		h.assertTrue(VillageManager.found(level, h.absolutePos(desk), TestUtil.RADIUS) == v, "a Mesa nova adota a mesma vila");
		CivicWork.refresh(level, v);
		h.assertValueEqual(v.civic.missionFund, 0L, "os 200 CB entram uma vez por vila");
		people.forEach(Villager::discard);
		h.setBlock(desk, Blocks.AIR);
		VillageData.get(level).villages.remove(v);
		h.succeed();
	}

	/** Revisão de segurança: partir e repor a Mesa (ou pô-la numa vila que já existia) não dá a posse a um estranho. */
	@GameTest
	public void strangerCannotTakeOverByReplacingTheDesk(GameTestHelper h) {
		TestUtil.clean(h);
		TestUtil.floor(h, 4);
		var level = h.getLevel();
		BlockPos desk = new BlockPos(4, 1, 4);
		ServerPlayer owner = TestUtil.playerAt(h, new BlockPos(4, 1, 2));
		TestUtil.placeWithItem(h, owner, new ItemStack(VilasBlocks.MAYOR_DESK), desk);
		Village v = VillageManager.byDesk(level, h.absolutePos(desk));
		h.assertTrue(v != null && owner.getUUID().equals(v.civic.owner), "quem pôs a primeira Mesa é o dono");
		h.setBlock(desk, Blocks.AIR); // partida (por quem for)
		ServerPlayer stranger = TestUtil.playerAt(h, new BlockPos(4, 1, 6));
		TestUtil.placeWithItem(h, stranger, new ItemStack(VilasBlocks.MAYOR_DESK), desk);
		h.assertBlockPresent(VilasBlocks.MAYOR_DESK, desk);
		h.assertTrue(VillageManager.byDesk(level, h.absolutePos(desk)) == v, "a Mesa reposta é a mesma vila");
		h.assertValueEqual(v.civic.owner, owner.getUUID(), "o estranho não fica dono; o dono antigo mantém-se");
		// uma vila que já existia sem dono (como a da Mesa gerada com a vila): repor a Mesa também não dá a posse
		h.setBlock(desk, Blocks.AIR);
		v.civic.owner = null;
		v.civic.meta.ownerHistory.clear();
		TestUtil.placeWithItem(h, stranger, new ItemStack(VilasBlocks.MAYOR_DESK), desk);
		h.assertTrue(v.civic.owner == null, "vila já existente sem dono: só os operadores");
		h.setBlock(desk, Blocks.AIR);
		VillageData.get(level).villages.remove(v);
		h.succeed();
	}

	// ------------------------------------------------------------------ M18: amigo desligado

	@GameTest
	public void offlineFriendCanBeRemoved(GameTestHelper h) {
		Village v = TestUtil.village(h);
		ServerPlayer owner = TestUtil.playerAt(h, new BlockPos(4, 1, 4));
		ServerPlayer friend = TestUtil.playerAt(h, new BlockPos(3, 1, 3));
		Lot lot = TestUtil.lotOverTest(h, v, owner);
		h.assertTrue(VillageManager.addFriend(owner, friend).ok(), "adiciona");
		String name = friend.getGameProfile().name();
		h.assertTrue(VillageManager.removeFriend(owner, name).ok(), "tira pelo nome");
		// um amigo que nunca mais entrou no servidor: só se conhece o nome guardado no terreno
		java.util.UUID gone = java.util.UUID.randomUUID();
		lot.friends.add(gone);
		lot.friendNames.put(gone, "Ausente");
		h.assertTrue(h.getLevel().getServer().getPlayerList().getPlayer(gone) == null, "não está ligado");
		h.assertTrue(VillageManager.removeFriend(owner, "ausente").ok(), "tira pelo nome, desligado (sem distinguir maiúsculas)");
		h.assertTrue(!lot.friends.contains(gone), "o desligado já não é amigo");
		h.assertTrue(!lot.friends.contains(friend.getUUID()), "já não é amigo");
		h.assertTrue(!VillageManager.removeFriend(owner, name).ok(), "não tira duas vezes");
		lot.friends.add(friend.getUUID());
		h.assertTrue(VillageManager.removeFriend(owner, friend.getUUID().toString()).ok(), "também pelo UUID");
		h.succeed();
	}

	// ------------------------------------------------------------------ M14: codecs estritos

	@GameTest
	public void strictCodecs(GameTestHelper h) {
		var ops = NbtOps.INSTANCE;
		CompoundTag bad = new CompoundTag();
		bad.putString("kind", "FOGUETE");
		bad.put("site", BlockPos.CODEC.encodeStart(ops, BlockPos.ZERO).getOrThrow());
		h.assertTrue(com.tcordeiro.vilas.village.Project.CODEC.parse(ops, bad).isError(), "obra de tipo desconhecido: erro (não rebenta)");
		var placement = new CompoundTag();
		placement.put("pos", BlockPos.CODEC.encodeStart(ops, BlockPos.ZERO).getOrThrow());
		placement.put("state", net.minecraft.nbt.NbtUtils.writeBlockState(Blocks.STONE.defaultBlockState()));
		placement.putInt("clear", 1_000_000);
		h.assertTrue(com.tcordeiro.vilas.village.Project.Placement.CODEC.parse(ops, placement).isError(), "«clear» enorme: erro");
		var mission = new CompoundTag();
		mission.putLong("id", 1);
		mission.putString("kind", "ROUBAR");
		mission.putString("target", "minecraft:wheat");
		mission.putInt("count", 1);
		mission.putInt("emeralds", 1);
		mission.putInt("reputation", 1);
		mission.putInt("tier", 1);
		h.assertTrue(com.tcordeiro.vilas.village.Mission.CODEC.parse(ops, mission).isError(), "missão de tipo desconhecido: erro");
		mission.putString("kind", "DELIVER");
		mission.putInt("emeralds", -5);
		h.assertTrue(com.tcordeiro.vilas.village.Mission.CODEC.parse(ops, mission).isError(), "recompensa negativa: erro");
		// a vila carrega na mesma, só sem o que estava estragado
		Village v = TestUtil.village(h);
		var good = com.tcordeiro.vilas.village.Village.CODEC.encodeStart(ops, v).getOrThrow();
		CompoundTag tag = (CompoundTag) good;
		var list = new net.minecraft.nbt.ListTag();
		list.add(mission.copy());
		mission.putInt("emeralds", 1);
		list.add(mission.copy());
		tag.put("missions", list);
		var back = com.tcordeiro.vilas.village.Village.CODEC.parse(ops, tag);
		h.assertTrue(back.isSuccess(), "a vila carrega");
		h.assertValueEqual(back.getOrThrow().missions.size(), 1, "só a missão estragada foi ignorada");
		h.assertTrue(TVilas.MOD_ID.equals("tvilas"), "ok");
		h.succeed();
	}

	/** Industrial 0.12.2+: os postes da iluminação têm 4 blocos (API de colunas, sem LED); tirar um poste não larga itens. */
	@GameTest
	public void streetPolesAreFourBlocksAndRemoveCleanly(GameTestHelper h) {
		var kit = com.tcordeiro.vilas.build.StreetLightKit.forBiome("plains");
		boolean api = kit.industrial() && com.tcordeiro.vilas.build.StreetLightKit.columnApi(kit.pole().get(0).getBlock()) != null;
		if (!api) {
			h.assertTrue(kit.height() <= 4, "sem a API: a altura de origem");
			h.succeed();
			return;
		}
		h.assertValueEqual(kit.height(), com.tcordeiro.vilas.build.StreetLightKit.POLE_HEIGHT, "com a API: 4 blocos");
		Village v = TestUtil.village(h);
		var level = h.getLevel();
		BlockPos base = new BlockPos(4, 1, 2);
		var p = new com.tcordeiro.vilas.village.Project(com.tcordeiro.vilas.village.Project.Kind.LIGHTING, h.absolutePos(base));
		p.pending.add(new com.tcordeiro.vilas.village.Project.Placement(h.absolutePos(base), kit.pole().get(0), kit.height()));
		p.total = 1;
		com.tcordeiro.vilas.build.Builders.start(level, v, p);
		com.tcordeiro.vilas.build.Builders.overnight(level, v);
		var b = kit.pole().get(0).getBlock();
		for (int i = 0; i < 4; i++) h.assertTrue(level.getBlockState(h.absolutePos(base.above(i))).is(b), "parte " + (i + 1) + " do poste");
		h.assertTrue(!level.getBlockState(h.absolutePos(base.above(4))).is(b), "e não mais de 4");
		h.killAllEntitiesOfClass(ItemEntity.class);
		com.tcordeiro.vilas.build.LightingWork.removePole(level, kit, h.absolutePos(base));
		for (int i = 0; i < 4; i++) h.assertTrue(level.getBlockState(h.absolutePos(base.above(i))).isAir(), "tirada a parte " + (i + 1));
		h.assertTrue(h.getEntities(EntityTypes.ITEM).isEmpty(), "sem largar itens");
		h.succeed();
	}
}
