package com.oracle.svm.core.sampling;

import java.util.concurrent.TimeUnit;

import org.graalvm.collections.PrefixTree;
import org.graalvm.nativeimage.Threading;
import org.graalvm.word.Pointer;

import com.oracle.svm.core.ProfilingSampler;
import com.oracle.svm.core.annotate.NeverInline;
import com.oracle.svm.core.snippets.KnownIntrinsics;
import com.oracle.svm.core.stack.JavaStackWalker;

public class AOTProfilingSampler implements ProfilingSampler {

    private final boolean collectingActive;
    private PrefixTree prefixTree;

    public AOTProfilingSampler(boolean collectingActive) {
        this.collectingActive = collectingActive;
    }

    public void sampleThreadStack() {
        // System.out.println("start: " + System.nanoTime());
        SamplingStackVisitor visitor = new SamplingStackVisitor();
        SamplingStackVisitor.StackTrace data = new SamplingStackVisitor.StackTrace();
        walkCurrentThread(data, visitor);
        long[] result = data.data;
        PrefixTree.Node node = prefixTree().root();
        for (int i = data.num - 1; i >= 0; i--) {
            node = node.at(result[i]);
        }
        node.incValue();
        // System.identityHashCode(data.node));
        System.out.println("--- end: " + System.nanoTime());
    }


    @NeverInline("")
    void walkCurrentThread(SamplingStackVisitor.StackTrace data, SamplingStackVisitor visitor) {
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
