package com.craftingdead.structures;

import com.craftingdead.structures.command.StructureCommands;
import com.craftingdead.structures.config.StructureServerConfig;
import com.craftingdead.structures.registry.StructureBlockEntityTypes;
import com.craftingdead.structures.registry.StructureBlocks;
import com.craftingdead.structures.registry.StructureProcessorTypes;
import com.craftingdead.structures.registry.StructurePieceTypes;
import com.craftingdead.structures.registry.StructureTypes;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;

@Mod(CraftingDeadStructures.ID)
public final class CraftingDeadStructures {

  public static final String ID = "craftingdeadstructures";

  public CraftingDeadStructures(IEventBus modEventBus, ModContainer modContainer) {
    StructureBlocks.BLOCKS.register(modEventBus);
    StructureBlocks.ITEMS.register(modEventBus);
    StructureBlockEntityTypes.BLOCK_ENTITIES.register(modEventBus);
    StructureProcessorTypes.PROCESSORS.register(modEventBus);
    StructurePieceTypes.PIECES.register(modEventBus);
    StructureTypes.TYPES.register(modEventBus);
    modContainer.registerConfig(ModConfig.Type.COMMON, StructureServerConfig.SPEC);
    NeoForge.EVENT_BUS.register(new StructureCommands());
  }
}
