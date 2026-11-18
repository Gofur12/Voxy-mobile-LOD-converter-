package dev.gofur12.lodconverter;

import dev.gofur12.lodconverter.dh.DhOverworldImporter;
import dev.gofur12.lodconverter.inspect.DhDatabaseInspector;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/** Simple in-game file browser and one-tap DH database importer. */
public final class DhConverterScreen extends Screen {
    private static final int ROWS_PER_PAGE = 7;
    private final Screen parent;
    private Path directory;
    private Path selectedFile;
    private final List<Path> entries = new ArrayList<>();
    private int page;
    private String status = "Pilih folder yang berisi DistantHorizons.sqlite.";
    private TextFieldWidget pathField;

    public DhConverterScreen(Screen parent) {
        super(Text.literal("Voxy Mobile LOD Converter"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int left = Math.max(12, (width - 360) / 2);
        int top = 42;
        int panelWidth = Math.min(360, width - 24);

        if (directory == null) {
            Path gameDir = client.runDirectory.toPath().toAbsolutePath().normalize();
            Path androidStorage = Path.of("/storage/emulated/0");
            directory = Files.isDirectory(androidStorage) ? androidStorage : gameDir;
        }

        if (entries.isEmpty()) refreshEntries();

        pathField = new TextFieldWidget(textRenderer, left, top, panelWidth, 20, Text.literal("Folder"));
        pathField.setMaxLength(512);
        pathField.setText(directory.toString());
        addDrawableChild(pathField);

        addDrawableChild(ButtonWidget.builder(Text.literal("Buka folder"), button -> {
            Path target = parsePath(pathField.getText());
            if (target != null && Files.isDirectory(target)) {
                directory = target;
                page = 0;
                refreshEntries();
                clearAndInit();
            } else {
                status = "Folder tidak ditemukan atau tidak bisa diakses.";
            }
        }).dimensions(left, top + 25, 110, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Folder induk"), button -> {
            Path parentDir = directory.getParent();
            if (parentDir != null) {
                directory = parentDir;
                page = 0;
                refreshEntries();
                clearAndInit();
            }
        }).dimensions(left + 116, top + 25, 110, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Muat ulang"), button -> {
            refreshEntries();
            status = "Daftar folder diperbarui.";
            clearAndInit();
        }).dimensions(left + 232, top + 25, panelWidth - 232, 20).build());

        int listTop = top + 53;
        int start = page * ROWS_PER_PAGE;
        for (int i = start; i < Math.min(entries.size(), start + ROWS_PER_PAGE); i++) {
            Path entry = entries.get(i);
            String label = Files.isDirectory(entry) ? "[DIR] " + entry.getFileName()
                : "[SQLITE] " + entry.getFileName();
            if (label.length() > 48) label = label.substring(0, 45) + "...";
            final Path chosen = entry;
            addDrawableChild(ButtonWidget.builder(Text.literal(label), button -> {
                if (Files.isDirectory(chosen)) {
                    directory = chosen;
                    page = 0;
                    refreshEntries();
                    clearAndInit();
                } else {
                    selectedFile = chosen;
                    status = "Terpilih: " + chosen.getFileName();
                    clearAndInit();
                }
            }).dimensions(left, listTop + (i - start) * 23, panelWidth, 20).build());
        }

        int footer = listTop + ROWS_PER_PAGE * 23 + 4;
        addDrawableChild(ButtonWidget.builder(Text.literal("Sebelumnya"), button -> {
            page = Math.max(0, page - 1);
            clearAndInit();
        }).dimensions(left, footer, 105, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Berikutnya"), button -> {
            if ((page + 1) * ROWS_PER_PAGE < entries.size()) page++;
            clearAndInit();
        }).dimensions(left + 111, footer, 105, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Pilih database"), button -> inspectSelected())
            .dimensions(left + 222, footer, panelWidth - 222, 20).build());

        int actionTop = footer + 27;
        addDrawableChild(ButtonWidget.builder(Text.literal("Impor ke Voxy"), button -> importSelected())
            .dimensions(left, actionTop, panelWidth, 22).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Tutup"), button -> close())
            .dimensions(left, actionTop + 27, panelWidth, 20).build());

    }

    private void refreshEntries() {
        entries.clear();
        try (Stream<Path> stream = Files.list(directory)) {
            stream.filter(path -> Files.isDirectory(path) || isSqlite(path))
                .sorted(Comparator.comparing((Path p) -> !Files.isDirectory(p))
                    .thenComparing(p -> p.getFileName().toString().toLowerCase(Locale.ROOT)))
                .forEach(entries::add);
            status = entries.size() + " folder/database ditemukan.";
        } catch (IOException | SecurityException exception) {
            status = "Tidak bisa membaca folder: " + exception.getMessage();
        }
        page = Math.min(page, Math.max(0, (entries.size() - 1) / ROWS_PER_PAGE));
    }

    private static boolean isSqlite(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return Files.isRegularFile(path) && (name.endsWith(".sqlite") || name.endsWith(".db"));
    }

    private Path parsePath(String value) {
        try { return Path.of(value).toAbsolutePath().normalize(); }
        catch (RuntimeException exception) { return null; }
    }

    private void inspectSelected() {
        if (selectedFile == null) { status = "Pilih file .sqlite terlebih dahulu."; return; }
        try {
            var report = DhDatabaseInspector.inspect(selectedFile);
            status = "DATABASE VALID — " + report.overworldRows() + " baris Overworld. Format DH V2.";
        } catch (Exception exception) {
            status = "Pemeriksaan gagal: " + exception.getMessage();
        }
    }

    private void importSelected() {
        if (selectedFile == null) { status = "Pilih file .sqlite terlebih dahulu."; return; }
        try {
            DhOverworldImporter.start(selectedFile);
            status = "Impor dimulai. Jangan keluar dari dunia sampai selesai.";
        } catch (Exception exception) {
            status = "Gagal memulai impor: " + exception.getMessage();
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context);
        int left = Math.max(12, (width - 360) / 2);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 15, 0xFFFFFF);
        context.drawTextWithShadow(textRenderer, "Folder: " + directory, left, 32, 0xA0A0A0);
        super.render(context, mouseX, mouseY, delta);
        context.drawTextWithShadow(textRenderer, status, left, height - 14, 0xFFFFFF);
        if (selectedFile != null) {
            context.drawTextWithShadow(textRenderer, "Dipilih: " + selectedFile.getFileName(),
                left, height - 27, 0x80FF80);
        }
    }

    @Override
    public void close() {
        if (client != null) client.setScreen(parent);
    }
}
