package me.arycer.stagecraft.mixin;

import me.arycer.stagecraft.gui.ScreenCapture;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Marks text drawn by the open screen (as opposed to the HUD). */
@Mixin(Screen.class)
abstract class ScreenMixin {
    @Inject(method = "renderWithTooltip", at = @At("HEAD"))
    private void stagecraft$beginScreen(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        ScreenCapture.beginScreen();
    }

    @Inject(method = "renderWithTooltip", at = @At("RETURN"))
    private void stagecraft$endScreen(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        ScreenCapture.endScreen();
    }
}
