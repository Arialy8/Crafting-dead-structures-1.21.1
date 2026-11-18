package com.craftingdead.structures.world.level.levelgen;

import com.craftingdead.structures.registry.StructureTypes;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

public final class MultipartStructure extends Structure {

  public static final MapCodec<MultipartStructure> CODEC =
      RecordCodecBuilder.mapCodec(instance -> instance.group(
          settingsCodec(instance),
          ResourceLocation.CODEC.fieldOf("template").forGetter(value -> value.template),
          Codec.intRange(1, 16).fieldOf("pieces_x").forGetter(value -> value.piecesX),
          Codec.intRange(1, 8).fieldOf("pieces_y").forGetter(value -> value.piecesY),
          Codec.intRange(1, 16).fieldOf("pieces_z").forGetter(value -> value.piecesZ),
          Codec.intRange(16, 512).fieldOf("piece_size").forGetter(value -> value.pieceSize),
          Codec.intRange(1, 4096).fieldOf("total_size_x").forGetter(value -> value.totalSizeX),
          Codec.intRange(1, 1024).fieldOf("total_size_y").forGetter(value -> value.totalSizeY),
          Codec.intRange(1, 4096).fieldOf("total_size_z").forGetter(value -> value.totalSizeZ),
          Codec.intRange(-256, 512).optionalFieldOf("height_offset", 0)
              .forGetter(value -> value.heightOffset),
          Codec.BOOL.optionalFieldOf("ocean_floor", false)
              .forGetter(value -> value.oceanFloor)
      ).apply(instance, MultipartStructure::new));

  private final ResourceLocation template;
  private final int piecesX;
  private final int piecesY;
  private final int piecesZ;
  private final int pieceSize;
  private final int totalSizeX;
  private final int totalSizeY;
  private final int totalSizeZ;
  private final int heightOffset;
  private final boolean oceanFloor;

  public MultipartStructure(StructureSettings settings, ResourceLocation template,
      int piecesX, int piecesY, int piecesZ, int pieceSize,
      int totalSizeX, int totalSizeY, int totalSizeZ,
      int heightOffset, boolean oceanFloor) {
    super(settings);
    this.template = template;
    this.piecesX = piecesX;
    this.piecesY = piecesY;
    this.piecesZ = piecesZ;
    this.pieceSize = pieceSize;
    this.totalSizeX = totalSizeX;
    this.totalSizeY = totalSizeY;
    this.totalSizeZ = totalSizeZ;
    this.heightOffset = heightOffset;
    this.oceanFloor = oceanFloor;
  }

  @Override
  protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
    int centerX = context.chunkPos().getMiddleBlockX();
    int centerZ = context.chunkPos().getMiddleBlockZ();
    Heightmap.Types heightmap = this.oceanFloor
        ? Heightmap.Types.OCEAN_FLOOR_WG : Heightmap.Types.WORLD_SURFACE_WG;
    int originX = centerX - this.totalSizeX / 2;
    int originZ = centerZ - this.totalSizeZ / 2;
    List<Integer> sampledHeights = new ArrayList<>();
    for (int sampleX = 0; sampleX <= 4; sampleX++) {
      for (int sampleZ = 0; sampleZ <= 4; sampleZ++) {
        int x = originX + this.totalSizeX * sampleX / 4;
        int z = originZ + this.totalSizeZ * sampleZ / 4;
        sampledHeights.add(context.chunkGenerator().getBaseHeight(
            x, z, heightmap, context.heightAccessor(), context.randomState()));
      }
    }
    Collections.sort(sampledHeights);
    int surfaceY = sampledHeights.get(sampledHeights.size() / 2);
    if (this.heightOffset < 40) {
      int terrainVariance = sampledHeights.getLast() - sampledHeights.getFirst();
      int maximumVariance = Math.max(this.totalSizeX, this.totalSizeZ) > 300 ? 12 : 18;
      if (terrainVariance > maximumVariance) {
        return Optional.empty();
      }
    }
    BlockPos origin = new BlockPos(
        originX,
        surfaceY + this.heightOffset,
        originZ);
    return Optional.of(new GenerationStub(origin,
        builder -> this.addPieces(context.structureTemplateManager(), builder, origin)));
  }

  private void addPieces(StructureTemplateManager manager,
      StructurePiecesBuilder builder, BlockPos origin) {
    for (int x = 0; x < this.piecesX; x++) {
      for (int y = 0; y < this.piecesY; y++) {
        for (int z = 0; z < this.piecesZ; z++) {
          ResourceLocation piece = ResourceLocation.fromNamespaceAndPath(
              this.template.getNamespace(),
              this.template.getPath() + "_x" + x + "_y" + y + "_z" + z);
          builder.addPiece(new MultipartTemplatePiece(
              manager, piece, origin.offset(
                  x * this.pieceSize, y * this.pieceSize, z * this.pieceSize)));
        }
      }
    }
  }

  @Override
  public StructureType<?> type() {
    return StructureTypes.MULTIPART.get();
  }
}
