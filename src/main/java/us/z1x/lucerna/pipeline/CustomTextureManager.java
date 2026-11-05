package us.z1x.lucerna.pipeline;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMaps;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.client.render.texture.AbstractTexture;
import net.minecraft.client.render.texture.SimpleTexture;
import net.minecraft.client.render.texture.Texture;
import net.minecraft.client.render.texture.TextureManager;
import net.minecraft.resource.Identifier;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL12C;
import org.lwjgl.system.MemoryUtil;
import us.z1x.lucerna.Lucerna;
import us.z1x.lucerna.mixin.GameRendererAccessor;
import us.z1x.lucerna.rendertarget.NativeImageBackedCustomTexture;
import us.z1x.lucerna.rendertarget.NativeImageBackedNoiseTexture;
import us.z1x.lucerna.shaderpack.PackDirectives;
import us.z1x.lucerna.shaderpack.texture.CustomTextureData;
import us.z1x.lucerna.shaderpack.texture.TextureStage;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Optional;
import java.util.function.IntSupplier;

public class CustomTextureManager {
    private final EnumMap<TextureStage, Object2ObjectMap<String, IntSupplier>> customTextureIdMap = new EnumMap<>(TextureStage.class);
    private final IntSupplier noise;

    // gl textures owned here, each must call releaseId in close to avoid leaks
    private final List<AbstractTexture> ownedTextures = new ArrayList<>();
    private final IntList ownedRawTextures = new IntArrayList();

    public CustomTextureManager(PackDirectives packDirectives,
                                EnumMap<TextureStage, Object2ObjectMap<String, CustomTextureData>> customTextureDataMap,
                                Optional<CustomTextureData> customNoiseTextureData) {
        customTextureDataMap.forEach((textureStage, customTextureStageDataMap) -> {
            Object2ObjectMap<String, IntSupplier> customTextureIds = new Object2ObjectOpenHashMap<>();

            customTextureStageDataMap.forEach((samplerName, textureData) -> {
                try {
                    customTextureIds.put(samplerName, createCustomTexture(textureData));
                } catch (IOException e) {
                    Lucerna.logger.error("Unable to parse the image data for the custom texture on stage {}, sampler {}", textureStage, samplerName, e);
                }
            });

            customTextureIdMap.put(textureStage, customTextureIds);
        });

        noise = customNoiseTextureData.flatMap(textureData -> {
            try {
                return Optional.of(createCustomTexture(textureData));
            } catch (IOException e) {
                Lucerna.logger.error("Unable to parse the image data for the custom noise texture", e);

                return Optional.empty();
            }
        }).orElseGet(() -> {
            final int noiseTextureResolution = packDirectives.getNoiseTextureResolution();

            AbstractTexture texture = new NativeImageBackedNoiseTexture(noiseTextureResolution);
            ownedTextures.add(texture);

            return texture::getGlId;
        });
    }

    private IntSupplier createCustomTexture(CustomTextureData textureData) throws IOException {
        if (textureData instanceof CustomTextureData.PngData) {
            AbstractTexture texture = new NativeImageBackedCustomTexture((CustomTextureData.PngData) textureData);
            ownedTextures.add(texture);

            return texture::getGlId;
        } else if (textureData instanceof CustomTextureData.RawData raw) {
            int texture = createRawTexture(raw);
            ownedRawTextures.add(texture);

            return () -> texture;
        } else if (textureData instanceof CustomTextureData.LightmapMarker) {
            // the light texture gets its own path since a mod can create a different one
            return () -> ((GameRendererAccessor) Minecraft.getInstance().gameRenderer).lucerna$getLightmapTexture().getGlId();
        } else if (textureData instanceof CustomTextureData.ResourceData) {
            CustomTextureData.ResourceData resourceData = (CustomTextureData.ResourceData) textureData;
            String namespace = resourceData.getNamespace();
            String location = resourceData.getLocation();

            TextureManager textureManager = Minecraft.getInstance().getTextureManager();
            Identifier textureLocation = new Identifier(namespace, location);

            // re-query the texture manager every time since a resource reload can delete the texture object
            return () -> {
                Texture texture = textureManager.get(textureLocation);

                // vanilla loads a texture when it first binds it, which never happens if the pack turned that renderer off
                if (texture == null) {
                    texture = new SimpleTexture(textureLocation);
                    textureManager.register(textureLocation, texture);
                }

                return texture.getGlId();
            };
        } else {
            throw new IllegalArgumentException("Unable to handle custom texture data " + textureData);
        }
    }

    private static int createRawTexture(CustomTextureData.RawData raw) {
        int target = raw instanceof CustomTextureData.RawData1D ? GL11C.GL_TEXTURE_1D : (raw instanceof CustomTextureData.RawData3D ? GL12C.GL_TEXTURE_3D : GL11C.GL_TEXTURE_2D);
        int texture = GL11C.glGenTextures();
        int filter = raw.getFilteringData().shouldBlur() ? GL11C.GL_LINEAR : GL11C.GL_NEAREST;
        int wrap = raw.getFilteringData().shouldClamp() ? GL12C.GL_CLAMP_TO_EDGE : GL11C.GL_REPEAT;
        ByteBuffer content = MemoryUtil.memAlloc(raw.getContent().length).put(raw.getContent()).flip();

        bind(target, texture);
        GL11C.glTexParameteri(target, GL11C.GL_TEXTURE_MIN_FILTER, filter);
        GL11C.glTexParameteri(target, GL11C.GL_TEXTURE_MAG_FILTER, filter);
        GL11C.glTexParameteri(target, GL11C.GL_TEXTURE_WRAP_S, wrap);
        GL11C.glTexParameteri(target, GL11C.GL_TEXTURE_WRAP_T, wrap);
        GL11C.glTexParameteri(target, GL12C.GL_TEXTURE_WRAP_R, wrap);
        GL11C.glPixelStorei(GL11C.GL_UNPACK_ALIGNMENT, 1);

        int internalFormat = raw.getInternalFormat().getGlFormat();
        int format = raw.getPixelFormat().getGlFormat();
        int type = raw.getPixelType().getGlFormat();

        if (raw instanceof CustomTextureData.RawData1D data) {
            GL11C.glTexImage1D(target, 0, internalFormat, data.getSizeX(), 0, format, type, content);
        } else if (raw instanceof CustomTextureData.RawData3D data) {
            GL12C.glTexImage3D(target, 0, internalFormat, data.getSizeX(), data.getSizeY(), data.getSizeZ(), 0, format, type, content);
        } else {
            CustomTextureData.RawData2D data = (CustomTextureData.RawData2D) raw;
            GL11C.glTexImage2D(target, 0, internalFormat, data.getSizeX(), data.getSizeY(), 0, format, type, content);
        }

        GL11C.glPixelStorei(GL11C.GL_UNPACK_ALIGNMENT, 4);
        bind(target, 0);
        MemoryUtil.memFree(content);

        return texture;
    }

    // 2d binds go through GlStateManager so its cached binding stays correct
    public static void bind(int target, int texture) {
        if (target == GL11C.GL_TEXTURE_2D) {
            GlStateManager.bindTexture(texture);
        } else {
            GL11C.glBindTexture(target, texture);
        }
    }

    public Object2ObjectMap<String, IntSupplier> getCustomTextureIdMap(TextureStage stage) {
        return customTextureIdMap.getOrDefault(stage, Object2ObjectMaps.emptyMap());
    }

    public IntSupplier getNoiseTexture() {
        return noise;
    }

    public void destroy() {
        ownedTextures.forEach(AbstractTexture::clearGlId);
        ownedRawTextures.forEach(GL11C::glDeleteTextures);
    }
}
