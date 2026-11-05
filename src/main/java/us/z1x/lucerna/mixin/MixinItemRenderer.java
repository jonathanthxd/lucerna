package us.z1x.lucerna.mixin;

import it.unimi.dsi.fastutil.objects.Object2IntFunction;
import net.minecraft.client.render.entity.ItemRenderer;
import net.minecraft.client.render.model.block.BakedModel;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import us.z1x.lucerna.blockrendering.BlockRenderingSettings;
import us.z1x.lucerna.shaderpack.materialmap.NamespacedId;
import us.z1x.lucerna.uniforms.CapturedRenderingState;

@Mixin(ItemRenderer.class)
public class MixinItemRenderer {
    @Inject(method = "renderItem(Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/render/model/block/BakedModel;)V", at = @At("HEAD"))
    private void lucerna$beginItem(ItemStack stack, BakedModel model, CallbackInfo ci) {
        Object2IntFunction<NamespacedId> itemIds = BlockRenderingSettings.INSTANCE.getItemIds();

        if (itemIds != null && stack != null) {
            CapturedRenderingState.INSTANCE.setCurrentRenderedItem(itemIds.applyAsInt(NamespacedId.of(stack.getItem())));
        }
    }

    @Inject(method = "renderItem(Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/render/model/block/BakedModel;)V", at = @At("RETURN"))
    private void lucerna$endItem(ItemStack stack, BakedModel model, CallbackInfo ci) {
        if (BlockRenderingSettings.INSTANCE.getItemIds() != null) {
            CapturedRenderingState.INSTANCE.setCurrentRenderedItem(0);
        }
    }
}
