package org.ptr47.slowercrops;

import com.mojang.logging.LogUtils;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import org.slf4j.Logger;

@Mod(Slowercrops.MODID)
public class Slowercrops {
    public static final String MODID = "slowercrops";
    private static final Logger LOGGER = LogUtils.getLogger();

    public Slowercrops() {
        ModLoadingContext.get().getActiveContainer().registerConfig(ModConfig.Type.COMMON, Config.SPEC);
        LOGGER.info("Slower Crops initialized");
    }
}
