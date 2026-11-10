package me.arycer.stagecraft;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.command.argument.IdentifierArgumentType;
import net.minecraft.command.CommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.nio.file.Files;
import java.io.InputStream;

public final class StagecraftClient implements ClientModInitializer {
    private ScenarioRunner runner;

    @Override public void onInitializeClient() {
        MinecraftClient client = MinecraftClient.getInstance();
        try {
            runner = new ScenarioRunner(client, client.runDirectory.toPath().resolve("config/stagecraft/scripts"));
            for (String name : new String[]{"platform", "fortress", "smoke"}) {
                var target = runner.scripts().resolve(name + ".lua");
                if (!Files.exists(target)) {
                    try (InputStream source = getClass().getResourceAsStream("/stagecraft/examples/" + name + ".lua")) {
                        if (source != null) Files.copy(source, target);
                    }
                }
            }
        }
        catch (Exception error) { throw new IllegalStateException("Cannot create Stagecraft script directory", error); }
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> runner.message(message.getString()));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, disconnected) -> runner.cancel());
        ClientLifecycleEvents.CLIENT_STOPPING.register(stopping -> runner.cancel());
        ClientReceiveMessageEvents.CHAT.register((message, signed, sender, params, timestamp) -> runner.message(message.getString()));
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, access) -> {
            var scenario = ClientCommandManager.argument("name", StringArgumentType.word())
                    .suggests((context, builder) -> CommandSource.suggestMatching(runner.scenarios(), builder))
                    .executes(context -> run(StringArgumentType.getString(context, "name"), currentOrigin(client)))
                    .then(ClientCommandManager.literal("at").then(position(false)))
                    .then(ClientCommandManager.literal("in").then(ClientCommandManager.argument("dimension", IdentifierArgumentType.identifier())
                            .then(ClientCommandManager.literal("at").then(position(true)))));
            dispatcher.register(
                ClientCommandManager.literal("stagecraft")
                        .executes(context -> {
                            context.getSource().sendFeedback(Text.literal("/stagecraft list | run <name> [in <dimension>] at <x> <y> <z> [facing <yaw> <pitch>] | status | cancel"));
                            return 1;
                        })
                        .then(ClientCommandManager.literal("status").executes(context -> {
                            context.getSource().sendFeedback(Text.literal(runner.status())); return 1;
                        }))
                        .then(ClientCommandManager.literal("cancel").executes(context -> { runner.cancel(); return 1; }))
                        .then(ClientCommandManager.literal("list").executes(context -> {
                            try (var files = Files.list(runner.scripts())) {
                                context.getSource().sendFeedback(Text.literal(String.join(", ", files
                                        .map(p -> p.getFileName().toString()).filter(n -> n.endsWith(".lua")).sorted().toList())));
                            } catch (Exception error) { context.getSource().sendError(Text.literal(error.getMessage())); }
                            return 1;
                        }))
                        .then(ClientCommandManager.literal("run").then(scenario)));
        });
    }

    private RequiredArgumentBuilder<FabricClientCommandSource, Double> position(boolean dimension) {
        var z = ClientCommandManager.argument("z", DoubleArgumentType.doubleArg())
                .executes(context -> runAt(context, dimension, false))
                .then(ClientCommandManager.literal("facing").then(ClientCommandManager.argument("yaw", FloatArgumentType.floatArg())
                        .then(ClientCommandManager.argument("pitch", FloatArgumentType.floatArg(-90, 90))
                                .executes(context -> runAt(context, dimension, true)))));
        return ClientCommandManager.argument("x", DoubleArgumentType.doubleArg())
                .then(ClientCommandManager.argument("y", DoubleArgumentType.doubleArg()).then(z));
    }

    private int runAt(CommandContext<FabricClientCommandSource> context, boolean dimension, boolean facing) {
        Origin current = currentOrigin(MinecraftClient.getInstance());
        String world = dimension ? context.getArgument("dimension", Identifier.class).toString() : current.dimension();
        if (Identifier.tryParse(world) == null) {
            context.getSource().sendError(Text.literal("Invalid dimension: " + world));
            return 0;
        }
        return run(StringArgumentType.getString(context, "name"), new Origin(world,
                DoubleArgumentType.getDouble(context, "x"), DoubleArgumentType.getDouble(context, "y"), DoubleArgumentType.getDouble(context, "z"),
                facing ? FloatArgumentType.getFloat(context, "yaw") : current.yaw(),
                facing ? FloatArgumentType.getFloat(context, "pitch") : current.pitch()));
    }

    private int run(String name, Origin origin) {
        try { runner.start(name, origin); return 1; }
        catch (Exception error) {
            MinecraftClient.getInstance().player.sendMessage(Text.literal("[Stagecraft] " + error.getMessage()), false);
            return 0;
        }
    }
    private Origin currentOrigin(MinecraftClient client) {
        var player = client.player;
        return new Origin(client.world.getRegistryKey().getValue().toString(), player.getX(), player.getY(), player.getZ(), player.getYaw(), player.getPitch());
    }
}
