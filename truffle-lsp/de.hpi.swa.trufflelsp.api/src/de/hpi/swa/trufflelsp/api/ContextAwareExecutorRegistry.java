package de.hpi.swa.trufflelsp.api;

public interface ContextAwareExecutorRegistry {

    public void register(ContextAwareExecutor executor);
}
