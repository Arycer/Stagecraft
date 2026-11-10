package me.arycer.stagecraft.mixin;

import me.arycer.stagecraft.gui.ScreenCapture;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** One {@link ScreenCapture} frame per rendered frame. */
@Mixin(GameRenderer.class)
abstract class GameRendererMixin {
    @Inject(method = "render", at = @At("HEAD"))
    private void stagecraft$beginFrame(RenderTickCounter tickCounter, boolean tick, CallbackInfo ci) {
        ScreenCapture.beginFrame();
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void stagecraft$endFrame(RenderTickCounter tickCounter, boolean tick, CallbackInfo ci) {
        ScreenCapture.endFrame();
    }
}
