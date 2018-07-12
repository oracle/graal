package de.hpi.swa.trufflelsp;

import java.util.concurrent.Future;
import java.util.function.Supplier;

public interface NestedEvaluator {

    public <T> Future<T> doWithDefaultContext(Supplier<T> taskWithResult);

    default public <T> Future<T> doWithDefaultContext(Runnable taskWithResult) {
        return doWithDefaultContext(() -> {
            taskWithResult.run();
            return null;
        });
    }

    public <T> Future<T> doWithNestedContext(Supplier<T> taskWithResult);

    default public <T> Future<T> doWithNestedContext(Runnable taskWithResult) {
        return doWithNestedContext(() -> {
            taskWithResult.run();
            return null;
        });
    }

}
