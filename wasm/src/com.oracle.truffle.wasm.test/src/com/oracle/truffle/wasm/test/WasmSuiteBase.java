/*
 * Copyright (c) 2019, Oracle and/or its affiliates.
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
package com.oracle.truffle.wasm.test;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.wasm.binary.WasmContext;
import com.oracle.truffle.wasm.binary.WasmModule;
import com.oracle.truffle.wasm.binary.memory.WasmMemory;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.io.ByteSequence;
import org.junit.Assert;

import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.wasm.predefined.testutil.TestutilModule;
import com.oracle.truffle.wasm.test.options.WasmTestOptions;

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

    private Context getInterpretedNoInline(Context.Builder contextBuilder) {
        contextBuilder.option("engine.Compilation", "false");
        contextBuilder.option("engine.Inlining", "false");
        return contextBuilder.build();
    }

    private Context getSyncCompiledNoInline(Context.Builder contextBuilder) {
        contextBuilder.option("engine.Compilation", "true");
        contextBuilder.option("engine.BackgroundCompilation", "false");
        contextBuilder.option("engine.CompileImmediately", "true");
        contextBuilder.option("engine.Inlining", "false");
        return contextBuilder.build();
    }

    private Context getSyncCompiledWithInline(Context.Builder contextBuilder) {
        contextBuilder.option("engine.Compilation", "true");
        contextBuilder.option("engine.BackgroundCompilation", "false");
        contextBuilder.option("engine.CompileImmediately", "true");
        contextBuilder.option("engine.Inlining", "true");
        return contextBuilder.build();
    }

    private Context getAsyncCompiled(Context.Builder contextBuilder) {
        contextBuilder.option("engine.Compilation", "true");
        contextBuilder.option("engine.BackgroundCompilation", "true");
        contextBuilder.option("engine.CompileImmediately", "false");
        contextBuilder.option("engine.Inlining", "true");
        return contextBuilder.build();
    }

    private Value runInContext(WasmTestCase testCase, Context context, Source source, int iterations, String phaseIcon, String phaseLabel) {
        boolean requiresZeroMemory = Boolean.parseBoolean(testCase.options().getProperty("zero-memory", "false"));

        final PrintStream oldOut = System.out;
        resetStatus(oldOut, PHASE_PARSE_ICON, "parsing");
        context.eval(source);

        // The sequence of WebAssembly functions to execute.
        // Run custom initialization.
        // Execute the main function (exported as "_main").
        // Then, optionally save memory and globals, and compare them.
        // Execute a special function, which resets memory and globals to their default values.
        Value mainFunction = context.getBindings("wasm").getMember("_main");
        Value resetContext = context.getBindings("wasm").getMember(TestutilModule.Names.RESET_CONTEXT);
        Value customInitialize = context.getBindings("wasm").getMember(TestutilModule.Names.RUN_CUSTOM_INITIALIZATION);
        Value saveContext = context.getBindings("wasm").getMember(TestutilModule.Names.SAVE_CONTEXT);
        Value compareContexts = context.getBindings("wasm").getMember(TestutilModule.Names.COMPARE_CONTEXTS);

        Value result = null;
        resetStatus(oldOut, phaseIcon, phaseLabel);
        ByteArrayOutputStream capturedStdout = null;
        Object firstIterationContextState = null;

        for (int i = 0; i != iterations; ++i) {
            try {
                capturedStdout = new ByteArrayOutputStream();
                System.setOut(new PrintStream(capturedStdout));

                // Run custom initialization.
                if (testCase.initialization() != null) {
                    customInitialize.execute(testCase.initialization());
                }

                // Execute benchmark.
                result = mainFunction.execute();

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

                validateResult(testCase.data.resultValidator, result, capturedStdout);
            } catch (PolyglotException e) {
                // We cannot label the tests with polyglot errors, because they might be return values.
                throw e;
            } catch (Throwable t) {
                final RuntimeException e = new RuntimeException("Error during test phase '" + phaseLabel + "'", t);
                e.setStackTrace(new StackTraceElement[0]);
                throw e;
            } finally {
                System.setOut(oldOut);
            }
        }

        return result;
    }

    private boolean iterationNeedsStateCheck(int i) {
        return i < INITIAL_STATE_CHECK_ITERATIONS || i % STATE_CHECK_PERIODICITY == 0;
    }

    private void resetStatus(PrintStream oldOut, String icon, String label) {
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

    private void eraseStatus(PrintStream oldOut) {
        for (int i = 0; i < STATUS_ICON_WIDTH + STATUS_LABEL_WIDTH; i++) {
            oldOut.print(MOVE_LEFT);
        }
    }

    private WasmTestStatus runTestCase(WasmTestCase testCase) {
        try {
            byte[] binary = testCase.createBinary();
            Context.Builder contextBuilder = Context.newBuilder("wasm");
            Source.Builder sourceBuilder = Source.newBuilder("wasm", ByteSequence.create(binary), "test");

            if (WasmTestOptions.LOG_LEVEL != null && !WasmTestOptions.LOG_LEVEL.equals("")) {
                contextBuilder.option("log.wasm.level", WasmTestOptions.LOG_LEVEL);
            }

            contextBuilder.allowExperimentalOptions(true);
            contextBuilder.option("wasm.PredefinedModules", includedExternalModules());
            Source source = sourceBuilder.build();
            Context context;

            // Run in interpreted mode, with inlining turned off, to ensure profiles are populated.
            int interpreterIterations = Integer.parseInt(testCase.options().getProperty("interpreter-iterations", String.valueOf(DEFAULT_INTERPRETER_ITERATIONS)));
            context = getInterpretedNoInline(contextBuilder);
            runInContext(testCase, context, source, interpreterIterations, PHASE_INTERPRETER_ICON, "interpreter");

            // Run in synchronous compiled mode, with inlining turned off.
            // We need to run the test at least twice like this, since the first run will lead to de-opts due to empty profiles.
            int syncNoinlineIterations = Integer.parseInt(testCase.options().getProperty("sync-noinline-iterations", String.valueOf(DEFAULT_SYNC_NOINLINE_ITERATIONS)));
            context = getSyncCompiledNoInline(contextBuilder);
            runInContext(testCase, context, source, syncNoinlineIterations, PHASE_SYNC_NO_INLINE_ICON, "sync,no-inl");

            // Run in synchronous compiled mode, with inlining turned on.
            // We need to run the test at least twice like this, since the first run will lead to de-opts due to empty profiles.
            int syncInlineIterations = Integer.parseInt(testCase.options().getProperty("sync-inline-iterations", String.valueOf(DEFAULT_SYNC_INLINE_ITERATIONS)));
            context = getSyncCompiledWithInline(contextBuilder);
            runInContext(testCase, context, source, syncInlineIterations, PHASE_SYNC_INLINE_ICON, "sync,inl");

            // Run with normal, asynchronous compilation.
            // Run 1000 + 1 times - the last time run with a surrogate stream, to collect output.
            int asyncIterations = Integer.parseInt(testCase.options().getProperty("async-iterations", String.valueOf(DEFAULT_ASYNC_ITERATIONS)));
            context = getAsyncCompiled(contextBuilder);
            runInContext(testCase, context, source, asyncIterations, PHASE_ASYNC_ICON, "async,multi");
        } catch (InterruptedException | IOException e) {
            Assert.fail(String.format("Test %s failed: %s", testCase.name, e.getMessage()));
        } catch (PolyglotException e) {
            validateThrown(testCase.data.expectedErrorMessage, e);
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

    private static void validateThrown(String expectedErrorMessage, PolyglotException e) throws PolyglotException{
        if (expectedErrorMessage != null) {
            if (!expectedErrorMessage.equals(e.getMessage())) {
                throw e;
            }
        } else {
            throw e;
        }
    }

    @Override
    public void test() throws IOException {
        Collection<? extends WasmTestCase> allTestCases = collectTestCases();
        Collection<? extends WasmTestCase> qualifyingTestCases = filterTestCases(allTestCases);
        Map<WasmTestCase, Throwable> errors = new LinkedHashMap<>();
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
        for (WasmTestCase testCase : qualifyingTestCases) {
            int extraWidth = 1 + STATUS_ICON_WIDTH + STATUS_LABEL_WIDTH;
            int requiredWidth = testCase.name.length() + extraWidth;
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
                System.out.print(testCase.name);
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
                for (int i = 0; i < requiredWidth; i++) {
                    System.out.print(MOVE_LEFT);
                    System.out.print(" ");
                    System.out.print(MOVE_LEFT);
                }
                System.out.print(statusIcon);
                System.out.flush();
            }
            position++;
        }
        System.out.println();
        System.out.println("Finished running: " + suiteName());
        if (!errors.isEmpty()) {
            for (Map.Entry<WasmTestCase, Throwable> entry : errors.entrySet()) {
                System.err.println(String.format("Failure in: %s.%s", suiteName(), entry.getKey().name));
                System.err.println(entry.getValue().getClass().getSimpleName() + ": " + entry.getValue().getMessage());
                entry.getValue().printStackTrace();
            }
            System.err.println(String.format("\uD83D\uDCA5\u001B[31m %d/%d Wasm tests passed.\u001B[0m", qualifyingTestCases.size() - errors.size(), qualifyingTestCases.size()));
        } else {
            System.out.println(String.format("\uD83C\uDF40\u001B[32m %d/%d Wasm tests passed.\u001B[0m", qualifyingTestCases.size() - errors.size(), qualifyingTestCases.size()));
        }
        System.out.println();
    }

    private int retrieveTerminalWidth() {
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
            throw new RuntimeException(e);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    protected String testResource() {
        return null;
    }

    protected Collection<? extends WasmTestCase> collectTestCases() throws IOException {
        return Stream.concat(collectStringTestCases().stream(), collectFileTestCases(testResource()).stream()).collect(Collectors.toList());
    }

    protected Collection<? extends WasmTestCase> collectStringTestCases() {
        return new ArrayList<>();
    }

    protected Collection<? extends WasmTestCase> filterTestCases(Collection<? extends WasmTestCase> testCases) {
        return testCases.stream().filter((WasmTestCase x) -> filterTestName().test(x.name)).collect(Collectors.toList());
    }

    protected String getResourceAsString(String resourceName, boolean fail) throws IOException {
        byte[] contents = getResourceAsBytes(resourceName, fail);
        if (contents != null) {
            return new String(contents);
        } else {
            assert !fail;
            return null;
        }
    }

    protected byte[] getResourceAsBytes(String resourceName, boolean fail) throws IOException {
        InputStream stream = getClass().getResourceAsStream(resourceName);
        if (stream == null) {
            if (fail) {
                Assert.fail(String.format("Could not find resource: %s", resourceName));
            } else {
                return null;
            }
        }
        byte[] contents = new byte[stream.available()];
        new DataInputStream(stream).readFully(contents);
        return contents;
    }

    protected Object getResourceAsTest(String baseName, boolean fail) throws IOException {
        final byte[] bytes = getResourceAsBytes(baseName + ".wasm", false);
        if (bytes != null) {
            return bytes;
        }
        final String text = getResourceAsString(baseName + ".wat", false);
        if (text != null) {
            return text;
        }
        if (fail) {
            Assert.fail(String.format("Could not find test (neither .wasm or .wat): %s", baseName));
        }
        return null;
    }

    protected Collection<WasmTestCase> collectFileTestCases(String testBundle) throws IOException {
        Collection<WasmTestCase> collectedCases = new ArrayList<>();
        if (testBundle == null) {
            return collectedCases;
        }

        // Open the wasm_test_index file of the test bundle. The wasm_test_index file contains the available tests for that bundle.
        InputStream index = getClass().getResourceAsStream(String.format("/test/%s/wasm_test_index", testBundle));
        BufferedReader indexReader = new BufferedReader(new InputStreamReader(index));

        // Iterate through the available test of the bundle.
        while (indexReader.ready()) {
            String testName = indexReader.readLine().trim();

            if (testName.equals("") || testName.startsWith("#")) {
                // Skip empty lines or lines starting with a hash (treat as a comment).
                continue;
            }

            Object mainContent = getResourceAsTest(String.format("/test/%s/%s", testBundle, testName), true);
            String resultContent = getResourceAsString(String.format("/test/%s/%s.result", testBundle, testName), true);
            String initContent = getResourceAsString(String.format("/test/%s/%s.init", testBundle, testName), false);
            String optsContent = getResourceAsString(String.format("/test/%s/%s.opts", testBundle, testName), false);
            WasmTestInitialization initializer = testInitialization(initContent);
            Properties options = testOptions(optsContent);

            String[] resultTypeValue = resultContent.split("\\s+", 2);
            String resultType = resultTypeValue[0];
            String resultValue = resultTypeValue[1];

            WasmTestCaseData testData = null;
            switch (resultType) {
                case "stdout":
                    testData = expectedStdout(resultValue);
                    break;
                case "int":
                    testData = expected(Integer.parseInt(resultValue.trim()));
                    break;
                case "long":
                    testData = expected(Long.parseLong(resultValue.trim()));
                    break;
                case "float":
                    testData = expected(Float.parseFloat(resultValue.trim()));
                    break;
                case "double":
                    testData = expected(Double.parseDouble(resultValue.trim()));
                    break;
                case "exception":
                    testData = expectedThrows(resultValue);
                    break;
                default:
                    Assert.fail(String.format("Unknown type in result specification: %s", resultType));
            }
            if (mainContent instanceof String) {
                collectedCases.add(testCase(testName, testData, (String) mainContent, initializer, options));
            } else if (mainContent instanceof byte[]) {
                collectedCases.add(testCase(testName, testData, (byte[]) mainContent, initializer, options));
            } else {
                Assert.fail("Unknown content type: " + mainContent.getClass());
            }
        }

        return collectedCases;
    }

    protected String suiteName() {
        return getClass().getSimpleName();
    }

    protected static WasmStringTestCase testCase(String name, WasmTestCaseData data, String program) {
        return new WasmStringTestCase(name, data, program, null, new Properties());
    }

    protected static WasmStringTestCase testCase(String name, WasmTestCaseData data, String program, WasmTestInitialization initializer, Properties options) {
        return new WasmStringTestCase(name, data, program, initializer, options);
    }

    protected static WasmBinaryTestCase testCase(String name, WasmTestCaseData data, byte[] binary, WasmTestInitialization initializer, Properties options) {
        return new WasmBinaryTestCase(name, data, binary, initializer, options);
    }

    protected static WasmTestCaseData expectedStdout(String expectedOutput) {
        return new WasmTestCaseData((Value result, String output) -> Assert.assertEquals("Failure: stdout: ", expectedOutput, output));
    }

    protected static WasmTestCaseData expected(Object expectedValue) {
        return new WasmTestCaseData((Value result, String output) -> Assert.assertEquals("Failure: result: ", expectedValue, result.as(Object.class)));
    }

    protected static WasmTestCaseData expectedFloat(float expectedValue, float delta) {
        return new WasmTestCaseData((Value result, String output) -> Assert.assertEquals("Failure: result: ", expectedValue, result.as(Float.class), delta));
    }

    protected static WasmTestCaseData expectedDouble(double expectedValue, float delta) {
        return new WasmTestCaseData((Value result, String output) -> Assert.assertEquals("Failure: result: ", expectedValue, result.as(Double.class), delta));
    }

    protected static WasmTestCaseData expectedThrows(String expectedErrorMessage) {
        return new WasmTestCaseData(expectedErrorMessage);
    }

    protected Properties testOptions(String optsContent) {
        Properties options = new Properties();
        if (optsContent == null) {
            return options;
        }

        try {
            options.load(new StringReader(optsContent));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return options;
    }

    protected WasmTestInitialization testInitialization(String initContent) {
        if (initContent == null) {
            return null;
        }

        final String[] lines = initContent.split("\n");
        Map<String, Long> globals = new LinkedHashMap<>();
        Map<String, String> memory = new LinkedHashMap<>();
        for (String line : lines) {
            if (line.startsWith("[")) {
                // Memory store.
                String[] parts = line.split("=");
                String address = parts[0].substring(1, parts[0].length() - 1);
                String value = parts[1];
                memory.put(address, value);
            } else {
                // Global store.
                String[] parts = line.split("=");
                String name = parts[0];
                Long value = Long.parseLong(parts[1]);
                globals.put(name, value);
            }
        }
        return new WasmTestInitialization(globals, memory);
    }

    protected static class WasmTestInitialization implements Consumer<WasmContext>, TruffleObject {
        private final Map<String, Long> globalValues;
        private final Map<String, String> memoryValues;

        public WasmTestInitialization(Map<String, Long> globalValues, Map<String, String> memoryValues) {
            this.globalValues = globalValues;
            this.memoryValues = memoryValues;
        }

        public void accept(WasmContext context) {
            try {
                final WasmModule module = context.modules().get("env");
                for (Map.Entry<String, Long> entry : globalValues.entrySet()) {
                    final String name = entry.getKey();
                    final Long value = entry.getValue();
                    if (!module.isLinked() || module.global(name).isMutable()) {
                        module.writeMember(name, value);
                    }
                }
                final WasmMemory memory = (WasmMemory) module.readMember("memory");
                for (Map.Entry<String, String> entry : memoryValues.entrySet()) {
                    final String addressGlobal = entry.getKey();
                    final long address = getValue(addressGlobal);
                    final String valueGlobal = entry.getValue();
                    final long value = getValue(valueGlobal);
                    // The memory array writes are indexed with 64-bit words.
                    // Therefore, we need to divide the byte-based address index with 8.
                    memory.writeArrayElement(address / 8, value);
                }
            } catch (UnknownIdentifierException | UnsupportedMessageException | InvalidArrayIndexException e) {
                throw new RuntimeException(e);
            }
        }

        private long getValue(String s) {
            if (Character.isDigit(s.charAt(0))) {
                return Integer.parseInt(s);
            } else {
                return globalValues.get(s);
            }
        }
    }

    protected static abstract class WasmTestCase {
        private final String name;
        private final WasmTestCaseData data;
        private final WasmTestInitialization initialization;
        private final Properties options;

        WasmTestCase(String name, WasmTestCaseData data, WasmTestInitialization initialization, Properties options) {
            this.name = name;
            this.data = data;
            this.initialization = initialization;
            this.options = options;
        }

        public abstract byte[] createBinary() throws IOException, InterruptedException;

        public WasmTestInitialization initialization() {
            return initialization;
        }

        public Properties options() {
            return options;
        }
    }

    protected static class WasmStringTestCase extends WasmTestCase {
        private String program;

        WasmStringTestCase(String name, WasmTestCaseData data, String program, WasmTestInitialization initializer, Properties options) {
            super(name, data, initializer, options);
            this.program = program;
        }

        @Override
        public byte[] createBinary() throws IOException, InterruptedException {
            return WasmTestToolkit.compileWatString(program);
        }
    }

    protected static class WasmBinaryTestCase extends WasmTestCase {
        private final byte[] binary;

        public WasmBinaryTestCase(String name, WasmTestCaseData data, byte[] binary, WasmTestInitialization initializer, Properties options) {
            super(name, data, initializer, options);
            this.binary = binary;
        }

        @Override
        public byte[] createBinary() throws IOException, InterruptedException {
            return binary;
        }
    }
}
