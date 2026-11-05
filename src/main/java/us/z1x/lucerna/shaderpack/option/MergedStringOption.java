package us.z1x.lucerna.shaderpack.option;

import org.jetbrains.annotations.Nullable;

public class MergedStringOption {
    private final StringOption option;

    public MergedStringOption(StringOption option) {
        this.option = option;
    }

    @Nullable
    public MergedStringOption merge(MergedStringOption other) {
        if (!this.option.getDefaultValue().equals(other.option.getDefaultValue())) {
            return null;
        }

        StringOption option;

        if (this.option.getComment().isPresent()) {
            option = this.option;
        } else {
            option = other.option;
        }

        return new MergedStringOption(option);
    }

    public StringOption getOption() {
        return option;
    }
}
