package com.oracle.svm.core.sampling;

import java.util.concurrent.TimeUnit;

import org.graalvm.collections.PrefixTree;
import org.graalvm.nativeimage.CurrentIsolate;
import org.graalvm.nativeimage.Threading;

import com.oracle.svm.core.ProfilingSampler;
import com.oracle.svm.core.stack.JavaStackWalker;

public class ProfilingRegister implements ProfilingSampler {

    private final boolean collectingActive;
    private PrefixTree prefixTree = null;
    private SamplingStackVisitor visitor = null;

    public ProfilingRegister(boolean collectingActive) {
        this.collectingActive = collectingActive;
    }

    public void sampleThreadStack() {
        SamplingStackVisitor visitor = visitor();
        System.out.println(visitor + ", visitor");
        SamplingStackVisitor.SamplingStackTrace data = new SamplingStackVisitor.SamplingStackTrace(prefixTree().root());
        JavaStackWalker.walkThread(CurrentIsolate.getCurrentThread(), visitor, data);
        data.node.incValue();
    }

    @Override
    public void registerSampler() {
        System.out.println("register");
        if (collectingActive) {
            System.out.println("active");
            Threading.registerRecurringCallback(1000, TimeUnit.MILLISECONDS, (access) -> {
                System.out.println("callback");
                sampleThreadStack();
            });
        }
    }

    @Override
    public PrefixTree prefixTree() {
        if (prefixTree == null) {
            prefixTree = new PrefixTree();
        }
        return prefixTree;
    }

    private SamplingStackVisitor visitor() {
        if (visitor == null) {
            visitor = new SamplingStackVisitor();
        }
        return visitor;
    }
}
