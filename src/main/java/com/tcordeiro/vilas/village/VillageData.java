package com.tcordeiro.vilas.village;

import java.util.ArrayList;
import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.tcordeiro.vilas.TVilas;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/** Vilas e terrenos de UM mundo/dimensão (SavedData do nível: data/tvilas_villages.dat). */
public class VillageData extends SavedData {
	public static final Codec<VillageData> CODEC = RecordCodecBuilder.create(i -> i.group(
			Village.CODEC.listOf().optionalFieldOf("villages", List.of()).forGetter(d -> d.villages),
			Lot.CODEC.listOf().optionalFieldOf("lots", List.of()).forGetter(d -> d.lots)).apply(i, VillageData::new));
	public static final SavedDataType<VillageData> TYPE = new SavedDataType<>(TVilas.id("villages"), VillageData::new, CODEC, null);

	public final List<Village> villages = new ArrayList<>();
	/** Os terrenos. Quem acrescenta/tira terrenos mexe nesta lista; o índice por chunk ({@link #lotAt}) refaz-se sozinho. */
	public final LotList lots = new LotList();

	/** Lista de terrenos que expõe o contador de alterações estruturais do ArrayList (add/remove/removeIf/clear). */
	public static final class LotList extends ArrayList<Lot> {
		int mods() {
			return modCount;
		}
	}

	/** chunk (ChunkPos.pack) → terrenos que tocam esse chunk (um terreno 16×16 toca até 4 chunks). */
	private final Long2ObjectOpenHashMap<Lot[]> lotIndex = new Long2ObjectOpenHashMap<>();
	private int indexedMods = -1;

	public VillageData() {}

	private VillageData(List<Village> v, List<Lot> l) {
		villages.addAll(v);
		lots.addAll(l);
	}

	public static VillageData get(ServerLevel level) {
		return level.getDataStorage().computeIfAbsent(TYPE);
	}

	/**
	 * O terreno que contém (x, z), ou null. Chamado MUITAS vezes (fluidos, fogo, explosões, pistões): uma procura num mapa
	 * long → array, sem alocações; o índice só se refaz quando a lista de terrenos mudou.
	 */
	public Lot lotAt(int x, int z) {
		if (lots.isEmpty()) return null;
		if (indexedMods != lots.mods()) reindex();
		Lot[] here = lotIndex.get(ChunkPos.pack(x >> 4, z >> 4));
		if (here == null) return null;
		for (Lot l : here) if (l.contains(x, z)) return l;
		return null;
	}

	private void reindex() {
		lotIndex.clear();
		for (Lot l : lots)
			for (int cx = l.area.minX() >> 4; cx <= l.area.maxX() >> 4; cx++)
				for (int cz = l.area.minZ() >> 4; cz <= l.area.maxZ() >> 4; cz++) {
					long key = ChunkPos.pack(cx, cz);
					Lot[] old = lotIndex.get(key);
					Lot[] arr = old == null ? new Lot[1] : java.util.Arrays.copyOf(old, old.length + 1);
					arr[arr.length - 1] = l;
					lotIndex.put(key, arr);
				}
		indexedMods = lots.mods();
	}

	/**
	 * 0.3.0 (M15): a vila cuja área cívica (Mesa, Prefeitura, Praça, Mercado, Taverna) contém (x, z), ou null. Sai logo se
	 * nenhuma vila tiver área cívica (os fluidos e o fogo chamam isto muitas vezes).
	 */
	public Village civicAt(int x, int z) {
		for (Village v : villages) if (!v.civic.boxes.isEmpty() && v.civic.covers(x, z)) return v;
		return null;
	}

	/** Marca para gravar (todas as alterações passam pelo VillageManager, que chama isto). */
	public void changed() {
		setDirty();
	}
}
