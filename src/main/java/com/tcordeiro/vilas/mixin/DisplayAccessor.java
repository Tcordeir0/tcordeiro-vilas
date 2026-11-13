package com.tcordeiro.vilas.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import com.mojang.math.Transformation;

import net.minecraft.world.entity.Display;

/** 0.5.0 — A escala/rotação e a interpolação dos mostradores do Vilas (boia do pescador, bancas do mercado). */
@Mixin(Display.class)
public interface DisplayAccessor {
	@Invoker("setTransformation")
	void tvilas$setTransformation(Transformation t);

	@Invoker("setPosRotInterpolationDuration")
	void tvilas$setPosRotInterpolationDuration(int ticks);

	@Invoker("setViewRange")
	void tvilas$setViewRange(float range);
}
