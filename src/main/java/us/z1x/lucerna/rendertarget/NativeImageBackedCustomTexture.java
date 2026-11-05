package us.z1x.lucerna.rendertarget;

import net.minecraft.client.render.texture.DynamicTexture;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL13C;
import us.z1x.lucerna.gl.LucernaRenderSystem;
import us.z1x.lucerna.shaderpack.texture.CustomTextureData;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.io.IOException;

public class NativeImageBackedCustomTexture extends DynamicTexture {
    public NativeImageBackedCustomTexture(CustomTextureData.PngData textureData) throws IOException {
        super(ImageIO.read(new ByteArrayInputStream(textureData.getContent())));

        // images are not blurred or clamped by default

        if (textureData.getFilteringData().shouldBlur()) {
            LucernaRenderSystem.texParameteri(getGlId(), GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_MIN_FILTER, GL11C.GL_LINEAR);
            LucernaRenderSystem.texParameteri(getGlId(), GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_MAG_FILTER, GL11C.GL_LINEAR);
        }

        if (textureData.getFilteringData().shouldClamp()) {
            LucernaRenderSystem.texParameteri(getGlId(), GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_WRAP_S, GL13C.GL_CLAMP_TO_EDGE);
            LucernaRenderSystem.texParameteri(getGlId(), GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_WRAP_T, GL13C.GL_CLAMP_TO_EDGE);
        }
    }
}
