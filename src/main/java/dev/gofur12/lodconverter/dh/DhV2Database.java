package dev.gofur12.lodconverter.dh;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.*;
import java.sql.*;
import java.util.*;

/** Read-only DH SQLite access. Uses Android's built-in SQLite on Android and JDBC on desktop. */
public final class DhV2Database implements AutoCloseable {
    public record Row(int x, int z, byte[] data, byte[] north, byte[] south, byte[] east, byte[] west, byte[] mapping) {}

    private final Connection connection;
    private final Object androidDatabase;

    public DhV2Database(Path path) throws Exception {
        Path file = path.toAbsolutePath().normalize();
        if (!Files.isRegularFile(file)) throw new IOException("SQLite file not found: " + file);

        Object androidDb = null;
        Connection jdbc = null;
        try {
            if (isAndroidRuntime()) {
                androidDb = openAndroidDatabase(file);
                validateAndroidDatabase(androidDb);
            } else {
                // Use a URI-encoded absolute path so spaces, '#', and other URI
                // characters in Android/desktop world-folder names are not parsed
                // as JDBC URL syntax.
                String sqliteUri = file.toUri().toString();
                jdbc = DriverManager.getConnection("jdbc:sqlite:" + sqliteUri + "?mode=ro");
                validateJdbcDatabase(jdbc);
            }
        } catch (Exception failure) {
            try {
                if (androidDb != null) androidDb.getClass().getMethod("close").invoke(androidDb);
                if (jdbc != null) jdbc.close();
            } catch (Exception closeFailure) {
                failure.addSuppressed(closeFailure);
            }
            throw failure;
        }
        this.androidDatabase = androidDb;
        this.connection = jdbc;
    }

    private static boolean isAndroidRuntime() {
        try {
            Class.forName("android.database.sqlite.SQLiteDatabase");
            return true;
        } catch (ClassNotFoundException ignored) {
            return false;
        }
    }

    private static Object openAndroidDatabase(Path file) throws Exception {
        Class<?> databaseClass = Class.forName("android.database.sqlite.SQLiteDatabase");
        Class<?> cursorFactory = Class.forName("android.database.sqlite.SQLiteDatabase$CursorFactory");
        Method open = databaseClass.getMethod("openDatabase", String.class, cursorFactory, int.class);
        return open.invoke(null, file.toString(), null, 1 /* OPEN_READONLY */);
    }

    private static Object androidQuery(Object database, String sql, String[] args) throws Exception {
        return database.getClass().getMethod("rawQuery", String.class, String[].class)
                .invoke(database, sql, args);
    }

    private static void validateAndroidDatabase(Object database) throws Exception {
        Object cursor = androidQuery(database, "PRAGMA integrity_check", null);
        try {
            boolean first = (boolean) cursor.getClass().getMethod("moveToFirst").invoke(cursor);
            if (!first || !"ok".equalsIgnoreCase((String) cursor.getClass().getMethod("getString", int.class).invoke(cursor, 0)))
                throw new IOException("SQLite integrity check failed");
        } finally {
            cursor.getClass().getMethod("close").invoke(cursor);
        }
        cursor = androidQuery(database, "SELECT COUNT(*) FROM FullData WHERE DetailLevel=0 AND DataFormatVersion=2 AND CompressionMode=4", null);
        try {
            if (!(boolean) cursor.getClass().getMethod("moveToFirst").invoke(cursor)
                    || ((Number) cursor.getClass().getMethod("getInt", int.class).invoke(cursor, 0)).intValue() == 0)
                throw new IOException("No Overworld FullData V2 rows with Zstandard compression found");
        } finally {
            cursor.getClass().getMethod("close").invoke(cursor);
        }
    }

    private static void validateJdbcDatabase(Connection connection) throws Exception {
        try (Statement s = connection.createStatement(); ResultSet rs = s.executeQuery("PRAGMA integrity_check")) {
            if (!rs.next() || !"ok".equalsIgnoreCase(rs.getString(1))) throw new IOException("SQLite integrity check failed");
        }
        try (Statement s = connection.createStatement(); ResultSet rs = s.executeQuery(
                "SELECT COUNT(*) FROM FullData WHERE DetailLevel=0 AND DataFormatVersion=2 AND CompressionMode=4")) {
            rs.next();
            if (rs.getInt(1) == 0) throw new IOException("No Overworld FullData V2 rows with Zstandard compression found");
        }
    }

    public List<int[]> listRows() throws Exception {
        List<int[]> rows = new ArrayList<>();
        String sql = "SELECT PosX, PosZ FROM FullData WHERE DetailLevel=0 AND DataFormatVersion=2 AND CompressionMode=4 ORDER BY (PosX*PosX+PosZ*PosZ)";
        if (androidDatabase != null) {
            Object cursor = androidQuery(androidDatabase, sql, null);
            try {
                while ((boolean) cursor.getClass().getMethod("moveToNext").invoke(cursor)) {
                    int x = ((Number) cursor.getClass().getMethod("getInt", int.class).invoke(cursor, 0)).intValue();
                    int z = ((Number) cursor.getClass().getMethod("getInt", int.class).invoke(cursor, 1)).intValue();
                    rows.add(new int[]{x, z});
                }
            } finally {
                cursor.getClass().getMethod("close").invoke(cursor);
            }
        } else {
            try (PreparedStatement ps = connection.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
                while (rs.next()) rows.add(new int[]{rs.getInt(1), rs.getInt(2)});
            }
        }
        return rows;
    }

    public Row readRow(int x, int z) throws Exception {
        String sql = "SELECT Data,NorthAdjData,SouthAdjData,EastAdjData,WestAdjData,Mapping " +
                "FROM FullData WHERE DetailLevel=0 AND PosX=? AND PosZ=? AND DataFormatVersion=2 AND CompressionMode=4";
        if (androidDatabase != null) {
            Object cursor = androidQuery(androidDatabase, sql, new String[]{Integer.toString(x), Integer.toString(z)});
            try {
                if (!(boolean) cursor.getClass().getMethod("moveToFirst").invoke(cursor))
                    throw new IOException("Missing DH V2 row at " + x + "," + z);
                Method getBlob = cursor.getClass().getMethod("getBlob", int.class);
                return new Row(x,z,(byte[])getBlob.invoke(cursor,0),(byte[])getBlob.invoke(cursor,1),
                        (byte[])getBlob.invoke(cursor,2),(byte[])getBlob.invoke(cursor,3),
                        (byte[])getBlob.invoke(cursor,4),(byte[])getBlob.invoke(cursor,5));
            } finally {
                cursor.getClass().getMethod("close").invoke(cursor);
            }
        }
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, x); ps.setInt(2, z);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new IOException("Missing DH V2 row at " + x + "," + z);
                return new Row(x,z,rs.getBytes(1),rs.getBytes(2),rs.getBytes(3),rs.getBytes(4),rs.getBytes(5),rs.getBytes(6));
            }
        }
    }

    @Override public void close() throws Exception {
        if (androidDatabase != null) androidDatabase.getClass().getMethod("close").invoke(androidDatabase);
        else if (connection != null) connection.close();
    }
}
