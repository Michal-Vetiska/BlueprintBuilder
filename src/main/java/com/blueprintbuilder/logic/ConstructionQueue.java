package com.blueprintbuilder.logic;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;

/**
 * Deterministic construction order and the "what can be built right now" query.
 */
public final class ConstructionQueue {
    private ConstructionQueue() {
    }

    public static List<BuildStep> build(List<StructureCell> cells) {
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        Map<String, StructureCell> byPos = new HashMap<>();
        for (StructureCell cell : cells) {
            if (MaterialCalculator.isIgnored(cell.blockId())) {
                continue;
            }
            byPos.put(key(cell.x(), cell.y(), cell.z()), cell);
            minX = Math.min(minX, cell.x());
            minY = Math.min(minY, cell.y());
            minZ = Math.min(minZ, cell.z());
            maxX = Math.max(maxX, cell.x());
            maxY = Math.max(maxY, cell.y());
            maxZ = Math.max(maxZ, cell.z());
        }
        if (byPos.isEmpty()) {
            return List.of();
        }

        int sizeX = maxX - minX + 1;
        int sizeZ = maxZ - minZ + 1;
        List<BuildStep> unsorted = new ArrayList<>();
        for (StructureCell cell : byPos.values()) {
            if (MaterialCalculator.isSecondaryHalf(cell)) {
                continue;
            }
            StructureCell secondary = findSecondary(cell, byPos);
            unsorted.add(new BuildStep(
                    -1,
                    cell,
                    secondary,
                    classify(cell, minX, minY, minZ, maxY, sizeX, sizeZ),
                    MaterialCalculator.itemId(cell),
                    isSolidSupport(cell.blockId()),
                    needsSupportFromAbove(cell)
            ));
        }
        unsorted.sort(Comparator
                .comparingInt((BuildStep step) -> step.stage().ordinal())
                .thenComparingInt(step -> step.primary().y())
                .thenComparingInt(step -> step.primary().z())
                .thenComparingInt(step -> step.primary().x())
                .thenComparing(step -> step.itemId()));

        List<BuildStep> indexed = new ArrayList<>(unsorted.size());
        for (int i = 0; i < unsorted.size(); i++) {
            BuildStep step = unsorted.get(i);
            indexed.add(new BuildStep(
                    i,
                    step.primary(),
                    step.secondary(),
                    step.stage(),
                    step.itemId(),
                    step.solidSupport(),
                    step.needsSupportFromAbove()
            ));
        }
        return List.copyOf(indexed);
    }

    public static Map<String, BuildStep> indexByPosition(List<BuildStep> steps) {
        Map<String, BuildStep> byPos = new HashMap<>();
        for (BuildStep step : steps) {
            StructureCell primary = step.primary();
            byPos.put(key(primary.x(), primary.y(), primary.z()), step);
            if (step.secondary() != null) {
                StructureCell secondary = step.secondary();
                byPos.put(key(secondary.x(), secondary.y(), secondary.z()), step);
            }
        }
        return byPos;
    }

    public static OptionalInt nextBuildable(List<BuildStep> steps, java.util.BitSet placed, Set<String> availableItems) {
        return nextBuildable(steps, placed, availableItems, new java.util.BitSet());
    }

    /**
     * First step that has its item, its support, and has not been placed yet.
     * Unavailable materials are skipped so other ready sections can proceed.
     * {@code skip} holds steps the world rejected for this pass, such as cells blocked by terrain.
     */
    public static OptionalInt nextBuildable(List<BuildStep> steps, java.util.BitSet placed, Set<String> availableItems, java.util.BitSet skip) {
        if (steps.isEmpty()) {
            return OptionalInt.empty();
        }
        int minY = steps.stream().mapToInt(step -> step.primary().y()).min().orElse(0);
        Map<String, BuildStep> byPos = indexByPosition(steps);
        for (BuildStep step : steps) {
            if (placed.get(step.index()) || skip.get(step.index())) {
                continue;
            }
            if (!availableItems.contains(step.itemId())) {
                continue;
            }
            if (PlacementRules.canPlace(step, byPos, placed, minY)) {
                return OptionalInt.of(step.index());
            }
        }
        return OptionalInt.empty();
    }

    public static String key(int x, int y, int z) {
        return x + "," + y + "," + z;
    }

    static ConstructionStage classify(StructureCell cell, int minX, int minY, int minZ, int maxY, int sizeX, int sizeZ) {
        String id = cell.blockId();
        if (isOpening(id)) {
            return ConstructionStage.OPENINGS;
        }
        if (isDecor(id)) {
            return ConstructionStage.DECOR;
        }
        if (cell.y() == minY && isSolidSupport(id)) {
            return ConstructionStage.FOUNDATION;
        }
        int height = Math.max(1, maxY - minY);
        int roofStart = minY + (int) Math.ceil(height * 0.6D);
        if (cell.y() >= roofStart && (id.endsWith("_stairs") || id.endsWith("_slab") || isSolidSupport(id))) {
            return ConstructionStage.ROOF;
        }
        if (id.endsWith("_log") || id.endsWith("_wood") || id.endsWith("_fence") || id.endsWith("_wall")) {
            return ConstructionStage.SUPPORTS;
        }
        int localX = cell.x() - minX;
        int localZ = cell.z() - minZ;
        boolean perimeter = localX == 0 || localZ == 0 || localX == sizeX - 1 || localZ == sizeZ - 1;
        if (!perimeter && cell.y() <= minY + 1 && isFloorBlock(id)) {
            return ConstructionStage.FLOOR;
        }
        if (perimeter && isSolidSupport(id)) {
            return ConstructionStage.WALLS;
        }
        if (isFloorBlock(id)) {
            return ConstructionStage.FLOOR;
        }
        return ConstructionStage.DECOR;
    }

    public static boolean isSolidSupport(String blockId) {
        if (isIgnored(blockId) || isOpening(blockId) || isDecor(blockId)) {
            return false;
        }
        return !blockId.endsWith("_carpet")
                && !blockId.endsWith("_fence")
                && !blockId.endsWith("_wall")
                && !blockId.endsWith("_pressure_plate")
                && !blockId.endsWith("_button");
    }

    private static boolean isIgnored(String blockId) {
        return MaterialCalculator.isIgnored(blockId);
    }

    public static boolean isOpening(String blockId) {
        return blockId.endsWith("_door")
                || blockId.endsWith("_trapdoor")
                || blockId.endsWith("_glass")
                || blockId.endsWith("_glass_pane")
                || blockId.equals("minecraft:glass")
                || blockId.contains("glass_pane");
    }

    public static boolean isDecor(String blockId) {
        return blockId.endsWith("_bed")
                || blockId.endsWith("_carpet")
                || blockId.endsWith("_banner")
                || blockId.endsWith("_sign")
                || blockId.endsWith("lantern")
                || blockId.endsWith("torch")
                || blockId.endsWith("candle")
                || blockId.equals("minecraft:crafting_table")
                || blockId.equals("minecraft:furnace")
                || blockId.equals("minecraft:blast_furnace")
                || blockId.equals("minecraft:smoker")
                || blockId.equals("minecraft:campfire")
                || blockId.equals("minecraft:soul_campfire")
                || blockId.equals("minecraft:flower_pot")
                || blockId.startsWith("minecraft:potted_")
                || blockId.equals("minecraft:chest")
                || blockId.equals("minecraft:barrel")
                || blockId.equals("minecraft:lectern")
                || blockId.equals("minecraft:bookshelf");
    }

    private static boolean isFloorBlock(String blockId) {
        return blockId.endsWith("_planks") || blockId.endsWith("_slab");
    }

    private static boolean needsSupportFromAbove(StructureCell cell) {
        return "true".equals(cell.property("hanging"));
    }

    private static StructureCell findSecondary(StructureCell primary, Map<String, StructureCell> byPos) {
        if (primary.blockId().endsWith("_door") || "lower".equals(primary.property("half"))) {
            StructureCell above = byPos.get(key(primary.x(), primary.y() + 1, primary.z()));
            if (above != null && above.blockId().equals(primary.blockId()) && "upper".equals(above.property("half"))) {
                return above;
            }
        }
        if (primary.blockId().endsWith("_bed") && !"head".equals(primary.property("part"))) {
            int[] offset = bedHeadOffset(primary.property("facing"));
            StructureCell head = byPos.get(key(primary.x() + offset[0], primary.y(), primary.z() + offset[1]));
            if (head != null && head.blockId().equals(primary.blockId()) && "head".equals(head.property("part"))) {
                return head;
            }
        }
        return null;
    }

    static int[] bedHeadOffset(String facing) {
        return switch (facing == null ? "north" : facing) {
            case "south" -> new int[] {0, 1};
            case "west" -> new int[] {-1, 0};
            case "east" -> new int[] {1, 0};
            default -> new int[] {0, -1};
        };
    }
}
