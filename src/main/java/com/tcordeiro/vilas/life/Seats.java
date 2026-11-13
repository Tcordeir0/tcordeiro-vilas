package com.tcordeiro.vilas.life;

import java.lang.reflect.Method;

import com.tcordeiro.vilas.compat.Mods;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.phys.AABB;

/**
 * 0.4.0 — Onde os aldeões se sentam: nas cadeiras, bancos e sofás do Móveis (o assento do próprio Móveis, por reflexão: um jogador
 * vê o lugar ocupado) e, sem o Móveis, nas escadas de baixo (com o assento invisível {@link LifeSeat}).
 */
public final class Seats {
	private Seats() {}

	private static Class<?> seatBlock;
	private static Method seatHeight, create, occupied;
	private static boolean tried;

	private static void reflect() {
		if (tried) return;
		tried = true;
		if (!Mods.loaded(Mods.MOVEIS)) return;
		try {
			seatBlock = Class.forName("com.tcordeiro.moveis.block.SeatBlock");
			seatHeight = seatBlock.getMethod("seatHeight", BlockState.class);
			Class<?> se = Class.forName("com.tcordeiro.moveis.entity.SeatEntity");
			create = se.getMethod("create", net.minecraft.world.level.Level.class, BlockPos.class, double.class, float.class);
			occupied = se.getMethod("occupied", net.minecraft.world.level.Level.class, BlockPos.class);
		} catch (ReflectiveOperationException | LinkageError e) {
			seatBlock = null;
		}
	}

	/** É um móvel do Móveis onde se senta? */
	public static boolean furniture(BlockState s) {
		reflect();
		return seatBlock != null && seatBlock.isInstance(s.getBlock());
	}

	/** Escada «de sentar» (meia de baixo, com ar por cima). */
	public static boolean stairs(BlockState s) {
		return s.getBlock() instanceof StairBlock && s.getValue(BlockStateProperties.HALF) == Half.BOTTOM;
	}

	public static boolean seat(BlockState s) {
		return furniture(s) || stairs(s);
	}

	/** Alguém (jogador ou aldeão) já está sentado neste bloco? */
	public static boolean taken(ServerLevel level, BlockPos pos) {
		return !level.getEntitiesOfClass(Entity.class, new AABB(pos), e -> !e.getPassengers().isEmpty()).isEmpty();
	}

	/** Para onde se fica virado: a frente do móvel (o Móveis guarda-a em «facing»); na escada, de costas para o degrau alto. */
	static float yaw(BlockState s) {
		if (!s.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) return 0;
		Direction f = s.getValue(BlockStateProperties.HORIZONTAL_FACING);
		return (stairs(s) ? f.getOpposite() : f).toYRot();
	}

	/** Senta o aldeão no bloco; false se não deu (ocupado, partido). */
	public static boolean sit(ServerLevel level, Villager v, BlockPos pos) {
		BlockState s = level.getBlockState(pos);
		if (!seat(s) || taken(level, pos) || v.isPassenger()) return false;
		Entity seat;
		try {
			if (furniture(s)) {
				if ((boolean) occupied.invoke(null, level, pos)) return false;
				seat = (Entity) create.invoke(null, level, pos, (double) seatHeight.invoke(s.getBlock(), s), yaw(s));
			} else seat = LifeSeat.create(level, pos, 0.5, yaw(s));
		} catch (ReflectiveOperationException | RuntimeException e) {
			return false;
		}
		level.addFreshEntity(seat);
		if (!v.startRiding(seat, true, false)) {
			seat.discard();
			return false;
		}
		v.setYRot(seat.getYRot());
		v.setYBodyRot(seat.getYRot());
		v.setYHeadRot(seat.getYRot());
		return true;
	}

	/** É um assento (o do Vilas ou o do Móveis)? */
	public static boolean isSeat(Entity e) {
		if (e instanceof LifeSeat) return true;
		return e != null && net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).toString().equals("tmoveis:seat");
	}

	static Block block(ServerLevel level, BlockPos pos) {
		return level.getBlockState(pos).getBlock();
	}
}
