package com.tcordeiro.vilas.build;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

/** 0.3.0: a API de colunas do Industrial 0.12.2+ é encontrada por reflexão (e sem ela, cai-se na altura antiga). */
class PoleColumnApiTest {
	static class OldPole {}

	static class NewPoleBase {
		public static boolean placeColumn(Level level, BlockPos pos, net.minecraft.world.level.block.Block pole, Direction facing, int height, boolean led) {
			return true;
		}

		public static boolean placeColumn(Level level, BlockPos pos, net.minecraft.world.level.block.Block pole, Direction facing, int height) {
			return true;
		}
	}

	static class NewPole extends NewPoleBase {}

	static class WrongPole {
		public static boolean placeColumn(Level level, BlockPos pos, int height) {
			return true;
		}
	}

	@Test
	void findsTheApiOnlyWithTheRightShape() {
		assertNull(StreetLightKit.columnApi(OldPole.class), "Industrial antigo: sem API");
		assertNotNull(StreetLightKit.columnApi(NewPole.class), "0.12.2: na superclasse do bloco (PoleBlock)");
		assertEquals(6, StreetLightKit.columnApi(NewPole.class).getParameterCount(), "prefere a variante com «led» (pede sem LED)");
		assertNull(StreetLightKit.columnApi(WrongPole.class), "outra assinatura: não usa");
		assertEquals(4, StreetLightKit.POLE_HEIGHT, "postes de 4 blocos nas Vilas");
	}
}
