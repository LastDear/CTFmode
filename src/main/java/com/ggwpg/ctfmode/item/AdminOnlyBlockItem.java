package com.ggwpg.ctfmode.item;

import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;

public class AdminOnlyBlockItem extends BlockItem {
    public AdminOnlyBlockItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public InteractionResult place(BlockPlaceContext context) {
        Player player = context.getPlayer();

        if (player != null && !player.hasPermissions(2)) {
            if (!player.level().isClientSide()) {
                player.displayClientMessage(Component.translatable("message.ctfmode.admin_only"), true);
            }
            return InteractionResult.FAIL;
        }

        return super.place(context);
    }
}
