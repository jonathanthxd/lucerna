package us.z1x.lucerna.uniforms;

import it.unimi.dsi.fastutil.objects.Object2IntFunction;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.living.player.LocalClientPlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import us.z1x.lucerna.gl.uniform.UniformHolder;
import us.z1x.lucerna.gl.uniform.UniformUpdateFrequency;
import us.z1x.lucerna.shaderpack.IdMap;
import us.z1x.lucerna.shaderpack.materialmap.NamespacedId;

import static us.z1x.lucerna.gl.uniform.UniformUpdateFrequency.PER_FRAME;

public final class IdMapUniforms {
    private static final NamespacedId AIR = new NamespacedId("minecraft", "air");

    private IdMapUniforms() {
    }

    public static void addIdMapUniforms(FrameUpdateNotifier notifier, UniformHolder uniforms, IdMap idMap, boolean isOldHandLight) {
        HeldItemSupplier mainHandSupplier = new HeldItemSupplier(idMap.getItemIdMap());
        notifier.addListener(mainHandSupplier::update);

        uniforms
            .uniform1i(UniformUpdateFrequency.PER_FRAME, "heldItemId", mainHandSupplier::getIntID)
            .uniform1i(UniformUpdateFrequency.PER_FRAME, "heldItemId2", () -> -1)
            .uniform1i(PER_FRAME, "heldBlockLightValue", mainHandSupplier::getLightValue)
            .uniform1i(PER_FRAME, "heldBlockLightValue2", () -> 0);
    }

    // 1.8.9 has no offhand, so the offhand uniforms are constant
    private static class HeldItemSupplier {
        private final Object2IntFunction<NamespacedId> itemIdMap;
        private int intID;
        private int lightValue;

        HeldItemSupplier(Object2IntFunction<NamespacedId> itemIdMap) {
            this.itemIdMap = itemIdMap;
        }

        public void update() {
            LocalClientPlayerEntity player = Minecraft.getInstance().player;

            if (player == null) {
                intID = -1;
                lightValue = 0;
                return;
            }

            ItemStack heldStack = player.getItemInHand();

            // 1.8.9 has no air item, but iris reports an empty hand as minecraft:air
            if (heldStack == null) {
                intID = itemIdMap.applyAsInt(AIR);
                lightValue = 0;
                return;
            }

            Item heldItem = heldStack.getItem();

            intID = itemIdMap.applyAsInt(NamespacedId.of(heldItem));
            lightValue = heldItem instanceof BlockItem blockItem ? blockItem.getBlock().getLight() : 0;
        }

        public int getIntID() {
            return intID;
        }

        public int getLightValue() {
            return lightValue;
        }
    }
}
