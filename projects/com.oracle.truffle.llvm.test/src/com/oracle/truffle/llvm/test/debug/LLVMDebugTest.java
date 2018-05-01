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
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.oracle.truffle.api.debug.Breakpoint;
import com.oracle.truffle.api.debug.DebugScope;
import com.oracle.truffle.api.debug.DebugStackFrame;
import com.oracle.truffle.api.debug.DebugValue;
import com.oracle.truffle.api.debug.DebuggerSession;
import com.oracle.truffle.api.debug.SuspendedCallback;
import com.oracle.truffle.api.debug.SuspendedEvent;
import com.oracle.truffle.llvm.runtime.LLVMLanguage;
import com.oracle.truffle.llvm.test.options.TestOptions;
import com.oracle.truffle.tck.DebuggerTester;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import static com.oracle.truffle.llvm.test.debug.StopRequest.Scope;
import static org.junit.Assert.assertEquals;

@RunWith(Parameterized.class)
public final class LLVMDebugTest {

    private static final String LANG_NAME = LLVMLanguage.NAME;

    private static final Path BC_DIR_PATH = Paths.get(TestOptions.TEST_SUITE_PATH, "debug");
    private static final Path SRC_DIR_PATH = Paths.get(TestOptions.PROJECT_ROOT, "..", "tests", "com.oracle.truffle.llvm.tests.debug", "debug");
    private static final Path TRACE_DIR_PATH = Paths.get(TestOptions.PROJECT_ROOT, "..", "tests", "com.oracle.truffle.llvm.tests.debug", "trace");

    private static final String TRACE_EXT = ".txt";
    private static final String OPTION_ENABLE_LVI = "llvm.enableLVI";
    private static final String OPTION_LAZY_PARSING = "llvm.lazyParsing";

    private static final String BC_O0 = "O0.bc";
    private static final String BC_O1 = "O1.bc";
    private static final String BC_MEM2REG = "O0_MEM2REG.bc";

    public LLVMDebugTest(String testName, String configuration) {
        this.testName = testName;
        this.configuration = configuration;
    }

    @Parameters(name = "{0} - {1}")
    public static Collection<Object[]> getConfigurations() {
        final Map<String, String[]> configs = new HashMap<>();
        configs.put("testPrimitives", new String[]{BC_O0, BC_MEM2REG});
        configs.put("testStructures", new String[]{BC_O0, BC_MEM2REG, BC_O1});
        configs.put("testUnions", new String[]{BC_O0, BC_MEM2REG, BC_O1});
        configs.put("testDecorators", new String[]{BC_O0, BC_MEM2REG, BC_O1});
        configs.put("testClasses", new String[]{BC_O0, BC_MEM2REG, BC_O1});
        configs.put("testScopes", new String[]{BC_O0, BC_MEM2REG, BC_O1});
        configs.put("testControlFlow", new String[]{BC_O0, BC_MEM2REG});
        configs.put("testReenterArgsAndVals", new String[]{BC_O0, BC_MEM2REG, BC_O1});
        configs.put("testFunctionPointer", new String[]{BC_O0, BC_MEM2REG, BC_O1});
        return configs.entrySet().stream().flatMap(e -> Stream.of(e.getValue()).map(v -> new Object[]{e.getKey(), v})).collect(Collectors.toSet());
    }

    private final String testName;
    private final String configuration;

    private DebuggerTester tester;

    @Before
    public void before() {
        tester = new DebuggerTester(Context.newBuilder().option(OPTION_ENABLE_LVI, String.valueOf(true)).option(OPTION_LAZY_PARSING, String.valueOf(false)).allowAllAccess(true));
    }

    @After
    public void dispose() {
        tester.close();
    }

    private static Source loadSource(File file) {
        Source source;
        try {
            final File canonicalFile = file.getCanonicalFile();
            source = Source.newBuilder(LANG_NAME, canonicalFile).build();
        } catch (IOException ex) {
            throw new AssertionError("Could not load source: " + file.getPath(), ex);
        }
        return source;
    }

    private static Source loadOriginalSource(String testName) {
        File file = SRC_DIR_PATH.resolve(testName + ".c").toFile();
        if (!file.exists()) {
            file = SRC_DIR_PATH.resolve(testName + ".cpp").toFile();
        }
        return loadSource(file);
    }

    private static Source loadBitcodeSource(String testName, String fileName) {
        final Path path = BC_DIR_PATH.resolve(Paths.get(testName, fileName));
        return loadSource(path.toFile());
    }

    private static Trace readTrace(String testName) {
        final Path path = TRACE_DIR_PATH.resolve(testName + TRACE_EXT);
        return Trace.parse(path);
    }

    private static void assertValues(DebugScope scope, Map<String, LLVMDebugValue> expectedLocals) {
        if (scope == null) {
            throw new AssertionError("Missing Scope!");
        }

        int count = 0;
        for (DebugValue actual : scope.getDeclaredValues()) {

            final String name = actual.getName();
            final LLVMDebugValue expected = expectedLocals.get(actual.getName());

            if (expected == null) {
                throw new AssertionError(String.format("Unexpected scope member: %s", name));
            }

            try {
                expected.check(actual);
            } catch (Throwable t) {
                throw new AssertionError(String.format("Error in local %s", name), t);
            }

            count++;
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

        public int getLastStop() {
            return lastStop;
        }

        public void setLastStop(int lastStop) {
            this.lastStop = lastStop;
        }

        public ContinueStrategy getLastStrategy() {
            return lastStrategy;
        }

        public void setLastStrategy(ContinueStrategy lastStrategy) {
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
                for (Scope expectedScope : bpr) {
                    if (actualScope == null) {
                        throw new AssertionError("Missing scope!");
                    }
                    if (expectedScope.getName() != null) {
                        assertEquals("Unexpected Scope name!", expectedScope.getName(), actualScope.getName());
                    }
                    assertValues(actualScope, expectedScope.getLocals());
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

    private void runTest(Source source, Source bitcode, Trace trace) {
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

    @Test
    public void test() throws Throwable {
        final Source source = loadOriginalSource(testName);
        final Trace trace = readTrace(testName);
        final Source bitcode = loadBitcodeSource(testName, configuration);
        runTest(source, bitcode, trace);
    }
}
