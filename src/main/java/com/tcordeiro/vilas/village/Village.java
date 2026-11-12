package com.tcordeiro.vilas.village;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;

/**
 * Uma vila com Prefeitura: o centro é a Mesa do Prefeito. Guarda o nível, a população contada, a reputação de cada jogador, as 3
 * missões ativas com o progresso de cada jogador e o estado do alarme. Guardada no mundo ({@link VillageData}).
 */
public final class Village {
	/** Estado do alarme (golens chamados, tempos). Os tempos são o «game time» do mundo; −1 = nunca. */
	public static final class Alarm {
		public static final Codec<Alarm> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.BOOL.optionalFieldOf("on", false).forGetter(a -> a.on),
				Codec.INT.optionalFieldOf("golems", 0).forGetter(a -> a.golems),
				Codec.LONG.optionalFieldOf("last_golem", -1L).forGetter(a -> a.lastGolem),
				Codec.LONG.optionalFieldOf("last_danger", -1L).forGetter(a -> a.lastDanger),
				Codec.LONG.optionalFieldOf("ended", -1L).forGetter(a -> a.ended),
				UUIDUtil.STRING_CODEC.listOf().optionalFieldOf("called", List.of()).forGetter(a -> List.copyOf(a.called)))
				.apply(i, (on, g, lg, ld, e, c) -> {
					Alarm a = new Alarm();
					a.on = on;
					a.golems = g;
					a.lastGolem = lg;
					a.lastDanger = ld;
					a.ended = e;
					a.called.addAll(c);
					return a;
				}));

		public boolean on;
		public int golems;
		public long lastGolem = -1, lastDanger = -1, ended = -1;
		/** Os golens chamados por este alarme (vão-se embora depois). */
		public final List<UUID> called = new ArrayList<>();
	}

	/** Tesouraria: o que os impostos renderam, as dívidas de quem não pôde pagar e os gastos do Prefeito. */
	public static final class Economy {
		/** Um gasto do Prefeito: dia, o quê (chave de tradução) e quanto. */
		public record Expense(long day, String what, long cents) {
			public static final Codec<Expense> CODEC = RecordCodecBuilder.create(i -> i.group(
					Codec.LONG.fieldOf("day").forGetter(Expense::day), Codec.STRING.fieldOf("what").forGetter(Expense::what),
					Codec.LONG.fieldOf("cents").forGetter(Expense::cents)).apply(i, Expense::new));
		}

		public static final Codec<Economy> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.LONG.optionalFieldOf("treasury", 0L).forGetter(e -> e.treasury),
				Codec.LONG.optionalFieldOf("collected", 0L).forGetter(e -> e.collected),
				Codec.unboundedMap(UUIDUtil.STRING_CODEC, Codec.LONG).optionalFieldOf("debts", Map.of()).forGetter(e -> e.debts),
				Expense.CODEC.listOf().optionalFieldOf("expenses", List.of()).forGetter(e -> e.expenses),
				Codec.LONG.optionalFieldOf("last_day", -1L).forGetter(e -> e.lastDay),
				Codec.LONG.optionalFieldOf("last_mayor_call", -1L).forGetter(e -> e.lastMayorCall),
				Codec.INT.optionalFieldOf("alarm_bonus", 0).forGetter(e -> e.alarmBonus))
				.apply(i, (t, c, d, ex, ld, lm, ab) -> {
					Economy e = new Economy();
					e.treasury = t;
					e.collected = c;
					e.debts.putAll(d);
					e.expenses.addAll(ex);
					e.lastDay = ld;
					e.lastMayorCall = lm;
					e.alarmBonus = ab;
					return e;
				}));
		public static final int MAX_EXPENSES = 6;

		public long treasury, collected;
		public final Map<UUID, Long> debts = new HashMap<>();
		/** Os últimos gastos (mais recente no fim). */
		public final List<Expense> expenses = new ArrayList<>();
		public long lastDay = -1, lastMayorCall = -1;
		/** Golens extra por alarme comprados pela tesouraria. */
		public int alarmBonus;

		public void log(long day, String what, long cents) {
			expenses.add(new Expense(day, what, cents));
			while (expenses.size() > MAX_EXPENSES) expenses.remove(0);
		}
	}

	/**
	 * 0.2.0: obras, imposto ajustável, dívidas congeladas, mercador viajante e construtor (num campo à parte, para o codec da
	 * vila não passar dos 16 campos).
	 */
	public static final class Extra {
		public static final int WALL_NONE = 0, WALL_BUILDING = 1, WALL_DONE = 2, WALL_CANCELLED = 3;
		public static final Codec<Extra> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.INT.optionalFieldOf("structures", 0).forGetter(x -> x.structures),
				Codec.DOUBLE.optionalFieldOf("last_rate", -1.0).forGetter(x -> x.lastRate),
				Codec.unboundedMap(UUIDUtil.STRING_CODEC, Codec.LONG).optionalFieldOf("debt_since", Map.of()).forGetter(x -> x.debtSince),
				UUIDUtil.STRING_CODEC.listOf().optionalFieldOf("frozen", List.of()).forGetter(x -> List.copyOf(x.frozen)),
				UUIDUtil.STRING_CODEC.listOf().optionalFieldOf("warned", List.of()).forGetter(x -> List.copyOf(x.warned)),
				Project.CODEC.listOf().optionalFieldOf("projects", List.of()).forGetter(x -> x.projects),
				Codec.INT.optionalFieldOf("wall", WALL_NONE).forGetter(x -> x.wall),
				Codec.INT.optionalFieldOf("poles", 0).forGetter(x -> x.poles),
				UUIDUtil.STRING_CODEC.optionalFieldOf("merchant").forGetter(x -> java.util.Optional.ofNullable(x.merchant)),
				UUIDUtil.STRING_CODEC.optionalFieldOf("donkey").forGetter(x -> java.util.Optional.ofNullable(x.donkey)),
				Codec.LONG.optionalFieldOf("merchant_arrived", -1L).forGetter(x -> x.merchantArrived),
				Codec.LONG.optionalFieldOf("merchant_next", -1L).forGetter(x -> x.merchantNext),
				UUIDUtil.STRING_CODEC.optionalFieldOf("builder").forGetter(x -> java.util.Optional.ofNullable(x.builder)),
				Codec.BOOL.optionalFieldOf("lighting", false).forGetter(x -> x.lighting))
				.apply(i, (st, lr, ds, fr, wa, pr, w, po, me, dk, ma, mn, b, li) -> {
					Extra x = new Extra();
					x.structures = st;
					x.lastRate = lr;
					x.debtSince.putAll(ds);
					x.frozen.addAll(fr);
					x.warned.addAll(wa);
					x.projects.addAll(pr);
					x.wall = w;
					x.poles = po;
					x.merchant = me.orElse(null);
					x.donkey = dk.orElse(null);
					x.merchantArrived = ma;
					x.merchantNext = mn;
					x.builder = b.orElse(null);
					x.lighting = li;
					return x;
				}));

		/** Melhorias pagas pela tesouraria (sobem a taxa das trocas). */
		public int structures;
		/** A última taxa anunciada (para avisar quando muda). */
		public double lastRate = -1;
		/** Devedor → dia desde quando deve (≥ o mínimo). */
		public final Map<UUID, Long> debtSince = new HashMap<>();
		/** Devedores com os terrenos congelados (não constroem até pagar; nada é apagado). */
		public final Set<UUID> frozen = new HashSet<>();
		/** Devedores já avisados de que o terreno vai congelar. */
		public final Set<UUID> warned = new HashSet<>();
		/** Obras em curso (a primeira é a que o construtor está a fazer). */
		public final List<Project> projects = new ArrayList<>();
		public int wall = WALL_NONE;
		/** Postes postos pela iluminação pública. */
		public int poles;
		/** A iluminação pública já foi comprada? */
		public boolean lighting;
		public UUID merchant, donkey;
		public long merchantArrived = -1, merchantNext = -1;
		/** O aldeão construtor (enquanto há obra à vista). */
		public UUID builder;
		/** Não guardados: quando pôs o último bloco e desde quando anda a caminho. */
		public long lastStep = -1, walkingSince = -1;
	}

	public static final Codec<Village> CODEC = RecordCodecBuilder.create(i -> i.group(
			UUIDUtil.STRING_CODEC.fieldOf("id").forGetter(v -> v.id),
			BlockPos.CODEC.fieldOf("center").forGetter(v -> v.center),
			Codec.INT.fieldOf("radius").forGetter(v -> v.radius),
			Codec.INT.optionalFieldOf("level", 0).forGetter(v -> v.level),
			Codec.INT.optionalFieldOf("population", 0).forGetter(v -> v.population),
			Codec.unboundedMap(UUIDUtil.STRING_CODEC, Codec.INT).optionalFieldOf("reputation", Map.of()).forGetter(v -> v.reputation),
			Mission.CODEC.listOf().optionalFieldOf("missions", List.of()).forGetter(v -> v.missions),
			Codec.LONG.optionalFieldOf("mission_counter", 0L).forGetter(v -> v.missionCounter),
			Codec.STRING.listOf().optionalFieldOf("accepted", List.of()).forGetter(v -> List.copyOf(v.accepted)),
			Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("kills", Map.of()).forGetter(v -> v.kills),
			Codec.unboundedMap(Codec.STRING, Codec.LONG.listOf()).optionalFieldOf("placed", Map.of()).forGetter(v -> v.placed),
			Alarm.CODEC.optionalFieldOf("alarm").forGetter(v -> java.util.Optional.of(v.alarm)),
			Economy.CODEC.optionalFieldOf("economy").forGetter(v -> java.util.Optional.of(v.economy)),
			Codec.LONG.listOf().optionalFieldOf("paid_spots", List.of())
					.forGetter(v -> java.util.Arrays.stream(v.paidSpots.toArray()).boxed().toList()),
			Extra.CODEC.optionalFieldOf("extra").forGetter(v -> java.util.Optional.of(v.extra)))
			.apply(i, (id, c, r, lvl, pop, rep, ms, mc, acc, k, pl, al, ec, ps, ex) -> {
				Village v = new Village(id, c, r);
				v.level = lvl;
				v.population = pop;
				v.reputation.putAll(rep);
				v.missions.addAll(ms);
				v.missionCounter = mc;
				v.accepted.addAll(acc);
				v.kills.putAll(k);
				pl.forEach((key, list) -> v.placed.put(key, new ArrayList<>(list)));
				v.alarm = al.orElseGet(Alarm::new);
				v.economy = ec.orElseGet(Economy::new);
				v.paidSpots = com.tcordeiro.vilas.logic.PaidSpots.fromArray(ps.stream().mapToLong(Long::longValue).toArray());
				v.extra = ex.orElseGet(Extra::new);
				return v;
			}));

	public final UUID id;
	public BlockPos center;
	public final int radius;
	public int level;
	public int population;
	public final Map<UUID, Integer> reputation = new HashMap<>();
	/** As 3 casas do quadro (pela ordem de {@code MissionRules.SLOTS}). */
	public final List<Mission> missions = new ArrayList<>();
	public long missionCounter;
	/** «jogador:missão» aceites. */
	public final Set<String> accepted = new HashSet<>();
	/** «jogador:missão» → monstros mortos (defesa). */
	public final Map<String, Integer> kills = new HashMap<>();
	/** «jogador:missão» → posições (BlockPos.asLong) dos blocos postos (decorar). */
	public final Map<String, List<Long>> placed = new HashMap<>();
	public Alarm alarm = new Alarm();
	public Economy economy = new Economy();
	/** Posições já pagas por missões de decorar/construir: não voltam a contar (nem partindo e repondo, nem noutra missão). */
	public com.tcordeiro.vilas.logic.PaidSpots paidSpots = new com.tcordeiro.vilas.logic.PaidSpots();
	/** 0.2.0: obras, imposto, dívidas, mercador. */
	public Extra extra = new Extra();

	public Village(UUID id, BlockPos center, int radius) {
		this.id = id;
		this.center = center;
		this.radius = radius;
	}

	public static String key(UUID player, Mission m) {
		return player + ":" + m.id();
	}

	public int reputationOf(UUID player) {
		return reputation.getOrDefault(player, 0);
	}

	public int reputationSum() {
		int s = 0;
		for (int r : reputation.values()) s += Math.max(0, r);
		return s;
	}

	/** A posição (x, z) está dentro do raio (distância horizontal ao centro)? */
	public boolean covers(int x, int z) {
		long dx = x - center.getX(), dz = z - center.getZ();
		return dx * dx + dz * dz <= (long) radius * radius;
	}

	public double distSqr(int x, int z) {
		double dx = x - center.getX(), dz = z - center.getZ();
		return dx * dx + dz * dz;
	}

	/** Esquece o progresso de todos numa missão (quando é entregue e trocada). */
	void forget(Mission m) {
		String suffix = ":" + m.id();
		accepted.removeIf(k -> k.endsWith(suffix));
		kills.keySet().removeIf(k -> k.endsWith(suffix));
		placed.keySet().removeIf(k -> k.endsWith(suffix));
	}
}
