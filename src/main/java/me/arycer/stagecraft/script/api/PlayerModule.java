package me.arycer.stagecraft.script.api;

import me.arycer.stagecraft.script.ScriptContext;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.registry.Registries;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

import static me.arycer.stagecraft.script.api.StageModule.function;

/** {@code player}, {@code inventory}, {@code fly}, {@code use} and {@code attack}. */
public final class PlayerModule implements StageModule {
    @Override
    public void install(LuaTable stage, ScriptContext context) {
        stage.set("player", function(a -> context.onClientGet(() -> player(context.client()))));
        stage.set("inventory", function(a -> context.onClientGet(() -> inventory(context.client()))));
        stage.set("target", function(a -> context.onClientGet(() -> LuaValue.valueOf(describe(context.client().crosshairTarget)))));
        stage.set("use", function(a -> {
            context.onClient(() -> use(context.client()));
            return LuaValue.NIL;
        }));
        stage.set("attack", function(a -> {
            context.onClient(() -> attack(context.client()));
            return LuaValue.NIL;
        }));
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

    /** Right-click in the world with the main hand: entity, block, then item use (like {@code doItemUse}). */
    private static void use(MinecraftClient client) {
        var player = client.player;
        if (player == null || client.interactionManager == null) throw new IllegalStateException("Not connected");
        HitResult hit = client.crosshairTarget;
        if (hit instanceof EntityHitResult entityHit) {
            // Fabric fires UseEntityCallback from MinecraftClient.doItemUse, before the interaction manager; mods
            // that open a screen on right-click (quest givers, shops) listen there, so it is fired here too.
            var callback = UseEntityCallback.EVENT.invoker().interact(player, player.getWorld(), Hand.MAIN_HAND, entityHit.getEntity(), entityHit);
            if (callback != ActionResult.PASS) {
                if (callback.isAccepted() && client.getNetworkHandler() != null) {
                    Vec3d offset = entityHit.getPos().subtract(entityHit.getEntity().getPos());
                    client.getNetworkHandler().sendPacket(PlayerInteractEntityC2SPacket.interactAt(entityHit.getEntity(), player.isSneaking(), Hand.MAIN_HAND, offset));
                }
                if (callback.shouldSwingHand()) player.swingHand(Hand.MAIN_HAND);
                return;
            }
            var result = client.interactionManager.interactEntityAtLocation(player, entityHit.getEntity(), entityHit, Hand.MAIN_HAND);
            if (!result.isAccepted()) result = client.interactionManager.interactEntity(player, entityHit.getEntity(), Hand.MAIN_HAND);
            if (result.isAccepted()) {
                if (result.shouldSwingHand()) player.swingHand(Hand.MAIN_HAND);
                return;
            }
        }
        if (hit instanceof BlockHitResult blockHit) {
            var result = client.interactionManager.interactBlock(player, Hand.MAIN_HAND, blockHit);
            if (result.isAccepted()) {
                if (result.shouldSwingHand()) player.swingHand(Hand.MAIN_HAND);
                return;
            }
        }
        var result = client.interactionManager.interactItem(player, Hand.MAIN_HAND);
        if (!result.isAccepted()) throw new IllegalStateException("Nothing was used (crosshair=" + describe(hit) + ")");
        if (result.shouldSwingHand()) player.swingHand(Hand.MAIN_HAND);
    }

    /** Left-click attack on the entity under the crosshair (like {@code doAttack}). */
    private static void attack(MinecraftClient client) {
        var player = client.player;
        if (player == null || client.interactionManager == null) throw new IllegalStateException("Not connected");
        if (client.crosshairTarget instanceof EntityHitResult entityHit) {
            client.interactionManager.attackEntity(player, entityHit.getEntity());
            player.swingHand(Hand.MAIN_HAND);
        } else {
            throw new IllegalStateException("No entity under the crosshair (crosshair=" + describe(client.crosshairTarget) + ")");
        }
    }

    private static String describe(HitResult hit) {
        if (hit == null) return "null";
        if (hit.getType() == HitResult.Type.MISS) return "MISS at " + hit.getPos().toString();
        if (hit instanceof EntityHitResult entityHit) return "entity " + entityHit.getEntity().getName().getString();
        if (hit instanceof BlockHitResult blockHit) return "block " + blockHit.getBlockPos().toShortString() + " " + blockHit.getSide();
        return hit.getType().name();
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
