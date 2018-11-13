package org.graalvm.tools.lsp;

public interface ContextAwareExecutorRegistry {

    public void register(ContextAwareExecutor executor);
}
