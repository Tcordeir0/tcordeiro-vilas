package traben.entity_model_features;

/**
 * Só para COMPILAR o Vilas (nunca vai no jar): a forma do gancho de animação do Entity Model Features 3.3.11, confirmada com
 * javap no jar instalado. No jogo usa-se a classe verdadeira do EMF (o Vilas só a carrega quando o EMF está instalado).
 */
public interface EMFAnimationApi {
	abstract class EMFAnimationHook {
		public EMFAnimationHook() {}

		public boolean onAnimationStart(AnimationContext ctx, boolean b) {
			return true;
		}

		public void onAnimationEnd(AnimationContext ctx, boolean b) {}

		public static final class AnimationContext {}
	}
}
