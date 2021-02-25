package com.oracle.svm.core.sampling;

import java.util.concurrent.TimeUnit;

import org.graalvm.nativeimage.CurrentIsolate;
import org.graalvm.nativeimage.Threading;

import com.oracle.svm.core.ProfilingSampler;
import com.oracle.svm.core.stack.JavaStackWalker;

public class ProfilingRegister implements ProfilingSampler {

    private boolean collectingActive;

    public ProfilingRegister(boolean collectingActive) {
        this.collectingActive = collectingActive;
    }

    public void sampleThreadStack() {
        SamplingStackVisitor visitor = new SamplingStackVisitor();
        SamplingStackVisitor.SamplingStackTrace data = new SamplingStackVisitor.SamplingStackTrace();
        JavaStackWalker.walkThread(CurrentIsolate.getCurrentThread(), visitor, data);
    }

    @Override
    public void registerSampler() {
        if (collectingActive) {
            Threading.registerRecurringCallback(1000, TimeUnit.MILLISECONDS, (access) -> {
                sampleThreadStack();
            });
        }
    }
}
