package com.tcordeiro.vilas.client;

import java.lang.reflect.Method;
import java.util.UUID;

import com.tcordeiro.vilas.logic.GestureRules;

import net.minecraft.client.model.geom.ModelPart;
import traben.entity_model_features.EMFAnimationApi;

/**
 * 0.5.0 — O gancho no Entity Model Features: depois de o EMF (e o pacote que estiver ativo, por exemplo o Fresh Animations) animar um
 * aldeão, se ele tiver um gesto em curso soma-se um pouco à cabeça e ao bloco dos braços ({@link GestureRules#deltas}). Nada de
 * ficheiros do Fresh Animations nem modelos novos: as animações dele ficam todas; o gesto vai por cima. Esta classe só é carregada
 * quando o EMF está instalado.
 */
public final class EmfGestureHook extends EMFAnimationApi.EMFAnimationHook {
	private static Method root, state, entity, uuid;
	/** Para o teste no jogo: chamadas do gancho e porque não mexeu. */
	public static int calls, noEntity, noGesture, noParts;
	private static volatile boolean packActive;
	/** Para o teste no jogo: com o pacote ativo, os valores dos braços/cabeça vistos durante e fora dos gestos (prova de que o EMF os lê). */
	public static float minArmsDuring = Float.MAX_VALUE, minArmsOutside = Float.MAX_VALUE;

	/**
	 * Chamado em cada recarga dos pacotes de recursos (ouvinte registado pelo {@link GestureClient}): o pacote «tvilas_animacoes»
	 * (ou outro que leia {@code tvilas_gesture}) está ativo? Então é ele que faz os gestos e o gancho não soma nada (senão o gesto ia a
	 * dobrar); sem ele, o gancho faz os gestos por cima do que houver.
	 */
	static void recheck(net.minecraft.server.packs.resources.ResourceManager rm) {
		boolean found = false;
		for (String path : new String[] {"optifine/cem/villager_animations.jpm", "optifine/cem/villager.jem", "emf/cem/villager.jem"}) {
			var res = rm.getResource(net.minecraft.resources.Identifier.withDefaultNamespace(path));
			if (res.isEmpty()) continue;
			try (var in = res.get().open()) {
				found |= new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).contains("tvilas_gesture");
			} catch (java.io.IOException e) {
				// ilegível: o gancho faz os gestos
			}
		}
		packActive = found;
		minArmsDuring = minArmsOutside = Float.MAX_VALUE;
		com.tcordeiro.vilas.TVilas.LOGGER.info("Vilas: pacote de gestos {}", found ? "ATIVO (o pacote anima; o gancho fica de lado)" : "ausente (o gancho anima)");
	}

	static boolean packActive() {
		return packActive;
	}

	static Object create() {
		return new EmfGestureHook();
	}

	@Override
	public void onAnimationEnd(AnimationContext ctx, boolean b) {
		calls++;
		if (GestureClient.ACTIVE.isEmpty()) return;
		try {
			// o aldeão animado: contexto → estado → entidade do EMF → UUID
			if (state == null) {
				state = ctx.getClass().getMethod("activeState");
				root = ctx.getClass().getMethod("animatingModelRoot");
			}
			Object st = state.invoke(ctx);
			if (st == null) return;
			if (entity == null) entity = Class.forName("traben.entity_model_features.models.animation.state.EMFEntityRenderState").getMethod("emfEntity");
			Object en = entity.invoke(st);
			if (en == null) {
				noEntity++;
				return;
			}
			if (uuid == null) uuid = Class.forName("traben.entity_texture_features.utils.ETFEntity").getMethod("etf$getUuid");
			UUID id = (UUID) uuid.invoke(en);
			float code = id == null ? 0 : GestureClient.code(id);
			if (!(root.invoke(ctx) instanceof ModelPart r) || !r.hasChild("head") || !r.hasChild("arms")) {
				noParts++;
				return;
			}
			if (packActive()) { // o pacote anima: só se observa (o teste compara os braços durante e fora do gesto)
				if (id != null && GestureClient.ACTIVE.containsKey(id)) {
					float ax = r.getChild("arms").xRot;
					if (code > 0) minArmsDuring = Math.min(minArmsDuring, ax);
					else minArmsOutside = Math.min(minArmsOutside, ax);
				}
				return;
			}
			if (code <= 0) {
				noGesture++;
				return;
			}
			float[] d = GestureRules.deltas((int) code, GestureClient.progress(id));
			ModelPart head = r.getChild("head"), arms = r.getChild("arms");
			head.xRot += d[GestureRules.HEAD_X];
			head.yRot += d[GestureRules.HEAD_Y];
			head.zRot += d[GestureRules.HEAD_Z];
			arms.xRot += d[GestureRules.ARMS_X];
			arms.zRot += d[GestureRules.ARMS_Z];
			arms.y += d[GestureRules.ARMS_Y];
			GestureClient.applied++;
		} catch (ReflectiveOperationException | RuntimeException e) {
			// um modelo de aldeão sem estas peças (outro pacote): fica como está
		}
	}

	public static boolean packActiveForTest() {
		return packActive;
	}
}
