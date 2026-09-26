package org.ptr47.slowercrops;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class Config {
    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.IntValue CROP_MIN_DAYS;
    public static final ModConfigSpec.IntValue CROP_MAX_DAYS;
    public static final ModConfigSpec.IntValue TREE_MIN_DAYS;
    public static final ModConfigSpec.IntValue TREE_MAX_DAYS;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.push("growth");
        CROP_MIN_DAYS = builder
                .comment("Minimum in-game days for supported crops to mature in favorable conditions.")
                .defineInRange("cropMinDays", 12, 1, 1000);
        CROP_MAX_DAYS = builder
                .comment("Maximum in-game days for supported crops to mature in favorable conditions.")
                .defineInRange("cropMaxDays", 18, 1, 1000);
        TREE_MIN_DAYS = builder
                .comment("Minimum in-game days for supported saplings to grow into trees in favorable conditions.")
                .defineInRange("treeMinDays", 16, 1, 1000);
        TREE_MAX_DAYS = builder
                .comment("Maximum in-game days for supported saplings to grow into trees in favorable conditions.")
                .defineInRange("treeMaxDays", 24, 1, 1000);
        builder.pop();

        SPEC = builder.build();
    }

    private Config() {
    }
}
