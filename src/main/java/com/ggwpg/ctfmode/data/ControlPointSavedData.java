package com.ggwpg.ctfmode.data;

import com.ggwpg.ctfmode.CTFMode;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedData.Factory;

public class ControlPointSavedData extends SavedData {
    private static final String DATA_NAME = CTFMode.MODID + "_control_points";
    private static final String POINTS_KEY = "control_points";
    private static final String DIMENSION_KEY = "dimension";
    private static final String POS_KEY = "pos";

    private final Set<ControlPointPosition> controlPoints = new HashSet<>();

    public static ControlPointSavedData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(
                new Factory<>(ControlPointSavedData::new, ControlPointSavedData::load, null),
                DATA_NAME
        );
    }

    public static ControlPointSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        ControlPointSavedData data = new ControlPointSavedData();
        ListTag points = tag.getList(POINTS_KEY, Tag.TAG_COMPOUND);

        for (int i = 0; i < points.size(); i++) {
            CompoundTag pointTag = points.getCompound(i);
            ResourceLocation dimensionId = ResourceLocation.parse(pointTag.getString(DIMENSION_KEY));
            ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, dimensionId);
            BlockPos pos = BlockPos.of(pointTag.getLong(POS_KEY));
            data.controlPoints.add(new ControlPointPosition(dimension, pos));
        }

        return data;
    }

    public void add(ServerLevel level, BlockPos pos) {
        if (controlPoints.add(new ControlPointPosition(level.dimension(), pos.immutable()))) {
            setDirty();
            CTFMode.LOGGER.info("Saved control point at {} {} {} in {}", pos.getX(), pos.getY(), pos.getZ(), level.dimension().location());
        }
    }

    public void remove(ServerLevel level, BlockPos pos) {
        if (controlPoints.remove(new ControlPointPosition(level.dimension(), pos.immutable()))) {
            setDirty();
            CTFMode.LOGGER.info("Removed control point at {} {} {} in {}", pos.getX(), pos.getY(), pos.getZ(), level.dimension().location());
        }
    }

    public Set<ControlPointPosition> getControlPoints() {
        return Collections.unmodifiableSet(controlPoints);
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag points = new ListTag();

        for (ControlPointPosition controlPoint : controlPoints) {
            CompoundTag pointTag = new CompoundTag();
            pointTag.putString(DIMENSION_KEY, controlPoint.dimension().location().toString());
            pointTag.putLong(POS_KEY, controlPoint.pos().asLong());
            points.add(pointTag);
        }

        tag.put(POINTS_KEY, points);
        return tag;
    }
}
