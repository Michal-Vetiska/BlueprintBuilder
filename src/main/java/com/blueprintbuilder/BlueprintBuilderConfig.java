package com.blueprintbuilder;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class BlueprintBuilderConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.DoubleValue BLOCKS_PER_SECOND = BUILDER
            .comment("How many structure blocks the construction engine places per second while materials are available.")
            .defineInRange("blocksPerSecond", 3.0D, 0.1D, 40.0D);

    public static final ModConfigSpec.IntValue MAX_BLOCKS_PER_TICK = BUILDER
            .comment("Upper bound on blocks placed in a single server tick, even if the configured speed would allow more.")
            .defineInRange("maxBlocksPerTick", 4, 1, 64);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private BlueprintBuilderConfig() {
    }
}
