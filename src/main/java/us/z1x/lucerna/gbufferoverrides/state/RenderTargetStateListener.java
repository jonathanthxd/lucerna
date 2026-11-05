package us.z1x.lucerna.gbufferoverrides.state;

public interface RenderTargetStateListener {
    RenderTargetStateListener NOP = new RenderTargetStateListener() {
        @Override
        public void beginPostChain() {
        }

        @Override
        public void endPostChain() {
        }
    };

    void beginPostChain();
    void endPostChain();
}
