package us.z1x.lucerna.texture.pbr;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.client.render.texture.AbstractTexture;
import net.minecraft.client.render.texture.SimpleTexture;
import net.minecraft.client.render.texture.TextureAtlas;
import net.minecraft.client.render.texture.TextureAtlasSprite;
import net.minecraft.client.render.texture.TextureUtil;
import net.minecraft.client.resource.manager.ResourceManager;
import net.minecraft.resource.Identifier;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.system.MemoryUtil;
import us.z1x.lucerna.Lucerna;
import us.z1x.lucerna.gl.state.StateUpdateNotifiers;
import us.z1x.lucerna.texture.TextureInfoCache;
import us.z1x.lucerna.texture.TextureTracker;

import java.awt.image.BufferedImage;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;

public final class PBRTextureManager {
    public static final PBRTextureManager INSTANCE = new PBRTextureManager();

    private static final Identifier FORMAT_LOCATION = new Identifier("optifine/texture.properties");
    private static final String LAB_PBR = "lab-pbr";

    private static Runnable normalListener;
    private static Runnable specularListener;

    static {
        StateUpdateNotifiers.normalTextureChangeNotifier = listener -> normalListener = listener;
        StateUpdateNotifiers.specularTextureChangeNotifier = listener -> specularListener = listener;
    }

    private final Int2ObjectMap<PBRTextures> textures = new Int2ObjectOpenHashMap<>();
    private final Map<TextureAtlasSprite, List<AnimatedLayer>> animated = new IdentityHashMap<>();
    @Nullable
    private String format;

    private PBRTextureManager() {
    }

    @Nullable
    public String getFormat() {
        return format;
    }

    public void reload(ResourceManager manager) {
        String previous = format;

        format = readFormat(manager);
        clear();

        if (!Objects.equals(previous, format)) {
            try {
                Lucerna.reload();
            } catch (IOException e) {
                Lucerna.logger.error("Failed to reload the shader pack for the new texture format", e);
            }
        }
    }

    @Nullable
    private static String readFormat(ResourceManager manager) {
        try (InputStream stream = manager.getResource(FORMAT_LOCATION).asStream()) {
            Properties properties = new Properties();
            properties.load(stream);
            String value = properties.getProperty("format");

            return value == null || value.isEmpty() ? null : value;
        } catch (FileNotFoundException e) {
            return null;
        } catch (IOException e) {
            Lucerna.logger.error("Failed to read the texture format", e);
            return null;
        }
    }

    public PBRTextures getTextures(int id) {
        PBRTextures loaded = textures.get(id);

        if (loaded != null) {
            return loaded;
        }

        AbstractTexture texture = TextureTracker.INSTANCE.getTexture(id);

        if (!(texture instanceof SimpleTexture) && !(texture instanceof TextureAtlas)) {
            return PBRTextures.EMPTY;
        }

        int bound = GlStateManager.TEXTURES[GlStateManager.texture].texture;
        ResourceManager manager = Minecraft.getInstance().getResourceManager();

        loaded = texture instanceof TextureAtlas atlas
            ? new PBRTextures(loadAtlas(atlas, manager, PBRType.NORMAL), loadAtlas(atlas, manager, PBRType.SPECULAR))
            : new PBRTextures(loadSimple((SimpleTexture) texture, manager, PBRType.NORMAL), loadSimple((SimpleTexture) texture, manager, PBRType.SPECULAR));

        GlStateManager.bindTexture(bound);
        textures.put(id, loaded);

        return loaded;
    }

    public void onDeleteTexture(int id) {
        PBRTextures removed = textures.remove(id);

        if (removed != null) {
            delete(removed);
        }
    }

    public void onSpriteTick(TextureAtlasSprite sprite, int frame) {
        List<AnimatedLayer> layers = animated.get(sprite);

        if (layers == null) {
            return;
        }

        for (AnimatedLayer layer : layers) {
            layer.upload(frame);
        }

        GL11.glBindTexture(GL11.GL_TEXTURE_2D, GlStateManager.TEXTURES[GlStateManager.texture].texture);
    }

    public static void notifyTexturesChanged() {
        if (normalListener != null) {
            normalListener.run();
        }

        if (specularListener != null) {
            specularListener.run();
        }
    }

    private void clear() {
        textures.values().forEach(PBRTextureManager::delete);
        textures.clear();
        animated.clear();
    }

    private static void delete(PBRTextures removed) {
        if (removed.normal() != 0) {
            TextureUtil.deleteTextures(removed.normal());
        }

        if (removed.specular() != 0) {
            TextureUtil.deleteTextures(removed.specular());
        }
    }

    @Nullable
    private static BufferedImage read(ResourceManager manager, Identifier location) {
        try (InputStream stream = manager.getResource(location).asStream()) {
            return TextureUtil.readImage(stream);
        } catch (FileNotFoundException e) {
            return null;
        } catch (IOException e) {
            Lucerna.logger.error("Failed to read {}", location, e);
            return null;
        }
    }

    private static int loadSimple(SimpleTexture texture, ResourceManager manager, PBRType type) {
        BufferedImage image = read(manager, type.append(texture.location));

        if (image == null) {
            return 0;
        }

        int id = GL11.glGenTextures();
        TextureUtil.prepare(id, image.getWidth(), image.getHeight());
        TextureUtil.upload(id, image, false, false);

        return id;
    }

    private int loadAtlas(TextureAtlas atlas, ResourceManager manager, PBRType type) {
        int atlasWidth = TextureInfoCache.INSTANCE.getInfo(atlas.getGlId()).getWidth();
        int atlasHeight = TextureInfoCache.INSTANCE.getInfo(atlas.getGlId()).getHeight();
        int mipLevels = atlas.maxMipLevel;
        boolean labSpecular = type == PBRType.SPECULAR && format != null && format.startsWith(LAB_PBR);
        int id = 0;

        for (TextureAtlasSprite sprite : atlas.stitchedSprites.values()) {
            Identifier location = type.append(atlas.getResourceId(new Identifier(sprite.getName()), 0));
            BufferedImage image = read(manager, location);

            if (image == null) {
                continue;
            }

            int width = sprite.getWidth();
            int height = sprite.getHeight();
            int frameHeight = Math.max(1, image.getWidth() * height / width);

            if (image.getHeight() < frameHeight || image.getHeight() % frameHeight != 0) {
                Lucerna.logger.warn("Ignoring {} because its height is not a multiple of the frame height {}", location, frameHeight);
                continue;
            }

            if (id == 0) {
                id = createAtlas(atlasWidth, atlasHeight, mipLevels, type, labSpecular);
            }

            int frames = image.getHeight() / frameHeight;
            int[][][] levels = new int[frames][][];

            for (int frame = 0; frame < frames; frame++) {
                levels[frame] = PBRMipmaps.generate(scaleFrame(image, frame * frameHeight, frameHeight, width, height), width, height, mipLevels, labSpecular);
            }

            AnimatedLayer layer = new AnimatedLayer(id, sprite.getX(), sprite.getY(), width, height, levels);

            GL11.glBindTexture(GL11.GL_TEXTURE_2D, id);
            layer.upload(0);

            if (sprite.isAnimated() && frames > 1) {
                animated.computeIfAbsent(sprite, key -> new ArrayList<>(2)).add(layer);
            }
        }

        return id;
    }

    private static int[] scaleFrame(BufferedImage image, int top, int frameHeight, int width, int height) {
        int[] pixels = new int[width * height];

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                pixels[y * width + x] = image.getRGB(x * image.getWidth() / width, top + y * frameHeight / height);
            }
        }

        return pixels;
    }

    private static int createAtlas(int width, int height, int mipLevels, PBRType type, boolean labSpecular) {
        int id = GL11.glGenTextures();

        TextureUtil.prepare(id, mipLevels, width, height);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, mipLevels == 0 ? GL11.GL_NEAREST : labSpecular ? GL11.GL_NEAREST_MIPMAP_NEAREST : GL11.GL_NEAREST_MIPMAP_LINEAR);

        IntBuffer fill = MemoryUtil.memAllocInt(width * height);

        for (int i = 0; i < fill.capacity(); i++) {
            fill.put(i, type.defaultColor);
        }

        for (int level = 0; level <= mipLevels; level++) {
            GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, level, 0, 0, width >> level, height >> level, GL12.GL_BGRA, GL12.GL_UNSIGNED_INT_8_8_8_8_REV, fill);
        }

        MemoryUtil.memFree(fill);

        return id;
    }

    private static final class AnimatedLayer {
        private final int texture;
        private final int x;
        private final int y;
        private final int width;
        private final int height;
        private final int[][][] frames;
        private int lastFrame = -1;

        private AnimatedLayer(int texture, int x, int y, int width, int height, int[][][] frames) {
            this.texture = texture;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.frames = frames;
        }

        private void upload(int frame) {
            frame %= frames.length;

            if (frame == lastFrame) {
                return;
            }

            lastFrame = frame;
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);

            int[][] levels = frames[frame];

            for (int level = 0; level < levels.length; level++) {
                GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, level, x >> level, y >> level, width >> level, height >> level, GL12.GL_BGRA, GL12.GL_UNSIGNED_INT_8_8_8_8_REV, levels[level]);
            }
        }
    }
}
