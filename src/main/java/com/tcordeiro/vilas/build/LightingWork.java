package com.tcordeiro.vilas.build;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.tcordeiro.vilas.TVilas;
import com.tcordeiro.vilas.logic.LightingRules;
import com.tcordeiro.vilas.village.Project;
import com.tcordeiro.vilas.village.Village;
import com.tcordeiro.vilas.village.VillageManager;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
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
				if (nearDoor(level, top, DOOR_CLEARANCE)) continue; // nunca à frente de uma porta ou portão
				if (!open(level, top, WALL_CLEARANCE, height)) continue; // ≥ 1 bloco de paredes e beirais (o fio não roça)
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
		// a fonte da Prefeitura (Industrial): no chão, perto da Mesa (sítios de onde se vê o céu primeiro, para os painéis)
		List<BlockState> sources = kit.sources();
		List<BlockPos[]> groups = sources.isEmpty() ? List.of() : sourceGroups(level, v);
		if (!groups.isEmpty())
			for (int k = 0; k < sources.size(); k++) {
				p.pending.add(new Project.Placement(groups.get(0)[k], sources.get(k)));
				p.anchors.add(groups.get(0)[k]);
			}
		p.total = p.pending.size();
		return p;
	}

	/** Distância mínima (blocos, na horizontal) de um poste a uma porta ou portão. */
	public static final int DOOR_CLEARANCE = 2;
	/** Até onde (blocos) se procura chão para a fonte, à volta da Mesa. */
	public static final int SOURCE_RANGE = 8;

	/** Há uma porta ou portão a {@code r} blocos (na horizontal; da altura do chão a 2 acima)? */
	public static boolean nearDoor(ServerLevel level, BlockPos at, int r) {
		for (BlockPos q : BlockPos.betweenClosed(at.offset(-r, -1, -r), at.offset(r, 2, r))) {
			BlockState s = level.getBlockState(q);
			if (s.is(BlockTags.DOORS) || s.is(BlockTags.FENCE_GATES)) return true;
		}
		return false;
	}

	/** Folga (blocos, na horizontal) dos postes a paredes e beirais, e da fonte a construções. */
	public static final int WALL_CLEARANCE = 1, SOURCE_CLEARANCE = 2;

	/** Nada com corpo (parede, beiral, vedação) a {@code r} blocos na horizontal, do chão até {@code height} acima (erva e flores não contam). */
	public static boolean open(ServerLevel level, BlockPos at, int r, int height) {
		for (BlockPos q : BlockPos.betweenClosed(at.offset(-r, 0, -r), at.offset(r, height, r))) {
			BlockState s = level.getBlockState(q);
			if (!s.isAir() && !s.getCollisionShape(level, q).isEmpty()) return false;
		}
		return true;
	}

	/** Um sítio para uma parte da fonte: livre, à altura da Mesa, apoiado num bloco sólido, fora da rua e longe de portas. */
	static boolean sourceSpot(ServerLevel level, Village v, BlockPos at) {
		if (!v.covers(at.getX(), at.getZ()) || !Ground.mayBuild(level, at) || !level.getBlockState(at.above()).isAir()) return false;
		if (!level.getBlockState(at.below()).isFaceSturdy(level, at.below(), Direction.UP)) return false;
		if (!Ground.natural(level.getBlockState(at.below()))) return false; // no chão (erva, terra, pedra), não em cima de uma horta ou de um muro
		return !Ground.street(level.getBlockState(at.below())) && !nearDoor(level, at, 1);
	}

	/**
	 * Onde pôr a fonte: os 3 blocos JUNTOS (painel, painel e a bateria ao meio, pela ordem de {@link StreetLightKit#sources()}) no chão
	 * à altura da Mesa (±1), apoiados num bloco sólido (nunca no ar nem num telhado), fora de terrenos e longe de portas. 0.3.0:
	 * TODAS as partes veem o céu e ficam a ≥ {@link #SOURCE_CLEARANCE} blocos de QUALQUER bloco que não seja natural (casas, poços e
	 * os seus telhados, vedações, água…) — {@link #sourceClear}. Procura a ≤ {@link #SOURCE_RANGE} blocos da Mesa; se não houver,
	 * até {@link #SOURCE_FAR}. Os mais perto da Mesa primeiro.
	 */
	public static List<BlockPos[]> sourceGroups(ServerLevel level, Village v) {
		for (int r : new int[] {SOURCE_RANGE, SOURCE_FAR}) {
			List<BlockPos[]> out = sourceGroups(level, v, r);
			if (!out.isEmpty()) return out;
		}
		return List.of();
	}

	/** Até onde se alarga a procura da fonte, se a ≤ {@link #SOURCE_RANGE} da Mesa não houver sítio. */
	public static final int SOURCE_FAR = 16;

	static List<BlockPos[]> sourceGroups(ServerLevel level, Village v, int r) {
		BlockPos c = v.center;
		List<BlockPos[]> out = new ArrayList<>();
		for (int dx = -r; dx <= r; dx++)
			for (int dz = -r; dz <= r; dz++) {
				if (Math.max(Math.abs(dx), Math.abs(dz)) < 2) continue;
				for (int dy = -1; dy <= 1; dy++) {
					BlockPos at = c.offset(dx, dy, dz);
					if (!sourceSpot(level, v, at)) continue;
					BlockPos[] g = null;
					for (Direction d : new Direction[] {Direction.EAST, Direction.SOUTH}) {
						BlockPos p1 = at.relative(d), p2 = at.relative(d.getOpposite());
						BlockPos[] cand = {p1.immutable(), p2.immutable(), at.immutable()};
						if (sourceSpot(level, v, p1) && sourceSpot(level, v, p2) && sourceClear(level, cand)) {
							g = cand;
							break;
						}
					}
					if (g != null) out.add(g);
					break;
				}
			}
		out.sort(Comparator.<BlockPos[]>comparingDouble(g -> g[2].distSqr(c)));
		return out;
	}

	/** Céu aberto por cima (nada que bloqueie, folhas incluídas): pelo mapa de alturas, que está sempre em dia (a luz do céu não). */
	public static boolean openSky(ServerLevel level, BlockPos p) {
		return level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, p.getX(), p.getZ()) <= p.getY();
	}

	/**
	 * REGRA (0.3.0): a fonte em campo aberto — cada parte vê o céu e não há nenhum bloco não natural (nem erva, flores, neve) a ≤
	 * {@link #SOURCE_CLEARANCE} blocos na horizontal, do chão até 4 acima. O chão por baixo de cada parte já foi visto em
	 * {@link #sourceSpot} (natural); ruas ao nível do chão à volta não contam.
	 */
	public static boolean sourceClear(ServerLevel level, BlockPos[] parts) {
		java.util.Set<BlockPos> own = java.util.Set.of(parts);
		for (BlockPos p : parts) {
			if (!openSky(level, p)) return false;
			int r = SOURCE_CLEARANCE;
			for (BlockPos q : BlockPos.betweenClosed(p.offset(-r, 0, -r), p.offset(r, 4, r))) {
				if (own.contains(q)) continue;
				BlockState s = level.getBlockState(q);
				if (s.isAir() || Ground.grassOrFlower(s) || s.is(net.minecraft.world.level.block.Blocks.SNOW)) continue;
				if (!Ground.natural(s) || !level.getFluidState(q).isEmpty()) return false;
			}
		}
		return true;
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
		if (!kit.industrial()) return poles.size();
		// a árvore dos postes: só fios livres e com folga (≤ o vão máximo). Fica o MAIOR grupo de postes ligáveis entre si (em empate,
		// o mais perto da Mesa); cada poste liga-se ao já ligado mais perto com fio livre. Os que não chegam a nenhum saem do mundo.
		int n = poles.size();
		boolean[][] ok = new boolean[n][n];
		double span2 = (double) StreetLightKit.POLE_SPAN * StreetLightKit.POLE_SPAN;
		for (int i = 0; i < n; i++)
			for (int j = i + 1; j < n; j++)
				if (poles.get(i).distSqr(poles.get(j)) <= span2) {
					String why = kit.wireBlocker(level, poles.get(i), poles.get(j));
					ok[i][j] = ok[j][i] = why == null;
					if (why != null) TVilas.LOGGER.info("Vilas: fio {} → {} recusado: {}", poles.get(i).toShortString(), poles.get(j).toShortString(), why);
				}
		int[] comp = new int[n];
		java.util.Arrays.fill(comp, -1);
		int best = -1, bestSize = 0, comps = 0;
		for (int i = 0; i < n; i++) {
			if (comp[i] >= 0) continue;
			int size = 0;
			java.util.ArrayDeque<Integer> todo = new java.util.ArrayDeque<>(List.of(i));
			comp[i] = comps;
			while (!todo.isEmpty()) {
				int k = todo.poll();
				size++;
				for (int j = 0; j < n; j++) if (ok[k][j] && comp[j] < 0) {
					comp[j] = comps;
					todo.add(j);
				}
			}
			if (size > bestSize) { // os postes vêm por distância à Mesa: em empate fica o grupo do mais perto
				bestSize = size;
				best = comps;
			}
			comps++;
		}
		List<BlockPos> linked = new ArrayList<>(), left = new ArrayList<>();
		List<Integer> inLinked = new ArrayList<>();
		for (int i = 0; i < n; i++) if (comp[i] != best) left.add(poles.get(i));
		int root = -1;
		for (int i = 0; i < n && root < 0; i++) if (comp[i] == best) root = i;
		if (root >= 0) {
			linked.add(poles.get(root));
			inLinked.add(root);
		}
		boolean grew = true;
		while (grew) {
			grew = false;
			int bi = -1, bj = -1;
			double bd = Double.MAX_VALUE;
			for (int j = 0; j < n; j++) {
				if (comp[j] != best || inLinked.contains(j)) continue;
				for (int i : inLinked)
					if (ok[i][j] && poles.get(i).distSqr(poles.get(j)) < bd) {
						bd = poles.get(i).distSqr(poles.get(j));
						bi = i;
						bj = j;
					}
			}
			if (bj >= 0) {
				inLinked.add(bj);
				grew = true;
				if (kit.link(level, poles.get(bi), poles.get(bj))) {
					p.linked++;
					linked.add(poles.get(bj));
				} else left.add(poles.get(bj));
			}
		}
		for (BlockPos pole : left) removePole(level, kit, pole);
		if (linked.size() == 1 && sources.isEmpty()) {
			removePole(level, kit, linked.get(0)); // um poste sozinho e sem fonte não serve para nada
			linked.clear();
		}
		// a fonte: os 3 blocos juntos; só a bateria leva fio (ao poste ligado mais perto com fio livre), os painéis encostados carregam-na.
		// Sem fio livre dali, a fonte inteira muda para o próximo sítio aberto que o tenha.
		List<BlockPos> placedSources = new ArrayList<>();
		BlockPos battery = null;
		for (BlockPos src : sources) if (kit.isBattery(level.getBlockState(src))) battery = src;
		if (battery != null) {
			BlockPos at = linkSource(level, kit, linked, battery);
			if (at != null) placedSources.addAll(sources);
			else {
				List<BlockState> states = kit.sources();
				for (BlockPos src : sources) level.setBlock(src, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
				for (BlockPos[] g : sourceGroups(level, v)) {
					for (int k = 0; k < g.length; k++) level.setBlock(g[k], states.get(k), 3);
					if (linkSource(level, kit, linked, g[g.length - 1]) != null) {
						placedSources.addAll(List.of(g));
						break;
					}
					for (BlockPos q : g) level.setBlock(q, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
				}
			}
			if (!placedSources.isEmpty()) p.linked++;
		}
		// junta (não substitui) os desta obra aos de obras anteriores; os que já não existem saem
		v.extra.lightAnchors.removeIf(a -> level.isLoaded(BlockPos.of(a)) && level.getBlockState(BlockPos.of(a)).isAir());
		for (BlockPos a : linked) if (!v.extra.lightAnchors.contains(a.asLong())) v.extra.lightAnchors.add(a.asLong());
		for (BlockPos a : placedSources) if (!v.extra.lightAnchors.contains(a.asLong())) v.extra.lightAnchors.add(a.asLong());
		return linked.size();
	}

	/** Liga a fonte em {@code src} ao poste ligado mais perto com fio livre. Devolve {@code src} se ligou, null se não. */
	static BlockPos linkSource(ServerLevel level, StreetLightKit kit, List<BlockPos> poles, BlockPos src) {
		List<BlockPos> byDist = new ArrayList<>(poles);
		byDist.sort(java.util.Comparator.comparingDouble(a -> a.distSqr(src)));
		for (BlockPos pole : byDist) {
			if (pole.distSqr(src) > (double) StreetLightKit.POLE_SPAN * StreetLightKit.POLE_SPAN) break;
			if (kit.wireClear(level, pole, src) && kit.link(level, pole, src)) return src;
		}
		return null;
	}

	/**
	 * Tira um poste que ficou sem fio livre: o pé e as partes de cima (o mesmo bloco; até 6 com o Industrial 0.12.2), de cima para
	 * baixo e SEM largar itens (pôr ar com 2|16 não corre a tabela de saque). Só blocos do próprio poste.
	 */
	public static void removePole(ServerLevel level, StreetLightKit kit, BlockPos base) {
		Block b = level.getBlockState(base).getBlock();
		int top = 0;
		for (int i = 1; i < Math.max(kit.height(), MAX_POLE); i++) {
			if (!level.getBlockState(base.above(i)).is(b)) break;
			top = i;
		}
		for (int i = top; i >= 0; i--) level.setBlock(base.above(i), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 2 | 16);
	}

	/** A coluna mais alta que um poste do Industrial pode ter (0.12.2). */
	public static final int MAX_POLE = 6;

	/**
	 * Revê os fios da iluminação desta vila (ao fim de cada dia e quando a Mesa volta a carregar): um fio que ficou tapado por uma
	 * construção nova é desfeito. Devolve quantos fios desfez.
	 */
	public static int revalidate(ServerLevel level, Village v) {
		if (v.extra.lightAnchors.isEmpty()) return 0;
		StreetLightKit kit = StreetLightKit.forVillage(level, v);
		if (!kit.industrial()) return 0;
		int cut = 0;
		for (long a : List.copyOf(v.extra.lightAnchors)) {
			BlockPos pa = BlockPos.of(a);
			if (!level.isLoaded(pa) || level.getBlockState(pa).isAir()) continue;
			for (long b : kit.partners(level, pa)) {
				BlockPos pb = BlockPos.of(b);
				if (!level.isLoaded(pb)) continue;
				if (!kit.wireClear(level, pa, pb)) {
					unlink(level, pa, pb);
					cut++;
				}
			}
		}
		return cut;
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
