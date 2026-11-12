package com.tcordeiro.vilas.build;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import com.tcordeiro.vilas.TVilas;
import com.tcordeiro.vilas.VilasConfig;
import com.tcordeiro.vilas.logic.CivicRules;
import com.tcordeiro.vilas.logic.LotRules;
import com.tcordeiro.vilas.registry.VilasBlocks;
import com.tcordeiro.vilas.village.Civic;
import com.tcordeiro.vilas.village.Lot;
import com.tcordeiro.vilas.village.Project;
import com.tcordeiro.vilas.village.Village;
import com.tcordeiro.vilas.village.VillageData;
import com.tcordeiro.vilas.village.VillageEconomy;
import com.tcordeiro.vilas.village.VillageManager;
import com.tcordeiro.vilas.worldgen.VillagePoolInjector;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.StructureTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * 0.3.0 — A área cívica e os edifícios que faltam a uma vila antiga.
 * <ul>
 * <li>{@link #refresh}: a área da Mesa (±{@link LotRules#CIVIC_MARGIN}) e, uma vez, as peças Prefeitura/Mercado/Taverna/Praça
 * que a vila já tem (geradas pelo mod) — ficam na área cívica protegida (M15) e contam como feitas.</li>
 * <li>{@link #commissionNext}: uma vez por dia, o Prefeito encomenda o primeiro edifício que falta ({@link CivicRules#next}), pago
 * pela tesouraria, com as estruturas NBT que já existem (as mesmas das vilas novas). Os construtores fazem-no bloco a bloco à
 * vista ({@link Builders}), ou de noite.</li>
 * </ul>
 * REGRA DE SEGURANÇA: o sítio é escolhido com o chão natural (nada de ruas nem blocos de jogador), o volume todo livre (ar, erva,
 * flores, neve), fora de terrenos e de outras áreas cívicas; e cada bloco é verificado outra vez ao pô-lo ({@link Ground#mayBuild}).
 * A Mesa do Prefeito da estrutura não é posta (a vila já tem a sua).
 */
public final class CivicWork {
	private CivicWork() {}

	/** Onde se procura sítio: entre esta distância do centro e {@link #MAX_DIST}. */
	public static final int MIN_DIST = LotRules.CIVIC_MARGIN + 2, MAX_DIST = 48, STEP = 2;

	public static Project.Kind kind(String building) {
		return Project.Kind.valueOf(building.toUpperCase(Locale.ROOT));
	}

	public static String building(Project.Kind k) {
		return k.name().toLowerCase(Locale.ROOT);
	}

	static boolean deskHere(ServerLevel level, Village v) {
		return level.isLoaded(v.center) && level.getBlockState(v.center).is(VilasBlocks.MAYOR_DESK);
	}

	/** A área cívica da Mesa e (uma vez) os edifícios que a vila já tem. */
	public static void refresh(ServerLevel level, Village v) {
		if (!deskHere(level, v)) return;
		Civic c = v.civic;
		int m = LotRules.CIVIC_MARGIN;
		LotRules.Area desk = new LotRules.Area(v.center.getX() - m, v.center.getZ() - m, v.center.getX() + m, v.center.getZ() + m);
		boolean changed = false;
		if (c.boxes.stream().noneMatch(b -> b.kind().equals(Civic.DESK) && b.area().equals(desk))) {
			c.put(Civic.DESK, desk);
			changed = true;
		}
		if (!c.scanned) {
			c.scanned = true;
			scan(level, v);
			changed = true;
		}
		if (changed) VillageData.get(level).changed();
	}

	/** A estrutura de vila do jogo onde fica a Mesa (ou null). */
	static StructureStart villageStructure(ServerLevel level, Village v) {
		var tag = level.registryAccess().lookupOrThrow(Registries.STRUCTURE).get(StructureTags.VILLAGE);
		if (tag.isEmpty()) return null;
		StructureStart s = level.structureManager().getStructureAt(v.center, tag.get());
		return s != null && s.isValid() ? s : null;
	}

	/** As peças do Vilas que a vila já tem: contam como feitas e entram na área cívica. */
	static void scan(ServerLevel level, Village v) {
		StructureStart s = villageStructure(level, v);
		if (s == null) return;
		int n = 0;
		for (StructurePiece piece : s.getPieces()) {
			if (!(piece instanceof PoolElementStructurePiece pe)) continue;
			String el = pe.getElement().toString();
			for (String k : Civic.BUILDINGS)
				if (el.contains(TVilas.MOD_ID + ":village/") && (el.contains("/" + k + "]") || el.endsWith("/" + k))) {
					v.civic.built.add(k);
					BoundingBox b = piece.getBoundingBox();
					v.civic.put(k + "_" + (n++), new LotRules.Area(b.minX() - 1, b.minZ() - 1, b.maxX() + 1, b.maxZ() + 1));
					break;
				}
		}
	}

	/** É uma vila de verdade (estrutura de vila do jogo, ou moradores bastantes)? */
	public static boolean realVillage(ServerLevel level, Village v) {
		return CivicRules.realVillage(villageStructure(level, v) != null, VillageManager.countPopulation(level, v));
	}

	static boolean building(Village v) {
		for (Project p : v.extra.projects) if (p.kind.civic()) return true;
		return false;
	}

	/** Uma vez por dia: encomenda o primeiro edifício que falta (se a tesouraria o pagar e houver sítio). */
	public static Project commissionNext(ServerLevel level, Village v) {
		if (!VilasConfig.get().civic_buildings || !deskHere(level, v) || building(v)) return null;
		String k = CivicRules.next(v.civic.built, false, v.economy.treasury, realVillage(level, v));
		return k == null ? null : commission(level, v, k);
	}

	/** Encomenda este edifício já (paga, marca a área cívica e entrega aos construtores), ou null se não houver sítio/dinheiro. */
	public static Project commission(ServerLevel level, Village v, String building) {
		long cost = CivicRules.cost(building);
		if (v.economy.treasury < cost) return null;
		Project p = create(level, v, building);
		if (p == null) return null;
		v.economy.treasury -= cost;
		v.economy.log(VillageEconomy.day(level), "tvilas.upgrade." + building, cost);
		VillageManager.broadcast(level, v, Component.translatable("tvilas.upgrade.bought", Component.translatable("tvilas.upgrade." + building),
				VillageManager.money(cost)));
		Builders.start(level, v, p);
		return p;
	}

	/** O bioma da vila para escolher a versão da estrutura. */
	static String biome(ServerLevel level, Village v) {
		String b = level.getBiome(v.center).unwrapKey().map(k -> k.identifier().getPath()).orElse("");
		if (b.contains("desert") || b.contains("badlands")) return "desert";
		if (b.contains("savanna")) return "savanna";
		if (b.contains("snow") || b.contains("ice") || b.contains("frozen")) return "snowy";
		if (b.contains("taiga")) return "taiga";
		return "plains";
	}

	static net.minecraft.resources.Identifier template(String biome, String building) {
		return switch (building) {
			case "town_hall" -> VillagePoolInjector.townHall(biome);
			case "market" -> VillagePoolInjector.market(biome);
			case "tavern" -> VillagePoolInjector.tavern(biome);
			default -> VillagePoolInjector.plaza(biome);
		};
	}

	/** A obra (blocos da estrutura NBT, de baixo para cima) num sítio livre, ou null. */
	public static Project create(ServerLevel level, Village v, String building) {
		var t = level.getServer().getStructureTemplateManager().get(template(biome(level, v), building));
		if (t.isEmpty()) {
			TVilas.LOGGER.warn("Vilas: estrutura {} não encontrada", template(biome(level, v), building));
			return null;
		}
		StructureTemplate tpl = t.get();
		var size = tpl.getSize();
		BlockPos origin = site(level, v, size.getX(), size.getY(), size.getZ());
		if (origin == null) {
			TVilas.LOGGER.info("Vilas: sem sítio livre para {} na vila em {}", building, v.center.toShortString());
			return null;
		}
		var palettes = ((com.tcordeiro.vilas.mixin.StructureTemplateAccessor) tpl).tvilas$palettes();
		if (palettes.isEmpty()) return null;
		List<StructureTemplate.StructureBlockInfo> blocks = new ArrayList<>(palettes.get(0).blocks());
		blocks.sort(Comparator.comparingInt((StructureTemplate.StructureBlockInfo b) -> b.pos().getY()).thenComparingInt(b -> b.pos().getX())
				.thenComparingInt(b -> b.pos().getZ()));
		Project p = new Project(kind(building), origin.offset(size.getX() / 2, 0, size.getZ() / 2));
		for (var b : blocks) {
			BlockState s = b.state();
			if (s.isAir() || s.is(Blocks.STRUCTURE_VOID) || s.is(Blocks.JIGSAW) || s.is(VilasBlocks.MAYOR_DESK)) continue;
			p.pending.add(new Project.Placement(origin.offset(b.pos()), s));
		}
		p.total = p.pending.size();
		v.civic.put(building + "_built", new LotRules.Area(origin.getX() - 1, origin.getZ() - 1, origin.getX() + size.getX(), origin.getZ() + size.getZ()));
		return p;
	}

	/**
	 * Um sítio livre para uma estrutura {@code sx}×{@code sy}×{@code sz} perto do centro: o canto de baixo (a camada 0 da
	 * estrutura fica no primeiro bloco livre acima do chão), ou null.
	 */
	public static BlockPos site(ServerLevel level, Village v, int sx, int sy, int sz) {
		int max = Math.min(MAX_DIST, v.radius - Math.max(sx, sz));
		for (int r = MIN_DIST; r <= max; r += STEP)
			for (int dx = -r; dx <= r; dx += STEP)
				for (int dz = -r; dz <= r; dz += STEP) {
					if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
					int x0 = v.center.getX() + dx - sx / 2, z0 = v.center.getZ() + dz - sz / 2;
					BlockPos base = fits(level, v, x0, z0, sx, sy, sz);
					if (base != null) return base;
				}
		return null;
	}

	/** REGRA DE SEGURANÇA: a estrutura cabe com o canto em (x0, z0)? Devolve a base ou null. */
	static BlockPos fits(ServerLevel level, Village v, int x0, int z0, int sx, int sy, int sz) {
		LotRules.Area area = new LotRules.Area(x0 - 1, z0 - 1, x0 + sx, z0 + sz);
		if (!LotRules.insideVillage(area, v.center.getX(), v.center.getZ(), v.radius)) return null;
		VillageData d = VillageData.get(level);
		for (Lot l : d.lots) if (l.area.overlaps(area)) return null;
		for (Village o : d.villages) if (o.civic.overlaps(area)) return null;
		int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
		int[] tops = new int[sx * sz];
		for (int x = 0; x < sx; x++)
			for (int z = 0; z < sz; z++) {
				int wx = x0 + x, wz = z0 + z;
				if (!level.isLoaded(new BlockPos(wx, v.center.getY(), wz))) return null;
				BlockPos top = Ground.surface(level, wx, wz, v.center.getY());
				if (!natural(level.getBlockState(top.below()))) return null; // rua, casa ou bloco de jogador: não
				tops[x * sz + z] = top.getY();
				minY = Math.min(minY, top.getY());
				maxY = Math.max(maxY, top.getY());
			}
		if (!CivicRules.flatEnough(minY, maxY)) return null;
		for (int x = 0; x < sx; x++)
			for (int z = 0; z < sz; z++)
				for (int y = tops[x * sz + z]; y < maxY + sy; y++) {
					BlockPos p = new BlockPos(x0 + x, y, z0 + z);
					if (level.isOutsideBuildHeight(p) || !Ground.free(level.getBlockState(p))) return null;
				}
		return new BlockPos(x0, maxY, z0);
	}

	/**
	 * Chão natural para um edifício: o de {@link Ground#natural} e também a relva, a podzólica, o micélio, a lama e o musgo (no
	 * 26.3 a etiqueta {@code minecraft:dirt} não traz o bloco de relva). Nunca ruas nem blocos de jogador.
	 */
	static boolean natural(BlockState s) {
		return !Ground.street(s) && (Ground.natural(s) || s.is(Blocks.GRASS_BLOCK) || s.is(Blocks.PODZOL) || s.is(Blocks.MYCELIUM)
				|| s.is(Blocks.COARSE_DIRT) || s.is(Blocks.ROOTED_DIRT) || s.is(Blocks.MUD) || s.is(Blocks.MOSS_BLOCK) || s.is(Blocks.DIRT));
	}

	/** Acabou a obra: a vila já tem este edifício. */
	static void finish(ServerLevel level, Village v, Project p) {
		v.civic.built.add(building(p.kind));
		v.extra.structures++;
		VillageData.get(level).changed();
	}
}
