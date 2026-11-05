package us.z1x.lucerna.mixin;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import us.z1x.lucerna.Lucerna;

@Mixin(Minecraft.class)
public class MixinMinecraft {
    @Inject(method = "init", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/platform/GLX;init()V", shift = At.Shift.AFTER))
    private void lucerna$onRenderSystemInit(CallbackInfo ci) {
        Lucerna.onRenderSystemInit();
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void lucerna$handleKeybinds(CallbackInfo ci) {
        Lucerna.handleKeybinds((Minecraft) (Object) this);
    }
}
