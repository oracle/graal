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

import static org.graalvm.compiler.truffle.TruffleCompilerOptions.TruffleCompilationThreshold;

import org.graalvm.compiler.truffle.GraalTruffleRuntime;
import org.graalvm.compiler.truffle.OptimizedCallTarget;
import org.graalvm.compiler.truffle.OptimizedDirectCallNode;
import org.graalvm.compiler.truffle.OptimizedIndirectCallNode;
import org.graalvm.compiler.truffle.TruffleCompilerOptions;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.RootNode;

@SuppressWarnings("try")
public class IndirectCallSiteTest extends TestWithSynchronousCompiling {

    private static final GraalTruffleRuntime runtime = (GraalTruffleRuntime) Truffle.getRuntime();

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

    abstract class DeoptimizeAwareRootNode extends RootNode {

        boolean deoptimized;

        protected DeoptimizeAwareRootNode() {
            super(null);
        }

        @Override
        public Object execute(VirtualFrame frame) {
            boolean wasValid = CompilerDirectives.inCompiledCode();
            Object returnValue = doExecute(frame);
            deoptimized = wasValid && CompilerDirectives.inInterpreter();
            return returnValue;
        }

        protected abstract Object doExecute(VirtualFrame frame);
    }

    class WritesToGlobalState extends DeoptimizeAwareRootNode {

        @Override
        public Object doExecute(VirtualFrame frame) {
            Object arg = frame.getArguments()[0];
            if (arg instanceof String) {
                assertTrue(CompilerDirectives.inInterpreter());
            }
            globalState[0] = arg;
            return null;
        }

        @CompilerDirectives.TruffleBoundary
        void assertTrue(boolean condition) {
            Assert.assertTrue(condition);
        }

        @Override
        public String toString() {
            return "WritesToGlobalState";
        }
    }

    class DirectlyCallsTargetWithArguments extends DeoptimizeAwareRootNode {

        DirectlyCallsTargetWithArguments(OptimizedCallTarget target, Object[] arguments) {
            super();
            this.directCallNode = new OptimizedDirectCallNode(runtime, target);
            this.arguments = arguments;
        }

        @Child OptimizedDirectCallNode directCallNode;
        final Object[] arguments;
        boolean deoptimized;

        @Override
        public Object doExecute(VirtualFrame frame) {
            boolean wasValid = CompilerDirectives.inCompiledCode();
            directCallNode.call(arguments);
            deoptimized = wasValid && CompilerDirectives.inInterpreter();
            return null;
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

    class IndirectCallTargetFromArgument extends DeoptimizeAwareRootNode {

        @Child OptimizedIndirectCallNode indirectCallNode = (OptimizedIndirectCallNode) runtime.createIndirectCallNode();

        @Override
        public Object doExecute(VirtualFrame frame) {
            return indirectCallNode.call((CallTarget) frame.getArguments()[0], new Object[]{LOREM_IPSUM});
        }

        @Override
        public String toString() {
            return "targetWithIndirectCall";
        }
    }

    static final String LOREM_IPSUM = "Lorem ipsum!";

    void assertDeoptimized(OptimizedCallTarget target) {
        DeoptimizeAwareRootNode rootNode = (DeoptimizeAwareRootNode) target.getRootNode();
        Assert.assertTrue(rootNode.deoptimized);
    }

    void assertNotDeoptimized(OptimizedCallTarget target) {
        DeoptimizeAwareRootNode rootNode = (DeoptimizeAwareRootNode) target.getRootNode();
        Assert.assertFalse(rootNode.deoptimized);
    }

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
            final OptimizedCallTarget targetWithIndirectCall = (OptimizedCallTarget) runtime.createCallTarget(new IndirectCallTargetFromArgument());

            for (int i = 0; i < compilationThreshold; i++) {
                targetWithDirectCall.call(new Object[0]);
            }
            assertCompiled(targetWithDirectCall);
            assertNotDeoptimized(targetWithDirectCall);
            assertCompiled(saveArgumentToGlobalState);
            assertNotDeoptimized(saveArgumentToGlobalState);

            for (int i = 0; i < compilationThreshold; i++) {
                targetWithIndirectCall.call(new Object[]{dummyInnerTarget});
            }
            assertCompiled(targetWithIndirectCall);
            assertNotDeoptimized(targetWithIndirectCall);

            globalState[0] = 0;
            targetWithIndirectCall.call(new Object[]{saveArgumentToGlobalState});
            Assert.assertEquals("Global state not updated!", LOREM_IPSUM, globalState[0]);

            assertCompiled(targetWithIndirectCall);
            // Deoptimizes because it's callee was invalidated, but is itself not invalidated
            assertDeoptimized(targetWithIndirectCall);

            // targetWithDirectCall is unaffected due to inlining
            assertCompiled(targetWithDirectCall);
            assertNotDeoptimized(targetWithDirectCall);

            // saveArgumentToGlobalState compilation is delayed by the invalidation
            assertNotCompiled(saveArgumentToGlobalState);
            assertNotDeoptimized(saveArgumentToGlobalState);
            Assert.assertEquals("saveArgumentToGlobalState was not invlidated!", 1, saveArgumentToGlobalState.getCompilationProfile().getInvalidationCount());
        }
    }

    /*
     * Same as previous but has the indirectCallNode explicitly call it's target.
     */
    @Test
    @Ignore("Unexplainable pass. Ignore while investigating.")
    public void testIndirectCallNodeDoesNotDeopOnTypeChangeWithInlining2() {
        try (TruffleCompilerOptions.TruffleOptionsOverrideScope scope = TruffleCompilerOptions.overrideOptions(TruffleCompilerOptions.TruffleFunctionInlining, true)) {
            final int compilationThreshold = TruffleCompilerOptions.getValue(TruffleCompilationThreshold);

            final OptimizedCallTarget saveArgumentToGlobalState = (OptimizedCallTarget) runtime.createCallTarget(new WritesToGlobalState());
            final OptimizedCallTarget targetWithDirectCall = (OptimizedCallTarget) runtime.createCallTarget(new DirectlyCallsTargetWithArguments(saveArgumentToGlobalState, new Object[]{1}));
            final OptimizedCallTarget dummyInnerTarget = (OptimizedCallTarget) runtime.createCallTarget(new DummyTarget());
            final OptimizedCallTarget targetWithIndirectCall = (OptimizedCallTarget) runtime.createCallTarget(new DeoptimizeAwareRootNode() {

                @Child OptimizedIndirectCallNode indirectCallNode = (OptimizedIndirectCallNode) runtime.createIndirectCallNode();

                @Override
                public Object doExecute(VirtualFrame frame) {
                    if (frame.getArguments().length == 0) {
                        return indirectCallNode.call(dummyInnerTarget, new Object[0]);
                    } else {
                        return indirectCallNode.call(saveArgumentToGlobalState, new Object[]{LOREM_IPSUM});
                    }
                }

                @Override
                public String toString() {
                    return "targetWithIndirectCall";
                }
            });

            for (int i = 0; i < compilationThreshold; i++) {
                targetWithDirectCall.call(new Object[0]);
            }
            assertCompiled(targetWithDirectCall);
            assertNotDeoptimized(targetWithDirectCall);
            assertCompiled(saveArgumentToGlobalState);
            assertNotDeoptimized(saveArgumentToGlobalState);

            for (int i = 0; i < compilationThreshold; i++) {
                targetWithIndirectCall.call(new Object[]{});
            }
            assertCompiled(targetWithIndirectCall);
            assertNotDeoptimized(targetWithIndirectCall);

            globalState[0] = 0;
            targetWithIndirectCall.call(new Object[]{"arbitrary Argument"});
            Assert.assertEquals("Global state not updated!", LOREM_IPSUM, globalState[0]);

            assertCompiled(targetWithIndirectCall);
            // This does not deoptimize because the call to saveArgumentToGlobalState with string
            // arguments somehow gets inlined by graal.
            assertDeoptimized(targetWithIndirectCall);

            // targetWithDirectCall is unaffected due to inlining
            assertCompiled(targetWithDirectCall);
            assertNotDeoptimized(targetWithDirectCall);

            // saveArgumentToGlobalState compilation is delayed by the invalidation
            assertNotCompiled(saveArgumentToGlobalState);
            assertNotDeoptimized(saveArgumentToGlobalState);
            Assert.assertEquals("saveArgumentToGlobalState was not invlidated!", 1, saveArgumentToGlobalState.getCompilationProfile().getInvalidationCount());
        }
    }
}
