package us.z1x.lucerna.pipeline.transform;

import io.github.douira.glsl_transformer.job_parameter.JobParameters;

import java.util.Map;

class Parameters extends JobParameters {
    public final Patch patch;
    // set per stage during a transform, so it stays out of the cache key
    public PatchShaderType type;
    // raw custom textures as "SAMPLER3D colortex6" to the sampler that replaces declarations of that type
    public Map<String, String> textureRenames = Map.of();

    public Parameters(Patch patch) {
        this.patch = patch;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((patch == null) ? 0 : patch.hashCode());
        result = prime * result + textureRenames.hashCode();
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        Parameters other = (Parameters) obj;
        if (patch != other.patch)
            return false;
        return textureRenames.equals(other.textureRenames);
    }
}
