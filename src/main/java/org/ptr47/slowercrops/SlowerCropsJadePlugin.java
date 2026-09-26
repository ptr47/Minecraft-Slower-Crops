package org.ptr47.slowercrops;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;
import snownee.jade.api.WailaPlugin;

@WailaPlugin
public final class SlowerCropsJadePlugin implements IWailaPlugin {
    private static final String MINIMUM_TICKS = "slowercrops_minimum_ticks";
    private static final String MAXIMUM_TICKS = "slowercrops_maximum_ticks";
    private static final GrowthDataProvider DATA_PROVIDER = new GrowthDataProvider();
    private static final GrowthTooltip TOOLTIP = new GrowthTooltip();

    @Override
    public void register(IWailaCommonRegistration registration) {
        registration.registerBlockDataProvider(DATA_PROVIDER, Block.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(TOOLTIP, Block.class);
    }

    private static final class GrowthDataProvider implements snownee.jade.api.IServerDataProvider<BlockAccessor> {
        @Override
        public ResourceLocation getUid() {
            return ResourceLocation.fromNamespaceAndPath(Slowercrops.MODID, "growth_data");
        }

        @Override
        public boolean shouldRequestData(BlockAccessor accessor) {
            return GrowthTimers.isManagedGrowthBlock(accessor.getBlockState())
                    && GrowthTimers.hasRemainingGrowth(accessor.getBlockState());
        }

        @Override
        public void appendServerData(net.minecraft.nbt.CompoundTag data, BlockAccessor accessor) {
            if (!(accessor.getLevel() instanceof ServerLevel level)) {
                return;
            }
            GrowthTimers.GrowthEstimate estimate = GrowthTimers.get(level)
                    .estimate(level, accessor.getPosition(), accessor.getBlockState());
            if (estimate != null) {
                data.putLong(MINIMUM_TICKS, estimate.minimumTicks());
                data.putLong(MAXIMUM_TICKS, estimate.maximumTicks());
            }
        }
    }

    private static final class GrowthTooltip implements IBlockComponentProvider {
        @Override
        public ResourceLocation getUid() {
            return ResourceLocation.fromNamespaceAndPath(Slowercrops.MODID, "growth_time");
        }

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            var data = accessor.getServerData();
            if (!data.contains(MINIMUM_TICKS) || !data.contains(MAXIMUM_TICKS)) {
                return;
            }
            long minimumDays = daysRoundedUp(data.getLong(MINIMUM_TICKS));
            long maximumDays = daysRoundedUp(data.getLong(MAXIMUM_TICKS));
            tooltip.add(Component.translatable(
                    "jade.slowercrops.growth_time_range", boldDays(minimumDays), boldDays(maximumDays)));
        }
    }

    private static Component boldDays(long days) {
        return Component.literal(Long.toString(days)).withStyle(ChatFormatting.WHITE);
    }

    private static long daysRoundedUp(long ticks) {
        return Math.max(1L, (ticks + 23999L) / 24000L);
    }
}

