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
			UUIDUtil.STRING_CODEC.listOf().optionalFieldOf("friends", List.of()).forGetter(l -> List.copyOf(l.friends)),
			Codec.unboundedMap(UUIDUtil.STRING_CODEC, Codec.string(0, 32)).optionalFieldOf("friend_names", java.util.Map.of()).forGetter(l -> l.friendNames))
			.apply(i, (id, v, x0, z0, x1, z1, o, n, f, fn) -> {
				Lot lot = new Lot(id, v, new LotRules.Area(x0, z0, x1, z1), o, n, new HashSet<>(f));
				lot.friendNames.putAll(fn);
				return lot;
			}));

	public final UUID id;
	public final UUID village;
	public final LotRules.Area area;
	public final UUID owner;
	public final String ownerName;
	public final Set<UUID> friends;
	/** 0.3.0 (M18): o nome de cada amigo quando foi adicionado (para o tirar mesmo com ele desligado). */
	public final java.util.Map<UUID, String> friendNames = new java.util.HashMap<>();

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

	/** O amigo com este nome (sem distinguir maiúsculas) ou com este UUID escrito, ou null. */
	public UUID friendByName(String name) {
		for (var e : friendNames.entrySet()) if (e.getValue().equalsIgnoreCase(name) && friends.contains(e.getKey())) return e.getKey();
		try {
			UUID id = UUID.fromString(name);
			return friends.contains(id) ? id : null;
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	/** REGRA DE SEGURANÇA (delegada em {@link LotRules#mayModify}). */
	public boolean mayModify(UUID actor) {
		return LotRules.mayModify(owner, friends, actor);
	}
}
