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
import org.junit.Test;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.tck.tests.TruffleTestAssumptions;

/**
 * Test that {@link CompilerDirectives#transferToInterpreter()} does not trigger deopt cycle
 * detection. The test first deopts "deopt cycle detection threshold"-times using
 * {@link CompilerDirectives#transferToInterpreterAndInvalidate()}. This does not trigger the
 * detection, because it is not enabled. Then it uses
 * {@link CompilerDirectives#transferToInterpreter()} to deopt, explicitly invalidates the code and
 * deopts repeatedly again. This must not trigger the deopt cycle detection.
 */
public class GR68545Test {
    private static final int DEOPT_CYCLE_DETECTION_THRESHOLD = 15;

    static class AlwaysDeoptNoInvalidate extends RootNode {

        @CompilerDirectives.CompilationFinal private Assumption assumption = Assumption.create("Test assumption");

        AlwaysDeoptNoInvalidate() {
            super(null);
        }

        @CompilerDirectives.TruffleBoundary
        static void boundaryMethod() {

        }

        @Override
        public Object execute(VirtualFrame frame) {
            if (assumption.isValid()) {
                int arg = (int) frame.getArguments()[0];
                int threshold = 15;
                if (arg < threshold) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                }
                // call boundary method to prevent compiler from moving the following deoptimization
                // up
                boundaryMethod();
                CompilerDirectives.transferToInterpreter();
            }
            return null;
        }

        @Override
        public String getName() {
            return "AlwaysDeoptNoInvalidate";
        }

        @Override
        public String toString() {
            return getName();
        }
    }

    @Test
    public void testAlwaysDeoptNoInvalidate() {
        TruffleTestAssumptions.assumeOptimizingRuntime();
        TruffleTestAssumptions.assumeDeoptLoopDetectionAvailable();
        try (Context context = Context.newBuilder().allowExperimentalOptions(true) //
                        .option("engine.CompilationFailureAction", "Throw") //
                        .option("engine.BackgroundCompilation", "false") //
                        .option("engine.CompileImmediately", "true") //
                        .option("compiler.DeoptCycleDetectionThreshold", String.valueOf(DEOPT_CYCLE_DETECTION_THRESHOLD)).build()) {
            context.enter();
            AlwaysDeoptNoInvalidate alwaysDeoptNoInvalidate = new AlwaysDeoptNoInvalidate();
            CallTarget callTarget = alwaysDeoptNoInvalidate.getCallTarget();
            for (int i = 0; i < DEOPT_CYCLE_DETECTION_THRESHOLD; i++) {
                callTarget.call(i);
            }
            callTarget.call(DEOPT_CYCLE_DETECTION_THRESHOLD);
            alwaysDeoptNoInvalidate.assumption.invalidate("Test reason");
            alwaysDeoptNoInvalidate.assumption = Assumption.create();
            callTarget.call(DEOPT_CYCLE_DETECTION_THRESHOLD + 1);
            for (int i = 0; i < 100; i++) {
                callTarget.call(DEOPT_CYCLE_DETECTION_THRESHOLD + 2 + i);
            }
        }
    }
}
