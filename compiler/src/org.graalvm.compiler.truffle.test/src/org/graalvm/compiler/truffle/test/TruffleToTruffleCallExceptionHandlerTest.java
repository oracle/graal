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
package org.graalvm.compiler.truffle.test;

import static org.graalvm.compiler.core.common.CompilationIdentifier.INVALID_COMPILATION_ID;

import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.debug.DebugHandlersFactory;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.StructuredGraph.AllowAssumptions;
import org.graalvm.compiler.nodes.UnwindNode;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.truffle.compiler.TruffleCompilerImpl;
import org.graalvm.compiler.truffle.common.TruffleCompilerOptions;
import org.graalvm.compiler.truffle.common.TruffleDebugJavaMethod;
import org.graalvm.compiler.truffle.common.TruffleCompilerOptions.TruffleOptionsOverrideScope;
import org.graalvm.compiler.truffle.runtime.DefaultInliningPolicy;
import org.graalvm.compiler.truffle.runtime.GraalTruffleRuntime;
import org.graalvm.compiler.truffle.runtime.OptimizedCallTarget;
import org.graalvm.compiler.truffle.runtime.TruffleInlining;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.RootNode;

import jdk.vm.ci.meta.SpeculationLog;

/**
 * A simple test class verifying that a truffle-2-truffle call never results in the compilation of
 * an exception handler edge if the exception was not seen in the interpreter.
 */
public class TruffleToTruffleCallExceptionHandlerTest {

    private static final GraalTruffleRuntime runtime = (GraalTruffleRuntime) Truffle.getRuntime();
    private static final TruffleCompilerImpl truffleCompiler = (TruffleCompilerImpl) runtime.newTruffleCompiler();

    private final OptimizedCallTarget calleeNoException = (OptimizedCallTarget) runtime.createCallTarget(new RootNode(null) {
        @Override
        public Object execute(VirtualFrame frame) {
            return null;
        }

        @Override
        public String toString() {
            return "CALLEE_NO_EXCEPTION";
        }
    });

    private final OptimizedCallTarget callerNoException = (OptimizedCallTarget) runtime.createCallTarget(new RootNode(null) {

        @Child protected DirectCallNode callNode = runtime.createDirectCallNode(calleeNoException);

        @Override
        public Object execute(VirtualFrame frame) {
            callNode.call(new Object[0]);
            return null;
        }

        @Override
        public String toString() {
            return "CALLER_NO_EXCEPTION";
        }
    });

    private final OptimizedCallTarget calleeWithException = (OptimizedCallTarget) runtime.createCallTarget(new RootNode(null) {
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

    private final OptimizedCallTarget callerWithException = (OptimizedCallTarget) runtime.createCallTarget(new RootNode(null) {

        @Child protected DirectCallNode callNode = runtime.createDirectCallNode(calleeWithException);

        @Override
        public Object execute(VirtualFrame frame) {
            callNode.call(new Object[0]);
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
        OptionValues options = TruffleCompilerOptions.getOptions();
        DebugContext debug = DebugContext.create(options, DebugHandlersFactory.LOADER);
        try (DebugContext.Scope s = debug.scope("TruffleCompilation", new TruffleDebugJavaMethod(compilable))) {
            TruffleInlining inliningDecision = new TruffleInlining(compilable, new DefaultInliningPolicy());
            SpeculationLog speculationLog = compilable.getSpeculationLog();
            return truffleCompiler.getPartialEvaluator().createGraph(debug, compilable, inliningDecision, allowAssumptions, INVALID_COMPILATION_ID, speculationLog, null);
        } catch (Throwable e) {
            throw debug.handle(e);
        }
    }

    @Test
    @SuppressWarnings("try")
    public void testNeverSeenExceptionHandlerSkipped() {
        /*
         * We disable truffle AST inlining to not inline the callee
         */
        try (TruffleOptionsOverrideScope o = TruffleCompilerOptions.overrideOptions(TruffleCompilerOptions.TruffleFunctionInlining, false)) {
            StructuredGraph graph = partialEval(callerNoException, new Object[0], AllowAssumptions.YES);
            Assert.assertEquals(0, graph.getNodes().filter(UnwindNode.class).count());
        }
    }

    @Test
    @SuppressWarnings("try")
    @Ignore
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
        try (TruffleOptionsOverrideScope o = TruffleCompilerOptions.overrideOptions(TruffleCompilerOptions.TruffleFunctionInlining, false)) {
            StructuredGraph graph = partialEval(callerWithException, new Object[0], AllowAssumptions.YES);
            Assert.assertEquals(1, graph.getNodes().filter(UnwindNode.class).count());
        }
    }

}
