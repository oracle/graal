/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.reflect.InvocationTargetException;

import org.graalvm.polyglot.Context;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.CompilerControl;
import org.openjdk.jmh.annotations.CompilerControl.Mode;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.OperationsPerInvocation;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.impl.DefaultTruffleRuntime;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.nodes.RootNode;

/*
 * This benchmark may take a long time to reach a stable performance.
 */
@Warmup(iterations = 100, time = 1)
@Measurement(iterations = 5, time = 2)
public class InterpreterCallBenchmark extends TruffleBenchmark {

    @State(Scope.Thread)
    public static class BenchmarkState {
        final AbstractRootNode[] rootNodes;
        final CallTarget[] callTargets;
        final DirectCallNode[] directCallNodes;
        final IndirectCallNode indirectCall;
        final Object[] singleArg = new Object[]{42};
        final VirtualFrame frame;
        final Context context;
        {
            if (Truffle.getRuntime() instanceof DefaultTruffleRuntime) {
                context = Context.newBuilder().build();
            } else {
                context = Context.newBuilder().allowExperimentalOptions(true).option("engine.Compilation", "false").build();
            }
            context.enter();
            rootNodes = new AbstractRootNode[ROOT_CLASSES.length];
            callTargets = new CallTarget[ROOT_CLASSES.length];
            directCallNodes = new DirectCallNode[ROOT_CLASSES.length];

            for (int i = 0; i < ROOT_CLASSES.length; i++) {
                Class<?> rootClass = ROOT_CLASSES[i];
                try {
                    rootNodes[i] = (AbstractRootNode) rootClass.getConstructor().newInstance();
                } catch (InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException | NoSuchMethodException | SecurityException e) {
                    throw new AssertionError(e);
                }
                callTargets[i] = Truffle.getRuntime().createCallTarget(rootNodes[i]);
                directCallNodes[i] = Truffle.getRuntime().createDirectCallNode(callTargets[i]);
            }

            this.frame = Truffle.getRuntime().createVirtualFrame(singleArg, rootNodes[0].getFrameDescriptor());
            indirectCall = Truffle.getRuntime().createIndirectCallNode();
        }

        @TearDown
        public void tearDown() {
            context.leave();
            context.close();
        }
    }

    @Benchmark
    @OperationsPerInvocation(ROOT_CLASSES_LENGTH)
    public int upperBound(BenchmarkState state) {
        AbstractRootNode[] roots = state.rootNodes;
        Object[] args = state.singleArg;
        int sum = 0;
        for (int i = 0; i < roots.length; i++) {
            RootNode root = roots[i];
            sum += (int) callBoundary(args, root);
        }
        return sum;
    }

    @CompilerControl(Mode.DONT_INLINE)
    private static Object callBoundary(Object[] args, RootNode root) {
        return root.execute(Truffle.getRuntime().createVirtualFrame(args, root.getFrameDescriptor()));
    }

    private static final int TARGETS = 10000;

    @State(Scope.Thread)
    public static class FirstCallState extends BenchmarkState {

        final CallTarget[] callTargets = new CallTarget[TARGETS];

        @Setup(Level.Invocation)
        public void setup() {
            for (int i = 0; i < TARGETS; i++) {
                callTargets[i] = Truffle.getRuntime().createCallTarget(rootNodes[i % rootNodes.length]);
            }
        }

    }

    @Benchmark
    @OperationsPerInvocation(TARGETS)
    public int firstCall(FirstCallState state) {
        int sum = 0;
        IndirectCallNode callNode = state.indirectCall;
        for (int i = 0; i < TARGETS; i++) {
            sum += (int) callNode.call(state.callTargets[i], state.singleArg);
        }
        return sum;
    }

    @State(Scope.Thread)
    public static class SecondCallState extends FirstCallState {

        @Override
        @Setup(Level.Invocation)
        public void setup() {
            super.setup();
            for (int i = 0; i < TARGETS; i++) {
                indirectCall.call(callTargets[i], singleArg);
            }
        }

    }

    @Benchmark
    @OperationsPerInvocation(TARGETS)
    public int secondCall(SecondCallState state) {
        int sum = 0;
        IndirectCallNode callNode = state.indirectCall;
        for (int i = 0; i < TARGETS; i++) {
            sum += (int) callNode.call(state.callTargets[i], state.singleArg);
        }
        return sum;
    }

    @State(Scope.Thread)
    public static class CallTargetCreateState extends BenchmarkState {

        final CallTarget[] callTargets = new CallTarget[TARGETS];

        @Setup(Level.Invocation)
        public void setup() {
            for (int i = 0; i < TARGETS; i++) {
                callTargets[i] = Truffle.getRuntime().createCallTarget(rootNodes[i % rootNodes.length]);
            }
        }

    }

    @Benchmark
    public Object callTargetCreate() {
        return Truffle.getRuntime().createCallTarget(new RootNode1());
    }

    @Benchmark
    @OperationsPerInvocation(ROOT_CLASSES_LENGTH)
    public Object directCall(BenchmarkState state) {
        DirectCallNode[] callNodes = state.directCallNodes;
        Object[] args = state.singleArg;
        int sum = 0;
        for (int i = 0; i < callNodes.length; i++) {
            DirectCallNode callNode = callNodes[i];
            sum += (int) callNode.call(args);
        }
        return sum;
    }

    @Benchmark
    @OperationsPerInvocation(ROOT_CLASSES_LENGTH)
    public Object indirectCall(BenchmarkState state) {
        CallTarget[] targets = state.callTargets;
        Object[] args = state.singleArg;
        IndirectCallNode callNode = state.indirectCall;
        int sum = 0;
        for (int i = 0; i < targets.length; i++) {
            sum += (int) callNode.call(targets[i], args);
        }
        return sum;
    }

    @Benchmark
    @OperationsPerInvocation(ROOT_CLASSES_LENGTH)
    public Object slowPathCall(BenchmarkState state) {
        CallTarget[] targets = state.callTargets;
        Object[] args = state.singleArg;
        int sum = 0;
        for (int i = 0; i < targets.length; i++) {
            sum += (int) targets[i].call(args);
        }
        return sum;
    }

    /*
     * In order to be representative of a real interpreter we simulate many different root nodes
     * such that the root node call becomes megamorphic, like in a typical interpreter.
     */
    static final Class<?>[] ROOT_CLASSES = new Class<?>[]{
                    RootNode1.class, RootNode2.class,
                    RootNode3.class, RootNode4.class,
                    RootNode5.class, RootNode6.class,
                    RootNode7.class, RootNode8.class,
                    RootNode9.class, RootNode10.class,
    };
    static final int ROOT_CLASSES_LENGTH = 10;

    public abstract static class AbstractRootNode extends RootNode {

        protected AbstractRootNode() {
            super(null);
        }

    }

    public static class RootNode1 extends AbstractRootNode {

        @Override
        public Object execute(VirtualFrame frame) {
            return frame.getArguments()[0];
        }

    }

    public static class RootNode2 extends AbstractRootNode {

        @Override
        public Object execute(VirtualFrame frame) {
            return frame.getArguments()[0];
        }

    }

    public static class RootNode3 extends AbstractRootNode {

        @Override
        public Object execute(VirtualFrame frame) {
            return frame.getArguments()[0];
        }

    }

    public static class RootNode4 extends AbstractRootNode {

        @Override
        public Object execute(VirtualFrame frame) {
            return frame.getArguments()[0];
        }

    }

    public static class RootNode5 extends AbstractRootNode {

        @Override
        public Object execute(VirtualFrame frame) {
            return frame.getArguments()[0];
        }

    }

    public static class RootNode6 extends AbstractRootNode {

        @Override
        public Object execute(VirtualFrame frame) {
            return frame.getArguments()[0];
        }

    }

    public static class RootNode7 extends AbstractRootNode {

        @Override
        public Object execute(VirtualFrame frame) {
            return frame.getArguments()[0];
        }

    }

    public static class RootNode8 extends AbstractRootNode {

        @Override
        public Object execute(VirtualFrame frame) {
            return frame.getArguments()[0];
        }

    }

    public static class RootNode9 extends AbstractRootNode {

        @Override
        public Object execute(VirtualFrame frame) {
            return frame.getArguments()[0];
        }

    }

    public static class RootNode10 extends AbstractRootNode {

        @Override
        public Object execute(VirtualFrame frame) {
            return frame.getArguments()[0];
        }

    }

}
