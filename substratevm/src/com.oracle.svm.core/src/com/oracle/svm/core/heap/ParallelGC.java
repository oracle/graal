package com.oracle.svm.core.heap;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.log.Log;
import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.nativeimage.ImageSingletons;

public abstract class ParallelGC {

    @Fold
    public static ParallelGC singleton() {
        return ImageSingletons.lookup(ParallelGC.class);
    }

    @Fold
    protected static boolean isSupported() {
        return SubstrateOptions.UseParallelGC.getValue();
    }

    public static void start() {
        Log log = Log.log().string("ParGC ");///
        if (isSupported()) {
            log.string("supported").newline();
            singleton().startWorkerThreads();
        } else {
            log.string("missing").newline();
        }
    }

    public abstract void startWorkerThreads();
}
