package com.blueprintbuilder.logic;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns a structure into the items a player must supply.
 * Air and structure voids are free. A door or a bed costs one item, not one per half.
 */
public final class MaterialCalculator {
    private MaterialCalculator() {
    }

    public static Map<String, Integer> calculate(List<StructureCell> cells) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (StructureCell cell : cells) {
            if (isIgnored(cell.blockId()) || isSecondaryHalf(cell)) {
                continue;
            }
            counts.merge(itemId(cell), 1, Integer::sum);
        }
        return Map.copyOf(counts);
    }

    public static boolean isIgnored(String blockId) {
        return blockId.equals("minecraft:air")
                || blockId.equals("minecraft:cave_air")
                || blockId.equals("minecraft:void_air")
                || blockId.equals("minecraft:structure_void")
                || blockId.equals("minecraft:structure_block")
                || blockId.equals("minecraft:jigsaw");
    }

    /**
     * The upper half of a door or tall plant, and the head of a bed, are placed with their partner.
     */
    public static boolean isSecondaryHalf(StructureCell cell) {
        if ("upper".equals(cell.property("half"))) {
            return true;
        }
        return "head".equals(cell.property("part"));
    }

    public static String itemId(StructureCell cell) {
        return cell.blockId();
    }
}
