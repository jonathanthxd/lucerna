package us.z1x.lucerna.shaderpack.option;

import org.jetbrains.annotations.Nullable;

public class MergedBooleanOption {
    private final BooleanOption option;

    public MergedBooleanOption(BooleanOption option) {
        this.option = option;
    }

    @Nullable
    public MergedBooleanOption merge(MergedBooleanOption other) {
        if (this.option.getDefaultValue() != other.option.getDefaultValue()) {
            return null;
        }

        BooleanOption option;

        if (this.option.getComment().isPresent()) {
            option = this.option;
        } else {
            option = other.option;
        }

        return new MergedBooleanOption(option);
    }

    public BooleanOption getOption() {
        return option;
    }
}
