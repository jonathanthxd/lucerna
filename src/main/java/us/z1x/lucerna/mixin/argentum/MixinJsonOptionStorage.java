package us.z1x.lucerna.mixin.argentum;

import dev.rdh.argentum.api.config.JsonOptionStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import us.z1x.lucerna.compat.argentum.ArgentumCompat;

// keeps the ArgentumCompat overrides out of the argentum config file
@Mixin(value = JsonOptionStorage.class, remap = false)
public class MixinJsonOptionStorage {
    @Inject(method = "save", at = @At("HEAD"))
    private void lucerna$beforeSave(CallbackInfo ci) {
        ArgentumCompat.beforeSave();
    }

    @Inject(method = "save", at = @At("RETURN"))
    private void lucerna$afterSave(CallbackInfo ci) {
        ArgentumCompat.afterSave();
    }
}
