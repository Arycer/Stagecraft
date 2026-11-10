package me.arycer.stagecraft.script.api;

import me.arycer.stagecraft.script.ScriptContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

import static me.arycer.stagecraft.script.api.StageModule.function;

/** {@code player}, {@code inventory} and {@code fly}. */
public final class PlayerModule implements StageModule {
    @Override
    public void install(LuaTable stage, ScriptContext context) {
        stage.set("player", function(a -> context.onClientGet(() -> player(context.client()))));
        stage.set("inventory", function(a -> context.onClientGet(() -> inventory(context.client()))));
        stage.set("fly", function(a -> {
            boolean flying = a.optboolean(1, true);
            context.onClient(() -> {
                var player = context.client().player;
                if (player == null || !player.getAbilities().allowFlying) throw new IllegalStateException("This player is not allowed to fly");
                player.getAbilities().flying = flying;
                player.sendAbilitiesUpdate();
            });
            return LuaValue.NIL;
        }));
    }

    private static LuaValue player(MinecraftClient client) {
        var player = client.player;
        if (player == null || client.world == null) throw new IllegalStateException("Not connected");
        LuaTable table = new LuaTable();
        table.set("name", player.getGameProfile().getName());
        table.set("x", player.getX());
        table.set("y", player.getY());
        table.set("z", player.getZ());
        table.set("yaw", player.getYaw());
        table.set("pitch", player.getPitch());
        table.set("dimension", client.world.getRegistryKey().getValue().toString());
        table.set("gamemode", client.interactionManager == null || client.interactionManager.getCurrentGameMode() == null
                ? "unknown" : client.interactionManager.getCurrentGameMode().getName());
        table.set("health", player.getHealth());
        table.set("food", player.getHungerManager().getFoodLevel());
        table.set("flying", LuaValue.valueOf(player.getAbilities().flying));
        table.set("on_ground", LuaValue.valueOf(player.isOnGround()));
        table.set("selected_slot", player.getInventory().selectedSlot);
        return table;
    }

    /** Non-empty slots as {@code {slot=…, id=…, count=…}}, in slot order (vanilla inventory indices). */
    private static LuaValue inventory(MinecraftClient client) {
        var player = client.player;
        if (player == null) throw new IllegalStateException("Not connected");
        LuaTable list = new LuaTable();
        var inventory = player.getInventory();
        int n = 0;
        for (int slot = 0; slot < inventory.size(); slot++) {
            ItemStack stack = inventory.getStack(slot);
            if (stack.isEmpty()) continue;
            LuaTable item = new LuaTable();
            item.set("slot", slot);
            item.set("id", Registries.ITEM.getId(stack.getItem()).toString());
            item.set("count", stack.getCount());
            list.set(++n, item);
        }
        return list;
    }
}
