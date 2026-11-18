package dev.gofur12.lodconverter.voxy;

import net.minecraft.client.MinecraftClient;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.state.property.Property;
import net.minecraft.world.biome.Biome;
import net.minecraft.registry.entry.RegistryEntry;

import java.lang.reflect.*;
import java.util.*;

/** Runtime-only reflective bridge. No Voxy source or jar is bundled or modified. */
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
        Method factory = Arrays.stream(identifier.getMethods())
                .filter(m -> Modifier.isStatic(m.getModifiers()) && m.getName().equals("ofEngine") && m.getParameterCount() == 1)
                .filter(m -> m.getParameterTypes()[0].isAssignableFrom(world.getClass()))
                .findFirst().orElseThrow(() -> new NoSuchMethodException("Voxy WorldIdentifier.ofEngine(Level)"));
        Object engine = factory.invoke(null, world);
        if (engine == null) throw new IllegalStateException("Voxy has no active engine for this world");
        return new VoxyBridge(engine);
    }

    public int blockId(String serial) throws Exception {
        BlockState state = resolveBlockState(serial);
        Method method = Arrays.stream(mapper.getClass().getMethods())
                .filter(m -> m.getName().equals("getIdForBlockState") && m.getParameterCount() == 1)
                .findFirst().orElseThrow(() -> new NoSuchMethodException("Voxy Mapper.getIdForBlockState"));
        return ((Number) method.invoke(mapper, state)).intValue();
    }

    public int biomeId(String biomeName) throws Exception {
        Identifier id = Identifier.tryParse(biomeName);
        Biome biome = id == null ? null : Registries.BIOME.get(id);
        if (biome == null) biome = Registries.BIOME.get(new Identifier("minecraft", "plains"));
        RegistryEntry<Biome> entry = Registries.BIOME.getEntry(biome);
        Method method = Arrays.stream(mapper.getClass().getMethods())
                .filter(m -> m.getName().equals("getIdForBiome") && m.getParameterCount() == 1)
                .findFirst().orElseThrow(() -> new NoSuchMethodException("Voxy Mapper.getIdForBiome"));
        return ((Number) method.invoke(mapper, entry)).intValue();
    }

    private static BlockState resolveBlockState(String serial) {
        if (serial.equals("AIR") || serial.equals("minecraft:air") || serial.equals("minecraft:cave_air") || serial.equals("minecraft:void_air"))
            return net.minecraft.block.Blocks.AIR.getDefaultState();
        int marker = serial.indexOf("_STATE_");
        String blockName = marker < 0 ? serial : serial.substring(0, marker);
        Identifier blockIdentifier = Identifier.tryParse(blockName);
        if (blockIdentifier == null) return net.minecraft.block.Blocks.AIR.getDefaultState();
        Block block = Registries.BLOCK.get(blockIdentifier);
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
        // WorldUpdater.insertUpdate consumes/copies the section synchronously on the
        // calling thread. Its async save queue retains Voxy WorldSections, not this
        // input buffer, so this scratch section is safe to reuse after the call returns.
        System.arraycopy(data, 0, sectionData, 0, Math.min(data.length, sectionData.length));
        voxelizedSection.getField("lvl0NonAirCount").setInt(section, nonAir);
        voxelizedSection.getMethod("setPosition", int.class, int.class, int.class).invoke(section, sectionX, sectionY, sectionZ);
        invokeStatic(mipper, "mipSection", section, mapper);
        invokeStatic(updater, "insertUpdate", engine, section);
    }

    private static Object invokeStatic(Class<?> owner, String name, Object... args) throws Exception {
        Method method = Arrays.stream(owner.getMethods())
                .filter(m -> Modifier.isStatic(m.getModifiers()) && m.getName().equals(name) && m.getParameterCount() == args.length)
                .filter(m -> {
                    Class<?>[] p = m.getParameterTypes();
                    for (int i = 0; i < p.length; i++) if (!p[i].isAssignableFrom(args[i].getClass())) return false;
                    return true;
                }).findFirst().orElseThrow(() -> new NoSuchMethodException(owner.getName() + "." + name));
        return method.invoke(null, args);
    }

    public void awaitCapacity() throws Exception {
        Object instance = engine.getClass().getField("instanceIn").get(engine);
        if (instance == null) return;
        java.util.function.BooleanSupplier limiter = (java.util.function.BooleanSupplier)
                instance.getClass().getField("savingServiceRateLimiter").get(instance);
        long deadline = System.nanoTime() + 120_000_000_000L;
        while (!limiter.getAsBoolean()) {
            if (System.nanoTime() > deadline) throw new IllegalStateException("Voxy save queue did not drain within 120 seconds");
            Thread.sleep(10L);
        }
    }

    public void acquireRef() throws Exception {
        engine.getClass().getMethod("acquireRef").invoke(engine);
    }

    public void releaseRef() throws Exception {
        engine.getClass().getMethod("releaseRef").invoke(engine);
    }

    public static long mapping(int light, int blockId, int biomeId) {
        if (blockId == 0) return ((long) light & 0xffL) << 56;
        return ((long) light & 0xffL) << 56 | ((long) biomeId & 0x1ffL) << 47 | ((long) blockId & 0xfffffL) << 27;
    }
}
