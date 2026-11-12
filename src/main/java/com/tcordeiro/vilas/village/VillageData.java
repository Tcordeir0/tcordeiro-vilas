package com.tcordeiro.vilas.village;

import java.util.ArrayList;
import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.tcordeiro.vilas.TVilas;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/** Vilas e terrenos de UM mundo/dimensão (SavedData do nível: data/tvilas_villages.dat). */
public class VillageData extends SavedData {
	public static final Codec<VillageData> CODEC = RecordCodecBuilder.create(i -> i.group(
			Village.CODEC.listOf().optionalFieldOf("villages", List.of()).forGetter(d -> d.villages),
			Lot.CODEC.listOf().optionalFieldOf("lots", List.of()).forGetter(d -> d.lots)).apply(i, VillageData::new));
	public static final SavedDataType<VillageData> TYPE = new SavedDataType<>(TVilas.id("villages"), VillageData::new, CODEC, null);

	public final List<Village> villages = new ArrayList<>();
	public final List<Lot> lots = new ArrayList<>();

	public VillageData() {}

	private VillageData(List<Village> v, List<Lot> l) {
		villages.addAll(v);
		lots.addAll(l);
	}

	public static VillageData get(ServerLevel level) {
		return level.getDataStorage().computeIfAbsent(TYPE);
	}

	/** Marca para gravar (todas as alterações passam pelo VillageManager, que chama isto). */
	public void changed() {
		setDirty();
	}
}
