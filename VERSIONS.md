# Versions and builds / 版本与构建

Each row corresponds to one Minecraft version and one Fabric JAR. The table shows the minimum runtime requirements declared by Carbon. Install the Fabric API build for the same Minecraft version.

每行对应一个 Minecraft 版本和一份 Fabric JAR。表中是 Carbon 声明的最低运行要求。Fabric API 也要和 Minecraft 版本对应。

| Minecraft | Carbon JAR | Fabric Loader | Java |
|---|---|---:|---:|
| 1.16.5 | `carbon-1.16.5.9-fabric.14.jar` | ≥ 0.14.25 | ≥ 8 |
| 1.17.1 | `carbon-1.17.1.9-fabric.8.jar` | ≥ 0.16.5 | ≥ 16 |
| 1.18.2 | `carbon-1.18.2.9-fabric.14.jar` | ≥ 0.16.5 | ≥ 17 |
| 1.19.4 | `carbon-1.19.4.9-fabric.7.jar` | ≥ 0.16.5 | ≥ 17 |
| 1.20.1 | `carbon-1.20.1.9-fabric.7.jar` | ≥ 0.15.11 | ≥ 17 |
| 1.20.2 | `carbon-1.20.2.9-fabric.7.jar` | ≥ 0.15.11 | ≥ 17 |
| 1.20.4 | `carbon-1.20.4.9-fabric.8.jar` | ≥ 0.15.11 | ≥ 17 |
| 1.20.6 | `carbon-1.20.6.9-fabric.7.jar` | ≥ 0.15.11 | ≥ 21 |
| 1.21.1 | `carbon-1.21.1.9-fabric.7.jar` | ≥ 0.15.11 | ≥ 21 |
| 1.21.3 | `carbon-1.21.3.9-fabric.6.jar` | ≥ 0.15.11 | ≥ 21 |
| 1.21.4 | `carbon-1.21.4.9-fabric.7.jar` | ≥ 0.15.11 | ≥ 21 |
| 1.21.5 | `carbon-1.21.5.9-fabric.13.jar` | ≥ 0.15.11 | ≥ 21 |
| 1.21.8 | `carbon-1.21.8.9-fabric.11.jar` | ≥ 0.15.11 | ≥ 21 |
| 1.21.10 | `carbon-1.21.10.9-fabric.10.jar` | ≥ 0.15.11 | ≥ 21 |
| 1.21.11 | `carbon-1.21.11.9-fabric.10.jar` | ≥ 0.15.11 | ≥ 21 |
| 26.1.2 | `carbon-26.1.2.9-fabric.10.jar` | ≥ 0.18.4 | ≥ 25 |
| 26.2 | `carbon-26.2.9-fabric.10.jar` | ≥ 0.18.4 | ≥ 25 |
| 26.3 | `carbon-26.3.9-fabric.7.jar` | ≥ 0.18.4 | ≥ 25 |

## Build / 构建

The build uses JDK 25 and Python 3.9 or later. Select one Minecraft target from the repository root. On Windows, use `gradlew.bat` in place of `./gradlew`.

构建使用 JDK 25 和 Python 3.9 或更新版本。在仓库根目录选择版本运行。Windows 使用 `gradlew.bat`。

```sh
./gradlew :mc1.16.5:build --no-daemon
./gradlew :mc1.21.8:build --no-daemon
./gradlew :mc26.3:build --no-daemon
```

The selected JAR is under `versions/<Minecraft>/build/libs`. The 1.18.2 target uses `:build` and writes to the root `build/libs`. Choose the `carbon-<version>.jar` runtime artifact.

对应产物在 `versions/<Minecraft>/build/libs`。1.18.2 使用根项目的 `:build`，产物在 `build/libs`。安装 `carbon-<version>.jar`。

## Source layout / 源码结构

- `src/main` contains the 1.18.2 base. `src/legacy`, `src/bufferedentities` and `src/renderstate` contain shared adapters for other rendering pipelines.
- `versions/<Minecraft>/overrides` and `patches.json` describe the changes for each version.
- `tools/prepare_version.py` assembles the version sources in `build/generated`. Make changes in the source and override directories.

The [benchmark](BENCHMARK.md) documents the tested setups and compatibility checks.
