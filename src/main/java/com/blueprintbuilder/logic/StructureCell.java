package com.blueprintbuilder.logic;

import java.util.Map;

/**
 * One block inside a blueprint, stored in structure coordinates.
 * This type is intentionally free of Minecraft classes so the construction rules can be unit tested.
 */
public record StructureCell(int x, int y, int z, String blockId, Map<String, String> properties) {
    public StructureCell {
        properties = Map.copyOf(properties);
    }

    public StructureCell(int x, int y, int z, String blockId) {
        this(x, y, z, blockId, Map.of());
    }

    public String property(String name) {
        return properties.get(name);
    }
}
