package us.z1x.lucerna.rendertarget;

import net.minecraft.client.render.platform.GlStateManager;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL13C;
import us.z1x.lucerna.gl.GlResource;
import us.z1x.lucerna.gl.LucernaRenderSystem;
import us.z1x.lucerna.gl.texture.DepthBufferFormat;

public class DepthTexture extends GlResource {
    public DepthTexture(int width, int height, DepthBufferFormat format) {
        super(LucernaRenderSystem.createTexture(GL11C.GL_TEXTURE_2D));
        int texture = getGlId();

        resize(width, height, format);

        LucernaRenderSystem.texParameteri(texture, GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_MIN_FILTER, GL11C.GL_NEAREST);
        LucernaRenderSystem.texParameteri(texture, GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_MAG_FILTER, GL11C.GL_NEAREST);
        LucernaRenderSystem.texParameteri(texture, GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_WRAP_S, GL13C.GL_CLAMP_TO_EDGE);
        LucernaRenderSystem.texParameteri(texture, GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_WRAP_T, GL13C.GL_CLAMP_TO_EDGE);

        GlStateManager.bindTexture(0);
    }

    public void resize(int width, int height, DepthBufferFormat format) {
        LucernaRenderSystem.texImage2D(getTextureId(), GL11C.GL_TEXTURE_2D, 0, format.getGlInternalFormat(), width, height, 0,
            format.getGlType(), format.getGlFormat(), null);
    }

    public int getTextureId() {
        return getGlId();
    }

    @Override
    protected void destroyInternal() {
        GlStateManager.deleteTextures(getGlId());
    }
}
