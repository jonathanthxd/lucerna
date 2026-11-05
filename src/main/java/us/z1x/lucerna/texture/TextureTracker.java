package us.z1x.lucerna.texture;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.client.render.texture.AbstractTexture;
import org.jetbrains.annotations.Nullable;
import us.z1x.lucerna.Lucerna;
import us.z1x.lucerna.gl.state.StateUpdateNotifiers;
import us.z1x.lucerna.pipeline.WorldRenderingPipeline;
import us.z1x.lucerna.texture.pbr.PBRTextureManager;

public class TextureTracker {
    public static final TextureTracker INSTANCE = new TextureTracker();

    private static Runnable bindTextureListener;

    static {
        StateUpdateNotifiers.bindTextureNotifier = listener -> bindTextureListener = listener;
    }

    private final Int2ObjectMap<AbstractTexture> textures = new Int2ObjectOpenHashMap<>();

    private boolean lockBindCallback;

    private TextureTracker() {
    }

    public void trackTexture(int id, AbstractTexture texture) {
        textures.put(id, texture);
    }

    @Nullable
    public AbstractTexture getTexture(int id) {
        return textures.get(id);
    }

    public void onBindTexture(int id) {
        if (lockBindCallback) {
            return;
        }
        if (GlStateManager.texture == 0) {
            lockBindCallback = true;
            if (bindTextureListener != null) {
                bindTextureListener.run();
            }
            WorldRenderingPipeline pipeline = Lucerna.getPipelineManager().getPipelineNullable();
            if (pipeline != null) {
                pipeline.onBindTexture(id);
            }
            lockBindCallback = false;
        }
    }

    public void onDeleteTexture(int id) {
        textures.remove(id);
        PBRTextureManager.INSTANCE.onDeleteTexture(id);
    }
}
