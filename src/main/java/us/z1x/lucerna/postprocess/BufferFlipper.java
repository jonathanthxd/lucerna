package us.z1x.lucerna.postprocess;

import com.google.common.collect.ImmutableSet;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;

public class BufferFlipper {
    private final IntSet flippedBuffers;

    public BufferFlipper() {
        this.flippedBuffers = new IntOpenHashSet();
    }

    public void flip(int target) {
        if (!flippedBuffers.remove(target)) {
            flippedBuffers.add(target);
        }
    }

    // when flipped, write to main and read from alt, otherwise the reverse
    public boolean isFlipped(int target) {
        return flippedBuffers.contains(target);
    }

    public ImmutableSet<Integer> snapshot() {
        return ImmutableSet.copyOf(flippedBuffers);
    }
}
