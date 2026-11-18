package com.craftingdead.structures.registry;

import com.craftingdead.structures.CraftingDeadStructures;
import com.craftingdead.structures.world.level.block.StructureLootGeneratorBlock;
import com.craftingdead.structures.world.level.block.ZombieSpawnerCageBlock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class StructureBlocks {

  public static final DeferredRegister<Block> BLOCKS =
      DeferredRegister.create(Registries.BLOCK, CraftingDeadStructures.ID);
  public static final DeferredRegister<Item> ITEMS =
      DeferredRegister.create(Registries.ITEM, CraftingDeadStructures.ID);

  public static final Supplier<Block> ZOMBIE_SPAWNER_CAGE =
      BLOCKS.register("zombie_spawner_cage", () -> new ZombieSpawnerCageBlock(
          BlockBehaviour.Properties.ofFullCopy(Blocks.SPAWNER)
              .strength(5.0F)
              .lightLevel(state -> 12)
              .requiresCorrectToolForDrops()));

  public static final Map<String, Supplier<Block>> LOOT_GENERATORS = new LinkedHashMap<>();

  static {
    ITEMS.register("zombie_spawner_cage",
        () -> new BlockItem(ZOMBIE_SPAWNER_CAGE.get(), new Item.Properties()));
    registerLootGenerator("civilian");
    registerLootGenerator("medical");
    registerLootGenerator("police");
    registerLootGenerator("rare_civilian");
    registerLootGenerator("military");
    registerMarker("civilian_loot");
    registerMarker("medical_loot");
    registerMarker("police_loot");
    registerMarker("rare_civilian_loot");
    registerMarker("military_loot");
    registerMarker("zombie_spawner");
    registerMarker("dangerous_zombie_spawner");
    registerMarker("elite_zombie_spawner");
    registerMarker("civilian_container");
    registerMarker("medical_container");
    registerMarker("police_container");
    registerMarker("military_container");
    registerMarker("weapons_container");
  }

  private static void registerLootGenerator(String tier) {
    LOOT_GENERATORS.put(tier, BLOCKS.register(tier + "_ground_loot_generator",
        () -> new StructureLootGeneratorBlock(
            BlockBehaviour.Properties.ofFullCopy(Blocks.STRUCTURE_VOID)
                .noCollission()
                .noOcclusion()
                .strength(5.0F),
            tier)));
  }

  private static void registerMarker(String name) {
    Supplier<Block> marker = BLOCKS.register(name + "_marker",
        () -> new Block(BlockBehaviour.Properties.ofFullCopy(Blocks.STRUCTURE_VOID)
            .noCollission()
            .noOcclusion()
            .strength(-1.0F, 3600000.0F)));
    ITEMS.register(name + "_marker",
        () -> new BlockItem(marker.get(), new Item.Properties()));
  }

  private StructureBlocks() {}
}
