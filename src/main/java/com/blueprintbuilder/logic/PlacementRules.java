package com.blueprintbuilder.logic;

import java.util.BitSet;
import java.util.Map;

/**
 * Support rules. A block is not placed until the structure underneath it exists,
 * unless it is a foundation. Overhangs (roof eaves, ceiling spans) wait for a finished neighbour.
 * Hanging blocks wait for the block they hang from.
 */
public final class PlacementRules {
    private PlacementRules() {
    }

    public static boolean canPlace(BuildStep step, Map<String, BuildStep> byPos, BitSet placed, int minY) {
        if (placed.get(step.index())) {
            return false;
        }
        StructureCell cell = step.primary();
        if (step.needsSupportFromAbove()) {
            BuildStep above = byPos.get(ConstructionQueue.key(cell.x(), cell.y() + 1, cell.z()));
            if (above == null || !placed.get(above.index())) {
                return false;
            }
        }

        BuildStep below = byPos.get(ConstructionQueue.key(cell.x(), cell.y() - 1, cell.z()));
        if (below != null && !placed.get(below.index()) && (below.solidSupport() || !step.solidSupport())) {
            // A solid block may bridge over an unbuilt decoration and take support from a neighbour.
            // Everything else waits for the block underneath.
            return false;
        }
        boolean supportedFromBelow = below != null && placed.get(below.index()) && below.solidSupport();
        if (step.needsSupportFromAbove()) {
            BuildStep above = byPos.get(ConstructionQueue.key(cell.x(), cell.y() + 1, cell.z()));
            if (above != null && placed.get(above.index()) && above.solidSupport()) {
                return true;
            }
        }
        if (!supportedFromBelow && cell.y() > minY && !hasPlacedSolidNeighbour(cell, byPos, placed)) {
            return false;
        }
        return true;
    }

    private static boolean hasPlacedSolidNeighbour(StructureCell cell, Map<String, BuildStep> byPos, BitSet placed) {
        int[][] offsets = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] offset : offsets) {
            BuildStep neighbour = byPos.get(ConstructionQueue.key(cell.x() + offset[0], cell.y(), cell.z() + offset[1]));
            if (neighbour != null && neighbour.solidSupport() && placed.get(neighbour.index())) {
                return true;
            }
        }
        return false;
    }
}
