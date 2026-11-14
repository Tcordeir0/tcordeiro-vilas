package com.tcordeiro.vilas.life.crisis;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.tcordeiro.vilas.VilasConfig;
import com.tcordeiro.vilas.village.Project;
import com.tcordeiro.vilas.village.Village;
import com.tcordeiro.vilas.village.VillageData;
import com.tcordeiro.vilas.village.VillageManager;
import com.tcordeiro.vilas.village.VillageNews;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.TntBlock;
import net.minecraft.world.level.block.entity.BellBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/**
 * 0.6 crises — Reconstrução depois de um ataque. Regista SÓ os blocos partidos por monstros (explosão de um creeper/ghast/wither,
 * zombie a partir uma porta) ou queimados pelo fogo, perto da vila ({@link CrisisRules#REBUILD_RADIUS}) e FORA de terrenos de
 * jogador; quando acaba o perigo, o Construtor repõe-nos aos poucos (obra {@link Project.Kind#REBUILD}, as mesmas regras de obra:
 * só onde há ar, erva, flores ou neve e nunca num terreno — {@link com.tcordeiro.vilas.build.Ground#mayBuild}).
 * <p>
 * Nunca guarda uma «fotografia» da vila: só o que se partiu, com teto. Os blocos que a explosão partiu e que ficam registados NÃO
 * largam item (o material fica para a obra; senão repor era duplicar).
 */
public final class Rebuild {
	private Rebuild() {}

	/** Contadores para os testes. */
	public static int recordedCount, started;

	public static boolean enabled() {
		return VilasConfig.get().rebuild;
	}

	static int cap() {
		return Math.max(0, Math.min(4096, VilasConfig.get().rebuild_max_blocks));
	}

	/** O raio da reconstrução nesta vila (nunca maior do que o território). */
	public static int radius(Village v) {
		return Math.max(8, Math.min(v.radius, VilasConfig.get().rebuild_radius));
	}

	/** A vila em cuja área de reconstrução está esta posição (null = nenhuma). */
	public static Village areaOf(ServerLevel level, BlockPos pos) {
		for (Village v : VillageData.get(level).villages) {
			long r = radius(v);
			if (v.distSqr(pos.getX(), pos.getZ()) <= r * r && v.covers(pos.getX(), pos.getZ())) return v;
		}
		return null;
	}

	/** Um bloco partido por um monstro/explosão/fogo: regista-o (e a outra metade de uma porta ou cama). Devolve true se ficou registado. */
	/** Revisão: esta posição já está registada para a reconstrução (na vila que a cobre)? */
	public static boolean recorded(ServerLevel level, BlockPos pos) {
		Village v = areaOf(level, pos);
		if (v == null) return false;
		long l = pos.asLong();
		for (CrisisData.Broken b : Crises.data(v).broken) if (b.pos() == l) return true;
		return false;
	}

	public static boolean record(ServerLevel level, BlockPos pos, BlockState state) {
		if (!enabled() || VillageData.get(level).villages.isEmpty()) return false;
		Village v = areaOf(level, pos);
		if (v == null) return false;
		if (!recordOne(level, v, pos, state, level.getBlockEntity(pos))) return false;
		BlockPos other = partner(pos, state);
		if (other != null && level.isLoaded(other)) {
			BlockState os = level.getBlockState(other);
			if (os.is(state.getBlock())) recordOne(level, v, other, os, level.getBlockEntity(other));
		}
		return true;
	}

	/** REGRA: a decisão está em {@link CrisisRules#mayRecord}; aqui só se lê o mundo. */
	static boolean recordOne(ServerLevel level, Village v, BlockPos pos, BlockState state, BlockEntity be) {
		CrisisData d = Crises.data(v);
		boolean inLot = VillageManager.lotAt(level, pos.getX(), pos.getZ()) != null;
		boolean emptyOrFire = state.isAir() || state.is(BlockTags.FIRE);
		boolean contents = be != null && !(be instanceof BellBlockEntity);
		boolean liquidOrTnt = !state.getFluidState().isEmpty() || state.getBlock() instanceof TntBlock;
		long rad = radius(v);
		boolean inside = v.distSqr(pos.getX(), pos.getZ()) <= rad * rad && v.covers(pos.getX(), pos.getZ());
		if (!CrisisRules.mayRecord(inside, inLot, emptyOrFire, contents, liquidOrTnt, d.recorded(pos.asLong()), d.broken.size(), cap())) return false;
		d.add(new CrisisData.Broken(pos.asLong(), state));
		d.lastBroken = level.getGameTime();
		recordedCount++;
		VillageData.get(level).changed();
		return true;
	}

	/** A outra metade de uma porta (em cima/em baixo) ou de uma cama (cabeceira/pés). */
	static BlockPos partner(BlockPos pos, BlockState s) {
		if (s.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF))
			return s.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.LOWER ? pos.above() : pos.below();
		if (s.getBlock() instanceof BedBlock && s.hasProperty(BedBlock.PART)) {
			Direction f = s.getValue(BedBlock.FACING);
			return s.getValue(BedBlock.PART) == BedPart.FOOT ? pos.relative(f) : pos.relative(f.getOpposite());
		}
		return null;
	}

	/** Quem causou a explosão conta? (monstros sim; jogadores e explosões sem causador não) */
	public static boolean causeCounts(Entity cause) {
		return CrisisRules.causeCounts(cause instanceof net.minecraft.world.entity.Mob, cause instanceof Player);
	}

	/**
	 * A cada segundo (pela Mesa): passado o perigo e um minuto sem estragos, o Construtor começa a repor o que se partiu — de baixo
	 * para cima (primeiro o chão e as paredes, depois o que se apoia nelas).
	 */
	public static void tick(ServerLevel level, Village v, CrisisData d, boolean danger, long now) {
		if (d.broken.isEmpty() || !CrisisRules.mayStartRebuild(d.broken.size(), danger, d.lastBroken, now)) return;
		Project p = null;
		for (Project q : v.extra.projects) if (q.kind == Project.Kind.REBUILD) p = q;
		boolean fresh = p == null;
		if (fresh) p = new Project(Project.Kind.REBUILD, BlockPos.of(d.broken.get(0).pos()));
		List<CrisisData.Broken> list = new ArrayList<>(d.broken);
		list.sort(Comparator.comparingInt((CrisisData.Broken b) -> BlockPos.getY(b.pos())));
		for (CrisisData.Broken b : list) p.pending.add(new Project.Placement(BlockPos.of(b.pos()), b.state()));
		p.total += list.size();
		d.clearBroken();
		if (fresh) {
			v.extra.projects.add(0, p); // é urgente: passa à frente das outras obras (que continuam depois)
			started++;
			VillageNews.announce(level, v, VillageNews.BUILD, "tvilas.news.building", Component.translatable(p.kind.key()));
		}
		VillageData.get(level).changed();
	}

	/** A obra acabou (chamado pelo {@link com.tcordeiro.vilas.build.Builders}). */
	public static void finish(ServerLevel level, Village v, Project p) {
		Crises.fact(level, v, "rebuilt", Integer.toString(p.placed));
		CrisisHooks.onCrisis(level, v, "rebuilt");
	}
}
