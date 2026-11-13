package com.tcordeiro.vilas.life;

import com.tcordeiro.vilas.logic.LifeRules;
import com.tcordeiro.vilas.logic.TownRules;
import com.tcordeiro.vilas.registry.VilasItems;
import com.tcordeiro.vilas.village.Village;
import com.tcordeiro.vilas.village.VillageData;
import com.tcordeiro.vilas.village.VillageEconomy;
import com.tcordeiro.vilas.village.VillageManager;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;

/**
 * 0.5.0 — O pescador (o Pescador do jogo) pesca de verdade nas horas de trabalho: vai à beira da água mais perto do posto (até 16
 * blocos), fica virado para ela com a cana na mão (item de cena) e lança a BOIA (um mostrador com o modelo do Vilas; NÃO é o anzol do
 * jogo, que precisa de um jogador). A boia balança; de vez em quando há um puxão (salpicos e som) e um peixe: aparece-lhe na mão e
 * REPÕE uma troca de venda de peixe dele (o jogador continua a pagar; no máximo {@link TownRules#CATCHES_PER_DAY} por dia).
 */
public final class Fishing {
	private Fishing() {}

	public static boolean isFisher(Villager v) {
		return v.getVillagerData().profession().is(VillagerProfession.FISHERMAN);
	}

	/** O sítio de pesca: em pé na margem ({@code bank}) virado para a água ({@code water}, à superfície). */
	public record Spot(BlockPos bank, BlockPos water, Direction dir) {}

	/** Procura a margem mais perto de {@code from} (raio 16, só uma vez de 5 em 5 minutos por pescador). */
	public static Spot find(ServerLevel level, BlockPos from) {
		Spot best = null;
		double bd = Double.MAX_VALUE;
		BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
		for (int dx = -16; dx <= 16; dx++)
			for (int dz = -16; dz <= 16; dz++)
				for (int dy = -3; dy <= 2; dy++) {
					m.set(from.getX() + dx, from.getY() + dy, from.getZ() + dz);
					if (!level.isLoaded(m) || !standable(level, m)) continue;
					for (Direction d : Direction.Plane.HORIZONTAL) {
						BlockPos w = m.relative(d).below();
						if (!surfaceWater(level, w)) continue;
						double dist = m.distSqr(from);
						if (dist < bd) {
							bd = dist;
							// a boia cai 2–3 blocos para dentro de água, se lá houver
							BlockPos far = w.relative(d, 2);
							best = new Spot(m.immutable(), surfaceWater(level, far) ? far : w, d);
						}
					}
				}
		return best;
	}

	static boolean standable(ServerLevel level, BlockPos p) {
		return level.getBlockState(p).isAir() && level.getBlockState(p.above()).isAir() && level.getBlockState(p.below()).isCollisionShapeFullBlock(level, p.below())
				&& level.getFluidState(p.below()).isEmpty();
	}

	static boolean surfaceWater(ServerLevel level, BlockPos p) {
		return level.isLoaded(p) && level.getFluidState(p).is(FluidTags.WATER) && level.getFluidState(p).isSource() && level.getBlockState(p.above()).isAir();
	}

	/** O pescador decide ir pescar? (horas de trabalho, tem margem perto, a pausa passou.) */
	public static boolean tryFish(ServerLevel level, Village v, Places places, Villager vi, LifeVillager.Memo m, long now, long tod) {
		if (!isFisher(vi) || !LifeRules.workTime(tod) || now < m.nextFish) return false;
		m.nextFish = now + 600; // sem margem: volta a ver daqui a 30 s
		if (m.fishSpot == null || now >= m.fishScan) {
			m.fishScan = now + 6000;
			BlockPos from = vi.getBrain().getMemory(MemoryModuleType.JOB_SITE).map(g -> g.pos()).orElse(vi.blockPosition());
			m.fishSpot = find(level, from);
			if (m.fishSpot == null && !from.equals(vi.blockPosition())) m.fishSpot = find(level, vi.blockPosition());
		}
		if (m.fishSpot == null) return false;
		RandomSource r = level.getRandom();
		long len = TownRules.FISH_MIN_TICKS + r.nextInt(TownRules.FISH_MAX_TICKS - TownRules.FISH_MIN_TICKS);
		// alcance largo: o caminho do jogo pára a 1 bloco do alvo (às vezes já dentro de água); chega quando está perto da margem
		Task t = new Task(Task.Kind.FISH, now, now + len).to(m.fishSpot.bank(), 1.5).speed(0.5);
		t.places = places;
		t.icon = Balloons.FISH;
		Tasks.start(level, vi, t);
		m.nextFish = now + len + TownRules.FISH_PAUSE;
		return true;
	}

	/** Chegou à margem: cana na mão e lança a boia. */
	static void arrive(ServerLevel level, Villager vi, Task t, long now) {
		Spot s = ((LifeVillager) vi).tvilas$memo().fishSpot;
		if (s == null || !surfaceWater(level, s.water()) || vi.isInWater()) {
			Tasks.end(level, vi, t, "no_water");
			return;
		}
		Props.hold(vi, new ItemStack(Items.FISHING_ROD), (int) Math.max(40, t.deadline - now));
		face(vi, s);
		BlockPos w = s.water();
		t.prop = Stage.spawn(level, VilasItems.BOBBER, w.getX() + 0.5, w.getY() + 0.95, w.getZ() + 0.5, 0.5f, 0);
		level.playSound(null, vi.blockPosition(), SoundEvents.FISHING_BOBBER_THROW, SoundSource.NEUTRAL, 0.5f, 0.6f);
		t.nextStage = now + 200 + level.getRandom().nextInt(300); // o primeiro puxão
		casts++;
	}

	static void face(Villager vi, Spot s) {
		BlockPos w = s.water();
		vi.getLookControl().setLookAt(w.getX() + 0.5, w.getY() + 0.9, w.getZ() + 0.5);
		float yaw = s.dir().toYRot();
		vi.setYRot(yaw);
		vi.setYBodyRot(yaw);
		vi.setYHeadRot(yaw);
	}

	/** À espera: a boia balança; no puxão, salpicos, som e um peixe. */
	static void stay(ServerLevel level, Villager vi, Task t, long now) {
		Spot s = ((LifeVillager) vi).tvilas$memo().fishSpot;
		if (s == null) return;
		face(vi, s);
		if (t.prop == null || !t.prop.isAlive()) return;
		BlockPos w = s.water();
		double baseY = w.getY() + 0.95;
		boolean bite = now >= t.nextStage && now < t.nextStage + 20;
		double y = bite ? baseY - 0.18 : baseY + Math.sin(now * 0.15) * 0.03;
		if (Tasks.every(t, now, 6)) t.prop.teleportTo(w.getX() + 0.5, y, w.getZ() + 0.5);
		if (now >= t.nextStage && now < t.nextStage + 2) {
			level.sendParticles(ParticleTypes.SPLASH, w.getX() + 0.5, baseY, w.getZ() + 0.5, 12, 0.2, 0.05, 0.2, 0.1);
			level.sendParticles(ParticleTypes.FISHING, w.getX() + 0.5, baseY, w.getZ() + 0.5, 6, 0.15, 0.02, 0.15, 0.05);
			level.playSound(null, w, SoundEvents.FISHING_BOBBER_SPLASH, SoundSource.NEUTRAL, 0.4f, 1.0f);
		}
		if (now >= t.nextStage + 20) {
			t.nextStage = now + 300 + level.getRandom().nextInt(400);
			caught(level, vi);
		}
	}

	/** Um peixe: mostra-o na mão um bocadinho e repõe uma troca de venda de peixe (com teto por dia). */
	public static boolean caught(ServerLevel level, Villager vi) {
		catches++;
		Balloons.show(vi, Balloons.FISH, 40);
		level.playSound(null, vi.blockPosition(), SoundEvents.FISHING_BOBBER_RETRIEVE, SoundSource.NEUTRAL, 0.5f, 1.0f);
		Village v = VillageManager.villageAt(level, vi.blockPosition());
		if (v == null) return false;
		TownData t = v.civic.life.town;
		long day = VillageEconomy.day(level);
		if (t.catchDay != day) {
			t.catchDay = day;
			t.catches.clear();
		}
		int n = t.catches.getOrDefault(vi.getUUID(), 0);
		if (!TownRules.mayRestock(n)) return false;
		for (MerchantOffer o : vi.getOffers()) {
			if (o.getUses() > 0 && sellsFish(o)) {
				o.resetUses();
				LifeData.putCapped(t.catches, vi.getUUID(), n + 1);
				VillageData.get(level).changed();
				restocks++;
				return true;
			}
		}
		return false;
	}

	/** Uma troca em que o pescador VENDE peixe (o resultado é peixe, cru ou cozinhado). */
	static boolean sellsFish(MerchantOffer o) {
		ItemStack r = o.getResult();
		return r.is(ItemTags.FISHES) || r.is(Items.COOKED_COD) || r.is(Items.COOKED_SALMON);
	}

	public static int casts, catches, restocks;
}
