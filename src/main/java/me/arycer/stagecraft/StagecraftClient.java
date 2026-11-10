package me.arycer.stagecraft;

import me.arycer.stagecraft.chat.TextKeys;
import me.arycer.stagecraft.command.StagecraftCommands;
import me.arycer.stagecraft.examples.ExampleInstaller;
import me.arycer.stagecraft.script.ScenarioRunner;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.MinecraftClient;

import java.util.List;

/** Entrypoint: creates the runner, installs the examples and wires the client events and the command. */
public final class StagecraftClient implements ClientModInitializer {
    private static final List<String> EXAMPLES = List.of("platform", "fortress", "smoke");

    @Override public void onInitializeClient() {
        MinecraftClient client = MinecraftClient.getInstance();
        ScenarioRunner runner;
        try {
            runner = new ScenarioRunner(client, client.runDirectory.toPath().resolve("config/stagecraft/scripts"));
            var outcome = ExampleInstaller.install(runner.scripts(), EXAMPLES,
                    name -> StagecraftClient.class.getResourceAsStream("/stagecraft/examples/" + name + ".lua"));
            if (!outcome.updated().isEmpty()) System.out.println("[Stagecraft] Updated examples: " + String.join(", ", outcome.updated()));
            if (!outcome.kept().isEmpty()) System.out.println("[Stagecraft] Edited examples kept; new versions in <name>.lua.new: " + String.join(", ", outcome.kept()));
        } catch (Exception error) {
            throw new IllegalStateException("Cannot create Stagecraft script directory", error);
        }
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> runner.message(message.getString(), TextKeys.of(message)));
        ClientReceiveMessageEvents.CHAT.register((message, signed, sender, params, timestamp) -> runner.message(message.getString(), TextKeys.of(message)));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, disconnected) -> runner.cancel());
        ClientLifecycleEvents.CLIENT_STOPPING.register(stopping -> runner.cancel());
        StagecraftCommands commands = new StagecraftCommands(runner);
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, access) -> commands.register(dispatcher));
    }
}
