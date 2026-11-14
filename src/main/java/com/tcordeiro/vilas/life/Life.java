package com.tcordeiro.vilas.life;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.tcordeiro.vilas.VilasConfig;
import com.tcordeiro.vilas.block.MayorDeskBlockEntity;
import com.tcordeiro.vilas.build.Builders;
import com.tcordeiro.vilas.logic.LifeRules;
import com.tcordeiro.vilas.village.Village;
import com.tcordeiro.vilas.village.VillageData;
import com.tcordeiro.vilas.village.VillageEconomy;
import com.tcordeiro.vilas.village.VillageManager;
import com.tcordeiro.vilas.village.VillageNews;
import com.tcordeiro.vilas.village.VillagerRequests;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.phys.Vec3;

/**
 * 0.4.0 — A vida dos aldeões de uma vila, a cada segundo (chamada pela Mesa do Prefeito, que já trabalha a cada segundo). Só para
 * os aldeões DENTRO do território; nada global muda (horário vanilla, nomes, replanta, clique das trocas).
 * <ul>
 * <li>LOD: até 48 blocos de um jogador tudo; até 96 só o essencial e a cada 3 s; mais longe nada (e larga as tarefas).</li>
 * <li>Rotina: convívio na Praça/Taverna ao fim da tarde (sentados), deitar aos poucos, cama de casal para quem não tem cama,
 * chuva → Taverna, frio → lareira; festa; aprendizes; trabalho visível; lanterna à noite; conversas; reações; sino.</li>
 * <li>Numa invasão ou com o alarme ligado, nada disto: o vanilla (esconder-se, pânico) manda.</li>
 * </ul>
 */
public final class Life {
	private Life() {}

	/** Estado em memória de cada vila (não guardado): a lista de aldeões (refeita a cada 5 s), os sítios, os tempos de espera. */
	static final class Runtime {
		final List<Villager> villagers = new ArrayList<>();
		long listed = Long.MIN_VALUE, lastTalk, lastFirework, meetingReportAt = -1;
		int strokes, tick;
		final Places places = new Places();
		// 0.5.0: bancas do mercado (cenário), o pregão, o vendedor de fora e o jogo das crianças
		final List<net.minecraft.world.entity.Entity> stalls = new ArrayList<>();
		final List<BlockPos> stallAt = new ArrayList<>();
		boolean cried;
		long traderDay = Long.MIN_VALUE, nextGame;
		int played;
		Play.Game game;
	}

	static final Map<UUID, Runtime> RT = new HashMap<>();

	static Runtime rt(Village v) {
		return RT.computeIfAbsent(v.id, k -> new Runtime());
	}

	public static Places places(ServerLevel level, Village v) {
		Runtime r = rt(v);
		r.places.refresh(level, v);
		return r.places;
	}

	/** Força reler os sítios (testes, e quando uma obra cívica acaba). */
	public static void rescan(Village v) {
		rt(v).places.scanned = Long.MIN_VALUE;
	}

	/** Os aldeões da vila (sem o Construtor nem o Mercador da Vila); lista refeita no máximo a cada 5 s. */
	public static List<Villager> villagers(ServerLevel level, Village v) {
		Runtime r = rt(v);
		long now = level.getGameTime();
		if (r.listed == Long.MIN_VALUE || now - r.listed >= 100 || now < r.listed) {
			r.listed = now;
			r.villagers.clear();
			r.villagers.addAll(level.getEntitiesOfClass(Villager.class, VillageManager.box(v, level), e -> e.isAlive() && v.covers(e.getBlockX(), e.getBlockZ())
					&& !Builders.isBuilder(e) && !e.getUUID().equals(v.extra.merchant)));
		} else r.villagers.removeIf(e -> !e.isAlive() || e.isRemoved());
		return r.villagers;
	}

	/** Para os testes: refaz já a lista. */
	public static void relist(Village v) {
		rt(v).listed = Long.MIN_VALUE;
	}

	static long seed(Villager v) {
		return v.getUUID().getLeastSignificantBits() ^ v.getUUID().getMostSignificantBits();
	}

	// ---------------------------------------------------------------- o segundo da vila

	/** Medição do tempo (nanossegundos) gasto na vida das vilas, para o teste de desempenho. */
	public static long nanos, calls;

	public static void tick(ServerLevel level, Village v) {
		if (!VilasConfig.get().villager_life) return;
		long t0 = System.nanoTime();
		try {
			tick0(level, v);
		} finally {
			nanos += System.nanoTime() - t0;
			calls++;
		}
	}

	static void tick0(ServerLevel level, Village v) {
		Runtime r = rt(v);
		r.tick++;
		long now = level.getGameTime(), tod = LifeRules.timeOfDay(level.getOverworldClockTime()), day = VillageEconomy.day(level);
		Places places = places(level, v);
		List<Villager> all = villagers(level, v);
		List<ServerPlayer> players = new ArrayList<>();
		double far = (double) v.radius + LifeRules.REDUCED;
		for (ServerPlayer p : level.players())
			if (!p.isSpectator() && v.distSqr(p.getBlockX(), p.getBlockZ()) <= far * far) players.add(p);
		boolean danger = v.alarm.on || MayorDeskBlockEntity.raidActive(level, v);
		Festa.tidy(level, v);
		bell(level, v, r, places, tod);
		if (r.meetingReportAt >= 0 && now >= r.meetingReportAt) {
			r.meetingReportAt = -1;
			report(level, v);
		}
		if (r.tick % 2 == 0) Care.guardsTick(level, v);
		Helper.tick(level, v, now); // 0.5.0: os ajudantes contratados (antes dos outros: não ficam com outra tarefa)
		if (!danger) {
			Market.tick(level, v, r, places, all, players, now, tod); // 0.5.0: mercado semanal
			Play.tick(level, v, r, places, all, players, now, tod); // 0.5.0: as crianças brincam
		} else if (r.game != null) Play.stop(level, r);
		boolean festa = Festa.active(level, v);
		if (festa && !players.isEmpty() && now - r.lastFirework >= 160 && places.plaza() != null && near(players, places.plaza().center, LifeRules.FULL)) {
			r.lastFirework = now;
			Festa.firework(level, places.plaza().center);
		}
		for (Villager vi : all) {
			LifeVillager.Memo m = ((LifeVillager) vi).tvilas$memo();
			if (!m.init) {
				m.init = true;
				vi.setPathfindingMalus(PathType.RAIL, 16f); // evita andar pelos trilhos
			}
			Props.expire(vi, now);
			int lod = LifeRules.lod(nearestSq(players, vi));
			Task t = Tasks.get(vi);
			if (lod == LifeRules.LOD_NONE) {
				if (t != null) Tasks.end(level, vi, t, "far");
				if (m.lantern) Props.clear(vi);
				continue;
			}
			if (danger) {
				if (t != null && t.kind != Task.Kind.FLEE) Tasks.end(level, vi, t, "danger");
				continue;
			}
			if (lod == LifeRules.LOD_REDUCED && Math.floorMod(vi.getId() + r.tick, 3) != 0) continue;
			if (t != null) {
				check(level, v, vi, t, tod, festa);
				continue;
			}
			if (vi.isPassenger() && Seats.isSeat(vi.getVehicle())) { // sentado sem tarefa (ex.: o mundo recarregou): levanta-se
				vi.stopRiding();
				continue;
			}
			if (vi.isSleeping() || vi.isTrading() || vi.isPassenger()) continue;
			try {
				decide(level, v, r, places, all, vi, m, lod, now, tod, day, festa);
			} catch (RuntimeException e) {
				com.tcordeiro.vilas.TVilas.LOGGER.warn("Vilas: vida do aldeão {} falhou: {}", vi.getUUID(), e.toString());
			}
		}
		if (!danger) {
			for (ServerPlayer p : players) if (v.covers(p.getBlockX(), p.getBlockZ())) reactions(level, v, places, all, p, now, day);
			conversations(level, v, r, places, all, players, now); // depois das reações: as boas-vindas não perdem o aldeão para uma conversa
		}
	}

	static boolean near(List<ServerPlayer> players, BlockPos pos, int dist) {
		for (ServerPlayer p : players) if (p.blockPosition().closerThan(pos, dist)) return true;
		return false;
	}

	static double nearestSq(List<ServerPlayer> players, Villager v) {
		double best = Double.MAX_VALUE;
		for (ServerPlayer p : players) best = Math.min(best, p.distanceToSqr(v));
		return best;
	}

	/** Uma tarefa em curso que já não faz sentido acaba (parou de chover, acabou a hora do convívio, é dia, acabou a festa). */
	static void check(ServerLevel level, Village v, Villager vi, Task t, long tod, boolean festa) {
		switch (t.kind) {
			case SHELTER -> {
				if (!Climate.raining(level) && !"cold".equals(t.icon)) Tasks.end(level, vi, t, "done");
			}
			case SOCIAL -> {
				if (!LifeRules.socialTime(tod, seed(vi))) Tasks.end(level, vi, t, "bedtime");
			}
			case SLEEP -> {
				if (!LifeRules.night(tod)) Tasks.end(level, vi, t, "morning");
			}
			case FESTA -> {
				if (!festa) Tasks.end(level, vi, t, "done");
			}
			case MARKET -> {
				if (!Market.active(level, v)) Tasks.end(level, vi, t, "market_over");
			}
			case FISH, DELIVER -> {
				if (!LifeRules.workTime(tod)) Tasks.end(level, vi, t, "after_work");
			}
			default -> {}
		}
	}

	// ---------------------------------------------------------------- decidir o que cada um faz

	static void decide(ServerLevel level, Village v, Runtime r, Places places, List<Villager> all, Villager vi, LifeVillager.Memo m, int lod, long now,
			long tod, long day, boolean festa) {
		LifeData d = v.civic.life;
		RandomSource rnd = level.getRandom();
		if (vi.isBaby()) {
			apprentice(level, v, places, all, vi, now, tod);
			return;
		}
		if (d.apprentices.containsKey(vi.getUUID())) graduate(level, v, vi);
		if (Helper.isHelper(vi)) return; // 0.5.0: contratado — o contrato manda
		long seed = seed(vi);
		BlockPos head = vi.blockPosition().above();
		boolean outdoors = level.canSeeSky(head);
		boolean night = LifeRules.night(tod);
		// chuva → Taverna (ou Prefeitura)
		if (!night && outdoors && now >= m.nextShelter && Climate.raining(level, head)) {
			Places.Spot s = places.tavern() != null && !places.tavern().indoor.isEmpty() ? places.tavern() : places.hall();
			if (s != null && !s.indoor.isEmpty()) {
				m.nextShelter = now + 3000;
				startShelter(level, places, vi, s, now, Balloons.RAIN, "rain", rnd);
				return;
			}
		}
		// frio → lareira/fogueira acesa (ou Prefeitura)
		if (!night && outdoors && now >= m.nextShelter && LifeRules.cold(Climate.celsius(level, vi.blockPosition(), tod))) {
			Places.Spot fire = null;
			for (Places.Spot s : places.spots.values()) if (!s.fires.isEmpty()) fire = s;
			m.nextShelter = now + 3000;
			if (fire != null) {
				BlockPos f = Places.any(fire.fires, rnd);
				Task t = new Task(Task.Kind.SHELTER, now, now + 1800).to(f, 2.6).speed(0.55);
				t.icon = Balloons.COLD;
				t.places = places;
				Tasks.start(level, vi, t);
				t.icon = "cold";
				return;
			}
			Places.Spot hall = places.hall() != null ? places.hall() : places.tavern();
			if (hall != null && !hall.indoor.isEmpty()) {
				startShelter(level, places, vi, hall, now, Balloons.COLD, "cold", rnd);
				return;
			}
		}
		// festa na Praça
		if (festa && places.plaza() != null) {
			Places.Spot p = places.plaza();
			Task t = new Task(Task.Kind.FESTA, now, now + Math.max(40, LifeRules.FESTA_END - tod)).to(p.center, 2.5).speed(0.55);
			t.fallback = p.center;
			t.target = p.stand.isEmpty() ? p.center : Places.any(p.stand, rnd);
			t.icon = Balloons.PARTY;
			t.places = places;
			Tasks.start(level, vi, t);
			return;
		}
		// convívio ao fim da tarde (até à hora de se deitar de cada um)
		if (LifeRules.socialTime(tod, seed) && now >= m.nextSocial) {
			m.nextSocial = now + 600;
			Places.Spot s = Climate.raining(level) || Math.floorMod(seed, 2) == 0 ? places.tavern() : places.plaza();
			if (s == null) s = places.plaza() != null ? places.plaza() : places.tavern();
			if (s != null) {
				Task t = new Task(Task.Kind.SOCIAL, now, now + Math.max(40, LifeRules.bedtime(seed) - tod)).speed(0.5);
				BlockPos seat = places.freeSeat(level, s, rnd);
				BlockPos stand = !s.indoor.isEmpty() && s == places.tavern() ? Places.any(s.indoor, rnd) : s.stand.isEmpty() ? s.center : Places.any(s.stand, rnd);
				t.fallback = stand;
				if (seat != null) {
					places.reserved.put(seat, vi.getUUID());
					t.seat = seat;
					t.to(seat, 1.3);
				} else t.to(stand, 2.5);
				t.places = places;
				Tasks.start(level, vi, t);
				return;
			}
		}
		// quem não tem cama dorme na cama de casal do Móveis (na Taverna/pousada), à sua hora
		if (night && (tod >= LifeRules.bedtime(seed) || tod < LifeRules.WAKE) && vi.getBrain().getMemory(MemoryModuleType.HOME).isEmpty()) {
			BlockPos bed = places.freeBed(level, rnd);
			if (bed != null) {
				places.reserved.put(bed, vi.getUUID());
				long until = Math.floorMod(LifeRules.WAKE - tod, LifeRules.DAY);
				Task t = new Task(Task.Kind.SLEEP, now, now + Math.max(100, until)).to(bed, 1.8).speed(0.5);
				t.bed = bed;
				t.places = places;
				Tasks.start(level, vi, t);
				return;
			}
		}
		if (lod != LifeRules.LOD_FULL) {
			watchdog(vi, m);
			return;
		}
		// clérigo e ferreiros (a cada 5 s)
		if ((now / 20 + vi.getId()) % 5 == 0) {
			if (Care.cleric(vi) && Care.tryHeal(level, v, places, vi, all)) return;
			if (Care.smith(vi) && Care.tryRepair(level, v, places, vi)) return;
		}
		// 0.5.0: o Carteiro entrega as cartas; o pescador vai pescar
		if (Mail.tryDeliver(level, v, places, vi, m, now, tod)) return;
		if (Fishing.tryFish(level, v, places, vi, m, now, tod)) return;
		work(level, vi, m, now, rnd);
		lantern(level, vi, m, tod, outdoors);
		watchdog(vi, m);
	}

	/** Para os testes: decide o que este aldeão faz a uma hora dada (sem LOD nem relógio do mundo). */
	public static void decideAt(ServerLevel level, Village v, Villager vi, long tod, boolean festa) {
		Runtime r = rt(v);
		decide(level, v, r, places(level, v), villagers(level, v), vi, ((LifeVillager) vi).tvilas$memo(), LifeRules.LOD_FULL, level.getGameTime(), tod,
				VillageEconomy.day(level), festa);
	}

	/** Para os testes: as verificações de uma tarefa em curso a uma hora dada. */
	public static void checkAt(ServerLevel level, Village v, Villager vi, long tod, boolean festa) {
		Task t = Tasks.get(vi);
		if (t != null) check(level, v, vi, t, tod, festa);
	}

	static void startShelter(ServerLevel level, Places places, Villager vi, Places.Spot s, long now, String balloon, String why, RandomSource rnd) {
		Task t = new Task(Task.Kind.SHELTER, now, now + 2400).speed(0.6);
		BlockPos seat = places.freeSeat(level, s, rnd);
		BlockPos in = Places.any(s.indoor, rnd);
		t.fallback = in;
		if (seat != null) {
			places.reserved.put(seat, vi.getUUID());
			t.seat = seat;
			t.to(seat, 1.3);
		} else t.to(in, 0.7);
		t.places = places;
		t.icon = balloon;
		Tasks.start(level, vi, t);
		t.icon = why;
	}

	/** Vigia anti-preso dos aldeões que andam pelo vanilla: 3 s sem andar com um destino → refaz o caminho; 6 s → larga o destino. */
	static void watchdog(Villager vi, LifeVillager.Memo m) {
		var brain = vi.getBrain();
		if (!brain.hasMemoryValue(MemoryModuleType.WALK_TARGET)) {
			m.still = 0;
			return;
		}
		double dx = vi.getX() - m.wx, dy = vi.getY() - m.wy, dz = vi.getZ() - m.wz;
		if (dx * dx + dy * dy + dz * dz < LifeRules.STUCK_MOVE * LifeRules.STUCK_MOVE) m.still++;
		else m.still = 0;
		m.wx = vi.getX();
		m.wy = vi.getY();
		m.wz = vi.getZ();
		if (m.still == 3) {
			brain.eraseMemory(MemoryModuleType.PATH);
			vi.getNavigation().stop();
			unstuck++;
		} else if (m.still >= 6) {
			brain.eraseMemory(MemoryModuleType.WALK_TARGET);
			brain.eraseMemory(MemoryModuleType.PATH);
			m.still = 0;
			unstuck++;
		}
	}

	/** Para os testes: quantas vezes o vigia soltou um aldeão preso. */
	public static int unstuck;

	// ---------------------------------------------------------------- aprendizes

	static void apprentice(ServerLevel level, Village v, Places places, List<Villager> all, Villager kid, long now, long tod) {
		LifeData d = v.civic.life;
		String prof = d.apprentices.get(kid.getUUID());
		if (prof == null) {
			List<Villager> masters = new ArrayList<>();
			for (Villager o : all)
				if (!o.isBaby() && master(o)) masters.add(o);
			if (masters.isEmpty()) return;
			masters.sort((a, b) -> Double.compare(a.distanceToSqr(kid), b.distanceToSqr(kid)));
			prof = VillagerRequests.profession(masters.get(0));
			LifeData.putCapped(d.apprentices, kid.getUUID(), prof);
			VillageData.get(level).changed();
		}
		if (!LifeRules.workTime(tod)) return;
		Villager master = null;
		double best = 48 * 48;
		for (Villager o : all)
			if (!o.isBaby() && VillagerRequests.profession(o).equals(prof) && o.distanceToSqr(kid) < best) {
				best = o.distanceToSqr(kid);
				master = o;
			}
		if (master == null || best < 5 * 5) return;
		Task t = new Task(Task.Kind.FOLLOW, now, now + 400).to(master.blockPosition(), 3).speed(0.6);
		t.follow = master;
		t.places = places;
		Tasks.start(level, kid, t);
	}

	/** Pode ser mestre: tem profissão de trabalho (não o Prefeito, o Construtor, desempregado nem o «nitwit»). */
	static boolean master(Villager o) {
		var p = o.getVillagerData().profession();
		return !p.is(VillagerProfession.NONE) && !p.is(VillagerProfession.NITWIT) && !VillageManager.isMayor(o) && !Builders.isBuilder(o);
	}

	/** O aprendiz cresceu: fica com a profissão do mestre (nível 1, com 1 de XP para não a perder sem posto). */
	public static boolean graduate(ServerLevel level, Village v, Villager vi) {
		String prof = v.civic.life.apprentices.remove(vi.getUUID());
		VillageData.get(level).changed();
		if (prof == null || !vi.getVillagerData().profession().is(VillagerProfession.NONE)) return false;
		Identifier id = prof.contains(":") ? Identifier.parse(prof) : Identifier.withDefaultNamespace(prof);
		ResourceKey<VillagerProfession> key = ResourceKey.create(Registries.VILLAGER_PROFESSION, id);
		if (level.registryAccess().lookupOrThrow(Registries.VILLAGER_PROFESSION).get(key).isEmpty()) {
			// as profissões deste mod e dos irmãos têm outro espaço de nomes
			key = ResourceKey.create(Registries.VILLAGER_PROFESSION, com.tcordeiro.vilas.TVilas.id(prof));
			if (level.registryAccess().lookupOrThrow(Registries.VILLAGER_PROFESSION).get(key).isEmpty()) return false;
		}
		vi.setVillagerData(vi.getVillagerData().withProfession(level.registryAccess(), key).withLevel(1));
		vi.setVillagerXp(Math.max(1, vi.getVillagerXp()));
		Balloons.show(vi, Balloons.PARTY, 60);
		Talk.fact(level, v, vi.blockPosition(), "apprentice", prof);
		graduated++;
		return true;
	}

	public static int graduated;

	// ---------------------------------------------------------------- trabalho visível, lanterna

	/** Na bancada (atividade TRABALHO, a 2,5 blocos do posto): item da profissão na mão, partículas no posto e o som, de 3 a 7 s. */
	static void work(ServerLevel level, Villager vi, LifeVillager.Memo m, long now, RandomSource rnd) {
		if (now < m.nextWork || !vi.getBrain().isActive(Activity.WORK)) return;
		var job = vi.getBrain().getMemory(MemoryModuleType.JOB_SITE);
		if (job.isEmpty() || job.get().dimension() != level.dimension()) return;
		BlockPos site = job.get().pos();
		if (vi.distanceToSqr(Vec3.atCenterOf(site)) > 2.5 * 2.5) return;
		m.nextWork = now + 60 + rnd.nextInt(80);
		String prof = VillagerRequests.profession(vi);
		Item item = workItem(prof);
		if (item != null) Props.hold(vi, new ItemStack(item), 40);
		ParticleOptions part = workParticle(prof);
		if (part != null) level.sendParticles(part, site.getX() + 0.5, site.getY() + 1.05, site.getZ() + 0.5, 5, 0.25, 0.1, 0.25, 0.01);
		vi.getLookControl().setLookAt(site.getX() + 0.5, site.getY() + 0.8, site.getZ() + 0.5);
		vi.playWorkSound();
		// 0.5.1: quem bate (ferreiros, pedreiro, marceneiro, mecânico) faz o gesto de martelar
		if (prof.endsWith("smith") || prof.equals("armorer") || prof.equals("mason") || prof.equals("carpenter") || prof.equals("mechanic"))
			Balloons.gesture(vi, Gestures.HAMMER, 40);
		works++;
	}

	public static int works;

	static Item workItem(String prof) {
		return switch (prof) {
			case "armorer" -> Items.IRON_INGOT;
			case "weaponsmith" -> Items.IRON_SWORD;
			case "toolsmith", "mechanic" -> Items.IRON_PICKAXE;
			case "librarian", "mayor" -> Items.BOOK;
			case "cleric" -> Items.GLASS_BOTTLE;
			case "farmer" -> Items.WHEAT;
			case "fisherman" -> Items.FISHING_ROD;
			case "fletcher" -> Items.ARROW;
			case "shepherd" -> Items.SHEARS;
			case "leatherworker" -> Items.LEATHER;
			case "mason" -> Items.BRICK;
			case "cartographer" -> Items.PAPER;
			case "butcher" -> Items.BEEF;
			case "baker" -> Items.BREAD;
			case "florist" -> Items.POPPY;
			case "innkeeper" -> Items.HONEY_BOTTLE;
			case "beekeeper" -> Items.HONEYCOMB;
			case "carpenter" -> Items.STICK;
			case "alloy_smith" -> Items.COPPER_INGOT;
			case "postman" -> Items.PAPER;
			default -> null;
		};
	}

	static ParticleOptions workParticle(String prof) {
		return switch (prof) {
			case "armorer", "weaponsmith", "toolsmith", "alloy_smith", "mechanic" -> ParticleTypes.ELECTRIC_SPARK;
			case "librarian", "cartographer", "mayor" -> ParticleTypes.ENCHANT;
			case "cleric" -> ParticleTypes.WITCH;
			case "baker", "butcher" -> ParticleTypes.SMOKE;
			case "fisherman" -> ParticleTypes.SPLASH;
			case "farmer", "florist", "beekeeper" -> ParticleTypes.HAPPY_VILLAGER;
			default -> null;
		};
	}

	/** À noite, quem anda lá fora leva uma lanterna na mão (a camada vanilla dos braços desenha-a). */
	static void lantern(ServerLevel level, Villager vi, LifeVillager.Memo m, long tod, boolean outdoors) {
		boolean dark = tod >= 12500 || tod < 300;
		if (dark && outdoors && !vi.isSleeping()) {
			if (!m.lantern && Props.hold(vi, new ItemStack(Items.LANTERN), 600)) m.lantern = true;
		} else if (m.lantern) Props.clear(vi);
	}

	// ---------------------------------------------------------------- conversas

	static void conversations(ServerLevel level, Village v, Runtime r, Places places, List<Villager> all, List<ServerPlayer> players, long now) {
		if (now - r.lastTalk < LifeRules.VILLAGE_TALK_COOLDOWN || players.isEmpty()) return;
		for (int i = 0; i < all.size(); i++) {
			Villager a = all.get(i);
			if (!talkable(a, now) || nearestSq(players, a) > 24 * 24) continue;
			for (int j = i + 1; j < all.size(); j++) {
				Villager b = all.get(j);
				if (!talkable(b, now) || a.distanceToSqr(b) > 3.5 * 3.5) continue;
				r.lastTalk = now;
				Talk.start(level, v, places, a, b);
				return;
			}
		}
	}

	static boolean talkable(Villager a, long now) {
		return a.isAlive() && !a.isBaby() && !a.isSleeping() && !a.isTrading() && !a.isPassenger() && Tasks.get(a) == null
				&& now >= ((LifeVillager) a).tvilas$memo().nextTalk;
	}

	// ---------------------------------------------------------------- reações a um jogador

	static boolean weapon(ItemStack s) {
		return s.is(ItemTags.SWORDS) || s.is(ItemTags.AXES) || s.is(Items.MACE) || s.is(Items.TRIDENT) || s.is(Items.BOW) || s.is(Items.CROSSBOW);
	}

	static void reactions(ServerLevel level, Village v, Places places, List<Villager> all, ServerPlayer p, long now, long day) {
		LifeData d = v.civic.life;
		// boas-vindas a quem esteve fora 3 dias ou mais
		Long seen = d.lastSeen.get(p.getUUID());
		if (seen == null || seen != day) {
			if (seen != null && LifeRules.welcome(seen, day)) welcome(level, places, all, p, now);
			LifeData.putCapped(d.lastSeen, p.getUUID(), day);
			VillageData.get(level).changed();
		}
		if (!v.civic.life.town.election.mandate.pending.isEmpty()) com.tcordeiro.vilas.life.election.VoteBonus.deliver(level, v, p); // 0.6 eleições: bolo e broche guardados
		boolean armed = weapon(p.getMainHandItem());
		boolean armored = p.getArmorValue() >= 15;
		boolean thanked = false;
		for (Villager vi : all) {
			double d2 = vi.distanceToSqr(p);
			if (d2 > 6 * 6 || vi.isBaby() || vi.isSleeping() || vi.isTrading() || Tasks.get(vi) != null) continue;
			LifeVillager.Memo m = ((LifeVillager) vi).tvilas$memo();
			int tier = LifeRules.villagerTier(vi.getPlayerReputation(p));
			if (!thanked && d2 < 5 * 5) thanked = Resident.maybeThank(level, v, vi, p);
			if (now < m.nextReact) continue;
			if (armed && d2 < 4 * 4 && tier < LifeRules.TIER_FRIEND) {
				m.nextReact = now + 600;
				Vec3 away = vi.position().subtract(p.position()).multiply(1, 0, 1).normalize().scale(2.5);
				Task t = new Task(Task.Kind.GO, now, now + 50).to(BlockPos.containing(vi.position().add(away)), 0.8).speed(0.55);
				t.places = places;
				t.icon = Balloons.QUESTION;
				Tasks.start(level, vi, t);
				Gestures.glance(vi, p);
				Gestures.shrug(vi); // 0.5.0: gesto EMF
				if (tier == LifeRules.TIER_DISTRUST) Gestures.no(vi);
				reacts++;
			} else if (armored && !armed && d2 < 5 * 5 && tier >= LifeRules.TIER_FRIENDLY) {
				m.nextReact = now + 1200;
				Gestures.glance(vi, p);
				Gestures.nod(level, vi);
				Balloons.say(vi, Balloons.WAVE, Component.translatable("tvilas.talk.armor"), 50);
				Gestures.wave(vi);
				reacts++;
			}
		}
	}

	public static int reacts, welcomes;

	/** Para os testes: as reações dos aldeões da vila a este jogador, agora. */
	public static void reactionsAt(ServerLevel level, Village v, ServerPlayer p) {
		reactions(level, v, places(level, v), villagers(level, v), p, level.getGameTime(), VillageEconomy.day(level));
	}

	static void welcome(ServerLevel level, Places places, List<Villager> all, ServerPlayer p, long now) {
		Villager best = null;
		double bd = 24 * 24;
		for (Villager vi : all)
			if (!vi.isBaby() && !vi.isSleeping() && Tasks.get(vi) == null && vi.distanceToSqr(p) < bd
					&& LifeRules.villagerTier(vi.getPlayerReputation(p)) >= LifeRules.TIER_FRIENDLY) {
				bd = vi.distanceToSqr(p);
				best = vi;
			}
		if (best == null) return;
		Task t = new Task(Task.Kind.WELCOME, now, now + 300).to(p.blockPosition(), 2.5).speed(0.6);
		t.follow = p;
		t.places = places;
		t.icon = Balloons.WAVE;
		Tasks.start(level, best, t);
	}

	/** O aldeão chegou ao jogador que voltou: olha, «sim», coração e uma fala. */
	static void welcomeArrived(ServerLevel level, Villager vi, Task t) {
		if (t.follow instanceof ServerPlayer p) {
			Tasks.look(vi, p);
			Gestures.nod(level, vi);
			Balloons.say(vi, Balloons.HEART, Component.translatable("tvilas.talk.welcome", p.getGameProfile().name()), 70);
			Gestures.wave(vi);
			welcomes++;
		}
		t.deadline = Math.min(t.deadline, level.getGameTime() + 60);
	}

	// ---------------------------------------------------------------- sino e reunião

	static void bell(ServerLevel level, Village v, Runtime r, Places places, long tod) {
		LifeData d = v.civic.life;
		long c = LifeRules.chime(tod, d.lastChime);
		if (c >= 0) {
			d.lastChime = c;
			r.strokes = LifeRules.strokes(c);
			VillageData.get(level).changed();
		}
		if (r.strokes > 0) {
			r.strokes--;
			// só o som (o toque vanilla do sino manda os aldeões esconder-se — isso fica para as invasões)
			if (places.bells.isEmpty()) level.playSound(null, v.center, SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 1.5f, 1.0f);
			for (BlockPos b : places.bells) level.playSound(null, b, SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 1.5f, 1.0f);
			chimes++;
		}
	}

	public static int chimes;

	/** O jogador convoca uma reunião na Praça (no quadro): os aldeões perto vão para lá e o Prefeito dá o relatório. */
	public static VillageManager.Result callMeeting(ServerPlayer p, Village v) {
		ServerLevel level = p.level();
		LifeData d = v.civic.life;
		long day = VillageEconomy.day(level);
		if (LifeRules.villageTier(v.reputationOf(p.getUUID())) < LifeRules.TIER_FRIENDLY && !p.permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER))
			return VillageManager.Result.fail("tvilas.meeting.no_rep");
		if (d.meetingDay == day) return VillageManager.Result.fail("tvilas.meeting.today");
		if (v.alarm.on || MayorDeskBlockEntity.raidActive(level, v)) return VillageManager.Result.fail("tvilas.meeting.danger");
		d.meetingDay = day;
		Places places = places(level, v);
		Places.Spot s = places.plaza();
		BlockPos at = s != null ? s.center : v.center;
		long now = level.getGameTime();
		int n = 0;
		RandomSource rnd = level.getRandom();
		for (Villager vi : villagers(level, v)) {
			if (vi.isBaby() || vi.isSleeping() || vi.distanceToSqr(Vec3.atCenterOf(at)) > 64 * 64) continue;
			Task t = new Task(Task.Kind.MEETING, now, now + 900).to(s != null && !s.stand.isEmpty() ? Places.any(s.stand, rnd) : at, 3).speed(0.6);
			t.places = places;
			t.icon = Balloons.EXCLAIM;
			Tasks.start(level, vi, t);
			n++;
		}
		rt(v).meetingReportAt = now + 300;
		ringNow(level, places, v);
		VillageData.get(level).changed();
		return VillageManager.Result.ok("tvilas.meeting.called", n);
	}

	static void ringNow(ServerLevel level, Places places, Village v) {
		rt(v).strokes = 3;
	}

	/** O relatório do Prefeito numa linha. */
	static void report(ServerLevel level, Village v) {
		LifeData d = v.civic.life;
		Component festa = d.festa.day >= 0 ? Component.translatable("tvilas.meeting.festa_yes") : Component.translatable("tvilas.meeting.festa_no");
		VillageNews.announce(level, v, "📢", "tvilas.meeting.report", VillageManager.money(v.economy.treasury), v.population,
				Component.translatable(com.tcordeiro.vilas.logic.VillageRules.levelKey(v.level)), d.guards.size(), festa);
		Villager mayor = v.civic.mayor != null && level.getEntity(v.civic.mayor) instanceof Villager m ? m : null;
		if (mayor != null) Balloons.say(mayor, Balloons.TALK, Component.translatable("tvilas.meeting.mayor"), 80);
	}

	// ---------------------------------------------------------------- uma vez por dia

	public static void daily(ServerLevel level, Village v, long day) {
		if (!VilasConfig.get().villager_life) return;
		LifeData d = v.civic.life;
		d.prune(day);
		Care.dailyGuards(level, v, places(level, v), day);
		if (v.extra.merchantNext == day + 1) Festa.schedule(level, v, day + 1, "merchant");
		Market.daily(level, v, day); // 0.5.0: mercado semanal (aviso na véspera)
		Election.daily(level, v, day); // 0.5.0: eleições (contar os votos de ontem, abrir as de hoje)
		com.tcordeiro.vilas.life.projects.Projects.daily(level, v, day); // 0.6.0: obra comunitária com doações
		VillageData.get(level).changed();
	}

	/** A vila subiu de nível: festa amanhã. */
	public static void levelUp(ServerLevel level, Village v) {
		if (!VilasConfig.get().villager_life) return;
		Festa.schedule(level, v, VillageEconomy.day(level) + 1, "level");
		Talk.fact(level, v, null, "level", "#" + com.tcordeiro.vilas.logic.VillageRules.levelKey(v.level));
	}

	/** O aldeão morreu ou foi descarregado com um item de cena: tira-o (nunca cai). */
	public static void cleanup(Villager v) {
		if (Props.holding(v)) v.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
		Task t = Tasks.get(v);
		if (t != null && t.prop != null) { // 0.5.0: a boia vai-se com ele
			Stage.remove(t.prop);
			t.prop = null;
		}
		if (v.level() instanceof ServerLevel sl) Helper.onDeath(sl, v); // 0.5.0: a bolsa do ajudante volta para quem o contratou
	}

	static GlobalPos unused() {
		return null;
	}
}
