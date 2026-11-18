package com.craftingdead.structures.registry;

import com.craftingdead.structures.CraftingDeadStructures;
import com.craftingdead.structures.world.level.levelgen.MultipartStructure;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class StructureTypes {

  public static final DeferredRegister<StructureType<?>> TYPES =
      DeferredRegister.create(Registries.STRUCTURE_TYPE, CraftingDeadStructures.ID);

  public static final Supplier<StructureType<MultipartStructure>> MULTIPART =
      TYPES.register("multipart", () -> () -> MultipartStructure.CODEC);

  private StructureTypes() {}
}
