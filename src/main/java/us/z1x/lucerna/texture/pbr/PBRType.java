package us.z1x.lucerna.texture.pbr;

import net.minecraft.resource.Identifier;

enum PBRType {
    NORMAL("_n", 0xFF7F7FFF),
    SPECULAR("_s", 0x00000000);

    final String suffix;
    final int defaultColor;

    PBRType(String suffix, int defaultColor) {
        this.suffix = suffix;
        this.defaultColor = defaultColor;
    }

    Identifier append(Identifier location) {
        String path = location.getPath();
        int extension = path.lastIndexOf('.');

        return new Identifier(location.getNamespace(), path.substring(0, extension) + suffix + path.substring(extension));
    }
}
