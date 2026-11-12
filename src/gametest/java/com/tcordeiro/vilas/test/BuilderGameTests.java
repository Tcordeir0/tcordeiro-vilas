package com.tcordeiro.vilas.test;

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

	@GameTest
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
		long sources = p.anchors.stream().filter(a -> !kit.isPole(level.getBlockState(a)) && !level.getBlockState(a).isAir()).count();
		h.assertValueEqual(sources, (long) kit.sources().size(), "fonte da Prefeitura (painéis e bateria; nada com as lanternas)");
		if (kit.industrial()) {
			h.assertValueEqual(sources, 3L, "2 painéis e uma bateria na Prefeitura");
			long poleCount = p.anchors.stream().filter(a -> kit.isPole(level.getBlockState(a))).count();
			h.assertValueEqual((long) p.linked, sources + poleCount - 1, "fios: cada poste ao anterior e a fonte ao primeiro");
			for (BlockPos a : p.anchors)
				if (!kit.isPole(level.getBlockState(a)) && !level.getBlockState(a).isAir())
					h.assertTrue(LightingWork.wires(level, a) >= 1, "a fonte da Prefeitura em " + a + " tem fio para o primeiro poste");
			// arrumar: os fios ficam guardados no mundo dos testes
			for (BlockPos a : p.anchors) for (BlockPos b : p.anchors) LightingWork.unlink(level, a, b);
		}
		for (BlockPos c : BlockPos.betweenClosed(h.absolutePos(new BlockPos(0, 1, 0)), h.absolutePos(new BlockPos(8, 5, 8))))
			level.setBlock(c, Blocks.AIR.defaultBlockState(), 2 | 16);
		h.succeed();
	}
}
