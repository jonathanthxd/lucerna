package us.z1x.lucerna.shaderpack.texture;

import us.z1x.lucerna.gl.texture.InternalTextureFormat;
import us.z1x.lucerna.gl.texture.PixelFormat;
import us.z1x.lucerna.gl.texture.PixelType;

public abstract class CustomTextureData {
    private CustomTextureData() {
    }

    public static final class PngData extends CustomTextureData {
        private final TextureFilteringData filteringData;
        private final byte[] content;

        public PngData(TextureFilteringData filteringData, byte[] content) {
            this.filteringData = filteringData;
            this.content = content;
        }

        public TextureFilteringData getFilteringData() {
            return filteringData;
        }

        public byte[] getContent() {
            return content;
        }
    }

    public static final class LightmapMarker extends CustomTextureData {
        @Override
        public boolean equals(Object obj) {
            return obj.getClass() == this.getClass();
        }

        @Override
        public int hashCode() {
            return 33;
        }
    }

    public static final class ResourceData extends CustomTextureData {
        private final String namespace;
        private final String location;

        public ResourceData(String namespace, String location) {
            this.namespace = namespace;
            this.location = location;
        }

        // not validated, the caller must check it
        public String getNamespace() {
            return namespace;
        }

        // not validated, the caller must check it
        public String getLocation() {
            return location;
        }
    }

    public abstract static class RawData extends CustomTextureData {
        private final byte[] content;
        private final InternalTextureFormat internalFormat;
        private final PixelFormat pixelFormat;
        private final PixelType pixelType;
        private final TextureFilteringData filteringData;

        private RawData(byte[] content, InternalTextureFormat internalFormat,
                        PixelFormat pixelFormat, PixelType pixelType, TextureFilteringData filteringData) {
            this.content = content;
            this.filteringData = filteringData;
            this.internalFormat = internalFormat;
            this.pixelFormat = pixelFormat;
            this.pixelType = pixelType;
        }

        public final byte[] getContent() {
            return content;
        }

        public final InternalTextureFormat getInternalFormat() {
            return internalFormat;
        }

        public final PixelFormat getPixelFormat() {
            return pixelFormat;
        }

        public final PixelType getPixelType() {
            return pixelType;
        }

        public final TextureFilteringData getFilteringData() {
            return filteringData;
        }
    }

    public static final class RawData1D extends RawData {
        private final int sizeX;

        public RawData1D(byte[] content, InternalTextureFormat internalFormat,
                         PixelFormat pixelFormat, PixelType pixelType, TextureFilteringData filteringData, int sizeX) {
            super(content, internalFormat, pixelFormat, pixelType, filteringData);

            this.sizeX = sizeX;
        }

        public int getSizeX() {
            return sizeX;
        }
    }

    public static final class RawData2D extends RawData {
        int sizeX;
        int sizeY;

        public RawData2D(byte[] content, InternalTextureFormat internalFormat,
                         PixelFormat pixelFormat, PixelType pixelType, TextureFilteringData filteringData, int sizeX, int sizeY) {
            super(content, internalFormat, pixelFormat, pixelType, filteringData);

            this.sizeX = sizeX;
            this.sizeY = sizeY;
        }

        public int getSizeX() {
            return sizeX;
        }

        public int getSizeY() {
            return sizeY;
        }
    }

    public static final class RawData3D extends RawData {
        int sizeX;
        int sizeY;
        int sizeZ;

        public RawData3D(byte[] content, InternalTextureFormat internalFormat,
                         PixelFormat pixelFormat, PixelType pixelType, TextureFilteringData filteringData, int sizeX, int sizeY, int sizeZ) {
            super(content, internalFormat, pixelFormat, pixelType, filteringData);

            this.sizeX = sizeX;
            this.sizeY = sizeY;
            this.sizeZ = sizeZ;
        }

        public int getSizeX() {
            return sizeX;
        }

        public int getSizeY() {
            return sizeY;
        }

        public int getSizeZ() {
            return sizeZ;
        }
    }
}
