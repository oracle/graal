/*
 * Copyright (c) 2019, 2023, Oracle and/or its affiliates. All rights reserved.
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

import static junit.framework.TestCase.fail;
import static org.graalvm.wasm.WasmUtil.prepend;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.EnvironmentAccess;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.io.IOAccess;
import org.graalvm.wasm.GlobalRegistry;
import org.graalvm.wasm.MemoryRegistry;
import org.graalvm.wasm.RuntimeState;
import org.graalvm.wasm.WasmContext;
import org.graalvm.wasm.WasmFunctionInstance;
import org.graalvm.wasm.WasmInstance;
import org.graalvm.wasm.WasmLanguage;
import org.graalvm.wasm.memory.WasmMemory;
import org.graalvm.wasm.test.options.WasmTestOptions;
import org.graalvm.wasm.utils.WasmBinaryTools;
import org.graalvm.wasm.utils.cases.WasmCase;
import org.graalvm.wasm.utils.cases.WasmCaseData;
import org.junit.Assert;

import com.oracle.truffle.api.Truffle;

import junit.framework.AssertionFailedError;

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
    private static final String PHASE_SHARED_ENGINE_ICON = "\uD83D\uDD38";
    private static final int STATUS_ICON_WIDTH = 2;
    private static final int STATUS_LABEL_WIDTH = 12;
    private static final int DEFAULT_INTERPRETER_ITERATIONS = 1;
    private static final int DEFAULT_SYNC_NOINLINE_ITERATIONS = 3;
    private static final int DEFAULT_SYNC_INLINE_ITERATIONS = 3;
    private static final int DEFAULT_ASYNC_ITERATIONS = 100000;
    private static final int DEFAULT_ASYNC_SHARED_ITERATIONS = 10000;
    private static final int INITIAL_STATE_CHECK_ITERATIONS = 10;
    private static final int STATE_CHECK_PERIODICITY = 2000;

    private static Map<String, String> getInterpretedNoInline() {
        return Map.ofEntries(
                        Map.entry("engine.Compilation", "false"),
                        Map.entry("compiler.Inlining", "false"));
    }

    private static Map<String, String> getSyncCompiledNoInline() {
        return Map.ofEntries(
                        Map.entry("engine.Compilation", "true"),
                        Map.entry("engine.BackgroundCompilation", "false"),
                        Map.entry("engine.CompileImmediately", "true"),
                        Map.entry("compiler.Inlining", "false"));
    }

    private static Map<String, String> getSyncCompiledWithInline() {
        return Map.ofEntries(
                        Map.entry("engine.Compilation", "true"),
                        Map.entry("engine.BackgroundCompilation", "false"),
                        Map.entry("engine.CompileImmediately", "true"),
                        Map.entry("compiler.Inlining", "true"));
    }

    private static Map<String, String> getAsyncCompiled() {
        return Map.ofEntries(
                        Map.entry("engine.Compilation", "true"),
                        Map.entry("engine.BackgroundCompilation", "true"),
                        Map.entry("engine.CompileImmediately", "false"),
                        Map.entry("compiler.Inlining", "false"),
                        Map.entry("engine.FirstTierCompilationThreshold", "100"));
    }

    private static Map<String, String> getAsyncCompiledShared() {
        return getAsyncCompiled();
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

    private void runInContext(WasmCase testCase, Context context, List<Source> sources, int iterations, String phaseIcon, String phaseLabel, ByteArrayOutputStream testOut) throws IOException {
        try {
            // Whereas the test needs memory to be reset between iterations.
            final boolean requiresZeroMemory = Boolean.parseBoolean(testCase.options().getProperty("zero-memory", "false"));

            resetStatus(System.out, PHASE_PARSE_ICON, "parsing");

            // This is needed so that we can call WasmContext.getCurrent().
            context.enter();

            try {
                sources.forEach(context::eval);
            } catch (PolyglotException e) {
                validateThrown(testCase.data(), WasmCaseData.ErrorType.Validation, e);
                return;
            }

            final WasmContext wasmContext = WasmContext.get(null);
            final Value mainFunction = findMain(wasmContext);

            resetStatus(System.out, phaseIcon, phaseLabel);

            final String argString = testCase.options().getProperty("argument");
            final Integer arg = argString == null ? null : Integer.parseInt(argString);
            ContextState firstIterationContextState = null;

            for (int i = 0; i != iterations; ++i) {
                try {
                    testOut.reset();
                    final Value result = arg == null ? mainFunction.execute() : mainFunction.execute(arg);
                    WasmCase.validateResult(testCase.data().resultValidator(), result, testOut);
                } catch (PolyglotException e) {
                    // If no exception is expected and the program returns with success exit status,
                    // then we check stdout.
                    if (e.isExit() && testCase.data().expectedErrorMessage() == null) {
                        Assert.assertEquals("Program exited with non-zero return code.", e.getExitStatus(), 0);
                        WasmCase.validateResult(testCase.data().resultValidator(), null, testOut);
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
                            wasmContext.memories().memory(j).reset();
                        }
                        for (int j = 0; j < wasmContext.tables().tableCount(); ++j) {
                            wasmContext.tables().table(j).reset();
                        }
                    }
                    List<WasmInstance> instanceList = new ArrayList<>(wasmContext.moduleInstances().values());
                    instanceList.sort(Comparator.comparingInt(RuntimeState::startFunctionIndex));
                    for (WasmInstance instance : instanceList) {
                        if (!instance.isBuiltin()) {
                            wasmContext.reinitInstance(instance, reinitMemory);
                        }
                    }

                    // Reset stdin
                    if (wasmContext.environment().in() instanceof ByteArrayInputStream) {
                        wasmContext.environment().in().reset();
                    }
                }
            }
        } finally {
            context.close(true);
        }
    }

    private static boolean iterationNeedsStateCheck(int i) {
        return i < INITIAL_STATE_CHECK_ITERATIONS || i % STATE_CHECK_PERIODICITY == 0;
    }

    @SuppressWarnings("static-method")
    private synchronized void resetStatus(PrintStream oldOut, String icon, String label) {
        if (isPoorShell()) {
            oldOut.println();
            oldOut.print(icon);
            oldOut.print(label);
            oldOut.flush();
        } else {
            String formattedLabel = label;
            if (formattedLabel.length() > STATUS_LABEL_WIDTH) {
                formattedLabel = formattedLabel.substring(0, STATUS_LABEL_WIDTH);
            }
            for (int i = formattedLabel.length(); i < STATUS_LABEL_WIDTH; i++) {
                formattedLabel += " ";
            }
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

    private WasmTestStatus runTestCase(WasmCase testCase, Engine sharedEngine) {
        ByteArrayOutputStream testOut = new ByteArrayOutputStream();
        Path tempWorkingDirectory = null;
        try {
            Context.Builder contextBuilder = Context.newBuilder(WasmLanguage.ID);
            if (sharedEngine != null) {
                contextBuilder.engine(sharedEngine);
            }

            contextBuilder.allowEnvironmentAccess(EnvironmentAccess.NONE);
            contextBuilder.out(testOut);
            contextBuilder.allowExperimentalOptions(true);

            if (WasmTestOptions.LOG_LEVEL != null && !WasmTestOptions.LOG_LEVEL.equals("")) {
                contextBuilder.option("log.wasm.level", WasmTestOptions.LOG_LEVEL);
            }

            contextBuilder.option("wasm.Builtins", includedExternalModules());
            contextBuilder.option("wasm.WasiConstantRandomGet", "true");
            final String commandLineArgs = testCase.options().getProperty("command-line-args");
            if (commandLineArgs != null) {
                // The first argument is the program name. We set it to the empty string in tests.
                contextBuilder.arguments(WasmLanguage.ID, prepend(commandLineArgs.split(" "), ""));
            }

            testCase.options().forEach((key, value) -> {
                if (key instanceof String && value instanceof String) {
                    String optionName = (String) key;
                    String optionValue = (String) value;
                    if (optionName.startsWith("wasm.")) {
                        contextBuilder.option(optionName, optionValue);
                    }
                }
            });

            final String envString = testCase.options().getProperty("env");
            if (envString != null) {
                for (String var : envString.split(" ")) {
                    final String[] parts = var.split("=");
                    contextBuilder.environment(parts[0], parts[1]);
                }
            }

            final boolean enableIO = Boolean.parseBoolean(testCase.options().getProperty("enable-io"));
            if (enableIO) {
                contextBuilder.allowIO(IOAccess.ALL);
                tempWorkingDirectory = Files.createTempDirectory("graalwasm-io-test");
                contextBuilder.currentWorkingDirectory(tempWorkingDirectory);
                contextBuilder.option("wasm.WasiMapDirs", "test::" + tempWorkingDirectory);

                // Create a file "file.txt" containing "Hello Graal! [rocket emoji]" in the
                // temporary test directory
                final Path testFile = tempWorkingDirectory.resolve("file.txt");
                Files.write(testFile, "Hello Graal! \uD83D\uDE80".getBytes(StandardCharsets.UTF_8));
            }

            final String stdinString = testCase.options().getProperty("stdin");
            if (stdinString != null) {
                final ByteArrayInputStream stdin = new ByteArrayInputStream(stdinString.getBytes(StandardCharsets.UTF_8));
                contextBuilder.in(stdin);
            }

            EnumSet<WasmBinaryTools.WabtOption> options = EnumSet.noneOf(WasmBinaryTools.WabtOption.class);
            String threadsOption = testCase.options().getProperty("wasm.Threads");
            if (threadsOption != null && threadsOption.equals("true")) {
                options.add(WasmBinaryTools.WabtOption.THREADS);
            }
            String multiMemoryOption = testCase.options().getProperty("wasm.MultiMemory");
            if (multiMemoryOption != null && multiMemoryOption.equals("true")) {
                options.add(WasmBinaryTools.WabtOption.MULTI_MEMORY);
            }
            ArrayList<Source> sources = testCase.getSources(options);

            runInContexts(testCase, contextBuilder, sources, sharedEngine, testOut);
        } catch (InterruptedException | IOException e) {
            throw new RuntimeException(String.format("Test %s failed: %s", testCase.name(), e.getMessage()));
        } finally {
            if (tempWorkingDirectory != null) {
                deleteFolder(tempWorkingDirectory.toFile());
            }
        }
        return WasmTestStatus.OK;
    }

    private void runInContexts(WasmCase testCase, Context.Builder contextBuilder, ArrayList<Source> sources, Engine sharedEngine, ByteArrayOutputStream testOut) throws IOException {
        Context context;
        if (sharedEngine == null) {
            // Run in interpreted mode, with inlining turned off, to ensure profiles are populated.
            int interpreterIterations = Integer.parseInt(testCase.options().getProperty("interpreter-iterations", String.valueOf(DEFAULT_INTERPRETER_ITERATIONS)));
            context = contextBuilder.options(getInterpretedNoInline()).build();
            runInContext(testCase, context, sources, interpreterIterations, PHASE_INTERPRETER_ICON, "interpreter", testOut);

            // Run in synchronous compiled mode, with inlining turned off.
            // We need to run the test at least twice like this, since the first run will lead to
            // de-opts due to empty profiles.
            int syncNoinlineIterations = Integer.parseInt(testCase.options().getProperty("sync-noinline-iterations", String.valueOf(DEFAULT_SYNC_NOINLINE_ITERATIONS)));
            context = contextBuilder.options(getSyncCompiledNoInline()).build();
            runInContext(testCase, context, sources, syncNoinlineIterations, PHASE_SYNC_NO_INLINE_ICON, "sync,no-inl", testOut);

            // Run in synchronous compiled mode, with inlining turned on.
            // We need to run the test at least twice like this, since the first run will lead to
            // de-opts due to empty profiles.
            int syncInlineIterations = Integer.parseInt(testCase.options().getProperty("sync-inline-iterations", String.valueOf(DEFAULT_SYNC_INLINE_ITERATIONS)));
            context = contextBuilder.options(getSyncCompiledWithInline()).build();
            runInContext(testCase, context, sources, syncInlineIterations, PHASE_SYNC_INLINE_ICON, "sync,inl", testOut);

            // Run with normal, asynchronous compilation.
            int asyncIterations = Integer.parseInt(testCase.options().getProperty("async-iterations", String.valueOf(DEFAULT_ASYNC_ITERATIONS)));
            context = contextBuilder.options(getAsyncCompiled()).build();
            runInContext(testCase, context, sources, asyncIterations, PHASE_ASYNC_ICON, "async,multi", testOut);
        } else {
            int asyncSharedIterations = testCase.options().containsKey("async-iterations") && !testCase.options().containsKey("async-shared-iterations")
                            ? Integer.parseInt(testCase.options().getProperty("async-iterations")) / 10
                            : Integer.parseInt(testCase.options().getProperty("async-shared-iterations", String.valueOf(DEFAULT_ASYNC_SHARED_ITERATIONS)));
            context = contextBuilder.build();
            runInContext(testCase, context, sources, asyncSharedIterations, PHASE_SHARED_ENGINE_ICON, "async,shared", testOut);
        }
    }

    protected String includedExternalModules() {
        return "testutil:testutil";
    }

    private static void validateThrown(WasmCaseData data, WasmCaseData.ErrorType phase, PolyglotException e) throws PolyglotException {
        if (data.expectedErrorMessage() == null || !data.expectedErrorMessage().equals(e.getMessage())) {
            throw new AssertionError("Expected error message [%s] but was: [%s]".formatted(data.expectedErrorMessage(), e.getMessage()), e);
        }
        Assert.assertEquals("Unexpected error phase.", data.expectedErrorTime(), phase);
    }

    public static void deleteFolder(File folder) {
        final File[] files = folder.listFiles();
        if (files != null) {
            for (final File f : files) {
                if (f.isDirectory()) {
                    deleteFolder(f);
                } else {
                    f.delete();
                }
            }
        }
        folder.delete();
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
        ExecutorService pool = WasmTestOptions.SHARED_ENGINE ? Executors.newFixedThreadPool(3) : null;
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
                if (!isPoorShell()) {
                    for (int i = 1; i < extraWidth; i++) {
                        System.out.print(" ");
                    }
                    System.out.flush();
                }

                if (WasmTestOptions.SHARED_ENGINE) {
                    try (Engine sharedEngine = Engine.newBuilder().allowExperimentalOptions(true).options(getAsyncCompiledShared()).build()) {
                        Callable<Void> task = () -> {
                            runTestCase(testCase, sharedEngine);
                            return null;
                        };
                        var tasks = IntStream.range(0, 3).mapToObj(i -> task).toList();
                        for (var f : pool.invokeAll(tasks)) {
                            f.get();
                        }
                    }
                } else {
                    runTestCase(testCase, null);
                }
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
        if (pool != null) {
            pool.shutdownNow();
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
        return testCases.stream().filter((WasmCase x) -> filterTestName().test(x.name())).filter(Predicate.not(WasmCase::isSkipped)).collect(Collectors.toList());
    }

    protected String suiteName() {
        return getClass().getSimpleName();
    }

    private static ContextState saveContext(WasmContext context) {
        final MemoryRegistry memories = context.memories().duplicate();
        final GlobalRegistry globals = context.globals().duplicate();
        return new ContextState(memories, globals, context.fdManager().size());
    }

    private static void assertContextEqual(ContextState expectedState, ContextState actualState) {
        // Compare memories
        final MemoryRegistry expectedMemories = expectedState.memories();
        final MemoryRegistry actualMemories = actualState.memories();
        Assert.assertEquals("Mismatch in memory counts.", expectedMemories.count(), actualMemories.count());
        for (int i = 0; i < expectedMemories.count(); i++) {
            final WasmMemory expectedMemory = expectedMemories.memory(i);
            final WasmMemory actualMemory = actualMemories.memory(i);
            if (expectedMemory == null) {
                Assert.assertNull("Memory should be null", actualMemory);
            } else {
                Assert.assertNotNull("Memory should not be null", actualMemory);
                Assert.assertEquals("Mismatch in memory lengths", expectedMemory.byteSize(), actualMemory.byteSize());
                for (int ptr = 0; ptr < expectedMemory.byteSize(); ptr++) {
                    byte expectedByte = (byte) expectedMemory.load_i32_8s(null, ptr);
                    byte actualByte = (byte) actualMemory.load_i32_8s(null, ptr);
                    Assert.assertEquals("Memory mismatch at offset " + ptr + ",", expectedByte, actualByte);
                }
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

        // Check number of opened file descriptors
        Assert.assertEquals("Mismatch in file descriptor counts.", expectedState.openedFdCount, actualState.openedFdCount);
    }

    private static final class ContextState {
        private final MemoryRegistry memories;
        private final GlobalRegistry globals;
        private final int openedFdCount;

        private ContextState(MemoryRegistry memories, GlobalRegistry globals, int openedFdCount) {
            this.memories = memories;
            this.globals = globals;
            this.openedFdCount = openedFdCount;
        }

        public MemoryRegistry memories() {
            return memories;
        }

        public GlobalRegistry globals() {
            return globals;
        }
    }
}
