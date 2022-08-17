package com.oracle.svm.core.heap;

import com.oracle.svm.core.SubstrateOptions;
import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.nativeimage.ImageSingletons;

public abstract class ParallelGC {

    @Fold
    public static boolean isSupported() {
        return SubstrateOptions.UseParallelGC.getValue();
    }

    public static void startWorkerThreads() {
        if (isSupported()) {
            ImageSingletons.lookup(ParallelGC.class).startWorkerThreadsImpl();
        }
    }

    public abstract void startWorkerThreadsImpl();
}
