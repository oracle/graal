/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.benchmark;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.OperationsPerInvocation;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import com.oracle.truffle.api.benchmark.DSLInterpreterBenchmarkFactory.DSLNodeGen;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.UnsupportedSpecializationException;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;

@Warmup(iterations = 5)
@Measurement(iterations = 5)
@State(Scope.Thread)
@Fork(value = 1)
public class DSLInterpreterBenchmark extends TruffleBenchmark {

    private static final int NODES = 10000;
    private static TestRootNode root = new TestRootNode();

    private static DSLNode createNode() {
        DSLNode node = DSLNodeGen.create();

        // adopt in a root node to initialize the parent pointer
        root.child = node;
        root.adoptChildren();
        return node;
    }

    @State(Scope.Thread)
    public static class SpecializeState {

        final DSLNode[] nodes = new DSLNode[NODES];

        @Setup(Level.Invocation)
        public void setup() {
            for (int i = 0; i < NODES; i++) {
                nodes[i] = createNode();
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
            DSLNode node = createNode();
            node.execute(42L);
            node.execute(42);
            try {
                node.execute("");
            } catch (UnsupportedSpecializationException e) {
            }

            node = createNode();
            node.execute(42);
            node.execute(42L);
            try {
                node.execute("");
            } catch (UnsupportedSpecializationException e) {
            }
        }
    }

    @State(Scope.Thread)
    public static class SecondIterationState {

        final DSLNode[] nodes = new DSLNode[NODES];

        @Setup(Level.Invocation)
        public void setup() {
            for (int i = 0; i < NODES; i++) {
                nodes[i] = createNode();
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

    abstract static class DSLNode extends Node {

        abstract int execute(Object v);

        @Specialization
        int doInt(int v) {
            return v;
        }

        @Specialization
        int doLong(long v) {
            return (int) v;
        }

    }

    @Benchmark
    @OperationsPerInvocation(NODES)
    public int firstIteration(SpecializeState state) {
        Integer v = Integer.valueOf(42);
        int sum = 0;
        for (int i = 0; i < NODES; i++) {
            sum += state.nodes[i].execute(v);
        }
        return sum;
    }

    @Benchmark
    @OperationsPerInvocation(NODES)
    public int secondIteration(SecondIterationState state) {
        Integer v = Integer.valueOf(42);
        int sum = 0;
        for (int i = 0; i < NODES; i++) {
            sum += state.nodes[i].execute(v);
        }
        return sum;
    }

}
