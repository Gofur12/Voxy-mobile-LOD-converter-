package dev.gofur12.lodconverter.dh;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Decoder for Distant Horizons FullDataSourceV2 column blobs (format 2). */
public final class DhV2Codec {
    public static final int WIDTH = 64;
    public static final int COLUMN_COUNT = WIDTH * WIDTH;
    private DhV2Codec() {}

    public record Point(int mappingId, int height, int bottom, int light) {}
    public record Mapping(String biome, String blockState) {}

    public static byte[] decompress(byte[] compressed) throws IOException {
        IOException jniFailure;
        try {
            Class<?> zstd = Class.forName("com.github.luben.zstd.Zstd");
            long size = ((Number) zstd.getMethod("decompressedSize", byte[].class).invoke(null, (Object) compressed)).longValue();
            if (size <= 0 || size > 256L * 1024 * 1024) {
                throw new IOException("Invalid/unknown Zstandard frame size: " + size);
            }
            byte[] out = new byte[(int) size];
            long actual = ((Number) zstd.getMethod("decompress", byte[].class, byte[].class)
                    .invoke(null, out, compressed)).longValue();
            boolean error = (Boolean) zstd.getMethod("isError", long.class).invoke(null, actual);
            if (error || actual != size) {
                String reason = String.valueOf(zstd.getMethod("getErrorName", long.class).invoke(null, actual));
                throw new IOException("Zstandard JNI decompression failed: " + reason);
            }
            return out;
        } catch (Throwable failure) {
            jniFailure = new IOException("zstd-jni unavailable on this runtime", failure);
        }

        try {
            Class<?> zstd = Class.forName("org.lwjgl.util.zstd.Zstd");
            java.nio.ByteBuffer source = java.nio.ByteBuffer.allocateDirect(compressed.length);
            source.put(compressed).flip();
            long size = ((Number) zstd.getMethod("ZSTD_getFrameContentSize", java.nio.ByteBuffer.class)
                    .invoke(null, source)).longValue();
            if (size <= 0 || size > 256L * 1024 * 1024) {
                throw new IOException("Invalid/unknown Zstandard frame size from LWJGL: " + size);
            }
            java.nio.ByteBuffer destination = java.nio.ByteBuffer.allocateDirect((int) size);
            long actual = ((Number) zstd.getMethod("ZSTD_decompress", java.nio.ByteBuffer.class, java.nio.ByteBuffer.class)
                    .invoke(null, destination, source)).longValue();
            boolean error = (Boolean) zstd.getMethod("ZSTD_isError", long.class).invoke(null, actual);
            if (error || actual != size) {
                String reason = String.valueOf(zstd.getMethod("ZSTD_getErrorName", long.class).invoke(null, actual));
                throw new IOException("LWJGL Zstandard decompression failed: " + reason);
            }
            destination.flip();
            byte[] out = new byte[(int) size];
            destination.get(out);
            return out;
        } catch (Throwable failure) {
            IOException error = new IOException("No compatible Zstandard decoder is available (zstd-jni and LWJGL fallback failed)", failure);
            error.addSuppressed(jniFailure);
            throw error;
        }
    }

    public static List<Mapping> readMapping(byte[] compressed) throws IOException {
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(decompress(compressed)))) {
            int count = in.readInt();
            if (count < 0 || count > 1_000_000) throw new IOException("Invalid mapping count: " + count);
            List<Mapping> result = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                String value = in.readUTF();
                int split = value.indexOf("_DH-BSW_");
                if (split <= 0) throw new IOException("Invalid DH mapping entry: " + value);
                result.add(new Mapping(value.substring(0, split), value.substring(split + 8)));
            }
            if (in.available() != 0) throw new IOException("Trailing mapping bytes");
            return result;
        }
    }

    public static void readColumns(byte[] compressed, List<Point>[] target,
                                   int xStart, int xEnd, int zStart, int zEnd) throws IOException {
        byte[] data = decompress(compressed);
        Cursor c = new Cursor(data);
        int cells = (xEnd - xStart) * (zEnd - zStart);
        int[] indexes = new int[cells];
        int at = 0;
        for (int x = xStart; x < xEnd; x++) {
            for (int z = zStart; z < zEnd; z++) {
                int index = x * WIDTH + z;
                indexes[at++] = index;
                int count = c.varInt();
                if (count < 0 || count > 4096) throw new IOException("Invalid column run count: " + count);
                target[index] = new ArrayList<>(count);
                for (int j = 0; j < count; j++) target[index].add(new Point(0, 0, 0, 0));
            }
        }
        int[][] flags = new int[cells][];
        for (int i = 0; i < cells; i++) {
            List<Point> column = target[indexes[i]];
            flags[i] = new int[column.size()];
            for (int j = 0; j < column.size(); j++) {
                int encoded = c.varInt();
                int id = encoded >>> 2;
                flags[i][j] = encoded & 3;
                column.set(j, new Point(id, 0, 0, 0));
            }
        }
        for (int i = 0; i < cells; i++) {
            List<Point> column = target[indexes[i]];
            for (int j = 0; j < column.size(); j++) {
                Point p = column.get(j);
                int height = c.varInt();
                if (height < 0 || height > 4095) throw new IOException("Invalid point height: " + height);
                column.set(j, new Point(p.mappingId(), height, 0, 0));
            }
        }
        int previousBottom = 0;
        for (int i = 0; i < cells; i++) {
            List<Point> column = target[indexes[i]];
            for (int j = 0; j < column.size(); j++) {
                Point p = column.get(j);
                int expected = previousBottom - p.height();
                int bottom = expected;
                if ((flags[i][j] & 1) != 0) bottom += unZigZag(c.varInt());
                if (bottom < 0 || bottom > 4095) throw new IOException("DH point bottom outside 12-bit range: " + bottom);
                column.set(j, new Point(p.mappingId(), p.height(), bottom, 0));
                previousBottom = bottom;
            }
        }
        for (int i = 0; i < cells; i++) {
            List<Point> column = target[indexes[i]];
            for (int j = 0; j < column.size(); j++) {
                if ((flags[i][j] & 2) != 0) {
                    Point p = column.get(j);
                    column.set(j, new Point(p.mappingId(), p.height(), p.bottom(), c.byteValue()));
                }
            }
        }
        if (!c.finished()) throw new IOException("Trailing bytes in DH V2 column blob: " + c.remaining());
    }

    private static int unZigZag(int value) { return (value >>> 1) ^ -(value & 1); }

    private static final class Cursor {
        private final byte[] data;
        private int pos;
        Cursor(byte[] data) { this.data = data; }
        int byteValue() throws IOException {
            if (pos >= data.length) throw new EOFException("Truncated DH V2 blob");
            return data[pos++] & 0xff;
        }
        int varInt() throws IOException {
            int value = 0;
            for (int shift = 0; shift < 35; shift += 7) {
                int b = byteValue();
                value |= (b & 0x7f) << shift;
                if ((b & 0x80) == 0) {
                    if (value < 0) throw new IOException("Negative DH VarInt");
                    return value;
                }
            }
            throw new IOException("Overlong DH VarInt");
        }
        boolean finished() { return pos == data.length; }
        int remaining() { return data.length - pos; }
    }
}
