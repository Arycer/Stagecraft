package me.arycer.stagecraft.mixin;

import me.arycer.stagecraft.window.WindowPlacement;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Adds {@code STAGECRAFT_LABEL} (the director's role) to the window title, so tiled windows are told apart. */
@Mixin(MinecraftClient.class)
abstract class MinecraftClientTitleMixin {
    @Inject(method = "getWindowTitle", at = @At("RETURN"), cancellable = true)
    private void stagecraft$label(CallbackInfoReturnable<String> cir) {
        String label = WindowPlacement.label();
        if (label != null) cir.setReturnValue("[" + label + "] " + cir.getReturnValue());
    }
}
