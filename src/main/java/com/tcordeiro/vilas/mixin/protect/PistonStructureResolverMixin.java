package com.tcordeiro.vilas.mixin.protect;

import java.util.List;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.tcordeiro.vilas.village.VillageManager;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.piston.PistonStructureResolver;

/**
 * Terrenos e pistões: o pistão só mexe se TODAS as posições envolvidas — os blocos movidos (de onde saem e para onde vão), os
 * que parte, e a cabeça ao estender — puderem ser mexidas a partir da posição do pistão ({@link VillageManager#mayCross}).
 * Um pistão fora de terrenos é «sem dono»: não empurra para dentro de um terreno nem puxa blocos de lá. Se falhar, o
 * {@code resolve()} devolve false e o vanilla trata-o como um bloco imóvel (o pistão não estende / não puxa).
 */
@Mixin(PistonStructureResolver.class)
public abstract class PistonStructureResolverMixin {
	@Shadow @Final private Level level;
	@Shadow @Final private BlockPos pistonPos;
	@Shadow @Final private boolean extending;
	@Shadow @Final private BlockPos startPos;
	@Shadow @Final private Direction pushDirection;
	@Shadow @Final private List<BlockPos> toPush;
	@Shadow @Final private List<BlockPos> toDestroy;

	@Inject(method = "resolve", at = @At("RETURN"), cancellable = true)
	private void tvilas$respectLots(CallbackInfoReturnable<Boolean> cir) {
		if (!cir.getReturnValueZ() || !(level instanceof ServerLevel sl) || VillageManager.data(sl).lots.isEmpty()) return;
		if (extending && !VillageManager.mayCross(sl, pistonPos, startPos)) {
			cir.setReturnValue(false);
			return;
		}
		for (int i = 0; i < toPush.size(); i++) {
			BlockPos p = toPush.get(i);
			if (!VillageManager.mayCross(sl, pistonPos, p) || !VillageManager.mayCross(sl, pistonPos, p.relative(pushDirection))) {
				cir.setReturnValue(false);
				return;
			}
		}
		for (int i = 0; i < toDestroy.size(); i++)
			if (!VillageManager.mayCross(sl, pistonPos, toDestroy.get(i))) {
				cir.setReturnValue(false);
				return;
			}
	}
}
