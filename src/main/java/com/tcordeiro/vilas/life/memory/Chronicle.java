package com.tcordeiro.vilas.life.memory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.tcordeiro.vilas.logic.LoreRules;
import com.tcordeiro.vilas.village.Village;
import com.tcordeiro.vilas.village.VillageData;
import com.tcordeiro.vilas.village.VillageEconomy;
import com.tcordeiro.vilas.village.VillageManager;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.dialog.ActionButton;
import net.minecraft.server.dialog.CommonButtonData;
import net.minecraft.server.dialog.CommonDialogData;
import net.minecraft.server.dialog.Dialog;
import net.minecraft.server.dialog.DialogAction;
import net.minecraft.server.dialog.MultiActionDialog;
import net.minecraft.server.dialog.action.StaticAction;
import net.minecraft.server.dialog.body.DialogBody;
import net.minecraft.server.dialog.body.PlainMessage;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;

/**
 * 0.6.0 — A crónica da vila: uma lista só de acrescentar (até {@link LoreRules#CHRONICLE_MAX} entradas; as mais antigas saem) com
 * o que aconteceu: subidas de nível, eleições, festas, aprendizes, obras, recém-chegados, resgates, obras comunitárias (e, com a
 * parte das crises, mortes e funerais). Guarda-se o facto; o texto gera-se ao ler, no quadro («Crónica») ou num livro da vila (custa
 * um livro do inventário: nada se cria do nada).
 */
public final class Chronicle {
	private Chronicle() {}

	/** Os factos das conversas (Talk) que também vão para a crónica. */
	public static final Set<String> FROM_TALK = Set.of("level", "elected", "news.done", "news.newcomer", "festa");

	public static Lore.Entry add(ServerLevel level, Village v, String key, String... args) {
		return add(v, VillageEconomy.day(level), key, args) == null ? null : changed(level, v);
	}

	static Lore.Entry changed(ServerLevel level, Village v) {
		VillageData.get(level).changed();
		List<Lore.Entry> c = v.civic.life.lore.chronicle;
		return c.get(c.size() - 1);
	}

	/** A regra, sem mundo: acrescenta (a mesma entrada no mesmo dia não se repete); as mais antigas saem com o teto. */
	public static Lore.Entry add(Village v, long day, String key, String... args) {
		Lore l = v.civic.life.lore;
		List<String> a = Arrays.asList(args);
		for (int i = Math.max(0, l.chronicle.size() - 8); i < l.chronicle.size(); i++) {
			Lore.Entry e = l.chronicle.get(i);
			if (e.day() == day && e.key().equals(key) && e.args().equals(a)) return null;
		}
		List<String> safe = new ArrayList<>();
		for (String s : a) if (safe.size() < 4) safe.add(s.length() > 64 ? s.substring(0, 64) : s);
		Lore.Entry e = new Lore.Entry(day, key, List.copyOf(safe));
		while (l.chronicle.size() >= LoreRules.CHRONICLE_MAX) {
			l.chronicle.remove(0);
			l.dropped++;
		}
		l.chronicle.add(e);
		added++;
		return e;
	}

	public static int added;

	/** Chamado por {@code Talk.fact}: os factos importantes também ficam na crónica. */
	public static void fromTalk(ServerLevel level, Village v, String key, String... args) {
		if (FROM_TALK.contains(key)) add(level, v, key, args);
	}

	/** A frase de uma entrada (gerada agora). */
	public static Component line(Lore.Entry e) {
		Object[] args = new Object[e.args().size()];
		for (int i = 0; i < args.length; i++) {
			String a = e.args().get(i);
			args[i] = a.startsWith("#") ? Component.translatable(a.substring(1)) : a;
		}
		return Component.translatable("tvilas.chronicle.day", e.day()).append(" ").append(Component.translatable("tvilas.chronicle." + e.key(), args));
	}

	// ---------------------------------------------------------------- ler

	/** O ecrã da crónica: as últimas entradas e o botão do livro. */
	public static void open(ServerPlayer p, Village v) {
		Lore l = v.civic.life.lore;
		List<DialogBody> body = new ArrayList<>();
		if (l.chronicle.isEmpty()) body.add(new PlainMessage(Component.translatable("tvilas.chronicle.empty"), 300));
		for (int i = l.chronicle.size() - 1; i >= Math.max(0, l.chronicle.size() - LoreRules.CHRONICLE_SHOWN); i--)
			body.add(new PlainMessage(line(l.chronicle.get(i)), 300));
		body.add(new PlainMessage(Component.translatable("tvilas.chronicle.count", l.chronicle.size() + l.dropped).withStyle(ChatFormatting.GRAY), 300));
		List<ActionButton> buttons = new ArrayList<>();
		if (!l.chronicle.isEmpty())
			buttons.add(new ActionButton(new CommonButtonData(Component.translatable("tvilas.chronicle.book_button"), 150),
					Optional.of(new StaticAction(new ClickEvent.RunCommand("/vila cronica livro")))));
		CommonDialogData common = new CommonDialogData(Component.translatable("tvilas.chronicle.title"), Optional.empty(), true, false, DialogAction.CLOSE, body,
				List.of());
		ActionButton close = new ActionButton(new CommonButtonData(Component.translatable("gui.done"), 150), Optional.empty());
		p.openDialog(Holder.direct((Dialog) new MultiActionDialog(common, buttons, Optional.of(close), 1)));
	}

	/** O livro da vila: troca um livro do inventário por um livro escrito com a crónica (3 entradas por página, até 50 páginas). */
	public static VillageManager.Result book(ServerPlayer p, Village v) {
		Lore l = v.civic.life.lore;
		if (l.chronicle.isEmpty()) return VillageManager.Result.fail("tvilas.chronicle.empty");
		if (VillageManager.count(p.getInventory(), Items.BOOK) < 1) return VillageManager.Result.fail("tvilas.chronicle.need_book");
		VillageManager.take(p.getInventory(), Items.BOOK, 1);
		p.getInventory().placeItemBackInInventory(book(v), net.minecraft.util.Prediction.SERVER_ONLY);
		books++;
		return VillageManager.Result.ok("tvilas.chronicle.book_given");
	}

	public static int books;

	public static ItemStack book(Village v) {
		Lore l = v.civic.life.lore;
		List<Filterable<Component>> pages = new ArrayList<>();
		MutableComponent page = Component.empty();
		int n = 0;
		int from = Math.max(0, l.chronicle.size() - LoreRules.BOOK_PAGES * 3);
		for (int i = from; i < l.chronicle.size(); i++) {
			if (n > 0) page.append("\n\n");
			page.append(line(l.chronicle.get(i)));
			if (++n == 3) {
				pages.add(Filterable.passThrough(page));
				page = Component.empty();
				n = 0;
			}
		}
		if (n > 0) pages.add(Filterable.passThrough(page));
		ItemStack b = new ItemStack(Items.WRITTEN_BOOK);
		b.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(Filterable.passThrough("Livro da Vila"), "Tcordeiro", 0, pages, true));
		b.set(DataComponents.CUSTOM_NAME, Component.translatable("tvilas.chronicle.book"));
		return b;
	}
}
