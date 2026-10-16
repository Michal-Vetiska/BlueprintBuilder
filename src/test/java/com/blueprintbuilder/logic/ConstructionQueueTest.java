package com.blueprintbuilder.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.BitSet;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;

import org.junit.jupiter.api.Test;

class ConstructionQueueTest {
    @Test
    void stagesAreOrderedFoundationBeforeRoofAndOpenings() {
        List<BuildStep> steps = ConstructionQueue.build(List.of(
                new StructureCell(0, 0, 0, "minecraft:cobblestone"),
                new StructureCell(2, 0, 2, "minecraft:cobblestone"),
                new StructureCell(1, 1, 1, "minecraft:oak_planks"),
                new StructureCell(0, 1, 0, "minecraft:oak_log", Map.of("axis", "y")),
                new StructureCell(0, 1, 1, "minecraft:oak_planks"),
                new StructureCell(0, 3, 0, "minecraft:spruce_stairs", Map.of("facing", "east")),
                new StructureCell(0, 2, 1, "minecraft:glass_pane"),
                new StructureCell(0, 1, 2, "minecraft:oak_door", Map.of("half", "lower", "facing", "north")),
                new StructureCell(0, 2, 2, "minecraft:oak_door", Map.of("half", "upper", "facing", "north"))
        ));

        assertStageBefore(steps, ConstructionStage.FOUNDATION, ConstructionStage.FLOOR);
        assertStageBefore(steps, ConstructionStage.SUPPORTS, ConstructionStage.WALLS);
        assertStageBefore(steps, ConstructionStage.WALLS, ConstructionStage.ROOF);
        assertStageBefore(steps, ConstructionStage.ROOF, ConstructionStage.OPENINGS);
        long doorSteps = steps.stream().filter(step -> step.itemId().equals("minecraft:oak_door")).count();
        assertEquals(1, doorSteps);
        assertTrue(steps.stream().filter(step -> step.itemId().equals("minecraft:oak_door")).findFirst().orElseThrow().secondary() != null);
    }

    @Test
    void missingMaterialDoesNotBlockOtherReadySections() {
        List<BuildStep> steps = ConstructionQueue.build(List.of(
                new StructureCell(0, 0, 0, "minecraft:cobblestone"),
                new StructureCell(1, 0, 0, "minecraft:oak_planks"),
                new StructureCell(2, 0, 0, "minecraft:glass")
        ));
        BitSet placed = new BitSet();

        OptionalInt next = ConstructionQueue.nextBuildable(steps, placed, Set.of("minecraft:cobblestone", "minecraft:glass"));

        assertTrue(next.isPresent());
        assertEquals("minecraft:cobblestone", steps.get(next.getAsInt()).itemId());
    }

    @Test
    void overhangWaitsForAPlacedNeighbour() {
        List<BuildStep> steps = ConstructionQueue.build(List.of(
                new StructureCell(0, 0, 0, "minecraft:cobblestone"),
                new StructureCell(0, 1, 0, "minecraft:oak_planks"),
                new StructureCell(1, 1, 0, "minecraft:oak_planks")
        ));
        BitSet placed = new BitSet();
        Set<String> items = Set.of("minecraft:cobblestone", "minecraft:oak_planks");

        int foundation = ConstructionQueue.nextBuildable(steps, placed, items).orElseThrow();
        placed.set(foundation);
        int supported = ConstructionQueue.nextBuildable(steps, placed, items).orElseThrow();
        assertEquals(0, steps.get(supported).primary().x());
        placed.set(supported);

        int overhang = ConstructionQueue.nextBuildable(steps, placed, items).orElseThrow();
        assertEquals(1, steps.get(overhang).primary().x());
    }

    @Test
    void hangingBlockWaitsForTheBlockAbove() {
        List<BuildStep> steps = ConstructionQueue.build(List.of(
                new StructureCell(0, 0, 0, "minecraft:cobblestone"),
                new StructureCell(0, 1, 0, "minecraft:oak_planks"),
                new StructureCell(0, 2, 0, "minecraft:oak_planks"),
                new StructureCell(0, 3, 0, "minecraft:oak_planks"),
                new StructureCell(1, 3, 0, "minecraft:oak_planks"),
                new StructureCell(1, 2, 0, "minecraft:lantern", Map.of("hanging", "true"))
        ));
        BitSet placed = new BitSet();
        Set<String> items = Set.of("minecraft:cobblestone", "minecraft:oak_planks", "minecraft:lantern");
        BuildStep lantern = steps.stream().filter(step -> step.itemId().equals("minecraft:lantern")).findFirst().orElseThrow();
        assertEquals(ConstructionStage.DECOR, lantern.stage());

        for (int guard = 0; guard < steps.size(); guard++) {
            int next = ConstructionQueue.nextBuildable(steps, placed, items).orElseThrow();
            if (next == lantern.index()) {
                BuildStep above = steps.stream()
                        .filter(step -> step.primary().x() == 1 && step.primary().y() == 3 && step.primary().z() == 0)
                        .findFirst()
                        .orElseThrow();
                assertTrue(placed.get(above.index()));
                return;
            }
            placed.set(next);
        }
        assertTrue(false, "lantern never became buildable");
    }

    private static void assertStageBefore(List<BuildStep> steps, ConstructionStage earlier, ConstructionStage later) {
        int earlierIndex = steps.stream().filter(step -> step.stage() == earlier).mapToInt(BuildStep::index).min().orElseThrow();
        int laterIndex = steps.stream().filter(step -> step.stage() == later).mapToInt(BuildStep::index).min().orElseThrow();
        assertTrue(earlierIndex < laterIndex, earlier + " should precede " + later);
    }
}
