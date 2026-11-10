package me.arycer.stagecraft.script.api;

import me.arycer.stagecraft.placement.Origin;
import me.arycer.stagecraft.script.ScriptContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.ChunkStatus;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

import static me.arycer.stagecraft.script.api.StageModule.function;

/** {@code assert_dimension}, {@code assert_block} and {@code assert_empty}, relative to the origin. */
public final class WorldModule implements StageModule {
    @Override
    public void install(LuaTable stage, ScriptContext context) {
        Origin origin = context.origin();
        stage.set("assert_dimension", function(a -> {
            String expected = a.checkjstring(1);
            context.onClient(() -> {
                MinecraftClient client = context.client();
                String actual = client.world == null ? "disconnected" : client.world.getRegistryKey().getValue().toString();
                if (!actual.equals(expected)) throw new IllegalStateException("Expected dimension " + expected + ", got " + actual);
            });
            return LuaValue.NIL;
        }));
        stage.set("assert_block", function(a -> {
            BlockPos pos = BlockPos.ofFloored(origin.x() + a.checkdouble(1), origin.y() + a.checkdouble(2), origin.z() + a.checkdouble(3));
            String expected = a.checkjstring(4);
            context.onClient(() -> {
                MinecraftClient client = context.client();
                if (!inOrigin(client, origin) || !loaded(client, pos)) throw new IllegalStateException("Assertion position is not loaded: " + pos);
                String actual = Registries.BLOCK.getId(client.world.getBlockState(pos).getBlock()).toString();
                if (!actual.equals(expected)) throw new IllegalStateException("Expected block " + expected + " at " + pos + ", got " + actual);
            });
            return LuaValue.NIL;
        }));
        stage.set("assert_empty", function(a -> {
            BlockPos min = BlockPos.ofFloored(origin.x() + a.checkdouble(1), origin.y() + a.checkdouble(2), origin.z() + a.checkdouble(3));
            BlockPos max = BlockPos.ofFloored(origin.x() + a.checkdouble(4), origin.y() + a.checkdouble(5), origin.z() + a.checkdouble(6));
            long dx = (long) max.getX() - min.getX() + 1;
            long dy = (long) max.getY() - min.getY() + 1;
            long dz = (long) max.getZ() - min.getZ() + 1;
            if (dx <= 0 || dy <= 0 || dz <= 0 || dx > 100_000 || dy > 100_000 || dz > 100_000 || dx * dy * dz > 100_000)
                throw new LuaError("assert_empty requires an ordered box of at most 100000 blocks");
            context.onClient(() -> {
                MinecraftClient client = context.client();
                if (!inOrigin(client, origin)) throw new IllegalStateException("Destination dimension is not loaded");
                for (BlockPos pos : BlockPos.iterate(min, max)) {
                    if (!loaded(client, pos)) throw new IllegalStateException("Destination chunk not loaded: " + pos);
                    if (!client.world.getBlockState(pos).isAir()) throw new IllegalStateException("Destination is not empty at " + pos);
                }
            });
            return LuaValue.NIL;
        }));
    }

    private static boolean inOrigin(MinecraftClient client, Origin origin) {
        return client.world != null && client.world.getRegistryKey().getValue().toString().equals(origin.dimension());
    }

    private static boolean loaded(MinecraftClient client, BlockPos pos) {
        return client.world.getChunkManager().getChunk(pos.getX() >> 4, pos.getZ() >> 4, ChunkStatus.FULL, false) != null;
    }
}
