# Architecture and scope

## Isolation rule
This is an independent Fabric add-on. It does not modify Voxy source, fork Voxy, patch its jar, or bundle Voxy classes.

## Import pipeline
1. Open the user's DH SQLite file in read-only mode.
2. Select Overworld rows with FullData format 2 and Zstandard compression mode 4.
3. Decode the compressed V2 mapping table and the inner 62x62 data plus four border strips.
4. Resolve block states and biomes through the active Minecraft registries and Voxy's runtime Mapper.
5. Fill Voxy-compatible 16x16x16 section payloads and submit them through Voxy's normal section/mip update methods.

## Runtime boundary
Voxy integration is reflective to avoid a compile-time dependency on Voxy source or jar. The integration expects the Voxy runtime classes and methods to exist. If they are missing or incompatible, import stops with an error rather than writing an alternate database format.

## Data safety
- The DH database is opened read-only.
- The importer never deletes or updates DH rows.
- Converted data is inserted into the active Voxy world storage.
- Validate counts, mapping indexes, compressed frame sizes, and blob boundaries before allocating or inserting.

## Platform notes
SQLite uses Android's built-in read-only database API when present, with JDBC on desktop. Zstandard uses zstd-jni first and reflectively falls back to Voxy/Minecraft's LWJGL Zstd module. Android/Zalith runtime behavior still requires an on-device test before the add-on is labelled mobile-compatible.
