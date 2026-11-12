package com.tcordeiro.vilas.mixin;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/** 0.3.0 — Os blocos de uma estrutura NBT, para os construtores a fazerem bloco a bloco ({@code build/CivicWork}). */
@Mixin(StructureTemplate.class)
public interface StructureTemplateAccessor {
	@Accessor("palettes")
	List<StructureTemplate.Palette> tvilas$palettes();
}
