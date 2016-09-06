/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.truffle.test;

import org.junit.Assert;
import org.junit.Test;

import com.oracle.graal.debug.Debug;
import com.oracle.graal.debug.Debug.Scope;
import com.oracle.graal.nodes.StructuredGraph;
import com.oracle.graal.nodes.StructuredGraph.AllowAssumptions;
import com.oracle.graal.nodes.UnwindNode;
import com.oracle.graal.options.OptionValue;
import com.oracle.graal.options.OptionValue.OverrideScope;
import com.oracle.graal.truffle.DefaultInliningPolicy;
import com.oracle.graal.truffle.DefaultTruffleCompiler;
import com.oracle.graal.truffle.GraalTruffleRuntime;
import com.oracle.graal.truffle.OptimizedCallTarget;
import com.oracle.graal.truffle.TruffleCompiler;
import com.oracle.graal.truffle.TruffleCompilerOptions;
import com.oracle.graal.truffle.TruffleDebugJavaMethod;
import com.oracle.graal.truffle.TruffleInlining;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.RootNode;

/**
 * A simple test class verifying that a truffle-2-truffle call never results in the compilation of
 * an exception handler edge if the exception was not seen in the interpreter.
 */
public class TruffleToTruffleCallExceptionHandlerTest {

    private static final GraalTruffleRuntime runtime = (GraalTruffleRuntime) Truffle.getRuntime();
    private static final TruffleCompiler truffleCompiler = DefaultTruffleCompiler.create(runtime);

    private final OptimizedCallTarget calleeNoException = (OptimizedCallTarget) runtime.createCallTarget(new RootNode(TruffleLanguage.class, null, null) {
        @Override
        public Object execute(VirtualFrame frame) {
            return null;
        }

        @Override
        public String toString() {
            return "CALLEE_NO_EXCEPTION";
        }
    });

    private final OptimizedCallTarget callerNoException = (OptimizedCallTarget) runtime.createCallTarget(new RootNode(TruffleLanguage.class, null, null) {

        @Child protected DirectCallNode callNode = runtime.createDirectCallNode(calleeNoException);

        @Override
        public Object execute(VirtualFrame frame) {
            callNode.call(frame, new Object[0]);
            return null;
        }

        @Override
        public String toString() {
            return "CALLER_NO_EXCEPTION";
        }
    });

    private final OptimizedCallTarget calleeWithException = (OptimizedCallTarget) runtime.createCallTarget(new RootNode(TruffleLanguage.class, null, null) {
        private boolean called;

        @Override
        public Object execute(VirtualFrame frame) {
            if (!called) {
                called = true;
                throw new RuntimeException();
            }
            return null;
        }

        @Override
        public String toString() {
            return "CALLEE_EXCEPTION";
        }
    });

    private final OptimizedCallTarget callerWithException = (OptimizedCallTarget) runtime.createCallTarget(new RootNode(TruffleLanguage.class, null, null) {

        @Child protected DirectCallNode callNode = runtime.createDirectCallNode(calleeWithException);

        @Override
        public Object execute(VirtualFrame frame) {
            callNode.call(frame, new Object[0]);
            return null;
        }

        @Override
        public String toString() {
            return "CALLER_EXCEPTION";
        }
    });

    @SuppressWarnings("try")
    private static StructuredGraph partialEval(OptimizedCallTarget compilable, Object[] arguments, AllowAssumptions allowAssumptions) {
        compilable.call(arguments);
        compilable.call(arguments);
        compilable.call(arguments);
        try (Scope s = Debug.scope("TruffleCompilation", new TruffleDebugJavaMethod(compilable))) {
            return truffleCompiler.getPartialEvaluator().createGraph(compilable, new TruffleInlining(compilable, new DefaultInliningPolicy()), allowAssumptions);
        } catch (Throwable e) {
            throw Debug.handle(e);
        }
    }

    @Test
    @SuppressWarnings("try")
    public void testNeverSeenExceptionHandlerSkipped() {
        /*
         * We disable truffle AST inlining to not inline the callee
         */
        try (OverrideScope o = OptionValue.override(TruffleCompilerOptions.TruffleFunctionInlining, false)) {
            StructuredGraph graph = partialEval(callerNoException, new Object[0], AllowAssumptions.YES);
            Assert.assertEquals(0, graph.getNodes().filter(UnwindNode.class).count());
        }
    }

    @Test
    @SuppressWarnings("try")
    public void testExceptionOnceCompileExceptionHandler() {
        /*
         * call the function at least once so the exception profile will record an exception and the
         * partial evaluator will compile the exception handler edge
         */
        try {
            calleeWithException.callDirect(new Object());
            Assert.fail();
        } catch (Throwable t) {
            Assert.assertTrue(t instanceof RuntimeException);
        }

        /*
         * We disable truffle AST inlining to not inline the callee
         */
        try (OverrideScope o = OptionValue.override(TruffleCompilerOptions.TruffleFunctionInlining, false)) {
            StructuredGraph graph = partialEval(callerWithException, new Object[0], AllowAssumptions.YES);
            Assert.assertEquals(1, graph.getNodes().filter(UnwindNode.class).count());
        }
    }

}
