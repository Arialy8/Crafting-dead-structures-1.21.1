package com.craftingdead.structures.world.level.block;

import com.craftingdead.structures.registry.StructureBlockEntityTypes;
import com.craftingdead.structures.world.level.block.entity.ZombieSpawnerCageBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public final class ZombieSpawnerCageBlock extends Block implements EntityBlock {

  public ZombieSpawnerCageBlock(Properties properties) {
    super(properties);
  }

  @Override
  public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
    return new ZombieSpawnerCageBlockEntity(pos, state);
  }

  @Override
  @SuppressWarnings("unchecked")
  public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(
      Level level, BlockState state, BlockEntityType<T> type) {
    if (level.isClientSide() || type != StructureBlockEntityTypes.ZOMBIE_SPAWNER_CAGE.get()) {
      return null;
    }
    return (tickLevel, tickPos, tickState, blockEntity) ->
        ZombieSpawnerCageBlockEntity.serverTick(
            tickLevel, tickPos, tickState, (ZombieSpawnerCageBlockEntity) blockEntity);
  }
}
