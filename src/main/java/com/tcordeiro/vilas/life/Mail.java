package com.tcordeiro.vilas.life;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.tcordeiro.vilas.TVilas;
import com.tcordeiro.vilas.compat.Mods;
import com.tcordeiro.vilas.logic.LifeRules;
import com.tcordeiro.vilas.logic.TownRules;
import com.tcordeiro.vilas.registry.VilasItems;
import com.tcordeiro.vilas.registry.VilasProfessions;
import com.tcordeiro.vilas.village.Lot;
import com.tcordeiro.vilas.village.Village;
import com.tcordeiro.vilas.village.VillageData;
import com.tcordeiro.vilas.village.VillageEconomy;
import com.tcordeiro.vilas.village.VillageManager;
import com.tcordeiro.vilas.village.VillageNews;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.dialog.ActionButton;
import net.minecraft.server.dialog.CommonButtonData;
import net.minecraft.server.dialog.CommonDialogData;
import net.minecraft.server.dialog.Dialog;
import net.minecraft.server.dialog.DialogAction;
import net.minecraft.server.dialog.MultiActionDialog;
import net.minecraft.server.dialog.body.DialogBody;
import net.minecraft.server.dialog.body.PlainMessage;
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
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * 0.5.0 — O Carteiro (profissão nova, só com o Móveis): leva cartas com itens de um jogador para a CAIXA DE CORREIO do Móveis que
 * está no terreno de outro jogador da vila. Shift+clique no Carteiro → «Mandar correio» → escolhe-se o destinatário → 9 espaços
 * (como a caixa de correio); ao fechar, a carta fica com o Carteiro (guardada na vila, nunca no aldeão) e paga-se o selo (1 CB, para
 * a tesouraria; grátis sem o CubCoin). Nas horas de trabalho ele vai a pé, com a bolsa ao ombro, pôr a carta na caixa. Caixa cheia,
 * partida ou longe demais: tenta outra vez; à 3.ª volta para quem a mandou, que a levanta no Carteiro. Os itens nunca se perdem.
 */
public final class Mail {
	private Mail() {}

	public static final String MAILBOX = "mailbox";

	public static boolean available() {
		return Mods.loaded(Mods.MOVEIS) && VilasProfessions.registered(VilasProfessions.POSTMAN);
	}

	public static boolean isPostman(Villager v) {
		return available() && v.getVillagerData().profession().is(VilasProfessions.POSTMAN);
	}

	static boolean isMailbox(ServerLevel level, BlockPos pos) {
		var id = BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock());
		return id.getNamespace().equals(Mods.MOVEIS) && id.getPath().equals(MAILBOX);
	}

	/** A caixa de correio do Móveis num terreno (os chunks dele já carregados), ou null. */
	public static BlockPos mailboxIn(ServerLevel level, Lot lot) {
		var a = lot.area;
		for (int cx = a.minX() >> 4; cx <= a.maxX() >> 4; cx++)
			for (int cz = a.minZ() >> 4; cz <= a.maxZ() >> 4; cz++) {
				LevelChunk ch = level.getChunkSource().getChunkNow(cx, cz);
				if (ch == null) continue;
				for (BlockEntity be : ch.getBlockEntities().values()) {
					BlockPos p = be.getBlockPos();
					if (lot.contains(p.getX(), p.getZ()) && be instanceof Container && isMailbox(level, p)) return p.immutable();
				}
			}
		return null;
	}

	/** Os destinatários possíveis: os donos de terrenos da vila com uma caixa de correio no terreno (dono → caixa), sem o próprio. */
	public static Map<UUID, BlockPos> recipients(ServerLevel level, Village v, UUID sender) {
		Map<UUID, BlockPos> out = new LinkedHashMap<>();
		for (Lot lot : VillageManager.lotsOf(level, v)) {
			if (lot.owner.equals(sender) || out.containsKey(lot.owner) || out.size() >= 8) continue;
			BlockPos box = mailboxIn(level, lot);
			if (box != null) out.put(lot.owner, box);
		}
		return out;
	}

	static String nameOf(ServerLevel level, Village v, UUID who) {
		for (Lot lot : VillageManager.lotsOf(level, v)) if (lot.owner.equals(who)) return lot.ownerName;
		return who.toString().substring(0, 8);
	}

	// ---------------------------------------------------------------- mandar

	/** «Mandar correio»: o ecrã com os destinatários (um botão cada). */
	public static VillageManager.Result chooseRecipient(ServerPlayer p, ServerLevel level, Village v, Villager postman) {
		TownData t = v.civic.life.town;
		if (!TownRules.mayPost(t.pendingOf(p.getUUID()), t.mail.size())) return VillageManager.Result.fail("tvilas.mail.full", TownRules.MAIL_PER_PLAYER);
		Map<UUID, BlockPos> to = recipients(level, v, p.getUUID());
		if (to.isEmpty()) return VillageManager.Result.fail("tvilas.mail.nobody");
		List<DialogBody> body = new ArrayList<>();
		body.add(new PlainMessage(Component.translatable("tvilas.mail.choose", stampText()), 300));
		body.add(new PlainMessage(Component.translatable("tvilas.mail.rules", TownRules.MAIL_PER_PLAYER, TownRules.MAIL_TRIES).withStyle(ChatFormatting.GRAY), 300));
		List<ActionButton> buttons = new ArrayList<>();
		for (UUID who : to.keySet())
			buttons.add(Resident.button(Component.translatable("tvilas.mail.to", nameOf(level, v, who)), "/vila morador " + postman.getUUID() + " carta " + who));
		CommonDialogData common = new CommonDialogData(Component.translatable("tvilas.mail.title"), Optional.empty(), true, false, DialogAction.CLOSE, body, List.of());
		ActionButton close = new ActionButton(new CommonButtonData(Component.translatable("gui.done"), 150), Optional.empty());
		p.openDialog(Holder.direct((Dialog) new MultiActionDialog(common, buttons, Optional.of(close), 2)));
		return new VillageManager.Result(true, Component.empty());
	}

	static Component stampText() {
		return TVilas.CUBCOIN ? VillageManager.money(TownRules.STAMP_CENTS) : Component.translatable("tvilas.mail.free");
	}

	/** O destinatário escolhido: abre os 9 espaços da carta; ao fechar, a carta fica com o Carteiro. */
	public static VillageManager.Result openLetter(ServerPlayer p, ServerLevel level, Village v, Villager postman, String toArg) {
		UUID to;
		try {
			to = UUID.fromString(toArg);
		} catch (IllegalArgumentException | NullPointerException e) {
			return VillageManager.Result.fail("tvilas.mail.nobody");
		}
		BlockPos box = recipients(level, v, p.getUUID()).get(to);
		if (box == null) return VillageManager.Result.fail("tvilas.mail.nobody");
		TownData t = v.civic.life.town;
		if (!TownRules.mayPost(t.pendingOf(p.getUUID()), t.mail.size())) return VillageManager.Result.fail("tvilas.mail.full", TownRules.MAIL_PER_PLAYER);
		String toName = nameOf(level, v, to);
		p.openMenu(new SimpleMenuProvider((id, inv, pl) -> new LetterMenu(id, inv, v.id, to, toName, box.asLong(), postman.getUUID()),
				Component.translatable("tvilas.mail.letter", toName, stampText())));
		return new VillageManager.Result(true, Component.empty());
	}

	/** Os 9 espaços da carta (só de quem a escreve); ao fechar, o que lá está vira uma carta (ou volta, se não der). */
	public static final class LetterMenu extends ChestMenu {
		final Container letter;
		final UUID village, to, postman;
		final String toName;
		final long box;
		boolean done;

		LetterMenu(int id, Inventory inv, UUID village, UUID to, String toName, long box, UUID postman) {
			this(id, inv, new SimpleContainer(TownRules.MAIL_SLOTS), village, to, toName, box, postman);
		}

		private LetterMenu(int id, Inventory inv, Container letter, UUID village, UUID to, String toName, long box, UUID postman) {
			super(MenuType.GENERIC_9x1, id, inv, letter, 1);
			this.letter = letter;
			this.village = village;
			this.to = to;
			this.toName = toName;
			this.box = box;
			this.postman = postman;
		}

		@Override
		public boolean stillValid(Player player) {
			return player.isAlive();
		}

		@Override
		public void removed(Player player) {
			super.removed(player);
			if (done || !(player instanceof ServerPlayer sp)) return;
			done = true;
			List<ItemStack> items = new ArrayList<>();
			for (int i = 0; i < letter.getContainerSize(); i++) {
				ItemStack s = letter.removeItemNoUpdate(i);
				if (!s.isEmpty()) items.add(s);
			}
			if (items.isEmpty()) return;
			Village v = VillageManager.byId(sp.level(), village);
			VillageManager.Result r = v == null ? VillageManager.Result.fail("tvilas.lot.no_village") : post(sp, v, to, toName, box, items);
			if (!r.ok()) for (ItemStack s : items) sp.getInventory().placeItemBackInInventory(s, net.minecraft.util.Prediction.SERVER_ONLY);
			sp.sendSystemMessage(r.message());
			if (r.ok() && sp.level().getEntity(postman) instanceof Villager pm) {
				Balloons.show(pm, Balloons.MAIL, 50);
				Gestures.nod(sp.level(), pm);
			}
		}
	}

	/**
	 * Guarda a carta (REGRA: teto por jogador e por vila) e cobra o selo: com o CubCoin 1 CB, TODO para a tesouraria (sem dinheiro não
	 * vai); sem o CubCoin é grátis. Devolve o resultado; se falhar, quem chama devolve os itens.
	 */
	public static VillageManager.Result post(ServerPlayer p, Village v, UUID to, String toName, long box, List<ItemStack> items) {
		ServerLevel level = p.level();
		TownData t = v.civic.life.town;
		if (!TownRules.mayPost(t.pendingOf(p.getUUID()), t.mail.size())) return VillageManager.Result.fail("tvilas.mail.full", TownRules.MAIL_PER_PLAYER);
		if (TVilas.CUBCOIN) {
			if (!com.tcordeiro.vilas.compat.CubCoinPay.pay(p, TownRules.STAMP_CENTS)) return VillageManager.Result.fail("tvilas.mail.no_money", stampText());
			v.economy.treasury += TownRules.STAMP_CENTS;
			v.economy.collected += TownRules.STAMP_CENTS;
		}
		TownData.Parcel parcel = new TownData.Parcel(++t.counter, p.getUUID(), p.getGameProfile().name(), to, toName, box, VillageEconomy.day(level));
		for (ItemStack s : items) if (parcel.items.size() < TownRules.MAIL_SLOTS) parcel.items.add(s);
		t.mail.add(parcel);
		posted++;
		Talk.fact(level, v, p.blockPosition(), "mail", p.getGameProfile().name(), toName);
		VillageData.get(level).changed();
		return VillageManager.Result.ok("tvilas.mail.posted", toName);
	}

	public static int posted, delivered, returned;

	/** «Levantar correio devolvido»: os itens das cartas que não se entregaram voltam para quem as mandou. */
	public static VillageManager.Result collectReturned(ServerPlayer p, Village v) {
		TownData t = v.civic.life.town;
		int n = 0;
		for (Iterator<TownData.Parcel> it = t.mail.iterator(); it.hasNext();) {
			TownData.Parcel parcel = it.next();
			if (!parcel.returned || !parcel.from.equals(p.getUUID())) continue;
			for (ItemStack s : parcel.items) p.getInventory().placeItemBackInInventory(s.copy(), net.minecraft.util.Prediction.SERVER_ONLY);
			parcel.items.clear();
			it.remove();
			n++;
		}
		if (n == 0) return VillageManager.Result.fail("tvilas.mail.none_returned");
		VillageData.get(p.level()).changed();
		return VillageManager.Result.ok("tvilas.mail.collected", n);
	}

	public static int returnedFor(Village v, UUID player) {
		int n = 0;
		for (TownData.Parcel p : v.civic.life.town.mail) if (p.returned && p.from.equals(player)) n++;
		return n;
	}

	// ---------------------------------------------------------------- entregar (a pé, nas horas de trabalho)

	/** O Carteiro decide ir entregar? (horas de trabalho, há cartas, a pausa entre entregas já passou.) */
	public static boolean tryDeliver(ServerLevel level, Village v, Places places, Villager vi, LifeVillager.Memo m, long now, long tod) {
		if (!isPostman(vi) || !LifeRules.workTime(tod) || now < m.nextMail) return false;
		TownData t = v.civic.life.town;
		TownData.Parcel next = null;
		for (TownData.Parcel p : t.mail) if (!p.returned) {
			next = p;
			break;
		}
		if (next == null) return false;
		m.nextMail = now + 400;
		if (TownRules.returnToSender(next.tries)) {
			bounce(level, v, next);
			return false;
		}
		next.tries++;
		VillageData.get(level).changed();
		BlockPos box = BlockPos.of(next.box);
		Task task = new Task(Task.Kind.DELIVER, now, now + 1600).to(box, 2.4).speed(0.55);
		task.ref = next.id;
		task.places = places;
		task.icon = Balloons.MAIL;
		Tasks.start(level, vi, task);
		Props.hold(vi, new ItemStack(VilasItems.MAIL_BAG), 1600);
		return true;
	}

	/** Chegou à caixa: põe lá os itens (o que não couber fica para a próxima). */
	static void arrive(ServerLevel level, Villager vi, Task t) {
		Village v = VillageManager.villageAt(level, vi.blockPosition());
		TownData.Parcel parcel = v == null ? null : find(v, t.ref);
		if (parcel != null) {
			BlockPos box = BlockPos.of(parcel.box);
			if (deliverInto(level, box, parcel)) {
				v.civic.life.town.mail.remove(parcel);
				delivered++;
				Tasks.look(vi, vi); // vira-se
				vi.getLookControl().setLookAt(box.getX() + 0.5, box.getY() + 0.5, box.getZ() + 0.5);
				Balloons.show(vi, Balloons.MAIL, 50);
				Gestures.nod(level, vi);
				level.playSound(null, box, SoundEvents.BUNDLE_DROP_CONTENTS, SoundSource.BLOCKS, 0.8f, 1.1f);
				ServerPlayer to = level.getServer().getPlayerList().getPlayer(parcel.to);
				if (to != null) VillageNews.tell(to, "✉", "tvilas.mail.delivered_to", parcel.fromName);
				ServerPlayer from = level.getServer().getPlayerList().getPlayer(parcel.from);
				if (from != null) VillageNews.tell(from, "✉", "tvilas.mail.delivered_from", parcel.toName);
			} else if (TownRules.returnToSender(parcel.tries)) bounce(level, v, parcel);
			VillageData.get(level).changed();
		}
		Tasks.end(level, vi, t, "done");
	}

	static TownData.Parcel find(Village v, long id) {
		for (TownData.Parcel p : v.civic.life.town.mail) if (p.id == id && !p.returned) return p;
		return null;
	}

	/** Põe os itens na caixa (só uma caixa de correio do Móveis); true se couberam todos. O que entrou sai da carta. */
	static boolean deliverInto(ServerLevel level, BlockPos box, TownData.Parcel parcel) {
		if (!level.isLoaded(box) || !isMailbox(level, box) || !(level.getBlockEntity(box) instanceof Container c)) return false;
		for (Iterator<ItemStack> it = parcel.items.iterator(); it.hasNext();) {
			ItemStack s = it.next();
			for (int i = 0; i < c.getContainerSize() && !s.isEmpty(); i++) {
				ItemStack in = c.getItem(i);
				if (in.isEmpty()) c.setItem(i, s.split(Math.min(s.getCount(), c.getMaxStackSize(s))));
				else if (ItemStack.isSameItemSameComponents(in, s) && in.getCount() < in.getMaxStackSize()) {
					int n = Math.min(s.getCount(), in.getMaxStackSize() - in.getCount());
					in.grow(n);
					s.shrink(n);
				}
			}
			if (s.isEmpty()) it.remove();
		}
		c.setChanged(); // o Móveis levanta a bandeirinha e avisa o dono
		return parcel.items.isEmpty();
	}

	/** A carta não se entregou ao fim de 3 tentativas: volta para quem a mandou (levanta-a no Carteiro). */
	static void bounce(ServerLevel level, Village v, TownData.Parcel parcel) {
		if (parcel.returned) return;
		parcel.returned = true;
		returned++;
		ServerPlayer from = level.getServer().getPlayerList().getPlayer(parcel.from);
		if (from != null) VillageNews.tell(from, "✉", "tvilas.mail.bounced", parcel.toName);
		VillageData.get(level).changed();
	}

	/** Para os testes: a carta que o Carteiro está a levar. */
	public static long carrying(Villager vi) {
		Task t = Tasks.get(vi);
		return t != null && t.kind == Task.Kind.DELIVER ? t.ref : -1;
	}
}
