package com.tcordeiro.vilas.build;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.tcordeiro.vilas.logic.LightingRules;
import com.tcordeiro.vilas.village.Project;
import com.tcordeiro.vilas.village.Village;
import com.tcordeiro.vilas.village.VillageManager;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Iluminação pública: postes ao lado das ruas da vila (blocos de caminho), afastados {@link LightingRules#SPACING} blocos uns dos
 * outros e cada um a no máximo {@link StreetLightKit#POLE_SPAN} de outro (o vão do fio aéreo). Os blocos vêm do
 * {@link StreetLightKit}: postes do Industrial (que acendem à noite, ligados por fio a 2 painéis e uma bateria na Prefeitura) ou,
 * sem ele, lanternas vanilla num poste de cerca.
 */
public final class LightingWork {
	private LightingWork() {}

	/** Sítios para postes: chão natural ao lado de uma rua, livre por cima (a altura do poste), fora de terrenos. */
	static List<LightingRules.Spot> candidates(ServerLevel level, Village v, int height) {
		int r = Math.min(LightingRules.SEARCH_RADIUS, v.radius);
		int cx = v.center.getX(), cz = v.center.getZ();
		List<LightingRules.Spot> out = new ArrayList<>();
		for (int x = cx - r; x <= cx + r; x++)
			for (int z = cz - r; z <= cz + r; z++) {
				if (!v.covers(x, z) || !level.isLoaded(new BlockPos(x, v.center.getY(), z))) continue;
				if (Math.abs(x - cx) <= 2 && Math.abs(z - cz) <= 2) continue; // não em cima da Mesa
				if (VillageManager.lotAt(level, x, z) != null) continue;
				BlockPos top = Ground.surface(level, x, z, v.center.getY());
				if (!Ground.natural(level.getBlockState(top.below()))) continue;
				boolean byStreet = false;
				for (Direction d : Direction.Plane.HORIZONTAL) {
					BlockPos n = Ground.surface(level, x + d.getStepX(), z + d.getStepZ(), v.center.getY());
					if (Ground.street(level.getBlockState(n.below())) && Math.abs(n.getY() - top.getY()) <= 1) byStreet = true;
				}
				if (!byStreet) continue;
				boolean free = true;
				for (int y = 0; y < height; y++) free &= Ground.mayBuild(level, top.above(y));
				if (free) out.add(new LightingRules.Spot(x, z));
			}
		out.sort(Comparator.comparingLong(s -> (long) (s.x() - cx) * (s.x() - cx) + (long) (s.z() - cz) * (s.z() - cz)));
		return out;
	}

	/** A obra (ou null se a vila não tem ruas onde pôr postes). */
	public static Project create(ServerLevel level, Village v) {
		StreetLightKit kit = StreetLightKit.forVillage(level, v);
		List<LightingRules.Spot> spots = LightingRules.choose(candidates(level, v, kit.height()), LightingRules.SPACING, StreetLightKit.POLE_SPAN,
				LightingRules.MAX_POLES);
		if (spots.isEmpty()) return null;
		Project p = new Project(Project.Kind.LIGHTING, v.center);
		for (LightingRules.Spot s : spots) {
			BlockPos base = Ground.surface(level, s.x(), s.z(), v.center.getY());
			List<BlockState> states = kit.pole();
			// com um só bloco (Industrial), o resto do poste aparece por cima: exige a altura toda livre
			int clear = states.size() == 1 ? kit.height() : 1;
			for (int i = 0; i < states.size(); i++) p.pending.add(new Project.Placement(base.above(i), states.get(i), clear));
			p.anchors.add(base);
		}
		// a fonte da Prefeitura (Industrial): ao lado da Mesa, em sítios livres
		List<BlockState> sources = kit.sources();
		int k = 0;
		for (int dist = 2; dist <= 4 && k < sources.size(); dist++)
			for (Direction d : Direction.Plane.HORIZONTAL) {
				if (k >= sources.size()) break;
				BlockPos at = Ground.surface(level, v.center.getX() + d.getStepX() * dist, v.center.getZ() + d.getStepZ() * dist, v.center.getY());
				if (!Ground.mayBuild(level, at) || level.getBlockState(at.below()).isAir()) continue;
				p.pending.add(new Project.Placement(at, sources.get(k++)));
				p.anchors.add(at);
			}
		p.total = p.pending.size();
		return p;
	}

	/** Acabaram os postes: o fio entre eles (cada um ao mais perto já ligado) e da fonte ao primeiro. Devolve os postes postos. */
	static int finish(ServerLevel level, Village v, Project p) {
		StreetLightKit kit = StreetLightKit.forVillage(level, v);
		List<BlockPos> poles = new ArrayList<>(), sources = new ArrayList<>();
		for (BlockPos a : p.anchors) {
			BlockState s = level.getBlockState(a);
			if (kit.isPole(s)) poles.add(a);
			else if (kit.industrial() && !s.isAir() && kit.sources().stream().anyMatch(x -> s.is(x.getBlock()))) sources.add(a);
		}
		if (kit.industrial()) {
			List<BlockPos> linked = new ArrayList<>();
			for (BlockPos pole : poles) {
				BlockPos best = null;
				for (BlockPos o : linked) if (best == null || o.distSqr(pole) < best.distSqr(pole)) best = o;
				if (best != null && kit.link(level, best, pole)) p.linked++;
				linked.add(pole);
			}
			if (!poles.isEmpty()) for (BlockPos src : sources) if (kit.link(level, poles.get(0), src)) p.linked++;
		}
		return poles.size();
	}

	/** Corta um fio (para os testes arrumarem; nada sem o Industrial). */
	public static void unlink(ServerLevel level, BlockPos a, BlockPos b) {
		try {
			Class.forName(StreetLightKit.WIRES_CLASS).getMethod("unlink", ServerLevel.class, BlockPos.class, BlockPos.class).invoke(null, level, a, b);
		} catch (ReflectiveOperationException | LinkageError e) {
			// sem o Industrial não há fios
		}
	}

	/** Quantos fios saem de um ponto (para os testes; 0 sem o Industrial). */
	public static int wires(ServerLevel level, BlockPos pos) {
		try {
			return (Integer) Class.forName(StreetLightKit.WIRES_CLASS).getMethod("wires", ServerLevel.class, BlockPos.class).invoke(null, level, pos);
		} catch (ReflectiveOperationException | LinkageError e) {
			return 0;
		}
	}
}
