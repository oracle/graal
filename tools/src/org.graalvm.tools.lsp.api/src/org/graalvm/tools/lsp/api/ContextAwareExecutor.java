package org.graalvm.tools.lsp.api;

import java.util.concurrent.Callable;
import java.util.concurrent.Future;

public interface ContextAwareExecutor {

    public <T> Future<T> executeWithDefaultContext(Callable<T> taskWithResult);

    default <T> Future<T> executeWithNestedContext(Callable<T> taskWithResult) {
        return executeWithNestedContext(taskWithResult, false);
    }

    public <T> Future<T> executeWithNestedContext(Callable<T> taskWithResult, boolean cached);

    public void resetContextCache();

    public void shutdown();

}
