/*
 * Copyright (c) 2016, 2022, Oracle and/or its affiliates. All rights reserved.
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

import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.UnwindNode;
import org.graalvm.polyglot.Context;
import org.junit.Assert;
import org.junit.Test;

import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.runtime.OptimizedTruffleRuntime;
import com.oracle.truffle.runtime.OptimizedCallTarget;

/**
 * A simple test class verifying that a truffle-2-truffle call never results in the compilation of
 * an exception handler edge if the exception was not seen in the interpreter.
 */
public class TruffleToTruffleCallExceptionHandlerTest extends PartialEvaluationTest {

    private static final OptimizedTruffleRuntime runtime = (OptimizedTruffleRuntime) Truffle.getRuntime();

    private static final class Compilables {

        final OptimizedCallTarget calleeNoException = (OptimizedCallTarget) new RootNode(null) {
            @Override
            public Object execute(VirtualFrame frame) {
                return null;
            }

            @Override
            public String toString() {
                return "CALLEE_NO_EXCEPTION";
            }
        }.getCallTarget();

        final OptimizedCallTarget callerNoException = (OptimizedCallTarget) new RootNode(null) {

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
        }.getCallTarget();

        final OptimizedCallTarget calleeWithException = (OptimizedCallTarget) new RootNode(null) {
            @Override
            public Object execute(VirtualFrame frame) {
                throw new RuntimeException();
            }

            @Override
            public String toString() {
                return "CALLEE_EXCEPTION";
            }
        }.getCallTarget();

        final OptimizedCallTarget callerWithException = (OptimizedCallTarget) new RootNode(null) {

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
        }.getCallTarget();
    }

    @Test
    @SuppressWarnings("try")
    public void testNeverSeenExceptionHandlerSkipped() {
        /*
         * We disable truffle AST inlining to not inline the callee
         */
        setupContext(Context.newBuilder().allowAllAccess(true).allowExperimentalOptions(true).option("compiler.Inlining", "false").build());
        Compilables compilables = new Compilables();
        StructuredGraph graph = partialEval(compilables.callerNoException, new Object[0]);
        Assert.assertEquals(0, graph.getNodes().filter(UnwindNode.class).count());
    }

    @Test
    @SuppressWarnings("try")
    public void testExceptionOnceCompileExceptionHandler() {
        preventProfileCalls = true;
        try {
            /*
             * We disable truffle AST inlining to not inline the callee
             */
            setupContext(Context.newBuilder().allowAllAccess(true).allowExperimentalOptions(true).option("compiler.Inlining", "false").build());
            Compilables compilables = new Compilables();

            /*
             * call the function at least once so the exception profile will record an exception and
             * the partial evaluator will compile the exception handler edge
             */
            try {
                compilables.callerWithException.callDirect(null, new Object());
                Assert.fail();
            } catch (Throwable t) {
                Assert.assertTrue(t instanceof RuntimeException);
            }

            StructuredGraph graph = partialEval(compilables.callerWithException, new Object[0]);
            Assert.assertEquals(1, graph.getNodes().filter(UnwindNode.class).count());
        } finally {
            preventProfileCalls = false;
        }
    }

}
