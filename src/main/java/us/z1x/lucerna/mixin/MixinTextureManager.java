package us.z1x.lucerna.mixin;

import net.minecraft.client.render.texture.TextureManager;
import net.minecraft.client.resource.manager.ResourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import us.z1x.lucerna.pipeline.CloudRenderer;
import us.z1x.lucerna.texture.pbr.PBRTextureManager;

@Mixin(TextureManager.class)
public class MixinTextureManager {
    @Inject(method = "reload", at = @At("HEAD"))
    private void lucerna$onReload(ResourceManager manager, CallbackInfo ci) {
        PBRTextureManager.INSTANCE.reload(manager);
        CloudRenderer.INSTANCE.invalidate();
    }
}
