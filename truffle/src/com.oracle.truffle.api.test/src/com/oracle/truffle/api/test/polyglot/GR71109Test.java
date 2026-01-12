/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.test.polyglot;

import org.graalvm.polyglot.Context;
import org.junit.Assert;
import org.junit.Test;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.tck.tests.TruffleTestAssumptions;

/**
 * Test that direct calls to a target that always throws an exception does not cause a
 * deoptimization cycle.
 */
public class GR71109Test {
    private static final int DEOPT_CYCLE_DETECTION_THRESHOLD = 15;

    static class Callee extends RootNode {

        Callee() {
            super(null);
        }

        @Override
        public Object execute(VirtualFrame frame) {
            throw getTestException();
        }

        @CompilerDirectives.TruffleBoundary
        private static RuntimeException getTestException() {
            return new RuntimeException("Test exception");
        }

        @Override
        public String getName() {
            return "callee";
        }

        @Override
        public String toString() {
            return getName();
        }
    }

    static class Caller extends RootNode {

        private final DirectCallNode callNode;

        Caller(CallTarget callee) {
            super(null);
            callNode = Truffle.getRuntime().createDirectCallNode(callee);
        }

        @Override
        public Object execute(VirtualFrame frame) {
            return callNode.call(frame.getArguments());
        }

        @Override
        public String getName() {
            return "caller";
        }

        @Override
        public String toString() {
            return getName();
        }
    }

    @Test
    public void testDirectCallToAlwaysThrowException() {
        TruffleTestAssumptions.assumeOptimizingRuntime();
        TruffleTestAssumptions.assumeDeoptLoopDetectionAvailable();
        try (Context context = Context.newBuilder().allowExperimentalOptions(true) //
                        .option("engine.CompilationFailureAction", "Throw") //
                        .option("engine.BackgroundCompilation", "false") //
                        .option("engine.CompileImmediately", "true") //
                        .option("compiler.Inlining", "false") //
                        .option("engine.CompileOnly", "caller") //
                        .option("compiler.DeoptCycleDetectionThreshold", String.valueOf(DEOPT_CYCLE_DETECTION_THRESHOLD)) //
                        .build()) {
            context.enter();
            /*
             * Compilation is limited to the caller, because immediate compilation of the callee
             * would initialize its return profile. We want to test that the return profile of the
             * callee is also initialized (to invalid return profile) as the result of throwing the
             * exception. Initialized return profile of the callee is needed for direct C2C and C2I
             * calls on SVM, otherwise the call causes a deopt.
             */
            Caller caller = new Caller(new Callee().getCallTarget());
            CallTarget callTarget = caller.getCallTarget();
            for (int i = 0; i < DEOPT_CYCLE_DETECTION_THRESHOLD + 100; i++) {
                try {
                    callTarget.call(i);
                    Assert.fail();
                } catch (RuntimeException e) {
                    if (!"Test exception".equals(e.getMessage())) {
                        throw e;
                    }
                }
            }
        }
    }

}
