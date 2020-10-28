/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.wasm.test;

import com.oracle.truffle.api.Truffle;
import junit.framework.AssertionFailedError;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.graalvm.wasm.GlobalRegistry;
import org.graalvm.wasm.WasmContext;
import org.graalvm.wasm.WasmFunctionInstance;
import org.graalvm.wasm.WasmInstance;
import org.graalvm.wasm.memory.WasmMemory;
import org.graalvm.wasm.test.options.WasmTestOptions;
import org.graalvm.wasm.utils.cases.WasmCase;
import org.graalvm.wasm.utils.cases.WasmCaseData;
import org.junit.Assert;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static junit.framework.TestCase.fail;

public abstract class WasmFileSuite extends AbstractWasmSuite {

    private static final String MOVE_LEFT = "\u001b[1D";
    private static final String TEST_PASSED_ICON = "\uD83D\uDE0D";
    private static final String TEST_FAILED_ICON = "\uD83D\uDE21";
    private static final String TEST_IN_PROGRESS_ICON = "\u003F";
    private static final String PHASE_PARSE_ICON = "\uD83D\uDCD6";
    private static final String PHASE_SYNC_NO_INLINE_ICON = "\uD83D\uDD39";
    private static final String PHASE_SYNC_INLINE_ICON = "\uD83D\uDD37";
    private static final String PHASE_ASYNC_ICON = "\uD83D\uDD36";
    private static final String PHASE_INTERPRETER_ICON = "\uD83E\uDD16";
    private static final int STATUS_ICON_WIDTH = 2;
    private static final int STATUS_LABEL_WIDTH = 11;
    private static final int DEFAULT_INTERPRETER_ITERATIONS = 1;
    private static final int DEFAULT_SYNC_NOINLINE_ITERATIONS = 3;
    private static final int DEFAULT_SYNC_INLINE_ITERATIONS = 3;
    private static final int DEFAULT_ASYNC_ITERATIONS = 100000;
    private static final int INITIAL_STATE_CHECK_ITERATIONS = 10;
    private static final int STATE_CHECK_PERIODICITY = 2000;

    private static Context getInterpretedNoInline(Context.Builder contextBuilder) {
        contextBuilder.option("engine.Compilation", "false");
        contextBuilder.option("engine.Inlining", "false");
        return contextBuilder.build();
    }

    private static Context getSyncCompiledNoInline(Context.Builder contextBuilder) {
        contextBuilder.option("engine.Compilation", "true");
        contextBuilder.option("engine.BackgroundCompilation", "false");
        contextBuilder.option("engine.CompileImmediately", "true");
        contextBuilder.option("engine.Inlining", "false");
        return contextBuilder.build();
    }

    private static Context getSyncCompiledWithInline(Context.Builder contextBuilder) {
        contextBuilder.option("engine.Compilation", "true");
        contextBuilder.option("engine.BackgroundCompilation", "false");
        contextBuilder.option("engine.CompileImmediately", "true");
        contextBuilder.option("engine.Inlining", "true");
        return contextBuilder.build();
    }

    private static Context getAsyncCompiled(Context.Builder contextBuilder) {
        contextBuilder.option("engine.Compilation", "true");
        contextBuilder.option("engine.BackgroundCompilation", "true");
        contextBuilder.option("engine.CompileImmediately", "false");
        contextBuilder.option("engine.Inlining", "false");
        contextBuilder.option("engine.CompilationThreshold", "100");
        return contextBuilder.build();
    }

    private static boolean inCI() {
        final String prid = System.getenv("CONTINUOUS_INTEGRATION");
        return prid != null;
    }

    private static boolean inWindows() {
        return System.getProperty("os.name").contains("win");
    }

    private static boolean isPoorShell() {
        return inCI() || inWindows();
    }

    private static Value findMain(WasmContext wasmContext) {
        for (final WasmInstance instance : wasmContext.moduleInstances().values()) {
            final WasmFunctionInstance function = instance.inferEntryPoint();
            if (function != null) {
                return Value.asValue(function);
            }
        }
        throw new AssertionFailedError("No start function exported.");
    }

    private static void runInContext(WasmCase testCase, Context context, List<Source> sources, int iterations, String phaseIcon, String phaseLabel) {
        final PrintStream oldOut = System.out;
        try {
            // TODO(mbovel): Make WASI functions use Env#out() instead of System#out so that we
            // don't need that hack.
            final ByteArrayOutputStream capturedStream = new ByteArrayOutputStream();
            final PrintStream capturedStdout = new PrintStream(capturedStream);
            System.setOut(capturedStdout);

            // Whereas the test needs memory to be reset between iterations.
            final boolean requiresZeroMemory = Boolean.parseBoolean(testCase.options().getProperty("zero-memory", "false"));

            resetStatus(oldOut, PHASE_PARSE_ICON, "parsing");

            // This is needed so that we can call WasmContext.getCurrent().
            context.enter();

            try {
                sources.forEach(context::eval);
            } catch (PolyglotException e) {
                validateThrown(testCase.data(), WasmCaseData.ErrorType.Validation, e);
                return;
            }

            final WasmContext wasmContext = WasmContext.getCurrent();
            final Value mainFunction = findMain(wasmContext);

            resetStatus(oldOut, phaseIcon, phaseLabel);

            final String argString = testCase.options().getProperty("argument");
            final Integer arg = argString == null ? null : Integer.parseInt(argString);
            ContextState firstIterationContextState = null;

            for (int i = 0; i != iterations; ++i) {
                try {
                    capturedStream.reset();
                    final Value result = arg == null ? mainFunction.execute() : mainFunction.execute(arg);
                    WasmCase.validateResult(testCase.data().resultValidator(), result, capturedStream);
                } catch (PolyglotException e) {
                    // If no exception is expected and the program returns with success exit status,
                    // then we check stdout.
                    if (e.isExit() && testCase.data().expectedErrorMessage() == null) {
                        Assert.assertEquals("Program exited with non-zero return code.", e.getExitStatus(), 0);
                        WasmCase.validateResult(testCase.data().resultValidator(), null, capturedStream);
                    } else if (testCase.data().expectedErrorTime() == WasmCaseData.ErrorType.Validation) {
                        validateThrown(testCase.data(), WasmCaseData.ErrorType.Validation, e);
                        return;
                    } else {
                        validateThrown(testCase.data(), WasmCaseData.ErrorType.Runtime, e);
                    }
                } catch (Throwable t) {
                    final RuntimeException e = new RuntimeException("Error during test phase '" + phaseLabel + "'", t);
                    e.setStackTrace(new StackTraceElement[0]);
                    throw e;
                } finally {
                    // Save context state, and check that it's consistent with the previous one.
                    if (iterationNeedsStateCheck(i)) {
                        final ContextState contextState = saveContext(wasmContext);
                        if (firstIterationContextState == null) {
                            firstIterationContextState = contextState;
                        } else {
                            assertContextEqual(firstIterationContextState, contextState);
                        }
                    }

                    // Reset context state.
                    final boolean reinitMemory = requiresZeroMemory || iterationNeedsStateCheck(i + 1);
                    if (reinitMemory) {
                        for (int j = 0; j < wasmContext.memories().count(); ++j) {
                            wasmContext.memories().memory(j).clear();
                        }
                    }
                    for (final WasmInstance instance : wasmContext.moduleInstances().values()) {
                        if (!instance.isBuiltin()) {
                            wasmContext.reinitInstance(instance, reinitMemory);
                        }
                    }
                }
            }
        } finally {
            context.close(true);
            System.setOut(oldOut);
        }
    }

    private static boolean iterationNeedsStateCheck(int i) {
        return i < INITIAL_STATE_CHECK_ITERATIONS || i % STATE_CHECK_PERIODICITY == 0;
    }

    private static void resetStatus(PrintStream oldOut, String icon, String label) {
        String formattedLabel = label;
        if (formattedLabel.length() > STATUS_LABEL_WIDTH) {
            formattedLabel = formattedLabel.substring(0, STATUS_LABEL_WIDTH);
        }
        for (int i = formattedLabel.length(); i < STATUS_LABEL_WIDTH; i++) {
            formattedLabel += " ";
        }
        if (isPoorShell()) {
            oldOut.println();
            oldOut.print(icon);
            oldOut.print(formattedLabel);
            oldOut.flush();
        } else {
            eraseStatus(oldOut);
            oldOut.print(icon);
            oldOut.print(formattedLabel);
            oldOut.flush();
        }
    }

    private static void eraseStatus(PrintStream oldOut) {
        for (int i = 0; i < STATUS_ICON_WIDTH + STATUS_LABEL_WIDTH; i++) {
            oldOut.print(MOVE_LEFT);
        }
    }

    private WasmTestStatus runTestCase(WasmCase testCase) {
        try {
            Context.Builder contextBuilder = Context.newBuilder("wasm");
            contextBuilder.allowExperimentalOptions(true);
            contextBuilder.option("engine.EncodedGraphCacheCapacity", "-1");

            if (WasmTestOptions.LOG_LEVEL != null && !WasmTestOptions.LOG_LEVEL.equals("")) {
                contextBuilder.option("log.wasm.level", WasmTestOptions.LOG_LEVEL);
            }

            if (WasmTestOptions.STORE_CONSTANTS_POLICY != null && !WasmTestOptions.STORE_CONSTANTS_POLICY.equals("")) {
                contextBuilder.option("wasm.StoreConstantsPolicy", WasmTestOptions.STORE_CONSTANTS_POLICY);
                System.out.println("wasm.StoreConstantsPolicy: " + WasmTestOptions.STORE_CONSTANTS_POLICY);
            }

            contextBuilder.option("wasm.Builtins", includedExternalModules());
            String commandLineArgs = testCase.options().getProperty("command-line-args");
            if (commandLineArgs != null) {
                contextBuilder.arguments("wasm", commandLineArgs.split(" "));
            }

            Context context;
            ArrayList<Source> sources = testCase.getSources();

            // Run in interpreted mode, with inlining turned off, to ensure profiles are populated.
            int interpreterIterations = Integer.parseInt(testCase.options().getProperty("interpreter-iterations", String.valueOf(DEFAULT_INTERPRETER_ITERATIONS)));
            context = getInterpretedNoInline(contextBuilder);
            runInContext(testCase, context, sources, interpreterIterations, PHASE_INTERPRETER_ICON, "interpreter");

            // Run in synchronous compiled mode, with inlining turned off.
            // We need to run the test at least twice like this, since the first run will lead to
            // de-opts due to empty profiles.
            int syncNoinlineIterations = Integer.parseInt(testCase.options().getProperty("sync-noinline-iterations", String.valueOf(DEFAULT_SYNC_NOINLINE_ITERATIONS)));
            context = getSyncCompiledNoInline(contextBuilder);
            runInContext(testCase, context, sources, syncNoinlineIterations, PHASE_SYNC_NO_INLINE_ICON, "sync,no-inl");

            // Run in synchronous compiled mode, with inlining turned on.
            // We need to run the test at least twice like this, since the first run will lead to
            // de-opts due to empty profiles.
            int syncInlineIterations = Integer.parseInt(testCase.options().getProperty("sync-inline-iterations", String.valueOf(DEFAULT_SYNC_INLINE_ITERATIONS)));
            context = getSyncCompiledWithInline(contextBuilder);
            runInContext(testCase, context, sources, syncInlineIterations, PHASE_SYNC_INLINE_ICON, "sync,inl");

            // Run with normal, asynchronous compilation.
            int asyncIterations = Integer.parseInt(testCase.options().getProperty("async-iterations", String.valueOf(DEFAULT_ASYNC_ITERATIONS)));
            context = getAsyncCompiled(contextBuilder);
            runInContext(testCase, context, sources, asyncIterations, PHASE_ASYNC_ICON, "async,multi");
        } catch (InterruptedException | IOException e) {
            Assert.fail(String.format("Test %s failed: %s", testCase.name(), e.getMessage()));
        }
        return WasmTestStatus.OK;
    }

    protected String includedExternalModules() {
        return "testutil:testutil";
    }

    private static void validateThrown(WasmCaseData data, WasmCaseData.ErrorType phase, PolyglotException e) throws PolyglotException {
        if (data.expectedErrorMessage() == null || !data.expectedErrorMessage().equals(e.getMessage())) {
            throw e;
        }
        Assert.assertEquals("Unexpected error phase.", data.expectedErrorTime(), phase);
    }

    @Override
    public void test() throws IOException {
        Collection<? extends WasmCase> allTestCases = collectTestCases();
        Collection<? extends WasmCase> qualifyingTestCases = filterTestCases(allTestCases);
        Map<WasmCase, Throwable> errors = new LinkedHashMap<>();
        System.out.println();
        System.out.println("--------------------------------------------------------------------------------");
        System.out.printf("Running: %s ", suiteName());
        if (allTestCases.size() != qualifyingTestCases.size()) {
            System.out.printf("(%d/%d tests - you have enabled filters)%n", qualifyingTestCases.size(), allTestCases.size());
        } else {
            System.out.printf("(%d tests)%n", qualifyingTestCases.size());
        }
        System.out.println("--------------------------------------------------------------------------------");
        System.out.println("Using runtime: " + Truffle.getRuntime().toString());
        int width = retrieveTerminalWidth();
        int position = 0;
        for (WasmCase testCase : qualifyingTestCases) {
            int extraWidth = 1 + STATUS_ICON_WIDTH + STATUS_LABEL_WIDTH;
            int requiredWidth = testCase.name().length() + extraWidth;
            if (position + requiredWidth >= width) {
                System.out.println();
                position = 0;
            }
            String statusIcon = TEST_IN_PROGRESS_ICON;
            try {
                // We print each test name behind the line of test status icons,
                // so that we know which test failed in case the VM exits suddenly.
                // If the test fails normally or succeeds, then we move the cursor to the left,
                // and erase the test name.
                System.out.print(" ");
                System.out.print(testCase.name());
                for (int i = 1; i < extraWidth; i++) {
                    System.out.print(" ");
                }
                System.out.flush();
                runTestCase(testCase);
                statusIcon = TEST_PASSED_ICON;
            } catch (Throwable e) {
                statusIcon = TEST_FAILED_ICON;
                errors.put(testCase, e);
            } finally {
                if (isPoorShell()) {
                    System.out.println();
                    System.out.println(statusIcon);
                    System.out.println();
                } else {
                    for (int i = 0; i < requiredWidth; i++) {
                        System.out.print(MOVE_LEFT);
                        System.out.print(" ");
                        System.out.print(MOVE_LEFT);
                    }
                    System.out.print(statusIcon);
                    System.out.flush();
                }
            }
            position++;
        }
        System.out.println();
        System.out.println("Finished running: " + suiteName());
        if (!errors.isEmpty()) {
            for (Map.Entry<WasmCase, Throwable> entry : errors.entrySet()) {
                System.err.printf("Failure in: %s.%s%n", suiteName(), entry.getKey().name());
                System.err.println(entry.getValue().getClass().getSimpleName() + ": " + entry.getValue().getMessage());
                entry.getValue().printStackTrace();
            }
            System.err.printf("\uD83D\uDCA5\u001B[31m %d/%d Wasm tests passed.\u001B[0m%n", qualifyingTestCases.size() - errors.size(), qualifyingTestCases.size());
            System.out.println();
            fail();
        } else {
            System.out.printf("\uD83C\uDF40\u001B[32m %d/%d Wasm tests passed.\u001B[0m%n", qualifyingTestCases.size() - errors.size(), qualifyingTestCases.size());
            System.out.println();
        }
    }

    private static int retrieveTerminalWidth() {
        try {
            final ProcessBuilder builder = new ProcessBuilder("/bin/sh", "-c", "stty size </dev/tty");
            final Process process = builder.start();
            final BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            final String output = reader.readLine();
            if (process.waitFor() != 0) {
                return -1;
            }
            return Integer.parseInt(output.split(" ")[1]);
        } catch (IOException e) {
            System.err.println("Could not retrieve terminal width: " + e.getMessage());
            return -1;
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    protected String testResource() {
        return null;
    }

    protected Collection<? extends WasmCase> collectTestCases() throws IOException {
        return Stream.concat(collectStringTestCases().stream(), WasmCase.collectFileCases("test", testResource()).stream()).collect(Collectors.toList());
    }

    protected Collection<? extends WasmCase> collectStringTestCases() {
        return new ArrayList<>();
    }

    protected Collection<? extends WasmCase> filterTestCases(Collection<? extends WasmCase> testCases) {
        return testCases.stream().filter((WasmCase x) -> filterTestName().test(x.name())).collect(Collectors.toList());
    }

    protected String suiteName() {
        return getClass().getSimpleName();
    }

    private static ContextState saveContext(WasmContext context) {
        Assert.assertTrue("Currently, only 0 or 1 memories can be saved.", context.memories().count() <= 1);
        final WasmMemory currentMemory = context.memories().count() == 1 ? context.memories().memory(0).duplicate() : null;
        final GlobalRegistry globals = context.globals().duplicate();
        return new ContextState(currentMemory, globals);
    }

    private static void assertContextEqual(ContextState expectedState, ContextState actualState) {
        // Compare memories
        final WasmMemory expectedMemory = expectedState.memory();
        final WasmMemory actualMemory = actualState.memory();
        if (expectedMemory == null) {
            Assert.assertNull("Memory should be null", actualMemory);
        } else {
            Assert.assertNotNull("Memory should not be null", actualMemory);
            Assert.assertEquals("Mismatch in memory lengths", expectedMemory.byteSize(), actualMemory.byteSize());
            for (int ptr = 0; ptr < expectedMemory.byteSize(); ptr++) {
                byte expectedByte = (byte) expectedMemory.load_i32_8s(null, ptr);
                byte actualByte = (byte) actualMemory.load_i32_8s(null, ptr);
                Assert.assertEquals("Memory mismatch", expectedByte, actualByte);
            }
        }

        // Compare globals
        final GlobalRegistry firstGlobals = expectedState.globals();
        final GlobalRegistry lastGlobals = actualState.globals();
        Assert.assertEquals("Mismatch in global counts.", firstGlobals.count(), lastGlobals.count());
        for (int address = 0; address < firstGlobals.count(); address++) {
            long first = firstGlobals.loadAsLong(address);
            long last = lastGlobals.loadAsLong(address);
            Assert.assertEquals("Mismatch in global at " + address + ". ", first, last);
        }
    }

    private static final class ContextState {
        private final WasmMemory memory;
        private final GlobalRegistry globals;

        private ContextState(WasmMemory memory, GlobalRegistry globals) {
            this.memory = memory;
            this.globals = globals;
        }

        public WasmMemory memory() {
            return memory;
        }

        public GlobalRegistry globals() {
            return globals;
        }
    }
}
