package com.oracle.svm.core.sampling;

import java.util.concurrent.TimeUnit;

import org.graalvm.collections.PrefixTree;
import org.graalvm.nativeimage.Threading;
import org.graalvm.word.Pointer;

import com.oracle.svm.core.ProfilingSampler;
import com.oracle.svm.core.annotate.NeverInline;
import com.oracle.svm.core.snippets.KnownIntrinsics;
import com.oracle.svm.core.stack.JavaStackWalker;

public class ProfilingRegister implements ProfilingSampler {

    private final boolean collectingActive;
    private PrefixTree prefixTree;

    public ProfilingRegister(boolean collectingActive) {
        this.collectingActive = collectingActive;
    }

    public void sampleThreadStack() {
        // System.out.println("start: " + System.nanoTime());
        SamplingStackVisitor visitor = new SamplingStackVisitor();
        SamplingStackVisitor.SamplingStackTrace data = new SamplingStackVisitor.SamplingStackTrace(prefixTree().root());
        walkCurrentThread(data, visitor);
        data.node.incValue();
        // prefixTree().topDown(null);
        // System.out.println(Thread.currentThread().getName() + " ... " +
        // System.identityHashCode(data.node));
        System.out.println("--- end: " + System.nanoTime());
    }

    @NeverInline("")
    void walkCurrentThread(SamplingStackVisitor.SamplingStackTrace data, SamplingStackVisitor visitor) {
        Pointer sp = KnownIntrinsics.readStackPointer();
        JavaStackWalker.walkCurrentThread(sp, visitor, data);
    }

    @Override
    public void registerSampler() {
        if (collectingActive) {
            Threading.registerRecurringCallback(10, TimeUnit.MILLISECONDS, (access) -> {
                sampleThreadStack();
            });
        }
    }

    @Override
    public synchronized PrefixTree prefixTree() {
        if (prefixTree == null) {
            prefixTree = new PrefixTree();
        }
        return prefixTree;
    }
}
