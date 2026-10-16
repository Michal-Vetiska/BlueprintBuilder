package com.blueprintbuilder.logic;

/**
 * One unit of construction. Doors and beds are a single step so both halves are placed together
 * and only one item is consumed.
 */
public record BuildStep(
        int index,
        StructureCell primary,
        StructureCell secondary,
        ConstructionStage stage,
        String itemId,
        boolean solidSupport,
        boolean needsSupportFromAbove
) {
}
