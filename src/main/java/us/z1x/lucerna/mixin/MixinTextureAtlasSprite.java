package us.z1x.lucerna.mixin;

import net.minecraft.client.render.texture.TextureAtlasSprite;
import net.minecraft.client.resource.metadata.AnimationMetadata;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import us.z1x.lucerna.texture.pbr.PBRTextureManager;

@Mixin(TextureAtlasSprite.class)
public class MixinTextureAtlasSprite {
    @Shadow
    protected int activeFrame;
    @Shadow
    private AnimationMetadata animation;

    @Inject(method = "tick", at = @At("RETURN"))
    private void lucerna$animatePbr(CallbackInfo ci) {
        if (animation != null) {
            PBRTextureManager.INSTANCE.onSpriteTick((TextureAtlasSprite) (Object) this, animation.getFrameIndex(activeFrame));
        }
    }
}
