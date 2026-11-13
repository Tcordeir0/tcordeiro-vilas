package com.tcordeiro.vilas.client;

import com.tcordeiro.vilas.life.LifeSeat;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.renderer.entity.NoopRenderer;

/** 0.4.0 — O primeiro código de cliente do Vilas: os balões de emoção e as falas por cima dos aldeões, e o assento invisível. */
public class VilasClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		EntityRendererRegistry.register(LifeSeat.TYPE, NoopRenderer::new);
		BalloonClient.init();
	}
}
