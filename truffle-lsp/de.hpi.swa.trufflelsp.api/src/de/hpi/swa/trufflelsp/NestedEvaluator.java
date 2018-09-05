package de.hpi.swa.trufflelsp;

import java.util.concurrent.Future;
import java.util.function.Supplier;

public interface NestedEvaluator {

    public <T> Future<T> executeWithDefaultContext(Supplier<T> taskWithResult);

    default public <T> Future<T> executeWithDefaultContext(Runnable taskWithResult) {
        return executeWithDefaultContext(() -> {
            taskWithResult.run();
            return null;
        });
    }

    public <T> Future<T> executeWithNestedContext(Supplier<T> taskWithResult);

    default public Future<Void> executeWithNestedContext(Runnable taskWithResult) {
        return executeWithNestedContext(() -> {
            taskWithResult.run();
            return null;
        });
    }

    public void shutdown();

}
