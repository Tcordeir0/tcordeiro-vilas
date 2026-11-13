package com.tcordeiro.vilas.life;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.tcordeiro.vilas.TVilas;
import com.tcordeiro.vilas.VilasConfig;
import com.tcordeiro.vilas.build.Builders;
import com.tcordeiro.vilas.logic.LifeRules;
import com.tcordeiro.vilas.logic.TownRules;
import com.tcordeiro.vilas.village.Lot;
import com.tcordeiro.vilas.village.Village;
import com.tcordeiro.vilas.village.VillageData;
import com.tcordeiro.vilas.village.VillageEconomy;
import com.tcordeiro.vilas.village.VillageManager;
import com.tcordeiro.vilas.village.VillageNews;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * 0.5.0 — Contratar um ajudante: no menu do morador (Shift+clique), um aldeão adulto que não desconfie de ti segue-te durante um dia
 * de jogo por {@code helper_price_cents} (20 CB; sem o CubCoin, 1 esmeralda) — TUDO para a tesouraria. Leva uma BOLSA de 9 espaços
 * (abre-se no menu dele) e colhe as plantações maduras (e volta a semear) nos TEUS terrenos. Regras de segurança: nunca entra em
 * terrenos alheios (espera à porta), nunca colhe fora dos teus terrenos, não apanha itens do chão nem abre baús, e não sai do
 * território. Um ajudante por jogador, um contrato por dia, dois por vila. No fim (ou se o dispensares, ou se ele morrer) a bolsa
 * volta para ti — se estiveres desligado, quando voltares.
 */
public final class Helper {
	private Helper() {}

	/** Os aldeões contratados agora (em memória, refeito a cada segundo pelas vilas). */
	static final Set<UUID> ACTIVE = new HashSet<>();

	public static boolean isHelper(Villager v) {
		return ACTIVE.contains(v.getUUID());
	}

	public static long price() {
		return Math.max(0, VilasConfig.get().helper_price_cents);
	}

	static Component priceText() {
		return TVilas.CUBCOIN ? VillageManager.money(price())
				: Component.translatable("tvilas.money.emeralds", TownRules.priceInEmeralds(price(), VilasConfig.get().emerald_value_cents));
	}

	/** O aldeão pode ser contratado? (adulto, livre, não o Prefeito/Construtor/Mercador/Carteiro a meio de uma carta) */
	static boolean free(Village v, Villager vi) {
		return vi.isAlive() && !vi.isBaby() && !VillageManager.isMayor(vi) && !Builders.isBuilder(vi) && !vi.getUUID().equals(v.extra.merchant)
				&& v.civic.life.town.helperByVillager(vi.getUUID()) == null && !vi.isSleeping();
	}

	// ---------------------------------------------------------------- contratar

	public static VillageManager.Result hire(ServerPlayer p, ServerLevel level, Village v, Villager vi) {
		TownData t = v.civic.life.town;
		long day = VillageEconomy.day(level);
		if (LifeRules.villagerTier(vi.getPlayerReputation(p)) == LifeRules.TIER_DISTRUST) return VillageManager.Result.fail("tvilas.helper.distrust");
		boolean has = t.helperOf(p.getUUID()) != null, today = t.hired.getOrDefault(p.getUUID(), -1L) == day;
		if (!TownRules.mayHire(has, today, t.activeHelpers(), free(v, vi))) {
			if (has) return VillageManager.Result.fail("tvilas.helper.already");
			if (today) return VillageManager.Result.fail("tvilas.helper.today");
			if (t.activeHelpers() >= TownRules.HELPERS_PER_VILLAGE) return VillageManager.Result.fail("tvilas.helper.village_full", TownRules.HELPERS_PER_VILLAGE);
			return VillageManager.Result.fail("tvilas.helper.busy");
		}
		long cents;
		if (TVilas.CUBCOIN) {
			cents = price();
			if (cents > 0 && !com.tcordeiro.vilas.compat.CubCoinPay.pay(p, cents)) return VillageManager.Result.fail("tvilas.helper.no_money", priceText());
		} else {
			long unit = VilasConfig.get().emerald_value_cents;
			int n = (int) TownRules.priceInEmeralds(price(), unit);
			if (VillageManager.count(p.getInventory(), Items.EMERALD) < n) return VillageManager.Result.fail("tvilas.helper.no_money", priceText());
			VillageManager.take(p.getInventory(), Items.EMERALD, n);
			cents = n * unit;
		}
		v.economy.treasury += cents; // o preço vai TODO para a tesouraria
		v.economy.collected += cents;
		TownData.Helper h = new TownData.Helper(p.getUUID(), p.getGameProfile().name(), vi.getUUID(), level.getGameTime() + TownRules.HELPER_TICKS);
		t.helpers.add(h);
		LifeData.putCapped(t.hired, p.getUUID(), day);
		ACTIVE.add(vi.getUUID());
		Task cur = Tasks.get(vi);
		if (cur != null) Tasks.end(level, vi, cur, "hired");
		Balloons.say(vi, Balloons.HEART, Component.translatable("tvilas.helper.hello", p.getGameProfile().name()), 60);
		Gestures.nod(level, vi);
		Talk.fact(level, v, vi.blockPosition(), "helper", p.getGameProfile().name());
		VillageData.get(level).changed();
		hires++;
		return VillageManager.Result.ok("tvilas.helper.hired", priceText());
	}

	public static int hires, harvests;

	// ---------------------------------------------------------------- a cada segundo (pela vila)

	static void tick(ServerLevel level, Village v, long now) {
		TownData t = v.civic.life.town;
		if (t.helpers.isEmpty()) return;
		boolean changed = false;
		for (Iterator<TownData.Helper> it = t.helpers.iterator(); it.hasNext();) {
			TownData.Helper h = it.next();
			if (!h.ended && now >= h.until) {
				finish(level, v, h, "time");
				changed = true;
			}
			if (h.ended) {
				ACTIVE.remove(h.villager);
				ServerPlayer p = level.getServer().getPlayerList().getPlayer(h.player);
				if (p != null && giveBack(p, h)) {
					it.remove();
					changed = true;
				}
				continue;
			}
			ACTIVE.add(h.villager);
			if (!(level.getEntity(h.villager) instanceof Villager vi) || !vi.isAlive()) continue; // longe: o contrato continua
			Task cur = Tasks.get(vi);
			if (cur != null && cur.kind == Task.Kind.HELP) continue;
			if (cur != null) Tasks.end(level, vi, cur, "helper");
			if (vi.isSleeping()) vi.stopSleeping();
			Task task = new Task(Task.Kind.HELP, now, Math.min(h.until, now + 2400)).speed(0.6);
			task.data = h;
			task.reach = 2.5;
			task.places = Life.places(level, v);
			Tasks.start(level, vi, task);
		}
		if (changed) VillageData.get(level).changed();
	}

	/** Acaba o contrato (tempo, dispensado, morreu): a bolsa volta para quem contratou (já, ou quando voltar). */
	static void finish(ServerLevel level, Village v, TownData.Helper h, String why) {
		if (h.ended) return;
		h.ended = true;
		ACTIVE.remove(h.villager);
		if (level.getEntity(h.villager) instanceof Villager vi) {
			Task t = Tasks.get(vi);
			if (t != null && t.kind == Task.Kind.HELP) Tasks.end(level, vi, t, why);
			if (vi.isAlive()) {
				Balloons.say(vi, Balloons.WAVE, Component.translatable("tvilas.helper.bye"), 60);
				Gestures.wave(vi);
			}
		}
		ServerPlayer p = level.getServer().getPlayerList().getPlayer(h.player);
		if (p != null) {
			if (p.containerMenu instanceof BagMenu bm && bm.helper == h) p.closeContainer(); // grava a bolsa antes de a devolver
			VillageNews.tell(p, "🤝", "tvilas.helper.ended." + why);
			if (giveBack(p, h)) v.civic.life.town.helpers.remove(h);
		}
		VillageData.get(level).changed();
	}

	/** Devolve a bolsa ao jogador (o que não couber cai-lhe aos pés, como o vanilla). */
	static boolean giveBack(ServerPlayer p, TownData.Helper h) {
		for (ItemStack s : h.bag) if (!s.isEmpty()) p.getInventory().placeItemBackInInventory(s.copy(), net.minecraft.util.Prediction.SERVER_ONLY);
		h.bag.clear();
		return true;
	}

	/** O ajudante morreu: o contrato acaba e a bolsa volta para quem o contratou (nunca cai ao chão). */
	public static void onDeath(ServerLevel level, Villager vi) {
		for (Village v : VillageData.get(level).villages) {
			TownData.Helper h = v.civic.life.town.helperByVillager(vi.getUUID());
			if (h != null) finish(level, v, h, "died");
		}
	}

	public static VillageManager.Result dismiss(ServerPlayer p, Village v) {
		TownData.Helper h = v.civic.life.town.helperOf(p.getUUID());
		if (h == null) return VillageManager.Result.fail("tvilas.helper.none");
		finish(p.level(), v, h, "fired");
		return new VillageManager.Result(true, Component.empty());
	}

	// ---------------------------------------------------------------- seguir e colher (de 2 em 2 ticks, pela tarefa)

	static void step(ServerLevel level, Villager vi, Task t, long now) {
		if (!(t.data instanceof TownData.Helper h) || h.ended) {
			Tasks.end(level, vi, t, "ended");
			return;
		}
		ServerPlayer p = level.getServer().getPlayerList().getPlayer(h.player);
		if (p == null || p.level() != level) { // o patrão foi-se: fica à espera onde está
			t.target = null;
			vi.getNavigation().stop();
			return;
		}
		Tasks.look(vi, p);
		// a colher: chegou à planta?
		if ("harvest".equals(t.role) && t.target != null) {
			if (vi.distanceToSqr(Vec3.atBottomCenterOf(t.target)) <= 2.2 * 2.2) {
				harvest(level, vi, h, t.target);
				t.role = null;
				t.holdUntil = now + 10;
			}
			return;
		}
		if (Tasks.every(t, now, 20) && h.openBy == null && free(h)) {
			BlockPos crop = crop(level, vi.blockPosition(), h.player);
			if (crop != null) {
				t.role = "harvest";
				t.target = crop;
				t.reach = 1.6;
				t.arrived = false;
				return;
			}
		}
		BlockPos want = p.blockPosition();
		if (vi.distanceToSqr(p) <= 3 * 3) {
			t.target = null;
			vi.getNavigation().stop();
			return;
		}
		if (mayStand(level, want, h.player)) {
			if (t.target != null && t.target.distSqr(want) > 4) vi.getNavigation().stop(); // o patrão andou: refaz o caminho
			t.target = want;
			t.reach = 2.5;
			t.arrived = false;
		} else { // o patrão entrou num terreno alheio (ou saiu da vila): espera à porta
			t.target = null;
			vi.getNavigation().stop();
			if (Tasks.every(t, now, 200)) Balloons.show(vi, Balloons.QUESTION, 40);
		}
		// nunca dentro de um terreno alheio: se lá estiver (o caminho cortou um canto), sai pelo sítio de onde veio
		if (!mayStand(level, vi.blockPosition(), h.player) && h.safe != null) {
			t.target = h.safe;
			t.arrived = false;
		} else if (mayStand(level, vi.blockPosition(), h.player)) h.safe = vi.blockPosition();
	}

	/** REGRA DE SEGURANÇA: o ajudante pode estar aqui? Dentro do território, fora de terrenos alheios. */
	public static boolean mayStand(ServerLevel level, BlockPos pos, UUID employer) {
		Village v = VillageManager.villageAt(level, pos);
		if (v == null) return false;
		Lot lot = VillageManager.lotAt(level, pos.getX(), pos.getZ());
		return TownRules.helperMayEnter(lot == null ? null : lot.owner, lot == null ? Set.of() : lot.friends, employer);
	}

	/** Uma planta madura a 4 blocos, num terreno de quem o contratou (REGRA {@link TownRules#helperMayHarvest}). */
	static BlockPos crop(ServerLevel level, BlockPos at, UUID employer) {
		for (BlockPos q : BlockPos.betweenClosed(at.offset(-4, -1, -4), at.offset(4, 1, 4))) {
			BlockState s = level.getBlockState(q);
			if (!(s.getBlock() instanceof CropBlock c) || !c.isMaxAge(s)) continue;
			Lot lot = VillageManager.lotAt(level, q.getX(), q.getZ());
			if (TownRules.helperMayHarvest(lot == null ? null : lot.owner, lot == null ? Set.of() : lot.friends, employer)) return q.immutable();
		}
		return null;
	}

	/** Para os testes: a planta madura que o ajudante colheria daqui (null = nenhuma permitida) e colher já. */
	public static BlockPos cropNear(ServerLevel level, BlockPos at, UUID employer) {
		return crop(level, at, employer);
	}

	public static void harvestAt(ServerLevel level, Villager vi, TownData.Helper h, BlockPos pos) {
		harvest(level, vi, h, pos);
	}

	/** Há lugar na bolsa para uma colheita (3 espaços livres)? */
	static boolean free(TownData.Helper h) {
		return TownRules.HELPER_SLOTS - h.bag.size() >= 3;
	}

	/** Colhe (os itens vão para a bolsa) e volta a semear com uma das sementes. */
	static void harvest(ServerLevel level, Villager vi, TownData.Helper h, BlockPos pos) {
		BlockState s = level.getBlockState(pos);
		if (!(s.getBlock() instanceof CropBlock c) || !c.isMaxAge(s) || h.openBy != null || !free(h)) return;
		Lot lot = VillageManager.lotAt(level, pos.getX(), pos.getZ());
		if (!TownRules.helperMayHarvest(lot == null ? null : lot.owner, lot == null ? Set.of() : lot.friends, h.player)) return;
		List<ItemStack> drops = new ArrayList<>(Block.getDrops(s, level, pos, null, vi, ItemStack.EMPTY));
		boolean replant = false;
		for (ItemStack d : drops)
			if (d.is(c.asItem()) && d.getCount() > 0) {
				d.shrink(1);
				replant = true;
				break;
			}
		level.levelEvent(2001, pos, Block.getId(s));
		level.setBlock(pos, replant ? c.getStateForAge(0) : net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
		for (ItemStack d : drops) if (!d.isEmpty()) add(h.bag, d);
		if (!drops.isEmpty() && !drops.get(0).isEmpty()) Props.hold(vi, drops.get(0), 20);
		h.harvested++;
		harvests++;
		level.playSound(null, pos, SoundEvents.CROP_BREAK, SoundSource.BLOCKS, 0.8f, 1.0f);
	}

	/** Junta à bolsa (até 9 espaços; o que não couber fica no último monte possível — a colheita só começa com 3 livres). */
	static void add(List<ItemStack> bag, ItemStack s) {
		for (ItemStack in : bag)
			if (ItemStack.isSameItemSameComponents(in, s) && in.getCount() < in.getMaxStackSize()) {
				int n = Math.min(s.getCount(), in.getMaxStackSize() - in.getCount());
				in.grow(n);
				s.shrink(n);
				if (s.isEmpty()) return;
			}
		if (!s.isEmpty() && bag.size() < TownRules.HELPER_SLOTS) bag.add(s.copy());
	}

	// ---------------------------------------------------------------- a bolsa (9 espaços)

	public static VillageManager.Result openBag(ServerPlayer p, Village v) {
		TownData.Helper h = v.civic.life.town.helperOf(p.getUUID());
		if (h == null) return VillageManager.Result.fail("tvilas.helper.none");
		if (!(p.level().getEntity(h.villager) instanceof Villager vi) || vi.distanceToSqr(p) > Resident.REACH * Resident.REACH)
			return VillageManager.Result.fail("tvilas.request.too_far");
		p.openMenu(new SimpleMenuProvider((id, inv, pl) -> new BagMenu(id, inv, h), Component.translatable("tvilas.helper.bag", vi.getVillagerData().profession().value().name())));
		return new VillageManager.Result(true, Component.empty());
	}

	/** O ecrã da bolsa: uma cópia dos itens; ao fechar, a bolsa fica com o que lá ficou (só o patrão a abre, uma de cada vez). */
	public static final class BagMenu extends ChestMenu {
		final TownData.Helper helper;
		final Container bag;
		boolean saved;

		BagMenu(int id, Inventory inv, TownData.Helper h) {
			this(id, inv, h, new SimpleContainer(TownRules.HELPER_SLOTS));
		}

		private BagMenu(int id, Inventory inv, TownData.Helper h, Container bag) {
			super(MenuType.GENERIC_9x1, id, inv, bag, 1);
			this.helper = h;
			this.bag = bag;
			for (int i = 0; i < h.bag.size() && i < bag.getContainerSize(); i++) bag.setItem(i, h.bag.get(i).copy());
			h.openBy = inv.player.getUUID();
		}

		@Override
		public boolean stillValid(Player player) {
			return !helper.ended && player.getUUID().equals(helper.player);
		}

		@Override
		public void removed(Player player) {
			super.removed(player);
			if (saved || player.level().isClientSide()) return;
			saved = true;
			helper.bag.clear();
			for (int i = 0; i < bag.getContainerSize(); i++) {
				ItemStack s = bag.removeItemNoUpdate(i);
				if (!s.isEmpty()) helper.bag.add(s);
			}
			helper.openBy = null;
			if (player instanceof ServerPlayer sp) VillageData.get(sp.level()).changed();
		}
	}

}
