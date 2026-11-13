package com.tcordeiro.vilas.client;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

import com.tcordeiro.vilas.TVilas;
import com.tcordeiro.vilas.life.Balloons;
import com.tcordeiro.vilas.logic.GestureRules;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

/**
 * 0.5.0 — Gestos próprios por variável do ENTITY MODEL FEATURES (EMF 3.3.11, API {@code traben.entity_model_features.EMFAnimationApi},
 * confirmada no jar instalado): o servidor manda «este aldeão acena/festeja/encolhe os ombros/aponta» ({@link Balloons.Gesture}) e o
 * cliente regista duas variáveis de animação:
 * <ul>
 * <li>{@code tvilas_gesture} — o código do gesto em curso (0 nenhum, 1 acenar, 2 festejar, 3 encolher os ombros, 4 sim, 5 não,
 * 6 apontar);</li>
 * <li>{@code tvilas_gesture_time} — de 0 a 1, quanto do gesto já passou.</li>
 * </ul>
 * Tudo por reflexão: sem o EMF nada disto é carregado e o Vilas funciona igual. O modelo do aldeão NÃO muda: só um pacote de recursos
 * que use as variáveis (por exemplo, por cima do Fresh Animations) mexe nas peças.
 */
public final class GestureClient {
	private GestureClient() {}

	record G(int code, long start, long until) {}

	static final Map<UUID, G> ACTIVE = new HashMap<>();
	private static Method current, uuidOf;
	public static boolean registered;

	public static void init() {
		ClientPlayNetworking.registerGlobalReceiver(Balloons.Gesture.TYPE, (msg, ctx) -> ctx.client().execute(() -> {
			var level = ctx.client().level;
			if (level == null) return;
			Entity e = level.getEntity(msg.entity());
			if (e == null) return;
			long now = level.getGameTime();
			ACTIVE.put(e.getUUID(), new G(msg.code(), now, now + Math.max(5, msg.ticks())));
			if (ACTIVE.size() > 256) ACTIVE.values().removeIf(g -> g.until < now);
		}));
		ClientPlayConnectionEvents.DISCONNECT.register((h, client) -> client.execute(ACTIVE::clear));
		if (FabricLoader.getInstance().isModLoaded("entity_model_features")) registerEmf();
	}

	/** O gesto em curso deste aldeão agora (para os testes e para o EMF). */
	public static float code(UUID id) {
		G g = ACTIVE.get(id);
		Minecraft mc = Minecraft.getInstance();
		if (g == null || mc.level == null) return 0f;
		return GestureRules.code(g.code, g.start, g.until, mc.level.getGameTime());
	}

	public static float progress(UUID id) {
		G g = ACTIVE.get(id);
		Minecraft mc = Minecraft.getInstance();
		if (g == null || mc.level == null) return 0f;
		return GestureRules.progress(g.start, g.until, mc.level.getGameTime());
	}

	/** A entidade que o EMF está a animar agora (EMFAnimationApi.getCurrentEntity → ETFEntity.etf$getUuid), ou null. */
	static UUID animating() {
		if (ACTIVE.isEmpty() || current == null) return null;
		try {
			Object e = current.invoke(null);
			return e == null ? null : (UUID) uuidOf.invoke(e);
		} catch (ReflectiveOperationException | ClassCastException ex) {
			return null;
		}
	}

	static void registerEmf() {
		try {
			Class<?> api = Class.forName("traben.entity_model_features.EMFAnimationApi");
			current = api.getMethod("getCurrentEntity");
			uuidOf = Class.forName("traben.entity_texture_features.utils.ETFEntity").getMethod("etf$getUuid");
			Method reg = api.getMethod("registerSingletonAnimationVariable", String.class, String.class, String.class, Supplier.class);
			Supplier<Float> code = () -> {
				UUID u = animating();
				return u == null ? 0f : code(u);
			};
			Supplier<Float> time = () -> {
				UUID u = animating();
				return u == null ? 0f : progress(u);
			};
			reg.invoke(null, TVilas.MOD_ID, "tvilas_gesture", "Vilas: gesture code of this villager (0 none, 1 wave, 2 cheer, 3 shrug, 4 yes, 5 no, 6 point)",
					code);
			reg.invoke(null, TVilas.MOD_ID, "tvilas_gesture_time", "Vilas: 0..1 progress of the current gesture", time);
			registered = true;
			TVilas.LOGGER.info("Vilas: variáveis do EMF registadas (tvilas_gesture, tvilas_gesture_time)");
		} catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
			TVilas.LOGGER.warn("Vilas: não consegui registar as variáveis do EMF ({}): os gestos ficam só nos balões", e.toString());
		}
	}
}
