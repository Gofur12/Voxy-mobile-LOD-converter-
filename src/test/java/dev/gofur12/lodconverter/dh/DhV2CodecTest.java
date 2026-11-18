package dev.gofur12.lodconverter.dh;

import com.github.luben.zstd.Zstd;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DhV2CodecTest {
    @SuppressWarnings("unchecked")
    @Test
    void decodesOneV2ColumnPoint() throws Exception {
        byte[] raw = new byte[]{1, 3, 5, 30, (byte) 0xF1};
        List<DhV2Codec.Point>[] columns = emptyColumns();
        DhV2Codec.readColumns(Zstd.compress(raw), columns, 1, 2, 1, 2);
        assertEquals(List.of(new DhV2Codec.Point(0, 5, 10, 0xF1)), columns[65]);
    }

    @Test
    void decodesCompressedMappingTable() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeInt(1);
            out.writeUTF("minecraft:plains_DH-BSW_AIR");
        }
        var mappings = DhV2Codec.readMapping(Zstd.compress(bytes.toByteArray()));
        assertEquals(1, mappings.size());
        assertEquals("minecraft:plains", mappings.get(0).biome());
        assertEquals("AIR", mappings.get(0).blockState());
    }

    @Test
    void rejectsOverlongVarintAndTruncatedData() {
        assertThrows(Exception.class, () -> DhV2Codec.readColumns(
                Zstd.compress(new byte[]{(byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80, 0}),
                emptyColumns(), 0, 1, 0, 1));
    }

    @SuppressWarnings("unchecked")
    private static List<DhV2Codec.Point>[] emptyColumns() {
        List<DhV2Codec.Point>[] columns = (List<DhV2Codec.Point>[]) new List<?>[4096];
        for (int i = 0; i < columns.length; i++) columns[i] = new ArrayList<>();
        return columns;
    }
}
