package com.craftingdead.structures.registry;

import com.craftingdead.structures.CraftingDeadStructures;
import com.craftingdead.structures.world.level.levelgen.MultipartTemplatePiece;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class StructurePieceTypes {

  public static final DeferredRegister<StructurePieceType> PIECES =
      DeferredRegister.create(Registries.STRUCTURE_PIECE, CraftingDeadStructures.ID);

  public static final Supplier<StructurePieceType> MULTIPART_TEMPLATE =
      PIECES.register("multipart_template",
          () -> (context, tag) -> new MultipartTemplatePiece(
              context.structureTemplateManager(), tag));

  private StructurePieceTypes() {}
}
