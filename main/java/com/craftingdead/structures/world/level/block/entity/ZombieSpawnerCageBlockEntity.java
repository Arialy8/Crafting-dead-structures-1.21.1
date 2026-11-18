package com.craftingdead.structures.world.level.block.entity;

import com.craftingdead.structures.config.StructureServerConfig;
import com.craftingdead.structures.registry.StructureBlockEntityTypes;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

public final class ZombieSpawnerCageBlockEntity extends BlockEntity {

  private static final List<String> BASIC_ZOMBIES = List.of(
      "weak_zombie", "weak_zombie", "weak_zombie", "weak_zombie",
      "fast_zombie", "fast_zombie",
      "police_zombie", "doctor_zombie",
      "firefighter_zombie", "miner_zombie");
  private static final List<String> DANGEROUS_ZOMBIES = List.of(
      "fast_zombie", "fast_zombie",
      "police_zombie", "police_zombie",
      "doctor_zombie",
      "firefighter_zombie", "miner_zombie",
      "soldier_zombie", "scout_zombie",
      "swat_zombie");
  private static final List<String> ELITE_ZOMBIES = List.of(
      "police_zombie", "firefighter_zombie",
      "soldier_zombie", "soldier_zombie",
      "scout_zombie", "scout_zombie",
      "swat_zombie", "swat_zombie");

  private int spawnDelay = 20 * 5;
  private int spawnerTier;

  public ZombieSpawnerCageBlockEntity(BlockPos pos, BlockState state) {
    super(StructureBlockEntityTypes.ZOMBIE_SPAWNER_CAGE.get(), pos, state);
  }

  public static void serverTick(Level level, BlockPos pos, BlockState state,
      ZombieSpawnerCageBlockEntity cage) {
    int activationRange = Math.max(24,
        StructureServerConfig.SPAWNER_ACTIVATION_RANGE.get());
    if (!(level instanceof ServerLevel serverLevel)
        || level.getDifficulty() == Difficulty.PEACEFUL
        || level.getNearestPlayer(pos.getX() + 0.5D, pos.getY() + 0.5D,
            pos.getZ() + 0.5D, activationRange,
            false) == null) {
      return;
    }

    if (cage.spawnDelay-- > 0) {
      return;
    }

    int nearby = serverLevel.getEntitiesOfClass(Zombie.class,
        new AABB(pos).inflate(activationRange)).size();
    int maxNearby = StructureServerConfig.SPAWNER_MAX_NEARBY.get();
    if (nearby >= maxNearby) {
      cage.resetDelay(serverLevel);
      return;
    }

    int minimum = StructureServerConfig.SPAWNER_MIN_COUNT.get();
    int maximum = Math.max(minimum, StructureServerConfig.SPAWNER_MAX_COUNT.get());
    int targetCount = minimum + serverLevel.random.nextInt(maximum - minimum + 1)
        + cage.spawnerTier;
    int allowed = Math.min(targetCount, maxNearby - nearby);
    int spawned = 0;
    for (int i = 0; i < allowed; i++) {
      if (cage.trySpawn(serverLevel, pos)) {
        spawned++;
      }
    }
    if (spawned == 0) {
      cage.spawnDelay = 20 * 2;
      cage.setChanged();
    } else {
      cage.resetDelay(serverLevel);
    }
  }

  private boolean trySpawn(ServerLevel level, BlockPos cagePos) {
    List<String> zombies = this.spawnerTier >= 2 ? ELITE_ZOMBIES
        : this.spawnerTier == 1 ? DANGEROUS_ZOMBIES : BASIC_ZOMBIES;
    String path = zombies.get(level.random.nextInt(zombies.size()));
    ResourceLocation id = ResourceLocation.fromNamespaceAndPath("craftingdeadsurvival", path);
    EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(id);
    if (type == null || type == EntityType.PIG) {
      return false;
    }

    for (int attempt = 0; attempt < 18; attempt++) {
      BlockPos spawnPos = cagePos.offset(
          level.random.nextInt(11) - 5,
          level.random.nextInt(3) - 1,
          level.random.nextInt(11) - 5);
      if (!level.isEmptyBlock(spawnPos)
          || !level.isEmptyBlock(spawnPos.above())
          || !level.getFluidState(spawnPos).isEmpty()
          || !Block.canSupportCenter(level, spawnPos.below(), Direction.UP)) {
        continue;
      }
      Entity entity = type.create(level);
      if (!(entity instanceof Mob mob)) {
        return false;
      }
      mob.moveTo(spawnPos.getX() + 0.5D, spawnPos.getY(),
          spawnPos.getZ() + 0.5D, level.random.nextFloat() * 360.0F, 0.0F);
      if (!level.noCollision(mob)) {
        continue;
      }
      mob.finalizeSpawn(level, level.getCurrentDifficultyAt(spawnPos),
          MobSpawnType.SPAWNER, null);
      level.addFreshEntityWithPassengers(mob);
      return true;
    }
    return false;
  }

  private void resetDelay(ServerLevel level) {
    int minimum = Math.min(8,
        StructureServerConfig.SPAWNER_MIN_DELAY_SECONDS.get()) * 20;
    int maximum = Math.max(minimum,
        Math.min(20, StructureServerConfig.SPAWNER_MAX_DELAY_SECONDS.get()) * 20);
    int tierReduction = this.spawnerTier * 20 * 5;
    this.spawnDelay = Math.max(20,
        minimum + level.random.nextInt(maximum - minimum + 1) - tierReduction);
    this.setChanged();
  }

  @Override
  protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
    super.loadAdditional(tag, registries);
    this.spawnDelay = Math.max(0, Math.min(20 * 20, tag.getInt("SpawnDelay")));
    this.spawnerTier = Math.max(0, Math.min(2, tag.getInt("SpawnerTier")));
  }

  @Override
  protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
    super.saveAdditional(tag, registries);
    tag.putInt("SpawnDelay", this.spawnDelay);
    tag.putInt("SpawnerTier", this.spawnerTier);
  }
}
