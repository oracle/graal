/*
 * Copyright (c) 2016, 2016, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package org.graalvm.compiler.truffle.test;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.RootNode;
import org.graalvm.compiler.code.CompilationResult;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.truffle.GraalTruffleCompilationListener;
import org.graalvm.compiler.truffle.GraalTruffleRuntime;
import org.graalvm.compiler.truffle.OptimizedCallTarget;
import org.graalvm.compiler.truffle.OptimizedDirectCallNode;
import org.graalvm.compiler.truffle.OptimizedIndirectCallNode;
import org.graalvm.compiler.truffle.TruffleCompilerOptions;
import org.graalvm.compiler.truffle.TruffleInlining;
import org.junit.Assert;
import org.junit.Test;

import static org.graalvm.compiler.truffle.TruffleCompilerOptions.TruffleCompilationThreshold;

@SuppressWarnings("try")
public class IndirectCallSiteTest extends TestWithSynchronousCompiling {

    private static final GraalTruffleRuntime runtime = (GraalTruffleRuntime) Truffle.getRuntime();

    class InvalidationListener implements GraalTruffleCompilationListener {

        final OptimizedCallTarget expectedInvlidation;
        boolean invalidationHappened;

        InvalidationListener(OptimizedCallTarget expectedInvlidation) {
            this.expectedInvlidation = expectedInvlidation;
            this.invalidationHappened = false;
        }

        @Override
        public void notifyCompilationSplit(OptimizedDirectCallNode callNode) {

        }

        @Override
        public void notifyCompilationQueued(OptimizedCallTarget target) {

        }

        @Override
        public void notifyCompilationDequeued(OptimizedCallTarget target, Object source, CharSequence reason) {

        }

        @Override
        public void notifyCompilationFailed(OptimizedCallTarget target, StructuredGraph graph, Throwable t) {

        }

        @Override
        public void notifyCompilationStarted(OptimizedCallTarget target) {

        }

        @Override
        public void notifyCompilationTruffleTierFinished(OptimizedCallTarget target, TruffleInlining inliningDecision, StructuredGraph graph) {

        }

        @Override
        public void notifyCompilationGraalTierFinished(OptimizedCallTarget target, StructuredGraph graph) {

        }

        @Override
        public void notifyCompilationSuccess(OptimizedCallTarget target, TruffleInlining inliningDecision, StructuredGraph graph, CompilationResult result) {

        }

        @Override
        public void notifyCompilationInvalidated(OptimizedCallTarget target, Object source, CharSequence reason) {
            Assert.assertEquals(expectedInvlidation, target);
            Assert.assertFalse("More than one invalidation", invalidationHappened);
            invalidationHappened = true;
        }

        @Override
        public void notifyCompilationDeoptimized(OptimizedCallTarget target, Frame frame) {

        }

        @Override
        public void notifyShutdown(GraalTruffleRuntime r) {

        }

        @Override
        public void notifyStartup(GraalTruffleRuntime r) {

        }
    }

    @Test
    public void testIndirectCallNodeDoesNotDeopOnFirstCall() {
        final Object[] noArguments = new Object[0];
        final OptimizedCallTarget innerTarget = (OptimizedCallTarget) runtime.createCallTarget(new RootNode(null) {
            @Override
            public Object execute(VirtualFrame frame) {
                return null;
            }
        });
        final OptimizedCallTarget uninitializedInnerTarget = (OptimizedCallTarget) runtime.createCallTarget(new RootNode(null) {
            @Override
            public Object execute(VirtualFrame frame) {
                return null;
            }
        });
        final OptimizedCallTarget outerTarget = (OptimizedCallTarget) runtime.createCallTarget(new RootNode(null) {
            @Child OptimizedIndirectCallNode indirectCallNode = (OptimizedIndirectCallNode) runtime.createIndirectCallNode();

            @Override
            public Object execute(VirtualFrame frame) {
                if (frame.getArguments().length == 0) {
                    return indirectCallNode.call(innerTarget, noArguments);
                } else {
                    return indirectCallNode.call(uninitializedInnerTarget, noArguments);
                }
            }
        });
        final int compilationThreshold = TruffleCompilerOptions.getValue(TruffleCompilationThreshold);
        for (int i = 0; i < compilationThreshold; i++) {
            outerTarget.call(noArguments);
        }
        assertCompiled(outerTarget);
        outerTarget.call(new Object[]{null});
        assertCompiled(outerTarget);
    }

    final Object[] globalState = new Object[1];

    class WritesToGlobalState extends RootNode {
        WritesToGlobalState() {
            super(null);
        }

        @Override
        public Object execute(VirtualFrame frame) {
            Object arg = frame.getArguments()[0];
            if (arg instanceof String) {
                Assert.assertTrue(CompilerDirectives.inInterpreter());
            }
            globalState[0] = arg;
            return null;
        }

        @Override
        public String toString() {
            return "WritesToGlobalState";
        }
    }

    class DirectlyCallsTargetWithArguments extends RootNode {

        DirectlyCallsTargetWithArguments(OptimizedCallTarget target, Object[] arguments) {
            super(null);
            this.directCallNode = new OptimizedDirectCallNode(runtime, target);
            this.arguments = arguments;
        }

        @Child OptimizedDirectCallNode directCallNode;
        final Object[] arguments;

        @Override
        public Object execute(VirtualFrame frame) {

            return directCallNode.call(arguments);
        }

        @Override
        public String toString() {
            return "targetWithDirectCall";
        }
    }

    class DummyTarget extends RootNode {

        protected DummyTarget() {
            super(null);
        }

        @Override
        public Object execute(VirtualFrame frame) {
            return null;
        }

        @Override
        public String toString() {
            return "dummyInnerTarget";
        }
    }

    static final String LOREM_IPSUM = "Lorem ipsum!";

    /*
     * Tests that a CallTarget will not deoptimize if it calls (using an IndirectCallNode) a target
     * previously compiled with a different argument assumption and also inlined into another
     * compiled target.
     */
    @Test
    public void testIndirectCallNodeDoesNotDeopOnTypeChangeWithInlining1() {
        try (TruffleCompilerOptions.TruffleOptionsOverrideScope scope = TruffleCompilerOptions.overrideOptions(TruffleCompilerOptions.TruffleFunctionInlining, true)) {
            final int compilationThreshold = TruffleCompilerOptions.getValue(TruffleCompilationThreshold);

            final OptimizedCallTarget saveArgumentToGlobalState = (OptimizedCallTarget) runtime.createCallTarget(new WritesToGlobalState());
            final OptimizedCallTarget targetWithDirectCall = (OptimizedCallTarget) runtime.createCallTarget(new DirectlyCallsTargetWithArguments(saveArgumentToGlobalState, new Object[]{1}));

            final OptimizedCallTarget dummyInnerTarget = (OptimizedCallTarget) runtime.createCallTarget(new DummyTarget());

            final OptimizedCallTarget targetWithIndirectCall = (OptimizedCallTarget) runtime.createCallTarget(new RootNode(null) {
                @Child OptimizedIndirectCallNode indirectCallNode = (OptimizedIndirectCallNode) runtime.createIndirectCallNode();

                @Override
                public Object execute(VirtualFrame frame) {
                    return indirectCallNode.call((CallTarget) frame.getArguments()[0], new Object[]{LOREM_IPSUM});
                }

                @Override
                public String toString() {
                    return "targetWithIndirectCall";
                }
            });

            final InvalidationListener listener = new InvalidationListener(saveArgumentToGlobalState);
            runtime.addCompilationListener(listener);

            for (int i = 0; i < compilationThreshold; i++) {
                targetWithDirectCall.call(new Object[0]);
            }
            assertCompiled(targetWithDirectCall);
            assertCompiled(saveArgumentToGlobalState);

            for (int i = 0; i < compilationThreshold; i++) {
                targetWithIndirectCall.call(new Object[]{dummyInnerTarget});
            }
            assertCompiled(targetWithIndirectCall);

            globalState[0] = 0;
            targetWithIndirectCall.call(new Object[]{saveArgumentToGlobalState});
            Assert.assertEquals("Global state not updated!", LOREM_IPSUM, globalState[0]);

            assertCompiled(targetWithIndirectCall);
            // targetWithDirectCall is unaffected due to inlining
            assertCompiled(targetWithDirectCall);
            // saveArgumentToGlobalState gets recompiled after invalidation
            assertCompiled(saveArgumentToGlobalState);

            runtime.removeCompilationListener(listener);
        }
    }

    /*
     * Same as previous but has the indirectCallNode explicitly call it's target. This causes the
     * saveArgumentToGlobalState argument assumption to invalidate targetWithIndirectCall for some
     * reason
     */
    @Test
    public void testIndirectCallNodeDoesNotDeopOnTypeChangeWithInlining2() {
        try (TruffleCompilerOptions.TruffleOptionsOverrideScope scope = TruffleCompilerOptions.overrideOptions(TruffleCompilerOptions.TruffleFunctionInlining, true)) {
            final int compilationThreshold = TruffleCompilerOptions.getValue(TruffleCompilationThreshold);

            final OptimizedCallTarget saveArgumentToGlobalState = (OptimizedCallTarget) runtime.createCallTarget(new WritesToGlobalState());
            final OptimizedCallTarget targetWithDirectCall = (OptimizedCallTarget) runtime.createCallTarget(new DirectlyCallsTargetWithArguments(saveArgumentToGlobalState, new Object[]{1}));

            final OptimizedCallTarget dummyInnerTarget = (OptimizedCallTarget) runtime.createCallTarget(new DummyTarget());

            final OptimizedCallTarget targetWithIndirectCall = (OptimizedCallTarget) runtime.createCallTarget(new RootNode(null) {
                @Child OptimizedIndirectCallNode indirectCallNode = (OptimizedIndirectCallNode) runtime.createIndirectCallNode();

                @Override
                public Object execute(VirtualFrame frame) {
                    if (frame.getArguments().length == 0) {
                        return indirectCallNode.call(dummyInnerTarget, new Object[0]);
                    } else {
                        // This is the line!
                        return indirectCallNode.call(saveArgumentToGlobalState, new Object[]{LOREM_IPSUM});
                    }
                }

                @Override
                public String toString() {
                    return "targetWithIndirectCall";
                }
            });

            final InvalidationListener listener = new InvalidationListener(saveArgumentToGlobalState);
            runtime.addCompilationListener(listener);

            for (int i = 0; i < compilationThreshold; i++) {
                targetWithDirectCall.call(new Object[0]);
            }
            assertCompiled(targetWithDirectCall);
            assertCompiled(saveArgumentToGlobalState);

            for (int i = 0; i < compilationThreshold; i++) {
                targetWithIndirectCall.call(new Object[0]);
            }
            assertCompiled(targetWithIndirectCall);

            globalState[0] = 0;
            targetWithIndirectCall.call(new Object[]{null});
            Assert.assertEquals("Global state not updated!", LOREM_IPSUM, globalState[0]);

            assertCompiled(targetWithIndirectCall);
            // targetWithDirectCall is unaffected due to inlining
            assertCompiled(targetWithDirectCall);
            // saveArgumentToGlobalState gets recompiled after invalidation
            assertCompiled(saveArgumentToGlobalState);

            runtime.removeCompilationListener(listener);
        }
    }
}
