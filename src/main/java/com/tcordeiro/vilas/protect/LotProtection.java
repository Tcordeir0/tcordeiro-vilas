package com.tcordeiro.vilas.protect;

import com.tcordeiro.vilas.village.VillageManager;

import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.FireChargeItem;
import net.minecraft.world.item.FlintAndSteelItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * Proteção dos terrenos: num terreno, só o dono e os amigos partem e põem blocos.
 * <ul>
 * <li>Partir: {@code PlayerBlockBreakEvents.BEFORE} — o mesmo evento que as máquinas do Industrial chamam em nome do dono
 * (com um jogador falso com o UUID do dono), por isso as mineradoras/lenhador também respeitam o terreno.</li>
 * <li>Pôr: {@code UseBlockCallback} com um item de bloco (na posição onde o bloco iria ficar) e itens que mudam blocos (baldes,
 * isqueiro, carga de fogo, enxadas, pás, machados); {@code UseItemCallback} para baldes apontados ao terreno.</li>
 * <li>De fora (0.1.1): líquidos, fogo, lava a acender, explosões, pistões e ejetores — mixins em {@code mixin/protect},
 * com {@link VillageManager#mayCross} e {@link VillageManager#explosionMayBreak}.</li>
 * </ul>
 */
public final class LotProtection {
	private LotProtection() {}

	public static void init() {
		PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, be) -> {
			if (!(world instanceof ServerLevel level)) return true;
			boolean ok = VillageManager.mayModify(level, pos, player);
			if (!ok) warn(player, level, pos);
			return ok;
		});

		UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
			if (!(world instanceof ServerLevel level)) return InteractionResult.PASS;
			ItemStack stack = player.getItemInHand(hand);
			BlockPos target = targetOf(player, hand, stack, hit);
			if (target == null || VillageManager.mayModify(level, target, player)) return InteractionResult.PASS;
			warn(player, level, target);
			return InteractionResult.FAIL;
		});

		UseItemCallback.EVENT.register((player, world, hand) -> {
			if (!(world instanceof ServerLevel level)) return InteractionResult.PASS;
			if (!(player.getItemInHand(hand).getItem() instanceof BucketItem)) return InteractionResult.PASS;
			Vec3 eye = player.getEyePosition();
			Vec3 end = eye.add(player.getViewVector(1.0f).scale(player.blockInteractionRange()));
			BlockHitResult hit = level.clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.SOURCE_ONLY, player));
			if (hit.getType() != HitResult.Type.BLOCK) return InteractionResult.PASS;
			if (VillageManager.mayModify(level, hit.getBlockPos(), player) && VillageManager.mayModify(level, hit.getBlockPos().relative(hit.getDirection()), player))
				return InteractionResult.PASS;
			warn(player, level, hit.getBlockPos());
			return InteractionResult.FAIL;
		});
	}

	/** Onde este clique mexeria num bloco (null = não mexe em blocos: abrir portas, baús, etc. continua livre). */
	static BlockPos targetOf(Player player, net.minecraft.world.InteractionHand hand, ItemStack stack, BlockHitResult hit) {
		Item item = stack.getItem();
		if (item instanceof BlockItem) return new BlockPlaceContext(player, hand, stack, hit).getClickedPos();
		if (item instanceof BucketItem || item instanceof FlintAndSteelItem || item instanceof FireChargeItem)
			return hit.getBlockPos().relative(hit.getDirection());
		if (stack.is(ItemTags.HOES) || stack.is(ItemTags.SHOVELS) || stack.is(ItemTags.AXES)) return hit.getBlockPos();
		return null;
	}

	private static void warn(Player player, ServerLevel level, BlockPos pos) {
		if (!(player instanceof ServerPlayer sp) || player instanceof net.fabricmc.fabric.api.entity.FakePlayer) return;
		var lot = VillageManager.lotAt(level, pos.getX(), pos.getZ());
		boolean frozen = lot != null && lot.mayModify(player.getUUID()) && VillageManager.frozen(level, lot);
		sp.sendSystemMessage(Component.translatable(frozen ? "tvilas.lot.frozen" : "tvilas.lot.protected"), true);
	}
}
