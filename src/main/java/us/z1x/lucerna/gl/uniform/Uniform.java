package us.z1x.lucerna.gl.uniform;

import us.z1x.lucerna.gl.state.ValueUpdateNotifier;

public abstract class Uniform {
    protected final int location;
    protected final ValueUpdateNotifier notifier;

    protected Uniform(int location) {
        this(location, null);
    }

    protected Uniform(int location, ValueUpdateNotifier notifier) {
        this.location = location;
        this.notifier = notifier;
    }

    public abstract void update();

    public final int getLocation() {
        return location;
    }
}
