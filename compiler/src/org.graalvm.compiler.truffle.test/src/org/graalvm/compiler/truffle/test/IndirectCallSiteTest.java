/*
 * Copyright (c) 2016, 2021, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.test;

import org.graalvm.compiler.truffle.options.PolyglotCompilerOptions;
import org.graalvm.compiler.truffle.runtime.GraalTruffleRuntime;
import org.graalvm.compiler.truffle.runtime.OptimizedCallTarget;
import org.graalvm.compiler.truffle.runtime.OptimizedDirectCallNode;
import org.graalvm.compiler.truffle.runtime.OptimizedIndirectCallNode;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.nodes.RootNode;

@SuppressWarnings("try")
public class IndirectCallSiteTest extends TestWithSynchronousCompiling {

    private static final GraalTruffleRuntime runtime = (GraalTruffleRuntime) Truffle.getRuntime();

    @Before
    @Override
    public void before() {
        setupContext("engine.MultiTier", "false");
        globalState[0] = null;
    }

    @Test
    public void testIndirectCallNodeDoesNotDeopOnFirstCall() {
        final Object[] noArguments = new Object[0];
        final OptimizedCallTarget innerTarget = (OptimizedCallTarget) new RootNode(null) {
            @Override
            public Object execute(VirtualFrame frame) {
                return null;
            }
        }.getCallTarget();
        final OptimizedCallTarget uninitializedInnerTarget = (OptimizedCallTarget) new RootNode(null) {
            @Override
            public Object execute(VirtualFrame frame) {
                return null;
            }
        }.getCallTarget();
        final OptimizedCallTarget outerTarget = (OptimizedCallTarget) new RootNode(null) {
            @Child OptimizedIndirectCallNode indirectCallNode = (OptimizedIndirectCallNode) runtime.createIndirectCallNode();

            @Override
            public Object execute(VirtualFrame frame) {
                if (frame.getArguments().length == 0) {
                    return indirectCallNode.call(innerTarget, noArguments);
                } else {
                    return indirectCallNode.call(uninitializedInnerTarget, noArguments);
                }
            }
        }.getCallTarget();
        final int compilationThreshold = outerTarget.getOptionValue(PolyglotCompilerOptions.SingleTierCompilationThreshold);
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
        boolean wasValid;

        protected DeoptimizeAwareRootNode() {
            super(null);
        }

        @Override
        public Object execute(VirtualFrame frame) {
            wasValid = CompilerDirectives.inCompiledCode();
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
            globalState[0] = arg;
            return arg;
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
            this.directCallNode = (OptimizedDirectCallNode) GraalTruffleRuntime.getRuntime().createDirectCallNode(target);
            this.arguments = arguments;
        }

        @Child OptimizedDirectCallNode directCallNode;
        final Object[] arguments;

        @Override
        public Object doExecute(VirtualFrame frame) {
            wasValid = CompilerDirectives.inCompiledCode();
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
            return indirectCallNode.call((CallTarget) frame.getArguments()[0], LOREM_IPSUM);
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

    /**
     * Tests that a {@link CallTarget} will not deoptimize if it calls (using an
     * {@link IndirectCallNode}) a target previously compiled with a different argument assumption
     * and also inlined into another compiled target.
     */
    @Test
    public void testIndirectCallNodeDoesNotDeoptOnTypeChangeWithInlining1() {
        final OptimizedCallTarget toInterpreterOnString = (OptimizedCallTarget) new WritesToGlobalState().getCallTarget();
        final Object[] directArguments = new Object[]{1};
        final OptimizedCallTarget directCall = (OptimizedCallTarget) new DirectlyCallsTargetWithArguments(toInterpreterOnString, directArguments).getCallTarget();
        final OptimizedCallTarget noOp = (OptimizedCallTarget) new DummyTarget().getCallTarget();
        final OptimizedCallTarget indirectCall = (OptimizedCallTarget) new IndirectCallTargetFromArgument().getCallTarget();

        final int compilationThreshold = toInterpreterOnString.getOptionValue(PolyglotCompilerOptions.SingleTierCompilationThreshold);

        for (int i = 0; i < compilationThreshold; i++) {
            directCall.call();
        }
        // make sure the direct call target is compiled too not just inlined
        for (int i = 0; i < compilationThreshold; i++) {
            toInterpreterOnString.callDirect(null, directArguments);
        }
        assertCompiled(directCall);
        assertNotDeoptimized(directCall);
        assertCompiled(toInterpreterOnString);
        assertNotDeoptimized(toInterpreterOnString);

        for (int i = 0; i < compilationThreshold; i++) {
            indirectCall.call(noOp);
        }
        assertCompiled(indirectCall);
        assertNotDeoptimized(indirectCall);

        indirectCall.call(toInterpreterOnString);
        Assert.assertEquals("Global state not updated!", LOREM_IPSUM, globalState[0]);

        assertCompiled(indirectCall);

        // targetWithDirectCall is unaffected by inlining
        assertCompiled(directCall);
        assertNotDeoptimized(directCall);

        // saveArgumentToGlobalState compilation is delayed by the invalidation
        assertNotCompiled(toInterpreterOnString);
        assertNotDeoptimized(toInterpreterOnString);
    }

    @Test
    public void testIndirectCallDoesNotDeoptInliningDirectCaller() {
        final OptimizedCallTarget callee = (OptimizedCallTarget) RootNode.createConstantNode(0).getCallTarget();
        final Object[] directArguments = new Object[]{"direct arg"};
        final OptimizedCallTarget directCall = (OptimizedCallTarget) new DirectlyCallsTargetWithArguments(callee, directArguments).getCallTarget();
        final OptimizedCallTarget indirectCall = (OptimizedCallTarget) new IndirectCallTargetFromArgument().getCallTarget();
        final int compilationThreshold = callee.getOptionValue(PolyglotCompilerOptions.SingleTierCompilationThreshold);

        try (DeoptInvalidateListener directListener = new DeoptInvalidateListener(runtime, directCall)) {
            for (int i = 0; i < compilationThreshold; i++) {
                directCall.call();
            }
            // make sure the direct call target is compiled too not just inlined
            for (int i = 0; i < compilationThreshold; i++) {
                callee.call(directArguments);
            }

            assertCompiled(callee);
            directListener.assertValid();

            indirectCall.call(callee);

            // This works because arguments profiling is skipped for inlined direct calls, and so
            // the callee arguments profiling assumption of callee is not registered for directCall.
            directListener.assertValid();
        }
    }

    @Test
    public void testIndirectCallDoesNotDeoptNotInliningDirectCaller() {
        setupContext("engine.MultiTier", "false", "engine.Inlining", "false");

        final OptimizedCallTarget callee = (OptimizedCallTarget) RootNode.createConstantNode(0).getCallTarget();
        final Object[] directArguments = new Object[]{"direct arg"};
        final OptimizedCallTarget directCall = (OptimizedCallTarget) new DirectlyCallsTargetWithArguments(callee, directArguments).getCallTarget();
        final OptimizedCallTarget indirectCall = (OptimizedCallTarget) new IndirectCallTargetFromArgument().getCallTarget();
        final int compilationThreshold = callee.getOptionValue(PolyglotCompilerOptions.SingleTierCompilationThreshold);

        try (DeoptInvalidateListener directListener = new DeoptInvalidateListener(runtime, directCall)) {
            for (int i = 0; i < compilationThreshold; i++) {
                directCall.call();
            }
            // make sure the direct call target is compiled too not just inlined
            for (int i = 0; i < compilationThreshold; i++) {
                callee.call(directArguments);
            }

            assertCompiled(callee);
            directListener.assertValid();

            indirectCall.call(callee);

            // This only works if the indirect call does not invalidate the argument profile
            directListener.assertValid();
        }
    }

    @Test
    public void testIndirectCallDoesNotDeoptCallee() {
        final OptimizedCallTarget callee = (OptimizedCallTarget) RootNode.createConstantNode(0).getCallTarget();
        final Object[] directArguments = new Object[]{"direct arg"};
        final OptimizedCallTarget directCall = (OptimizedCallTarget) new DirectlyCallsTargetWithArguments(callee, directArguments).getCallTarget();
        final OptimizedCallTarget indirectCall = (OptimizedCallTarget) new IndirectCallTargetFromArgument().getCallTarget();
        final int compilationThreshold = callee.getOptionValue(PolyglotCompilerOptions.SingleTierCompilationThreshold);

        try (DeoptInvalidateListener calleeListener = new DeoptInvalidateListener(runtime, callee)) {
            // make sure the direct callee is compiled too not just inlined
            for (int i = 0; i < compilationThreshold; i++) {
                directCall.call();
            }
            // make sure the direct call target is compiled too not just inlined
            for (int i = 0; i < compilationThreshold; i++) {
                callee.call(directArguments);
            }

            assertCompiled(directCall);
            calleeListener.assertValid();

            indirectCall.call(callee);

            // This only works if the indirect call does not invalidate the argument profile
            calleeListener.assertValid();
        }
    }
}
