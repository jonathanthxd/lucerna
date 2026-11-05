package us.z1x.lucerna.mixin.entityrendercontext;

import it.unimi.dsi.fastutil.objects.Object2IntFunction;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.entity.Entities;
import net.minecraft.entity.Entity;
import net.minecraft.entity.global.LightningBoltEntity;
import net.minecraft.entity.living.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import us.z1x.lucerna.blockrendering.BlockRenderingSettings;
import us.z1x.lucerna.shaderpack.materialmap.NamespacedId;
import us.z1x.lucerna.uniforms.CapturedRenderingState;

import java.util.Locale;
import java.util.Map;

@Mixin(EntityRenderDispatcher.class)
public class MixinEntityRenderDispatcher {
    @Unique
    private static final Map<Class<?>, NamespacedId> LUCERNA_ENTITY_NAMES = new Reference2ObjectOpenHashMap<>();

    // shader packs expect the modern entity names
    @Unique
    private static final Map<String, String> LUCERNA_LEGACY_NAMES = Map.ofEntries(
        Map.entry("EntityHorse", "horse"),
        Map.entry("PigZombie", "zombie_pigman"),
        Map.entry("MushroomCow", "mooshroom"),
        Map.entry("VillagerGolem", "iron_golem"),
        Map.entry("SnowMan", "snow_golem"),
        Map.entry("Ozelot", "ocelot"),
        Map.entry("LavaSlime", "magma_cube"),
        Map.entry("PrimedTnt", "tnt"),
        Map.entry("FallingSand", "falling_block"),
        Map.entry("ThrownPotion", "potion"),
        Map.entry("ThrownEnderpearl", "ender_pearl"),
        Map.entry("ThrownExpBottle", "experience_bottle"),
        Map.entry("XPOrb", "experience_orb"),
        Map.entry("WitherBoss", "wither"),
        Map.entry("MinecartRideable", "minecart"),
        Map.entry("Item", "item")
    );

    @Unique
    private static NamespacedId lucerna$getName(Entity entity) {
        return LUCERNA_ENTITY_NAMES.computeIfAbsent(entity.getClass(), type -> {
            if (entity instanceof PlayerEntity) {
                return new NamespacedId("minecraft", "player");
            } else if (entity instanceof LightningBoltEntity) {
                return new NamespacedId("minecraft", "lightning_bolt");
            }

            String key = Entities.getKey(entity);

            if (key == null) {
                return new NamespacedId("minecraft", "unknown");
            }

            String name = LUCERNA_LEGACY_NAMES.get(key);

            if (name == null) {
                name = key.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase(Locale.ROOT);
            }

            return new NamespacedId("minecraft", name);
        });
    }

    @Inject(method = "render(Lnet/minecraft/entity/Entity;DDDFFZ)Z", at = @At("HEAD"))
    private void lucerna$beginEntityRender(Entity entity, double dx, double dy, double dz, float yaw, float tickDelta, boolean skipHitbox, CallbackInfoReturnable<Boolean> cir) {
        Object2IntFunction<NamespacedId> entityIds = BlockRenderingSettings.INSTANCE.getEntityIds();

        if (entityIds == null) {
            return;
        }

        CapturedRenderingState.INSTANCE.setCurrentEntity(entityIds.applyAsInt(lucerna$getName(entity)));
    }

    @Inject(method = "render(Lnet/minecraft/entity/Entity;DDDFFZ)Z", at = @At("RETURN"))
    private void lucerna$endEntityRender(Entity entity, double dx, double dy, double dz, float yaw, float tickDelta, boolean skipHitbox, CallbackInfoReturnable<Boolean> cir) {
        if (BlockRenderingSettings.INSTANCE.getEntityIds() != null) {
            CapturedRenderingState.INSTANCE.setCurrentEntity(-1);
        }
    }
}
