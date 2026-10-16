package com.blueprintbuilder.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class MaterialCalculatorTest {
    @Test
    void doorAndBedCostOneItem() {
        List<StructureCell> cells = List.of(
                new StructureCell(0, 0, 0, "minecraft:cobblestone"),
                new StructureCell(1, 0, 0, "minecraft:oak_door", Map.of("half", "lower")),
                new StructureCell(1, 1, 0, "minecraft:oak_door", Map.of("half", "upper")),
                new StructureCell(2, 0, 0, "minecraft:white_bed", Map.of("part", "foot", "facing", "south")),
                new StructureCell(2, 0, 1, "minecraft:white_bed", Map.of("part", "head", "facing", "south")),
                new StructureCell(3, 0, 0, "minecraft:air"),
                new StructureCell(4, 0, 0, "minecraft:glass_pane")
        );

        Map<String, Integer> materials = MaterialCalculator.calculate(cells);

        assertEquals(1, materials.get("minecraft:cobblestone"));
        assertEquals(1, materials.get("minecraft:oak_door"));
        assertEquals(1, materials.get("minecraft:white_bed"));
        assertEquals(1, materials.get("minecraft:glass_pane"));
        assertFalse(materials.containsKey("minecraft:air"));
        assertEquals(4, materials.values().stream().mapToInt(Integer::intValue).sum());
    }

    @Test
    void identicalBlocksAccumulate() {
        List<StructureCell> cells = List.of(
                new StructureCell(0, 0, 0, "minecraft:oak_planks"),
                new StructureCell(1, 0, 0, "minecraft:oak_planks"),
                new StructureCell(2, 0, 0, "minecraft:oak_planks")
        );

        assertEquals(3, MaterialCalculator.calculate(cells).get("minecraft:oak_planks"));
    }
}
