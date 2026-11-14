package com.tcordeiro.vilas.life.projects;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.tcordeiro.vilas.build.Builders;
import com.tcordeiro.vilas.build.CivicWork;
import com.tcordeiro.vilas.build.Ground;
import com.tcordeiro.vilas.life.memory.Chronicle;
import com.tcordeiro.vilas.life.memory.Lore;
import com.tcordeiro.vilas.logic.LoreRules;
import com.tcordeiro.vilas.village.Project;
import com.tcordeiro.vilas.village.Village;
import com.tcordeiro.vilas.village.VillageData;
import com.tcordeiro.vilas.village.VillageEconomy;
import com.tcordeiro.vilas.village.VillageManager;
import com.tcordeiro.vilas.village.VillageNews;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StandingSignBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.entity.SignTextSlot;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * 0.6.0 — Projetos comunitários com doações: a vila propõe uma obra (ponte, chafariz, farol ou coreto), os jogadores doam os
 * materiais no quadro ({@code /vila obra doar}), o Construtor faz a obra (fila de obras da 0.2) e no fim fica uma placa com os
 * nomes de quem doou. Nunca se cria dinheiro: doar dá só reputação (com teto por dia); só se tira ao jogador o que falta.
 * As obras só ocupam ar, erva, flores e neve fora dos terrenos ({@link Ground#mayBuild}): nunca partem nada.
 */
public final class Projects {
	private Projects() {}

	public static Lore lore(Village v) {
		return v.civic.life.lore;
	}

	// ---------------------------------------------------------------- o que cada obra pede

	public static Map<String, Integer> needs(String kind, int length) {
		Map<String, Integer> m = new java.util.LinkedHashMap<>();
		switch (kind) {
			case Community.FOUNTAIN -> {
				m.put("minecraft:stone_bricks", 16);
				m.put("minecraft:stone_brick_wall", 2);
				m.put("minecraft:lantern", 1);
			}
			case Community.BANDSTAND -> {
				m.put("minecraft:oak_planks", 25);
				m.put("minecraft:oak_fence", 12);
				m.put("minecraft:oak_slab", 25);
				m.put("minecraft:lantern", 1);
			}
			case Community.LIGHTHOUSE -> {
				m.put("minecraft:stone_bricks", 48);
				m.put("minecraft:glass", 8);
				m.put("minecraft:glowstone", 1);
			}
			default -> { // ponte
				m.put("minecraft:oak_planks", 3 * length);
				m.put("minecraft:oak_fence", 2 * length);
			}
		}
		return m;
	}

	static BlockState state(String id) {
		return BuiltInRegistries.BLOCK.getValue(Identifier.parse(id)).defaultBlockState();
	}

	/** Os blocos da obra, de baixo para cima. */
	public static List<Project.Placement> placements(Community c) {
		List<Project.Placement> out = new ArrayList<>();
		BlockPos b = c.site;
		switch (c.kind) {
			case Community.FOUNTAIN -> {
				for (int x = 0; x < 5; x++)
					for (int z = 0; z < 5; z++) {
						boolean edge = x == 0 || z == 0 || x == 4 || z == 4;
						if (edge) out.add(new Project.Placement(b.offset(x, 0, z), Blocks.STONE_BRICKS.defaultBlockState()));
					}
				out.add(new Project.Placement(b.offset(2, 0, 2), Blocks.STONE_BRICK_WALL.defaultBlockState()));
				out.add(new Project.Placement(b.offset(2, 1, 2), Blocks.STONE_BRICK_WALL.defaultBlockState()));
				out.add(new Project.Placement(b.offset(2, 2, 2), Blocks.LANTERN.defaultBlockState()));
				for (int x = 1; x < 4; x++)
					for (int z = 1; z < 4; z++)
						if (x != 2 || z != 2) out.add(new Project.Placement(b.offset(x, 0, z), Blocks.WATER.defaultBlockState()));
			}
			case Community.BANDSTAND -> {
				for (int x = 0; x < 5; x++)
					for (int z = 0; z < 5; z++) out.add(new Project.Placement(b.offset(x, 0, z), Blocks.OAK_PLANKS.defaultBlockState()));
				for (int[] k : new int[][] {{0, 0}, {4, 0}, {0, 4}, {4, 4}})
					for (int y = 1; y <= 3; y++) out.add(new Project.Placement(b.offset(k[0], y, k[1]), Blocks.OAK_FENCE.defaultBlockState()));
				BlockState slab = Blocks.OAK_SLAB.defaultBlockState().setValue(BlockStateProperties.SLAB_TYPE, SlabType.BOTTOM);
				for (int x = 0; x < 5; x++)
					for (int z = 0; z < 5; z++) out.add(new Project.Placement(b.offset(x, 4, z), slab));
				out.add(new Project.Placement(b.offset(2, 3, 2), Blocks.LANTERN.defaultBlockState().setValue(BlockStateProperties.HANGING, true)));
			}
			case Community.LIGHTHOUSE -> {
				for (int y = 0; y < 6; y++)
					for (int x = 0; x < 3; x++)
						for (int z = 0; z < 3; z++)
							if (x != 1 || z != 1) out.add(new Project.Placement(b.offset(x, y, z), Blocks.STONE_BRICKS.defaultBlockState()));
				for (int x = 0; x < 3; x++)
					for (int z = 0; z < 3; z++)
						if (x != 1 || z != 1) out.add(new Project.Placement(b.offset(x, 6, z), Blocks.GLASS.defaultBlockState()));
				out.add(new Project.Placement(b.offset(1, 6, 1), Blocks.GLOWSTONE.defaultBlockState()));
			}
			default -> { // ponte: tabuleiro de 3 de largura sobre a água e guardas de cerca
				Direction along = c.dir == 0 ? Direction.EAST : Direction.SOUTH;
				Direction side = along.getClockWise();
				for (int i = 0; i < c.length; i++) {
					BlockPos at = b.relative(along, i);
					for (int s = -1; s <= 1; s++) out.add(new Project.Placement(at.relative(side, s), Blocks.OAK_PLANKS.defaultBlockState()));
				}
				for (int i = 0; i < c.length; i++) {
					BlockPos at = b.relative(along, i);
					out.add(new Project.Placement(at.relative(side, -1).above(), Blocks.OAK_FENCE.defaultBlockState()));
					out.add(new Project.Placement(at.relative(side, 1).above(), Blocks.OAK_FENCE.defaultBlockState()));
				}
			}
		}
		return out;
	}

	// ---------------------------------------------------------------- propor uma obra

	/** Uma vez por dia: sem obra a decorrer e já passaram {@link LoreRules#PROJECT_EVERY_DAYS} dias, a vila propõe uma. */
	public static void daily(ServerLevel level, Village v, long day) {
		Lore l = lore(v);
		if (l.project != null || !v.civic.meta.real || !LoreRules.projectDue(l.projectDay, day)) return;
		propose(level, v, day);
	}

	/** Propõe uma obra (a primeira que couber, por uma ordem sorteada com seed). Devolve-a ou null. */
	public static Community propose(ServerLevel level, Village v, long day) {
		Lore l = lore(v);
		List<String> order = new ArrayList<>(Community.KINDS);
		java.util.Collections.shuffle(order, new java.util.Random(v.id.getLeastSignificantBits() ^ day));
		for (String kind : order) {
			Community c = site(level, v, kind);
			if (c == null) continue;
			c.needs.putAll(needs(kind, c.length));
			l.project = c;
			l.projectDay = day;
			VillageNews.announce(level, v, VillageNews.BUILD, "tvilas.project060.proposed", Component.translatable("tvilas.project060." + kind));
			Chronicle.add(level, v, "project_proposed", "#tvilas.project060." + kind);
			VillageData.get(level).changed();
			proposed++;
			return c;
		}
		l.projectDay = day; // não coube nada: tenta outra vez daqui a uns dias
		return null;
	}

	public static int proposed, built;

	/** Onde fica a obra (ou null se não cabe): o chafariz, o coreto e o farol num chão plano livre; a ponte sobre um rio perto. */
	static Community site(ServerLevel level, Village v, String kind) {
		return switch (kind) {
			case Community.FOUNTAIN -> at(CivicWork.site(level, v, 5, 3, 5), kind);
			case Community.BANDSTAND -> at(CivicWork.site(level, v, 5, 5, 5), kind);
			case Community.LIGHTHOUSE -> at(CivicWork.site(level, v, 3, 7, 3), kind);
			default -> bridge(level, v);
		};
	}

	static Community at(BlockPos base, String kind) {
		return base == null ? null : new Community(kind, base, 0, 0);
	}

	/** Uma ponte: uma faixa de 3 a 12 blocos de água, entre duas margens, perto do centro e fora dos terrenos. */
	static Community bridge(ServerLevel level, Village v) {
		int max = Math.min(48, v.radius - 4);
		for (int r = 8; r <= max; r += 4)
			for (int dx = -r; dx <= r; dx += 4)
				for (int dz = -r; dz <= r; dz += 4) {
					if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
					int x = v.center.getX() + dx, z = v.center.getZ() + dz;
					if (!level.isLoaded(new BlockPos(x, v.center.getY(), z))) continue;
					BlockPos top = Ground.surface(level, x, z, v.center.getY());
					BlockPos w = top.below();
					if (!level.getBlockState(w).is(Blocks.WATER)) continue;
					for (int dir = 0; dir < 2; dir++) {
						Community c = span(level, v, w, dir);
						if (c != null) return c;
					}
				}
		return null;
	}

	static Community span(ServerLevel level, Village v, BlockPos w, int dir) {
		Direction along = dir == 0 ? Direction.EAST : Direction.SOUTH;
		BlockPos a = w;
		int n = 0;
		while (n < 14 && level.isLoaded(a.relative(along.getOpposite())) && level.getBlockState(a.relative(along.getOpposite())).is(Blocks.WATER)) {
			a = a.relative(along.getOpposite());
			n++;
		}
		BlockPos bank = a.relative(along.getOpposite());
		if (!level.isLoaded(bank) || !Ground.natural(level.getBlockState(bank))) return null;
		int len = 0;
		BlockPos p = a;
		while (len < 14 && level.isLoaded(p) && level.getBlockState(p).is(Blocks.WATER)) {
			p = p.relative(along);
			len++;
		}
		if (len < 3 || len > 12 || !level.isLoaded(p) || !Ground.natural(level.getBlockState(p))) return null;
		BlockPos deck = a.above();
		Direction side = along.getClockWise();
		for (int i = 0; i < len; i++)
			for (int s = -1; s <= 1; s++) {
				BlockPos q = deck.relative(along, i).relative(side, s);
				if (!v.covers(q.getX(), q.getZ()) || !Ground.mayBuild(level, q) || !Ground.mayBuild(level, q.above())) return null;
			}
		return new Community(Community.BRIDGE, deck, dir, len);
	}

	// ---------------------------------------------------------------- doar

	/** {@code /vila obra doar}: tira do inventário o que falta (só isso), dá reputação (teto por dia) e começa a obra quando estiver tudo. */
	public static VillageManager.Result donate(ServerPlayer p, Village v) {
		ServerLevel level = p.level();
		Community c = lore(v).project;
		if (c == null || c.state != Community.COLLECTING) return VillageManager.Result.fail("tvilas.project060.none");
		Inventory inv = p.getInventory();
		int total = 0;
		for (var e : c.needs.entrySet()) {
			Item item = VillageManager.item(e.getKey());
			int have = c.have.getOrDefault(e.getKey(), 0);
			int n = LoreRules.take(e.getValue(), have, VillageManager.count(inv, item));
			if (n <= 0) continue;
			VillageManager.take(inv, item, n);
			c.have.put(e.getKey(), have + n);
			total += n;
		}
		if (total == 0) return VillageManager.Result.fail("tvilas.project060.nothing");
		long day = VillageEconomy.day(level);
		String k = p.getUUID() + ":" + day;
		int today = c.repDay.getOrDefault(k, 0);
		int rep = LoreRules.donationRep(total, today);
		if (c.repDay.size() >= 64 && !c.repDay.containsKey(k)) c.repDay.clear();
		c.repDay.put(k, today + rep);
		if (rep > 0) v.reputation.merge(p.getUUID(), rep, Integer::sum);
		if (c.donors.size() < LoreRules.MAX_DONORS || c.donors.containsKey(p.getUUID())) {
			c.donors.put(p.getUUID(), p.getGameProfile().name());
			c.given.merge(p.getUUID(), total, Integer::sum);
		}
		donations++;
		if (c.complete()) start(level, v, c);
		VillageData.get(level).changed();
		return VillageManager.Result.ok("tvilas.project060.donated", total, rep);
	}

	public static int donations;

	/** Está tudo: o Construtor começa a obra. */
	public static void start(ServerLevel level, Village v, Community c) {
		c.state = Community.BUILDING;
		Project p = new Project(Project.Kind.COMMUNITY, c.site);
		p.pending.addAll(placements(c));
		p.total = p.pending.size();
		Builders.start(level, v, p);
		Chronicle.add(level, v, "project_started", "#tvilas.project060." + c.kind);
	}

	/** O Construtor acabou: a placa com os nomes de quem doou, a crónica e uma festa de palmas. */
	public static void finish(ServerLevel level, Village v, Project p) {
		Lore l = lore(v);
		Community c = l.project;
		if (c == null) return;
		c.state = Community.DONE;
		BlockPos sign = plaque(level, c);
		List<String> names = c.topDonors();
		String who = names.isEmpty() ? "-" : String.join(", ", names.subList(0, Math.min(3, names.size())));
		Chronicle.add(level, v, "project_done", "#tvilas.project060." + c.kind, who);
		VillageNews.announce(level, v, VillageNews.DONE, "tvilas.project060.done", Component.translatable("tvilas.project060." + c.kind), who);
		for (var vi : com.tcordeiro.vilas.life.Life.villagers(level, v))
			if (vi.distanceToSqr(c.site.getX(), c.site.getY(), c.site.getZ()) < 24 * 24) com.tcordeiro.vilas.life.Gestures.cheer(vi);
		l.project = null;
		l.projectsDone++;
		lastSign = sign;
		built++;
		VillageData.get(level).changed();
	}

	public static BlockPos lastSign;

	/** A placa: uma tabuleta de pé ao lado da obra, com o nome da obra e de quem mais doou. */
	static BlockPos plaque(ServerLevel level, Community c) {
		BlockPos[] tries = c.kind.equals(Community.BRIDGE) ? new BlockPos[] {c.site.relative(c.dir == 0 ? Direction.WEST : Direction.NORTH).above(),
				c.site.relative(c.dir == 0 ? Direction.WEST : Direction.NORTH)}
				: new BlockPos[] {c.site.offset(-1, 0, -1), c.site.offset(-1, 0, 2), c.site.offset(2, 0, -1), c.site.offset(-1, 0, 0)};
		for (BlockPos at : tries) {
			if (!Ground.mayBuild(level, at)) continue;
			level.setBlock(at, Blocks.OAK_SIGN.defaultBlockState().setValue(StandingSignBlock.ROTATION, 8), 3);
			if (level.getBlockEntity(at) instanceof SignBlockEntity s) {
				List<String> names = c.topDonors();
				Component[] lines = new Component[4];
				lines[0] = Component.translatable("tvilas.project060." + c.kind);
				for (int i = 0; i < 3; i++) {
					if (i == 2 && names.size() > 3) lines[3] = Component.translatable("tvilas.project060.more", names.size() - 2);
					else lines[i + 1] = Component.literal(i < names.size() ? names.get(i) : "");
				}
				for (int i = 0; i < 4; i++) if (lines[i] == null) lines[i] = Component.empty();
				SignText t = new SignText(List.of(lines), List.of(lines), net.minecraft.world.item.DyeColor.BLACK, true);
				s.setText(t, SignTextSlot.FRONT);
				s.setText(t, SignTextSlot.BACK);
				s.setWaxed(true);
				s.setChanged();
			}
			return at;
		}
		return null;
	}

	/** Para o quadro: «Obra da vila: Chafariz — pedra 10/16, …». */
	public static net.minecraft.network.chat.MutableComponent progress(Community c) {
		var line = Component.translatable("tvilas.project060.board", Component.translatable("tvilas.project060." + c.kind));
		for (var e : c.needs.entrySet()) {
			line.append(" · ").append(new ItemStack(VillageManager.item(e.getKey())).getHoverName()).append(" " + c.have.getOrDefault(e.getKey(), 0) + "/" + e.getValue());
		}
		if (c.state == Community.BUILDING) line.append(Component.translatable("tvilas.project060.building"));
		return line;
	}
}
