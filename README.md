# Crafting Dead: Structure Generation（行尸走肉 · 建筑生成）

> Minecraft `1.21.1` · NeoForge `21.1.x` · 扩展模组

本仓库是 **Crafting Dead（行尸走肉）** 的**建筑生成**扩展模组源码。它在世界中自然生成末日建筑，并配套生成**战利品箱子**与**地面物资刷新**，以及带独立概率的僵尸刷怪笼。

This repository contains the source of the **Structure Generation** add-on for **Crafting Dead**. It naturally generates post-apocalyptic buildings in the world, together with **loot chests**, **ground loot spawns**, and zombie spawner cages with independent probabilities.

需要与主模组 Crafting Dead 一同安装；建筑、箱子与物资均面向末日生存玩法。
Requires the main Crafting Dead mod. Buildings, chests and loot are designed for the zombie-survival gameplay.

仓库仅为主源码目录 `src/main`：
- `src/main/java` — Java 源代码（16 个文件）
- `src/main/resources` — 结构 NBT、战利品表与资源

Contains only `src/main`:
- `src/main/java` — Java source code (16 files)
- `src/main/resources` — structure NBTs, loot tables and assets

---

## 介绍视频 / Video

- 哔哩哔哩（Bilibili）：https://www.bilibili.com/video/BV1opM96vEbz/?spm_id_from=333.1387.homepage.video_card.click

---

## 功能特色 / Features

- **建筑生成**：多部件（multipart）自然生成建筑，包括小城市、体育场、海滨别墅、私家别墅、半山别墅、科学大楼、沙漠站点、树屋、蟾蜍屋、天空岛等，支持结构随机化处理。
- **战利品箱子**：内置军用、警察、医疗、民用、武器、弹药、食物饮水等多类战利品箱，按类型填充物资。
- **地面物资刷新**：民用、稀有民用、医疗、军用、警察等地面战利品生成器，在世界中有概率刷新物资。
- **僵尸刷怪笼**：普通、危险、精英僵尸刷怪笼，各自使用独立生成概率。
- **配套指令**：`/craftingdead structures` 提供状态查看、结构列表、定位与手动放置功能。

- **Building generation**: multipart natural structures including small cities, stadiums, seaside/private/hillside villas, science building, desert site, tree house, toad house and sky islands, with structure randomization.
- **Loot chests**: military, police, medical, civilian, weapons, ammo and food/water chests filled by type.
- **Ground loot spawns**: civilian, rare civilian, medical, military and police ground loot generators with configurable spawn chances.
- **Zombie spawners**: normal, dangerous and elite zombie spawner cages with independent probabilities.
- **Commands**: `/craftingdead structures` provides status, list, locate and place functions.

---

## 指令介绍 / Commands

需管理员权限（权限等级 2）。/ Requires operator permission (level 2).

| 指令 / Command | 说明 / Description |
|---|---|
| `/craftingdead structures status` | 查看结构生成状态 / View structure generation status |
| `/craftingdead structures list` | 列出所有可生成结构 / List all generatable structures |
| `/craftingdead structures locate <名称>` | 定位最近的结构坐标 / Locate the nearest structure |
| `/craftingdead structures place <名称> [坐标]` | 在指定位置手动放置结构 / Place a structure manually |

---

## 环境要求 / Requirements

| 项目 / Item | 版本 / Version |
|---|---|
| Minecraft | 1.21.1 |
| Mod 加载器 / Mod loader | NeoForge 21.1.x |
| 主模组 / Main mod | Crafting Dead（需同时安装 / required） |
| Java | 21 |

客户端与服务端需使用相同版本模组，请勿混用不同版本。
Client and server must use the same mod version; do not mix versions.

---

## 联系与反馈 / Contact

遇到问题或有建议，可通过邮箱或 QQ 群联系 / For bugs or suggestions, contact via email or QQ group:

- 邮箱 / Email：**2078195736@qq.com**
- QQ 群聊 / QQ group：**947796302**

---

## 许可 / License

本模组元数据中标注的许可为 `All Rights Reserved`，与原工程保持一致。
The license declared in the mod metadata is `All Rights Reserved`, kept as in the original project.
