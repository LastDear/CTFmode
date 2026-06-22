package com.ggwpg.ctfmode.block;

import com.ggwpg.ctfmode.CTFMode;
import com.ggwpg.ctfmode.data.ControlPointSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public class ControlPointBlock extends Block {
    public ControlPointBlock(Properties properties) {
        super(properties);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);

        if (level instanceof ServerLevel serverLevel) {
            ControlPointSavedData.get(serverLevel).add(serverLevel, pos);
            CTFMode.LOGGER.info("Control point placed at {} {} {}", pos.getX(), pos.getY(), pos.getZ());
        }
    }
}
