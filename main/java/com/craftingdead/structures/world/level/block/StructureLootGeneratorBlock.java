package com.craftingdead.structures.world.level.block;

import com.craftingdead.structures.config.StructureServerConfig;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class StructureLootGeneratorBlock extends Block {

  public static final MapCodec<StructureLootGeneratorBlock> CODEC = simpleCodec(
      properties -> new StructureLootGeneratorBlock(properties, "civilian"));

  private final String tier;

  public StructureLootGeneratorBlock(Properties properties, String tier) {
    super(properties);
    this.tier = tier;
  }

  @Override
  protected MapCodec<? extends Block> codec() {
    return CODEC;
  }

  @Override
  protected RenderShape getRenderShape(BlockState state) {
    return RenderShape.INVISIBLE;
  }

  @Override
  protected VoxelShape getShape(BlockState state, BlockGetter level,
      BlockPos pos, CollisionContext context) {
    return Shapes.empty();
  }

  @Override
  protected void onPlace(BlockState state, Level level, BlockPos pos,
      BlockState oldState, boolean movedByPiston) {
    super.onPlace(state, level, pos, oldState, movedByPiston);
    if (!level.isClientSide()) {
      level.scheduleTick(pos, this, 1);
    }
  }

  @Override
  protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
    String targetPath = switch (this.tier) {
      case "medical" -> "medic_loot_gen";
      case "police" -> "police_loot_gen";
      case "rare_civilian" -> "civilian_rare_loot_gen";
      case "military" -> "military_loot_gen";
      default -> "civilian_loot_gen";
    };
    Block generator = BuiltInRegistries.BLOCK.get(
        ResourceLocation.fromNamespaceAndPath("craftingdeadsurvival", targetPath));
    if (generator != null && generator != net.minecraft.world.level.block.Blocks.AIR) {
      level.setBlockAndUpdate(pos, generator.defaultBlockState());
      level.scheduleTick(pos, generator, 1);
      return;
    }
    level.scheduleTick(pos, this, 20);
  }
}
