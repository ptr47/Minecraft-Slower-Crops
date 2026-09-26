package org.ptr47.slowercrops;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.PitcherCropBlock;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.NotNull;

/**
 * Per-position, persistent growth clocks. Time only accumulates while the plant is eligible to grow.
 */
public final class GrowthTimers extends SavedData {
    private static final String DATA_NAME = "slowercrops_growth_timers";
    private final Map<Long, Timer> timers = new HashMap<>();

    private GrowthTimers() {
    }

    public static GrowthTimers get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                new Factory<>(GrowthTimers::new, GrowthTimers::load, DataFixTypes.LEVEL), DATA_NAME);
    }

    private static GrowthTimers load(CompoundTag tag, HolderLookup.Provider registries) {
        GrowthTimers data = new GrowthTimers();
        ListTag entries = tag.getList("plants", Tag.TAG_COMPOUND);
        for (int i = 0; i < entries.size(); i++) {
            CompoundTag entry = entries.getCompound(i);
            data.timers.put(entry.getLong("pos"), new Timer(
                    entry.getString("block"), entry.getInt("age"), entry.getLong("elapsed"),
                    entry.getLong("last"), entry.getLong("duration")));
        }
        return data;
    }

    @Override
    public @NotNull CompoundTag save(CompoundTag tag, HolderLookup.@NotNull Provider registries) {
        ListTag entries = new ListTag();
        timers.forEach((pos, timer) -> {
            CompoundTag entry = new CompoundTag();
            entry.putLong("pos", pos);
            entry.putString("block", timer.block);
            entry.putInt("age", timer.age);
            entry.putLong("elapsed", timer.elapsed);
            entry.putLong("last", timer.last);
            entry.putLong("duration", timer.duration);
            entries.add(entry);
        });
        tag.put("plants", entries);
        return tag;
    }

    public static boolean isManagedGrowthBlock(BlockState state) {
        var block = state.getBlock();
        return block instanceof SaplingBlock
                || block instanceof CropBlock
                || block instanceof StemBlock
                || block instanceof NetherWartBlock
                || block instanceof PitcherCropBlock
                || block instanceof CocoaBlock;
    }

    public static boolean hasRemainingGrowth(BlockState state) {
        if (state.getBlock() instanceof SaplingBlock) {
            return state.getValue(SaplingBlock.STAGE) < 2;
        }
        IntegerProperty ageProperty = getAgeProperty(state);
        return ageProperty != null
                && state.getValue(ageProperty) < ageProperty.getPossibleValues().stream()
                .mapToInt(Integer::intValue).max().orElse(state.getValue(ageProperty));
    }

    /**
     * Returns an ideal-conditions estimate for reaching maturity, or {@code null} if the
     * block is not a supported immature crop or sapling.
     */
    public GrowthEstimate estimate(ServerLevel level, BlockPos pos, BlockState state) {
        if (!hasRemainingGrowth(state)) {
            return null;
        }

        boolean sapling = state.getBlock() instanceof SaplingBlock;
        String blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
        IntegerProperty ageProperty = sapling ? null : getAgeProperty(state);
        int age = sapling ? state.getValue(SaplingBlock.STAGE) : state.getValue(ageProperty);
        int maxAge = sapling ? 2 : ageProperty.getPossibleValues().stream()
                .mapToInt(Integer::intValue).max().orElse(age);

        long remainingNumerator = maxAge - age;
        long remainingDenominator = maxAge;
        Timer timer = timers.get(pos.asLong());
        if (timer != null && timer.block.equals(blockId) && age >= timer.age) {
            // Treat time since the last random-tick opportunity as favorable growth time.
            long elapsed = Math.max(timer.elapsed, timer.duration * age / maxAge)
                    + Math.max(0L, level.getGameTime() - timer.last);
            remainingNumerator = Math.max(0L, timer.duration - elapsed);
            remainingDenominator = timer.duration;
        }

        int minDays = sapling ? Config.TREE_MIN_DAYS.get() : Config.CROP_MIN_DAYS.get();
        int maxDays = sapling ? Config.TREE_MAX_DAYS.get() : Config.CROP_MAX_DAYS.get();
        maxDays = Math.max(minDays, maxDays);
        return new GrowthEstimate(
                scaleRemaining(minDays * 24000L, remainingNumerator, remainingDenominator),
                scaleRemaining(maxDays * 24000L, remainingNumerator, remainingDenominator));
    }

    public record GrowthEstimate(long minimumTicks, long maximumTicks) {
    }

    /** Handles a crop or sapling at one of its ordinary random-tick opportunities. */
    public void tick(ServerLevel level, BlockPos pos, BlockState state, RandomSource random) {
        long key = pos.asLong();
        String blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
        boolean sapling = state.getBlock() instanceof SaplingBlock;
        IntegerProperty ageProperty = sapling ? null : getAgeProperty(state);
        if (!sapling && ageProperty == null) {
            state.randomTick(level, pos, random);
            return;
        }

        int age = sapling ? state.getValue(SaplingBlock.STAGE) : state.getValue(ageProperty);
        int maxAge = sapling
                ? 2
                : ageProperty.getPossibleValues().stream().mapToInt(Integer::intValue).max().orElse(age);
        if (age >= maxAge) {
            timers.remove(key);
            return;
        }

        long now = level.getGameTime();
        Timer timer = timers.get(key);
        if (timer == null || !timer.block.equals(blockId)) {
            timer = createTimer(pos, blockId, age, maxAge, now, sapling);
            timers.put(key, timer);
            setDirty();
            return;
        }
        if (timer.age != age) {
            if (age > timer.age) {
                // Preserve elapsed time when bone meal or another effect advances the crop.
                timer.age = age;
                timer.elapsed = Math.max(timer.elapsed, timer.duration * age / maxAge);
                timer.last = now;
                setDirty();
            } else {
                timer = createTimer(pos, blockId, age, maxAge, now, sapling);
                timers.put(key, timer);
                setDirty();
                return;
            }
        }

        // Account for plants that were already part-grown before their timer was created.
        timer.elapsed = Math.max(timer.elapsed, timer.duration * age / maxAge);

        // A position receives random ticks at irregular intervals. Credit all elapsed
        // in-game time, including periods when its chunk was unloaded.
        long delta = Math.max(0L, now - timer.last);
        timer.last = now;
        timer.elapsed += delta;
        if (!canGrowNow(level, pos, state)) {
            return;
        }
        if (!hasIdealConditions(level, pos, state)) {
            timer.elapsed -= delta * 3L / 4L;
        }
        long threshold = timer.duration * (age + 1L) / maxAge;
        if (timer.elapsed >= threshold) {
            if (sapling) {
                ((SaplingBlock) state.getBlock()).advanceTree(level, pos, state, random);
                BlockState after = level.getBlockState(pos);
                if (after.getBlock() == state.getBlock() && after.hasProperty(SaplingBlock.STAGE)) {
                    timer.age = after.getValue(SaplingBlock.STAGE);
                } else {
                    timers.remove(key);
                }
            } else if (canGrowNow(level, pos, state)) {
                level.setBlock(pos, state.setValue(ageProperty, age + 1), 2);
                if (age + 1 >= maxAge) {
                    timers.remove(key);
                } else {
                    timer.age = age + 1;
                }
            }
        }
        setDirty();
    }

    private static Timer createTimer(
            BlockPos pos, String block, int age, int maxAge, long now, boolean sapling) {
        int minDays = sapling ? Config.TREE_MIN_DAYS.get() : Config.CROP_MIN_DAYS.get();
        int maxDays = sapling ? Config.TREE_MAX_DAYS.get() : Config.CROP_MAX_DAYS.get();
        maxDays = Math.max(minDays, maxDays);
        long range = (long) (maxDays - minDays) * 24000L;
        long offset = range == 0 ? 0 : Math.floorMod(mix(pos.asLong()), range + 1);
        long duration = minDays * 24000L + offset;
        long elapsed = duration * age / maxAge;
        return new Timer(block, age, elapsed, now, duration);
    }

    private static long scaleRemaining(long duration, long remaining, long total) {
        if (remaining <= 0) {
            return 0;
        }
        return (duration * remaining + total - 1) / total;
    }

    private static IntegerProperty getAgeProperty(BlockState state) {
        if (!(state.getBlock() instanceof CropBlock || state.getBlock() instanceof StemBlock
                || state.getBlock() instanceof NetherWartBlock || state.getBlock() instanceof PitcherCropBlock
                || state.getBlock() instanceof CocoaBlock)) {
            return null;
        }
        for (var property : state.getProperties()) {
            if (property instanceof IntegerProperty integerProperty && property.getName().equals("age")) {
                return integerProperty;
            }
        }
        return null;
    }

    private static boolean canGrowNow(ServerLevel level, BlockPos pos, BlockState state) {
        return state.canSurvive(level, pos)
                && (!(state.getBlock() instanceof SaplingBlock)
                    || level.getMaxLocalRawBrightness(pos.above()) >= 9);
    }

    private static boolean hasIdealConditions(ServerLevel level, BlockPos pos, BlockState state) {
        if (level.getRawBrightness(pos, 0) < 9) {
            return false;
        }
        if (state.getBlock() instanceof SaplingBlock) {
            return level.getMaxLocalRawBrightness(pos.above()) >= 9;
        }
        BlockState soil = level.getBlockState(pos.below());
        return !soil.is(Blocks.FARMLAND) || soil.getValue(FarmBlock.MOISTURE) > 0;
    }

    private static long mix(long value) {
        value = (value ^ (value >>> 30)) * 0xbf58476d1ce4e5b9L;
        value = (value ^ (value >>> 27)) * 0x94d049bb133111ebL;
        return value ^ (value >>> 31);
    }

    private static final class Timer {
        private final String block;
        private int age;
        private long elapsed;
        private long last;
        private final long duration;

        private Timer(String block, int age, long elapsed, long last, long duration) {
            this.block = block;
            this.age = age;
            this.elapsed = elapsed;
            this.last = last;
            this.duration = duration;
        }
    }
}
