package dev.gofur12.lodconverter.voxy;

import net.minecraft.client.MinecraftClient;
import net.minecraft.registry.Registry;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.state.property.Property;
import net.minecraft.world.biome.Biome;
import net.minecraft.registry.entry.RegistryEntry;

import java.lang.reflect.*;
import java.util.*;

/**
 * Runtime-only bridge to Voxy. It does not compile against, copy, patch, or modify Voxy classes.
 * The bridge resolves the running world's Voxy engine and calls its section update pipeline.
 */
public final class VoxyBridge {
    private final Object engine;
    private final Object mapper;
    private final Class<?> voxelizedSection;
    private final Class<?> updater;
    private final Class<?> mipper;
    private final Object section;
    private final long[] sectionData;

    private VoxyBridge(Object engine) throws Exception {
        this.engine = engine;
        this.mapper = engine.getClass().getMethod("getMapper").invoke(engine);
        this.voxelizedSection = Class.forName("me.cortex.voxy.common.voxelization.VoxelizedSection");
        this.updater = Class.forName("me.cortex.voxy.common.world.WorldUpdater");
        this.mipper = Class.forName("me.cortex.voxy.common.voxelization.WorldVoxilizedSectionMipper");
        this.section = voxelizedSection.getMethod("createEmpty").invoke(null);
        this.sectionData = (long[]) voxelizedSection.getField("section").get(section);
    }

    public static VoxyBridge currentWorld() throws Exception {
        var world = MinecraftClient.getInstance().world;
        if (world == null) throw new IllegalStateException("Join a world first");
        Class<?> identifier = Class.forName("me.cortex.voxy.commonImpl.WorldIdentifier");
        Object engine = identifier.getMethod("ofEngine", net.minecraft.world.World.class).invoke(null, world);
        if (engine == null) throw new IllegalStateException("Voxy has no active engine for this world");
        return new VoxyBridge(engine);
    }

    public int blockId(String serial) throws Exception {
        BlockState state = resolveBlockState(serial);
        return (int) mapper.getClass().getMethod("getIdForBlockState", BlockState.class).invoke(mapper, state);
    }

    public int biomeId(String biomeName) throws Exception {
        Identifier id = Identifier.tryParse(biomeName);
        RegistryEntry<Biome> biome = Registries.BIOME.getEntry(Registries.BIOME.get(id)
                .orElse(Registries.BIOME.get(Identifier.ofVanilla("plains"))));
        return (int) mapper.getClass().getMethod("getIdForBiome", RegistryEntry.class).invoke(mapper, biome);
    }

    private static BlockState resolveBlockState(String serial) {
        if (serial.equals("AIR") || serial.equals("minecraft:air") || serial.equals("minecraft:cave_air") || serial.equals("minecraft:void_air"))
            return net.minecraft.block.Blocks.AIR.getDefaultState();
        int marker = serial.indexOf("_STATE_");
        String blockName = marker < 0 ? serial : serial.substring(0, marker);
        Block block = Registries.BLOCK.get(Identifier.tryParse(blockName));
        BlockState state = block.getDefaultState();
        if (marker < 0) return state;
        String tail = serial.substring(marker + 7);
        int pos = 0;
        while (pos < tail.length()) {
            if (tail.charAt(pos) != '{') throw new IllegalArgumentException("Malformed block-state serial: " + serial);
            int end = tail.indexOf('}', pos);
            if (end < 0) throw new IllegalArgumentException("Malformed block-state serial: " + serial);
            String[] pair = tail.substring(pos + 1, end).split(":", 2);
            if (pair.length != 2) throw new IllegalArgumentException("Malformed property: " + serial);
            Property<?> property = block.getStateManager().getProperty(pair[0]);
            if (property != null) state = applyProperty(state, property, pair[1]);
            pos = end + 1;
        }
        return state;
    }

    private static <T extends Comparable<T>> BlockState applyProperty(BlockState state, Property<T> property, String value) {
        Optional<T> parsed = property.parse(value);
        return parsed.map(t -> state.with(property, t)).orElse(state);
    }

    public void insert(int sectionX, int sectionY, int sectionZ, long[] data, int nonAir) throws Exception {
        System.arraycopy(data, 0, sectionData, 0, Math.min(data.length, sectionData.length));
        voxelizedSection.getField("lvl0NonAirCount").setInt(section, nonAir);
        voxelizedSection.getMethod("setPosition", int.class, int.class, int.class).invoke(section, sectionX, sectionY, sectionZ);
        mipper.getMethod("mipSection", voxelizedSection, mapper.getClass()).invoke(null, section, mapper);
        updater.getMethod("insertUpdate", engine.getClass(), voxelizedSection).invoke(null, engine, section);
        Arrays.fill(sectionData, 0L);
    }

    public static long mapping(long light, int blockId, int biomeId) {
        return (light & 0xffL) << 56 | ((long) biomeId & 0x1ffL) << 47 | ((long) blockId & 0xfffffL) << 27;
    }
}
