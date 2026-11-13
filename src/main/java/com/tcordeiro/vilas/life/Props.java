package com.tcordeiro.vilas.life;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/**
 * 0.4.0 — Itens «de cena» na mão do aldeão (pão a comer, martelo na bancada, lanterna à noite, poção do clérigo): a camada vanilla
 * dos braços cruzados desenha-os. São marcados ({@code tvilas_prop}), NUNCA caem (chance 0, e são tirados ao morrer e ao carregar)
 * e só se põem se a mão estiver vazia — não são itens a sair do nada.
 */
public final class Props {
	private Props() {}

	public static final String TAG = "tvilas_prop";

	public static boolean isProp(ItemStack s) {
		CustomData d = s.get(DataComponents.CUSTOM_DATA);
		return d != null && d.copyTag().getBooleanOr(TAG, false);
	}

	/** Põe o item na mão por {@code ticks} (se a mão estiver vazia ou já com um item de cena). */
	public static boolean hold(Villager v, ItemStack stack, int ticks) {
		ItemStack cur = v.getItemBySlot(EquipmentSlot.MAINHAND);
		if (!cur.isEmpty() && !isProp(cur)) return false;
		ItemStack s = stack.copyWithCount(1);
		CompoundTag t = new CompoundTag();
		t.putBoolean(TAG, true);
		s.set(DataComponents.CUSTOM_DATA, CustomData.of(t));
		v.setItemSlot(EquipmentSlot.MAINHAND, s);
		v.setDropChance(EquipmentSlot.MAINHAND, 0f);
		((LifeVillager) v).tvilas$memo().propUntil = v.level().getGameTime() + ticks;
		return true;
	}

	public static boolean holding(Villager v) {
		return isProp(v.getItemBySlot(EquipmentSlot.MAINHAND));
	}

	public static void clear(Villager v) {
		if (holding(v)) v.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
		((LifeVillager) v).tvilas$memo().propUntil = 0;
		((LifeVillager) v).tvilas$memo().lantern = false;
	}

	/** Tira o item de cena se já passou o tempo. */
	public static void expire(Villager v, long now) {
		LifeVillager.Memo m = ((LifeVillager) v).tvilas$memo();
		if (m.propUntil != 0 && now >= m.propUntil) clear(v);
	}
}
