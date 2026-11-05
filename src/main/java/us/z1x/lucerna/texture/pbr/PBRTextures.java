package us.z1x.lucerna.texture.pbr;

// a texture id of 0 means the pipeline's flat default
public record PBRTextures(int normal, int specular) {
    static final PBRTextures EMPTY = new PBRTextures(0, 0);
}
