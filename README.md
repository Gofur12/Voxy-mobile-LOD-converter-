# Voxy Mobile LOD Converter

A standalone Fabric client mod project. It does **not** contain, fork, patch, or modify Voxy source code.

## Target
- Minecraft Java 1.20.1
- Fabric Loader / Fabric API
- Java 17
- Distant Horizons 3.3.3 SQLite databases (initial target: Overworld)

## Current status
This is the independent project bootstrap. The first in-game command is a **read-only database inspector**:

```
/dhconvert inspect <absolute-path-to-sqlite>
```

It checks SQLite integrity and reports table names and the `FullData` columns. It never writes to the source database.

## Important limitation
The inspector is not yet a converter. The next implementation stages are:
1. Validate the exact DH 3.3.3 V2 metadata and compressed payloads.
2. Decode V2 region and adjacency data with bounds checks.
3. Implement a Voxy storage adapter using only a documented/stable external integration point.
4. Test the produced data against a real Voxy 1.20.1 instance before publishing a usable conversion release.

SQLite JDBC and Zstandard JNI are currently declared as bundled runtime dependencies for desktop development. Android/Zalith compatibility is **not verified**; native library loading must be validated on-device before claiming support.

## Build
Use JDK 17 and Gradle:
```sh
gradle build
```

The built mod jar is in `build/libs/`.
