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
    private PrefixTree prefixTree = null;
    private SamplingStackVisitor visitor = null;

    public ProfilingRegister(boolean collectingActive) {
        this.collectingActive = collectingActive;
    }

    public int sampleThreadStack() {
        System.out.println("start: " + System.nanoTime());
        SamplingStackVisitor visitor = visitor();
        SamplingStackVisitor.SamplingStackTrace data = new SamplingStackVisitor.SamplingStackTrace(prefixTree().root());
        walkCurrentThread(data, visitor);
        data.node.incValue();
        System.out.println("end: " + System.nanoTime());
        return 0;
    }

    @NeverInline("")
    void walkCurrentThread(SamplingStackVisitor.SamplingStackTrace data, SamplingStackVisitor visitor) {
        Pointer sp = KnownIntrinsics.readStackPointer();
        JavaStackWalker.walkCurrentThread(sp, visitor, data);
    }

    @Override
    public void registerSampler() {
        if (collectingActive) {
            Threading.registerRecurringCallback(1000, TimeUnit.MILLISECONDS, (access) -> {
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
