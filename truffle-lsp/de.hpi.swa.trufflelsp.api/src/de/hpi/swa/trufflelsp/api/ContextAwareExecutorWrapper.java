package de.hpi.swa.trufflelsp.api;

import java.util.concurrent.Callable;
import java.util.concurrent.Future;

public interface ContextAwareExecutorWrapper {

    public <T> Future<T> executeWithDefaultContext(Callable<T> taskWithResult);

    public <T> Future<T> executeWithNestedContext(Callable<T> taskWithResult);

    public void shutdown();

}
