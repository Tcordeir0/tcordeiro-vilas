package com.tcordeiro.vilas.test;

import com.tcordeiro.vilas.build.Builders;
import com.tcordeiro.vilas.build.CivicWork;
import com.tcordeiro.vilas.logic.CivicRules;
import com.tcordeiro.vilas.registry.VilasBlocks;
import com.tcordeiro.vilas.village.Lot;
import com.tcordeiro.vilas.village.Project;
import com.tcordeiro.vilas.village.Village;
import com.tcordeiro.vilas.village.VillageData;
import com.tcordeiro.vilas.village.VillageManager;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.block.Blocks;

/**
 * 0.3.0 — Numa vila antiga (sem Prefeitura, Mercado, Taverna, Praça), o Prefeito manda construir o que falta: um de cada vez,
 * pago pela tesouraria, num sítio livre (nunca sobre construções nem terrenos), com os construtores ou de noite. Estes testes
 * ocupam muito espaço: correm longe dos outros (30 000 blocos), tudo no mesmo tick.
 */
public class CivicBuildGameTests {
	static final int R = 40, HALF = 36;

	/** Um chão de relva largo (o mundo de teste à volta pode não ter chão natural) e ar por cima. */
	static void meadow(ServerLevel level, BlockPos c) {
		for (int x = -HALF; x <= HALF; x++)
			for (int z = -HALF; z <= HALF; z++) {
				level.setBlock(c.offset(x, -2, z), Blocks.DIRT.defaultBlockState(), 2);
				level.setBlock(c.offset(x, -1, z), Blocks.GRASS_BLOCK.defaultBlockState(), 2);
				for (int y = 0; y <= 14; y++) if (x != 0 || z != 0 || y != 0) level.setBlock(c.offset(x, y, z), Blocks.AIR.defaultBlockState(), 2);
			}
	}

	static void clear(ServerLevel level, BlockPos c) {
		for (int x = -HALF; x <= HALF; x++)
			for (int z = -HALF; z <= HALF; z++)
				for (int y = 0; y <= 14; y++) level.setBlock(c.offset(x, y, z), Blocks.AIR.defaultBlockState(), 2);
	}

	static Village oldVillage(GameTestHelper h, BlockPos c) {
		ServerLevel level = h.getLevel();
		VillageData d = VillageData.get(level);
		d.villages.removeIf(x -> x.center.distManhattan(c) < 200);
		d.lots.removeIf(l -> l.area.overlaps(new com.tcordeiro.vilas.logic.LotRules.Area(c.getX() - 60, c.getZ() - 60, c.getX() + 60, c.getZ() + 60)));
		meadow(level, c);
		level.setBlock(c, VilasBlocks.MAYOR_DESK.defaultBlockState(), 3);
		Village v = VillageManager.found(level, c, R);
		v.economy.treasury = 1_000_000;
		return v;
	}

	@GameTest
	public void oldVillageGetsWhatIsMissingOneAtATime(GameTestHelper h) {
		ServerLevel level = h.getLevel();
		// longe dos outros testes (é tudo feito neste tick: os chunks ficam carregados enquanto se trabalha)
		BlockPos c = h.absolutePos(new BlockPos(4, 1, 4)).offset(30000, 0, 0);
		Village v = oldVillage(h, c);
		CivicWork.refresh(level, v);
		h.assertValueEqual(v.civic.missing().size(), 4, "a vila não tem nenhum dos 4 edifícios");
		h.assertTrue(CivicWork.commissionNext(level, v) == null, "sem moradores não é uma vila a sério: nada no meio do mato");
		java.util.List<Villager> residents = new java.util.ArrayList<>();
		for (int i = 0; i < CivicRules.MIN_POPULATION; i++) {
			Villager vi = EntityTypes.VILLAGER.create(level, net.minecraft.world.entity.EntitySpawnReason.MOB_SUMMONED);
			vi.setNoAi(true);
			vi.snapTo(c.getX() - 2 + i + 0.5, c.getY(), c.getZ() - 2 + 0.5, 0, 0);
			level.addFreshEntity(vi);
			residents.add(vi);
		}
		// um terreno de jogador em toda a metade oeste (onde a procura de sítio começa): a obra nunca o ocupa
		ServerPlayerHolder owner = new ServerPlayerHolder(TestUtil.playerAt(h, new BlockPos(4, 1, 4)));
		Lot lot = new Lot(java.util.UUID.randomUUID(), v.id, new com.tcordeiro.vilas.logic.LotRules.Area(c.getX() - HALF, c.getZ() - HALF, c.getX() - 9, c.getZ() + HALF),
				owner.p.getUUID(), "dono", new java.util.HashSet<>());
		VillageData.get(level).lots.add(lot);
		long before = v.economy.treasury;
		Project p = CivicWork.commissionNext(level, v);
		h.assertTrue(p != null && p.kind == Project.Kind.TOWN_HALL, "primeiro a Prefeitura");
		h.assertValueEqual(v.economy.treasury, before - CivicRules.TOWN_HALL_CENTS, "paga pela tesouraria");
		h.assertTrue(CivicWork.commissionNext(level, v) == null, "um de cada vez");
		for (var pl : p.pending) h.assertTrue(!lot.contains(pl.pos().getX(), pl.pos().getZ()), "nunca dentro do terreno");
		for (var pl : p.pending) h.assertTrue(!pl.state().is(VilasBlocks.MAYOR_DESK), "a Mesa da estrutura não entra (a vila já tem uma)");
		h.assertTrue(VillageManager.civic(level, p.pending.get(0).pos()), "o sítio já é área cívica (não se vende, ninguém parte)");
		// ninguém a ver: de noite aparece
		int put = Builders.overnight(level, v);
		h.assertTrue(put > 50, "a Prefeitura apareceu de noite (" + put + " blocos)");
		h.assertTrue(v.civic.built.contains("town_hall"), "acabada, conta como feita");
		Project next = CivicWork.commissionNext(level, v);
		h.assertTrue(next != null && next.kind == Project.Kind.MARKET, "depois o Mercado");
		// à vista: um construtor põe bloco a bloco (o tempo do jogo é passado à mão: andar, saltar se preso, 1 bloco por segundo)
		var watcher = TestUtil.playerAt(h, new BlockPos(4, 1, 4));
		BlockPos first = next.pending.get(0).pos();
		watcher.teleportTo(first.getX() + 0.5, first.getY() + 2, first.getZ() + 0.5);
		long t = level.getGameTime() + 10_000_000L;
		int start = next.placed;
		Builders.tick(level, v, t); // chega o Construtor e põe-se a caminho
		h.assertTrue(v.extra.builder != null && level.getEntity(v.extra.builder) instanceof Villager, "com um Construtor");
		Builders.tick(level, v, t + com.tcordeiro.vilas.logic.BuildRules.STUCK_TICKS); // longe/preso: vai para o lado da obra e põe
		Builders.tick(level, v, t + com.tcordeiro.vilas.logic.BuildRules.STUCK_TICKS + 5); // ainda não passou 1 s: espera
		int one = next.placed;
		Builders.tick(level, v, t + com.tcordeiro.vilas.logic.BuildRules.STUCK_TICKS + com.tcordeiro.vilas.logic.BuildRules.STEP_TICKS);
		h.assertValueEqual(one, start + 1, "à vista: um bloco");
		h.assertValueEqual(next.placed, start + 2, "um segundo depois, outro (bloco a bloco)");
		// arrumar
		v.extra.projects.clear();
		Builders.tick(level, v, t + 1_000_000); // sem obras: o Construtor vai-se embora
		for (Villager r : residents) r.discard();
		watcher.discard();
		VillageData.get(level).lots.remove(lot);
		VillageData.get(level).villages.remove(v);
		clear(level, c);
		h.succeed();
	}

	record ServerPlayerHolder(net.minecraft.server.level.ServerPlayer p) {}

	/** Sem sítio livre (tudo ocupado por construções de jogadores), não paga nem constrói nada. */
	@GameTest
	public void noFreeSpotMeansNoCharge(GameTestHelper h) {
		ServerLevel level = h.getLevel();
		BlockPos c = h.absolutePos(new BlockPos(4, 1, 4)).offset(-30000, 0, 0); // longe dos outros testes
		Village v = oldVillage(h, c);
		// tudo à volta é chão de jogador (tábuas): não é chão natural
		for (int x = -HALF; x <= HALF; x++)
			for (int z = -HALF; z <= HALF; z++) level.setBlock(c.offset(x, -1, z), Blocks.OAK_PLANKS.defaultBlockState(), 2);
		long before = v.economy.treasury;
		h.assertTrue(CivicWork.commission(level, v, "plaza") == null, "sem sítio");
		h.assertValueEqual(v.economy.treasury, before, "não cobrou");
		h.assertTrue(v.extra.projects.isEmpty(), "nenhuma obra");
		VillageData.get(level).villages.remove(v);
		clear(level, c);
		h.succeed();
	}

	/** Sem cama livre, o prestígio das missões faz o Prefeito encomendar uma casa simples (paga pela tesouraria). */
	@GameTest
	public void noFreeBedOrdersAHouse(GameTestHelper h) {
		ServerLevel level = h.getLevel();
		BlockPos c = h.absolutePos(new BlockPos(4, 1, 4)).offset(0, 0, 30000); // longe dos outros testes
		Village v = oldVillage(h, c);
		v.civic.prestige = com.tcordeiro.vilas.VilasConfig.get().prestige_per_resident;
		long before = v.economy.treasury;
		h.assertValueEqual(com.tcordeiro.vilas.village.Growth.step(level, v), com.tcordeiro.vilas.logic.GrowthRules.Step.ORDER_HOUSE, "sem cama: casa");
		h.assertTrue(v.extra.projects.stream().anyMatch(p -> p.kind == Project.Kind.HOUSE), "a casa está em obras");
		h.assertValueEqual(v.economy.treasury, before - com.tcordeiro.vilas.logic.GrowthRules.HOUSE_CENTS, "paga pela tesouraria");
		h.assertValueEqual(com.tcordeiro.vilas.village.Growth.step(level, v), com.tcordeiro.vilas.logic.GrowthRules.Step.WAIT, "uma casa de cada vez");
		Builders.overnight(level, v); // feita de noite: tem cama, chega o morador
		h.assertTrue(com.tcordeiro.vilas.village.Growth.freeBeds(level, v) > 0 || v.civic.prestige == 0, "a casa tem cama (ou o morador já a ocupou)");
		VillageData.get(level).villages.remove(v);
		level.getEntitiesOfClass(Villager.class, new net.minecraft.world.phys.AABB(c).inflate(HALF)).forEach(net.minecraft.world.entity.Entity::discard);
		clear(level, c);
		h.succeed();
	}

	/** 0.3.0: a fonte da iluminação fica em campo aberto — ≥ 2 blocos de qualquer construção (um poço com telhado ao lado da Mesa) e céu aberto. */
	@GameTest
	public void lightingSourceAvoidsTheWell(GameTestHelper h) {
		ServerLevel level = h.getLevel();
		BlockPos c = h.absolutePos(new BlockPos(4, 1, 4)).offset(-30000, 0, 30000); // longe dos outros testes
		Village v = oldVillage(h, c);
		// um poço 3×3 de pedregulho com água e um telhado de lajes, 3 blocos a leste da Mesa
		BlockPos w = c.offset(3, 0, -1);
		for (int x = 0; x < 3; x++)
			for (int z = 0; z < 3; z++) {
				level.setBlock(w.offset(x, 0, z), (x == 1 && z == 1 ? Blocks.WATER : Blocks.COBBLESTONE).defaultBlockState(), 2);
				level.setBlock(w.offset(x, 3, z), Blocks.OAK_SLAB.defaultBlockState(), 2);
			}
		for (int y = 1; y <= 2; y++) {
			level.setBlock(w.offset(0, y, 0), Blocks.OAK_FENCE.defaultBlockState(), 2);
			level.setBlock(w.offset(2, y, 2), Blocks.OAK_FENCE.defaultBlockState(), 2);
		}
		var groups = com.tcordeiro.vilas.build.LightingWork.sourceGroups(level, v);
		h.assertTrue(!groups.isEmpty(), "há sítio para a fonte");
		BlockPos[] g = groups.get(0);
		for (BlockPos part : g) {
			h.assertTrue(com.tcordeiro.vilas.build.LightingWork.openSky(level, part), "céu aberto por cima de " + part.toShortString());
			for (BlockPos q : BlockPos.betweenClosed(part.offset(-2, 0, -2), part.offset(2, 4, 2)))
				h.assertTrue(!(q.getX() >= w.getX() && q.getX() <= w.getX() + 2 && q.getZ() >= w.getZ() && q.getZ() <= w.getZ() + 2 && q.getY() >= w.getY()
						&& q.getY() <= w.getY() + 3), "a fonte fica a ≥ 2 blocos do poço (" + part.toShortString() + ")");
		}
		h.assertTrue(Math.max(Math.abs(g[2].getX() - c.getX()), Math.abs(g[2].getZ() - c.getZ())) <= com.tcordeiro.vilas.build.LightingWork.SOURCE_RANGE,
				"com sítio perto, fica a ≤ 8 blocos da Mesa");
		// um telhado alto (7 acima) por cima desse sítio: já não vale (os painéis precisam de céu aberto)
		for (int x = -6; x <= 6; x++)
			for (int z = -6; z <= 6; z++) level.setBlock(g[2].offset(x, 7, z), Blocks.OAK_PLANKS.defaultBlockState(), 2);
		var roofed = com.tcordeiro.vilas.build.LightingWork.sourceGroups(level, v);
		h.assertTrue(!roofed.isEmpty(), "há outro sítio");
		for (BlockPos part : roofed.get(0)) h.assertTrue(com.tcordeiro.vilas.build.LightingWork.openSky(level, part), "debaixo de um telhado não: " + part.toShortString());
		for (int x = -6; x <= 6; x++)
			for (int z = -6; z <= 6; z++) level.setBlock(g[2].offset(x, 7, z), Blocks.AIR.defaultBlockState(), 2);
		// o centro todo ocupado (pilares de 3 em 3 até 8 blocos): a procura alarga até 16
		for (int x = -9; x <= 9; x += 3)
			for (int z = -9; z <= 9; z += 3)
				if (x != 0 || z != 0) level.setBlock(c.offset(x, 0, z), Blocks.COBBLESTONE.defaultBlockState(), 2);
		var far = com.tcordeiro.vilas.build.LightingWork.sourceGroups(level, v);
		h.assertTrue(!far.isEmpty(), "alarga a procura até 16 blocos");
		int d = Math.max(Math.abs(far.get(0)[2].getX() - c.getX()), Math.abs(far.get(0)[2].getZ() - c.getZ()));
		h.assertTrue(d > com.tcordeiro.vilas.build.LightingWork.SOURCE_RANGE && d <= com.tcordeiro.vilas.build.LightingWork.SOURCE_FAR,
				"fonte entre 9 e 16 blocos da Mesa (" + d + ")");
		VillageData.get(level).villages.remove(v);
		clear(level, c);
		h.succeed();
	}
}
