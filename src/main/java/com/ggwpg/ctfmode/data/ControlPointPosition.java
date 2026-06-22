package com.ggwpg.ctfmode.data;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

public record ControlPointPosition(ResourceKey<Level> dimension, BlockPos pos) {
}
