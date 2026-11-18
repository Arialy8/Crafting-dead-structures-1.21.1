package com.craftingdead.structures.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class StructureServerConfig {

  private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

  public static final ModConfigSpec.IntValue CIVILIAN_REFRESH_MINUTES;
  public static final ModConfigSpec.IntValue MEDICAL_REFRESH_MINUTES;
  public static final ModConfigSpec.IntValue POLICE_REFRESH_MINUTES;
  public static final ModConfigSpec.IntValue RARE_CIVILIAN_REFRESH_MINUTES;
  public static final ModConfigSpec.IntValue MILITARY_REFRESH_MINUTES;

  public static final ModConfigSpec.IntValue SPAWNER_ACTIVATION_RANGE;
  public static final ModConfigSpec.IntValue SPAWNER_MAX_NEARBY;
  public static final ModConfigSpec.IntValue SPAWNER_MIN_COUNT;
  public static final ModConfigSpec.IntValue SPAWNER_MAX_COUNT;
  public static final ModConfigSpec.IntValue SPAWNER_MIN_DELAY_SECONDS;
  public static final ModConfigSpec.IntValue SPAWNER_MAX_DELAY_SECONDS;

  public static final ModConfigSpec SPEC;

  static {
    BUILDER.push("ground_loot");
    CIVILIAN_REFRESH_MINUTES = BUILDER
        .comment("普通民用地面战利品刷新时间（分钟）")
        .defineInRange("civilianRefreshMinutes", 5, 1, 10);
    MEDICAL_REFRESH_MINUTES = BUILDER
        .comment("医疗地面战利品刷新时间（分钟）")
        .defineInRange("medicalRefreshMinutes", 6, 1, 10);
    POLICE_REFRESH_MINUTES = BUILDER
        .comment("警用地面战利品刷新时间（分钟）")
        .defineInRange("policeRefreshMinutes", 8, 1, 10);
    RARE_CIVILIAN_REFRESH_MINUTES = BUILDER
        .comment("稀有民用地面战利品刷新时间（分钟）")
        .defineInRange("rareCivilianRefreshMinutes", 9, 1, 10);
    MILITARY_REFRESH_MINUTES = BUILDER
        .comment("军用地面战利品刷新时间（分钟）")
        .defineInRange("militaryRefreshMinutes", 10, 1, 10);
    BUILDER.pop();

    BUILDER.push("zombie_spawner_cage");
    SPAWNER_ACTIVATION_RANGE = BUILDER
        .defineInRange("activationRange", 24, 1, 64);
    SPAWNER_MAX_NEARBY = BUILDER
        .defineInRange("maxNearbyZombies", 6, 1, 64);
    SPAWNER_MIN_COUNT = BUILDER
        .defineInRange("minimumSpawnCount", 1, 1, 16);
    SPAWNER_MAX_COUNT = BUILDER
        .defineInRange("maximumSpawnCount", 3, 1, 16);
    SPAWNER_MIN_DELAY_SECONDS = BUILDER
        .defineInRange("minimumDelaySeconds", 8, 1, 60);
    SPAWNER_MAX_DELAY_SECONDS = BUILDER
        .defineInRange("maximumDelaySeconds", 20, 1, 60);
    BUILDER.pop();

    SPEC = BUILDER.build();
  }

  public static int lootRefreshTicks(String tier) {
    int minutes = switch (tier) {
      case "medical" -> MEDICAL_REFRESH_MINUTES.get();
      case "police" -> POLICE_REFRESH_MINUTES.get();
      case "rare_civilian" -> RARE_CIVILIAN_REFRESH_MINUTES.get();
      case "military" -> MILITARY_REFRESH_MINUTES.get();
      default -> CIVILIAN_REFRESH_MINUTES.get();
    };
    return Math.min(minutes, 10) * 60 * 20;
  }

  private StructureServerConfig() {}
}
