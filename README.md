# Voxy Mobile LOD Converter

Standalone Fabric client add-on for importing Distant Horizons FullData V2 data into the currently active Voxy world. This repository does not bundle, fork, patch, or modify Voxy.

## Target
- Minecraft Java 1.20.1
- Fabric Loader / Fabric API
- Java 17
- Distant Horizons SQLite FullData format V2, Zstandard compression mode 4
- Voxy installed separately (runtime integration is reflective)

## In-game GUI

Press **K** in-game to open the converter screen. Browse folders, select a `.sqlite` / `.db` file, inspect it, then press **Impor ke Voxy**. The screen includes a built-in folder browser so the database path does not need to be typed as a command. On Android, folder access still depends on Zalith / Android storage permissions.

The original commands remain available for advanced use.

## In-game commands

Inspect the database without changing it:

```text
/dhconvert inspect <absolute-path-to-DistantHorizons.sqlite>
```

Import Overworld LODs into the currently open Voxy world:

```text
/dhconvert import <absolute-path-to-DistantHorizons.sqlite>
```

The importer reads only `DetailLevel=0`, `DataFormatVersion=2`, `CompressionMode=4` rows. It decodes the 62x62 inner columns and the four adjacency strips, resolves the DH block/biome mapping against the active Minecraft registries, and inserts 16x16x16 sections through Voxy's runtime section update pipeline. Voxy's normal mip propagation then builds parent LOD levels.

**Important:** run the import in the matching destination world. Back up the world before testing. Keep the world open until the chat reports completion. The source SQLite database is opened read-only.

## Current limitations / verification status

- Overworld importer implementation and decoder unit tests are committed.
- Voxy integration uses reflective access to the existing runtime classes (`WorldIdentifier`, `VoxelizedSection`, `WorldVoxilizedSectionMipper`, and `WorldUpdater`). No Voxy classes are copied into this add-on.
- The build workflow is configured, but a successful CI build and in-game import have not yet been verified. Do not treat this as a tested release until both pass.
- SQLite access uses Android's built-in read-only SQLite API when running on Android, with SQLite JDBC as the desktop fallback. Zstandard tries zstd-jni first and falls back to Voxy/Minecraft's LWJGL Zstd module when available. Android/Zalith runtime compatibility is still **not verified on-device**; a successful desktop build does not establish Android support.
- DIM-1 / DIM1-style dimension databases are not imported yet; this first pass is Overworld only.

## Build

Use JDK 17 and Gradle:

```sh
gradle --no-daemon test build
```

The mod JAR is written to `build/libs/`.
