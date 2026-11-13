package com.tcordeiro.vilas.life;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.tcordeiro.vilas.VilasConfig;
import com.tcordeiro.vilas.block.MayorDeskBlockEntity;
import com.tcordeiro.vilas.logic.LifeRules;
import com.tcordeiro.vilas.village.Village;
import com.tcordeiro.vilas.village.VillageData;
import com.tcordeiro.vilas.village.VillageEconomy;
import com.tcordeiro.vilas.village.VillageManager;
import com.tcordeiro.vilas.village.VillageNews;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * 0.4.0 — Guardas, curas e pânico.
 * <ul>
 * <li><b>Guardas</b>: golens da vila em POSTOS FIXOS (Praça/Mesa, postes da iluminação, cantos da muralha), sem patrulha: se se
 * afastam mais de 6 blocos e não estão a lutar, voltam ao posto. Cada guarda custa {@code guard_wage_cents} por dia à TESOURARIA;
 * só fica de guarda quem a tesouraria consegue pagar (nunca fica negativa). Sem dinheiro, o posto fica vago (o golem continua a ser
 * um golem da vila).</li>
 * <li><b>Clérigo</b> cura feridos (Regeneração) e <b>ferreiros</b> (armeiro, ferramenteiro, armeiro de armas) reparam golens: 3 vezes
 * por dia cada um.</li>
 * <li><b>Pânico coletivo</b>: um aldeão atacado por um monstro avisa os vizinhos a 15 blocos, que fogem para o guarda/golem mais
 * perto (ou para um jogador amigo); o Fresh Animations mostra-os assustados porque correm.</li>
 * </ul>
 */
public final class Care {
	private Care() {}

	public static final String GUARD_TAG = "tvilas.guard";

	// ---------------------------------------------------------------- guardas

	/** Os postos desta vila: a Praça (ou a Mesa), os postes da iluminação e os cantos da muralha. */
	public static List<BlockPos> posts(ServerLevel level, Village v, Places places) {
		List<BlockPos> out = new ArrayList<>();
		Places.Spot plaza = places.plaza();
		out.add(plaza != null ? plaza.center : v.center);
		for (long a : v.extra.lightAnchors) {
			BlockPos p = BlockPos.of(a);
			out.add(new BlockPos(p.getX() + 1, Places.ground(level, p.getX() + 1, p.getZ(), p.getY()), p.getZ())); // ao lado do poste
			if (out.size() >= 6) break;
		}
		if (v.extra.wall == Village.Extra.WALL_DONE) {
			int h = v.radius / 2;
			for (int[] c : new int[][] {{-1, -1}, {1, 1}, {-1, 1}, {1, -1}}) {
				BlockPos p = v.center.offset(c[0] * (h - 3), 0, c[1] * (h - 3));
				out.add(new BlockPos(p.getX(), Places.ground(level, p.getX(), p.getZ(), v.center.getY()), p.getZ()));
			}
		}
		return out;
	}

	public static long wage() {
		return Math.max(0, VilasConfig.get().guard_wage_cents);
	}

	/**
	 * Uma vez por dia (pela {@code daily} da vila): paga os guardas que a tesouraria consegue (REGRA {@link LifeRules#guardsPaid}),
	 * escolhe golens para os postos que faltam e dispensa os que não se pagaram. Devolve quantos ficaram de guarda.
	 */
	public static int dailyGuards(ServerLevel level, Village v, Places places, long day) {
		LifeData d = v.civic.life;
		if (d.guardDay == day) return d.guards.size();
		d.guardDay = day;
		List<BlockPos> posts = posts(level, v, places);
		int wanted = Math.min(posts.size(), com.tcordeiro.vilas.logic.TownRules.guardPostsWithPolicy(LifeRules.guardPosts(v.level), Election.policy(v))); // 0.5.0: «mais guardas»
		List<IronGolem> golems = level.getEntitiesOfClass(IronGolem.class, new net.minecraft.world.phys.AABB(v.center).inflate(VillageEconomy.coreRadius(v)),
				g -> g.isAlive() && !g.entityTags().contains(MayorDeskBlockEntity.CALLED_TAG) && v.covers(g.getBlockX(), g.getBlockZ()));
		wanted = Math.min(wanted, golems.size());
		int paid = LifeRules.guardsPaid(v.economy.treasury, wage(), wanted);
		long cost = paid * wage();
		v.economy.treasury -= cost;
		if (cost > 0) v.economy.log(day, "tvilas.upgrade.guards", cost);
		// os guardas atuais primeiro (continuam no posto), depois os golens mais perto da Praça
		golems.sort((a, b) -> {
			boolean ga = d.guards.containsKey(a.getUUID()), gb = d.guards.containsKey(b.getUUID());
			if (ga != gb) return ga ? -1 : 1;
			return Double.compare(a.distanceToSqr(Vec3.atCenterOf(posts.get(0))), b.distanceToSqr(Vec3.atCenterOf(posts.get(0))));
		});
		Map<UUID, Long> next = new HashMap<>();
		for (int i = 0; i < paid; i++) {
			IronGolem g = golems.get(i);
			BlockPos post = posts.get(i);
			next.put(g.getUUID(), post.asLong());
			g.addTag(GUARD_TAG);
			g.setHomeTo(post, LifeRules.GUARD_LEASH);
		}
		for (UUID old : d.guards.keySet())
			if (!next.containsKey(old) && level.getEntity(old) instanceof IronGolem g) dismiss(g);
		boolean changed = !next.keySet().equals(d.guards.keySet());
		d.guards.clear();
		d.guards.putAll(next);
		if (changed && paid > 0) VillageNews.announce(level, v, VillageNews.DONE, "tvilas.news.guards", paid, VillageManager.money(cost));
		if (wanted > paid) VillageNews.announce(level, v, VillageNews.WARN, "tvilas.news.guards_unpaid", wanted - paid);
		VillageData.get(level).changed();
		return paid;
	}

	static void dismiss(IronGolem g) {
		g.removeTag(GUARD_TAG);
		g.clearHome();
	}

	/** A cada 2 s: o guarda que se afastou do posto (sem estar a lutar) volta para lá. Nada de patrulhas. */
	public static void guardsTick(ServerLevel level, Village v) {
		for (var e : v.civic.life.guards.entrySet()) {
			if (!(level.getEntity(e.getKey()) instanceof IronGolem g) || !g.isAlive()) continue;
			BlockPos post = BlockPos.of(e.getValue());
			if (g.getTarget() != null) continue;
			if (g.distanceToSqr(Vec3.atBottomCenterOf(post)) > LifeRules.GUARD_LEASH * LifeRules.GUARD_LEASH && g.getNavigation().isDone())
				g.getNavigation().moveTo(post.getX() + 0.5, post.getY(), post.getZ() + 0.5, 0.6);
			if (!g.hasHome()) g.setHomeTo(post, LifeRules.GUARD_LEASH);
		}
	}

	public static boolean isGuard(Entity e) {
		return e.entityTags().contains(GUARD_TAG);
	}

	// ---------------------------------------------------------------- clérigo e ferreiros

	/** Quantas curas/reparações cada aldeão fez hoje (em memória; não é economia). */
	static final Map<UUID, long[]> DONE = new HashMap<>();

	static int doneToday(Villager v, long day) {
		long[] c = DONE.get(v.getUUID());
		return c == null || c[0] != day ? 0 : (int) c[1];
	}

	/** Para os testes: conta uma cura/reparação feita hoje. */
	public static void countHeal(Villager v, long day) {
		count(v, day);
	}

	static void count(Villager v, long day) {
		long[] c = DONE.computeIfAbsent(v.getUUID(), k -> new long[] {day, 0});
		if (c[0] != day) {
			c[0] = day;
			c[1] = 0;
		}
		c[1]++;
		if (DONE.size() > 2048) DONE.clear();
	}

	public static boolean smith(Villager v) {
		var p = v.getVillagerData().profession();
		return p.is(VillagerProfession.ARMORER) || p.is(VillagerProfession.TOOLSMITH) || p.is(VillagerProfession.WEAPONSMITH);
	}

	public static boolean cleric(Villager v) {
		return v.getVillagerData().profession().is(VillagerProfession.CLERIC);
	}

	/** Procura alguém para curar (aldeão ferido ou jogador amigo com pouca vida, a 16 blocos) e manda o clérigo. */
	public static boolean tryHeal(ServerLevel level, Village v, Places places, Villager cleric, List<Villager> all) {
		long day = VillageEconomy.day(level);
		if (!LifeRules.mayHeal(doneToday(cleric, day))) return false;
		LivingEntity hurt = null;
		for (Villager o : all)
			if (o != cleric && o.isAlive() && o.getHealth() < o.getMaxHealth() - 2 && o.distanceToSqr(cleric) < 256) {
				hurt = o;
				break;
			}
		if (hurt == null)
			for (ServerPlayer p : level.players())
				if (p.distanceToSqr(cleric) < 256 && p.getHealth() < p.getMaxHealth() * 0.5f
						&& LifeRules.villagerTier(cleric.getPlayerReputation(p)) >= LifeRules.TIER_FRIENDLY) {
					hurt = p;
					break;
				}
		if (hurt == null) return false;
		long now = level.getGameTime();
		Task t = new Task(Task.Kind.HEAL, now, now + 400).to(hurt.blockPosition(), 2.2).speed(0.6);
		t.follow = hurt;
		t.places = places;
		t.icon = Balloons.HEAL;
		Tasks.start(level, cleric, t);
		return true;
	}

	/** Procura um golem com menos de 70 % de vida (a 24 blocos) e manda o ferreiro. */
	public static boolean tryRepair(ServerLevel level, Village v, Places places, Villager smith) {
		long day = VillageEconomy.day(level);
		if (!LifeRules.mayRepair(doneToday(smith, day))) return false;
		for (IronGolem g : level.getEntitiesOfClass(IronGolem.class, smith.getBoundingBox().inflate(24), g -> g.isAlive() && g.getTarget() == null
				&& g.getHealth() < g.getMaxHealth() * LifeRules.REPAIR_BELOW)) {
			long now = level.getGameTime();
			Task t = new Task(Task.Kind.REPAIR, now, now + 500).to(g.blockPosition(), 2.6).speed(0.6);
			t.follow = g;
			t.places = places;
			t.icon = Balloons.REPAIR;
			Tasks.start(level, smith, t);
			return true;
		}
		return false;
	}

	/** Chegou ao ferido: Regeneração II por 10 s, poção na mão, corações. */
	static void heal(ServerLevel level, Villager cleric, Task t) {
		if (t.follow instanceof LivingEntity le && le.isAlive()) {
			le.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 200, 1));
			Props.hold(cleric, new ItemStack(Items.POTION), 40);
			level.sendParticles(ParticleTypes.HAPPY_VILLAGER, le.getX(), le.getY() + 1.2, le.getZ(), 8, 0.4, 0.4, 0.4, 0.0);
			level.playSound(null, le.blockPosition(), SoundEvents.BREWING_STAND_BREW, SoundSource.NEUTRAL, 0.6f, 1.4f);
			Balloons.show(le instanceof Villager ? le : cleric, Balloons.HEART, 40);
			count(cleric, VillageEconomy.day(level));
			heals++;
		}
		Tasks.end(level, cleric, t, "done");
	}

	/** Chegou ao golem: +25 de vida (como um lingote), lingote na mão, som de reparar. */
	static void repair(ServerLevel level, Villager smith, Task t) {
		if (t.follow instanceof IronGolem g && g.isAlive()) {
			g.heal(LifeRules.REPAIR_AMOUNT);
			Props.hold(smith, new ItemStack(Items.IRON_INGOT), 40);
			level.playSound(null, g.blockPosition(), SoundEvents.IRON_GOLEM_REPAIR, SoundSource.NEUTRAL, 1.0f, 1.0f);
			level.sendParticles(ParticleTypes.WAX_OFF, g.getX(), g.getY() + 1.5, g.getZ(), 10, 0.5, 0.7, 0.5, 0.0);
			count(smith, VillageEconomy.day(level));
			repairs++;
		}
		Tasks.end(level, smith, t, "done");
	}

	/** Para os testes. */
	public static int heals, repairs, panics;

	// ---------------------------------------------------------------- pânico coletivo

	/**
	 * Um aldeão foi atacado por um monstro: os vizinhos a 15 blocos fogem para o guarda/golem mais perto (ou para um jogador amigo a
	 * 24 blocos); balão «!» vermelho. Devolve quantos fugiram.
	 */
	public static int panic(ServerLevel level, Village v, Places places, Villager victim, Entity attacker) {
		long now = level.getGameTime();
		int n = 0;
		Balloons.show(victim, Balloons.ALERT, 50);
		for (Villager o : Life.villagers(level, v)) {
			if (o == victim || !o.isAlive() || o.isSleeping() || o.distanceToSqr(victim) > LifeRules.PANIC_RADIUS * LifeRules.PANIC_RADIUS) continue;
			Entity safe = refuge(level, o);
			Task t = new Task(Task.Kind.FLEE, now, now + 160).speed(0.9);
			if (safe != null) {
				t.to(safe.blockPosition(), 2.5);
				t.follow = safe;
			} else { // ninguém: para longe do atacante, para o lado da Mesa
				Vec3 away = o.position().subtract(attacker.position()).normalize().scale(10);
				t.to(BlockPos.containing(o.position().add(away)), 2);
			}
			t.places = places;
			t.icon = Balloons.ALERT;
			Tasks.start(level, o, t);
			n++;
		}
		panics++;
		return n;
	}

	/** O refúgio mais perto: um guarda/golem a 32 blocos, ou um jogador «Simpático» ou mais a 24. */
	static Entity refuge(ServerLevel level, Villager o) {
		Entity best = null;
		double bd = Double.MAX_VALUE;
		for (ServerPlayer p : level.players())
			if (!p.isSpectator() && p.distanceToSqr(o) < 24 * 24 && LifeRules.villagerTier(o.getPlayerReputation(p)) >= LifeRules.TIER_FRIENDLY
					&& p.distanceToSqr(o) < bd) {
				bd = p.distanceToSqr(o);
				best = p;
			}
		if (best != null) return best;
		for (IronGolem g : level.getEntitiesOfClass(IronGolem.class, o.getBoundingBox().inflate(32), IronGolem::isAlive))
			if (g.distanceToSqr(o) < bd) {
				bd = g.distanceToSqr(o);
				best = g;
			}
		return best;
	}
}
