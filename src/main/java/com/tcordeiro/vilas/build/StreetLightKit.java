package com.tcordeiro.vilas.build;

import java.lang.reflect.Method;
import java.util.List;

import com.tcordeiro.vilas.TVilas;
import com.tcordeiro.vilas.compat.Mods;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * O que a iluminação pública põe em cada poste — integração OPCIONAL com a rede aérea do Industrial 0.11 (por ids e reflexão:
 * nada do Industrial é preciso para compilar).
 * <ul>
 * <li><b>Com o Industrial 0.11+</b>: postes {@code tindustrial:<madeira>_lamp_post} (3 blocos; 4 com a API de colunas do Industrial 0.12.2+) escolhidos pelo bioma da vila
 * (planície → carvalho, taiga/neve → abeto, savana → acácia, …) ou {@code tindustrial:iron_street_light} (4 blocos) no deserto.
 * Põe-se só o bloco de baixo e o Industrial completa o resto por cima. Os postes ligam-se com
 * {@code com.tcordeiro.industrial.grid.AerialWires.link(ServerLevel, BlockPos, BlockPos)} (cada poste ao mais perto já ligado,
 * ≤ 16 blocos) e a Prefeitura ganha 2 painéis solares e uma bateria, ligados ao primeiro poste: acendem à noite.</li>
 * <li><b>Sem o Industrial</b> (ou um Industrial sem postes, ≤ 0.10): poste de cerca com lanterna vanilla, sempre acesa.</li>
 * </ul>
 */
public abstract class StreetLightKit {
	public static final String WIRES_CLASS = "com.tcordeiro.industrial.grid.AerialWires";
	public static final Identifier IRON_POLE = Identifier.fromNamespaceAndPath(Mods.INDUSTRIAL, "iron_street_light");
	public static final Identifier SOLAR = Identifier.fromNamespaceAndPath(Mods.INDUSTRIAL, "basic_solar_panel");
	public static final Identifier BATTERY = Identifier.fromNamespaceAndPath(Mods.INDUSTRIAL, "battery");
	/** Vão máximo de um fio entre postes no Industrial (GridMath.POLE_SPAN). */
	public static final int POLE_SPAN = 16;
	/** Folga mínima (blocos) entre o fio e qualquer bloco com corpo (paredes, vidros, beirais), e o troço junto às fixações que não conta. */
	public static final double CLEARANCE = 0.6, END_SKIP = 0.5;

	/** 0.3.0: altura dos postes da iluminação pública com a API de colunas do Industrial (0.12.2+: postes de 2 a 6 blocos). */
	public static final int POLE_HEIGHT = 4;

	/** Classe do Industrial 0.12.2+ com a API de colunas. */
	public static final String POLE_CLASS = "com.tcordeiro.industrial.grid.PoleBlock";

	/**
	 * A API do Industrial 0.12.2+ para pôr um poste inteiro com a altura escolhida (2 a 6 blocos): o método estático
	 * {@code PoleBlock.placeColumn(Level, BlockPos pé, Block poste, Direction frente, int altura, boolean led)} (ou a variante sem
	 * {@code led}), procurado por reflexão na classe do bloco do poste e nas superclasses (é lá que está o {@code PoleBlock}).
	 * Null se não existir (Industrial mais antigo: põe-se só o pé e ele completa a altura de origem — 3 na madeira, 4 no ferro).
	 */
	static final java.util.Map<Class<?>, java.util.Optional<Method>> COLUMN_API = new java.util.concurrent.ConcurrentHashMap<>();

	public static Method columnApi(Block b) {
		return columnApi(b.getClass());
	}

	static Method columnApi(Class<?> blockClass) {
		return COLUMN_API.computeIfAbsent(blockClass, c -> {
			Method five = null;
			for (Class<?> k = c; k != null && k != Object.class; k = k.getSuperclass())
				for (Method m : k.getDeclaredMethods()) {
					if (!m.getName().equals("placeColumn") || !java.lang.reflect.Modifier.isStatic(m.getModifiers())) continue;
					Class<?>[] t = m.getParameterTypes();
					boolean head = t.length >= 5 && t[0].isAssignableFrom(ServerLevel.class) && t[1] == BlockPos.class && t[2].isAssignableFrom(Block.class)
							&& t[3] == net.minecraft.core.Direction.class && t[4] == int.class;
					if (!head) continue;
					if (t.length == 6 && t[5] == boolean.class) {
						m.setAccessible(true);
						return java.util.Optional.of(m); // a variante com «led» (preferida: pedimos sempre sem LED)
					}
					if (t.length == 5) five = m;
				}
			if (five != null) five.setAccessible(true);
			return java.util.Optional.ofNullable(five);
		}).orElse(null);
	}

	/**
	 * Põe o poste inteiro com a API de colunas do Industrial (se existir), com {@code height} blocos e SEM LED (o lampião amarelo
	 * de sempre). Devolve true se o pôs; false = não há API, ou o Industrial recusou (altura, espaço) — quem chama põe o pé como
	 * antes. A frente vem do estado do bloco (FACING), ou norte.
	 */
	public static boolean placeColumn(ServerLevel level, BlockPos pos, BlockState state, int height) {
		Method m = columnApi(state.getBlock());
		if (m == null) return false;
		net.minecraft.core.Direction facing = state.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING)
				? state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING) : net.minecraft.core.Direction.NORTH;
		try {
			Object r = m.getParameterCount() == 6 ? m.invoke(null, level, pos, state.getBlock(), facing, height, false)
					: m.invoke(null, level, pos, state.getBlock(), facing, height);
			return !(r instanceof Boolean ok) || ok;
		} catch (ReflectiveOperationException | RuntimeException e) {
			TVilas.LOGGER.warn("Vilas: a API de postes do Industrial falhou em {} ({}); ponho como antes", pos.toShortString(), e.toString());
			return false;
		}
	}

	public static Identifier woodPole(String wood) {
		return Identifier.fromNamespaceAndPath(Mods.INDUSTRIAL, wood + "_lamp_post");
	}

	/** Os blocos a pôr em cada poste, de baixo para cima (com o Industrial só o de baixo: o resto aparece sozinho). */
	public abstract List<BlockState> pole();

	/** Blocos livres precisos por cima do chão para o poste inteiro. */
	public int height() {
		return pole().size();
	}

	/** Este bloco é a base de um poste deste kit? */
	public boolean isPole(BlockState s) {
		return s.is(pole().get(0).getBlock());
	}

	/** A fonte da Prefeitura (painéis, bateria); vazio com as lanternas. */
	public List<BlockState> sources() {
		return List.of();
	}

	/** Estica um fio entre dois pontos (nada com as lanternas). */
	public boolean link(ServerLevel level, BlockPos a, BlockPos b) {
		return false;
	}

	/**
	 * O fio entre estes dois pontos passaria só pelo ar, com folga? Amostra a MESMA curva que o Industrial desenha (catenária entre os
	 * pontos de fixação reais, {@code GridAnchors.attach} + {@code GridMath.points}) a cada 1/6 de bloco: cada ponto tem de ficar a
	 * ≥ {@link #CLEARANCE} de qualquer bloco com corpo (parede, vidro, beiral), tirando o próprio poste ou a própria fonte das pontas e
	 * os primeiros/últimos {@link #END_SKIP} blocos junto às fixações. Sem fio aéreo: sempre livre (não há fio).
	 */
	public boolean wireClear(ServerLevel level, BlockPos a, BlockPos b) {
		return wireBlocker(level, a, b) == null;
	}

	/** O que tapa o fio entre estes pontos (null = livre). Para os registos e os testes. */
	public String wireBlocker(ServerLevel level, BlockPos a, BlockPos b) {
		return null;
	}

	/** É a bateria da fonte (a única parte que leva fio; os painéis encostados carregam-na)? */
	public boolean isBattery(BlockState s) {
		List<BlockState> src = sources();
		return !src.isEmpty() && s.is(src.get(src.size() - 1).getBlock());
	}

	/** Os pontos ligados por fio ao ponto em {@code pos} (vazio sem o Industrial). */
	public long[] partners(ServerLevel level, BlockPos pos) {
		return new long[0];
	}

	public boolean industrial() {
		return false;
	}

	/** Lanternas vanilla num poste de cerca (sem o Industrial). */
	public static final StreetLightKit LANTERNS = new StreetLightKit() {
		@Override
		public List<BlockState> pole() {
			return List.of(Blocks.OAK_FENCE.defaultBlockState(), Blocks.OAK_FENCE.defaultBlockState(), Blocks.LANTERN.defaultBlockState());
		}
	};

	/** O kit para esta vila: com os postes do Industrial se existirem, senão lanternas. */
	public static StreetLightKit forBiome(String biome) {
		if (!Mods.loaded(Mods.INDUSTRIAL)) return LANTERNS;
		String wood = com.tcordeiro.vilas.logic.LightingRules.woodFor(biome);
		Block pole = BuiltInRegistries.BLOCK.getOptional(wood == null ? IRON_POLE : woodPole(wood))
				.or(() -> BuiltInRegistries.BLOCK.getOptional(woodPole("oak"))).orElse(null);
		if (pole == null) return LANTERNS; // Industrial sem postes (≤ 0.10): lanternas
		// 0.3.0: com a API de colunas (Industrial 0.12.2+) os postes têm POLE_HEIGHT (4); sem ela, a altura antiga do modelo
		int height = columnApi(pole) != null ? POLE_HEIGHT : pole == BuiltInRegistries.BLOCK.getValue(IRON_POLE) ? 4 : 3;
		return new Industrial(pole, height);
	}

	/** Kit de uma vila no mundo. */
	public static StreetLightKit forVillage(ServerLevel level, com.tcordeiro.vilas.village.Village v) {
		return forBiome(level.getBiome(v.center).unwrapKey().map(k -> k.identifier().getPath()).orElse("plains"));
	}

	/** Postes do Industrial, com fio aéreo e a fonte na Prefeitura. */
	static final class Industrial extends StreetLightKit {
		final Block pole;
		final int height;
		final Method link;
		/** A geometria do fio do Industrial (0.11+): anchorAt, attach, points; e os vizinhos de um ponto (WireData). Null se faltar. */
		final Method anchorAt, attach, points, wireData, partners;

		Industrial(Block pole, int height) {
			this.pole = pole;
			this.height = height;
			Method m = null, an = null, at = null, pt = null, wd = null, pa = null;
			try {
				m = Class.forName(WIRES_CLASS).getMethod("link", ServerLevel.class, BlockPos.class, BlockPos.class);
				Class<?> anchors = Class.forName("com.tcordeiro.industrial.grid.GridAnchors");
				an = anchors.getMethod("anchorAt", net.minecraft.world.level.BlockGetter.class, BlockPos.class);
				at = anchors.getMethod("attach", BlockState.class, long.class, long.class, double[].class);
				pt = Class.forName("com.tcordeiro.industrial.grid.GridMath").getMethod("points", double.class, double.class, double.class, int.class, float[].class);
				Class<?> data = Class.forName("com.tcordeiro.industrial.grid.WireData");
				wd = data.getMethod("get", ServerLevel.class);
				pa = data.getMethod("partners", long.class);
			} catch (ReflectiveOperationException | LinkageError e) {
				TVilas.LOGGER.info("Vilas: o Industrial não tem fio aéreo ({}): postes soltos", e.toString());
				m = null;
			}
			this.link = m;
			this.anchorAt = an;
			this.attach = at;
			this.points = pt;
			this.wireData = wd;
			this.partners = pa;
		}

		@Override
		public String wireBlocker(ServerLevel level, BlockPos a, BlockPos b) {
			if (link == null) return "sem fio aéreo";
			try {
				BlockPos pa = (BlockPos) anchorAt.invoke(null, level, a), pb = (BlockPos) anchorAt.invoke(null, level, b);
				if (pa == null || pb == null || pa.equals(pb)) return "sem ponto de ligação";
				double[] A = new double[3], B = new double[3];
				attach.invoke(null, level.getBlockState(pa), pa.asLong(), pb.asLong(), A);
				attach.invoke(null, level.getBlockState(pb), pb.asLong(), pa.asLong(), B);
				double dx = B[0] - A[0], dy = B[1] - A[1], dz = B[2] - A[2];
				int n = Math.max(8, (int) Math.ceil(Math.sqrt(dx * dx + dy * dy + dz * dz) * 6));
				float[] pts = new float[(n + 1) * 3];
				points.invoke(null, dx, dy, dz, n, pts);
				// comprimento ao longo da curva (para saltar ~0,5 bloco junto a cada fixação)
				double[] along = new double[n + 1];
				for (int i = 1; i <= n; i++) {
					double ex = pts[i * 3] - pts[i * 3 - 3], ey = pts[i * 3 + 1] - pts[i * 3 - 2], ez = pts[i * 3 + 2] - pts[i * 3 - 1];
					along[i] = along[i - 1] + Math.sqrt(ex * ex + ey * ey + ez * ez);
				}
				double total = along[n];
				BlockPos.MutableBlockPos q = new BlockPos.MutableBlockPos();
				for (int i = 0; i <= n; i++) {
					if (along[i] < END_SKIP || total - along[i] < END_SKIP) continue;
					double px = A[0] + pts[i * 3], py = A[1] + pts[i * 3 + 1], pz = A[2] + pts[i * 3 + 2];
					int bx = (int) Math.floor(px), by = (int) Math.floor(py), bz = (int) Math.floor(pz);
					for (int ox = -1; ox <= 1; ox++)
						for (int oy = -1; oy <= 1; oy++)
							for (int oz = -1; oz <= 1; oz++) {
								q.set(bx + ox, by + oy, bz + oz);
								BlockState s = level.getBlockState(q);
								if (s.isAir()) continue;
								// o próprio poste ou a própria fonte das pontas (o fio sai deles)
								if (q.equals(pa) || q.equals(pb)) continue;
								Object own = anchorAt.invoke(null, level, q.immutable());
								if (pa.equals(own) || pb.equals(own)) continue;
								// os painéis encostados à bateria da ponta fazem parte da mesma fonte
								if ((q.distSqr(pa) <= 2 || q.distSqr(pb) <= 2) && sources().stream().anyMatch(x -> s.is(x.getBlock()))) continue;
								var shape = s.getCollisionShape(level, q);
								if (shape.isEmpty()) continue; // erva, flores: sem corpo
								for (var box : shape.toAabbs()) {
									double cx = Math.max(box.minX + q.getX(), Math.min(px, box.maxX + q.getX()));
									double cy = Math.max(box.minY + q.getY(), Math.min(py, box.maxY + q.getY()));
									double cz = Math.max(box.minZ + q.getZ(), Math.min(pz, box.maxZ + q.getZ()));
									double dx2 = px - cx, dy2 = py - cy, dz2 = pz - cz;
									if (dx2 * dx2 + dy2 * dy2 + dz2 * dz2 < CLEARANCE * CLEARANCE)
										return s.getBlock() + " em " + q.toShortString() + " a " + String.format(java.util.Locale.ROOT, "%.2f", Math.sqrt(dx2 * dx2 + dy2 * dy2 + dz2 * dz2));
								}
							}
				}
				return null;
			} catch (ReflectiveOperationException | RuntimeException e) {
				TVilas.LOGGER.warn("Vilas: não deu para ver o caminho do fio entre {} e {}: {}", a, b, e.toString());
				return e.toString();
			}
		}

		@Override
		public long[] partners(ServerLevel level, BlockPos pos) {
			if (link == null) return new long[0];
			try {
				BlockPos p = (BlockPos) anchorAt.invoke(null, level, pos);
				return p == null ? new long[0] : (long[]) partners.invoke(wireData.invoke(null, level), p.asLong());
			} catch (ReflectiveOperationException | RuntimeException e) {
				return new long[0];
			}
		}

		@Override
		public List<BlockState> pole() {
			return List.of(pole.defaultBlockState());
		}

		@Override
		public int height() {
			return height;
		}

		@Override
		public boolean industrial() {
			return true;
		}

		@Override
		public List<BlockState> sources() {
			Block solar = BuiltInRegistries.BLOCK.getOptional(SOLAR).orElse(null), battery = BuiltInRegistries.BLOCK.getOptional(BATTERY).orElse(null);
			if (solar == null || battery == null || link == null) return List.of();
			return List.of(solar.defaultBlockState(), solar.defaultBlockState(), battery.defaultBlockState());
		}

		@Override
		public boolean link(ServerLevel level, BlockPos a, BlockPos b) {
			if (link == null) return false;
			try {
				return (Boolean) link.invoke(null, level, a, b);
			} catch (ReflectiveOperationException | RuntimeException e) {
				TVilas.LOGGER.warn("Vilas: fio aéreo falhou entre {} e {}: {}", a, b, e.toString());
				return false;
			}
		}
	}
}
