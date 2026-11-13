package com.tcordeiro.vilas.life;

import com.tcordeiro.vilas.TVilas;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * 0.4.0 — Assento invisível para os aldeões se sentarem nas ESCADAS (sem o Móveis; com ele usam o assento do Móveis nas cadeiras).
 * Desaparece sozinho quando ninguém está sentado, a escada sai, ou quem está sentado já não tem a tarefa (ex.: depois de recarregar
 * o mundo). Tem de ser «guardável»: o jogo não deixa montar entidades que não se guardam.
 */
public class LifeSeat extends Entity {
	public static EntityType<LifeSeat> TYPE;

	public static void init() {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, TVilas.id("seat"));
		TYPE = Registry.register(BuiltInRegistries.ENTITY_TYPE, key, EntityType.Builder.<LifeSeat>of(LifeSeat::new, MobCategory.MISC)
				.sized(0.001f, 0.001f).noSummon().clientTrackingRange(10).build(key));
	}

	public LifeSeat(EntityType<? extends LifeSeat> type, Level level) {
		super(type, level);
		this.noPhysics = true;
	}

	public static LifeSeat create(Level level, BlockPos pos, double height, float yaw) {
		LifeSeat s = new LifeSeat(TYPE, level);
		s.setPos(pos.getX() + 0.5, pos.getY() + height, pos.getZ() + 0.5);
		s.setYRot(yaw);
		return s;
	}

	@Override
	public void tick() {
		super.tick();
		if (level().isClientSide()) return;
		if (getPassengers().isEmpty() || !(level().getBlockState(blockPosition()).getBlock() instanceof StairBlock)
				|| getPassengers().get(0) instanceof net.minecraft.world.entity.npc.villager.Villager v && Tasks.get(v) == null) {
			ejectPassengers();
			discard();
		}
	}

	@Override
	protected Vec3 getPassengerAttachmentPoint(Entity passenger, EntityDimensions dimensions, float scale) {
		return Vec3.ZERO;
	}

	@Override
	protected void positionRider(Entity passenger, Entity.MoveFunction moveFunction) {
		super.positionRider(passenger, moveFunction);
		if (passenger instanceof net.minecraft.world.entity.LivingEntity le) le.setYBodyRot(getYRot());
	}

	@Override
	protected boolean canAddPassenger(Entity passenger) {
		return getPassengers().isEmpty();
	}

	@Override
	public PushReaction getPistonPushReaction() {
		return PushReaction.IGNORE_ENTITY;
	}

	@Override
	public boolean isIgnoringBlockTriggers() {
		return true;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		return false;
	}
}
