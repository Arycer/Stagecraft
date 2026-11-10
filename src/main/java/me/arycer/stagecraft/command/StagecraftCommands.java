package me.arycer.stagecraft.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import me.arycer.stagecraft.placement.Origin;
import me.arycer.stagecraft.script.Failures;
import me.arycer.stagecraft.script.ScenarioArgs;
import me.arycer.stagecraft.script.ScenarioRunner;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;
import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.IdentifierArgumentType;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.Map;

/** The local {@code /stagecraft} command: list, run, status and cancel. */
public final class StagecraftCommands {
    private static final String USAGE = "/stagecraft list | run <name> [[in <dimension>] at <x> <y> <z> [facing <yaw> <pitch>]] [with key=value …] | status | cancel";

    private final ScenarioRunner runner;

    public StagecraftCommands(ScenarioRunner runner) {
        this.runner = runner;
    }

    public void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        var scenario = withArgs(ClientCommandManager.argument("name", StringArgumentType.word())
                .suggests((context, builder) -> CommandSource.suggestMatching(runner.scenarios(), builder)),
                context -> run(context, currentOrigin()))
                .then(ClientCommandManager.literal("at").then(position(false)))
                .then(ClientCommandManager.literal("in").then(ClientCommandManager.argument("dimension", IdentifierArgumentType.identifier())
                        .then(ClientCommandManager.literal("at").then(position(true)))));
        dispatcher.register(ClientCommandManager.literal("stagecraft")
                .executes(context -> {
                    context.getSource().sendFeedback(Text.literal(USAGE));
                    return 1;
                })
                .then(ClientCommandManager.literal("status").executes(context -> {
                    context.getSource().sendFeedback(Text.literal(runner.status()));
                    return 1;
                }))
                .then(ClientCommandManager.literal("cancel").executes(context -> {
                    runner.cancel();
                    return 1;
                }))
                .then(ClientCommandManager.literal("list").executes(context -> {
                    context.getSource().sendFeedback(Text.literal(String.join(", ", runner.scenarios())));
                    return 1;
                }))
                .then(ClientCommandManager.literal("run").then(scenario)));
    }

    /** The node runs the scenario, and so does the node followed by {@code with <key=value …>}. */
    private static <T extends ArgumentBuilder<FabricClientCommandSource, T>> T withArgs(T node, Command<FabricClientCommandSource> command) {
        return node.executes(command)
                .then(ClientCommandManager.literal("with").then(ClientCommandManager.argument("args", StringArgumentType.greedyString()).executes(command)));
    }

    private RequiredArgumentBuilder<FabricClientCommandSource, Double> position(boolean dimension) {
        var z = withArgs(ClientCommandManager.argument("z", DoubleArgumentType.doubleArg()), context -> runAt(context, dimension, false))
                .then(ClientCommandManager.literal("facing").then(ClientCommandManager.argument("yaw", FloatArgumentType.floatArg())
                        .then(withArgs(ClientCommandManager.argument("pitch", FloatArgumentType.floatArg(-90, 90)),
                                context -> runAt(context, dimension, true)))));
        return ClientCommandManager.argument("x", DoubleArgumentType.doubleArg())
                .then(ClientCommandManager.argument("y", DoubleArgumentType.doubleArg()).then(z));
    }

    private int runAt(CommandContext<FabricClientCommandSource> context, boolean dimension, boolean facing) {
        Origin current = currentOrigin();
        String world = dimension ? context.getArgument("dimension", Identifier.class).toString() : current.dimension();
        return run(context, new Origin(world,
                DoubleArgumentType.getDouble(context, "x"), DoubleArgumentType.getDouble(context, "y"), DoubleArgumentType.getDouble(context, "z"),
                facing ? FloatArgumentType.getFloat(context, "yaw") : current.yaw(),
                facing ? FloatArgumentType.getFloat(context, "pitch") : current.pitch()));
    }

    private int run(CommandContext<FabricClientCommandSource> context, Origin origin) {
        try {
            runner.start(StringArgumentType.getString(context, "name"), origin, args(context));
            return 1;
        } catch (Exception error) {
            context.getSource().sendError(Text.literal("[Stagecraft] " + Failures.describe(error, 0)));
            return 0;
        }
    }

    private static Map<String, String> args(CommandContext<FabricClientCommandSource> context) {
        boolean given = context.getNodes().stream().anyMatch(node -> node.getNode().getName().equals("args"));
        return given ? ScenarioArgs.parse(StringArgumentType.getString(context, "args")) : Map.of();
    }

    private static Origin currentOrigin() {
        MinecraftClient client = MinecraftClient.getInstance();
        var player = client.player;
        return new Origin(client.world.getRegistryKey().getValue().toString(), player.getX(), player.getY(), player.getZ(), player.getYaw(), player.getPitch());
    }
}
