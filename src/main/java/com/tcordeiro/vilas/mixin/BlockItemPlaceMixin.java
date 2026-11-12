package com.tcordeiro.vilas.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.tcordeiro.vilas.village.VillageManager;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;

/** Depois de um jogador pôr um bloco com sucesso: conta para as missões de decorar/construir da vila. */
@Mixin(BlockItem.class)
public abstract class BlockItemPlaceMixin {
	@Inject(method = "place", at = @At("RETURN"))
	private void tvilas$afterPlace(BlockPlaceContext ctx, CallbackInfoReturnable<InteractionResult> cir) {
		if (cir.getReturnValue().consumesAction() && ctx.getPlayer() instanceof ServerPlayer p)
			VillageManager.onPlaced(p, ctx.getClickedPos(), p.level().getBlockState(ctx.getClickedPos()));
	}
}
