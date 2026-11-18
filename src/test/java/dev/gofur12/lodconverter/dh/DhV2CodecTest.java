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
    void keepsPredictiveBottomAcrossAdjacentColumns() throws Exception {
        // Two columns, one point each. The second bottom is predicted from
        // the first point, matching FullDataSourceV2DTO's global predictor.
        byte[] raw = new byte[]{1, 1, 1, 0, 5, 3, 30};
        List<DhV2Codec.Point>[] columns = emptyColumns();
        DhV2Codec.readColumns(Zstd.compress(raw), columns, 0, 1, 0, 2);
        assertEquals(new DhV2Codec.Point(0, 5, 10, 0), columns[0].get(0));
        assertEquals(new DhV2Codec.Point(0, 3, 7, 0), columns[1].get(0));
    }

    @Test
    void acceptsEmptyMappingTable() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeInt(0);
        }
        assertTrue(DhV2Codec.readMapping(Zstd.compress(bytes.toByteArray())).isEmpty());
    }

    @Test
    void rejectsOverlongVarintAndTruncatedData() {
        assertThrows(Exception.class, () -> DhV2Codec.readColumns(
                Zstd.compress(new byte[]{(byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80, 0}),
                emptyColumns(), 0, 1, 0, 1));
    }

    @Test
    void rejectsVarIntOverflowInsteadOfWrapping() {
        byte[] overflow = new byte[]{(byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80, 0x08};
        assertThrows(Exception.class, () -> DhV2Codec.readColumns(
                Zstd.compress(overflow), emptyColumns(), 0, 1, 0, 1));
    }

    @Test
    void rejectsZeroHeightDataPoints() {
        // One column, one mapping ID, then an invalid zero height.
        byte[] raw = new byte[]{1, 0, 0};
        assertThrows(Exception.class, () -> DhV2Codec.readColumns(
                Zstd.compress(raw), emptyColumns(), 0, 1, 0, 1));
    }

    @SuppressWarnings("unchecked")
    private static List<DhV2Codec.Point>[] emptyColumns() {
        List<DhV2Codec.Point>[] columns = (List<DhV2Codec.Point>[]) new List<?>[4096];
        for (int i = 0; i < columns.length; i++) columns[i] = new ArrayList<>();
        return columns;
    }
}
