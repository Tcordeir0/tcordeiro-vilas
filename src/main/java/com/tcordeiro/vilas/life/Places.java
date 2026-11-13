package com.tcordeiro.vilas.life;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.tcordeiro.vilas.logic.LotRules;
import com.tcordeiro.vilas.village.Civic;
import com.tcordeiro.vilas.village.Village;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * 0.4.0 — Os sítios da vida da vila, lidos UMA vez a cada 5 minutos (não a cada tick): a Praça e a Taverna (o convívio), os
 * assentos lá dentro, as camas de casal do Móveis, as lareiras e fogueiras acesas, os sinos e os lugares cobertos (abrigo). Vem das
 * áreas cívicas que a vila já conhece (Prefeitura, Mercado, Taverna, Praça); sem elas, a praça da Mesa.
 */
public final class Places {
	public static final int REFRESH = 6000;

	/** Um edifício: o centro (no chão), os lugares de pé lá dentro, os assentos, as camas de casal e as lareiras. */
	public static final class Spot {
		public final String kind;
		public final BlockPos center;
		public final List<BlockPos> stand = new ArrayList<>(), indoor = new ArrayList<>(), seats = new ArrayList<>(), beds = new ArrayList<>(),
				fires = new ArrayList<>();

		Spot(String kind, BlockPos center) {
			this.kind = kind;
			this.center = center;
		}
	}

	public final Map<String, Spot> spots = new HashMap<>();
	public final List<BlockPos> bells = new ArrayList<>();
	public long scanned = Long.MIN_VALUE;
	/** Assento ou cama → aldeão que o reservou (para dois não irem ao mesmo). */
	public final Map<BlockPos, UUID> reserved = new HashMap<>();

	public Spot plaza() {
		Spot s = spots.get("plaza");
		return s != null ? s : spots.get(Civic.DESK);
	}

	public Spot tavern() {
		return spots.get("tavern");
	}

	public Spot hall() {
		return spots.get("town_hall");
	}

	/** Um assento livre (não reservado nem ocupado) num destes sítios, ou null. */
	public BlockPos freeSeat(ServerLevel level, Spot s, RandomSource r) {
		if (s == null || s.seats.isEmpty()) return null;
		int start = r.nextInt(s.seats.size());
		for (int k = 0; k < s.seats.size(); k++) {
			BlockPos p = s.seats.get((start + k) % s.seats.size());
			if (!reserved.containsKey(p) && Seats.seat(level.getBlockState(p)) && !Seats.taken(level, p)) return p;
		}
		return null;
	}

	/** Uma cama de casal livre (um dos lados), ou null. */
	public BlockPos freeBed(ServerLevel level, RandomSource r) {
		for (Spot s : spots.values())
			for (BlockPos p : s.beds) {
				BlockState st = level.getBlockState(p);
				if (reserved.containsKey(p) || !st.hasProperty(BedBlock.OCCUPIED) || st.getValue(BedBlock.OCCUPIED)) continue;
				return p;
			}
		return null;
	}

	public static BlockPos any(List<BlockPos> list, RandomSource r) {
		return list.isEmpty() ? null : list.get(r.nextInt(list.size()));
	}

	public void release(UUID who) {
		reserved.values().removeIf(who::equals);
	}

	// ---------------------------------------------------------------- leitura

	public void refresh(ServerLevel level, Village v) {
		long now = level.getGameTime();
		if (scanned != Long.MIN_VALUE && now - scanned < REFRESH && now >= scanned) return;
		scanned = now;
		spots.clear();
		bells.clear();
		for (Civic.Box b : v.civic.boxes) {
			if (b.kind().equals(Civic.DESK) && v.civic.boxes.stream().noneMatch(o -> "plaza".equals(building(o.kind())))) {
				spots.put(Civic.DESK, scan(level, b.kind(), b.area(), v.center.getY()));
				continue;
			}
			String k = building(b.kind());
			if (k != null && !spots.containsKey(k)) spots.put(k, scan(level, k, b.area(), v.center.getY()));
		}
		if (plaza() == null) { // sem área cívica conhecida: a praça da Mesa
			LotRules.Area a = new LotRules.Area(v.center.getX() - 6, v.center.getZ() - 6, v.center.getX() + 6, v.center.getZ() + 6);
			spots.put(Civic.DESK, scan(level, Civic.DESK, a, v.center.getY()));
		}
		for (BlockPos p : BlockPos.betweenClosed(v.center.offset(-12, -4, -12), v.center.offset(12, 6, 12)))
			if (level.isLoaded(p) && level.getBlockState(p).getBlock() instanceof BellBlock) bells.add(p.immutable());
	}

	/** O edifício de uma área cívica: as áreas chamam-se «tavern_0», «plaza_built»… (ou só «tavern» nos testes). */
	public static String building(String kind) {
		for (String k : Civic.BUILDINGS) if (kind.equals(k) || kind.startsWith(k + "_")) return k;
		return null;
	}

	static Spot scan(ServerLevel level, String kind, LotRules.Area a, int refY) {
		int cx = (a.minX() + a.maxX()) / 2, cz = (a.minZ() + a.maxZ()) / 2;
		int ground = ground(level, cx, cz, refY);
		int y0 = Math.min(ground, refY) - 4, y1 = Math.max(ground, refY) + 10;
		Spot s = new Spot(kind, new BlockPos(cx, ground, cz));
		BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
		for (int x = a.minX(); x <= a.maxX(); x++)
			for (int z = a.minZ(); z <= a.maxZ(); z++) {
				m.set(x, refY, z);
				if (!level.isLoaded(m)) continue;
				for (int y = y0; y <= y1; y++) {
					m.set(x, y, z);
					BlockState st = level.getBlockState(m);
					if (Seats.seat(st) && level.getBlockState(m.above()).isAir() && s.seats.size() < 24) s.seats.add(m.immutable());
					else if (doubleBed(st) && s.beds.size() < 8) s.beds.add(m.immutable());
					else if (fire(st) && s.fires.size() < 8) s.fires.add(m.immutable());
					else if (st.isAir() && level.getBlockState(m.above()).isAir()) {
						BlockState below = level.getBlockState(m.below());
						if (!below.isAir() && below.isCollisionShapeFullBlock(level, m.below()) && level.getFluidState(m).isEmpty()) {
							boolean covered = !level.canSeeSky(m);
							if (covered && s.indoor.size() < 32) s.indoor.add(m.immutable());
							else if (!covered && s.stand.size() < 32 && y >= ground - 1 && y <= ground + 1) s.stand.add(m.immutable());
						}
					}
				}
			}
		return s;
	}

	/** O chão (o primeiro bloco de ar com chão firme por baixo) perto da altura {@code refY}: não se usa o mapa de alturas (telhados). */
	public static int ground(ServerLevel level, int x, int z, int refY) {
		BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos(x, refY, z);
		if (!level.isLoaded(m)) return refY;
		for (int d = 0; d <= 8; d++)
			for (int y : new int[] {refY + d, refY - d}) {
				m.set(x, y, z);
				if (level.getBlockState(m).isAir() && !level.getBlockState(m.below()).isAir() && level.getFluidState(m.below()).isEmpty()) return y;
			}
		return refY;
	}

	/** Cabeceira de uma cama de casal do Móveis (cada lado é uma cama). */
	static boolean doubleBed(BlockState s) {
		var id = BuiltInRegistries.BLOCK.getKey(s.getBlock());
		return id.getPath().endsWith("_double_bed") && s.hasProperty(BlockStateProperties.BED_PART)
				&& s.getValue(BlockStateProperties.BED_PART) == BedPart.HEAD;
	}

	/** Lareira acesa: fogueira, fornalha/defumador/alto-forno acesos, ou qualquer bloco «fireplace» aceso. */
	static boolean fire(BlockState s) {
		if (s.getBlock() instanceof CampfireBlock || s.getBlock() instanceof AbstractFurnaceBlock)
			return s.hasProperty(BlockStateProperties.LIT) && s.getValue(BlockStateProperties.LIT);
		var id = BuiltInRegistries.BLOCK.getKey(s.getBlock());
		return id.getPath().contains("fireplace") && (!s.hasProperty(BlockStateProperties.LIT) || s.getValue(BlockStateProperties.LIT));
	}
}
