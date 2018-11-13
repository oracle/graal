package org.graalvm.tools.lsp.api;

public interface ContextAwareExecutorRegistry {

    public void register(ContextAwareExecutor executor);
}
