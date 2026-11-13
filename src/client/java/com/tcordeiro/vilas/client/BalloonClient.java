package com.tcordeiro.vilas.client;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.joml.Quaternionf;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tcordeiro.vilas.TVilas;
import com.tcordeiro.vilas.life.Balloons;
import com.tcordeiro.vilas.logic.LifeRules;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * 0.4.0 — Os balões no cliente: a lista que o servidor manda ({@link Balloons.Show}) e o desenho, por cima da cabeça, virado para a
 * câmara: o ícone 16×16 num quadrado de 0,5 bloco (aparece com um pequeno salto) e a fala curta no estilo das etiquetas de nome.
 * Só até 32 blocos; nada é alocado por fotograma a não ser o que o jogo já aloca nas etiquetas.
 */
public final class BalloonClient {
	private BalloonClient() {}

	static final class B {
		final String icon;
		final Component text;
		final long start, until;

		B(String icon, Component text, long start, long until) {
			this.icon = icon;
			this.text = text;
			this.start = start;
			this.until = until;
		}
	}

	static final Map<Integer, B> SHOWN = new HashMap<>();
	static final Map<String, RenderType> TYPES = new HashMap<>();
	private static final Quaternionf ROT = new Quaternionf();

	/** Para os testes de cliente: quantos balões estão à vista. */
	public static int count() {
		return SHOWN.size();
	}

	public static void init() {
		ClientPlayNetworking.registerGlobalReceiver(Balloons.Show.TYPE, (msg, ctx) -> ctx.client().execute(() -> {
			ClientLevel l = ctx.client().level;
			if (l == null) return;
			long now = l.getGameTime();
			SHOWN.put(msg.entity(), new B(msg.icon(), msg.text().orElse(null), now, now + Math.max(10, msg.ticks())));
		}));
		ClientPlayConnectionEvents.DISCONNECT.register((h, client) -> client.execute(SHOWN::clear));
		LevelRenderEvents.COLLECT_SUBMITS.register(BalloonClient::render);
	}

	static RenderType type(String icon) {
		return TYPES.computeIfAbsent(icon, k -> RenderTypes.entityCutout(Identifier.fromNamespaceAndPath(TVilas.MOD_ID, "textures/balloon/" + k + ".png")));
	}

	static void render(LevelRenderContext ctx) {
		Minecraft mc = Minecraft.getInstance();
		ClientLevel level = mc.level;
		if (level == null || SHOWN.isEmpty()) return;
		CameraRenderState cam = ctx.levelState().cameraRenderState;
		if (cam == null || cam.pos == null) return;
		long now = level.getGameTime();
		float pt = ctx.levelState().worldPartialTicks;
		Vec3 cp = cam.pos;
		PoseStack pose = ctx.poseStack();
		double max = LifeRules.BALLOON_RANGE * LifeRules.BALLOON_RANGE;
		for (Iterator<Map.Entry<Integer, B>> it = SHOWN.entrySet().iterator(); it.hasNext();) {
			Map.Entry<Integer, B> e = it.next();
			B b = e.getValue();
			Entity en = level.getEntity(e.getKey());
			if (en == null || en.isRemoved() || now >= b.until) {
				it.remove();
				continue;
			}
			Vec3 p = en.getPosition(pt);
			if (p.distanceToSqr(cp) > max) continue;
			double top = en.getBbHeight() + (en.isCustomNameVisible() ? 0.85 : 0.55);
			pose.pushPose();
			pose.translate(p.x - cp.x, p.y - cp.y, p.z - cp.z);
			if (b.text != null) {
				ctx.submitNodeCollector().submitNameTag(pose, new Vec3(0, top - 0.1, 0), 0, b.text, false, 0xF000F0, cam);
				top += 0.3;
			}
			if (b.icon != null && !b.icon.isEmpty()) {
				float age = (now - b.start) + pt;
				float pop = age < 4 ? 0.6f + 0.1f * age : 1f; // aparece com um pequeno salto
				float bob = (float) Math.sin(age * 0.15) * 0.03f;
				pose.translate(0, top + 0.25 + bob, 0);
				pose.rotate(ROT.set(cam.orientation));
				float s = 0.25f * pop;
				pose.scale(s, s, s);
				ctx.submitNodeCollector().submitCustomGeometry(pose, type(b.icon), QUAD);
			}
			pose.popPose();
		}
	}

	/** Um quadrado de −1..1 com a textura inteira, iluminado por completo. */
	static final SubmitNodeCollector.CustomGeometryRenderer QUAD = (PoseStack.Pose p, VertexConsumer vc) -> {
		vertex(vc, p, -1, -1, 0, 1);
		vertex(vc, p, 1, -1, 1, 1);
		vertex(vc, p, 1, 1, 1, 0);
		vertex(vc, p, -1, 1, 0, 0);
	};

	static void vertex(VertexConsumer vc, PoseStack.Pose p, float x, float y, float u, float v) {
		vc.addVertex(p, x, y, 0).setColor(0xFFFFFFFF).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(0xF000F0).setNormal(p, 0, 0, 1);
	}
}
