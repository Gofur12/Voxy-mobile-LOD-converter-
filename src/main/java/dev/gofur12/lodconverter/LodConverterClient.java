package dev.gofur12.lodconverter;

import com.mojang.brigadier.arguments.StringArgumentType;
import dev.gofur12.lodconverter.inspect.DhDatabaseInspector;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.minecraft.text.Text;

import java.nio.file.Path;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public final class LodConverterClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
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
            )
        );
    }
}
