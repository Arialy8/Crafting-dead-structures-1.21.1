package com.craftingdead.structures.registry;

import com.craftingdead.structures.CraftingDeadStructures;
import com.craftingdead.structures.world.level.levelgen.StructureRandomizationProcessor;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class StructureProcessorTypes {

  public static final DeferredRegister<StructureProcessorType<?>> PROCESSORS =
      DeferredRegister.create(Registries.STRUCTURE_PROCESSOR, CraftingDeadStructures.ID);

  public static final Supplier<StructureProcessorType<StructureRandomizationProcessor>> RANDOMIZE =
      PROCESSORS.register("structure_randomization",
          () -> () -> StructureRandomizationProcessor.CODEC);

  private StructureProcessorTypes() {}
}
