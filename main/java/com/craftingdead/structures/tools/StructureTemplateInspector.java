package com.craftingdead.structures.tools;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;

public final class StructureTemplateInspector {

  private StructureTemplateInspector() {}

  public static void main(String[] args) throws Exception {
    if (args.length == 0) {
      throw new IllegalArgumentException("Usage: inspectStructure <template.nbt>...");
    }
    for (String arg : args) {
      inspect(Path.of(arg));
    }
  }

  private static void inspect(Path path) throws Exception {
    System.out.println("Template: " + path);
    CompoundTag template = NbtIo.readCompressed(
        path, NbtAccounter.unlimitedHeap());
    ListTag palette = template.getList("palette", Tag.TAG_COMPOUND);
    ListTag blocks = template.getList("blocks", Tag.TAG_COMPOUND);
    Map<String, Integer> counts = new LinkedHashMap<>();
    int lootContainers = 0;
    int emptyContainers = 0;
    int preservedContainers = 0;
    int quartzBlocks = 0;
    int quartzMinY = Integer.MAX_VALUE;
    int quartzMaxY = Integer.MIN_VALUE;

    for (int index = 0; index < blocks.size(); index++) {
      CompoundTag block = blocks.getCompound(index);
      String name = palette.getCompound(block.getInt("state")).getString("Name");
      CompoundTag nbt = block.contains("nbt", Tag.TAG_COMPOUND)
          ? block.getCompound("nbt") : null;
      ListTag pos = block.getList("pos", Tag.TAG_INT);
      if (name.contains("quartz")) {
        quartzBlocks++;
        quartzMinY = Math.min(quartzMinY, pos.getInt(1));
        quartzMaxY = Math.max(quartzMaxY, pos.getInt(1));
      }
      boolean interesting = name.endsWith("_marker")
          || name.contains("chest")
          || name.contains("barrel")
          || name.contains("shulker_box");
      if (!interesting) {
        continue;
      }
      counts.merge(name, 1, Integer::sum);
      String lootTable = nbt != null && nbt.contains("LootTable", Tag.TAG_STRING)
          ? " loot=" + nbt.getString("LootTable") : "";
      String items = "";
      if (nbt != null && nbt.contains("Items", Tag.TAG_LIST)) {
        ListTag itemList = nbt.getList("Items", Tag.TAG_COMPOUND);
        items = " preserved_items=" + itemList.size();
        if (!itemList.isEmpty()) {
          items += " first=" + itemList.getCompound(0).getString("id");
        }
      }
      boolean container = name.equals("minecraft:chest")
          || name.equals("minecraft:trapped_chest")
          || name.equals("minecraft:barrel")
          || name.endsWith("_shulker_box");
      if (container) {
        if (!lootTable.isEmpty()) {
          lootContainers++;
        } else if (!items.isEmpty()) {
          preservedContainers++;
        } else {
          emptyContainers++;
        }
      }
      System.out.printf("%s @ %d %d %d%s%s%n",
          name, pos.getInt(0), pos.getInt(1), pos.getInt(2), lootTable, items);
    }
    System.out.println("Summary: " + counts);
    System.out.println("ContainerSummary: loot=" + lootContainers
        + " empty=" + emptyContainers + " preserved=" + preservedContainers);
    System.out.println("QuartzSummary: count=" + quartzBlocks
        + (quartzBlocks == 0 ? "" : " y=" + quartzMinY + ".." + quartzMaxY));
  }
}
