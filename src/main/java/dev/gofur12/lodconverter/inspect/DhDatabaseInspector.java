package dev.gofur12.lodconverter.inspect;

import dev.gofur12.lodconverter.dh.DhV2Database;
import java.nio.file.Files;
import java.nio.file.Path;

/** Read-only structural inspection using the same cross-platform SQLite layer as import. */
public final class DhDatabaseInspector {
    private DhDatabaseInspector() {}

    public static Report inspect(Path input) throws Exception {
        Path file = input.toAbsolutePath().normalize();
        if (!Files.isRegularFile(file)) throw new IllegalArgumentException("File not found: " + file);
        try (DhV2Database database = new DhV2Database(file)) {
            int rows = database.listRows().size();
            return new Report(file.toString(), "ok", rows,
                    "FullData V2 / Zstandard / DetailLevel 0");
        }
    }

    public record Report(String path, String integrity, int overworldRows, String format) {
        public String toChatText() {
            return "DH database inspected (read-only)\nFile: " + path
                + "\nIntegrity: " + integrity
                + "\nFormat: " + format
                + "\nOverworld rows: " + overworldRows
                + "\nThis command only inspects the database; it does not modify DH or Voxy data.";
        }
    }
}
