package com.craftingdead.structures.world.level.levelgen;

import com.craftingdead.structures.registry.StructurePieceTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.TemplateStructurePiece;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

public final class MultipartTemplatePiece extends TemplateStructurePiece {

  public MultipartTemplatePiece(StructureTemplateManager manager,
      ResourceLocation template, BlockPos position) {
    super(StructurePieceTypes.MULTIPART_TEMPLATE.get(), 0, manager,
        template, template.toString(), placementSettings(), position);
  }

  public MultipartTemplatePiece(StructureTemplateManager manager, CompoundTag tag) {
    super(StructurePieceTypes.MULTIPART_TEMPLATE.get(), tag, manager,
        ignored -> placementSettings());
  }

  private static StructurePlaceSettings placementSettings() {
    return new StructurePlaceSettings()
        .setMirror(Mirror.NONE)
        .setRotation(Rotation.NONE)
        .setIgnoreEntities(false)
        .setFinalizeEntities(true)
        .addProcessor(StructureRandomizationProcessor.INSTANCE);
  }

  @Override
  public synchronized void postProcess(
      WorldGenLevel level,
      StructureManager structureManager,
      ChunkGenerator chunkGenerator,
      RandomSource random,
      BoundingBox chunkBox,
      ChunkPos chunkPos,
      BlockPos pivot) {
    // TemplateStructurePiece mutates its shared placement settings with the current chunk box.
    // C2ME may process the same piece for several chunks concurrently, so serialize each piece
    // to prevent one worker from placing blocks with another worker's bounding box.
    super.postProcess(level, structureManager, chunkGenerator, random, chunkBox, chunkPos, pivot);
  }

  @Override
  protected void handleDataMarker(String marker, BlockPos pos,
      ServerLevelAccessor level, RandomSource random, BoundingBox box) {}
}
