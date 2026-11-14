package com.tcordeiro.vilas.village;

import java.util.ArrayList;
import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Uma obra da vila, feita pelos construtores bloco a bloco ({@link com.tcordeiro.vilas.build.Builders}): o golem montado com
 * blocos de ferro e uma abóbora, a muralha da Cidade, os postes da iluminação pública. Guardada no mundo com a vila.
 * <p>
 * {@code pending} são os blocos que ainda faltam (por ordem). Na muralha os blocos são planeados coluna a coluna à medida
 * ({@code nextColumn}), porque as colunas longe podem estar em chunks por carregar.
 */
public final class Project {
	public enum Kind {
		GOLEM, WALL, LIGHTING,
		/** 0.3.0: os edifícios cívicos que faltam a uma vila antiga ({@link com.tcordeiro.vilas.build.CivicWork}). */
		TOWN_HALL, MARKET, TAVERN, PLAZA,
		/** 0.3.0: uma casa simples (sem cama livre, para o morador que o prestígio traz). */
		HOUSE,
		/** 0.6 crises: repor os blocos partidos por monstros, explosões ou fogo ({@link com.tcordeiro.vilas.life.crisis.Rebuild}). */
		REBUILD;

		/** 0.3.0 (M14): nomes desconhecidos dão erro ao ler (a obra é ignorada) em vez de rebentar o carregamento do mundo. */
		public static final Codec<Kind> CODEC = Codec.STRING.comapFlatMap(s -> {
			for (Kind k : values()) if (k.name().equals(s)) return com.mojang.serialization.DataResult.success(k);
			return com.mojang.serialization.DataResult.error(() -> "obra desconhecida: " + s);
		}, Kind::name);

		public boolean civic() {
			return this == TOWN_HALL || this == MARKET || this == TAVERN || this == PLAZA;
		}

		/** Uma estrutura NBT posta pelos construtores (edifícios cívicos e a casa). */
		public boolean structure() {
			return civic() || this == HOUSE;
		}

		public String key() {
			return "tvilas.project." + name().toLowerCase(java.util.Locale.ROOT);
		}
	}

	/** Um bloco a pôr; {@code clear} = quantos blocos (a contar deste, para cima) têm de estar livres (postes altos do Industrial). */
	public record Placement(BlockPos pos, BlockState state, int clear) {
		/** M14: quantos blocos livres por cima se podem pedir (um valor estragado não faz o construtor varrer o mundo). */
		public static final int MAX_CLEAR = 32;

		public static final Codec<Placement> CODEC = RecordCodecBuilder.create(i -> i.group(
				BlockPos.CODEC.fieldOf("pos").forGetter(Placement::pos), BlockState.CODEC.fieldOf("state").forGetter(Placement::state),
				Codec.intRange(1, MAX_CLEAR).optionalFieldOf("clear", 1).forGetter(Placement::clear))
				.apply(i, Placement::new));

		public Placement(BlockPos pos, BlockState state) {
			this(pos, state, 1);
		}
	}

	/** M14: contadores guardados entre 0 e isto. */
	public static final int MAX_COUNT = 1 << 20;

	public static final Codec<Project> CODEC = RecordCodecBuilder.create(i -> i.group(
			Kind.CODEC.fieldOf("kind").forGetter(p -> p.kind),
			BlockPos.CODEC.fieldOf("site").forGetter(p -> p.site),
			SafeCodecs.list(Placement.CODEC, "bloco de obra").optionalFieldOf("pending", List.of()).forGetter(p -> p.pending),
			Codec.intRange(0, MAX_COUNT).optionalFieldOf("next_column", 0).forGetter(p -> p.nextColumn),
			Codec.intRange(0, MAX_COUNT).optionalFieldOf("placed", 0).forGetter(p -> p.placed),
			Codec.intRange(0, MAX_COUNT).optionalFieldOf("skipped", 0).forGetter(p -> p.skipped),
			Codec.intRange(0, MAX_COUNT).optionalFieldOf("total", 0).forGetter(p -> p.total),
			BlockPos.CODEC.listOf().optionalFieldOf("anchors", List.of()).forGetter(p -> p.anchors))
			.apply(i, (k, s, pe, nc, pl, sk, t, an) -> {
				Project p = new Project(k, s);
				p.pending.addAll(pe);
				p.nextColumn = nc;
				p.placed = pl;
				p.skipped = sk;
				p.total = t;
				p.anchors.addAll(an);
				return p;
			}));

	public final Kind kind;
	/** Onde fica a obra (para saber se alguém está a ver e para onde vai o construtor primeiro). */
	public final BlockPos site;
	public final List<Placement> pending = new ArrayList<>();
	public int nextColumn;
	public int placed, skipped;
	/** Blocos (ou colunas, na muralha) previstos — para mostrar o progresso. */
	public int total;
	/** Fios aéreos esticados ao acabar (não guardado; para os testes). */
	public int linked;
	/** Pontos de referência da obra (a base de cada poste da iluminação). */
	public final List<BlockPos> anchors = new ArrayList<>();

	public Project(Kind kind, BlockPos site) {
		this.kind = kind;
		this.site = site.immutable();
	}
}
