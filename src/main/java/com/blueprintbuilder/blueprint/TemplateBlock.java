package com.blueprintbuilder.blueprint;

import org.jetbrains.annotations.Nullable;

import com.blueprintbuilder.logic.StructureCell;

import net.minecraft.nbt.CompoundTag;

public record TemplateBlock(StructureCell cell, @Nullable CompoundTag blockEntityTag) {
}
