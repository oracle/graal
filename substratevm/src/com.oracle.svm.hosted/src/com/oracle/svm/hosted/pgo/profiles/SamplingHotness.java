/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.oracle.svm.hosted.pgo.profiles;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.svm.hosted.cai.PrefixTree;
import com.oracle.svm.hosted.meta.HostedMethod;

import jdk.graal.compiler.graph.NodeSourcePosition;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Method-rooted view of the {@code samplingProfiles} stacks.
 * <p>
 * {@link PrefixTree} organizes samples as a calling-context tree whose entry points are the
 * outermost frames, and hands the inliner a {@code PrefixTree.Cursor} for each compilation root.
 * Selecting compilation roots per calling context is not available here, so every method that
 * appears in a sampled stack becomes its own entry point: for each stack and each frame in it, the
 * suffix starting at that frame is added to the tree rooted at that frame's method. The resulting
 * per-method tree merges all contexts the method was seen in, which is what a context-insensitive
 * compilation unit needs (callee hotness relative to the compilation root, and which callees were
 * observed at an indirect call) while remaining context-sensitive below the root.
 * <p>
 * Self time is the share of non-idle samples whose innermost frame is in the method; a method is a
 * hot caller if it appears anywhere in a sampled stack that does not end in an idle leaf.
 */
public final class SamplingHotness {

    /**
     * Leaf frames that consume no processor time. A sampler that walks every thread also samples
     * threads parked in these, and a thread that waits for the whole run is sampled as often as one
     * that works. Leaving those samples in the denominator lowers every method's reported self
     * time, which is the signal the compiler uses to decide what counts as globally hot code, so
     * they are excluded. The blocking primitives are matched by declaring class as well as name, so
     * an application method that happens to be called {@code sleep} still counts.
     */
    private static final Set<String> IDLE_LEAF_CLASSES = Set.of(
                    "com.oracle.svm.core.thread.PlatformThreads",
                    "com.oracle.svm.core.thread.JavaThreads",
                    "java.lang.Object",
                    "java.lang.Thread",
                    "jdk.internal.misc.Unsafe",
                    "sun.misc.Unsafe",
                    "java.util.concurrent.locks.LockSupport");
    /** Names of the idle leaf methods in {@link #IDLE_LEAF_CLASSES}. */
    private static final Set<String> IDLE_LEAF_METHODS = Set.of("sleep", "sleep0", "sleepNanos", "sleepNanos0", "beforeSleep", "afterSleep",
                    "park", "park0", "parkNanos", "parkUntil", "parkCurrentPlatformOrCarrierThread", "wait", "wait0", "onSpinWait", "yield", "yield0");
    /** One entry point per sampled method, with every non-idle stack suffix that starts in it. */
    private final PrefixTree methodRootedTree;
    /** Non-idle samples per innermost method. */
    private final Map<AnalysisMethod, Long> selfSamples;
    /** Samples whose innermost frame is not an idle leaf. */
    private final long nonIdleSamples;
    /** Samples whose innermost frame is an idle leaf; they count toward no method. */
    private final long idleSamples;
    /** The compilation roots that {@link #cursorFor} was asked about that have samples. */
    private final Set<AnalysisMethod> sampledRoots = ConcurrentHashMap.newKeySet();
    /** The compilation roots that {@link #cursorFor} was asked about that have no samples. */
    private final Set<AnalysisMethod> unsampledRoots = ConcurrentHashMap.newKeySet();

    /**
     * Builds the method-rooted trees and the self-time counts.
     *
     * @param stackSamples the resolved {@code samplingProfiles}: each stack, with the outermost
     *            frame at its head, mapped to its sample count
     */
    public SamplingHotness(Map<NodeSourcePosition, Long> stackSamples) {
        Map<List<NodeSourcePosition>, Long> stacks = new HashMap<>();
        for (Map.Entry<NodeSourcePosition, Long> entry : stackSamples.entrySet()) {
            List<NodeSourcePosition> outermostFirst = new ArrayList<>();
            /*
             * The resolver builds each stack as a chain whose head is the outermost frame, so this
             * loop, which follows getCaller(), goes from the outermost frame inward.
             */
            for (NodeSourcePosition position : entry.getKey()) {
                outermostFirst.add(position);
            }
            stacks.put(List.copyOf(outermostFirst), entry.getValue());
        }

        SampleAccounting<AnalysisMethod> accounting = accountSamples(stacks, frame -> (AnalysisMethod) frame.getMethod(), SamplingHotness::isIdle);
        selfSamples = accounting.selfSamples();
        nonIdleSamples = accounting.nonIdleSamples();
        idleSamples = accounting.idleSamples();

        Map<NodeSourcePosition, Long> suffixes = new HashMap<>();
        for (Map.Entry<List<NodeSourcePosition>, Long> entry : stacks.entrySet()) {
            List<NodeSourcePosition> outermostFirst = entry.getKey();
            if (isIdle(outermostFirst.getLast())) {
                continue;
            }
            /*
             * Every frame's suffix (that frame down to the leaf) is a stack rooted at that frame, so
             * the stack's count is added once per frame; ParsedIprofProfile makes sure such sums fit.
             */
            for (List<NodeSourcePosition> suffixFrames : sampledSuffixes(outermostFirst)) {
                NodeSourcePosition suffix = null;
                for (int i = suffixFrames.size() - 1; i >= 0; i--) {
                    NodeSourcePosition frame = suffixFrames.get(i);
                    suffix = new NodeSourcePosition(suffix, frame.getMethod(), frame.getBCI());
                }
                suffixes.merge(suffix, entry.getValue(), Long::sum);
            }
        }
        methodRootedTree = new PrefixTree(suffixes);
    }

    /**
     * Result of {@link #accountSamples}.
     *
     * @param <M> the method type
     * @param selfSamples non-idle samples per innermost method
     * @param nonIdleSamples samples whose innermost frame is not an idle leaf
     * @param idleSamples samples whose innermost frame is an idle leaf
     */
    record SampleAccounting<M>(Map<M, Long> selfSamples, long nonIdleSamples, long idleSamples) {
    }

    /**
     * Counts the samples of each innermost method, apart from idle ones. Generic in the frame type
     * so that tests can use plain strings.
     *
     * @param <F> the frame type
     * @param <M> the method type
     * @param stackSamples sampled stacks, each ordered outermost frame to leaf, with their counts
     * @param method gives the method of a frame
     * @param idleLeaf tells whether a leaf frame is idle
     * @return the counts
     * @throws IllegalArgumentException if a stack is empty
     */
    static <F, M> SampleAccounting<M> accountSamples(Map<List<F>, Long> stackSamples, Function<F, M> method, Predicate<F> idleLeaf) {
        Map<M, Long> self = new HashMap<>();
        long nonIdle = 0;
        long idle = 0;
        for (Map.Entry<List<F>, Long> entry : stackSamples.entrySet()) {
            List<F> stack = entry.getKey();
            if (stack.isEmpty()) {
                throw new IllegalArgumentException("Sampled stack must not be empty");
            }
            long count = entry.getValue();
            F leaf = stack.getLast();
            if (idleLeaf.test(leaf)) {
                idle += count;
                continue;
            }
            nonIdle += count;
            self.merge(method.apply(leaf), count, Long::sum);
        }
        return new SampleAccounting<>(Map.copyOf(self), nonIdle, idle);
    }

    /**
     * Every suffix of a sampled stack, one per frame, so a method that recurs gets one per
     * occurrence.
     *
     * @param <F> the frame type
     * @param outermostFirst the stack, outermost frame first
     * @return the suffixes, the whole stack first and the leaf alone last
     */
    static <F> List<List<F>> sampledSuffixes(List<F> outermostFirst) {
        List<List<F>> suffixes = new ArrayList<>(outermostFirst.size());
        for (int start = 0; start < outermostFirst.size(); start++) {
            suffixes.add(List.copyOf(outermostFirst.subList(start, outermostFirst.size())));
        }
        return List.copyOf(suffixes);
    }

    /**
     * Cursor for a compilation unit rooted at {@code method}. The inlining provider asks once per
     * decision, so the root is recorded in a set.
     *
     * @param method the compilation root
     * @return the root of the method's tree, or {@code null} if the method was never sampled
     */
    PrefixTree.Cursor cursorFor(HostedMethod method) {
        PrefixTree.Node node = methodRootedTree.entryPoints().get(method.getWrapped());
        (node == null ? unsampledRoots : sampledRoots).add(method.getWrapped());
        return node;
    }

    /**
     * Whether {@code method} is on a non-idle sampled stack.
     *
     * @param method the method
     * @return {@code true} if the method has a tree
     */
    private boolean isSampled(HostedMethod method) {
        return methodRootedTree.hasMethod(method);
    }

    /**
     * Self time of {@code method}.
     *
     * @param method the method
     * @return the fraction in [0, 1] of all non-idle samples whose innermost frame lies in it
     */
    private double selfTimeShare(HostedMethod method) {
        if (nonIdleSamples == 0) {
            return 0.0;
        }
        return (double) selfSamples.getOrDefault(method.getWrapped(), 0L) / nonIdleSamples;
    }

    /** Whether the method of {@code leaf} is one of the idle leaf methods. */
    private static boolean isIdle(NodeSourcePosition leaf) {
        ResolvedJavaMethod method = leaf.getMethod();
        return IDLE_LEAF_METHODS.contains(method.getName()) &&
                        IDLE_LEAF_CLASSES.contains(method.getDeclaringClass().toJavaName());
    }

    /**
     * Samples discarded because the sampled thread was parked in a method that uses no processor
     * time.
     *
     * @return the number of idle samples
     */
    public long idleSamples() {
        return idleSamples;
    }

    /**
     * Samples that count toward self time.
     *
     * @return the number of non-idle samples
     */
    public long nonIdleSamples() {
        return nonIdleSamples;
    }

    /**
     * Methods on non-idle sampled stacks.
     *
     * @return the number of method-rooted trees
     */
    public int sampledMethodCount() {
        return methodRootedTree.entryPoints().size();
    }

    /**
     * Compilation roots the inliner asked about that have stack samples.
     *
     * @return the number of distinct such roots
     */
    public int sampledRootCount() {
        return sampledRoots.size();
    }

    /**
     * Compilation roots the inliner asked about that have no stack samples.
     *
     * @return the number of distinct such roots
     */
    public int unsampledRootCount() {
        return unsampledRoots.size();
    }

    /**
     * Per-graph provider. {@code hotCaller} lets the inliner devirtualize hot callees and apply
     * profiles while it expands callees; self time gives hot code larger budgets in the phases that
     * ask {@code GlobalProfilesOptimizationUtility}, such as duplication and loop unrolling.
     *
     * @param method the compilation root
     * @return the provider for the root's graph
     */
    public StructuredGraph.GlobalProfileProvider providerFor(HostedMethod method) {
        double selfTime = ProfiledInliningProvider.Options.PGOSamplingSelfTime.getValue() ? selfTimeShare(method) : StructuredGraph.GlobalProfileProvider.GLOBAL_PROFILE_PROVIDER_DISABLED;
        boolean hot = ProfiledInliningProvider.Options.PGOSamplingHotCaller.getValue() && isSampled(method);
        return new StructuredGraph.GlobalProfileProvider() {
            @Override
            public double getGlobalSelfTimePercent() {
                return selfTime;
            }

            @Override
            public boolean hotCaller() {
                return hot;
            }
        };
    }
}
