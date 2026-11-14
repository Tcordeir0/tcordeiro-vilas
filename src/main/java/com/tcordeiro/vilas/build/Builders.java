package com.tcordeiro.vilas.build;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.tcordeiro.vilas.logic.BuildRules;
import com.tcordeiro.vilas.logic.WallRules;
import com.tcordeiro.vilas.village.Project;
import com.tcordeiro.vilas.village.Village;
import com.tcordeiro.vilas.village.VillageData;
import com.tcordeiro.vilas.village.VillageNews;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.SpawnUtil;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Os construtores da vila: as obras pagas pela tesouraria (golem, muralha, iluminação) são feitas por um aldeão «Construtor»
 * que vai ao sítio e põe os blocos UM a UM, com partículas e som ({@link #tick}). Se ninguém estiver a ver, a obra espera pela
 * noite: o que falta aparece depois de dormir ({@link #overnight}, chamado uma vez por dia de jogo).
 * <p>
 * REGRA DE SEGURANÇA: cada bloco é verificado NO MOMENTO de o pôr ({@link Ground#mayBuild}) — só ar, erva, flores e neve, nunca
 * em terrenos. Um bloco que já não pode ir para ali é saltado (a obra contorna-o); nada é partido.
 */
public final class Builders {
	private Builders() {}

	/** Etiqueta (entity tag) do aldeão construtor (não conta como morador). */
	public static final String BUILDER_TAG = "tvilas.builder";

	/** Começa uma obra (fica na fila da vila). */
	public static Project start(ServerLevel level, Village v, Project p) {
		v.extra.projects.add(p);
		VillageData.get(level).changed();
		VillageNews.announce(level, v, VillageNews.BUILD, "tvilas.news.building", Component.translatable(p.kind.key()));
		return p;
	}

	/** Planeia mais blocos se a obra ainda os tiver por planear (a muralha, coluna a coluna). Devolve false se já não há mais. */
	static boolean refill(ServerLevel level, Village v, Project p) {
		if (!p.pending.isEmpty()) return true;
		if (p.kind == Project.Kind.WALL) return WallWork.expand(level, v, p);
		return false;
	}

	static boolean finished(Project p) {
		return p.pending.isEmpty() && (p.kind != Project.Kind.WALL || p.nextColumn >= p.total);
	}

	/** Um passo à vista (chamado pela Mesa a cada tick; público para os testes): o construtor anda e põe no máximo 1 bloco. */
	public static void tick(ServerLevel level, Village v, long now) {
		Village.Extra x = v.extra;
		if (x.projects.isEmpty()) {
			dismiss(level, v);
			return;
		}
		Project p = x.projects.get(0);
		if (!refill(level, v, p)) {
			if (finished(p)) complete(level, v, p);
			return;
		}
		Project.Placement next = p.pending.get(0);
		if (!level.isLoaded(next.pos())) return;
		if (!Ground.watched(level, v, next.pos(), BuildRules.WATCH_RADIUS)) return; // ninguém a ver: fica para a noite
		if (p.kind == Project.Kind.WALL && v.economy.treasury < WallRules.BLOCK_CENTS) return; // sem dinheiro: parada
		Villager b = builder(level, v, next.pos());
		if (b != null) {
			double d2 = b.distanceToSqr(next.pos().getX() + 0.5, next.pos().getY(), next.pos().getZ() + 0.5);
			if (d2 > BuildRules.REACH * BuildRules.REACH) {
				b.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(next.pos(), 0.6f, 2));
				if (x.walkingSince < 0) x.walkingSince = now;
				if (!BuildRules.stuck(x.walkingSince, now)) return;
				// preso atrás de alguma coisa: vai para o lado da obra
				BlockPos to = Ground.surface(level, next.pos().getX() + 1, next.pos().getZ() + 1, next.pos().getY());
				b.teleportTo(to.getX() + 0.5, to.getY(), to.getZ() + 0.5);
			}
		}
		if (BuildRules.blocksNow(true, x.lastStep, now) < 1) return;
		x.lastStep = now;
		x.walkingSince = -1;
		p.pending.remove(0);
		place(level, v, p, next, true);
		if (b != null) b.getLookControl().setLookAt(next.pos().getX() + 0.5, next.pos().getY() + 0.5, next.pos().getZ() + 0.5);
		if (p.pending.isEmpty() && !refill(level, v, p) && finished(p)) complete(level, v, p);
		VillageData.get(level).changed();
	}

	/**
	 * A noite passou: o que falta das obras aparece de uma vez (no máximo {@link BuildRules#OVERNIGHT_MAX} blocos; os blocos em
	 * chunks por carregar ficam para depois). Devolve quantos blocos pôs.
	 */
	public static int overnight(ServerLevel level, Village v) {
		int budget = BuildRules.OVERNIGHT_MAX, put = 0;
		for (Iterator<Project> it = new ArrayList<>(v.extra.projects).iterator(); it.hasNext() && budget > 0;) {
			Project p = it.next();
			List<Project.Placement> later = new ArrayList<>();
			while (budget > 0 && refill(level, v, p)) {
				Project.Placement next = p.pending.remove(0);
				if (!level.isLoaded(next.pos())) {
					later.add(next);
					if (p.pending.isEmpty()) break;
					continue;
				}
				if (p.kind == Project.Kind.WALL && v.economy.treasury < WallRules.BLOCK_CENTS) {
					later.add(next);
					break;
				}
				if (place(level, v, p, next, false)) put++;
				budget--;
			}
			p.pending.addAll(0, later);
			if (finished(p)) complete(level, v, p);
		}
		if (put > 0) VillageNews.announce(level, v, VillageNews.NIGHT, "tvilas.news.overnight", put);
		VillageData.get(level).changed();
		return put;
	}

	/** Põe um bloco da obra (se ainda puder ir para ali). Devolve true se o pôs. */
	static boolean place(ServerLevel level, Village v, Project p, Project.Placement at, boolean show) {
		BlockPos pos = at.pos();
		boolean free = Ground.mayBuild(level, pos);
		for (int i = 1; free && i < at.clear(); i++) free = Ground.mayBuild(level, pos.above(i));
		if (!free) {
			p.skipped++;
			return false;
		}
		BlockState state = at.state();
		// 0.3.0: postes do Industrial 0.12.2+ com a altura escolhida (API de colunas); sem ela, o bloco de baixo como antes
		if (p.kind == Project.Kind.LIGHTING && at.clear() > 1 && StreetLightKit.placeColumn(level, pos, state, at.clear())) {
			p.placed++;
			if (show) level.playSound(null, pos, state.getSoundType().getPlaceSound(), SoundSource.BLOCKS, 1.0f, 0.9f);
			return true;
		}
		// 0.3.0: os edifícios põem portas, camas e escadas metade a metade — sem atualizar a forma dos vizinhos até estar tudo
		// 0.6 crises: a reconstrução também põe portas e camas metade a metade (sem desfazer a metade já posta)
		level.setBlock(pos, state, p.kind.structure() || p.kind == Project.Kind.REBUILD
				? net.minecraft.world.level.block.Block.UPDATE_CLIENTS | net.minecraft.world.level.block.Block.UPDATE_KNOWN_SHAPE : 3);
		p.placed++;
		if (p.kind == Project.Kind.WALL) v.economy.treasury -= WallRules.BLOCK_CENTS;
		if (show) {
			level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 12,
					0.3, 0.3, 0.3, 0.05);
			level.sendParticles(ParticleTypes.HAPPY_VILLAGER, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 3, 0.3, 0.2, 0.3, 0.0);
			level.playSound(null, pos, state.getSoundType().getPlaceSound(), SoundSource.BLOCKS, 1.0f, 0.9f);
		}
		return true;
	}

	static void complete(ServerLevel level, Village v, Project p) {
		v.extra.projects.remove(p);
		switch (p.kind) {
			case GOLEM -> GolemWork.finish(level, v, p);
			case WALL -> {
				v.extra.wall = Village.Extra.WALL_DONE;
				v.extra.structures++;
			}
			case LIGHTING -> v.extra.poles = LightingWork.finish(level, v, p);
			case TOWN_HALL, MARKET, TAVERN, PLAZA -> CivicWork.finish(level, v, p);
			case HOUSE -> com.tcordeiro.vilas.village.Growth.step(level, v); // a casa tem cama: chega o morador
			case COMMUNITY -> com.tcordeiro.vilas.life.projects.Projects.finish(level, v, p); // 0.6.0: a placa com os doadores
			case REBUILD -> com.tcordeiro.vilas.life.crisis.Rebuild.finish(level, v, p); // 0.6 crises
		}
		VillageNews.announce(level, v, VillageNews.DONE, "tvilas.news.done", Component.translatable(p.kind.key()));
		if (v.extra.projects.isEmpty()) dismiss(level, v);
		VillageData.get(level).changed();
	}

	/** O construtor desta vila (chega um, perto da obra, se ainda não houver). */
	static Villager builder(ServerLevel level, Village v, BlockPos near) {
		if (v.extra.builder != null && level.getEntity(v.extra.builder) instanceof Villager b && b.isAlive()) return b;
		BlockPos from = v.center.distSqr(near) < 48 * 48 ? v.center : near;
		Villager b = SpawnUtil.trySpawnMob(EntityTypes.VILLAGER, EntitySpawnReason.MOB_SUMMONED, level, from, 10, 3, 4,
				SpawnUtil.Strategy.ON_TOP_OF_COLLIDER, false).orElse(null);
		if (b == null) return null;
		b.setVillagerData(b.getVillagerData().withProfession(level.registryAccess(), VillagerProfession.NITWIT));
		b.addTag(BUILDER_TAG);
		b.setCustomName(Component.translatable("entity.tvilas.builder"));
		b.setCustomNameVisible(true);
		b.setPersistenceRequired();
		v.extra.builder = b.getUUID();
		v.extra.walkingSince = -1;
		return b;
	}

	/** Sem obras: o construtor vai-se embora. */
	static void dismiss(ServerLevel level, Village v) {
		if (v.extra.builder == null) return;
		Entity e = level.getEntity(v.extra.builder);
		if (e != null) e.discard();
		v.extra.builder = null;
	}

	/** Alguém está a ver a obra atual? (para decidir se a acabada de começar aparece já de manhã) */
	public static boolean watchedNow(ServerLevel level, Village v) {
		if (v.extra.projects.isEmpty()) return false;
		Project p = v.extra.projects.get(0);
		return Ground.watched(level, v, p.pending.isEmpty() ? p.site : p.pending.get(0).pos(), BuildRules.WATCH_RADIUS);
	}

	public static boolean isBuilder(Entity e) {
		return e.entityTags().contains(BUILDER_TAG);
	}
}
