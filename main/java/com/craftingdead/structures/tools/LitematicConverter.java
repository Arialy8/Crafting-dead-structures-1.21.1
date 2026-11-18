package com.craftingdead.structures.tools;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;

/**
 * Build-time converter for Litematica v7 files.
 *
 * <p>The converter intentionally runs outside the game. It creates vanilla structure templates,
 * removes captured living entities, clears ordinary saved inventories, preserves explicitly
 * whitelisted containers, masks exterior air and splits large schematics into 48-block pieces.</p>
 */
public final class LitematicConverter {

  private static int maxPieceSize = 48;
  private static final Set<String> PRESERVED_ENTITY_TYPES = Set.of(
      "minecraft:painting",
      "minecraft:item_frame",
      "minecraft:glow_item_frame",
      "minecraft:armor_stand",
      "minecraft:minecart",
      "minecraft:chest_minecart",
      "minecraft:hopper_minecart");

  public static void main(String[] args) throws Exception {
    if (args.length < 2 || args.length > 3) {
      throw new IllegalArgumentException(
          "Usage: convertLitematic <input.litematic> <output-directory> [piece-size]");
    }
    if (args.length == 3) {
      maxPieceSize = Math.max(16, Math.min(512, Integer.parseInt(args[2])));
    }
    Path input = Path.of(args[0]).toAbsolutePath().normalize();
    Path output = Path.of(args[1]).toAbsolutePath().normalize();
    Files.createDirectories(output);
    if (Files.isDirectory(input)) {
      try (var files = Files.list(input)) {
        for (Path file : files
            .filter(path -> path.getFileName().toString()
                .toLowerCase(Locale.ROOT).endsWith(".litematic"))
            .sorted()
            .toList()) {
          convert(file, output);
        }
      }
    } else {
      convert(input, output);
    }
  }

  private static void convert(Path input, Path output) throws IOException {
    if (input.getFileName().toString().equals("度假楼.litematic")
        || input.getFileName().toString().equals("楼梯向上屋子.litematic")) {
      System.out.println("Skipped removed structure " + input.getFileName());
      return;
    }
    CompoundTag root = NbtIo.readCompressed(input, NbtAccounter.unlimitedHeap());
    CompoundTag regions = root.getCompound("Regions");
    if (regions.getAllKeys().size() != 1) {
      throw new IOException("Current converter requires one region; found "
          + regions.getAllKeys().size() + " in " + input.getFileName());
    }

    String regionName = regions.getAllKeys().iterator().next();
    CompoundTag region = regions.getCompound(regionName);
    RegionData data = decodeRegion(region);
    if (isSkyIsland(input.getFileName().toString())) {
      removeReferenceBase(input.getFileName().toString(), data);
    } else {
      blendTerrainEdges(input.getFileName().toString(), data);
    }
    boolean[] exteriorAir = markExteriorAir(data);
    Map<Long, CompoundTag> blockEntities = readBlockEntities(
        input.getFileName().toString(), region, data);
    injectGameplayMarkers(input.getFileName().toString(), data, blockEntities);
    List<EntityData> entities = readEntities(region, data);

    String structureName = slug(input.getFileName().toString()
        .replaceFirst("(?i)\\.litematic$", ""));
    List<PieceInfo> pieces = new ArrayList<>();
    for (int ox = 0; ox < data.sizeX; ox += maxPieceSize) {
      for (int oy = 0; oy < data.sizeY; oy += maxPieceSize) {
        for (int oz = 0; oz < data.sizeZ; oz += maxPieceSize) {
          int sx = Math.min(maxPieceSize, data.sizeX - ox);
          int sy = Math.min(maxPieceSize, data.sizeY - oy);
          int sz = Math.min(maxPieceSize, data.sizeZ - oz);
          String pieceName = structureName + "_x" + (ox / maxPieceSize)
              + "_y" + (oy / maxPieceSize)
              + "_z" + (oz / maxPieceSize);
          CompoundTag template = makePiece(
              root.getInt("MinecraftDataVersion"), data, exteriorAir, blockEntities,
              entities, ox, oy, oz, sx, sy, sz);
          Path target = output.resolve(pieceName + ".nbt");
          NbtIo.writeCompressed(template, target);
          pieces.add(new PieceInfo(pieceName, ox, oy, oz, sx, sy, sz,
              template.getList("blocks", Tag.TAG_COMPOUND).size(),
              template.getList("entities", Tag.TAG_COMPOUND).size()));
        }
      }
    }

    Files.writeString(output.resolve(structureName + ".manifest.json"),
        manifestJson(input, root, data, pieces));
    System.out.println("Converted " + input.getFileName() + " into " + pieces.size()
        + " pieces at " + output);
  }

  private static RegionData decodeRegion(CompoundTag region) {
    CompoundTag sizeTag = region.getCompound("Size");
    CompoundTag positionTag = region.getCompound("Position");
    int signedX = sizeTag.getInt("x");
    int signedY = sizeTag.getInt("y");
    int signedZ = sizeTag.getInt("z");
    int sizeX = Math.abs(signedX);
    int sizeY = Math.abs(signedY);
    int sizeZ = Math.abs(signedZ);
    int volume = Math.multiplyExact(Math.multiplyExact(sizeX, sizeY), sizeZ);

    ListTag palette = region.getList("BlockStatePalette", Tag.TAG_COMPOUND);
    long[] packed = region.getLongArray("BlockStates");
    int bits = Math.max(2, 32 - Integer.numberOfLeadingZeros(palette.size() - 1));
    long mask = (1L << bits) - 1L;
    int[] states = new int[volume];
    for (int index = 0; index < volume; index++) {
      long bitIndex = (long) index * bits;
      int longIndex = (int) (bitIndex >>> 6);
      int bitOffset = (int) (bitIndex & 63L);
      long value = packed[longIndex] >>> bitOffset;
      if (bitOffset + bits > 64) {
        value |= packed[longIndex + 1] << (64 - bitOffset);
      }
      states[index] = (int) (value & mask);
    }

    int posX = positionTag.getInt("x");
    int posY = positionTag.getInt("y");
    int posZ = positionTag.getInt("z");
    int minX = signedX >= 0 ? posX : posX - sizeX + 1;
    int minY = signedY >= 0 ? posY : posY - sizeY + 1;
    int minZ = signedZ >= 0 ? posZ : posZ - sizeZ + 1;
    int airState = -1;
    for (int i = 0; i < palette.size(); i++) {
      if ("minecraft:air".equals(palette.getCompound(i).getString("Name"))) {
        airState = i;
        break;
      }
    }
    if (airState < 0) {
      throw new IllegalArgumentException("Litematic palette has no minecraft:air state");
    }
    return new RegionData(sizeX, sizeY, sizeZ, posX, posY, posZ,
        minX, minY, minZ, Integer.signum(signedX), Integer.signum(signedY),
        Integer.signum(signedZ), airState, states, palette);
  }

  private static boolean[] markExteriorAir(RegionData data) {
    boolean[] exterior = new boolean[data.states.length];
    ArrayDeque<Integer> queue = new ArrayDeque<>();
    for (int x = 0; x < data.sizeX; x++) {
      for (int y = 0; y < data.sizeY; y++) {
        seedExterior(data, exterior, queue, x, y, 0);
        seedExterior(data, exterior, queue, x, y, data.sizeZ - 1);
      }
    }
    for (int x = 0; x < data.sizeX; x++) {
      for (int z = 0; z < data.sizeZ; z++) {
        seedExterior(data, exterior, queue, x, 0, z);
        seedExterior(data, exterior, queue, x, data.sizeY - 1, z);
      }
    }
    for (int y = 0; y < data.sizeY; y++) {
      for (int z = 0; z < data.sizeZ; z++) {
        seedExterior(data, exterior, queue, 0, y, z);
        seedExterior(data, exterior, queue, data.sizeX - 1, y, z);
      }
    }

    int[] dx = {1, -1, 0, 0, 0, 0};
    int[] dy = {0, 0, 1, -1, 0, 0};
    int[] dz = {0, 0, 0, 0, 1, -1};
    while (!queue.isEmpty()) {
      int index = queue.removeFirst();
      int x = index % data.sizeX;
      int yz = index / data.sizeX;
      int z = yz % data.sizeZ;
      int y = yz / data.sizeZ;
      for (int i = 0; i < 6; i++) {
        int nx = x + dx[i];
        int ny = y + dy[i];
        int nz = z + dz[i];
        if (nx >= 0 && nx < data.sizeX && ny >= 0 && ny < data.sizeY
            && nz >= 0 && nz < data.sizeZ) {
          seedExterior(data, exterior, queue, nx, ny, nz);
        }
      }
    }
    return exterior;
  }

  private static void injectGameplayMarkers(String fileName, RegionData data,
      Map<Long, CompoundTag> blockEntities) {
    if (hasManualGameplayMarkers(data)) {
      normalizeManualGameplayMarkers(data);
      System.out.println("Using manually placed gameplay markers in " + fileName);
      return;
    }
    List<Candidate> candidates = new ArrayList<>();
    int nonAir = 0;
    for (int state : data.states) {
      if (state != data.airState) {
        nonAir++;
      }
    }
    for (int x = 1; x < data.sizeX - 1; x++) {
      for (int y = 1; y < data.sizeY - 2; y++) {
        for (int z = 1; z < data.sizeZ - 1; z++) {
          if (isInteriorFloorCandidate(data, x, y, z)) {
            candidates.add(new Candidate(x, y, z,
                mix(fileName.hashCode(), x, y, z)));
          }
        }
      }
    }
    candidates.sort(Comparator.comparingInt(Candidate::score));

    Map<String, Integer> markerStates = new HashMap<>();
    int spawnerTarget = Math.max(3, Math.min(24, 3 + nonAir / 30000));
    List<Candidate> selectedSpawners =
        chooseSpaced(candidates, spawnerTarget, 8, List.of());

    int lootTarget = Math.max(8, Math.min(200, nonAir / 2000));
    int lowerLevelCutoff = Math.max(4, data.sizeY / 3);
    List<Candidate> lowerLevelCandidates = candidates.stream()
        .filter(candidate -> candidate.y <= lowerLevelCutoff)
        .toList();
    int lowerLevelTarget = lowerLevelCandidates.isEmpty()
        ? 0 : Math.min(Math.max(2, lootTarget / 3), 40);
    List<Candidate> blockedForLoot = new ArrayList<>(selectedSpawners);
    List<Candidate> selectedLoot = new ArrayList<>(
        chooseSpaced(lowerLevelCandidates, lowerLevelTarget, 4, blockedForLoot));
    int lowerLevelSelected = selectedLoot.size();
    blockedForLoot.addAll(selectedLoot);
    selectedLoot.addAll(chooseSpaced(candidates,
        lootTarget - selectedLoot.size(), 4, blockedForLoot));
    for (Candidate candidate : selectedLoot) {
      String marker = lootMarker(fileName, nonAir, candidate.score);
      int state = markerStates.computeIfAbsent(marker,
          key -> addPaletteState(data.palette, "craftingdeadstructures:" + key));
      data.states[data.index(candidate.x, candidate.y + 1, candidate.z)] = state;
    }

    for (Candidate candidate : selectedSpawners) {
      String marker = spawnerMarker(nonAir, candidate.score);
      int spawnerState = markerStates.computeIfAbsent(marker,
          key -> addPaletteState(data.palette, "craftingdeadstructures:" + key));
      data.states[data.index(candidate.x, candidate.y, candidate.z)] = spawnerState;
    }

    int existingContainers = (int) blockEntities.values().stream()
        .filter(tag -> isLootContainer(tag.getString("id")))
        .count();
    int containersToAdd = existingContainers == 0
        ? 7 + Math.floorMod(fileName.hashCode(), 2)
        : Math.max(0, Math.min(4, 2 + nonAir / 50000) - existingContainers);
    List<Candidate> excluded = new ArrayList<>(selectedLoot);
    excluded.addAll(selectedSpawners);
    List<Candidate> selectedContainers =
        chooseSpaced(candidates, containersToAdd, 4, excluded);
    int barrelState = addPaletteState(data.palette, "minecraft:barrel");
    for (Candidate candidate : selectedContainers) {
      data.states[data.index(candidate.x, candidate.y, candidate.z)] = barrelState;
      CompoundTag barrel = new CompoundTag();
      barrel.putString("id", "minecraft:barrel");
      barrel.putString("LootTable",
          chooseLootTable(fileName, candidate.x, candidate.y, candidate.z));
      blockEntities.put(pack(candidate.x, candidate.y, candidate.z), barrel);
    }
    System.out.println("Injected " + selectedLoot.size() + " loot markers ("
        + lowerLevelSelected + " lower-level), "
        + selectedSpawners.size() + " spawner markers and "
        + selectedContainers.size() + " automatic containers into " + fileName);
  }

  private static boolean hasManualGameplayMarkers(RegionData data) {
    for (int state : data.states) {
      String name = data.palette.getCompound(state).getString("Name");
      if (name.startsWith("craftingdeadstructures:")
          && name.endsWith("_marker")) {
        return true;
      }
    }
    return false;
  }

  private static void normalizeManualGameplayMarkers(RegionData data) {
    List<Candidate> groundLootMarkers = new ArrayList<>();
    for (int x = 0; x < data.sizeX; x++) {
      for (int y = 0; y < data.sizeY - 1; y++) {
        for (int z = 0; z < data.sizeZ; z++) {
          int state = data.states[data.index(x, y, z)];
          String name = data.palette.getCompound(state).getString("Name");
          if (name.startsWith("craftingdeadstructures:")
              && name.endsWith("_loot_marker")
              && data.states[data.index(x, y + 1, z)] == data.airState) {
            groundLootMarkers.add(new Candidate(x, y, z, state));
          }
        }
      }
    }
    for (Candidate marker : groundLootMarkers) {
      data.states[data.index(marker.x, marker.y, marker.z)] = data.airState;
      data.states[data.index(marker.x, marker.y + 1, marker.z)] = marker.score;
    }
    System.out.println("Grounded " + groundLootMarkers.size()
        + " manually placed loot markers");
  }

  private static boolean isInteriorFloorCandidate(RegionData data, int x, int y, int z) {
    if (data.states[data.index(x, y, z)] != data.airState
        || data.states[data.index(x, y + 1, z)] != data.airState) {
      return false;
    }
    String floor = data.palette.getCompound(
        data.states[data.index(x, y - 1, z)]).getString("Name");
    if (floor.equals("minecraft:air") || floor.contains("water")
        || floor.endsWith("_leaves") || floor.endsWith("_fence")
        || floor.endsWith("_wall") || floor.endsWith("_door")
        || floor.endsWith("_trapdoor") || floor.endsWith("_stairs")
        || floor.endsWith("_slab")) {
      return false;
    }

    boolean roof = false;
    for (int dy = 2; dy <= 10 && y + dy < data.sizeY; dy++) {
      if (data.states[data.index(x, y + dy, z)] != data.airState) {
        roof = true;
        break;
      }
    }
    if (!roof) {
      return false;
    }

    int enclosedDirections = 0;
    int[][] directions = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
    for (int[] direction : directions) {
      for (int distance = 1; distance <= 12; distance++) {
        int nx = x + direction[0] * distance;
        int nz = z + direction[1] * distance;
        if (nx < 0 || nx >= data.sizeX || nz < 0 || nz >= data.sizeZ) {
          break;
        }
        if (data.states[data.index(nx, y, nz)] != data.airState
            || data.states[data.index(nx, y + 1, nz)] != data.airState) {
          enclosedDirections++;
          break;
        }
      }
    }
    return enclosedDirections >= 3;
  }

  private static void removeReferenceBase(String fileName, RegionData data) {
    int removed = 0;
    int footprint = data.sizeX * data.sizeZ;
    int layers = Math.min(3, data.sizeY);
    for (int y = 0; y < layers; y++) {
      Map<Integer, Integer> counts = new HashMap<>();
      for (int x = 0; x < data.sizeX; x++) {
        for (int z = 0; z < data.sizeZ; z++) {
          int state = data.states[data.index(x, y, z)];
          if (state != data.airState) {
            counts.merge(state, 1, Integer::sum);
          }
        }
      }
      int dominantState = -1;
      int dominantCount = 0;
      for (Map.Entry<Integer, Integer> entry : counts.entrySet()) {
        if (entry.getValue() > dominantCount) {
          dominantState = entry.getKey();
          dominantCount = entry.getValue();
        }
      }
      if (dominantState < 0) {
        continue;
      }
      String dominantName =
          data.palette.getCompound(dominantState).getString("Name");
      boolean skyQuartz = isSkyIsland(fileName)
          && y == 0 && dominantName.contains("quartz");
      boolean largeReferencePlane = dominantCount >= 128
          && dominantCount * 100 >= footprint * 45
          && isReferenceBaseBlock(dominantName);
      if (!skyQuartz && !largeReferencePlane) {
        continue;
      }
      boolean[] protectedColumns =
          protectedTerrainColumns(data, dominantState, y, 2);
      for (int x = 0; x < data.sizeX; x++) {
        for (int z = 0; z < data.sizeZ; z++) {
          int index = data.index(x, y, z);
          if (data.states[index] == dominantState
              && (!protectedColumns[x * data.sizeZ + z] || skyQuartz)) {
            data.states[index] = data.airState;
            removed++;
          }
        }
      }
    }
    if (isSkyIsland(fileName)) {
      for (int x = 0; x < data.sizeX; x++) {
        for (int z = 0; z < data.sizeZ; z++) {
          int index = data.index(x, 0, z);
          String name = data.palette.getCompound(data.states[index]).getString("Name");
          if (name.contains("quartz")) {
            data.states[index] = data.airState;
            removed++;
          }
        }
      }
    }
    System.out.println("Removed " + removed
        + " reference-base blocks from " + fileName);
  }

  private static boolean isReferenceBaseBlock(String name) {
    return isNaturalTerrainBlock(name)
        || name.contains("quartz")
        || name.endsWith("_concrete")
        || name.endsWith("_wool")
        || name.endsWith("_terracotta")
        || name.equals("minecraft:smooth_stone")
        || name.equals("minecraft:glass");
  }

  private static void stripBoundaryConnectedTerrain(String fileName, RegionData data) {
    if (isSkyIsland(fileName)) {
      return;
    }
    boolean[] connected = new boolean[data.states.length];
    ArrayDeque<Integer> queue = new ArrayDeque<>();
    for (int x = 0; x < data.sizeX; x++) {
      for (int y = 0; y < data.sizeY; y++) {
        seedTerrain(data, connected, queue, x, y, 0);
        seedTerrain(data, connected, queue, x, y, data.sizeZ - 1);
      }
    }
    for (int z = 0; z < data.sizeZ; z++) {
      for (int y = 0; y < data.sizeY; y++) {
        seedTerrain(data, connected, queue, 0, y, z);
        seedTerrain(data, connected, queue, data.sizeX - 1, y, z);
      }
    }
    for (int x = 0; x < data.sizeX; x++) {
      for (int z = 0; z < data.sizeZ; z++) {
        seedTerrain(data, connected, queue, x, 0, z);
      }
    }

    int[] dx = {1, -1, 0, 0, 0, 0};
    int[] dy = {0, 0, 1, -1, 0, 0};
    int[] dz = {0, 0, 0, 0, 1, -1};
    while (!queue.isEmpty()) {
      int index = queue.removeFirst();
      int x = index % data.sizeX;
      int yz = index / data.sizeX;
      int z = yz % data.sizeZ;
      int y = yz / data.sizeZ;
      for (int i = 0; i < 6; i++) {
        int nx = x + dx[i];
        int ny = y + dy[i];
        int nz = z + dz[i];
        if (nx >= 0 && nx < data.sizeX && ny >= 0 && ny < data.sizeY
            && nz >= 0 && nz < data.sizeZ) {
          seedTerrain(data, connected, queue, nx, ny, nz);
        }
      }
    }

    boolean[] protectedColumns = protectedTerrainColumns(data, -1, -1, 1);
    int removed = 0;
    for (int index = 0; index < connected.length; index++) {
      int x = index % data.sizeX;
      int yz = index / data.sizeX;
      int z = yz % data.sizeZ;
      if (connected[index] && !protectedColumns[x * data.sizeZ + z]) {
        data.states[index] = data.airState;
        removed++;
      }
    }
    System.out.println("Removed " + removed
        + " boundary-connected terrain blocks from " + fileName);
  }

  private static void seedTerrain(RegionData data, boolean[] connected,
      ArrayDeque<Integer> queue, int x, int y, int z) {
    int index = data.index(x, y, z);
    if (!connected[index] && isNaturalTerrainBlock(
        data.palette.getCompound(data.states[index]).getString("Name"))) {
      connected[index] = true;
      queue.add(index);
    }
  }

  private static boolean[] protectedTerrainColumns(RegionData data,
      int ignoredState, int ignoredLayer, int radius) {
    boolean[] anchors = new boolean[data.sizeX * data.sizeZ];
    for (int x = 0; x < data.sizeX; x++) {
      for (int y = 0; y < data.sizeY; y++) {
        for (int z = 0; z < data.sizeZ; z++) {
          int state = data.states[data.index(x, y, z)];
          if (state == data.airState
              || (state == ignoredState && y <= ignoredLayer)
              || !isStructureAnchorBlock(
                  data.palette.getCompound(state).getString("Name"))) {
            continue;
          }
          anchors[x * data.sizeZ + z] = true;
        }
      }
    }
    boolean[] protectedColumns = anchors.clone();
    for (int x = 0; x < data.sizeX; x++) {
      for (int z = 0; z < data.sizeZ; z++) {
        if (!anchors[x * data.sizeZ + z]) {
          continue;
        }
        for (int dx = -radius; dx <= radius; dx++) {
          for (int dz = -radius; dz <= radius; dz++) {
            int nx = x + dx;
            int nz = z + dz;
            if (nx >= 0 && nx < data.sizeX && nz >= 0 && nz < data.sizeZ
                && dx * dx + dz * dz <= radius * radius) {
              protectedColumns[nx * data.sizeZ + nz] = true;
            }
          }
        }
      }
    }
    return protectedColumns;
  }

  private static boolean isStructureAnchorBlock(String name) {
    return !isNaturalTerrainBlock(name)
        && !name.equals("minecraft:air")
        && !name.contains("water")
        && !name.contains("lava")
        && !name.endsWith("_leaves")
        && !name.endsWith("_sapling")
        && !name.endsWith("_flower")
        && !name.endsWith("_tulip")
        && !name.endsWith("_mushroom")
        && !name.endsWith("_coral")
        && !name.endsWith("_crop")
        && !name.contains("grass")
        && !name.contains("fern")
        && !name.contains("vine")
        && !name.contains("bush");
  }

  private static void blendTerrainEdges(String fileName, RegionData data) {
    if (isSkyIsland(fileName)) {
      return;
    }
    int blendWidth = 16;
    int removed = 0;
    for (int x = 0; x < data.sizeX; x++) {
      for (int y = 0; y < data.sizeY; y++) {
        for (int z = 0; z < data.sizeZ; z++) {
          int edgeDistance = Math.min(
              Math.min(x, data.sizeX - 1 - x),
              Math.min(z, data.sizeZ - 1 - z));
          if (edgeDistance >= blendWidth) {
            continue;
          }
          int index = data.index(x, y, z);
          String name = data.palette.getCompound(data.states[index]).getString("Name");
          if (!isNaturalTerrainBlock(name)) {
            continue;
          }
          int removeChance = (blendWidth - edgeDistance) * 100 / blendWidth;
          if (Math.floorMod(mix(fileName.hashCode(), x, y, z), 100) < removeChance) {
            data.states[index] = data.airState;
            removed++;
          }
        }
      }
    }
    System.out.println("Feathered " + removed
        + " terrain-edge blocks in " + fileName);
  }

  private static boolean isNaturalTerrainBlock(String name) {
    return name.equals("minecraft:grass_block")
        || name.equals("minecraft:dirt")
        || name.equals("minecraft:coarse_dirt")
        || name.equals("minecraft:rooted_dirt")
        || name.equals("minecraft:podzol")
        || name.equals("minecraft:mycelium")
        || name.equals("minecraft:stone")
        || name.equals("minecraft:deepslate")
        || name.equals("minecraft:sand")
        || name.equals("minecraft:red_sand")
        || name.equals("minecraft:gravel")
        || name.equals("minecraft:clay")
        || name.equals("minecraft:mud")
        || name.equals("minecraft:moss_block")
        || name.equals("minecraft:snow_block")
        || name.endsWith("_terracotta");
  }

  private static List<Candidate> chooseSpaced(List<Candidate> candidates,
      int target, int minimumDistance, List<Candidate> excluded) {
    List<Candidate> selected = new ArrayList<>();
    if (target <= 0) {
      return selected;
    }
    int minimumSquared = minimumDistance * minimumDistance;
    for (Candidate candidate : candidates) {
      if (farEnough(candidate, selected, minimumSquared)
          && farEnough(candidate, excluded, minimumSquared)) {
        selected.add(candidate);
        if (selected.size() >= target) {
          break;
        }
      }
    }
    return selected;
  }

  private static boolean farEnough(
      Candidate candidate, List<Candidate> others, int minimumSquared) {
    for (Candidate other : others) {
      int dx = candidate.x - other.x;
      int dy = candidate.y - other.y;
      int dz = candidate.z - other.z;
      if (dx * dx + dy * dy + dz * dz < minimumSquared) {
        return false;
      }
    }
    return true;
  }

  private static String lootMarker(String fileName, int nonAir, int score) {
    int roll = Math.floorMod(score, 100);
    if (fileName.contains("体育场")) {
      return roll < 25 ? "civilian_loot_marker"
          : roll < 40 ? "medical_loot_marker"
          : roll < 55 ? "rare_civilian_loot_marker"
          : roll < 75 ? "police_loot_marker"
          : "military_loot_marker";
    }
    if (fileName.contains("小城市")) {
      return roll < 35 ? "civilian_loot_marker"
          : roll < 50 ? "medical_loot_marker"
          : roll < 65 ? "rare_civilian_loot_marker"
          : roll < 90 ? "police_loot_marker"
          : "military_loot_marker";
    }
    if (nonAir >= 150000) {
      return roll < 40 ? "civilian_loot_marker"
          : roll < 60 ? "medical_loot_marker"
          : roll < 75 ? "rare_civilian_loot_marker"
          : roll < 90 ? "police_loot_marker"
          : "military_loot_marker";
    }
    if (roll < 55) {
      return "civilian_loot_marker";
    }
    if (roll < 75) {
      return "medical_loot_marker";
    }
    return roll < 90 ? "rare_civilian_loot_marker"
        : roll < 97 ? "police_loot_marker"
        : "military_loot_marker";
  }

  private static String spawnerMarker(int nonAir, int score) {
    int roll = Math.floorMod(score, 100);
    if (nonAir >= 500000) {
      return roll < 40 ? "elite_zombie_spawner_marker"
          : roll < 80 ? "dangerous_zombie_spawner_marker"
          : "zombie_spawner_marker";
    }
    if (nonAir >= 150000) {
      return roll < 20 ? "elite_zombie_spawner_marker"
          : roll < 65 ? "dangerous_zombie_spawner_marker"
          : "zombie_spawner_marker";
    }
    if (nonAir >= 50000 && roll < 35) {
      return "dangerous_zombie_spawner_marker";
    }
    return "zombie_spawner_marker";
  }

  private static int addPaletteState(ListTag palette, String name) {
    for (int i = 0; i < palette.size(); i++) {
      if (name.equals(palette.getCompound(i).getString("Name"))) {
        return i;
      }
    }
    CompoundTag state = new CompoundTag();
    state.putString("Name", name);
    palette.add(state);
    return palette.size() - 1;
  }

  private static int mix(int seed, int x, int y, int z) {
    int value = seed;
    value = Integer.rotateLeft(value ^ x * 0x45D9F3B, 13);
    value = Integer.rotateLeft(value ^ y * 0x119DE1F3, 11);
    return Integer.rotateLeft(value ^ z * 0x3449F, 7);
  }

  private static void seedExterior(RegionData data, boolean[] exterior,
      ArrayDeque<Integer> queue, int x, int y, int z) {
    int index = data.index(x, y, z);
    if (!exterior[index] && data.states[index] == data.airState) {
      exterior[index] = true;
      queue.add(index);
    }
  }

  private static Map<Long, CompoundTag> readBlockEntities(
      String fileName, CompoundTag region, RegionData data) {
    Map<Long, CompoundTag> result = new HashMap<>();
    ListTag tags = region.getList("TileEntities", Tag.TAG_COMPOUND);
    List<Candidate> lootContainers = new ArrayList<>();
    List<ThemedMarker> themedMarkers = manualThemeMarkers(data);
    boolean manualGameplay = hasManualGameplayMarkers(data);
    int preservedFilledContainers = 0;
    for (int i = 0; i < tags.size(); i++) {
      CompoundTag blockEntity = tags.getCompound(i);
      boolean preserve = isPreservedSpecialContainer(fileName, blockEntity)
          || hasSavedItems(blockEntity);
      if (preserve && isLootContainer(blockEntity.getString("id"))) {
        preservedFilledContainers++;
      }
      if (!preserve && isLootContainer(blockEntity.getString("id"))) {
        lootContainers.add(new Candidate(
            blockEntity.getInt("x"), blockEntity.getInt("y"), blockEntity.getInt("z"),
            mix(fileName.hashCode(), blockEntity.getInt("x"),
                blockEntity.getInt("y"), blockEntity.getInt("z"))));
      }
    }
    lootContainers.sort(Comparator.comparingInt(Candidate::score));
    int lootPercent = manualGameplay
        ? (fileName.contains("城市") ? 25
            : lootContainers.size() >= 20 ? 65 : 75)
        : isLargeStructure(fileName) ? 95 : 85;
    int enabledContainerTarget = lootContainers.isEmpty()
        ? 0 : Math.max(1,
            (lootContainers.size() * lootPercent + 99) / 100);
    Set<Long> enabledContainers = new HashSet<>();
    for (int i = 0; i < enabledContainerTarget; i++) {
      Candidate candidate = lootContainers.get(i);
      enabledContainers.add(pack(candidate.x, candidate.y, candidate.z));
    }

    for (int i = 0; i < tags.size(); i++) {
      CompoundTag blockEntity = tags.getCompound(i).copy();
      int x = blockEntity.getInt("x") - data.minX;
      int y = blockEntity.getInt("y") - data.minY;
      int z = blockEntity.getInt("z") - data.minZ;
      boolean preserve = isPreservedSpecialContainer(fileName, blockEntity)
          || hasSavedItems(blockEntity);
      if (!preserve) {
        blockEntity.remove("Items");
        blockEntity.remove("LootTable");
        blockEntity.remove("LootTableSeed");
        if (isLootContainer(blockEntity.getString("id"))
            && enabledContainers.contains(pack(
                blockEntity.getInt("x"), blockEntity.getInt("y"),
                blockEntity.getInt("z")))) {
          blockEntity.putString("LootTable",
              themedMarkers.isEmpty()
                  ? chooseLootTable(fileName, x, y, z)
                  : chooseThemedLootTable(fileName, x, y, z, themedMarkers));
        }
      }
      blockEntity.remove("x");
      blockEntity.remove("y");
      blockEntity.remove("z");
      result.put(pack(x, y, z), blockEntity);
    }
    System.out.println("Enabled loot in " + enabledContainerTarget + " of "
        + lootContainers.size() + " ordinary containers at " + lootPercent
        + "% and preserved " + preservedFilledContainers
        + " filled containers in " + fileName);
    return result;
  }

  private static boolean hasSavedItems(CompoundTag blockEntity) {
    return blockEntity.contains("Items", Tag.TAG_LIST)
        && !blockEntity.getList("Items", Tag.TAG_COMPOUND).isEmpty();
  }

  private static List<ThemedMarker> manualThemeMarkers(RegionData data) {
    List<ThemedMarker> markers = new ArrayList<>();
    for (int x = 0; x < data.sizeX; x++) {
      for (int y = 0; y < data.sizeY; y++) {
        for (int z = 0; z < data.sizeZ; z++) {
          String name = data.palette.getCompound(
              data.states[data.index(x, y, z)]).getString("Name");
          if (!name.startsWith("craftingdeadstructures:")) {
            continue;
          }
          String path = name.substring(name.indexOf(':') + 1);
          String tier = null;
          if (path.endsWith("_loot_marker")) {
            tier = path.substring(0, path.length() - "_loot_marker".length());
          } else if (path.endsWith("_container_marker")) {
            tier = path.substring(0, path.length() - "_container_marker".length());
          }
          if (tier != null) {
            markers.add(new ThemedMarker(x, y, z, tier));
          }
        }
      }
    }
    return markers;
  }

  private static String chooseThemedLootTable(String fileName,
      int x, int y, int z, List<ThemedMarker> markers) {
    ThemedMarker nearest = null;
    long nearestDistance = Long.MAX_VALUE;
    for (ThemedMarker marker : markers) {
      long dx = x - marker.x;
      long dy = (long) (y - marker.y) * 2L;
      long dz = z - marker.z;
      long distance = dx * dx + dy * dy + dz * dz;
      if (distance < nearestDistance) {
        nearestDistance = distance;
        nearest = marker;
      }
    }
    if (nearest == null) {
      return chooseLootTable(fileName, x, y, z);
    }
    int roll = Math.floorMod(
        fileName.hashCode() * 31 + x * 7349 + y * 9151 + z * 1237, 100);
    String path = switch (nearest.tier) {
      case "medical" -> "medical";
      case "police" -> "police";
      case "military" -> "military";
      case "weapons" -> "weapons";
      case "rare_civilian" -> roll < 45 ? "weapons"
          : roll < 75 ? "ammo" : "civilian_general";
      default -> roll < 35 ? "food_water" : "civilian_general";
    };
    return "craftingdeadstructures:chests/" + path;
  }

  private static boolean isPreservedSpecialContainer(
      String fileName, CompoundTag blockEntity) {
    return fileName.equals("空中小岛.litematic")
        && blockEntity.getInt("x") == 73
        && blockEntity.getInt("y") == 14
        && blockEntity.getInt("z") == 98;
  }

  private static boolean isLootContainer(String id) {
    return id.equals("minecraft:chest")
        || id.equals("minecraft:trapped_chest")
        || id.equals("minecraft:barrel")
        || id.equals("minecraft:shulker_box");
  }

  private static String chooseLootTable(String fileName, int x, int y, int z) {
    int roll = Math.floorMod(fileName.hashCode() * 31 + x * 7349 + y * 9151 + z * 1237, 100);
    if (fileName.contains("体育场") || fileName.contains("小城市")) {
      String path = roll < 30 ? "military"
          : roll < 55 ? "police"
          : roll < 75 ? "weapons"
          : roll < 87 ? "ammo"
          : roll < 95 ? "medical"
          : "food_water";
      return "craftingdeadstructures:chests/" + path;
    }
    if (fileName.contains("空中小岛") || fileName.contains("海边")) {
      String path = roll < 18 ? "military"
          : roll < 35 ? "police"
          : roll < 52 ? "weapons"
          : roll < 70 ? "ammo"
          : roll < 84 ? "medical"
          : roll < 93 ? "civilian_general"
          : "food_water";
      return "craftingdeadstructures:chests/" + path;
    }
    if (fileName.contains("小馆子") || fileName.contains("度假")) {
      roll = Math.floorMod(roll, 60);
    }
    String path = roll < 28 ? "food_water"
        : roll < 58 ? "civilian_general"
        : roll < 70 ? "medical"
        : roll < 85 ? "ammo"
        : "weapons";
    return "craftingdeadstructures:chests/" + path;
  }

  private static boolean isLargeStructure(String fileName) {
    return fileName.contains("体育场")
        || fileName.contains("小城市")
        || fileName.contains("空中小岛")
        || fileName.contains("海边");
  }

  private static boolean isSkyIsland(String fileName) {
    return fileName.equals("空中小岛.litematic")
        || fileName.equals("空岛.litematic");
  }

  private static List<EntityData> readEntities(CompoundTag region, RegionData data) {
    List<EntityData> result = new ArrayList<>();
    ListTag tags = region.getList("Entities", Tag.TAG_COMPOUND);
    for (int i = 0; i < tags.size(); i++) {
      CompoundTag entity = tags.getCompound(i).copy();
      String id = entity.getString("id");
      if (!PRESERVED_ENTITY_TYPES.contains(id)) {
        continue;
      }
      ListTag pos = entity.getList("Pos", Tag.TAG_DOUBLE);
      if (pos.size() != 3) {
        continue;
      }
      double x = data.posX + pos.getDouble(0) - data.minX;
      double y = data.posY + pos.getDouble(1) - data.minY;
      double z = data.posZ + pos.getDouble(2) - data.minZ;
      entity.remove("UUID");
      result.add(new EntityData(x, y, z, entity));
    }
    return result;
  }

  private static CompoundTag makePiece(int dataVersion, RegionData data,
      boolean[] exteriorAir, Map<Long, CompoundTag> blockEntities,
      List<EntityData> entities, int ox, int oy, int oz, int sx, int sy, int sz) {
    CompoundTag template = new CompoundTag();
    template.putInt("DataVersion", dataVersion);
    template.put("size", intList(sx, sy, sz));
    template.put("palette", data.palette.copy());

    ListTag blocks = new ListTag();
    for (int x = ox; x < ox + sx; x++) {
      for (int y = oy; y < oy + sy; y++) {
        for (int z = oz; z < oz + sz; z++) {
          int index = data.indexFromWorldOrder(x, y, z);
          int state = data.states[index];
          if (state == data.airState && exteriorAir[index]) {
            continue;
          }
          CompoundTag block = new CompoundTag();
          block.put("pos", intList(x - ox, y - oy, z - oz));
          block.putInt("state", state);
          CompoundTag blockEntity = blockEntities.get(pack(x, y, z));
          if (blockEntity != null) {
            block.put("nbt", blockEntity.copy());
          }
          blocks.add(block);
        }
      }
    }
    template.put("blocks", blocks);

    ListTag entityList = new ListTag();
    for (EntityData entity : entities) {
      if (entity.x >= ox && entity.x < ox + sx
          && entity.y >= oy && entity.y < oy + sy
          && entity.z >= oz && entity.z < oz + sz) {
        double localX = entity.x - ox;
        double localY = entity.y - oy;
        double localZ = entity.z - oz;
        CompoundTag entityNbt = entity.nbt.copy();
        entityNbt.put("Pos", doubleList(localX, localY, localZ));
        if (entityNbt.contains("TileX", Tag.TAG_INT)) {
          entityNbt.putInt("TileX", (int) Math.floor(localX));
          entityNbt.putInt("TileY", (int) Math.floor(localY));
          entityNbt.putInt("TileZ", (int) Math.floor(localZ));
        }
        CompoundTag wrapper = new CompoundTag();
        wrapper.put("pos", doubleList(localX, localY, localZ));
        wrapper.put("blockPos", intList(
            (int) Math.floor(localX),
            (int) Math.floor(localY),
            (int) Math.floor(localZ)));
        wrapper.put("nbt", entityNbt);
        entityList.add(wrapper);
      }
    }
    template.put("entities", entityList);
    return template;
  }

  private static ListTag intList(int... values) {
    ListTag list = new ListTag();
    for (int value : values) {
      list.add(IntTag.valueOf(value));
    }
    return list;
  }

  private static ListTag doubleList(double... values) {
    ListTag list = new ListTag();
    for (double value : values) {
      list.add(DoubleTag.valueOf(value));
    }
    return list;
  }

  private static long pack(int x, int y, int z) {
    return ((long) x & 0x1FFFFFL) << 42
        | ((long) y & 0x1FFFFFL) << 21
        | ((long) z & 0x1FFFFFL);
  }

  private static String slug(String value) {
    return switch (value) {
      case "半山腰别墅" -> "half_mountain_villa";
      case "度假楼" -> "resort_building";
      case "海边" -> "seaside";
      case "空中小岛" -> "sky_island";
      case "空岛" -> "sky_island";
      case "癞蛤蟆小屋" -> "toad_house";
      case "癞蛤蟆" -> "toad_house";
      case "沙漠" -> "desert_site";
      case "树屋" -> "tree_house";
      case "私人别墅" -> "private_villa";
      case "体育场" -> "stadium";
      case "小城市" -> "small_city";
      case "城市" -> "small_city";
      case "科学楼" -> "science_building";
      case "小馆子" -> "small_restaurant";
      default -> value.toLowerCase(Locale.ROOT)
          .replaceAll("[^a-z0-9._-]+", "_")
          .replaceAll("^_+|_+$", "");
    };
  }

  private static String manifestJson(Path input, CompoundTag root,
      RegionData data, List<PieceInfo> pieces) {
    StringBuilder json = new StringBuilder();
    json.append("{\n");
    json.append("  \"source\": \"").append(escape(input.getFileName().toString())).append("\",\n");
    json.append("  \"data_version\": ").append(root.getInt("MinecraftDataVersion")).append(",\n");
    json.append("  \"size\": [").append(data.sizeX).append(", ")
        .append(data.sizeY).append(", ").append(data.sizeZ).append("],\n");
    json.append("  \"piece_size\": ").append(maxPieceSize).append(",\n");
    json.append("  \"pieces\": [\n");
    for (int i = 0; i < pieces.size(); i++) {
      PieceInfo p = pieces.get(i);
      json.append("    {\"name\":\"").append(p.name)
          .append("\",\"offset\":[").append(p.x).append(",").append(p.y).append(",")
          .append(p.z).append("],\"size\":[").append(p.sx).append(",").append(p.sy)
          .append(",").append(p.sz).append("],\"blocks\":").append(p.blocks)
          .append(",\"entities\":").append(p.entities).append("}");
      json.append(i + 1 == pieces.size() ? "\n" : ",\n");
    }
    json.append("  ]\n}\n");
    return json.toString();
  }

  private static String escape(String value) {
    return value.replace("\\", "\\\\").replace("\"", "\\\"");
  }

  private record EntityData(double x, double y, double z, CompoundTag nbt) {}

  private record PieceInfo(String name, int x, int y, int z,
      int sx, int sy, int sz, int blocks, int entities) {}

  private record Candidate(int x, int y, int z, int score) {}

  private record ThemedMarker(int x, int y, int z, String tier) {}

  private record RegionData(int sizeX, int sizeY, int sizeZ,
      int posX, int posY, int posZ, int minX, int minY, int minZ,
      int signX, int signY, int signZ, int airState, int[] states, ListTag palette) {

    int index(int containerX, int containerY, int containerZ) {
      return (containerY * this.sizeZ + containerZ) * this.sizeX + containerX;
    }

    int indexFromWorldOrder(int x, int y, int z) {
      // Litematica stores the region volume in canonical min-to-max order. The signs on Size only
      // describe which selection corner was used as Position; they must not mirror the palette.
      return this.index(x, y, z);
    }
  }

  private LitematicConverter() {}
}
