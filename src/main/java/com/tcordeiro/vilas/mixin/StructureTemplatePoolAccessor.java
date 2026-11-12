package com.tcordeiro.vilas.mixin;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

import com.mojang.datafixers.util.Pair;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;

/** Acesso às listas internas das template pools, para acrescentar as peças da Prefeitura e do Mercado às vilas vanilla. */
@Mixin(StructureTemplatePool.class)
public interface StructureTemplatePoolAccessor {
	@Accessor("templates")
	ObjectArrayList<StructurePoolElement> tvilas$templates();

	@Accessor("rawTemplates")
	List<Pair<StructurePoolElement, Integer>> tvilas$rawTemplates();

	@Mutable
	@Accessor("rawTemplates")
	void tvilas$setRawTemplates(List<Pair<StructurePoolElement, Integer>> list);
}
