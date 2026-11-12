package com.tcordeiro.vilas.village;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.tcordeiro.vilas.logic.LotRules;

import net.minecraft.core.UUIDUtil;

/** Terreno comprado: área 16×16 (toda a altura), dono e amigos. Guardado no mundo ({@link VillageData}). */
public final class Lot {
	public static final Codec<Lot> CODEC = RecordCodecBuilder.create(i -> i.group(
			UUIDUtil.STRING_CODEC.fieldOf("id").forGetter(l -> l.id),
			UUIDUtil.STRING_CODEC.fieldOf("village").forGetter(l -> l.village),
			Codec.INT.fieldOf("min_x").forGetter(l -> l.area.minX()),
			Codec.INT.fieldOf("min_z").forGetter(l -> l.area.minZ()),
			Codec.INT.fieldOf("max_x").forGetter(l -> l.area.maxX()),
			Codec.INT.fieldOf("max_z").forGetter(l -> l.area.maxZ()),
			UUIDUtil.STRING_CODEC.fieldOf("owner").forGetter(l -> l.owner),
			Codec.STRING.optionalFieldOf("owner_name", "").forGetter(l -> l.ownerName),
			UUIDUtil.STRING_CODEC.listOf().optionalFieldOf("friends", List.of()).forGetter(l -> List.copyOf(l.friends)))
			.apply(i, (id, v, x0, z0, x1, z1, o, n, f) -> new Lot(id, v, new LotRules.Area(x0, z0, x1, z1), o, n, new HashSet<>(f))));

	public final UUID id;
	public final UUID village;
	public final LotRules.Area area;
	public final UUID owner;
	public final String ownerName;
	public final Set<UUID> friends;

	public Lot(UUID id, UUID village, LotRules.Area area, UUID owner, String ownerName, Set<UUID> friends) {
		this.id = id;
		this.village = village;
		this.area = area;
		this.owner = owner;
		this.ownerName = ownerName;
		this.friends = friends;
	}

	public boolean contains(int x, int z) {
		return area.contains(x, z);
	}

	/** REGRA DE SEGURANÇA (delegada em {@link LotRules#mayModify}). */
	public boolean mayModify(UUID actor) {
		return LotRules.mayModify(owner, friends, actor);
	}
}
