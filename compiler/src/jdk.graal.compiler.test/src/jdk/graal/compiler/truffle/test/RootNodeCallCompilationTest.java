/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.truffle.test;

import java.util.ArrayList;
import java.util.List;

import org.graalvm.polyglot.Context;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.runtime.OptimizedCallTarget;

public class RootNodeCallCompilationTest extends PartialEvaluationTest {

    private static final int BYTECODE_INDEX = 42;

    @Before
    public void setup() {
        // Compile only through compileHelper so that interpreted targets stay interpreted.
        setupContext(Context.newBuilder().allowExperimentalOptions(true).option("engine.Compilation", "false"));
    }

    @Test
    public void testInterpretedToCompiledCall() {
        CalleeRootNode callee = new CalleeRootNode();
        OptimizedCallTarget calleeTarget = compileHelper("callee", callee, new Object[0]);
        Assert.assertTrue(calleeTarget.isValid());

        CallerRootNode caller = new CallerRootNode(callee);
        assertFrames((StackSummary) caller.getCallTarget().call(), caller, callee);
        Assert.assertFalse(((OptimizedCallTarget) caller.getCallTarget()).isValid());
    }

    @Test
    public void testCompiledToInterpretedCall() {
        CalleeRootNode callee = new CalleeRootNode();
        CallerRootNode caller = new CallerRootNode(callee);
        OptimizedCallTarget callerTarget = compileHelper("caller", caller, new Object[0]);
        Assert.assertTrue(callerTarget.isValid());
        Assert.assertFalse(((OptimizedCallTarget) callee.getCallTarget()).isValid());

        assertFrames((StackSummary) callerTarget.call(), caller, caller.location, callee);
    }

    @Test
    public void testIndexedIndirectCallIsNeverInline() {
        Assert.assertTrue("indexed indirect calls must remain visible to stack walking",
                        List.of(getTruffleCompiler().getPartialEvaluator().getNeverInlineMethods()).contains(getTypes().OptimizedCallTarget_callIndirect));
    }

    @Test
    public void testCompiledIndexedIndirectCall() {
        CalleeRootNode callee = new CalleeRootNode();
        IndirectCallerRootNode caller = new IndirectCallerRootNode(callee);
        OptimizedCallTarget callerTarget = compileHelper("caller", caller, new Object[0]);
        Assert.assertTrue(callerTarget.isValid());
        Assert.assertFalse(((OptimizedCallTarget) callee.getCallTarget()).isValid());

        assertFrames((StackSummary) callerTarget.call(), caller, caller.call, callee);
    }

    @Test
    public void testCompiledRootCallRequiresInitialization() {
        assertCompiledRootCallRequiresInitialization(false);
    }

    @Test
    public void testCompiledIndirectRootCallRequiresInitialization() {
        assertCompiledRootCallRequiresInitialization(true);
    }

    private void assertCompiledRootCallRequiresInitialization(boolean indirect) {
        RootNode callee = RootNode.createConstantNode(42);
        callee.initializeCallTarget();
        DynamicCallerRootNode caller = new DynamicCallerRootNode(indirect);
        OptimizedCallTarget callerTarget = compileHelper("dynamicCaller", caller, new Object[]{callee});
        Assert.assertTrue(callerTarget.isValid());

        for (int i = 0; i < 3; i++) {
            RootNode next = RootNode.createConstantNode(42);
            next.initializeCallTarget();
            Assert.assertEquals(42, callerTarget.call(new Object[]{next}));
            Assert.assertTrue(callerTarget.isValid());
        }

        RootNode uninitialized = RootNode.createConstantNode(42);
        Assert.assertThrows(IllegalStateException.class, () -> callerTarget.call(new Object[]{uninitialized}));
        Assert.assertFalse(callerTarget.isValid());
    }

    private static final class DynamicCallerRootNode extends RootNode {
        private final boolean indirect;
        @Child private IndirectCallNode call = IndirectCallNode.create();

        DynamicCallerRootNode(boolean indirect) {
            super(null);
            this.indirect = indirect;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            RootNode callee = (RootNode) frame.getArguments()[0];
            if (indirect) {
                return call.call(callee, BYTECODE_INDEX, new Object[0]);
            }
            return callee.call(this, BYTECODE_INDEX, new Object[0]);
        }
    }

    private static void assertFrames(StackSummary summary, CallerRootNode caller, CalleeRootNode callee) {
        assertFrames(summary, caller, caller.location, callee);
    }

    private static void assertFrames(StackSummary summary, RootNode caller, Node expectedCallNode, CalleeRootNode callee) {
        Assert.assertEquals(2, summary.frames.size());
        FrameSummary current = summary.frames.get(0);
        Assert.assertSame(callee.getCallTarget(), current.target);
        Assert.assertNull(current.callNode);
        Assert.assertEquals(-1, current.bytecodeIndex);

        FrameSummary callerFrame = summary.frames.get(1);
        Assert.assertSame(caller.getCallTarget(), callerFrame.target);
        Assert.assertSame(expectedCallNode, callerFrame.callNode);
        Assert.assertEquals(BYTECODE_INDEX, callerFrame.bytecodeIndex);
    }

    private static final class CallerRootNode extends RootNode {
        private final CalleeRootNode callee;
        @Child private Node location = new Node() {
        };

        CallerRootNode(CalleeRootNode callee) {
            super(null);
            this.callee = callee;
            callee.initializeCallTarget();
        }

        @Override
        public Object execute(VirtualFrame frame) {
            return getCallee().call(location, BYTECODE_INDEX, new Object[0]);
        }

        @TruffleBoundary
        private CalleeRootNode getCallee() {
            return callee;
        }
    }

    private static final class IndirectCallerRootNode extends RootNode {
        private final CalleeRootNode callee;
        @Child private IndirectCallNode call = Truffle.getRuntime().createIndirectCallNode();

        IndirectCallerRootNode(CalleeRootNode callee) {
            super(null);
            this.callee = callee;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            return call.call(getCalleeTarget(), BYTECODE_INDEX, new Object[0]);
        }

        @TruffleBoundary
        private CallTarget getCalleeTarget() {
            return callee.getCallTarget();
        }
    }

    private static final class CalleeRootNode extends RootNode {

        CalleeRootNode() {
            super(null);
        }

        @Override
        public Object execute(VirtualFrame frame) {
            return captureFrames();
        }
    }

    @TruffleBoundary
    private static StackSummary captureFrames() {
        List<FrameSummary> frames = new ArrayList<>(2);
        Truffle.getRuntime().iterateFrames(frame -> {
            frames.add(new FrameSummary(frame.getCallTarget(), frame.getCallNode(), frame.getBytecodeIndex()));
            return frames.size() == 2 ? Boolean.TRUE : null;
        });
        return new StackSummary(frames);
    }

    private record StackSummary(List<FrameSummary> frames) {
    }

    private record FrameSummary(CallTarget target, Node callNode, int bytecodeIndex) {
    }
}
