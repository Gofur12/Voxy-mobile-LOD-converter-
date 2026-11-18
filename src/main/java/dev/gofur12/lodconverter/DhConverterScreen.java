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

/** Compact, scrollable in-game DH database browser. */
public final class DhConverterScreen extends Screen {
    private static final int ROW_HEIGHT = 21;
    private final Screen parent;
    private Path directory;
    private Path selectedFile;
    private final List<Path> entries = new ArrayList<>();
    private int scrollOffset;
    private int visibleRows = 1;
    private String status = "Pilih database DistantHorizons.sqlite.";
    private TextFieldWidget pathField;

    public DhConverterScreen(Screen parent) {
        super(Text.literal("Voxy Mobile LOD Converter"));
        this.parent = parent;
    }

    private int panelWidth() { return Math.max(160, Math.min(340, width - 16)); }
    private int left() { return (width - panelWidth()) / 2; }
    private int listTop() { return 82; }
    private int navY() { return Math.max(listTop() + ROW_HEIGHT, height - 55); }

    @Override
    protected void init() {
        int x = left(), w = panelWidth();
        if (directory == null) directory = client.runDirectory.toPath().toAbsolutePath().normalize();
        if (entries.isEmpty()) refreshEntries();

        pathField = new TextFieldWidget(textRenderer, x, 36, w, 18, Text.literal("Folder"));
        pathField.setMaxLength(512);
        pathField.setText(directory.toString());
        addDrawableChild(pathField);

        int gap = 4, third = (w - gap * 2) / 3;
        addDrawableChild(ButtonWidget.builder(Text.literal("Buka"), b -> {
            Path target = parsePath(pathField.getText());
            if (target != null && Files.isDirectory(target)) {
                directory = target; scrollOffset = 0; refreshEntries(); clearAndInit();
            } else status = "Folder tidak ditemukan / tidak bisa diakses.";
        }).dimensions(x, 58, third, 18).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Induk"), b -> {
            Path p = directory.getParent();
            if (p != null) { directory = p; scrollOffset = 0; refreshEntries(); clearAndInit(); }
        }).dimensions(x + third + gap, 58, third, 18).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Muat ulang"), b -> {
            refreshEntries(); status = "Daftar diperbarui."; clearAndInit();
        }).dimensions(x + (third + gap) * 2, 58, w - (third + gap) * 2, 18).build());

        visibleRows = Math.max(1, (navY() - listTop() - 3) / ROW_HEIGHT);
        int end = Math.min(entries.size(), scrollOffset + visibleRows);
        for (int i = scrollOffset; i < end; i++) {
            Path entry = entries.get(i);
            boolean dir = Files.isDirectory(entry);
            String label = (dir ? "[DIR] " : "[DB] ") + entry.getFileName();
            int maxChars = Math.max(12, (w - 12) / 6);
            if (label.length() > maxChars) label = label.substring(0, maxChars - 3) + "...";
            final Path chosen = entry;
            addDrawableChild(ButtonWidget.builder(Text.literal(label), b -> {
                if (Files.isDirectory(chosen)) {
                    directory = chosen; scrollOffset = 0; refreshEntries(); clearAndInit();
                } else {
                    selectedFile = chosen;
                    status = "Dipilih: " + chosen.getFileName();
                    clearAndInit();
                }
            }).dimensions(x, listTop() + (i - scrollOffset) * ROW_HEIGHT, w, 19).build());
        }

        int navWidth = (w - 8) / 3;
        addDrawableChild(ButtonWidget.builder(Text.literal("▲"), b -> {
            scrollOffset = Math.max(0, scrollOffset - visibleRows); clearAndInit();
        }).dimensions(x, navY(), navWidth, 18).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("▼"), b -> {
            scrollOffset = Math.min(maxScroll(), scrollOffset + visibleRows); clearAndInit();
        }).dimensions(x + navWidth + 4, navY(), navWidth, 18).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Pilih DB"), b -> inspectSelected())
            .dimensions(x + (navWidth + 4) * 2, navY(), w - (navWidth + 4) * 2, 18).build());

        int actionY = height - 32;
        addDrawableChild(ButtonWidget.builder(Text.literal("IMPOR KE VOXY"), b -> importSelected())
            .dimensions(x, actionY, w, 20).build());
    }

    private int maxScroll() { return Math.max(0, entries.size() - visibleRows); }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (mouseY >= listTop() - 2 && mouseY <= navY()) {
            int next = Math.max(0, Math.min(maxScroll(), scrollOffset - (int) Math.signum(amount) * 2));
            if (next != scrollOffset) { scrollOffset = next; clearAndInit(); }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, amount);
    }

    private void refreshEntries() {
        entries.clear();
        try (Stream<Path> stream = Files.list(directory)) {
            stream.filter(p -> Files.isDirectory(p) || isSqlite(p))
                .sorted(Comparator.comparing((Path p) -> !Files.isDirectory(p))
                    .thenComparing(p -> p.getFileName().toString().toLowerCase(Locale.ROOT)))
                .forEach(entries::add);
            status = entries.size() + " folder/database.";
        } catch (IOException | SecurityException e) {
            status = "Tidak bisa membaca folder: " + e.getMessage();
        }
        scrollOffset = Math.min(scrollOffset, Math.max(0, entries.size() - 1));
    }

    private static boolean isSqlite(Path p) {
        String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
        return Files.isRegularFile(p) && (n.endsWith(".sqlite") || n.endsWith(".db"));
    }

    private Path parsePath(String value) {
        try { return Path.of(value).toAbsolutePath().normalize(); }
        catch (RuntimeException e) { return null; }
    }

    private void inspectSelected() {
        if (selectedFile == null) { status = "Pilih file database dahulu."; return; }
        try {
            var report = DhDatabaseInspector.inspect(selectedFile);
            status = "VALID: " + report.overworldRows() + " baris Overworld (DH V2).";
        } catch (Exception e) { status = "Pemeriksaan gagal: " + e.getMessage(); }
    }

    private void importSelected() {
        if (selectedFile == null) { status = "Pilih file database dahulu."; return; }
        try {
            DhOverworldImporter.start(selectedFile);
            status = "Impor dimulai. Jangan keluar dari dunia.";
        } catch (Exception e) { status = "Gagal memulai: " + e.getMessage(); }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context);
        int x = left();
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 8, 0xFFFFFF);
        String folder = "Folder: " + directory;
        int max = Math.max(10, (panelWidth() - 8) / 6);
        if (folder.length() > max) folder = "… " + folder.substring(Math.max(0, folder.length() - max + 2));
        context.drawTextWithShadow(textRenderer, folder, x, 25, 0xB0B0B0);
        super.render(context, mouseX, mouseY, delta);
        context.drawTextWithShadow(textRenderer, status, x, height - 10, 0xFFFFFF);
        if (selectedFile != null) {
            String selected = "DB: " + selectedFile.getFileName();
            context.drawTextWithShadow(textRenderer, selected, x, navY() - 11, 0x80FF80);
        }
        if (entries.size() > visibleRows) {
            String count = (scrollOffset + 1) + "-" + Math.min(entries.size(), scrollOffset + visibleRows) + "/" + entries.size();
            context.drawTextWithShadow(textRenderer, count, x + panelWidth() - textRenderer.getWidth(count), navY() - 11, 0xB0B0B0);
        }
    }

    @Override
    public void close() {
        if (client != null) client.setScreen(parent);
    }
}
