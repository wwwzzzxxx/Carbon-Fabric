# Carbon for Fabric

Thanks to **First_sight (FirstSight)** for creating Carbon and sharing its source code. [Original project](https://www.curseforge.com/minecraft/mc-mods/carbons) · [Upstream source](https://github.com/CHUJIAN-afk/Carbon)

[简体中文](README.zh-CN.md)

Carbon for Fabric is an unofficial client-side port of Carbon. It caches item model geometry and uses GPU instancing to draw repeated dropped items. The benefit is most visible when many items occupy the same area, such as the output of a large farm. Each supported Minecraft version has its own build. [Supported versions](VERSIONS.md)

## Benchmark

RTX 5060 Ti native OpenGL, 1280×720, a 1.5 GiB Java heap, and an eight-core JVM budget. The fixed view contains 3,000 ticking flint and steel drops. Entity shadows are enabled and shader packs are disabled. The Sodium stack includes Iris and Lithium. OptiFine G8 uses a separate 1.16.5 profile. Each row uses one isolated client and two four-second windows per setting. Values are average FPS / 1% low. Unlimited and 4096 both draw 3,000 items. The 512 limit draws 512 items.

| Minecraft | Carbon suffix | Unlimited | 4096 limit | 512 limit |
|---|---:|---:|---:|---:|
| 1.16.5 Sodium | .14 | 981.3 / 224.9 | 996.4 / 225.6 | 1124.5 / 236.4 |
| 1.21.8 | .11 | 2102.8 / 304.2 | 1882.6 / 291.9 | 2098.6 / 317.2 |
| 26.3 | .7 | 699.1 / 192.1 | 719.9 / 219.8 | 1290.4 / 272.6 |

The full benchmark contains both the seven-condition comparison with and without Carbon and the item-limit tests, covering 18 versions and both 1.16.5 renderers. [Benchmark](BENCHMARK.md)

## Installation

Build the JAR for your exact Minecraft version and put it in the instance's `mods` directory alongside Fabric API. Use one Carbon JAR per instance. The supported-version table lists the minimum Fabric Loader and Java versions. Minecraft 1.16.5 supports Fabric Loader 0.14.25. [Version requirements](VERSIONS.md)

## Recommended mods

- **Sodium** for general client rendering performance and **Lithium** for game logic. Choose the builds for your Minecraft version. [Sodium](https://modrinth.com/mod/sodium) · [Lithium](https://modrinth.com/mod/lithium)
- **Iris** if you use shader packs. Match its version to Sodium. On 1.21.5 and 1.21.8, the shader-pack path uses compatibility rendering. BSL Original 8.3 was tested in both versions. [Iris](https://modrinth.com/mod/iris)

## Mod compatibility

- **Tweakeroo** still provides `tweakRenderLimitEntities` and `renderLimitItem`. On Carbon's supported rendering paths, the limit applies to the visible dropped items Carbon selects. Batched and native items share the same limit. Item selection can differ from Tweakeroo's native order. [Tweakeroo](https://github.com/maruohon/tweakeroo)
- **TweakerMore** keeps its `preciseItemEntityModel` transforms, including scale and orientation. Carbon uses the compatible model submission path where appropriate. [TweakerMore](https://github.com/Fallen-Breath/tweakermore)
- **EntityCulling** continues to handle entity visibility in the compatibility path. [EntityCulling](https://github.com/tr7zw/EntityCulling)

Tweakeroo and TweakerMore use MaLiLib. [MaLiLib](https://modrinth.com/mod/malilib)

Use `/carbon config` to open the settings and `/carbon stats` to inspect rendering counters. Toggle between `/carbon enabled false` and `/carbon enabled true` for an in-game comparison.

## Build from source

Use JDK 25 and Python 3.9 or later on your PATH. Run this command from the repository root.

```sh
./gradlew :mc1.21.8:build --no-daemon
```

Use `gradlew.bat` on Windows. The JAR is written to `versions/1.21.8/build/libs`. For 1.18.2, run `./gradlew :build --no-daemon` and look in `build/libs`. [Build and version details](VERSIONS.md)

## License

MIT, with the original copyright notice retained. The port started from the upstream Forge 1.20.1 implementation. [License](LICENSE) · [Upstream base](https://github.com/CHUJIAN-afk/Carbon/commit/8c8cd5135240c82b765e4f58c6023d14be378339) · [Changelog](CHANGELOG.md)
