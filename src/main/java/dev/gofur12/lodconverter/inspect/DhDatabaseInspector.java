package dev.gofur12.lodconverter.inspect;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/** Read-only structural inspection. This deliberately does not modify the source database. */
public final class DhDatabaseInspector {
    private DhDatabaseInspector() {}

    public static Report inspect(Path input) throws Exception {
        Path file = input.toAbsolutePath().normalize();
        if (!Files.isRegularFile(file)) throw new IllegalArgumentException("File not found: " + file);
        String url = "jdbc:sqlite:file:" + file + "?mode=ro";
        try (Connection connection = DriverManager.getConnection(url)) {
            String integrity;
            try (Statement statement = connection.createStatement();
                 ResultSet result = statement.executeQuery("PRAGMA integrity_check")) {
                integrity = result.next() ? result.getString(1) : "no result";
            }

            List<String> tables = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT name FROM sqlite_master WHERE type='table' ORDER BY name");
                 ResultSet result = statement.executeQuery()) {
                while (result.next()) tables.add(result.getString(1));
            }

            List<String> columns = new ArrayList<>();
            for (String table : tables) {
                if (table.equalsIgnoreCase("FullData")) {
                    try (Statement statement = connection.createStatement();
                         ResultSet result = statement.executeQuery("PRAGMA table_info('FullData')")) {
                        while (result.next()) columns.add(result.getString("name"));
                    }
                }
            }

            return new Report(file.toString(), integrity, tables, columns);
        }
    }

    public record Report(String path, String integrity, List<String> tables, List<String> fullDataColumns) {
        public String toChatText() {
            return "DH database inspected (read-only)\nFile: " + path
                + "\nIntegrity: " + integrity
                + "\nTables: " + String.join(", ", tables)
                + "\nFullData columns: " + String.join(", ", fullDataColumns)
                + "\nThis command only inspects the database; it does not convert or write Voxy data yet.";
        }
    }
}
