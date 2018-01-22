/*
 * Copyright (c) 2018, Oracle and/or its affiliates.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to
 * endorse or promote products derived from this software without specific prior written
 * permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oracle.truffle.llvm.test.debug;

import java.io.File;
import java.io.IOException;

import org.junit.After;
import static org.junit.Assert.assertEquals;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import com.oracle.truffle.api.debug.Breakpoint;
import com.oracle.truffle.api.debug.DebugStackFrame;
import com.oracle.truffle.api.debug.DebuggerSession;
import com.oracle.truffle.api.debug.SuspendedCallback;
import com.oracle.truffle.api.debug.SuspendedEvent;
import com.oracle.truffle.llvm.test.options.TestOptions;
import com.oracle.truffle.tck.DebuggerTester;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;

public class LLVMDebugTest {

    private static final String OPTION_ENABLE_LVI = "llvm.enableLVI";
    private DebuggerTester tester;

    @Before
    public void before() {
        tester = new DebuggerTester(Context.newBuilder().option(OPTION_ENABLE_LVI, "true"));
    }

    @After
    public void dispose() {
        tester.close();
    }

    @Test
    @Ignore
    public void testReenterArgumentsAndValues() throws Throwable {
        // Test that after a re-enter, arguments are kept and variables are cleared.
        final Source source = loadTestSource("testReenterArgsAndVals");
        final Source bitcode = loadTestBitcode("testReenterArgsAndVals");

        try (DebuggerSession session = tester.startSession()) {
            session.install(Breakpoint.newBuilder(source.getURI()).lineIs(6).build());
            tester.startEval(bitcode);

            expectSuspended((SuspendedEvent event) -> {
                DebugStackFrame frame = event.getTopStackFrame();
                assertEquals(6, frame.getSourceSection().getStartLine());
                // checkArgs(frame, "n", "11", "m", "20");
                // checkStack(frame, "fnc", "n", "11", "m", "20");
                event.prepareStepOver(5);
            });
            expectSuspended((SuspendedEvent event) -> {
                DebugStackFrame frame = event.getTopStackFrame();
                assertEquals(10, frame.getSourceSection().getStartLine());
                // checkArgs(frame, "n", "11", "m", "20");
                // checkStack(frame, "fnc", "n", "9", "m", "10.0", "x", "50.0");
                event.prepareUnwindFrame(frame);
            });
            expectSuspended((SuspendedEvent event) -> {
                DebugStackFrame frame = event.getTopStackFrame();
                assertEquals(3, frame.getSourceSection().getStartLine());
                // checkArgs(frame);
                // checkStack(frame, "main");
            });
            expectSuspended((SuspendedEvent event) -> {
                DebugStackFrame frame = event.getTopStackFrame();
                assertEquals(6, frame.getSourceSection().getStartLine());
                // checkArgs(frame, "n", "11", "m", "20");
                // checkStack(frame, "fnc", "n", "11", "m", "20");
            });
            assertEquals("121", tester.expectDone());
        }
    }

    private static Source loadTestSource(String name) {
        File file = new File(TestOptions.PROJECT_ROOT, "../tests/com.oracle.truffle.llvm.tests.debug/debug/" + name + ".c");
        Source source;
        try {
            file = file.getCanonicalFile();
            source = Source.newBuilder("llvm", file).build();
        } catch (IOException ex) {
            throw new AssertionError(ex);
        }
        return source;
    }

    private static Source loadTestBitcode(String name) {
        File file = new File(TestOptions.TEST_SUITE_PATH, "debug/" + name + "/O0_MEM2REG.bc");
        Source source;
        try {
            source = Source.newBuilder("llvm", file).build();
        } catch (IOException ex) {
            throw new AssertionError(ex);
        }
        return source;
    }

    private void expectSuspended(SuspendedCallback callback) {
        tester.expectSuspended(callback);
    }

}
