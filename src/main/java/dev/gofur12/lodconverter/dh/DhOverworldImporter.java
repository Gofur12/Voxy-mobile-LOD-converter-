package dev.gofur12.lodconverter.dh;

import dev.gofur12.lodconverter.voxy.VoxyBridge;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.world.World;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Imports one DH 3.3.3 V2 Overworld database into the currently open Voxy world. */
public final class DhOverworldImporter {
    private static final AtomicBoolean RUNNING = new AtomicBoolean();
    private DhOverworldImporter() {}

    public static void start(Path file) {
        if (!RUNNING.compareAndSet(false, true)) throw new IllegalStateException("An import is already running");
        Thread worker = new Thread(() -> {
            try {
                run(file);
            } catch (Throwable error) {
                MinecraftClient.getInstance().execute(() -> MinecraftClient.getInstance().inGameHud.getChatHud()
                        .addMessage(Text.literal("[DH Converter] Import failed: " + error.getMessage())));
                error.printStackTrace();
            } finally {
                RUNNING.set(false);
            }
        }, "DH-V2-Overworld-Importer");
        worker.setDaemon(true);
        worker.start();
    }

    @SuppressWarnings("unchecked")
    private static void run(Path file) throws Exception {
        MinecraftClient client = MinecraftClient.getInstance();
        var world = client.world;
        if (world == null) throw new IllegalStateException("Join the target world before importing");
        if (!World.OVERWORLD.equals(world.getRegistryKey())) {
            throw new IllegalStateException("This database is the Overworld. Join the Overworld before importing so Nether/End LOD storage is not overwritten.");
        }
        VoxyBridge voxy = VoxyBridge.currentWorld();
        voxy.acquireRef();
        try {
        int bottomY = world.getBottomY();
        int height = world.getHeight();
        int sectionCount = (height + 15) >> 4;

        try (DhV2Database db = new DhV2Database(file)) {
            List<int[]> rows = db.listRows();
            if (rows.isEmpty()) throw new IllegalStateException("No Overworld LOD rows found");
            long completed = 0;
            long total = rows.size();
            for (int[] position : rows) {
                if (Thread.currentThread().isInterrupted()) throw new InterruptedException("Import interrupted");
                if (client.world != world) throw new IllegalStateException("World changed during import; stopping safely");
                DhV2Database.Row row = db.readRow(position[0], position[1]);
                List<DhV2Codec.Point>[] columns = (List<DhV2Codec.Point>[]) new List<?>[DhV2Codec.COLUMN_COUNT];
                for (int i = 0; i < columns.length; i++) columns[i] = new ArrayList<>();
                DhV2Codec.readColumns(row.data(), columns, 1, 63, 1, 63);
                if (row.north() != null) DhV2Codec.readColumns(row.north(), columns, 0, 64, 0, 1);
                if (row.south() != null) DhV2Codec.readColumns(row.south(), columns, 0, 64, 63, 64);
                if (row.east() != null) DhV2Codec.readColumns(row.east(), columns, 63, 64, 0, 64);
                if (row.west() != null) DhV2Codec.readColumns(row.west(), columns, 0, 1, 0, 64);

                List<DhV2Codec.Mapping> mappings = DhV2Codec.readMapping(row.mapping());
                long[] voxyMappings = new long[mappings.size()];
                for (int i = 0; i < mappings.size(); i++) {
                    DhV2Codec.Mapping mapping = mappings.get(i);
                    int blockId = voxy.blockId(mapping.blockState());
                    int biomeId = voxy.biomeId(mapping.biome());
                    voxyMappings[i] = VoxyBridge.mapping(0, blockId, biomeId);
                }

                // Keep a single bounded scratch allocation per DH row instead of allocating
                // section arrays 16 times for every row. Every chunk is cleared before reuse.
                long[][] sectionData = new long[sectionCount][4096];
                int[] nonAir = new int[sectionCount];
                for (int chunkX = 0; chunkX < 4; chunkX++) {
                    for (int chunkZ = 0; chunkZ < 4; chunkZ++) {
                        if (Thread.currentThread().isInterrupted()) throw new InterruptedException("Import interrupted");
                        if (client.world != world) throw new IllegalStateException("World changed during import; stopping safely");
                        voxy.awaitCapacity();
                        if (client.world != world) throw new IllegalStateException("World changed during import; stopping safely");
                        for (long[] section : sectionData) Arrays.fill(section, 0L);
                        Arrays.fill(nonAir, 0);
                        for (int lx = 0; lx < 16; lx++) {
                            int tileX = chunkX * 16 + lx;
                            for (int lz = 0; lz < 16; lz++) {
                                int tileZ = chunkZ * 16 + lz;
                                List<DhV2Codec.Point> runs = columns[tileX * 64 + tileZ];
                                for (DhV2Codec.Point point : runs) {
                                    if (point.mappingId() < 0 || point.mappingId() >= voxyMappings.length)
                                        throw new IllegalStateException("DH mapping index out of range: " + point.mappingId());
                                    long mapped = VoxyBridge.mapping(point.light(), (int)((voxyMappings[point.mappingId()] >>> 27) & 0xfffff),
                                            (int)((voxyMappings[point.mappingId()] >>> 47) & 0x1ff));
                                    int start = Math.max(0, point.bottom());
                                    int end = Math.min(height, point.bottom() + point.height());
                                    for (int relY = start; relY < end; relY++) {
                                        int sy = relY >> 4;
                                        int index = ((relY & 15) << 8) | (lz << 4) | lx;
                                        long old = sectionData[sy][index];
                                        sectionData[sy][index] = mapped;
                                        int oldBlock = (int) ((old >>> 27) & 0xfffff);
                                        int newBlock = (int) ((mapped >>> 27) & 0xfffff);
                                        if (oldBlock == 0 && newBlock != 0) nonAir[sy]++;
                                        else if (oldBlock != 0 && newBlock == 0) nonAir[sy]--;
                                    }
                                }
                            }
                        }
                        for (int sy = 0; sy < sectionCount; sy++) {
                            if (client.world != world) throw new IllegalStateException("World changed during import; stopping safely");
                            voxy.insert(row.x() * 4 + chunkX, (bottomY >> 4) + sy,
                                    row.z() * 4 + chunkZ, sectionData[sy], nonAir[sy]);
                        }
                    }
                }
                completed++;
                if (completed == 1 || completed % 8 == 0 || completed == total) {
                    final long done = completed;
                    MinecraftClient.getInstance().execute(() -> client.inGameHud.getChatHud()
                            .addMessage(Text.literal("[DH Converter] Overworld rows: " + done + "/" + total)));
                }
            }
            MinecraftClient.getInstance().execute(() -> client.inGameHud.getChatHud()
                    .addMessage(Text.literal("[DH Converter] Import complete: " + total + " DH rows processed.")));
        }
        } finally {
            voxy.releaseRef();
        }
    }
}
