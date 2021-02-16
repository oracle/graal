/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.tests.debug;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

import com.oracle.truffle.api.debug.Breakpoint;
import com.oracle.truffle.api.debug.DebugScope;
import com.oracle.truffle.api.debug.DebugStackFrame;
import com.oracle.truffle.api.debug.DebugValue;
import com.oracle.truffle.api.debug.DebuggerSession;
import com.oracle.truffle.api.debug.SuspendedCallback;
import com.oracle.truffle.api.debug.SuspendedEvent;
import com.oracle.truffle.llvm.runtime.LLVMLanguage;
import com.oracle.truffle.llvm.runtime.options.SulongEngineOption;
import com.oracle.truffle.llvm.tests.pipe.CaptureNativeOutput;
import com.oracle.truffle.tck.DebuggerTester;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public abstract class LLVMDebugTestBase {

    private static final String LANG_ID = LLVMLanguage.ID;

    private static final String TRACE_EXT = ".txt";
    public static final String TEST_FOLDER_EXT = ".dir";
    private static final String OPTION_LAZY_PARSING = "llvm.lazyParsing";

    LLVMDebugTestBase(String testName, String configuration) {
        this.testName = testName;
        this.configuration = configuration;
    }

    private final String testName;
    private final String configuration;

    private DebuggerTester tester;

    abstract void setContextOptions(Context.Builder contextBuilder);

    abstract Path getBitcodePath();

    abstract Path getSourcePath();

    abstract Path getTracePath();

    String getTestName() {
        return testName;
    }

    boolean isCxx() {
        return testName.endsWith(".cpp");
    }

    @Before
    public void before() {
        final Context.Builder contextBuilder = Context.newBuilder(LANG_ID);
        contextBuilder.allowAllAccess(true);
        contextBuilder.option(OPTION_LAZY_PARSING, String.valueOf(false));
        if (isCxx()) {
            contextBuilder.option(SulongEngineOption.LOAD_CXX_LIBRARIES_NAME, "true");
        }
        setContextOptions(contextBuilder);
        tester = new DebuggerTester(contextBuilder);
    }

    @After
    public void dispose() {
        tester.close();
    }

    private static Source loadSource(File file) {
        Source source;
        try {
            final File canonicalFile = file.getCanonicalFile();
            source = Source.newBuilder(LANG_ID, canonicalFile).build();
        } catch (IOException ex) {
            throw new AssertionError("Could not load source: " + file.getPath(), ex);
        }
        return source;
    }

    protected Source loadOriginalSource() {
        final File file = getSourcePath().resolve(testName).toFile();
        Assert.assertTrue("Locate Source " + file, file.exists());
        return loadSource(file);
    }

    protected Source loadBitcodeSource() {
        final File file = getBitcodePath().resolve(Paths.get(testName + ".dir", configuration)).toFile();
        Assert.assertTrue("Locate Bitcode " + file, file.exists());
        return loadSource(file);
    }

    private Trace readTrace() {
        final Path path = getTracePath().resolve(testName + TRACE_EXT);
        Assert.assertTrue("Locate Trace " + path, path.toFile().exists());
        return Trace.parse(path);
    }

    private static void assertValues(DebugScope scope, Map<String, LLVMDebugValue> expectedLocals, boolean isPartialScope) {
        if (scope == null) {
            throw new AssertionError("Missing Scope!");
        }

        int count = 0;
        for (DebugValue actual : scope.getDeclaredValues()) {

            final String name = actual.getName();
            final LLVMDebugValue expected = expectedLocals.get(actual.getName());

            if (expected != null) {
                try {
                    expected.check(actual);
                    count++;
                } catch (Throwable t) {
                    throw new AssertionError(String.format("Error in local %s", name), t);
                }

            } else if (!isPartialScope) {
                throw new AssertionError(String.format("Unexpected scope member: %s", name));
            }
        }

        // the receiver is somewhat special in that we always set it because of a limitation of the
        // chrome inspector
        final DebugValue receiver = scope.getReceiver();
        if (receiver != null) {
            final LLVMDebugValue expected = expectedLocals.get(receiver.getName());
            if (expected != null) {
                expected.check(receiver);
                count++;
            }
        }

        assertEquals("Unexpected number of scope variables", expectedLocals.size(), count);
    }

    private static final class BreakInfo {

        private int lastStop;
        private ContinueStrategy lastStrategy;

        BreakInfo() {
            this.lastStop = -1;
            this.lastStrategy = null;
        }

        int getLastStop() {
            return lastStop;
        }

        void setLastStop(int lastStop) {
            this.lastStop = lastStop;
        }

        ContinueStrategy getLastStrategy() {
            return lastStrategy;
        }

        void setLastStrategy(ContinueStrategy lastStrategy) {
            this.lastStrategy = lastStrategy;
        }
    }

    private static final class TestCallback implements SuspendedCallback {

        private final BreakInfo info;
        private final StopRequest bpr;

        TestCallback(BreakInfo info, StopRequest bpr) {
            this.info = info;
            this.bpr = bpr;
        }

        private static void setStrategy(SuspendedEvent event, DebugStackFrame frame, ContinueStrategy strategy) {
            if (strategy != null) {
                strategy.prepareInEvent(event, frame);
            } else {
                ContinueStrategy.STEP_INTO.prepareInEvent(event, frame);
            }
        }

        @Override
        public void onSuspend(SuspendedEvent event) {
            final DebugStackFrame frame = event.getTopStackFrame();

            final int currentLine = event.getSourceSection().getStartLine();

            if (currentLine == info.getLastStop()) {
                // since we are stepping on IR-instructions rather than source-statements it can
                // happen that we step at the same line multiple times, so we simply try the last
                // action again. The exact stops differ between LLVM versions and optimization
                // levels which would make it difficult to record an exact trace.
                setStrategy(event, frame, info.getLastStrategy());
                return;

            } else if (currentLine == bpr.getLine()) {
                info.setLastStop(currentLine);
                info.setLastStrategy(bpr.getNextAction());
                setStrategy(event, frame, bpr.getNextAction());

            } else {
                throw new AssertionError(String.format("Unexpected stop at line %d", currentLine));
            }

            DebugScope actualScope = frame.getScope();
            assertEquals("Unexpected function name!", bpr.getFunctionName(), frame.getName());

            try {
                for (StopRequest.Scope expectedScope : bpr) {
                    if (actualScope == null) {
                        throw new AssertionError("Missing scope!");
                    }
                    if (expectedScope.getName() != null) {
                        assertEquals("Unexpected Scope name!", expectedScope.getName(), actualScope.getName());
                    }
                    assertValues(actualScope, expectedScope.getLocals(), expectedScope.isPartial());
                    actualScope = actualScope.getParent();
                }
            } catch (Throwable t) {
                throw new AssertionError(String.format("Error in function %s on line %d", bpr.getFunctionName(), bpr.getLine()), t);
            }
        }

        boolean isDone() {
            return info.getLastStop() == bpr.getLine();
        }
    }

    private static Breakpoint buildBreakPoint(Source source, int line) {
        return Breakpoint.newBuilder(source.getURI()).lineIs(line).build();
    }

    @SuppressWarnings("try")
    private void runTest(Source source, Source bitcode, Trace trace) throws IOException {
        try (CaptureNativeOutput out = new CaptureNativeOutput()) {
            try (DebuggerSession session = tester.startSession()) {
                trace.requestedBreakpoints().forEach(line -> session.install(buildBreakPoint(source, line)));
                if (trace.suspendOnEntry()) {
                    session.suspendNextExecution();
                }

                tester.startEval(bitcode);

                final BreakInfo info = new BreakInfo();
                for (StopRequest bpr : trace) {
                    final TestCallback expectedEvent = new TestCallback(info, bpr);
                    do {
                        tester.expectSuspended(expectedEvent);
                    } while (!expectedEvent.isDone());
                }

                tester.expectDone();
            }
        }
    }

    @Test
    public void test() throws Throwable {
        final Trace trace = readTrace();
        // Trace.updateLines(trace, 0, 0);
        // TraceWriter.write(trace, getTracePath().resolve(testName + TRACE_EXT));

        final Source source = loadOriginalSource();
        final Source bitcode = loadBitcodeSource();
        runTest(source, bitcode, trace);
    }
}
