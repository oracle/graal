/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates.
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

import static com.oracle.truffle.llvm.tests.debug.LLVMDebugTestBase.TEST_FOLDER_EXT;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.debug.Breakpoint;
import com.oracle.truffle.api.debug.DebugException;
import com.oracle.truffle.api.debug.DebugStackFrame;
import com.oracle.truffle.api.debug.DebuggerSession;
import com.oracle.truffle.api.debug.SuspendedCallback;
import com.oracle.truffle.api.debug.SuspendedEvent;
import com.oracle.truffle.llvm.runtime.LLVMLanguage;
import com.oracle.truffle.llvm.tests.options.TestOptions;
import com.oracle.truffle.tck.DebuggerTester;

@RunWith(Parameterized.class)
public final class LLVMDebugExprParserTest {

    private static final Path BC_DIR_PATH = Paths.get(TestOptions.TEST_SUITE_PATH, "debugexpr");
    private static final Path SRC_DIR_PATH = Paths.get(TestOptions.PROJECT_ROOT, "..", "tests", "com.oracle.truffle.llvm.tests.debugexpr.native", "debugexpr");
    private static final Path TRACE_DIR_PATH = Paths.get(TestOptions.PROJECT_ROOT, "..", "tests", "com.oracle.truffle.llvm.tests.debugexpr.native", "testExpr");

    private static final String CONFIGURATION = "O1.bc";

    public LLVMDebugExprParserTest(String testName, String configuration) {
        this.testName = testName;
        this.configuration = configuration;
    }

    @Parameters(name = "{0}")
    public static Collection<Object[]> getConfigurations() {
        try (Stream<Path> dirs = Files.walk(BC_DIR_PATH)) {
            return dirs.filter(path -> path.endsWith(CONFIGURATION)).map(path -> new Object[]{getTestSource(path), CONFIGURATION}).collect(Collectors.toSet());
        } catch (IOException e) {
            throw new AssertionError("Error while finding tests!", e);
        }
    }

    private static String getTestSource(Path path) {
        String filename = path.getParent().getFileName().toString();
        if (filename.endsWith(TEST_FOLDER_EXT)) {
            return filename.substring(0, filename.length() - TEST_FOLDER_EXT.length());
        }
        return filename;
    }

    private static final String LANG_ID = LLVMLanguage.ID;

    private static final String TRACE_EXT = ".txt";
    private static final String OPTION_LAZY_PARSING = "llvm.lazyParsing";

    private final String testName;
    private final String configuration;

    private DebuggerTester tester;

    String getTestName() {
        return testName;
    }

    @Before
    public void before() {
        final Context.Builder contextBuilder = Context.newBuilder(LANG_ID);
        contextBuilder.allowAllAccess(true);
        contextBuilder.option(OPTION_LAZY_PARSING, String.valueOf(false));
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

    private Source loadOriginalSource() {
        final File file = getSourcePath().resolve(testName).toFile();
        Assert.assertTrue("Locate Source", file.exists());
        return loadSource(file);
    }

    private Source loadBitcodeSource() {
        final File file = getBitcodePath().resolve(Paths.get(testName + ".dir", configuration)).toFile();
        Assert.assertTrue("Locate Bitcode", file.exists());
        return loadSource(file);
    }

    private TestExpressions fetchExpressions() {
        final Path path = getTracePath().resolve(testName + TRACE_EXT);
        Assert.assertTrue("Locate Test Expression", path.toFile().exists());
        return TestExpressions.parse(path);
    }

    static Path getBitcodePath() {
        return BC_DIR_PATH;
    }

    static Path getSourcePath() {
        return SRC_DIR_PATH;
    }

    static Path getTracePath() {
        return TRACE_DIR_PATH;
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
        private final Map<String, String> textExpressionMap;
        private final boolean allowFailure;

        TestCallback(BreakInfo info, StopRequest bpr, Map<String, String> textExpressionMap, boolean allowFailure) {
            this.info = info;
            this.bpr = bpr;
            this.textExpressionMap = textExpressionMap;
            this.allowFailure = allowFailure;
        }

        private static void setStrategy(SuspendedEvent event, DebugStackFrame frame, ContinueStrategy strategy) {
            if (strategy != null) {
                strategy.prepareInEvent(event, frame);
            } else {
                ContinueStrategy.STEP_INTO.prepareInEvent(event, frame);
            }
        }

        @Override
        @TruffleBoundary
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

            for (Entry<String, String> kv : textExpressionMap.entrySet()) {
                if (kv.getValue().startsWith("EXCEPTION ")) {
                    try {
                        String actual = frame.eval(kv.getKey()).asString();
                        assertTrue("Evaluation of expression \"" + kv.getKey() + "\"  on line " + currentLine + "did evaluate to " + actual + " and did not throw expected " + kv.getValue(), false);
                    } catch (DebugException e) {
                        // OK since expected exception has been thrown
                    }
                } else {
                    String actual = frame.eval(kv.getKey()).toDisplayString();
                    String noNewLineActual = actual.replace("\n", "");
                    if (allowFailure) {
                        if (noNewLineActual.contains(kv.getValue())) {
                            System.out.println("Evaluation of expression \"" + kv.getKey() + "  on line " + currentLine + "produced correct failure error.");
                            return;
                        }
                    }
                    assertEquals("Evaluation of expression \"" + kv.getKey() + "\" on line " + currentLine + " produced unexpected result: ", kv.getValue(), noNewLineActual);
                }
            }
        }

        boolean isDone() {
            return info.getLastStop() == bpr.getLine();
        }
    }

    private static Breakpoint buildBreakPoint(Source source, int line) {
        return Breakpoint.newBuilder(source.getURI()).lineIs(line).build();
    }

    private void runTest(Source source, Source bitcode, TestExpressions testExpr) {
        try (DebuggerSession session = tester.startSession()) {
            testExpr.requestedBreakpoints().forEach(line -> session.install(buildBreakPoint(source, line)));

            tester.startEval(bitcode);

            final BreakInfo info = new BreakInfo();
            for (StopRequest bpr : testExpr) {
                final TestCallback expectedEvent = new TestCallback(info, bpr, testExpr.getExpressions(bpr), ((Boolean) testExpr.getFailure(bpr)).booleanValue());
                do {
                    tester.expectSuspended(expectedEvent);
                } while (!expectedEvent.isDone());
            }

            tester.expectDone();
        }
    }

    @Test
    public void test() throws Throwable {
        final TestExpressions testExpr = fetchExpressions();

        final Source source = loadOriginalSource();
        final Source bitcode = loadBitcodeSource();
        runTest(source, bitcode, testExpr);
    }

}
