package com.tcordeiro.vilas.life;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import com.tcordeiro.vilas.compat.Mods;
import com.tcordeiro.vilas.logic.LifeRules;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/**
 * 0.4.0 — A temperatura para a reação ao frio: com o Clima, a do Clima ({@code ClimaApi.celsius}, por reflexão — sem depender
 * dele); sem ele, um bioma de neve conta como frio (0 °C) e o resto como ameno (15 °C). Também serve para os testes forçarem o frio.
 */
public final class Climate {
	private Climate() {}

	public static final String CLIMA = "tclima";
	private static MethodHandle CELSIUS;
	private static boolean tried;
	/** Os testes podem forçar a temperatura (NaN = não forçada). */
	public static double forced = Double.NaN;

	/** Os testes podem forçar a chuva num sítio (null = a do mundo). */
	public static Boolean forcedRain;

	/** Chove em cima deste bloco? (com o teste a poder forçar) */
	public static boolean raining(ServerLevel level, BlockPos pos) {
		return forcedRain != null ? forcedRain && level.canSeeSky(pos) : level.isRainingAt(pos);
	}

	public static boolean raining(ServerLevel level) {
		return forcedRain != null ? forcedRain : level.isRaining();
	}

	public static boolean clima() {
		return Mods.loaded(CLIMA);
	}

	public static double celsius(ServerLevel level, BlockPos pos, long tod) {
		if (!Double.isNaN(forced)) return forced;
		if (!tried) {
			tried = true;
			if (clima()) try {
				Class<?> c = Class.forName("com.tcordeiro.clima.ClimaApi");
				CELSIUS = MethodHandles.publicLookup().findStatic(c, "celsius", MethodType.methodType(double.class, Level.class, BlockPos.class));
			} catch (ReflectiveOperationException | LinkageError e) {
				CELSIUS = null;
			}
		}
		if (CELSIUS != null) try {
			return (double) CELSIUS.invokeExact((Level) level, pos);
		} catch (Throwable t) {
			// cai para o vanilla
		}
		return level.getBiome(pos).value().coldEnoughToSnow(pos, level.getSeaLevel()) ? 0 : LifeRules.COLD_C + 10;
	}
}
