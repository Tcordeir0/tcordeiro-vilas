package com.tcordeiro.vilas.test;

import java.util.List;

import com.tcordeiro.vilas.TVilas;
import com.tcordeiro.vilas.compat.Mods;
import com.tcordeiro.vilas.life.Balloons;
import com.tcordeiro.vilas.life.Care;
import com.tcordeiro.vilas.life.Climate;
import com.tcordeiro.vilas.life.Festa;
import com.tcordeiro.vilas.life.Life;
import com.tcordeiro.vilas.life.LifeData;
import com.tcordeiro.vilas.life.Props;
import com.tcordeiro.vilas.life.Resident;
import com.tcordeiro.vilas.life.Talk;
import com.tcordeiro.vilas.life.Task;
import com.tcordeiro.vilas.life.Tasks;
import com.tcordeiro.vilas.logic.LifeRules;
import com.tcordeiro.vilas.logic.LotRules;
import com.tcordeiro.vilas.village.Village;
import com.tcordeiro.vilas.village.VillageEconomy;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.gossip.GossipType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * 0.4.0 — A vida dos aldeões: um teste por mecânica, nos 3 modos (com os mods, sem o CubCoin, sem nenhum mod — aí as partes do
 * Móveis/Clima degradam: escadas em vez de cadeiras, sem cama de casal, frio pelo bioma). O relógio e o tempo do mundo NÃO são
 * mexidos (os testes correm lado a lado): as horas entram por {@link Life#decideAt} e a chuva/frio por {@link Climate}.
 */
public class LifeGameTests {
	static Village village(GameTestHelper h) {
		Village v = TestUtil.village(h);
		v.civic.life = new LifeData();
		Life.relist(v);
		return v;
	}

	/** Uma área cívica que cobre o teste todo (0..8). */
	static void civic(GameTestHelper h, Village v, String kind) {
		civic(h, v, kind, 0, 0, 8, 8);
	}

	static void civic(GameTestHelper h, Village v, String kind, int x0, int z0, int x1, int z1) {
		BlockPos a = h.absolutePos(new BlockPos(x0, 0, z0)), b = h.absolutePos(new BlockPos(x1, 0, z1));
		v.civic.put(kind, new LotRules.Area(Math.min(a.getX(), b.getX()), Math.min(a.getZ(), b.getZ()), Math.max(a.getX(), b.getX()), Math.max(a.getZ(), b.getZ())));
		Life.rescan(v);
		Life.relist(v);
	}

	static Villager villager(GameTestHelper h, BlockPos at, net.minecraft.resources.ResourceKey<VillagerProfession> prof) {
		Villager vi = h.spawn(EntityTypes.VILLAGER, at);
		if (prof != null) vi.setVillagerData(vi.getVillagerData().withProfession(h.getLevel().registryAccess(), prof).withLevel(2));
		vi.setVillagerXp(1);
		return vi;
	}

	static Block moveis(String path) {
		return BuiltInRegistries.BLOCK.getOptional(Identifier.fromNamespaceAndPath(Mods.MOVEIS, path)).orElse(null);
	}

	// ---------------------------------------------------------------- 2/3) convívio: sentar (cadeira do Móveis ou escada)

	@GameTest(maxTicks = 400)
	public void eveningSitsOnAChairOrStairs(GameTestHelper h) {
		Village v = village(h);
		civic(h, v, "plaza_0"); // como as vilas do jogo guardam as áreas («plaza_0», «tavern_built»…)
		Block chair = moveis("oak_chair");
		BlockState seat = chair != null ? chair.defaultBlockState() : Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.NORTH);
		h.setBlock(new BlockPos(6, 1, 6), seat);
		Life.rescan(v);
		Villager vi = villager(h, new BlockPos(2, 1, 2), VillagerProfession.FARMER);
		Life.relist(v);
		long tod = LifeRules.SOCIAL_START + 100;
		Life.decideAt(h.getLevel(), v, vi, tod, false);
		Task t = Tasks.get(vi);
		h.assertTrue(t != null && t.kind == Task.Kind.SOCIAL, "às 16 h vai para a Praça");
		h.assertTrue(t.seat != null && t.seat.equals(h.absolutePos(new BlockPos(6, 1, 6))), "reservou o assento: " + t.seat);
		h.succeedWhen(() -> {
			h.assertTrue(vi.isPassenger(), "sentou-se (" + (chair != null ? "cadeira do Móveis" : "escada") + ")");
			if (chair != null) h.assertTrue(BuiltInRegistries.ENTITY_TYPE.getKey(vi.getVehicle().getType()).getNamespace().equals(Mods.MOVEIS),
					"no assento do Móveis (o jogador vê o lugar ocupado)");
			else h.assertTrue(vi.getVehicle() instanceof com.tcordeiro.vilas.life.LifeSeat, "no assento invisível das escadas");
			// à hora de se deitar de cada um, levanta-se
			Life.checkAt(h.getLevel(), v, vi, LifeRules.bedtime(vi.getUUID().getLeastSignificantBits() ^ vi.getUUID().getMostSignificantBits()) + 1, false);
			h.assertTrue(Tasks.get(vi) == null && !vi.isPassenger(), "à sua hora levanta-se e vai deitar-se (o vanilla volta)");
		});
	}

	@GameTest(maxTicks = 60)
	public void bedtimesAreSpreadAndNoSocialAtNight(GameTestHelper h) {
		Village v = village(h);
		civic(h, v, "plaza");
		Villager vi = villager(h, new BlockPos(4, 1, 4), VillagerProfession.MASON);
		Life.relist(v);
		Life.decideAt(h.getLevel(), v, vi, 14000, false);
		h.assertTrue(Tasks.get(vi) == null || Tasks.get(vi).kind != Task.Kind.SOCIAL, "de noite não há convívio");
		Life.decideAt(h.getLevel(), v, vi, 3000, false);
		h.assertTrue(Tasks.get(vi) == null || Tasks.get(vi).kind != Task.Kind.SOCIAL, "de manhã também não");
		h.succeed();
	}

	// ---------------------------------------------------------------- 3) cama de casal do Móveis

	@GameTest(maxTicks = 300)
	public void homelessSleepsInTheDoubleBed(GameTestHelper h) {
		Village v = village(h);
		civic(h, v, "tavern");
		Block bed = moveis("red_double_bed");
		Villager vi = villager(h, new BlockPos(1, 1, 1), VillagerProfession.FARMER);
		vi.getBrain().eraseMemory(MemoryModuleType.HOME);
		if (bed == null) { // sem o Móveis: não há cama de casal, e nada parte
			Life.decideAt(h.getLevel(), v, vi, 13000, false);
			h.assertTrue(Tasks.get(vi) == null || Tasks.get(vi).kind != Task.Kind.SLEEP, "sem o Móveis: o vanilla trata do sono");
			h.succeed();
			return;
		}
		ServerPlayer p = TestUtil.playerAt(h, new BlockPos(5, 1, 3));
		TestUtil.placeWithItem(h, p, new ItemStack(bed.asItem()), new BlockPos(5, 1, 5));
		Life.rescan(v);
		Life.decideAt(h.getLevel(), v, vi, 13600, false);
		Task t = Tasks.get(vi);
		h.assertTrue(t != null && t.kind == Task.Kind.SLEEP, "sem cama, à noite, vai para a cama de casal");
		h.succeedWhen(() -> {
			h.assertTrue(vi.isSleeping(), "dorme na cama de casal");
			Life.checkAt(h.getLevel(), v, vi, 1000, false);
			h.assertTrue(!vi.isSleeping() && Tasks.get(vi) == null, "de manhã acorda");
		});
	}

	// ---------------------------------------------------------------- 4) clima

	@GameTest(maxTicks = 300)
	public void rainSendsToTheTavern(GameTestHelper h) {
		Village v = village(h);
		// uma «Taverna»: telhado sobre metade do teste
		for (int x = 0; x <= 8; x++)
			for (int z = 5; z <= 8; z++) h.setBlock(new BlockPos(x, 4, z), Blocks.OAK_PLANKS);
		civic(h, v, "tavern", 1, 6, 7, 7); // só debaixo do telhado
		Villager vi = villager(h, new BlockPos(2, 1, 1), VillagerProfession.FISHERMAN);
		h.runAfterDelay(20, () -> { // a luz do céu debaixo do telhado novo demora uns ticks
			Life.rescan(v);
			Climate.forcedRain = true;
			try {
				Life.decideAt(h.getLevel(), v, vi, 4000, false);
			} finally {
				Climate.forcedRain = null;
			}
			Task t = Tasks.get(vi);
			h.assertTrue(t != null && t.kind == Task.Kind.SHELTER, "com chuva vai para a Taverna; coberto " + Life.places(h.getLevel(), v).tavern().indoor.size());
			h.succeedWhen(() -> h.assertTrue(!h.getLevel().canSeeSky(vi.blockPosition().above()), "abrigado: tem telhado por cima; fim " + t.endReason + " alvo " + h.relativePos(t.target) + " está " + h.relativePos(vi.blockPosition()) + " chegou " + t.arrived));
		});
	}

	@GameTest(maxTicks = 300)
	public void coldSendsToTheFire(GameTestHelper h) {
		Village v = village(h);
		h.setBlock(new BlockPos(7, 1, 7), Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, true));
		civic(h, v, "town_hall");
		Villager vi = villager(h, new BlockPos(1, 1, 1), VillagerProfession.SHEPHERD);
		Climate.forced = 0; // com o Clima seriam os °C do Clima; aqui força-se o frio
		try {
			Life.decideAt(h.getLevel(), v, vi, 4000, false);
		} finally {
			Climate.forced = Double.NaN;
		}
		Task t = Tasks.get(vi);
		h.assertTrue(t != null && t.kind == Task.Kind.SHELTER, "com frio vai para a fogueira");
		BlockPos fire = h.absolutePos(new BlockPos(7, 1, 7));
		h.succeedWhen(() -> h.assertTrue(vi.blockPosition().closerThan(fire, 3.2), "ao pé da fogueira"));
	}

	// ---------------------------------------------------------------- 1) anti-preso e portas

	@GameTest(maxTicks = 400)
	public void opensTheDoorAndClosesItBehind(GameTestHelper h) {
		Village v = village(h);
		// parede com uma porta de madeira em z=4
		for (int x = 0; x <= 8; x++)
			for (int y = 1; y <= 3; y++) if (x != 4) h.setBlock(new BlockPos(x, y, 4), Blocks.STONE);
		h.setBlock(new BlockPos(4, 1, 4), Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.NORTH));
		h.setBlock(new BlockPos(4, 2, 4), Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.NORTH)
				.setValue(DoorBlock.HALF, net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER));
		h.setBlock(new BlockPos(4, 3, 4), Blocks.STONE);
		Villager vi = villager(h, new BlockPos(4, 1, 1), VillagerProfession.LIBRARIAN);
		long now = h.getLevel().getGameTime();
		Task t = new Task(Task.Kind.GO, now, now + 360).to(h.absolutePos(new BlockPos(4, 1, 7)), 0.9);
		Tasks.start(h.getLevel(), vi, t);
		BlockPos door = h.absolutePos(new BlockPos(4, 1, 4));
		boolean[] opened = {false};
		h.onEachTick(() -> {
			BlockState s = h.getLevel().getBlockState(door);
			if (s.getBlock() instanceof DoorBlock d && d.isOpen(s)) opened[0] = true;
		});
		h.succeedWhen(() -> {
			h.assertTrue(vi.getZ() > door.getZ() + 1.5, "passou a porta");
			h.assertTrue(opened[0], "abriu a porta");
			BlockState s = h.getLevel().getBlockState(door);
			h.assertTrue(s.getBlock() instanceof DoorBlock d && !d.isOpen(s), "fechou a porta atrás de si");
		});
	}

	@GameTest(maxTicks = 400)
	public void stuckGivesUpInTime(GameTestHelper h) {
		Village v = village(h);
		// preso numa caixa de pedra: refaz o caminho 2 vezes e desiste (plano B) muito antes do prazo
		for (int x = 1; x <= 3; x++)
			for (int z = 1; z <= 3; z++)
				for (int y = 1; y <= 3; y++) if (x != 2 || z != 2 || y == 3) h.setBlock(new BlockPos(x, y, z), Blocks.STONE);
		Villager vi = villager(h, new BlockPos(2, 1, 2), VillagerProfession.MASON);
		long now = h.getLevel().getGameTime();
		Task t = new Task(Task.Kind.GO, now, now + 2000).to(h.absolutePos(new BlockPos(7, 1, 7)), 1);
		Tasks.start(h.getLevel(), vi, t);
		h.succeedWhen(() -> {
			h.assertTrue(Tasks.get(vi) == null, "desistiu");
			h.assertValueEqual(t.endReason, "stuck", "por estar preso (não pelo prazo)");
		});
	}

	// ---------------------------------------------------------------- 5) guardas, curas, pânico

	@GameTest(maxTicks = 100)
	public void guardsArePaidByTheTreasuryOnly(GameTestHelper h) {
		Village v = village(h);
		ServerLevel level = h.getLevel();
		IronGolem g = h.spawn(EntityTypes.IRON_GOLEM, new BlockPos(2, 1, 2));
		v.economy.treasury = Care.wage() - 1; // não chega para um guarda
		long day = VillageEconomy.day(level);
		h.assertValueEqual(Care.dailyGuards(level, v, Life.places(level, v), day), 0, "sem dinheiro, sem guarda");
		h.assertValueEqual(v.economy.treasury, Care.wage() - 1, "a tesouraria não foi tocada (nunca fica negativa)");
		v.civic.life.guardDay = -1;
		v.economy.treasury = 10 * Care.wage();
		h.assertValueEqual(Care.dailyGuards(level, v, Life.places(level, v), day), 1, "um guarda (Aldeia: 1 posto)");
		h.assertValueEqual(v.economy.treasury, 9 * Care.wage(), "o salário saiu da tesouraria");
		h.assertTrue(Care.isGuard(g) && g.hasHome(), "o golem está no posto");
		// longe do posto e sem lutar: volta
		g.teleportTo(g.getX() + 0, g.getY(), g.getZ() + 0);
		BlockPos post = BlockPos.of(v.civic.life.guards.get(g.getUUID()));
		h.assertTrue(post.closerThan(v.center, 8), "o posto é a praça: " + post + " centro " + v.center);
		h.assertValueEqual(Care.dailyGuards(level, v, Life.places(level, v), day), 1, "no mesmo dia não se paga duas vezes");
		h.assertValueEqual(v.economy.treasury, 9 * Care.wage(), "não pagou outra vez");
		h.succeed();
	}

	@GameTest(maxTicks = 300)
	public void clericHealsAndSmithRepairsGolems(GameTestHelper h) {
		Village v = village(h);
		ServerLevel level = h.getLevel();
		Villager cleric = villager(h, new BlockPos(1, 1, 1), VillagerProfession.CLERIC);
		Villager hurt = villager(h, new BlockPos(6, 1, 6), VillagerProfession.FARMER);
		hurt.setNoAi(true);
		hurt.setHealth(6);
		Villager smith = villager(h, new BlockPos(1, 1, 7), VillagerProfession.ARMORER);
		IronGolem g = h.spawn(EntityTypes.IRON_GOLEM, new BlockPos(6, 1, 2));
		g.setNoAi(true);
		g.setHealth(30);
		Life.relist(v);
		h.assertTrue(Care.tryHeal(level, v, Life.places(level, v), cleric, Life.villagers(level, v)), "o clérigo vai curar");
		h.assertTrue(Care.tryRepair(level, v, Life.places(level, v), smith), "o armeiro vai reparar o golem");
		h.succeedWhen(() -> {
			h.assertTrue(hurt.hasEffect(MobEffects.REGENERATION), "o ferido tem Regeneração");
			h.assertTrue(g.getHealth() >= 30 + LifeRules.REPAIR_AMOUNT - 0.01f, "o golem foi reparado");
			h.assertTrue(!Props.holding(cleric) || cleric.getItemBySlot(EquipmentSlot.MAINHAND).is(Items.POTION), "poção na mão (item de cena)");
		});
	}

	@GameTest(maxTicks = 100)
	public void healsAreCappedPerDay(GameTestHelper h) {
		Village v = village(h);
		ServerLevel level = h.getLevel();
		Villager cleric = villager(h, new BlockPos(4, 1, 4), VillagerProfession.CLERIC);
		Villager hurt = villager(h, new BlockPos(5, 1, 4), VillagerProfession.FARMER);
		hurt.setNoAi(true);
		Life.relist(v);
		hurt.setHealth(4);
		long day = VillageEconomy.day(level);
		int ok = 0;
		for (int i = 0; i < 6; i++) {
			if (Care.tryHeal(level, v, Life.places(level, v), cleric, Life.villagers(level, v))) {
				ok++;
				Tasks.end(level, cleric, Tasks.get(cleric), "test");
				Care.countHeal(cleric, day); // como se tivesse chegado e curado
			}
		}
		h.assertValueEqual(ok, LifeRules.HEALS_PER_DAY, "3 curas por dia, nem mais uma");
		h.assertTrue(ok <= LifeRules.HEALS_PER_DAY, "no máximo 3 curas por dia: " + ok);
		h.succeed();
	}

	@GameTest(maxTicks = 100)
	public void panicSendsNeighborsToTheGolem(GameTestHelper h) {
		Village v = village(h);
		ServerLevel level = h.getLevel();
		IronGolem g = h.spawn(EntityTypes.IRON_GOLEM, new BlockPos(7, 1, 7));
		g.setNoAi(true);
		Villager victim = villager(h, new BlockPos(1, 1, 1), VillagerProfession.FARMER);
		Villager near = villager(h, new BlockPos(3, 1, 1), VillagerProfession.MASON);
		var zombie = h.spawn(EntityTypes.ZOMBIE, new BlockPos(1, 1, 3));
		zombie.setNoAi(true);
		Life.relist(v);
		int n = Care.panic(level, v, Life.places(level, v), victim, zombie);
		h.assertTrue(n >= 1, "os vizinhos fugiram: " + n);
		Task t = Tasks.get(near);
		h.assertTrue(t != null && t.kind == Task.Kind.FLEE && t.follow == g, "o vizinho corre para o golem");
		h.assertTrue(t.speed > 0.8, "a correr (o Fresh Animations mostra «assustado»)");
		zombie.discard();
		h.succeed();
	}

	// ---------------------------------------------------------------- 6) conversas e fofoca

	@GameTest(maxTicks = 300)
	public void villagersChatAboutRealFactsAndSpreadThem(GameTestHelper h) {
		Village v = village(h);
		ServerLevel level = h.getLevel();
		Villager a = villager(h, new BlockPos(3, 1, 4), VillagerProfession.FARMER);
		Villager b = villager(h, new BlockPos(5, 1, 4), VillagerProfession.LIBRARIAN);
		Life.relist(v);
		// um facto que só o A viu (o B está «longe»: tira-se da lista de quem sabe)
		LifeData.Fact f = Talk.fact(level, v, a.blockPosition(), "mission", "Talys");
		f.knowers.remove(b.getUUID());
		h.assertTrue(f.knowers.contains(a.getUUID()) && !f.knowers.contains(b.getUUID()), "só o A sabe");
		Talk.start(level, v, Life.places(level, v), a, b);
		h.assertTrue(f.knowers.contains(b.getUUID()), "ao ouvir, o B fica a saber (espalha-se de boca em boca)");
		h.assertTrue(Talk.lastLine.getString().contains("Talys") || Talk.lastLine.toString().contains("Talys"), "fala do facto real: " + Talk.lastLine);
		h.assertTrue(Tasks.get(a) != null && Tasks.get(a).kind == Task.Kind.TALK && Tasks.get(b) != null, "param os dois a conversar");
		h.assertTrue(v.civic.life.opinion(a.getUUID(), b.getUUID()) != 0, "a opinião do par mudou");
		h.assertTrue(((com.tcordeiro.vilas.life.LifeVillager) a).tvilas$memo().nextTalk > level.getGameTime() + 1000, "tempo de espera de 1 min");
		h.succeedWhen(() -> h.assertTrue(Tasks.get(a) == null && Tasks.get(b) == null, "a conversa acaba sozinha"));
	}

	// ---------------------------------------------------------------- 7) festa (reembolso da tesouraria)

	@GameTest(maxTicks = 60)
	public void festaRebateComesFromTheTreasuryWithCaps(GameTestHelper h) {
		Village v = village(h);
		ServerLevel level = h.getLevel();
		ServerPlayer p = TestUtil.playerAt(h, new BlockPos(4, 1, 4));
		p.getInventory().clearContent();
		var offer = new net.minecraft.world.item.trading.MerchantOffer(new net.minecraft.world.item.trading.ItemCost(Items.EMERALD, 10),
				new ItemStack(Items.BREAD, 6), 99, 1, 0.05f);
		long unit = com.tcordeiro.vilas.VilasConfig.get().emerald_value_cents;
		Festa.forceActive = true;
		try {
			v.economy.treasury = 0;
			h.assertValueEqual(Festa.onTrade(p, v, offer), 0L, "tesouraria vazia: nada de reembolso (nunca dinheiro novo)");
			v.economy.treasury = 100_000;
			long before = v.economy.treasury, money = TestUtil.money(p);
			long paid = 0;
			for (int i = 0; i < 10; i++) paid += Festa.onTrade(p, v, offer);
			h.assertTrue(paid > 0, "houve reembolso: esmeraldas " + com.tcordeiro.vilas.economy.CoinTrades.of(offer).tvilas$emeraldsA() + " tesouraria " + v.economy.treasury + " devido " + v.civic.life.festa.owed + " gasto " + v.civic.life.festa.spent);
			h.assertTrue(paid <= LifeRules.festaPlayerCap(TVilas.CUBCOIN ? 1 : unit), "teto por jogador: " + paid);
			h.assertValueEqual(before - v.economy.treasury, paid, "saiu tudo da tesouraria");
			h.assertValueEqual(TestUtil.money(p) - money, paid, "o jogador recebeu exatamente o que saiu");
			if (!TVilas.CUBCOIN) h.assertTrue(paid % unit == 0, "sem CubCoin: esmeraldas inteiras");
		} finally {
			Festa.forceActive = null;
		}
		Festa.forceActive = false;
		try {
			h.assertValueEqual(Festa.onTrade(p, v, offer), 0L, "fora da festa, nada");
		} finally {
			Festa.forceActive = null;
		}
		h.succeed();
	}

	@GameTest(maxTicks = 200)
	public void festaGathersAtThePlaza(GameTestHelper h) {
		Village v = village(h);
		civic(h, v, "plaza");
		ServerLevel level = h.getLevel();
		long today = VillageEconomy.day(level);
		h.assertTrue(Festa.schedule(level, v, today + 1, "level"), "marcada para amanhã (o Prefeito avisa)");
		h.assertTrue(!Festa.schedule(level, v, today + 2, "merchant"), "uma festa de cada vez");
		Villager vi = villager(h, new BlockPos(1, 1, 1), VillagerProfession.BUTCHER);
		Life.relist(v);
		Life.decideAt(level, v, vi, LifeRules.FESTA_START + 500, true);
		Task t = Tasks.get(vi);
		h.assertTrue(t != null && t.kind == Task.Kind.FESTA, "na festa vai para a Praça");
		Life.checkAt(level, v, vi, LifeRules.FESTA_START + 600, false);
		h.assertTrue(Tasks.get(vi) == null, "acabou a festa, volta ao normal");
		h.succeed();
	}

	// ---------------------------------------------------------------- 8) aprendizes

	@GameTest(maxTicks = 300)
	public void apprenticeFollowsTheMasterAndInheritsTheJob(GameTestHelper h) {
		Village v = village(h);
		ServerLevel level = h.getLevel();
		Villager master = villager(h, new BlockPos(7, 1, 7), VillagerProfession.FLETCHER);
		master.setNoAi(true);
		Villager kid = h.spawn(EntityTypes.VILLAGER, new BlockPos(1, 1, 1));
		kid.setAge(-24000);
		Life.relist(v);
		Life.decideAt(level, v, kid, 3000, false);
		h.assertValueEqual(v.civic.life.apprentices.get(kid.getUUID()), "fletcher", "escolheu o mestre (flecheiro)");
		Task t = Tasks.get(kid);
		h.assertTrue(t != null && t.kind == Task.Kind.FOLLOW && t.follow == master, "de dia segue o mestre");
		h.succeedWhen(() -> {
			h.assertTrue(kid.distanceToSqr(master) < 4.5 * 4.5, "chegou ao pé do mestre");
			Tasks.end(level, kid, Tasks.get(kid) == null ? t : Tasks.get(kid), "test");
			kid.setAge(0);
			h.assertTrue(Life.graduate(level, v, kid), "cresceu");
			h.assertTrue(kid.getVillagerData().profession().is(VillagerProfession.FLETCHER), "herdou a profissão do mestre");
		});
	}

	// ---------------------------------------------------------------- B) presentes, menu, reputação, caminho

	@GameTest(maxTicks = 60)
	public void giftsHaveTastesAndWeeklyCaps(GameTestHelper h) {
		Village v = village(h);
		ServerLevel level = h.getLevel();
		Villager lib = villager(h, new BlockPos(4, 1, 5), VillagerProfession.LIBRARIAN);
		lib.setNoAi(true);
		ServerPlayer p = TestUtil.playerAt(h, new BlockPos(4, 1, 3));
		p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL); // no criativo o presente não se gasta (como no vanilla)
		int rep0 = v.reputationOf(p.getUUID()), gossip0 = lib.getPlayerReputation(p);
		ItemStack flesh = new ItemStack(Items.ROTTEN_FLESH, 3);
		p.setItemInHand(InteractionHand.MAIN_HAND, flesh);
		h.assertValueEqual(Resident.gift(p, level, v, lib, flesh), LifeRules.Taste.DISLIKED, "carne podre: detesta");
		h.assertValueEqual(flesh.getCount(), 3, "o presente detestado volta (não se perde)");
		h.assertValueEqual(v.reputationOf(p.getUUID()), rep0, "sem castigo");
		ItemStack books = new ItemStack(Items.BOOK, 5);
		p.setItemInHand(InteractionHand.MAIN_HAND, books);
		h.assertValueEqual(Resident.gift(p, level, v, lib, books), LifeRules.Taste.LOVED, "o bibliotecário adora livros");
		h.assertValueEqual(books.getCount(), 4, "o livro foi dado");
		h.assertTrue(lib.getPlayerReputation(p) > gossip0, "mexericos vanilla a favor (preços melhores)");
		h.assertTrue(v.reputationOf(p.getUUID()) > rep0, "reputação na vila");
		Resident.gift(p, level, v, lib, books);
		h.assertTrue(Resident.gift(p, level, v, lib, books) == null, "3.º presente na semana: recusado");
		h.assertValueEqual(books.getCount(), 3, "o 3.º não foi gasto");
		h.assertTrue(v.reputationOf(p.getUUID()) - rep0 <= LifeRules.GIFT_REP_PER_PLAYER_DAY, "teto de reputação por dia");
		h.succeed();
	}

	@GameTest(maxTicks = 60)
	public void giftReputationHasADailyCapAcrossVillagers(GameTestHelper h) {
		Village v = village(h);
		ServerLevel level = h.getLevel();
		ServerPlayer p = TestUtil.playerAt(h, new BlockPos(4, 1, 1));
		int rep0 = v.reputationOf(p.getUUID());
		for (int i = 0; i < 6; i++) {
			Villager vi = villager(h, new BlockPos(1 + i, 1, 6), VillagerProfession.LIBRARIAN);
			vi.setNoAi(true);
			ItemStack b = new ItemStack(Items.BOOK, 2);
			p.setItemInHand(InteractionHand.MAIN_HAND, b);
			Resident.gift(p, level, v, vi, b);
			Resident.gift(p, level, v, vi, b);
		}
		h.assertValueEqual(v.reputationOf(p.getUUID()) - rep0, LifeRules.GIFT_REP_PER_PLAYER_DAY, "12 livros adorados, mas só o teto do dia");
		h.succeed();
	}

	@GameTest(maxTicks = 100)
	public void friendGivesAGiftBackOnceAWeekNeverMoney(GameTestHelper h) {
		Village v = village(h);
		ServerLevel level = h.getLevel();
		Villager vi = villager(h, new BlockPos(4, 1, 5), VillagerProfession.CLERIC);
		vi.setNoAi(true);
		ServerPlayer p = TestUtil.playerAt(h, new BlockPos(4, 1, 3));
		h.assertTrue(!Resident.maybeThank(level, v, vi, p), "ainda não é Amigo");
		vi.getGossips().add(p.getUUID(), GossipType.MAJOR_POSITIVE, 100);
		h.assertTrue(Resident.maybeThank(level, v, vi, p), "Amigo: dá um presente");
		h.assertTrue(!Resident.maybeThank(level, v, vi, p), "uma vez por semana (e por dia)");
		h.runAfterDelay(20, () -> {
			List<ItemEntity> drops = level.getEntitiesOfClass(ItemEntity.class, new AABB(vi.blockPosition()).inflate(6));
			for (ItemEntity e : drops) h.assertTrue(!e.getItem().is(Items.EMERALD), "nunca dinheiro");
			h.succeed();
		});
	}

	@GameTest(maxTicks = 60)
	public void reputationTiersFromGossip(GameTestHelper h) {
		h.assertValueEqual(LifeRules.villagerTier(-50), LifeRules.TIER_DISTRUST, "desconfiado");
		h.assertValueEqual(LifeRules.villagerTier(0), LifeRules.TIER_NEUTRAL, "neutro");
		h.assertValueEqual(LifeRules.villagerTier(30), LifeRules.TIER_FRIENDLY, "simpático");
		h.assertValueEqual(LifeRules.villagerTier(80), LifeRules.TIER_FRIEND, "amigo");
		Village v = village(h);
		Villager vi = villager(h, new BlockPos(4, 1, 5), VillagerProfession.FARMER);
		vi.setNoAi(true);
		ServerPlayer p = TestUtil.playerAt(h, new BlockPos(4, 1, 3));
		p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		p.setShiftKeyDown(true);
		int menus = Resident.menus;
		var r = net.fabricmc.fabric.api.event.player.UseEntityCallback.EVENT.invoker().interact(p, h.getLevel(), InteractionHand.MAIN_HAND, vi, null);
		h.assertValueEqual(r, net.minecraft.world.InteractionResult.SUCCESS, "Shift+clique com a mão vazia");
		h.assertValueEqual(Resident.menus, menus + 1, "abre o menu do morador");
		p.setShiftKeyDown(false);
		h.assertValueEqual(net.fabricmc.fabric.api.event.player.UseEntityCallback.EVENT.invoker().interact(p, h.getLevel(), InteractionHand.MAIN_HAND, vi, null),
				net.minecraft.world.InteractionResult.PASS, "o clique simples continua a ir para as trocas vanilla");
		h.succeed();
	}

	@GameTest(maxTicks = 100)
	public void askingTheWayPointsAndAnswers(GameTestHelper h) {
		Village v = village(h);
		civic(h, v, "tavern");
		ServerLevel level = h.getLevel();
		Villager vi = villager(h, new BlockPos(1, 1, 1), VillagerProfession.FARMER);
		ServerPlayer p = TestUtil.playerAt(h, new BlockPos(2, 1, 1));
		var r = Resident.directions(p, level, v, vi, "tavern");
		h.assertTrue(r.ok(), "respondeu");
		h.assertTrue(r.message().getString().contains("📍"), "com a distância e o rumo: " + r.message().getString());
		h.assertTrue(Tasks.get(vi) != null && Tasks.get(vi).kind == Task.Kind.POINT, "vira-se para lá («aponta»)");
		h.assertTrue(!Resident.directions(p, level, v, vi, "market").ok(), "lugar que a vila não tem: diz que não há");
		h.succeed();
	}

	// ---------------------------------------------------------------- B) trabalho visível, itens de cena

	@GameTest(maxTicks = 100)
	public void propsNeverDrop(GameTestHelper h) {
		Village v = village(h);
		ServerLevel level = h.getLevel();
		Villager vi = villager(h, new BlockPos(4, 1, 4), VillagerProfession.WEAPONSMITH);
		h.assertTrue(Props.hold(vi, new ItemStack(Items.IRON_SWORD), 200), "espada de cena na mão");
		h.assertTrue(Props.holding(vi), "é de cena");
		vi.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.DIAMOND));
		h.assertTrue(!Props.hold(vi, new ItemStack(Items.BOOK), 20), "não tira um item verdadeiro da mão");
		vi.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
		Props.hold(vi, new ItemStack(Items.IRON_SWORD), 200);
		vi.kill(level);
		h.runAfterDelay(5, () -> {
			for (ItemEntity e : level.getEntitiesOfClass(ItemEntity.class, new AABB(h.absolutePos(new BlockPos(4, 1, 4))).inflate(4)))
				h.assertTrue(!e.getItem().is(Items.IRON_SWORD), "a espada de cena nunca cai");
			h.succeed();
		});
	}

	@GameTest(maxTicks = 100)
	public void workIsVisibleAtTheWorkstation(GameTestHelper h) {
		Village v = village(h);
		ServerLevel level = h.getLevel();
		h.setBlock(new BlockPos(4, 1, 5), Blocks.LECTERN);
		Villager vi = villager(h, new BlockPos(4, 1, 4), VillagerProfession.LIBRARIAN);
		vi.setNoAi(true);
		vi.getBrain().setMemory(MemoryModuleType.JOB_SITE, net.minecraft.core.GlobalPos.of(level.dimension(), h.absolutePos(new BlockPos(4, 1, 5))));
		vi.getBrain().setActiveActivityIfPossible(net.minecraft.world.entity.schedule.Activity.WORK);
		Life.relist(v);
		int works = Life.works;
		Life.decideAt(level, v, vi, 4000, false);
		h.assertValueEqual(Life.works, works + 1, "mostra o trabalho na bancada");
		h.assertTrue(vi.getItemBySlot(EquipmentSlot.MAINHAND).is(Items.BOOK) && Props.holding(vi), "livro (de cena) na mão");
		h.succeed();
	}

	// ---------------------------------------------------------------- C) lanterna à noite, reações, LOD

	@GameTest(maxTicks = 60)
	public void lanternAtNightOutdoors(GameTestHelper h) {
		Village v = village(h);
		Villager vi = villager(h, new BlockPos(4, 1, 4), VillagerProfession.NITWIT);
		vi.setNoAi(true);
		vi.getBrain().setMemory(MemoryModuleType.HOME, net.minecraft.core.GlobalPos.of(h.getLevel().dimension(), vi.blockPosition()));
		Life.relist(v);
		Life.decideAt(h.getLevel(), v, vi, 13500, false);
		h.assertTrue(vi.getItemBySlot(EquipmentSlot.MAINHAND).is(Items.LANTERN), "lanterna na mão à noite");
		Life.decideAt(h.getLevel(), v, vi, 3000, false);
		h.assertTrue(vi.getItemBySlot(EquipmentSlot.MAINHAND).isEmpty(), "de dia guarda a lanterna");
		h.succeed();
	}

	@GameTest(maxTicks = 100)
	public void farAwayVillagersAreLeftToVanilla(GameTestHelper h) {
		h.assertValueEqual(LifeRules.lod(10 * 10), LifeRules.LOD_FULL, "perto: tudo");
		h.assertValueEqual(LifeRules.lod(70 * 70), LifeRules.LOD_REDUCED, "médio: o essencial");
		h.assertValueEqual(LifeRules.lod(200 * 200), LifeRules.LOD_NONE, "longe: nada");
		Village v = village(h);
		Villager vi = villager(h, new BlockPos(4, 1, 4), VillagerProfession.FARMER);
		long now = h.getLevel().getGameTime();
		Task t = new Task(Task.Kind.GO, now, now + 30).to(h.absolutePos(new BlockPos(7, 1, 7)), 0.5);
		Tasks.start(h.getLevel(), vi, t);
		h.succeedWhen(() -> {
			h.assertTrue(Tasks.get(vi) == null, "toda a tarefa tem prazo");
			h.assertTrue(t.endReason != null, "acabou: " + t.endReason);
		});
	}

	// ---------------------------------------------------------------- reações, reunião, desempenho

	@GameTest(maxTicks = 100)
	public void swordMakesThemBackOff(GameTestHelper h) {
		Village v = village(h);
		ServerLevel level = h.getLevel();
		Villager vi = villager(h, new BlockPos(4, 1, 5), VillagerProfession.MASON);
		ServerPlayer p = TestUtil.playerAt(h, new BlockPos(4, 1, 3));
		p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
		Life.relist(v);
		int reacts = Life.reacts;
		Life.reactionsAt(level, v, p);
		h.assertValueEqual(Life.reacts, reacts + 1, "reage à espada");
		h.assertTrue(Tasks.get(vi) != null && Tasks.get(vi).kind == Task.Kind.GO, "recua");
		Life.reactionsAt(level, v, p);
		h.assertValueEqual(Life.reacts, reacts + 1, "com tempo de espera (não repete logo)");
		h.succeed();
	}

	@GameTest(maxTicks = 100)
	public void meetingNeedsReputationAndOncePerDay(GameTestHelper h) {
		Village v = village(h);
		civic(h, v, "plaza");
		ServerLevel level = h.getLevel();
		villager(h, new BlockPos(1, 1, 1), VillagerProfession.MASON);
		Life.relist(v);
		ServerPlayer p = TestUtil.playerAt(h, new BlockPos(4, 1, 3));
		p.permissions(); // (jogador falso: sem permissões de operador)
		v.reputation.put(p.getUUID(), 0);
		h.assertTrue(!Life.callMeeting(p, v).ok() || p.permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER),
				"sem reputação não convoca");
		v.reputation.put(p.getUUID(), 50);
		h.assertTrue(Life.callMeeting(p, v).ok(), "Simpático: convoca");
		h.assertTrue(!Life.callMeeting(p, v).ok(), "uma vez por dia");
		h.succeed();
	}

	@GameTest(maxTicks = 400)
	public void fortyVillagersTickCost(GameTestHelper h) {
		Village v = village(h);
		civic(h, v, "plaza");
		ServerLevel level = h.getLevel();
		for (int i = 0; i < 40; i++) {
			Villager vi = villager(h, new BlockPos(1 + i % 7, 1, 1 + (i / 7) % 7), i % 3 == 0 ? VillagerProfession.FARMER : VillagerProfession.MASON);
			vi.setNoAi(i % 2 == 0);
		}
		TestUtil.playerAt(h, new BlockPos(4, 1, 4));
		Life.relist(v);
		long n0 = Life.nanos, c0 = Life.calls;
		for (int k = 0; k < 200; k++) Life.tick(level, v);
		long per = (Life.nanos - n0) / Math.max(1, Life.calls - c0);
		System.out.println("[Vilas040] DESEMPENHO (GameTest): 40 aldeões, vida da vila " + String.format("%.3f", per / 1e6) + " ms por chamada (1 por segundo)");
		h.assertTrue(per < 5_000_000, "menos de 5 ms por segundo de jogo: " + per / 1e6 + " ms");
		h.succeed();
	}

	@GameTest(maxTicks = 200)
	public void welcomeBackAfterThreeDays(GameTestHelper h) {
		Village v = village(h);
		ServerLevel level = h.getLevel();
		Villager friend = villager(h, new BlockPos(7, 1, 7), VillagerProfession.BUTCHER);
		ServerPlayer p = TestUtil.playerAt(h, new BlockPos(1, 1, 1));
		friend.getGossips().add(p.getUUID(), GossipType.MAJOR_POSITIVE, 60);
		Life.relist(v);
		v.civic.life.lastSeen.put(p.getUUID(), VillageEconomy.day(level) - 1);
		int w = Life.welcomes;
		Life.reactionsAt(level, v, p);
		h.assertTrue(Tasks.get(friend) == null, "1 dia fora: nada");
		v.civic.life.lastSeen.put(p.getUUID(), VillageEconomy.day(level) - 4);
		Life.reactionsAt(level, v, p);
		h.assertTrue(Tasks.get(friend) != null && Tasks.get(friend).kind == Task.Kind.WELCOME, "4 dias fora: o amigo vem ter com ele");
		h.assertValueEqual(v.civic.life.lastSeen.get(p.getUUID()), VillageEconomy.day(level), "fica registado que voltou");
		h.succeedWhen(() -> h.assertTrue(Life.welcomes > w, "chegou e deu as boas-vindas"));
	}
}
