package com.tcordeiro.vilas.village;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.tcordeiro.vilas.logic.LotRules;

import net.minecraft.core.UUIDUtil;

/**
 * 0.3.0: a área cívica (Mesa, Prefeitura, Praça, Mercado, Taverna — protegida como um terreno sem dono), os edifícios que a
 * vila já tem, o Prefeito (para saber se foi levado) e o limite diário das missões. Num campo à parte da vila (o codec da vila
 * fica nos 16 campos).
 */
public final class Civic {
	/** Os edifícios cívicos (a ordem é a das obras: o que falta primeiro). */
	public static final List<String> BUILDINGS = List.of("town_hall", "market", "tavern", "plaza");
	public static final String DESK = "desk";
	/** Lado máximo de uma área cívica guardada (um dado estragado não protege meio mundo). */
	public static final int MAX_SIDE = 64;

	/** Uma área cívica: o quê e o retângulo (toda a altura, como os terrenos). */
	public record Box(String kind, LotRules.Area area) {
		public static final Codec<Box> CODEC = RecordCodecBuilder.<Box>create(i -> i.group(
				Codec.string(1, 32).fieldOf("kind").forGetter(Box::kind),
				Codec.INT.fieldOf("min_x").forGetter(b -> b.area.minX()), Codec.INT.fieldOf("min_z").forGetter(b -> b.area.minZ()),
				Codec.INT.fieldOf("max_x").forGetter(b -> b.area.maxX()), Codec.INT.fieldOf("max_z").forGetter(b -> b.area.maxZ()))
				.apply(i, (k, x0, z0, x1, z1) -> new Box(k, new LotRules.Area(x0, z0, x1, z1)))).validate(Box::check);

		static DataResult<Box> check(Box b) {
			LotRules.Area a = b.area;
			if (a.maxX() < a.minX() || a.maxZ() < a.minZ() || a.maxX() - a.minX() >= MAX_SIDE || a.maxZ() - a.minZ() >= MAX_SIDE)
				return DataResult.error(() -> "área cívica inválida: " + a);
			return DataResult.success(b);
		}
	}

	public static final Codec<Civic> CODEC = RecordCodecBuilder.create(i -> i.group(
			UUIDUtil.STRING_CODEC.optionalFieldOf("mayor").forGetter(c -> java.util.Optional.ofNullable(c.mayor)),
			Codec.BOOL.optionalFieldOf("mayor_taken", false).forGetter(c -> c.mayorTaken),
			SafeCodecs.list(Box.CODEC, "área cívica").optionalFieldOf("boxes", List.of()).forGetter(c -> c.boxes),
			Codec.BOOL.optionalFieldOf("scanned", false).forGetter(c -> c.scanned),
			Codec.string(1, 32).listOf().optionalFieldOf("built", List.of()).forGetter(c -> List.copyOf(c.built)),
			Codec.LONG.optionalFieldOf("mission_day", -1L).forGetter(c -> c.missionDay),
			Codec.LONG.optionalFieldOf("paid_today", 0L).forGetter(c -> c.paidToday),
			Codec.unboundedMap(UUIDUtil.STRING_CODEC, Codec.intRange(0, 1000)).optionalFieldOf("done_today", Map.of()).forGetter(c -> c.doneToday))
			.apply(i, (m, t, b, s, bu, md, pt, dt) -> {
				Civic c = new Civic();
				c.mayor = m.orElse(null);
				c.mayorTaken = t;
				c.boxes.addAll(b);
				c.scanned = s;
				c.built.addAll(bu);
				c.missionDay = md;
				c.paidToday = Math.max(0, pt);
				c.doneToday.putAll(dt);
				return c;
			}));

	/** O Prefeito desta vila (o aldeão), ou null se ainda não se sabe / morreu. */
	public UUID mayor;
	/** O Prefeito foi levado da Prefeitura (vivo, longe): não chega outro; volta só pela Mesa. */
	public boolean mayorTaken;
	public final List<Box> boxes = new ArrayList<>();
	/** Já se viu que edifícios a vila tem (uma vez, quando a Mesa começa a trabalhar)? */
	public boolean scanned;
	/** Edifícios cívicos que a vila já tem (gerados com a vila ou construídos). */
	public final Set<String> built = new HashSet<>();
	/** Missões: o dia de jogo das contas, quanto a tesouraria já pagou hoje e quantas cada jogador já entregou hoje. */
	public long missionDay = -1;
	public long paidToday;
	public final Map<UUID, Integer> doneToday = new HashMap<>();

	public boolean covers(int x, int z) {
		for (Box b : boxes) if (b.area.contains(x, z)) return true;
		return false;
	}

	public boolean overlaps(LotRules.Area a) {
		for (Box b : boxes) if (b.area.overlaps(a)) return true;
		return false;
	}

	/** Põe (ou muda) a área com este nome. */
	public void put(String kind, LotRules.Area a) {
		boxes.removeIf(b -> b.kind.equals(kind));
		boxes.add(new Box(kind, a));
	}

	/** Os edifícios cívicos que ainda faltam, pela ordem das obras. */
	public List<String> missing() {
		List<String> out = new ArrayList<>();
		for (String k : BUILDINGS) if (!built.contains(k)) out.add(k);
		return out;
	}
}
