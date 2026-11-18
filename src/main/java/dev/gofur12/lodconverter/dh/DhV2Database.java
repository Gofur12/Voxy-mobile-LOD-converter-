package dev.gofur12.lodconverter.dh;

import java.io.IOException;
import java.nio.file.*;
import java.sql.*;
import java.util.*;

public final class DhV2Database implements AutoCloseable {
    public record Row(int x, int z, byte[] data, byte[] north, byte[] south, byte[] east, byte[] west, byte[] mapping) {}
    private final Connection connection;

    public DhV2Database(Path path) throws Exception {
        Path file = path.toAbsolutePath().normalize();
        if (!Files.isRegularFile(file)) throw new IOException("SQLite file not found: " + file);
        this.connection = DriverManager.getConnection("jdbc:sqlite:" + file + "?mode=ro");
        try (Statement s = connection.createStatement(); ResultSet rs = s.executeQuery("PRAGMA integrity_check")) {
            if (!rs.next() || !"ok".equalsIgnoreCase(rs.getString(1))) throw new IOException("SQLite integrity check failed");
        }
        try (Statement s = connection.createStatement(); ResultSet rs = s.executeQuery(
                "SELECT COUNT(*) FROM FullData WHERE DetailLevel=0 AND DataFormatVersion=2 AND CompressionMode=4")) {
            rs.next();
            if (rs.getInt(1) == 0) throw new IOException("No Overworld FullData V2 rows with Zstandard compression found");
        }
    }

    public List<int[]> listRows() throws SQLException {
        List<int[]> rows = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT PosX, PosZ FROM FullData WHERE DetailLevel=0 AND DataFormatVersion=2 AND CompressionMode=4 ORDER BY (PosX*PosX+PosZ*PosZ)");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) rows.add(new int[]{rs.getInt(1), rs.getInt(2)});
        }
        return rows;
    }

    public Row readRow(int x, int z) throws SQLException, IOException {
        String sql = "SELECT Data,NorthAdjData,SouthAdjData,EastAdjData,WestAdjData,Mapping " +
                "FROM FullData WHERE DetailLevel=0 AND PosX=? AND PosZ=? AND DataFormatVersion=2 AND CompressionMode=4";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, x); ps.setInt(2, z);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new IOException("Missing DH V2 row at " + x + "," + z);
                return new Row(x,z,rs.getBytes(1),rs.getBytes(2),rs.getBytes(3),rs.getBytes(4),rs.getBytes(5),rs.getBytes(6));
            }
        }
    }

    @Override public void close() throws SQLException { connection.close(); }
}
