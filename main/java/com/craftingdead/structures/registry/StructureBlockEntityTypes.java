package com.craftingdead.structures.registry;

import com.craftingdead.structures.CraftingDeadStructures;
import com.craftingdead.structures.world.level.block.entity.ZombieSpawnerCageBlockEntity;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class StructureBlockEntityTypes {

  public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
      DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, CraftingDeadStructures.ID);

  public static final Supplier<BlockEntityType<ZombieSpawnerCageBlockEntity>> ZOMBIE_SPAWNER_CAGE =
      BLOCK_ENTITIES.register("zombie_spawner_cage",
          () -> BlockEntityType.Builder.of(ZombieSpawnerCageBlockEntity::new,
              StructureBlocks.ZOMBIE_SPAWNER_CAGE.get()).build(null));

  private StructureBlockEntityTypes() {}
}
