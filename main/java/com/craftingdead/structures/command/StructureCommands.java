package com.craftingdead.structures.command;

import com.craftingdead.structures.world.level.levelgen.StructureRandomizationProcessor;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

public final class StructureCommands {

  @SubscribeEvent
  public void registerCommands(RegisterCommandsEvent event) {
    event.getDispatcher().register(Commands.literal("craftingdead")
        .requires(source -> source.hasPermission(2))
        .then(Commands.literal("structures")
            .executes(context -> status(context.getSource()))
            .then(Commands.literal("status")
                .executes(context -> status(context.getSource())))
            .then(Commands.literal("list")
                .executes(context -> list(context.getSource())))
            .then(Commands.literal("locate")
                .then(Commands.argument("name", StringArgumentType.word())
                    .suggests(StructureCommands::suggestStructures)
                    .executes(context -> locate(context.getSource(),
                        StringArgumentType.getString(context, "name")))))
            .then(Commands.literal("place")
                .then(Commands.argument("name", StringArgumentType.word())
                    .suggests(StructureCommands::suggestStructures)
                    .executes(context -> place(
                        context.getSource(),
                        StringArgumentType.getString(context, "name"),
                        BlockPos.containing(context.getSource().getPosition())))
                    .then(Commands.argument("pos", BlockPosArgument.blockPos())
                        .executes(context -> place(
                            context.getSource(),
                            StringArgumentType.getString(context, "name"),
                            BlockPosArgument.getBlockPos(context, "pos"))))))));
  }

  private static int status(CommandSourceStack source) {
    source.sendSuccess(() -> Component.translatable(
        "command.craftingdeadstructures.status"), false);
    return 1;
  }

  private static int locate(CommandSourceStack source, String name) {
    ResourceLocation id = ResourceLocation.tryParse(
        name.contains(":") ? name : "craftingdeadstructures:" + name);
    if (id == null) {
      source.sendFailure(Component.translatable(
          "command.craftingdeadstructures.locate.invalid", name));
      return 0;
    }
    source.getServer().getCommands().performPrefixedCommand(
        source, "locate structure " + id);
    return 1;
  }

  private static int list(CommandSourceStack source) {
    Set<String> names = structureNames(source);
    source.sendSuccess(() -> Component.translatable(
        "command.craftingdeadstructures.list", String.join(", ", names)), false);
    return names.size();
  }

  private static CompletableFuture<Suggestions> suggestStructures(
      CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
    return SharedSuggestionProvider.suggest(
        structureNames(context.getSource()), builder);
  }

  private static Set<String> structureNames(CommandSourceStack source) {
    Set<String> names = new TreeSet<>();
    source.getServer().registryAccess().registryOrThrow(Registries.STRUCTURE)
        .keySet().stream()
        .filter(id -> id.getNamespace().equals("craftingdeadstructures"))
        .map(ResourceLocation::getPath)
        .forEach(names::add);
    return names;
  }

  private static int place(CommandSourceStack source, String name, BlockPos origin) {
    ResourceLocation id = parseStructureId(name);
    if (id == null) {
      source.sendFailure(Component.translatable(
          "command.craftingdeadstructures.locate.invalid", name));
      return 0;
    }

    ServerLevel level = source.getLevel();
    StructureTemplate template = level.getStructureManager().get(id).orElse(null);
    if (template == null) {
      boolean configuredStructure = source.getServer().registryAccess()
          .registryOrThrow(Registries.STRUCTURE).containsKey(id);
      if (configuredStructure) {
        source.getServer().getCommands().performPrefixedCommand(
            source, "place structure " + id + " "
                + origin.getX() + " " + origin.getY() + " " + origin.getZ());
        return 1;
      }
      source.sendFailure(Component.translatable(
          "command.craftingdeadstructures.place.missing", id.toString()));
      return 0;
    }

    StructurePlaceSettings settings = new StructurePlaceSettings()
        .setMirror(Mirror.NONE)
        .setRotation(Rotation.NONE)
        .setIgnoreEntities(false)
        .setFinalizeEntities(true)
        .addProcessor(StructureRandomizationProcessor.INSTANCE);
    boolean placed = template.placeInWorld(
        level, origin, origin, settings, level.getRandom(), Block.UPDATE_ALL);
    if (!placed) {
      source.sendFailure(Component.translatable(
          "command.craftingdeadstructures.place.failed", id.toString()));
      return 0;
    }

    source.sendSuccess(() -> Component.translatable(
        "command.craftingdeadstructures.place.success",
        id.toString(), origin.getX(), origin.getY(), origin.getZ()), true);
    return 1;
  }

  private static ResourceLocation parseStructureId(String name) {
    return ResourceLocation.tryParse(
        name.contains(":") ? name : "craftingdeadstructures:" + name);
  }
}
