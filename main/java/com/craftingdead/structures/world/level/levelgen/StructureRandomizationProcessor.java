package com.craftingdead.structures.world.level.levelgen;

import com.craftingdead.structures.registry.StructureProcessorTypes;
import com.mojang.serialization.MapCodec;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

public final class StructureRandomizationProcessor extends StructureProcessor {

  public static final StructureRandomizationProcessor INSTANCE =
      new StructureRandomizationProcessor();
  public static final MapCodec<StructureRandomizationProcessor> CODEC =
      MapCodec.unit(INSTANCE);

  private StructureRandomizationProcessor() {}

  @Override
  public StructureTemplate.StructureBlockInfo processBlock(
      LevelReader level, BlockPos structurePos, BlockPos pivot,
      StructureTemplate.StructureBlockInfo original,
      StructureTemplate.StructureBlockInfo current,
      StructurePlaceSettings settings) {
    ResourceLocation id = BuiltInRegistries.BLOCK.getKey(current.state().getBlock());
    if (!id.getNamespace().equals("craftingdeadstructures")
        || !id.getPath().endsWith("_marker")) {
      return current;
    }

    String path = id.getPath();
    if (path.endsWith("_container_marker")) {
      String tier = path.substring(0, path.length() - "_container_marker".length());
      String table = switch (tier) {
        case "medical" -> "medical";
        case "police" -> "police";
        case "military" -> "military";
        case "weapons" -> "weapons";
        default -> "civilian_general";
      };
      CompoundTag nbt = new CompoundTag();
      nbt.putString("id", "minecraft:chest");
      nbt.putString("LootTable",
          "craftingdeadstructures:chests/" + table);
      return replacement(current, "minecraft:chest", nbt);
    }
    if (path.endsWith("zombie_spawner_marker")) {
      int tier = path.startsWith("elite_") ? 2
          : path.startsWith("dangerous_") ? 1 : 0;
      CompoundTag nbt = new CompoundTag();
      nbt.putString("id", "craftingdeadstructures:zombie_spawner_cage");
      nbt.putInt("SpawnerTier", tier);
      return replacement(current,
          "craftingdeadstructures:zombie_spawner_cage", nbt);
    }
    String tier = path.substring(0, path.length() - "_loot_marker".length());
    String generator = switch (tier) {
      case "medical" -> "craftingdeadsurvival:medic_loot_gen";
      case "police" -> "craftingdeadsurvival:police_loot_gen";
      case "rare_civilian" -> "craftingdeadsurvival:civilian_rare_loot_gen";
      case "military" -> "craftingdeadsurvival:military_loot_gen";
      default -> "craftingdeadsurvival:civilian_loot_gen";
    };
    return replacement(current, generator);
  }

  @Override
  public List<StructureTemplate.StructureBlockInfo> finalizeProcessing(
      ServerLevelAccessor level, BlockPos structurePos, BlockPos pivot,
      List<StructureTemplate.StructureBlockInfo> original,
      List<StructureTemplate.StructureBlockInfo> processed,
      StructurePlaceSettings settings) {
    for (StructureTemplate.StructureBlockInfo info : processed) {
      ResourceLocation id = BuiltInRegistries.BLOCK.getKey(info.state().getBlock());
      if (id.getNamespace().equals("craftingdeadsurvival")
          && id.getPath().endsWith("_loot_gen")) {
        level.scheduleTick(info.pos(), info.state().getBlock(), 1);
      }
    }
    return processed;
  }

  @Override
  public StructureTemplate.StructureEntityInfo processEntity(
      LevelReader level, BlockPos structurePos,
      StructureTemplate.StructureEntityInfo original,
      StructureTemplate.StructureEntityInfo current,
      StructurePlaceSettings settings, StructureTemplate template) {
    if (!current.nbt.contains("TileX")) {
      return current;
    }
    CompoundTag nbt = current.nbt.copy();
    nbt.putInt("TileX", current.blockPos.getX());
    nbt.putInt("TileY", current.blockPos.getY());
    nbt.putInt("TileZ", current.blockPos.getZ());
    return new StructureTemplate.StructureEntityInfo(
        current.pos, current.blockPos, nbt);
  }

  private static StructureTemplate.StructureBlockInfo replacement(
      StructureTemplate.StructureBlockInfo current, String blockPath) {
    return replacement(current, blockPath, null);
  }

  private static StructureTemplate.StructureBlockInfo replacement(
      StructureTemplate.StructureBlockInfo current, String blockPath, CompoundTag nbt) {
    Block block = blockPath == null ? Blocks.AIR : BuiltInRegistries.BLOCK.get(
        ResourceLocation.parse(blockPath));
    if (block == null) {
      block = Blocks.AIR;
    }
    return new StructureTemplate.StructureBlockInfo(
        current.pos(), block.defaultBlockState(), nbt);
  }

  @Override
  protected StructureProcessorType<?> getType() {
    return StructureProcessorTypes.RANDOMIZE.get();
  }
}
