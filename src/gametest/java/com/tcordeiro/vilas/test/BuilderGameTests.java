package com.tcordeiro.vilas.test;

import java.util.List;

import com.tcordeiro.vilas.build.Builders;
import com.tcordeiro.vilas.build.GolemWork;
import com.tcordeiro.vilas.build.LightingWork;
import com.tcordeiro.vilas.build.StreetLightKit;
import com.tcordeiro.vilas.build.WallWork;
import com.tcordeiro.vilas.logic.BuildRules;
import com.tcordeiro.vilas.village.Lot;
import com.tcordeiro.vilas.village.Project;
import com.tcordeiro.vilas.village.Village;
import com.tcordeiro.vilas.village.VillageData;
import com.tcordeiro.vilas.village.VillageEconomy;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 0.2.0 — construtores, muralha e iluminação: com alguém a ver, os blocos vão um a um; sem ninguém, aparecem depois da noite;
 * a muralha NUNCA destrói nem substitui blocos de jogador nem nada dentro de terrenos (salta-os e contorna-os).
 */
public class BuilderGameTests {
	static void cleanup(GameTestHelper h) {
		h.killAllEntitiesOfClass(Villager.class);
		h.killAllEntitiesOfClass(IronGolem.class);
	}

	@GameTest
	public void builderPlacesBlocksOneByOneWhileWatched(GameTestHelper h) {
		Village v = TestUtil.village(h);
		ServerPlayer watcher = TestUtil.playerAt(h, new BlockPos(1, 1, 7));
		Project p = GolemWork.create(h.getLevel(), v);
		h.assertTrue(p != null, "há sítio para o golem");
		Builders.start(h.getLevel(), v, p);
		int total = p.pending.size(), last = 0;
		long now = h.getLevel().getGameTime();
		for (int step = 0; step < 200 && !v.extra.projects.isEmpty(); step++) {
			now += BuildRules.STEP_TICKS;
			Builders.tick(h.getLevel(), v, now);
			h.assertTrue(p.placed - last <= 1, "um bloco de cada vez (passo " + step + ": " + last + " → " + p.placed + ")");
			if (p.placed > last && p.placed < total)
				h.assertTrue(v.extra.builder != null, "há um Construtor a trabalhar");
			last = p.placed;
		}
		h.assertValueEqual(p.placed, total, "todos os blocos do golem, um a um");
		h.assertTrue(v.extra.projects.isEmpty(), "obra acabada");
		h.assertTrue(!h.getLevel().getEntitiesOfClass(IronGolem.class, new net.minecraft.world.phys.AABB(p.site).inflate(4)).isEmpty(), "o golem acordou");
		h.assertTrue(v.extra.builder == null, "o Construtor foi embora");
		watcher.discard();
		cleanup(h);
		h.succeed();
	}

	@GameTest
	public void unwatchedWorkAppearsAfterTheNight(GameTestHelper h) {
		Village v = TestUtil.village(h);
		Project p = GolemWork.create(h.getLevel(), v);
		Builders.start(h.getLevel(), v, p);
		long now = h.getLevel().getGameTime();
		for (int i = 0; i < 20; i++) Builders.tick(h.getLevel(), v, now += BuildRules.STEP_TICKS);
		h.assertValueEqual(p.placed, 0, "ninguém a ver: nada se constrói durante o dia");
		h.assertTrue(v.extra.builder == null, "nem vem um Construtor");
		// a noite passa (o dia muda): o que falta aparece
		long day = VillageEconomy.day(h.getLevel()) + 4000;
		VillageEconomy.daily(h.getLevel(), v, day);
		VillageEconomy.daily(h.getLevel(), v, day + 1);
		h.assertTrue(v.extra.projects.isEmpty(), "a obra apareceu depois da noite");
		h.assertValueEqual(p.placed, p.total, "todos os blocos postos de noite");
		h.assertTrue(!h.getLevel().getEntitiesOfClass(IronGolem.class, new net.minecraft.world.phys.AABB(p.site).inflate(4)).isEmpty(), "e o golem com ela");
		cleanup(h);
		h.succeed();
	}

	@GameTest
	public void wallNeverDestroysPlayerOrLotBlocks(GameTestHelper h) {
		Village v = TestUtil.village(h); // raio 6 → muralha de meio lado 3: perímetro x/z = 1 e 7
		v.economy.treasury = 100_000;
		ServerPlayer owner = TestUtil.playerAt(h, new BlockPos(6, 1, 6));
		// um bloco de jogador NO perímetro: um baú com coisas dentro
		BlockPos chest = new BlockPos(1, 1, 4);
		h.setBlock(chest, Blocks.CHEST);
		((ChestBlockEntity) h.getBlockEntity(chest, ChestBlockEntity.class)).setItem(0, new ItemStack(Items.DIAMOND, 3));
		// uma parede de jogador no chão do perímetro (tábuas por cima da pedra)
		BlockPos planks = new BlockPos(4, 1, 7);
		h.setBlock(planks, Blocks.OAK_PLANKS);
		// erva e flor no perímetro: essas sim, dão lugar à muralha
		BlockPos flower = new BlockPos(3, 1, 1), grass = new BlockPos(7, 1, 3);
		h.setBlock(flower, Blocks.POPPY);
		// um bloco de jogador no ar, a meio da altura de uma coluna (só se vê ao pôr o bloco, não no plano)
		BlockPos glass = new BlockPos(5, 2, 1);
		h.setBlock(glass, Blocks.GLASS);
		h.setBlock(grass, Blocks.SHORT_GRASS);
		// um terreno no canto (x/z 5..7, em cima do perímetro): nem o ar dele é tocado — a muralha passa por fora
		Lot lot = TestUtil.lot(h, v, owner, 5, 5, 7, 7);
		Project p = WallWork.create(v);
		Builders.start(h.getLevel(), v, p);
		for (int night = 0; night < 5 && !v.extra.projects.isEmpty(); night++) Builders.overnight(h.getLevel(), v);
		h.assertTrue(v.extra.projects.isEmpty(), "muralha acabada");
		// nada de jogador foi destruído
		h.assertBlockPresent(Blocks.CHEST, chest);
		h.assertValueEqual(((ChestBlockEntity) h.getBlockEntity(chest, ChestBlockEntity.class)).getItem(0).getCount(), 3, "o baú tem tudo");
		h.assertBlockPresent(Blocks.OAK_PLANKS, planks);
		h.assertBlockPresent(Blocks.GLASS, glass);
		// o mundo mudou entre o plano e a obra: o bloco que lá apareceu fica e o da obra é saltado
		BlockPos late = new BlockPos(2, 2, 2);
		h.setBlock(late, Blocks.GLASS);
		Project q = new Project(Project.Kind.LIGHTING, h.absolutePos(late));
		q.pending.add(new Project.Placement(h.absolutePos(late), Blocks.STONE_BRICKS.defaultBlockState()));
		q.total = 1;
		Builders.start(h.getLevel(), v, q);
		Builders.overnight(h.getLevel(), v);
		h.assertBlockPresent(Blocks.GLASS, late);
		h.assertValueEqual(q.skipped, 1, "o bloco planeado foi saltado");
		v.extra.projects.clear();
		h.assertTrue(h.getBlockState(planks.above()).isAir(), "não se constrói por cima de blocos de jogador");
		// nada dentro do terreno
		for (int x = 5; x <= 7; x++)
			for (int z = 5; z <= 7; z++)
				for (int y = 1; y <= 6; y++)
					h.assertTrue(h.getBlockState(new BlockPos(x, y, z)).isAir(), "o terreno ficou intacto em " + x + "," + y + "," + z);
		// erva e flor deram lugar à muralha
		h.assertTrue(!h.getBlockState(flower).isAir() && !h.getBlockState(flower).is(Blocks.POPPY), "a flor deu lugar à muralha");
		h.assertTrue(!h.getBlockState(grass).isAir() && !h.getBlockState(grass).is(Blocks.SHORT_GRASS), "a erva deu lugar à muralha");
		// contornou o baú: a coluna foi 1 bloco para fora (x = 0)
		h.assertTrue(!h.getBlockState(new BlockPos(0, 1, 4)).isAir(), "contornou o baú por fora");
		h.assertTrue(!h.getBlockState(new BlockPos(6, 1, 8)).isAir(), "contornou o terreno por fora");
		h.assertValueEqual(p.nextColumn, p.total, "passou por todas as colunas");
		h.assertTrue(p.placed >= 30, "e construiu o resto: " + p.placed);
		VillageData.get(h.getLevel()).lots.remove(lot);
		owner.discard();
		h.succeed();
	}

	@GameTest(skyAccess = true) // 0.3.0: a fonte precisa de céu aberto (sem o teto de barreiras do teste)
	public void streetLightsBesideTheStreetsWithLanterns(GameTestHelper h) {
		TestUtil.clean(h);
		TestUtil.floor(h, 4);
		// vila de raio 4: o território é só o chão deste teste
		Village v = com.tcordeiro.vilas.village.VillageManager.found(h.getLevel(), h.absolutePos(new BlockPos(4, 1, 4)), 4);
		for (int x = 0; x <= 8; x++) h.setBlock(new BlockPos(x, 0, 4), Blocks.DIRT_PATH); // rua de oeste a leste
		StreetLightKit kit = StreetLightKit.forVillage(h.getLevel(), v);
		boolean poles = net.minecraft.core.registries.BuiltInRegistries.BLOCK.containsKey(StreetLightKit.woodPole("oak"));
		h.assertValueEqual(kit.industrial(), poles, "postes do Industrial se ele os tiver (0.11+); senão lanternas");
		Project p = LightingWork.create(h.getLevel(), v);
		h.assertTrue(p != null, "há sítios ao lado da rua");
		var level = h.getLevel();
		Builders.start(h.getLevel(), v, p);
		Builders.overnight(h.getLevel(), v);
		h.assertTrue(v.extra.poles >= 1, "postes postos: " + v.extra.poles);
		// um poste alto com um bloco de jogador por cima do sítio (posto depois do plano): não se põe, nem pela metade
		BlockPos spot = h.absolutePos(new BlockPos(4, 1, 7));
		level.setBlock(spot.above(kit.height() - 1), Blocks.GLASS.defaultBlockState(), 3);
		Project q = new Project(Project.Kind.LIGHTING, spot);
		q.pending.add(new Project.Placement(spot, kit.pole().get(0), kit.height()));
		q.total = 1;
		Builders.start(h.getLevel(), v, q);
		Builders.overnight(h.getLevel(), v);
		h.assertTrue(level.getBlockState(spot.above(kit.height() - 1)).is(Blocks.GLASS), "o bloco do jogador ficou");
		h.assertTrue(level.getBlockState(spot).isAir() && q.skipped == 1, "sem a altura livre o poste não se põe");
		BlockPos first = null;
		for (BlockPos a : p.anchors) {
			if (!kit.isPole(level.getBlockState(a))) continue; // a fonte da Prefeitura
			if (first == null) first = a;
			boolean byStreet = false;
			for (var d : net.minecraft.core.Direction.Plane.HORIZONTAL) byStreet |= level.getBlockState(a.below().relative(d)).is(Blocks.DIRT_PATH);
			h.assertTrue(byStreet, "ao lado da rua: " + a);
			h.assertTrue(v.covers(a.getX(), a.getZ()), "dentro da vila: " + a);
			if (kit.industrial()) {
				for (int i = 0; i < kit.height(); i++)
					h.assertTrue(level.getBlockState(a.above(i)).is(level.getBlockState(a).getBlock()), "poste do Industrial inteiro em " + a + " +" + i);
			} else {
				h.assertTrue(level.getBlockState(a).is(Blocks.OAK_FENCE) && level.getBlockState(a.above()).is(Blocks.OAK_FENCE), "poste de cerca em " + a);
				h.assertTrue(level.getBlockState(a.above(2)).is(Blocks.LANTERN), "lanterna em cima em " + a);
			}
		}
		h.assertTrue(first != null, "pelo menos um poste");
		List<BlockPos> src = v.extra.lightAnchors.stream().map(BlockPos::of).filter(a -> !kit.isPole(level.getBlockState(a)) && !level.getBlockState(a).isAir()).toList();
		h.assertValueEqual((long) src.size(), (long) kit.sources().size(), "fonte da Prefeitura (painéis e bateria; nada com as lanternas)");
		if (kit.industrial()) {
			h.assertValueEqual(src.size(), 3, "2 painéis e uma bateria na Prefeitura");
			long poleCount = v.extra.lightAnchors.stream().filter(a -> kit.isPole(level.getBlockState(BlockPos.of(a)))).count();
			h.assertValueEqual((long) p.linked, poleCount, "fios: cada poste ao anterior e UM fio para a bateria");
			BlockPos bat = src.stream().filter(a -> kit.isBattery(level.getBlockState(a))).findFirst().orElse(null);
			h.assertTrue(bat != null && LightingWork.wires(level, bat) == 1, "só a bateria leva fio");
			for (BlockPos a : src)
				if (a != bat) {
					h.assertValueEqual(LightingWork.wires(level, a), 0, "o painel em " + a + " não leva fio (está encostado à bateria)");
					h.assertTrue(a.distManhattan(bat) == 1, "o painel encostado à bateria: " + a);
				}
			// arrumar: os fios ficam guardados no mundo dos testes
			for (BlockPos a : p.anchors) for (BlockPos b : p.anchors) LightingWork.unlink(level, a, b);
		}
		for (BlockPos c : BlockPos.betweenClosed(h.absolutePos(new BlockPos(0, 1, 0)), h.absolutePos(new BlockPos(8, 5, 8))))
			level.setBlock(c, Blocks.AIR.defaultBlockState(), 2 | 16);
		h.succeed();
	}

	/** 0.2.0 (12/11, visto no jogo): o poste nunca à frente de uma porta, a fonte nunca no ar nem num telhado, e nenhum fio solto. */
	// 0.3.0: a fonte vai para campo aberto (≥ 2 blocos de construções, céu aberto): o teste precisa de chão livre à volta — mais
	// espaço (padding) para não tocar nos vizinhos
	@GameTest(padding = 24, skyAccess = true)
	public void streetLightsKeepDoorsClearSourcesOnTheGroundAndNoLooseWires(GameTestHelper h) {
		TestUtil.clean(h);
		TestUtil.floor(h, 12);
		for (int x = -8; x <= 16; x++)
			for (int z = -8; z <= 16; z++)
				if (x < 0 || x > 8 || z < 0 || z > 8) for (int y = 1; y <= 6; y++) h.setBlock(new BlockPos(x, y, z), Blocks.AIR);
		Village v = com.tcordeiro.vilas.village.VillageManager.found(h.getLevel(), h.absolutePos(new BlockPos(4, 1, 4)), 12);
		var level = h.getLevel();
		for (int x = 0; x <= 8; x++) h.setBlock(new BlockPos(x, 0, 4), Blocks.DIRT_PATH);
		// uma porta virada para a rua (a casa da frente) e um pilar ao lado da Mesa (o «telhado» onde a fonte não pode ir)
		h.setBlock(new BlockPos(6, 1, 6), Blocks.OAK_DOOR.defaultBlockState());
		h.setBlock(new BlockPos(6, 2, 6), Blocks.OAK_DOOR.defaultBlockState().setValue(net.minecraft.world.level.block.DoorBlock.HALF,
				net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER));
		for (int y = 1; y <= 3; y++) h.setBlock(new BlockPos(4, y, 2), Blocks.STONE_BRICKS);
		StreetLightKit kit = StreetLightKit.forVillage(level, v);
		Project p = LightingWork.create(level, v);
		h.assertTrue(p != null, "há sítios ao lado da rua");
		Builders.start(level, v, p);
		Builders.overnight(level, v);
		int poles = 0;
		for (BlockPos a : p.anchors) {
			BlockState s = level.getBlockState(a);
			if (s.isAir()) continue;
			if (kit.isPole(s)) {
				poles++;
				h.assertTrue(!LightingWork.nearDoor(level, a, LightingWork.DOOR_CLEARANCE), "poste a menos de 2 blocos da porta: " + a);
			} else {
				h.assertTrue(Math.abs(a.getY() - v.center.getY()) <= 1, "a fonte à altura da Mesa (não num telhado): " + a);
				h.assertTrue(level.getBlockState(a.below()).isFaceSturdy(level, a.below(), net.minecraft.core.Direction.UP), "a fonte apoiada: " + a);
			}
		}
		h.assertTrue(poles >= 1, "pelo menos um poste");
		if (kit.industrial()) {
			int withWire = 0;
			for (long al : v.extra.lightAnchors) {
				BlockPos a = BlockPos.of(al);
				BlockState s = level.getBlockState(a);
				if (s.isAir()) continue;
				if (!kit.isPole(s)) {
					h.assertTrue(Math.abs(a.getY() - v.center.getY()) <= 1 && level.getBlockState(a.below()).isFaceSturdy(level, a.below(), net.minecraft.core.Direction.UP),
							"a fonte no chão: " + a);
					if (LightingWork.wires(level, a) > 0) withWire++;
				}
				for (long b : kit.partners(level, a)) h.assertTrue(kit.wireClear(level, a, BlockPos.of(b)), "o fio em " + a.toShortString() + " está livre e com folga");
			}
			h.assertValueEqual(withWire, 1, "um só fio para a fonte (a bateria)");
		}
		h.assertTrue(level.getBlockState(h.absolutePos(new BlockPos(4, 4, 2))).isAir(), "nada em cima do pilar");
		// fios: só em blocos que estão mesmo postos
		for (BlockPos c : BlockPos.betweenClosed(h.absolutePos(new BlockPos(0, 1, 0)), h.absolutePos(new BlockPos(8, 6, 8))))
			if (LightingWork.wires(level, c) > 0) h.assertTrue(!level.getBlockState(c).isAir(), "fio solto em " + c.toShortString());
		for (BlockPos a : p.anchors) for (BlockPos b : p.anchors) LightingWork.unlink(level, a, b);
		for (BlockPos c : BlockPos.betweenClosed(h.absolutePos(new BlockPos(0, 1, 0)), h.absolutePos(new BlockPos(8, 6, 8))))
			level.setBlock(c, Blocks.AIR.defaultBlockState(), 2 | 16);
		h.succeed();
	}

	/** 12/11, visto no jogo: no 26.3 a erva não está na tag «dirt» — a muralha, o golem e os postes têm de assentar em erva. */
	@GameTest
	public void buildersWorkOnGrass(GameTestHelper h) {
		TestUtil.clean(h);
		for (int x = 0; x <= 8; x++)
			for (int z = 0; z <= 8; z++) h.setBlock(new BlockPos(x, 0, z), Blocks.GRASS_BLOCK);
		h.assertTrue(com.tcordeiro.vilas.build.Ground.natural(Blocks.GRASS_BLOCK.defaultBlockState()), "a erva é chão natural");
		Village v = com.tcordeiro.vilas.village.VillageManager.found(h.getLevel(), h.absolutePos(new BlockPos(4, 1, 4)), TestUtil.RADIUS);
		v.economy.treasury = 100_000;
		h.assertTrue(GolemWork.create(h.getLevel(), v) != null, "há sítio para o golem na erva");
		Project p = WallWork.create(v);
		Builders.start(h.getLevel(), v, p);
		for (int night = 0; night < 5 && !v.extra.projects.isEmpty(); night++) Builders.overnight(h.getLevel(), v);
		h.assertTrue(p.placed >= 30, "a muralha assentou na erva: " + p.placed + " blocos (saltadas " + p.skipped + ")");
		for (BlockPos c : BlockPos.betweenClosed(h.absolutePos(new BlockPos(0, 1, 0)), h.absolutePos(new BlockPos(8, 6, 8))))
			h.getLevel().setBlock(c, Blocks.AIR.defaultBlockState(), 2 | 16);
		h.succeed();
	}

	/** 12/11 (visto no jogo): o fio nunca atravessa uma casa — com uma parede entre os dois postes não há fio, e o poste que fica sem fio sai. */
	@GameTest
	public void wireNeverCrossesAHouse(GameTestHelper h) {
		TestUtil.clean(h);
		TestUtil.floor(h, 4);
		Village v = com.tcordeiro.vilas.village.VillageManager.found(h.getLevel(), h.absolutePos(new BlockPos(4, 1, 4)), 4);
		var level = h.getLevel();
		StreetLightKit kit = StreetLightKit.forVillage(level, v);
		if (!kit.industrial()) {
			h.succeed(); // sem o Industrial não há fios
			return;
		}
		BlockPos a = h.absolutePos(new BlockPos(1, 1, 4)), b = h.absolutePos(new BlockPos(7, 1, 4));
		// a «casa»: uma parede de tábuas entre os postes, mais alta do que o fio
		for (int z = 2; z <= 6; z++)
			for (int y = 1; y <= 6; y++) h.setBlock(new BlockPos(4, y, z), Blocks.OAK_PLANKS);
		Project p = new Project(Project.Kind.LIGHTING, v.center);
		for (BlockPos q : new BlockPos[] {a, b}) {
			p.pending.add(new Project.Placement(q, kit.pole().get(0), kit.height()));
			p.anchors.add(q);
		}
		p.total = 2;
		Builders.start(level, v, p);
		Builders.overnight(level, v);
		h.assertTrue(!kit.wireClear(level, a, b) || !kit.isPole(level.getBlockState(b)) || !kit.isPole(level.getBlockState(a)), "com a parede o fio não está livre");
		h.assertValueEqual(LightingWork.wires(level, a) + LightingWork.wires(level, b), 0, "nenhum fio atravessa a casa");
		h.assertTrue(level.getBlockState(a).isAir() || level.getBlockState(b).isAir(), "o poste sem fio livre saiu (não fica poste solto)");
		// sem a parede, entre dois postes postos, o fio está livre
		for (int z = 2; z <= 6; z++)
			for (int y = 1; y <= 6; y++) h.setBlock(new BlockPos(4, y, z), Blocks.AIR);
		for (BlockPos q : new BlockPos[] {a, b}) if (level.getBlockState(q).isAir()) level.setBlock(q, kit.pole().get(0), 3);
		h.assertTrue(kit.wireClear(level, a, b), "sem a parede o fio passa");
		// um fio já feito que uma construção nova tapa é desfeito na revisão
		h.assertTrue(kit.link(level, a, b), "liga");
		v.extra.lightAnchors.clear();
		v.extra.lightAnchors.add(a.asLong());
		v.extra.lightAnchors.add(b.asLong());
		for (int z = 2; z <= 6; z++)
			for (int y = 1; y <= 6; y++) h.setBlock(new BlockPos(4, y, z), Blocks.OAK_PLANKS);
		h.assertTrue(LightingWork.revalidate(level, v) >= 1, "a revisão desfez o fio tapado");
		h.assertValueEqual(LightingWork.wires(level, a), 0, "sem fio depois da revisão");
		// folga: alçapões abertos rentes ao fio (o corpo fica a ~0,3 bloco do fio, sem tocar) → recusado; uma parede a 1,5 bloco → aceite
		for (int z = 2; z <= 6; z++)
			for (int y = 1; y <= 6; y++) h.setBlock(new BlockPos(4, y, z), Blocks.AIR);
		h.assertTrue(kit.wireClear(level, a, b), "livre outra vez");
		BlockState trap = Blocks.OAK_TRAPDOOR.defaultBlockState().setValue(net.minecraft.world.level.block.TrapDoorBlock.OPEN, true)
				.setValue(net.minecraft.world.level.block.TrapDoorBlock.FACING, net.minecraft.core.Direction.NORTH); // corpo em z 13/16..1 do bloco
		for (int x = 3; x <= 5; x++)
			for (int y = 3; y <= 4; y++) h.setBlock(new BlockPos(x, y, 4), trap);
		h.assertTrue(!kit.wireClear(level, a, b), "um bloco a 0,3 do fio: recusado (o fio roçava)");
		for (int x = 3; x <= 5; x++)
			for (int y = 3; y <= 4; y++) h.setBlock(new BlockPos(x, y, 4), Blocks.AIR);
		for (int x = 3; x <= 5; x++)
			for (int y = 1; y <= 5; y++) h.setBlock(new BlockPos(x, y, 6), Blocks.OAK_PLANKS); // a face a 1,5 bloco do fio
		h.assertTrue(kit.wireClear(level, a, b), "uma parede a 1,5 bloco: aceite");
		for (BlockPos c : BlockPos.betweenClosed(h.absolutePos(new BlockPos(0, 1, 0)), h.absolutePos(new BlockPos(8, 7, 8))))
			level.setBlock(c, Blocks.AIR.defaultBlockState(), 2 | 16);
		h.succeed();
	}
}
