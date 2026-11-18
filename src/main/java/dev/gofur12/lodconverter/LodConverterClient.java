package dev.gofur12.lodconverter;

import com.mojang.brigadier.arguments.StringArgumentType;
import dev.gofur12.lodconverter.dh.DhOverworldImporter;
import dev.gofur12.lodconverter.inspect.DhDatabaseInspector;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.text.Text;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Path;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public final class LodConverterClient implements ClientModInitializer {
    private static KeyBinding openScreenKey;

    @Override
    public void onInitializeClient() {
        openScreenKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.voxy_mobile_lod_converter.open",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_K,
            "category.voxy_mobile_lod_converter"
        ));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openScreenKey.wasPressed()) {
                client.setScreen(new DhConverterScreen(client.currentScreen));
            }
        });

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
            dispatcher.register(literal("dhconvert")
                .then(literal("inspect")
                    .then(argument("database", StringArgumentType.greedyString())
                        .executes(context -> {
                            String rawPath = StringArgumentType.getString(context, "database");
                            try {
                                var report = DhDatabaseInspector.inspect(Path.of(rawPath));
                                context.getSource().sendFeedback(Text.literal(report.toChatText()));
                                return 1;
                            } catch (Exception exception) {
                                context.getSource().sendError(Text.literal(
                                    "DH database inspection failed: " + exception.getMessage()));
                                return 0;
                            }
                        })))
                .then(literal("import")
                    .then(argument("database", StringArgumentType.greedyString())
                        .executes(context -> {
                            String rawPath = StringArgumentType.getString(context, "database");
                            try {
                                DhOverworldImporter.start(Path.of(rawPath));
                                context.getSource().sendFeedback(Text.literal(
                                    "DH V2 Overworld import started. Keep the world open until it finishes."));
                                return 1;
                            } catch (Exception exception) {
                                context.getSource().sendError(Text.literal(
                                    "Could not start DH import: " + exception.getMessage()));
                                return 0;
                            }
                        })))
            )
        );
    }
}
