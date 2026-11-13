package com.tcordeiro.vilas.life;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.mojang.math.Transformation;
import com.tcordeiro.vilas.mixin.DisplayAccessor;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * 0.5.0 — Cenário temporário (a boia do pescador, as bancas do mercado): mostradores de item vanilla com um modelo do Vilas feito no
 * Blender. Não têm colisão, não se apanham e não ficam no mundo: o Vilas apaga-os no fim da tarefa/do mercado, e um mostrador do
 * Vilas que volte a carregar sem ser desta sessão (o servidor caiu a meio) é apagado logo ao carregar.
 */
public final class Stage {
	private Stage() {}

	public static final String TAG = "tvilas.stage";
	/** Os mostradores vivos desta sessão (os outros com a etiqueta são restos e apagam-se ao carregar). */
	static final Set<UUID> LIVE = new HashSet<>();

	public static Display.ItemDisplay spawn(ServerLevel level, Item item, double x, double y, double z, float scale, float yawDeg) {
		Display.ItemDisplay d = EntityTypes.ITEM_DISPLAY.create(level, EntitySpawnReason.TRIGGERED);
		if (d == null) return null;
		d.getSlot(0).set(new ItemStack(item));
		d.snapTo(x, y, z, 0, 0);
		d.addTag(TAG);
		((DisplayAccessor) d).tvilas$setTransformation(new Transformation(new Vector3f(), new Quaternionf().rotationY((float) Math.toRadians(-yawDeg)),
				new Vector3f(scale, scale, scale), new Quaternionf()));
		((DisplayAccessor) d).tvilas$setViewRange(0.75f);
		((DisplayAccessor) d).tvilas$setPosRotInterpolationDuration(6);
		LIVE.add(d.getUUID());
		level.addFreshEntity(d);
		return d;
	}

	public static void remove(Entity e) {
		if (e == null) return;
		LIVE.remove(e.getUUID());
		e.discard();
	}

	public static boolean isStage(Entity e) {
		return e instanceof Display.ItemDisplay && e.entityTags().contains(TAG);
	}

	/** Ao carregar: um mostrador do Vilas que não é desta sessão é um resto — apaga-se. */
	public static void onLoad(Entity e) {
		if (isStage(e) && !LIVE.contains(e.getUUID())) e.discard();
	}
}
