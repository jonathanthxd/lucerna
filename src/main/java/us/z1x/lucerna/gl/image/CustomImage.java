package us.z1x.lucerna.gl.image;

import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL12C;
import org.lwjgl.opengl.GL44C;
import us.z1x.lucerna.pipeline.CustomTextureManager;
import us.z1x.lucerna.shaderpack.ImageInformation;

import java.nio.ByteBuffer;

public class CustomImage {
    private final ImageInformation info;
    private final int texture;

    public CustomImage(ImageInformation info, int screenWidth, int screenHeight) {
        this.info = info;
        this.texture = GL11C.glGenTextures();

        CustomTextureManager.bind(info.target(), texture);
        GL11C.glTexParameteri(info.target(), GL11C.GL_TEXTURE_MIN_FILTER, GL11C.GL_LINEAR);
        GL11C.glTexParameteri(info.target(), GL11C.GL_TEXTURE_MAG_FILTER, GL11C.GL_LINEAR);
        GL11C.glTexParameteri(info.target(), GL11C.GL_TEXTURE_WRAP_S, GL12C.GL_CLAMP_TO_EDGE);
        GL11C.glTexParameteri(info.target(), GL11C.GL_TEXTURE_WRAP_T, GL12C.GL_CLAMP_TO_EDGE);
        GL11C.glTexParameteri(info.target(), GL12C.GL_TEXTURE_WRAP_R, GL12C.GL_CLAMP_TO_EDGE);
        resize(screenWidth, screenHeight);
    }

    public void resize(int screenWidth, int screenHeight) {
        int width = info.relative() ? Math.max(1, (int) (screenWidth * info.relativeWidth())) : info.width();
        int height = info.relative() ? Math.max(1, (int) (screenHeight * info.relativeHeight())) : info.height();
        int internalFormat = info.internalFormat().getGlFormat();
        int format = info.format().getGlFormat();
        int type = info.type().getGlFormat();

        CustomTextureManager.bind(info.target(), texture);

        if (info.target() == GL11C.GL_TEXTURE_1D) {
            GL11C.glTexImage1D(info.target(), 0, internalFormat, width, 0, format, type, (ByteBuffer) null);
        } else if (info.target() == GL12C.GL_TEXTURE_3D) {
            GL12C.glTexImage3D(info.target(), 0, internalFormat, width, height, info.depth(), 0, format, type, (ByteBuffer) null);
        } else {
            GL11C.glTexImage2D(info.target(), 0, internalFormat, width, height, 0, format, type, (ByteBuffer) null);
        }

        CustomTextureManager.bind(info.target(), 0);
        GL44C.glClearTexImage(texture, 0, format, type, (ByteBuffer) null);
    }

    public void clearIfNeeded() {
        if (info.clear()) {
            GL44C.glClearTexImage(texture, 0, info.format().getGlFormat(), info.type().getGlFormat(), (ByteBuffer) null);
        }
    }

    public ImageInformation getInfo() {
        return info;
    }

    public int getTexture() {
        return texture;
    }

    public void destroy() {
        GL11C.glDeleteTextures(texture);
    }
}
