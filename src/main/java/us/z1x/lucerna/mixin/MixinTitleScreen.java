package us.z1x.lucerna.mixin;

import net.minecraft.client.gui.screen.menu.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import us.z1x.lucerna.Lucerna;

@Mixin(TitleScreen.class)
public class MixinTitleScreen {
    @Unique
    private static boolean lucerna$hasFirstInit;

    @Inject(method = "init", at = @At("RETURN"))
    private void lucerna$firstInit(CallbackInfo ci) {
        if (!lucerna$hasFirstInit) {
            lucerna$hasFirstInit = true;
            Lucerna.onLoadingComplete();
        }
    }
}
