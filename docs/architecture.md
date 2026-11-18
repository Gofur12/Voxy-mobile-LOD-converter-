# Architecture and scope

## Isolation rule
This repository is an independent Fabric mod. It must not import Voxy implementation classes by copying source, patch Voxy files, or publish a modified Voxy jar.

## Import pipeline
- Source reader: opens DH SQLite in read-only mode.
- Decoder: validates metadata, Zstandard frames, and DH V2 payload boundaries.
- Conversion model: normalized LOD columns / sections.
- Target adapter: writes through a supported external Voxy integration interface. If no stable public interface exists for the targeted Voxy build, stop and document the blocker rather than patching Voxy.

## Safety of user data
- Never update or delete rows in the DH database.
- Write converted output to a separate destination.
- Validate dimensions, counts, and payload sizes before allocation.
- Keep Overworld import separate from DIM-1 and DIM1 until the first dimension passes validation.

## Android
Desktop SQLite JDBC and Zstandard JNI dependencies are provisional. They are not proof of Android support. The Android runtime must be tested on the actual launcher / ABI before a release is labelled Android-compatible.
