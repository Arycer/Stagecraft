package me.arycer.stagecraft.mixin;

import me.arycer.stagecraft.gui.ScreenCapture;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.OrderedText;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Feeds {@link ScreenCapture} with every string drawn; the {@code Text} and shadow variants delegate to these two. */
@Mixin(DrawContext.class)
abstract class DrawContextMixin {
    @Shadow @Final private MatrixStack matrices;

    @Inject(method = "drawText(Lnet/minecraft/client/font/TextRenderer;Ljava/lang/String;IIIZ)I", at = @At("HEAD"))
    private void stagecraft$captureString(TextRenderer renderer, String text, int x, int y, int color, boolean shadow, CallbackInfoReturnable<Integer> cir) {
        ScreenCapture.text(renderer, text, x, y, matrices.peek().getPositionMatrix());
    }

    @Inject(method = "drawText(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/text/OrderedText;IIIZ)I", at = @At("HEAD"))
    private void stagecraft$captureOrdered(TextRenderer renderer, OrderedText text, int x, int y, int color, boolean shadow, CallbackInfoReturnable<Integer> cir) {
        ScreenCapture.text(renderer, text, x, y, matrices.peek().getPositionMatrix());
    }
}
