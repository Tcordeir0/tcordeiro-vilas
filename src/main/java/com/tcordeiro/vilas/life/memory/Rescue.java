package com.tcordeiro.vilas.life.memory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.tcordeiro.vilas.life.Balloons;
import com.tcordeiro.vilas.life.Gestures;
import com.tcordeiro.vilas.life.Mail;
import com.tcordeiro.vilas.life.TownData;
import com.tcordeiro.vilas.logic.LifeRules;
import com.tcordeiro.vilas.logic.LoreRules;
import com.tcordeiro.vilas.logic.TownRules;
import com.tcordeiro.vilas.village.Lot;
import com.tcordeiro.vilas.village.Village;
import com.tcordeiro.vilas.village.VillageData;
import com.tcordeiro.vilas.village.VillageEconomy;
import com.tcordeiro.vilas.village.VillageManager;
import com.tcordeiro.vilas.village.VillageNews;
import com.tcordeiro.vilas.village.VillagerRequests;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.gossip.GossipType;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

/**
 * 0.6.0 — «Você me salvou»: um jogador mata o monstro que estava a atacar um aldeão da vila (era o alvo dele, ou bateu-lhe há menos
 * de 10 s). O aldeão grava a recordação, agradece (coração, aceno, fala), ganha opinião do jogador (mexericos vanilla e reputação da
 * vila, uma vez por dia) e manda uma CARTA de agradecimento: com o Móveis, pelo Carteiro até à caixa de correio do terreno do jogador;
 * sem ele (ou sem caixa), uma mensagem e um presente na hora. Uma carta por jogador por semana; o presente é um item da profissão,
 * nunca dinheiro.
 */
public final class Rescue {
	private Rescue() {}

	/** Monstro → (aldeão que atacou, quando). Em memória, com teto. */
	static final Map<UUID, Object[]> HITS = new LinkedHashMap<>();

	/** Um monstro bateu num aldeão (chamado pelo dano dos aldeões). */
	public static void hit(net.minecraft.world.entity.Entity mob, Villager vi, long now) {
		if (HITS.size() >= 256) HITS.remove(HITS.keySet().iterator().next());
		HITS.put(mob.getUUID(), new Object[] {vi.getUUID(), now});
	}

	/** Um monstro morreu: foi um jogador que salvou um aldeão da vila? */
	public static void onDeath(LivingEntity dead, net.minecraft.world.damagesource.DamageSource source) {
		if (!(dead instanceof Enemy) || !(dead.level() instanceof ServerLevel level) || !(source.getEntity() instanceof ServerPlayer p)) return;
		Object[] h = HITS.remove(dead.getUUID());
		Villager vi = dead instanceof Mob m && m.getTarget() instanceof Villager t && t.isAlive() ? t : null;
		long last = -1;
		if (vi == null && h != null && level.getEntity((UUID) h[0]) instanceof Villager t && t.isAlive()) {
			vi = t;
			last = (long) h[1];
		}
		if (vi == null || !LoreRules.rescue(dead instanceof Mob m && m.getTarget() == vi, last, level.getGameTime())) return;
		Village v = VillageManager.villageAt(level, vi.blockPosition());
		if (v == null) return;
		saved(level, v, vi, p);
	}

	/** O aldeão foi salvo por este jogador. Devolve true se a reputação foi dada (uma vez por dia). */
	public static boolean saved(ServerLevel level, Village v, Villager vi, ServerPlayer p) {
		Lore l = v.civic.life.lore;
		long day = VillageEconomy.day(level);
		String name = p.getGameProfile().name();
		Memories.remember(level, v, vi, Memories.SAVED, p.getUUID(), name);
		Gestures.glance(vi, p);
		Balloons.say(vi, Balloons.HEART, Component.translatable("tvilas.rescue.thanks", name), 80);
		Gestures.wave(vi);
		String prof = VillagerRequests.profession(vi);
		Chronicle.add(level, v, "saved", name, profArg(vi));
		String k = p.getUUID() + ":" + vi.getUUID();
		boolean rewarded = false;
		if (LoreRules.mayRewardSave(l.saved.getOrDefault(k, Long.MIN_VALUE), day)) {
			Lore.putCapped(l.saved, k, day);
			vi.getGossips().add(p.getUUID(), GossipType.MINOR_POSITIVE, LoreRules.SAVE_GOSSIP);
			v.reputation.merge(p.getUUID(), LoreRules.SAVE_REP, Integer::sum);
			rewarded = true;
			rewards++;
		}
		long week = LifeRules.week(day);
		if (LoreRules.mayWriteLetter(l.letters.getOrDefault(p.getUUID(), Long.MIN_VALUE), week)) {
			Lore.putCapped(l.letters, p.getUUID(), week);
			letter(level, v, vi, p, prof, day);
		}
		saves++;
		VillageData.get(level).changed();
		return rewarded;
	}

	public static int saves, rewards, letters, gifts;

	/** A profissão como argumento da crónica («#chave» da tradução; fala-se na língua de quem lê). */
	public static String profArg(Villager vi) {
		Component n = vi.getVillagerData().profession().value().name();
		return n.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t ? "#" + t.getKey() : n.getString();
	}

	/** A carta: pelo Carteiro se der (Móveis + caixa de correio num terreno do jogador nesta vila), senão mensagem e presente já. */
	static void letter(ServerLevel level, Village v, Villager vi, ServerPlayer p, String prof, long day) {
		Component from = vi.getVillagerData().profession().value().name();
		List<ItemStack> items = new ArrayList<>();
		items.add(letterBook(from, p.getGameProfile().name(), day));
		ItemStack gift = gift(level, vi);
		if (!gift.isEmpty()) items.add(gift);
		BlockPos box = Mail.available() ? mailbox(level, v, p.getUUID()) : null;
		TownData t = v.civic.life.town;
		if (box != null && t.mail.size() < TownRules.MAIL_PER_VILLAGE) {
			TownData.Parcel parcel = new TownData.Parcel(++t.counter, vi.getUUID(), from.getString(), p.getUUID(), p.getGameProfile().name(), box.asLong(), day);
			parcel.npc = true;
			parcel.items.addAll(items);
			t.mail.add(parcel);
			letters++;
			VillageNews.tell(p, "✉", "tvilas.rescue.letter_coming", from);
			return;
		}
		for (ItemStack s : items) p.getInventory().placeItemBackInInventory(s, net.minecraft.util.Prediction.SERVER_ONLY);
		gifts++;
		VillageNews.tell(p, "🎁", "tvilas.rescue.gift", from);
	}

	static BlockPos mailbox(ServerLevel level, Village v, UUID player) {
		for (Lot lot : VillageManager.lotsOf(level, v)) {
			if (!lot.owner.equals(player)) continue;
			BlockPos b = Mail.mailboxIn(level, lot);
			if (b != null) return b;
		}
		return null;
	}

	/** O texto da carta (um livro escrito curto, gerado agora). */
	static ItemStack letterBook(Component from, String to, long day) {
		ItemStack b = new ItemStack(Items.WRITTEN_BOOK);
		Component page = Component.translatable("tvilas.rescue.letter_text", to, day, from);
		b.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(Filterable.passThrough("Obrigado"), from.getString(), 0,
				List.of(Filterable.passThrough(page)), true));
		b.set(DataComponents.CUSTOM_NAME, Component.translatable("tvilas.rescue.letter_name", from));
		return b;
	}

	/** O presente: os presentes vanilla do Herói da Aldeia da profissão (sem esmeraldas: nunca dinheiro). */
	public static ItemStack gift(ServerLevel level, Villager vi) {
		String prof = vi.isBaby() ? "baby" : VillagerRequests.profession(vi);
		LootTable lt = level.getServer().reloadableRegistries().getLootTable(ResourceKey.create(Registries.LOOT_TABLE,
				Identifier.withDefaultNamespace("gameplay/hero_of_the_village/" + prof + "_gift")));
		if (lt == LootTable.EMPTY) lt = level.getServer().reloadableRegistries().getLootTable(ResourceKey.create(Registries.LOOT_TABLE,
				Identifier.withDefaultNamespace("gameplay/hero_of_the_village/unemployed_gift")));
		LootParams params = new LootParams.Builder(level).withParameter(LootContextParams.ORIGIN, vi.position()).withParameter(LootContextParams.THIS_ENTITY, vi)
				.create(LootContextParamSets.GIFT);
		for (ItemStack s : lt.getRandomItems(params)) if (!s.isEmpty() && !s.is(Items.EMERALD)) return s;
		return new ItemStack(Items.BREAD);
	}
}
