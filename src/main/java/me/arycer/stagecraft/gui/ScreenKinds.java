package me.arycer.stagecraft.gui;

import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.ConfirmScreen;
import net.minecraft.client.gui.screen.DeathScreen;
import net.minecraft.client.gui.screen.DisconnectedScreen;
import net.minecraft.client.gui.screen.DownloadingTerrainScreen;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.ingame.BookEditScreen;
import net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.screen.ingame.SignEditScreen;
import net.minecraft.client.gui.screen.option.GameOptionsScreen;
import net.minecraft.client.gui.screen.option.OptionsScreen;

import java.util.List;
import java.util.Map;

/**
 * A readable name for vanilla screens. In a production client Minecraft's classes have intermediary names
 * ({@code class_433}), so {@code getSimpleName()} is only readable for mods' screens; {@code instanceof}
 * survives remapping. Most specific first.
 */
public final class ScreenKinds {
    private static final List<Map.Entry<Class<? extends Screen>, String>> KINDS = List.of(
            Map.entry(GameMenuScreen.class, "GameMenuScreen"),
            Map.entry(ChatScreen.class, "ChatScreen"),
            Map.entry(OptionsScreen.class, "OptionsScreen"),
            Map.entry(GameOptionsScreen.class, "GameOptionsScreen"),
            Map.entry(CreativeInventoryScreen.class, "CreativeInventoryScreen"),
            Map.entry(InventoryScreen.class, "InventoryScreen"),
            Map.entry(HandledScreen.class, "HandledScreen"),
            Map.entry(SignEditScreen.class, "SignEditScreen"),
            Map.entry(BookEditScreen.class, "BookEditScreen"),
            Map.entry(DeathScreen.class, "DeathScreen"),
            Map.entry(ConfirmScreen.class, "ConfirmScreen"),
            Map.entry(DisconnectedScreen.class, "DisconnectedScreen"),
            Map.entry(DownloadingTerrainScreen.class, "DownloadingTerrainScreen"),
            Map.entry(TitleScreen.class, "TitleScreen"));

    private ScreenKinds() {}

    /** The vanilla kind, or the class's simple name for anything else (mods keep their names). */
    public static String of(Screen screen) {
        for (var kind : KINDS) if (kind.getKey().isInstance(screen)) return kind.getValue();
        return screen.getClass().getSimpleName();
    }
}
