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

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.graalvm.wasm.predefined.testutil.TestutilModule;
import org.graalvm.wasm.test.options.WasmTestOptions;
import org.graalvm.wasm.utils.cases.WasmCase;
import org.graalvm.wasm.utils.cases.WasmCaseData;
import org.junit.Assert;

import com.oracle.truffle.api.Truffle;

import static junit.framework.TestCase.fail;

public abstract class WasmSuiteBase extends WasmTestBase {

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

    private static void runInContext(WasmCase testCase, Context context, List<Source> sources, int iterations, String phaseIcon, String phaseLabel) {
        boolean requiresZeroMemory = Boolean.parseBoolean(testCase.options().getProperty("zero-memory", "false"));

        final PrintStream oldOut = System.out;
        try {
            resetStatus(oldOut, PHASE_PARSE_ICON, "parsing");

            try {
                sources.forEach(context::eval);
            } catch (PolyglotException e) {
                validateThrown(testCase.data(), WasmCaseData.ErrorType.Validation, e);
                return;
            }

            // The sequence of WebAssembly functions to execute.
            // Run custom initialization.
            // Execute the main function (exported as "_main").
            // Then, optionally save memory and globals, and compare them.
            // Execute a special function, which resets memory and globals to their default values.
            Value mainFunction = context.getBindings("wasm").getMember("_start");
            if (mainFunction == null) {
                mainFunction = context.getBindings("wasm").getMember("_main");
            }
            Value resetContext = context.getBindings("wasm").getMember(TestutilModule.Names.RESET_CONTEXT);
            Value saveContext = context.getBindings("wasm").getMember(TestutilModule.Names.SAVE_CONTEXT);
            Value compareContexts = context.getBindings("wasm").getMember(TestutilModule.Names.COMPARE_CONTEXTS);

            resetStatus(oldOut, phaseIcon, phaseLabel);
            ByteArrayOutputStream capturedStdout;
            Object firstIterationContextState = null;

            for (int i = 0; i != iterations; ++i) {
                try {
                    capturedStdout = new ByteArrayOutputStream();
                    System.setOut(new PrintStream(capturedStdout));

                    final String argString = testCase.options().getProperty("argument");

                    // Execute benchmark.
                    final Value result = argString == null ? mainFunction.execute() : mainFunction.execute(Integer.parseInt(argString));

                    // Save context state, and check that it's consistent with the previous one.
                    if (iterationNeedsStateCheck(i)) {
                        Object contextState = saveContext.execute();
                        if (firstIterationContextState == null) {
                            firstIterationContextState = contextState;
                        } else {
                            compareContexts.execute(firstIterationContextState, contextState);
                        }
                    }

                    // Reset context state.
                    boolean zeroMemory = iterationNeedsStateCheck(i + 1) || requiresZeroMemory;
                    resetContext.execute(zeroMemory);

                    validateResult(testCase.data().resultValidator(), result, capturedStdout);
                } catch (PolyglotException e) {
                    // We cannot label the tests with polyglot errors, because they might
                    // semantically be return values of the test.
                    if (testCase.data().expectedErrorTime() == WasmCaseData.ErrorType.Validation) {
                        validateThrown(testCase.data(), WasmCaseData.ErrorType.Validation, e);
                        return;
                    }
                    validateThrown(testCase.data(), WasmCaseData.ErrorType.Runtime, e);
                } catch (Throwable t) {
                    final RuntimeException e = new RuntimeException("Error during test phase '" + phaseLabel + "'", t);
                    e.setStackTrace(new StackTraceElement[0]);
                    throw e;
                }
            }
        } finally {
            System.setOut(oldOut);
            context.close(true);
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
            // Run 1000 + 1 times - the last time run with a surrogate stream, to collect output.
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

    private static void validateResult(BiConsumer<Value, String> validator, Value result, OutputStream capturedStdout) {
        if (validator != null) {
            validator.accept(result, capturedStdout.toString());
        } else {
            Assert.fail("Test was not expected to return a value.");
        }
    }

    private static void validateThrown(WasmCaseData data, WasmCaseData.ErrorType phase, PolyglotException e) throws PolyglotException {
        if (data.expectedErrorMessage() == null || !data.expectedErrorMessage().equals(e.getMessage())) {
            throw e;
        }
        Assert.assertEquals("Unexpected error phase (should not have been thrown during the running phase).", data.expectedErrorTime(), phase);
    }

    @Override
    public void test() throws IOException {
        Collection<? extends WasmCase> allTestCases = collectTestCases();
        Collection<? extends WasmCase> qualifyingTestCases = filterTestCases(allTestCases);
        Map<WasmCase, Throwable> errors = new LinkedHashMap<>();
        System.out.println();
        System.out.println("--------------------------------------------------------------------------------");
        System.out.print(String.format("Running: %s ", suiteName()));
        if (allTestCases.size() != qualifyingTestCases.size()) {
            System.out.println(String.format("(%d/%d tests - you have enabled filters)", qualifyingTestCases.size(), allTestCases.size()));
        } else {
            System.out.println(String.format("(%d tests)", qualifyingTestCases.size()));
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
                System.err.println(String.format("Failure in: %s.%s", suiteName(), entry.getKey().name()));
                System.err.println(entry.getValue().getClass().getSimpleName() + ": " + entry.getValue().getMessage());
                entry.getValue().printStackTrace();
            }
            System.err.println(String.format("\uD83D\uDCA5\u001B[31m %d/%d Wasm tests passed.\u001B[0m", qualifyingTestCases.size() - errors.size(), qualifyingTestCases.size()));
            System.out.println();
            fail();
        } else {
            System.out.println(String.format("\uD83C\uDF40\u001B[32m %d/%d Wasm tests passed.\u001B[0m", qualifyingTestCases.size() - errors.size(), qualifyingTestCases.size()));
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
            final int width = Integer.parseInt(output.split(" ")[1]);
            return width;
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
}
