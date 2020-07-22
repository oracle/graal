/*
 * Copyright (c) 2016, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.benchmark;

import java.util.function.Supplier;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.OperationsPerInvocation;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import com.oracle.truffle.api.benchmark.DSLInterpreterBenchmarkFactory.CachedDSLNodeGen;
import com.oracle.truffle.api.benchmark.DSLInterpreterBenchmarkFactory.SimpleDSLNodeGen;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.UnsupportedSpecializationException;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;

@Warmup(iterations = 15)
@Measurement(iterations = 5)
@State(Scope.Thread)
@Fork(value = 1)
public class DSLInterpreterBenchmark extends TruffleBenchmark {

    private static final int NODES = 10000;
    private static TestRootNode root = new TestRootNode();

    private static <T extends AbstractNode> T createNode(Supplier<T> nodeFactory) {
        T node = nodeFactory.get();

        // adopt in a root node to initialize the parent pointer
        root.child = node;
        root.adoptChildren();
        return node;
    }

    @State(Scope.Thread)
    public static class SimpleFirstIterationState {

        final SimpleDSLNode[] nodes = new SimpleDSLNode[NODES];

        @Setup(Level.Invocation)
        public void setup() {
            for (int i = 0; i < NODES; i++) {
                nodes[i] = createNode(SimpleDSLNodeGen::create);
            }
        }

    }

    @State(Scope.Thread)
    public static class CachedFirstIterationState {

        final CachedDSLNode[] nodes = new CachedDSLNode[NODES];

        @Setup(Level.Invocation)
        public void setup() {
            for (int i = 0; i < NODES; i++) {
                nodes[i] = createNode(CachedDSLNodeGen::create);
            }
        }

    }

    /*
     * We setup the interpreter profile to avoid host profiling to have influence. In a typical
     * warmed up interpreter all specialization paths are taken.
     */
    @Setup
    public void setupInterpreterProfile() {
        for (int i = 0; i < 100; i++) {
            AbstractNode node = createNode(SimpleDSLNodeGen::create);
            node.execute(42L);
            node.execute(42);
            try {
                node.execute("");
            } catch (UnsupportedSpecializationException e) {
            }

            node = createNode(SimpleDSLNodeGen::create);
            node.execute(42);
            node.execute(42L);
            try {
                node.execute("");
            } catch (UnsupportedSpecializationException e) {
            }

            node = createNode(CachedDSLNodeGen::create);
            node.execute(42);
            node.execute(42L);
            try {
                node.execute("");
            } catch (UnsupportedSpecializationException e) {
            }
        }
    }

    @State(Scope.Thread)
    public static class SimpleSecondIterationState {

        final SimpleDSLNode[] nodes = new SimpleDSLNode[NODES];

        @Setup(Level.Invocation)
        public void setup() {
            for (int i = 0; i < NODES; i++) {
                nodes[i] = createNode(SimpleDSLNodeGen::create);
                nodes[i].execute(42);
            }
        }

    }

    @State(Scope.Thread)
    public static class CachedSecondIterationState {

        final CachedDSLNode[] nodes = new CachedDSLNode[NODES];

        @Setup(Level.Invocation)
        public void setup() {
            for (int i = 0; i < NODES; i++) {
                nodes[i] = createNode(CachedDSLNodeGen::create);
                nodes[i].execute(42);
            }
        }

    }

    static final class TestRootNode extends RootNode {
        protected TestRootNode() {
            super(null);
        }

        @Child Node child;

        @Override
        public Object execute(VirtualFrame frame) {
            return null;
        }
    }

    abstract static class AbstractNode extends Node {

        abstract int execute(Object v);
    }

    abstract static class SimpleDSLNode extends AbstractNode {

        @Specialization
        int doInt(int v) {
            return v;
        }

        @Specialization
        int doLong(long v) {
            return (int) v;
        }

    }

    abstract static class CachedDSLNode extends AbstractNode {

        @Specialization
        int doCached(@SuppressWarnings("unused") int v, @Cached("CACHED") int cached) {
            return cached;
        }

        @Specialization
        int doLong(long v) {
            return (int) v;
        }

        static final int CACHED = 42;

    }

    @Benchmark
    @OperationsPerInvocation(NODES)
    public int simpleFirstIteration(SimpleFirstIterationState state) {
        Integer v = Integer.valueOf(42);
        int sum = 0;
        for (int i = 0; i < NODES; i++) {
            sum += state.nodes[i].execute(v);
        }
        return sum;
    }

    @Benchmark
    @OperationsPerInvocation(NODES)
    public int simpleSecondIteration(SimpleSecondIterationState state) {
        Integer v = Integer.valueOf(42);
        int sum = 0;
        for (int i = 0; i < NODES; i++) {
            sum += state.nodes[i].execute(v);
        }
        return sum;
    }

    @Benchmark
    @OperationsPerInvocation(NODES)
    public int cachedFirstIteration(CachedFirstIterationState state) {
        Integer v = Integer.valueOf(42);
        int sum = 0;
        for (int i = 0; i < NODES; i++) {
            sum += state.nodes[i].execute(v);
        }
        return sum;
    }

    @Benchmark
    @OperationsPerInvocation(NODES)
    public int cachedSecondIteration(CachedSecondIterationState state) {
        Integer v = Integer.valueOf(42);
        int sum = 0;
        for (int i = 0; i < NODES; i++) {
            sum += state.nodes[i].execute(v);
        }
        return sum;
    }

}
