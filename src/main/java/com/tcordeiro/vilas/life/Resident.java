package com.tcordeiro.vilas.life;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.tcordeiro.vilas.logic.LifeRules;
import com.tcordeiro.vilas.logic.LifeRules.Taste;
import com.tcordeiro.vilas.village.Batch;
import com.tcordeiro.vilas.village.Village;
import com.tcordeiro.vilas.village.VillageData;
import com.tcordeiro.vilas.village.VillageEconomy;
import com.tcordeiro.vilas.village.VillageManager;
import com.tcordeiro.vilas.village.VillageNews;
import com.tcordeiro.vilas.village.VillagerRequests;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
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
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.ai.gossip.GossipType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

/**
 * 0.4.0 — O morador: Shift+clique com a mão vazia abre o MENU DO MORADOR (Trocar, Conversar, Onde fica…?, o pedido «!» se houver,
 * e o que ele pensa de ti); Shift+clique com um item dá-lhe um PRESENTE (gostos por profissão; 2 por semana; detestado volta para
 * ti, sem castigo). O clique simples continua igual (trocas, ou o pedido «!»). Só nos aldeões do território de uma Mesa.
 */
public final class Resident {
	private Resident() {}

	public static final double REACH = 8;

	// ---------------------------------------------------------------- clique

	public static InteractionResult onUse(ServerPlayer p, ServerLevel level, Villager vi, InteractionHand hand) {
		if (hand != InteractionHand.MAIN_HAND || !p.isShiftKeyDown() || vi.isBaby() || VillageManager.isMayor(vi) || vi.isSleeping()) return InteractionResult.PASS;
		Village v = VillageManager.villageAt(level, vi.blockPosition());
		if (v == null || !com.tcordeiro.vilas.VilasConfig.get().villager_life) return InteractionResult.PASS;
		ItemStack held = p.getMainHandItem();
		if (held.isEmpty()) {
			menu(p, level, v, vi);
			return InteractionResult.SUCCESS;
		}
		if (giftable(held)) {
			gift(p, level, v, vi, held);
			return InteractionResult.SUCCESS;
		}
		return InteractionResult.PASS;
	}

	/** O que NÃO é presente: etiquetas, trelas, ovos de criação, baldes (o vanilla usa-os no aldeão). */
	static boolean giftable(ItemStack s) {
		return !(s.is(Items.NAME_TAG) || s.is(Items.LEAD) || s.is(Items.BUCKET) || s.getItem() instanceof net.minecraft.world.item.SpawnEggItem);
	}

	// ---------------------------------------------------------------- menu

	public static void menu(ServerPlayer p, ServerLevel level, Village v, Villager vi) {
		List<DialogBody> body = new ArrayList<>();
		int gossip = vi.getPlayerReputation(p);
		int tier = LifeRules.villagerTier(gossip);
		String prof = VillagerRequests.profession(vi);
		body.add(new PlainMessage(Component.translatable("tvilas.resident.opinion", Component.translatable(LifeRules.TIER_KEYS[tier]).withStyle(tierColor(tier)),
				gossip), 300));
		int given = v.civic.life.gifts.getOrDefault(giftKey(p.getUUID(), vi.getUUID(), LifeRules.week(VillageEconomy.day(level))), 0);
		body.add(new PlainMessage(Component.translatable("tvilas.resident.gifts", given, LifeRules.GIFTS_PER_VILLAGER_WEEK).withStyle(ChatFormatting.GRAY), 300));
		body.add(new PlainMessage(Component.translatable("tvilas.resident.hint." + (HINTS.contains(prof) ? prof : "none")).withStyle(ChatFormatting.GRAY), 300));
		body.add(new PlainMessage(Component.translatable("tvilas.resident.howto").withStyle(ChatFormatting.DARK_GRAY), 300));
		List<ActionButton> buttons = new ArrayList<>();
		String id = vi.getUUID().toString();
		buttons.add(button(Component.translatable("tvilas.resident.trade"), "/vila morador " + id + " trocar"));
		buttons.add(button(Component.translatable("tvilas.resident.talk"), "/vila morador " + id + " conversar"));
		if (VillagerRequests.villageOf(level, vi.getUUID()) != null && !v.civic.batch.done.contains(Batch.key(p.getUUID(), vi.getUUID())))
			buttons.add(button(Component.translatable("tvilas.resident.request"), "/vila morador " + id + " pedido"));
		// 0.5.0: o Carteiro (mandar e levantar correio) e contratar um ajudante
		if (Mail.isPostman(vi)) {
			buttons.add(button(Component.translatable("tvilas.resident.mail"), "/vila morador " + id + " correio"));
			int back = Mail.returnedFor(v, p.getUUID());
			if (back > 0) buttons.add(button(Component.translatable("tvilas.resident.mail_back", back), "/vila morador " + id + " devolvido"));
		}
		TownData.Helper mine = v.civic.life.town.helperOf(p.getUUID());
		if (mine != null && mine.villager.equals(vi.getUUID())) {
			body.add(1, new PlainMessage(Component.translatable("tvilas.resident.helper_until", Math.max(0, (mine.until - level.getGameTime()) / 1000), mine.harvested)
					.withStyle(ChatFormatting.GOLD), 300));
			buttons.add(button(Component.translatable("tvilas.resident.bag"), "/vila morador " + id + " bolsa"));
			buttons.add(button(Component.translatable("tvilas.resident.dismiss"), "/vila morador " + id + " dispensar"));
		} else if (mine == null && Helper.free(v, vi) && tier != LifeRules.TIER_DISTRUST)
			buttons.add(button(Component.translatable("tvilas.resident.hire", Helper.priceText()), "/vila morador " + id + " contratar"));
		Places places = Life.places(level, v);
		for (String kind : new String[] {"town_hall", "market", "tavern", "plaza"})
			if (places.spots.containsKey(kind))
				buttons.add(button(Component.translatable("tvilas.resident.where", Component.translatable("tvilas.upgrade." + kind)), "/vila morador " + id + " caminho " + kind));
		Component title = Component.translatable("tvilas.resident.title", vi.getVillagerData().profession().value().name());
		CommonDialogData common = new CommonDialogData(title, Optional.empty(), true, false, DialogAction.CLOSE, body, List.of());
		ActionButton close = new ActionButton(new CommonButtonData(Component.translatable("gui.done"), 150), Optional.empty());
		p.openDialog(Holder.direct((Dialog) new MultiActionDialog(common, buttons, Optional.of(close), 2)));
		Gestures.glance(vi, p);
		menus++;
	}

	public static int menus;

	static ChatFormatting tierColor(int tier) {
		return switch (tier) {
			case LifeRules.TIER_DISTRUST -> ChatFormatting.RED;
			case LifeRules.TIER_NEUTRAL -> ChatFormatting.YELLOW;
			case LifeRules.TIER_FRIENDLY -> ChatFormatting.GREEN;
			default -> ChatFormatting.AQUA;
		};
	}

	static ActionButton button(Component label, String command) {
		return new ActionButton(new CommonButtonData(label, 150), Optional.of(new StaticAction(new ClickEvent.RunCommand(command))));
	}

	static Villager near(ServerPlayer p, UUID id) {
		return p.level().getEntity(id) instanceof Villager vi && vi.isAlive() && vi.distanceToSqr(p) <= REACH * REACH ? vi : null;
	}

	/** Os botões do menu: {@code /vila morador <aldeão> trocar|conversar|pedido|caminho <sítio>}. */
	public static VillageManager.Result command(ServerPlayer p, UUID id, String what, String arg) {
		ServerLevel level = p.level();
		Villager vi = near(p, id);
		if (vi == null) return VillageManager.Result.fail("tvilas.request.too_far");
		Village v = VillageManager.villageAt(level, vi.blockPosition());
		if (v == null) return VillageManager.Result.fail("tvilas.lot.no_village");
		switch (what) {
			case "trocar" -> {
				Task t = Tasks.get(vi);
				if (t != null) Tasks.end(level, vi, t, "trade");
				vi.mobInteract(p, InteractionHand.MAIN_HAND);
				return new VillageManager.Result(true, Component.empty());
			}
			case "pedido" -> {
				Village rv = VillagerRequests.villageOf(level, id);
				if (rv != null) VillagerRequests.open(p, level, rv, vi);
				return new VillageManager.Result(true, Component.empty());
			}
			case "conversar" -> {
				return chat(p, level, v, vi);
			}
			case "caminho" -> {
				return directions(p, level, v, vi, arg);
			}
			// 0.5.0
			case "correio" -> {
				return Mail.isPostman(vi) ? Mail.chooseRecipient(p, level, v, vi) : VillageManager.Result.fail("tvilas.resident.unknown");
			}
			case "carta" -> {
				return Mail.isPostman(vi) ? Mail.openLetter(p, level, v, vi, arg) : VillageManager.Result.fail("tvilas.resident.unknown");
			}
			case "devolvido" -> {
				return Mail.collectReturned(p, v);
			}
			case "contratar" -> {
				return Helper.hire(p, level, v, vi);
			}
			case "bolsa" -> {
				return Helper.openBag(p, v);
			}
			case "dispensar" -> {
				return Helper.dismiss(p, v);
			}
			default -> {
				return VillageManager.Result.fail("tvilas.resident.unknown");
			}
		}
	}

	/** Conversar: diz uma novidade que sabe (fofoca) ou conversa de circunstância; balão com a fala e uma linha no chat. */
	static VillageManager.Result chat(ServerPlayer p, ServerLevel level, Village v, Villager vi) {
		long day = VillageEconomy.day(level);
		List<LifeData.Fact> facts = Talk.known(v, vi.getUUID(), day);
		Component line = facts.isEmpty() || level.getRandom().nextInt(4) == 0 ? Talk.smallTalk(level, vi, level.getRandom())
				: Talk.factLine(facts.get(level.getRandom().nextInt(facts.size())));
		if (LifeRules.villagerTier(vi.getPlayerReputation(p)) == LifeRules.TIER_DISTRUST) {
			line = Component.translatable("tvilas.talk.distrust");
			Gestures.no(vi);
		} else Gestures.nod(level, vi);
		Gestures.glance(vi, p);
		Balloons.say(vi, Balloons.TALK, line, 70);
		return new VillageManager.Result(true, Component.translatable("tvilas.resident.says", vi.getVillagerData().profession().value().name(), line));
	}

	/** Onde fica…? O aldeão vira-se para lá («aponta»), diz a distância e o rumo, e um rasto de luz (só para quem perguntou) mostra o caminho. */
	public static VillageManager.Result directions(ServerPlayer p, ServerLevel level, Village v, Villager vi, String kind) {
		Places.Spot s = Life.places(level, v).spots.get(kind);
		if (s == null) return VillageManager.Result.fail("tvilas.resident.no_place");
		double dx = s.center.getX() + 0.5 - vi.getX(), dz = s.center.getZ() + 0.5 - vi.getZ();
		int dist = (int) Math.round(Math.sqrt(dx * dx + dz * dz));
		long now = level.getGameTime();
		Task t = new Task(Task.Kind.POINT, now, now + 100);
		t.target = s.center;
		t.asker = p.getUUID();
		t.places = Life.places(level, v);
		Tasks.start(level, vi, t);
		Balloons.show(vi, Balloons.POINT, 60);
		Balloons.gesture(vi, Gestures.POINT, 100);
		Component place = Component.translatable("tvilas.upgrade." + kind);
		Component msg = dist <= 6 ? Component.translatable("tvilas.dir.here", place)
				: Component.translatable("tvilas.dir.answer", place, dist, Component.translatable("tvilas.dir." + LifeRules.compass(dx, dz)));
		return new VillageManager.Result(true, Component.literal("📍 ").append(msg));
	}

	// ---------------------------------------------------------------- presentes

	static String giftKey(UUID player, UUID villager, long week) {
		return player + ":" + villager + ":" + week;
	}

	/** Os HINTS existem para estas profissões (a dica do menu). */
	static final List<String> HINTS = List.of("farmer", "librarian", "cleric", "armorer", "weaponsmith", "toolsmith", "butcher", "fisherman", "fletcher",
			"shepherd", "mason", "cartographer", "leatherworker", "baker", "florist", "innkeeper", "beekeeper", "mayor", "nitwit");

	/** Gostos por profissão (o resto é neutro): adorado, gostado, detestado. */
	public static Taste taste(String prof, ItemStack s) {
		if (s.is(Items.ROTTEN_FLESH) || s.is(Items.SPIDER_EYE) || s.is(Items.POISONOUS_POTATO) || s.is(Items.PUFFERFISH) || s.is(Items.BONE))
			return Taste.DISLIKED;
		if (s.is(Items.CAKE)) return Taste.LOVED;
		Item[] loved = switch (prof) {
			case "farmer" -> new Item[] {Items.PUMPKIN_PIE, Items.HONEY_BOTTLE};
			case "librarian" -> new Item[] {Items.BOOK, Items.WRITABLE_BOOK};
			case "cleric" -> new Item[] {Items.GLOWSTONE_DUST, Items.RABBIT_FOOT};
			case "armorer", "weaponsmith", "toolsmith" -> new Item[] {Items.IRON_INGOT, Items.GOLD_INGOT};
			case "butcher" -> new Item[] {Items.COOKED_BEEF, Items.COOKED_PORKCHOP};
			case "fisherman" -> new Item[] {Items.COOKED_COD, Items.COOKED_SALMON};
			case "fletcher" -> new Item[] {Items.FEATHER, Items.FLINT};
			case "shepherd" -> new Item[] {Items.SHEARS, Items.STRING};
			case "mason" -> new Item[] {Items.CLAY_BALL, Items.BRICK};
			case "cartographer" -> new Item[] {Items.COMPASS, Items.MAP};
			case "leatherworker" -> new Item[] {Items.LEATHER, Items.RABBIT_HIDE};
			case "baker" -> new Item[] {Items.SUGAR, Items.EGG};
			case "innkeeper" -> new Item[] {Items.SWEET_BERRIES, Items.MUSHROOM_STEW};
			case "beekeeper" -> new Item[] {Items.HONEYCOMB, Items.SUNFLOWER};
			case "mayor" -> new Item[] {Items.BOOK, Items.WRITABLE_BOOK};
			default -> new Item[] {Items.COOKIE};
		};
		for (Item i : loved) if (s.is(i)) return Taste.LOVED;
		if (s.getItem() instanceof net.minecraft.world.item.BlockItem bi && bi.getBlock().defaultBlockState().is(net.minecraft.tags.BlockTags.SMALL_FLOWERS)) return prof.equals("florist") ? Taste.LOVED : Taste.LIKED;
		if (s.is(Items.COOKIE) || s.is(Items.BREAD) || s.is(Items.APPLE) || s.is(Items.EMERALD)) return Taste.LIKED;
		return Taste.NEUTRAL;
	}

	/**
	 * Dá o presente: teto de 2 por aldeão e por semana; adorado/gostado = coração, «sim» com a cabeça, mexericos vanilla (preços
	 * melhores com ele) e reputação na vila (teto por dia); detestado = abana a cabeça e devolve. Devolve o gosto (ou null se recusou).
	 */
	public static Taste gift(ServerPlayer p, ServerLevel level, Village v, Villager vi, ItemStack held) {
		LifeData d = v.civic.life;
		long day = VillageEconomy.day(level);
		String key = giftKey(p.getUUID(), vi.getUUID(), LifeRules.week(day));
		int given = d.gifts.getOrDefault(key, 0);
		Gestures.glance(vi, p);
		if (!LifeRules.mayGift(given)) {
			Gestures.no(vi);
			p.sendSystemMessage(Component.translatable("tvilas.gift.enough"), true);
			return null;
		}
		String prof = VillagerRequests.profession(vi);
		Taste taste = taste(prof, held);
		Component name = vi.getVillagerData().profession().value().name();
		if (!LifeRules.keepsGift(taste)) {
			Gestures.no(vi);
			Balloons.show(vi, Balloons.ANGRY, 50);
			p.sendSystemMessage(Component.literal("✖ ").append(Component.translatable("tvilas.gift.disliked", name, held.getHoverName())), true);
			return taste;
		}
		held.consume(1, p);
		LifeData.putCapped(d.gifts, key, given + 1);
		String repKey = p.getUUID() + ":" + day;
		int repToday = d.giftRep.getOrDefault(repKey, 0);
		int rep = LifeRules.giftReputation(taste, repToday);
		int gossip = LifeRules.giftGossip(taste);
		if (gossip > 0) vi.getGossips().add(p.getUUID(), GossipType.MINOR_POSITIVE, gossip);
		if (rep > 0) {
			LifeData.putCapped(d.giftRep, repKey, repToday + rep);
			v.reputation.merge(p.getUUID(), rep, Integer::sum);
		}
		Gestures.nod(level, vi);
		Balloons.show(vi, taste == Taste.LOVED ? Balloons.HEART : taste == Taste.LIKED ? Balloons.NOTE : Balloons.TALK, 60);
		if (taste != Taste.NEUTRAL) level.sendParticles(ParticleTypes.HEART, vi.getX(), vi.getY() + 2.1, vi.getZ(), taste == Taste.LOVED ? 5 : 2, 0.3, 0.2, 0.3, 0);
		Talk.fact(level, v, vi.blockPosition(), "gift", p.getGameProfile().name(), prof);
		p.sendSystemMessage(Component.literal(taste == Taste.LOVED ? "♥ " : "✔ ").append(Component.translatable("tvilas.gift." + taste.name().toLowerCase(), name, rep)),
				true);
		VillageData.get(level).changed();
		gifts++;
		return taste;
	}

	public static int gifts, thanks;

	// ---------------------------------------------------------------- presente de volta (gratidão)

	/** Um aldeão «Amigo» perto do jogador dá-lhe um presente da profissão (os presentes vanilla do Herói da Aldeia), 1×/semana. */
	public static boolean maybeThank(ServerLevel level, Village v, Villager vi, ServerPlayer p) {
		LifeData d = v.civic.life;
		long day = VillageEconomy.day(level), week = LifeRules.week(day);
		String k = p.getUUID() + ":" + vi.getUUID();
		int tier = LifeRules.villagerTier(vi.getPlayerReputation(p));
		if (!LifeRules.mayThank(tier, d.thanks.getOrDefault(k, Long.MIN_VALUE), week, d.thanksDay.getOrDefault(p.getUUID(), Long.MIN_VALUE), day)) return false;
		String prof = vi.isBaby() ? "baby" : VillagerRequests.profession(vi);
		Identifier table = Identifier.withDefaultNamespace("gameplay/hero_of_the_village/" + prof + "_gift");
		LootTable lt = level.getServer().reloadableRegistries().getLootTable(ResourceKey.create(net.minecraft.core.registries.Registries.LOOT_TABLE, table));
		if (lt == LootTable.EMPTY) lt = level.getServer().reloadableRegistries().getLootTable(ResourceKey.create(net.minecraft.core.registries.Registries.LOOT_TABLE,
				Identifier.withDefaultNamespace("gameplay/hero_of_the_village/unemployed_gift")));
		LootParams params = new LootParams.Builder(level).withParameter(LootContextParams.ORIGIN, vi.position()).withParameter(LootContextParams.THIS_ENTITY, vi)
				.create(LootContextParamSets.GIFT);
		List<ItemStack> items = lt.getRandomItems(params);
		LifeData.putCapped(d.thanks, k, week);
		LifeData.putCapped(d.thanksDay, p.getUUID(), day);
		for (ItemStack s : items) {
			if (s.is(Items.EMERALD)) continue; // nunca dinheiro
			net.minecraft.world.entity.ai.behavior.BehaviorUtils.throwItem(vi, s, p.position());
		}
		Gestures.glance(vi, p);
		Balloons.show(vi, Balloons.HEART, 60);
		VillageNews.tell(p, "🎁", "tvilas.gift.thanks", vi.getVillagerData().profession().value().name());
		VillageData.get(level).changed();
		thanks++;
		return true;
	}

	static Vec3 unused() {
		return Vec3.ZERO;
	}
}
