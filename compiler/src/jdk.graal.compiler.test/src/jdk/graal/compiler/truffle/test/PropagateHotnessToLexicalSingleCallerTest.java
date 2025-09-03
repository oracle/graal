/*
 * Copyright (c) 2022, 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.util.function.Function;

import org.graalvm.polyglot.Context;
import org.junit.Assert;
import org.junit.Test;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.LoopNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RepeatingNode;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.runtime.OptimizedCallTarget;
import com.oracle.truffle.runtime.OptimizedTruffleRuntime;

public class PropagateHotnessToLexicalSingleCallerTest extends TestWithSynchronousCompiling {

    @Override
    protected Context.Builder newContextBuilder() {
        Context.Builder builder = super.newContextBuilder();
        builder.option("engine.PropagateLoopCountToLexicalSingleCaller", Boolean.TRUE.toString());
        builder.option("engine.PropagateLoopCountToLexicalSingleCallerMaxDepth", Integer.toString(3));
        return builder;
    }

    static class SimpleLoopNode extends Node implements RepeatingNode {
        private int loopCount = 0;
        static final int LOOP_LIMIT = 100;

        @Override
        public boolean executeRepeating(VirtualFrame frame) {
            return loopCount++ < LOOP_LIMIT;
        }
    }

    abstract static class NamedRootNode extends RootNode {

        private final FrameDescriptor parentFrameDescriptor;
        private final String name;

        protected NamedRootNode(String name, FrameDescriptor parentFrameDescriptor) {
            super(null);
            this.parentFrameDescriptor = parentFrameDescriptor;
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }

        @Override
        public FrameDescriptor getParentFrameDescriptor() {
            return parentFrameDescriptor;
        }
    }

    static class RootNodeWithLoop extends NamedRootNode {

        @Child LoopNode loopNode = OptimizedTruffleRuntime.getRuntime().createLoopNode(new SimpleLoopNode());

        protected RootNodeWithLoop(String name, FrameDescriptor parentFrameDescriptor) {
            super(name, parentFrameDescriptor);
        }

        @Override
        public Object execute(VirtualFrame frame) {
            loopNode.execute(frame);
            return 42;
        }
    }

    static class CallerRootNode extends NamedRootNode {

        private final Function<FrameDescriptor, NamedRootNode> rootNodeFactory;
        @Child DirectCallNode callNode;
        OptimizedCallTarget target;

        protected CallerRootNode(String name, Function<FrameDescriptor, NamedRootNode> rootNodeFactory, FrameDescriptor parentFrameDescriptor) {
            super(name, parentFrameDescriptor);
            this.rootNodeFactory = rootNodeFactory;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            if (callNode == null) {
                createCallNode(frame.getFrameDescriptor());
            }
            callNode.call();
            return 42;
        }

        @CompilerDirectives.TruffleBoundary
        private void createCallNode(FrameDescriptor frameDescriptor) {
            target = (OptimizedCallTarget) rootNodeFactory.apply(frameDescriptor).getCallTarget();
            callNode = insert(OptimizedTruffleRuntime.getRuntime().createDirectCallNode(target));
        }
    }

    @Test
    public void basicTest() {
        final String callerName = "Caller";
        final String calleeName = "Callee";
        CallerRootNode callerRootNode = new CallerRootNode(callerName, frameDescriptor -> new RootNodeWithLoop(calleeName, frameDescriptor), null);
        OptimizedCallTarget callerTarget = (OptimizedCallTarget) callerRootNode.getCallTarget();
        compile(callerTarget);
        Assert.assertTrue(callerTarget.getCallAndLoopCount() > callerRootNode.target.getCallAndLoopCount());
    }

    @Test
    public void basicNoReorderTest() {
        final String callerName = "Caller";
        final String calleeName = "Callee";
        CallerRootNode callerRootNode = new CallerRootNode(callerName, _ -> new RootNodeWithLoop(calleeName, null), null);
        OptimizedCallTarget callTarget = ((OptimizedCallTarget) callerRootNode.getCallTarget());
        compile(callTarget);
        Assert.assertTrue(callTarget.getCallAndLoopCount() < callerRootNode.target.getCallAndLoopCount());
    }

    @Test
    public void withIntermediateTest() {
        final String callerName = "Caller";
        final String intermediateName = "Intermediate";
        final String calleeName = "Callee";
        CallerRootNode callerRootNode = new CallerRootNode(callerName, frameDescriptor -> {
            return new CallerRootNode(intermediateName, _ -> {
                return new RootNodeWithLoop(calleeName, frameDescriptor);
            }, null);
        }, null);
        OptimizedCallTarget caller = (OptimizedCallTarget) callerRootNode.getCallTarget();
        compile(caller);
        OptimizedCallTarget intermediate = callerRootNode.target;
        Assert.assertTrue(caller.getCallAndLoopCount() > intermediate.getCallAndLoopCount());
        OptimizedCallTarget callee = ((CallerRootNode) intermediate.getRootNode()).target;
        Assert.assertTrue(caller.getCallAndLoopCount() > callee.getCallAndLoopCount());
        Assert.assertTrue(intermediate.getCallAndLoopCount() < callee.getCallAndLoopCount());
    }

    @Test
    public void withIntermediateTangledTest() {
        final String callerName = "Caller";
        final String intermediateName = "Intermediate";
        final String calleeName = "Callee";
        CallerRootNode callerRootNode = new CallerRootNode(callerName, callerFD -> {
            return new CallerRootNode(intermediateName, intermediateFD -> {
                return new RootNodeWithLoop(calleeName, intermediateFD);
            }, callerFD);
        }, null);
        OptimizedCallTarget caller = (OptimizedCallTarget) callerRootNode.getCallTarget();
        compile(caller);
        OptimizedCallTarget intermediate = callerRootNode.target;
        Assert.assertTrue(caller.getCallAndLoopCount() > intermediate.getCallAndLoopCount());
        OptimizedCallTarget callee = ((CallerRootNode) intermediate.getRootNode()).target;
        Assert.assertTrue(caller.getCallAndLoopCount() > callee.getCallAndLoopCount());
        Assert.assertTrue(intermediate.getCallAndLoopCount() > callee.getCallAndLoopCount());
    }

    @Test
    public void testDepth() {
        final String name = "Caller";
        CallerRootNode callerRootNode = new CallerRootNode(name + "0", frameDescriptor0 -> {
            return new CallerRootNode(name + "1", _ -> {
                return new CallerRootNode(name + "2", _ -> {
                    return new CallerRootNode(name + "3", _ -> {
                        return new CallerRootNode(name + "4", _ -> {
                            return new RootNodeWithLoop("loop", frameDescriptor0);
                        }, null);
                    }, null);
                }, null);
            }, null);
        }, null);
        OptimizedCallTarget callTarget = ((OptimizedCallTarget) callerRootNode.getCallTarget());
        compile(callTarget);
        Assert.assertEquals(callTarget.getCallAndLoopCount(), callerRootNode.target.getCallAndLoopCount());
    }

    private static void compile(RootCallTarget callTarget) {
        for (int i = 0; i < LAST_TIER_THRESHOLD; i++) {
            callTarget.call();
        }
    }
}
