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
			Batch.CODEC.optionalFieldOf("batch").forGetter(c -> java.util.Optional.of(c.batch)),
			Codec.LONG.optionalFieldOf("mayor_seen", -1L).forGetter(c -> c.mayorSeen),
			UUIDUtil.STRING_CODEC.listOf().optionalFieldOf("retired", List.of()).forGetter(c -> List.copyOf(c.retired)),
			Codec.LONG.optionalFieldOf("mission_fund", 0L).forGetter(c -> c.missionFund),
			UUIDUtil.STRING_CODEC.optionalFieldOf("owner").forGetter(c -> java.util.Optional.ofNullable(c.owner)),
			Codec.unboundedMap(UUIDUtil.STRING_CODEC, Codec.string(0, 32)).optionalFieldOf("friends", Map.of()).forGetter(c -> c.friends),
			Codec.intRange(0, 1_000_000).optionalFieldOf("prestige", 0).forGetter(c -> c.prestige),
			Meta.CODEC.optionalFieldOf("meta").forGetter(c -> java.util.Optional.of(c.meta)))
			.apply(i, (m, t, b, s, bu, md, pt, dt, ms, rt, mf, ow, fr, pr, me) -> {
				Civic c = new Civic();
				c.mayor = m.orElse(null);
				c.mayorTaken = t;
				c.boxes.addAll(b);
				c.scanned = s;
				c.built.addAll(bu);
				c.missionDay = md;
				c.paidToday = Math.max(0, pt);
				c.batch = dt.orElseGet(Batch::new);
				c.mayorSeen = ms;
				c.retired.addAll(rt);
				c.missionFund = Math.max(0, mf);
				c.owner = ow.orElse(null);
				c.friends.putAll(fr);
				c.prestige = pr;
				c.meta = me.orElseGet(Meta::new);
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
	/** Missões: o dia de jogo das contas e quanto a vila já pagou hoje (teto diário). */
	public long missionDay = -1;
	public long paidToday;
	/** 0.3.0: o lote de missões (renova a cada 4 dias): o que cada jogador já fez e os pedidos dos aldeões. */
	public Batch batch = new Batch();

	/** Quando o Prefeito foi visto pela última vez (vivo, carregado, ainda Prefeito); −1 = nunca. */
	public long mayorSeen = -1;
	/** Antigos Prefeitos (sumidos 3 dias): quando voltarem a carregar perdem a profissão. */
	public final Set<UUID> retired = new HashSet<>();
	/**
	 * Fundo das missões (cêntimos): começa a 0; quando a vila é de verdade recebe 200 CB UMA vez e depois 50 CB por dia, até 400 CB
	 * ({@link com.tcordeiro.vilas.logic.FundRules}).
	 */
	public long missionFund;
	/**
	 * 0.3.0 (revisão de segurança): é uma vila de verdade (estrutura de vila do jogo ou ≥ 5 aldeões, revisto a cada 5 s pela Mesa)?
	 * Os 200 CB do fundo já entraram (uma vez por vila)? Quem foi dono (o dono nunca se perde por partir a Mesa).
	 */
	public static final class Meta {
		public static final Codec<Meta> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.BOOL.optionalFieldOf("real", false).forGetter(m -> m.real),
				Codec.BOOL.optionalFieldOf("fund_seeded", false).forGetter(m -> m.fundSeeded),
				UUIDUtil.STRING_CODEC.listOf().optionalFieldOf("owner_history", List.of()).forGetter(m -> List.copyOf(m.ownerHistory)))
				.apply(i, (r, f, h) -> {
					Meta m = new Meta();
					m.real = r;
					m.fundSeeded = f;
					m.ownerHistory.addAll(h);
					return m;
				}));

		public boolean real, fundSeeded;
		public final List<UUID> ownerHistory = new ArrayList<>();
	}

	public Meta meta = new Meta();

	/** 0.3.0: prestígio das missões concluídas (a cada {@code prestige_per_resident} chega um morador). */
	public int prestige;
	/** Quem pôs a Mesa (manda na área cívica, com os amigos); null = só os operadores (Mesas das vilas geradas). */
	public UUID owner;
	/** Amigos do dono da Mesa na área cívica: UUID → nome. */
	public final Map<UUID, String> friends = new HashMap<>();

	/**
	 * REGRA DE SEGURANÇA (revisão): quem põe a Mesa fica dono? Só se a vila nasceu agora com esta Mesa (nunca adotada), nunca teve
	 * dono e o jogador pode construir ali. Repor a Mesa numa vila que já existia não muda nada.
	 */
	public static boolean mayTakeOwnership(boolean villageExisted, Civic c, boolean mayBuildHere) {
		return !villageExisted && c.owner == null && c.meta.ownerHistory.isEmpty() && mayBuildHere;
	}

	/** REGRA DE SEGURANÇA (M15): pode este jogador mexer na área cívica? O dono da Mesa e os amigos dele (os operadores à parte). */
	public boolean mayBuild(UUID actor) {
		return actor != null && (actor.equals(owner) || friends.containsKey(actor));
	}

	/** O amigo com este nome (sem distinguir maiúsculas) ou UUID escrito, ou null. */
	public UUID friendByName(String name) {
		for (var e : friends.entrySet()) if (e.getValue().equalsIgnoreCase(name)) return e.getKey();
		try {
			UUID id = UUID.fromString(name);
			return friends.containsKey(id) ? id : null;
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

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
