# Carbon Fabric 移植版

感谢原作者 **First_sight（FirstSight）** 创作 Carbon 并开放源代码。[原版项目](https://www.curseforge.com/minecraft/mc-mods/carbons) · [上游源码](https://github.com/CHUJIAN-afk/Carbon)

[English](README.md)

这是 Carbon 的非官方 Fabric 客户端移植。它缓存物品模型几何，用 GPU 实例化合批绘制重复的掉落物。大量物品集中在农场产出口等场景时，效果更明显。每个 Minecraft 版本对应独立的 JAR。[支持版本](VERSIONS.md)

## Benchmark

RTX 5060 Ti 原生 OpenGL，1280×720，1.5 GiB Java 堆，8 核 JVM 预算。正对相同布置的单区块场景内 3,000 个正常 tick 的打火石，开启实体阴影并关闭光影包。Sodium 测试均搭配对应版本的 Iris 和 Lithium，1.16.5 OptiFine G8 使用独立整合包。每行在同一客户端内正反序采样，每档各 4 秒，数值为平均 FPS / 1% low。不限额与 4096 档均实际绘制 3,000 个，512 档实际绘制 512 个。

| Minecraft | Carbon 后缀 | 不限额 | 4096 限额 | 512 限额 |
|---|---:|---:|---:|---:|
| 1.16.5 Sodium | .14 | 981.3 / 224.9 | 996.4 / 225.6 | 1124.5 / 236.4 |
| 1.21.8 | .11 | 2102.8 / 304.2 | 1882.6 / 291.9 | 2098.6 / 317.2 |
| 26.3 | .7 | 699.1 / 192.1 | 719.9 / 219.8 | 1290.4 / 272.6 |

完整文档同时收录有无 Carbon 的七组对照和限额测试，覆盖 18 个版本及 1.16.5 的两套渲染配置。[Benchmark](BENCHMARK.md)

## 安装

构建与 Minecraft 版本对应的 JAR，和 Fabric API 一起放进该实例的 `mods` 目录。每个实例只放一份 Carbon。最低 Fabric Loader 和 Java 版本见版本表。1.16.5 支持 Fabric Loader 0.14.25。[版本要求](VERSIONS.md)

## 建议搭配

- **Sodium** 优化客户端的通用渲染，**Lithium** 优化游戏逻辑。按 Minecraft 版本选择对应的 Fabric 构建。[Sodium](https://modrinth.com/mod/sodium) · [Lithium](https://modrinth.com/mod/lithium)
- **Iris** 用于加载光影包，安装时与 Sodium 配套。1.21.5 和 1.21.8 开启光影包后走兼容绘制路径，两版均实测了 BSL Original 8.3。[Iris](https://modrinth.com/mod/iris)

## 模组兼容

- **Tweakeroo** 继续提供 `tweakRenderLimitEntities` 开关和 `renderLimitItem` 上限。Carbon 在支持的渲染路径上从可见掉落物里挑选，合批物品和原生绘制物品共用额度。选中的实体可能和 Tweakeroo 原生顺序不同。[Tweakeroo](https://github.com/maruohon/tweakeroo)
- **TweakerMore** 开启 `preciseItemEntityModel` 时保留其精确模型的缩放和朝向，符合条件的模型走兼容的提交路径。[TweakerMore](https://github.com/Fallen-Breath/tweakermore)
- **EntityCulling** 通过兼容路径继续控制实体可见性。[EntityCulling](https://github.com/tr7zw/EntityCulling)

Tweakeroo 和 TweakerMore 都需要 MaLiLib。[MaLiLib](https://modrinth.com/mod/malilib)

`/carbon config` 打开配置，`/carbon stats` 查看渲染计数。`/carbon enabled false` 和 `/carbon enabled true` 可以在游戏里做开关对照。

## 从源码构建

使用 JDK 25，并确保终端可以执行 Python 3.9 或更新版本。以下命令在仓库根目录运行。

```powershell
.\gradlew.bat :mc1.21.8:build --no-daemon
```

Linux 或 macOS 用 `./gradlew`。该版本的产物在 `versions/1.21.8/build/libs`。1.18.2 使用根任务 `:build`，产物在 `build/libs`。[构建与版本说明](VERSIONS.md)

## 许可证

沿用 MIT 许可证和原版权声明，移植以 Forge 1.20.1 上游实现为起点。[许可证](LICENSE) · [上游基线](https://github.com/CHUJIAN-afk/Carbon/commit/8c8cd5135240c82b765e4f58c6023d14be378339) · [更新记录](CHANGELOG.md)
