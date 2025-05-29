/*
 * Copyright (c) 2018, 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package org.graalvm.truffle.benchmark;

import org.graalvm.polyglot.Context;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.CompilerControl;
import org.openjdk.jmh.annotations.CompilerControl.Mode;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.OperationsPerInvocation;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;

import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.impl.DefaultTruffleRuntime;
import com.oracle.truffle.api.nodes.Node;

public class NodeInliningBenchmark extends TruffleBenchmark {

    static final int INNER_LOOP = 100000;

    @State(Scope.Thread)
    public static class BenchmarkState {
        final Context context;
        {
            if (Truffle.getRuntime() instanceof DefaultTruffleRuntime) {
                context = Context.newBuilder().build();
            } else {
                context = Context.newBuilder().allowExperimentalOptions(true).option("engine.Compilation", "false").build();
            }
            context.enter();
        }

        final InlinedNode[] inlinedNodes = new InlinedNode[INNER_LOOP];
        final CachedNode[] cachedNodes = new CachedNode[INNER_LOOP];
        final InlinedSharedExclusiveNode[] sharedExclusiveInlinedNodes = new InlinedSharedExclusiveNode[INNER_LOOP];
        final CachedSharedExclusiveNode[] sharedExclusiveCachedNodes = new CachedSharedExclusiveNode[INNER_LOOP];
        {
            for (int i = 0; i < INNER_LOOP; i++) {
                inlinedNodes[i] = NodeInliningBenchmarkFactory.InlinedNodeGen.create();
                cachedNodes[i] = NodeInliningBenchmarkFactory.CachedNodeGen.create();

                sharedExclusiveInlinedNodes[i] = NodeInliningBenchmarkFactory.InlinedSharedExclusiveNodeGen.create();
                sharedExclusiveCachedNodes[i] = NodeInliningBenchmarkFactory.CachedSharedExclusiveNodeGen.create();

                sharedExclusiveInlinedNodes[i].execute(0, 0, 0, 0);
                sharedExclusiveInlinedNodes[i].execute(1, 0, 0, 0);
                sharedExclusiveInlinedNodes[i].execute(2, 0, 0, 0);
                sharedExclusiveCachedNodes[i].execute(0, 0, 0, 0);
                sharedExclusiveCachedNodes[i].execute(1, 0, 0, 0);
                sharedExclusiveCachedNodes[i].execute(2, 0, 0, 0);
            }
        }

        @TearDown
        public void tearDown() {
            context.leave();
            context.close();
        }
    }

    @State(Scope.Thread)
    public static class FirstCallState extends BenchmarkState {

        final InlinedNode[] inlinedNodes = new InlinedNode[INNER_LOOP];
        final CachedNode[] cachedNodes = new CachedNode[INNER_LOOP];

        @Setup(Level.Invocation)
        public void setup() {
            for (int i = 0; i < INNER_LOOP; i++) {
                inlinedNodes[i] = NodeInliningBenchmarkFactory.InlinedNodeGen.create();
                cachedNodes[i] = NodeInliningBenchmarkFactory.CachedNodeGen.create();
            }
        }

        @TearDown(Level.Invocation)
        public void tearDownIteration() {
            System.gc();
        }

    }

    @Benchmark
    @OperationsPerInvocation(INNER_LOOP)
    public void createInlined(BenchmarkState state) {
        InlinedNode[] nodes = state.inlinedNodes;
        for (int i = 0; i < nodes.length; i++) {
            nodes[i] = NodeInliningBenchmarkFactory.InlinedNodeGen.create();
        }
    }

    @Benchmark
    @OperationsPerInvocation(INNER_LOOP)
    public void createCached(BenchmarkState state) {
        CachedNode[] nodes = state.cachedNodes;
        for (int i = 0; i < nodes.length; i++) {
            nodes[i] = NodeInliningBenchmarkFactory.CachedNodeGen.create();
        }
    }

    @Benchmark
    @OperationsPerInvocation(INNER_LOOP)
    public long executeSpecializeInlined(FirstCallState state) {
        InlinedNode[] nodes = state.inlinedNodes;
        long sum = 0;
        for (int i = 0; i < nodes.length; i++) {
            sum += nodes[i].execute(i, -i, i, -i);
        }
        return sum;
    }

    @Benchmark
    @OperationsPerInvocation(INNER_LOOP)
    public long executeSpecializeCached(FirstCallState state) {
        CachedNode[] nodes = state.cachedNodes;
        long sum = 0;
        for (int i = 0; i < nodes.length; i++) {
            sum += nodes[i].execute(i, -i, i, -i);
        }
        return sum;
    }

    @Benchmark
    @OperationsPerInvocation(INNER_LOOP)
    public long executeFastInlined(BenchmarkState state) {
        InlinedNode[] nodes = state.inlinedNodes;
        long sum = 0;
        for (int i = 0; i < nodes.length; i++) {
            sum += nodes[i].execute(i, -i, i, -i);
        }
        return sum;
    }

    @Benchmark
    @OperationsPerInvocation(INNER_LOOP)
    public long executeFastCached(BenchmarkState state) {
        CachedNode[] nodes = state.cachedNodes;
        long sum = 0;
        for (int i = 0; i < nodes.length; i++) {
            sum += nodes[i].execute(i, -i, i, -i);
        }
        return sum;
    }

    @GenerateInline
    @GenerateCached(false)
    public abstract static class InlinedAbsNode extends Node {

        abstract long execute(Node node, long value);

        @Specialization(guards = "v >= 0")
        static long doInt(long v) {
            return v;
        }

        @Specialization(guards = "v < 0")
        static long doLong(long v) {
            return -v;
        }

    }

    @GenerateInline
    @GenerateCached(false)
    public abstract static class InlinedAddAbsNode extends Node {

        abstract long execute(Node node, long left, long right);

        @Specialization
        static long doAdd(Node node, long left, long right,
                        @Cached InlinedAbsNode leftAbs,
                        @Cached InlinedAbsNode rightAbs) {
            return leftAbs.execute(node, left) + rightAbs.execute(node, right);
        }
        // ...
    }

    @GenerateCached(alwaysInlineCached = true)
    @GenerateInline(false)
    public abstract static class InlinedNode extends Node {

        abstract long execute(long v0, long v1, long v2, long v3);

        @Specialization
        long doInt(long v0, long v1, long v2, long v3,
                        @Cached InlinedAddAbsNode add0,
                        @Cached InlinedAddAbsNode add1,
                        @Cached InlinedAddAbsNode add2) {
            long v;
            v = add0.execute(this, v0, v1);
            v = add1.execute(this, v, v2);
            v = add2.execute(this, v, v3);
            return v;
        }

    }

    @SuppressWarnings("truffle-inlining")
    public abstract static class AbsNode extends Node {

        abstract long execute(long value);

        @Specialization(guards = "v >= 0")
        static long doInt(long v) {
            return v;
        }

        @Specialization(guards = "v < 0")
        static long doLong(long v) {
            return -v;
        }

    }

    @SuppressWarnings("truffle-inlining")
    public abstract static class AddAbsNode extends Node {

        abstract long execute(long left, long right);

        @Specialization
        static long doAdd(long left, long right,
                        @Cached AbsNode leftAbs,
                        @Cached AbsNode rightAbs) {
            return leftAbs.execute(left) + rightAbs.execute(right);
        }
        // ...
    }

    @SuppressWarnings("truffle-inlining")
    public abstract static class CachedNode extends Node {

        abstract long execute(long v0, long v1, long v2, long v3);

        @Specialization
        long doInt(long v0, long v1, long v2, long v3,
                        @Cached AddAbsNode add0,
                        @Cached AddAbsNode add1,
                        @Cached AddAbsNode add2) {
            long v;
            v = add0.execute(v0, v1);
            v = add1.execute(v, v2);
            v = add2.execute(v, v3);
            return v;
        }

    }

    @SuppressWarnings({"truffle-inlining", "truffle-sharing"})
    public abstract static class CachedSharedExclusiveNode extends Node {

        abstract long execute(long v0, long v1, long v2, long v3);

        @Specialization(guards = "v0 == cachedV0", limit = "3")
        @CompilerControl(Mode.DONT_INLINE)
        @SuppressWarnings("unused")
        static long do0(@SuppressWarnings("unused") long v0, long v1, long v2, long v3,
                        @Cached("v0") long cachedV0,
                        @Shared @Cached AddAbsNode add0,
                        @Cached AddAbsNode add1,
                        @Cached AddAbsNode add2) {
            long v;
            v = add0.execute(cachedV0, v1);
            v = add1.execute(v, v2);
            v = add2.execute(v, v3);
            return v;
        }

        @Specialization(guards = "v0 == cachedV0", limit = "3")
        @CompilerControl(Mode.DONT_INLINE)
        @SuppressWarnings("unused")
        static long do1(long v0, long v1, long v2, long v3,
                        @Cached("v0") long cachedV0,
                        @Shared @Cached AddAbsNode add0,
                        @Cached AddAbsNode add1,
                        @Cached AddAbsNode add2) {
            long v;
            v = add0.execute(cachedV0, v1);
            v = add1.execute(v, v2);
            v = add2.execute(v, v3);
            return v;
        }
    }

    @SuppressWarnings({"truffle-inlining", "truffle-sharing", "truffle-interpreted-performance"})
    public abstract static class InlinedSharedExclusiveNode extends Node {

        abstract long execute(long v0, long v1, long v2, long v3);

        @Specialization(guards = "v0 == cachedV0", limit = "3")
        @CompilerControl(Mode.DONT_INLINE)
        @SuppressWarnings("unused")
        static long do0(@SuppressWarnings("unused") long v0, long v1, long v2, long v3,
                        @Bind Node node,
                        @Cached("v0") long cachedV0,
                        @Shared @Cached InlinedAddAbsNode add0,
                        @Cached InlinedAddAbsNode add1,
                        @Cached InlinedAddAbsNode add2) {
            long v;
            v = add0.execute(node, cachedV0, v1);
            v = add1.execute(node, v, v2);
            v = add2.execute(node, v, v3);
            return v;
        }

        @Specialization(guards = "v0 == cachedV0", limit = "3")
        @CompilerControl(Mode.DONT_INLINE)
        @SuppressWarnings("unused")
        static long do1(long v0, long v1, long v2, long v3,
                        @Bind Node node,
                        @Cached("v0") long cachedV0,
                        @Shared @Cached InlinedAddAbsNode add0,
                        @Cached InlinedAddAbsNode add1,
                        @Cached InlinedAddAbsNode add2) {
            long v;
            v = add0.execute(node, cachedV0, v1);
            v = add1.execute(node, v, v2);
            v = add2.execute(node, v, v3);
            return v;
        }

    }

    /*
     * Inlining beats a cached node by a wide margin if everything gets inlined properly. If
     * inlining is explicitly disabled and the an inlining slow-path is triggered then inlined
     * performs worse than cached.
     */

    @Benchmark
    @OperationsPerInvocation(INNER_LOOP)
    public long executeSharedExclusiveInlined(BenchmarkState state) {
        var nodes = state.sharedExclusiveInlinedNodes;
        long sum = 0;
        for (int i = 0; i < nodes.length; i++) {
            sum += nodes[i % 3].execute(0, -i, i, -i);
        }
        return sum;
    }

    @Benchmark
    @OperationsPerInvocation(INNER_LOOP)
    public long executeSharedExclusiveCached(BenchmarkState state) {
        var nodes = state.sharedExclusiveCachedNodes;
        long sum = 0;
        for (int i = 0; i < nodes.length; i++) {
            sum += nodes[i % 3].execute(0, -i, i, -i);
        }
        return sum;
    }

}
