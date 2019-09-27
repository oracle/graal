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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.oracle.truffle.wasm.predefined.testutil.TestutilModule;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.io.ByteSequence;
import org.junit.Assert;

import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.wasm.test.options.WasmTestOptions;

public abstract class WasmSuiteBase extends WasmTestBase {
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

    private Context getInterpretedWithInline(Context.Builder contextBuilder) {
        contextBuilder.option("engine.Compilation", "false");
        contextBuilder.option("engine.Inlining", "true");
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

    private Value runInContext(Context context, Source source, int iterations) {
        context.eval(source);

        // The sequence of WebAssembly functions to execute.
        // First, we execute the main function (exported as "_main").
        // Then, we execute a special function, which resets the globals to their initial values.
        Value mainFunction = context.getBindings("wasm").getMember("_main");
        Value resetGlobals = context.getBindings("wasm").getMember(TestutilModule.Names.RESET_GLOBALS);

        Value result = null;

        for (int i = 0; i != iterations; ++i) {
            result = mainFunction.execute();
            resetGlobals.execute();
        }

        return result;
    }

    private WasmTestStatus runTestCase(WasmTestCase testCase) {
        try {
            byte[] binary = testCase.selfCompile();
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
            context = getInterpretedNoInline(contextBuilder);
            runInContext(context, source, 1);

            // Run in synchronous compiled mode, with inlining turned off.
            // We need to run the test at least twice like this, since the first run will lead to de-opts due to empty profiles.
            context = getSyncCompiledNoInline(contextBuilder);
            runInContext(context, source, 2);

            // Run in interpreted mode, with inlining turned on, to ensure profiles are populated.
            context = getInterpretedWithInline(contextBuilder);
            runInContext(context, source, 1);

            // Run in synchronous compiled mode, with inlining turned on.
            // We need to run the test at least twice like this, since the first run will lead to de-opts due to empty profiles.
            context = getSyncCompiledWithInline(contextBuilder);
            runInContext(context, source, 2);

            // Run with normal, asynchronous compilation, 1000 times.
            context = getAsyncCompiled(contextBuilder);
            final Value result = runInContext(context, source, 1000);

            validateResult(testCase.data.resultValidator, result);
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

    private static void validateResult(Consumer<Value> validator, Value result) {
        if (validator != null) {
            validator.accept(result);
        } else {
            Assert.fail("Test was not expected to return a value.");
        }
    }

    private static void validateThrown(String expectedErrorMessage, PolyglotException e) throws PolyglotException{
        if (expectedErrorMessage != null) {
            if (!expectedErrorMessage.equals(e.getMessage())){
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
        for (WasmTestCase testCase : qualifyingTestCases) {
            String statusIcon = "\u003F";
            try {
                // We print each test name behind the line of test status icons,
                // so that we know which test failed in case the VM exits suddenly.
                // If the test fails normally or succeeds, then we move the cursor to the left,
                // and erase the test name.
                System.out.print(" ");
                System.out.print(testCase.name);
                System.out.flush();
                runTestCase(testCase);
                statusIcon = "\uD83D\uDE0D";
            } catch (Throwable e) {
                statusIcon = "\uD83D\uDE21";
                errors.put(testCase, e);
            } finally {
                for (int i = 0; i < testCase.name.length() + 1; i++) {
                    System.out.print("\u001b[1D");
                    System.out.print(" ");
                    System.out.print("\u001b[1D");
                }
                System.out.print(statusIcon);
                System.out.flush();
            }
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

    protected Path testDirectory() {
        return null;
    }

    protected Collection<? extends WasmTestCase> collectTestCases() throws IOException {
        return Stream.concat(collectStringTestCases().stream(), collectFileTestCases(testDirectory()).stream()).collect(Collectors.toList());
    }

    protected Collection<? extends WasmTestCase> collectStringTestCases() {
        return new ArrayList<>();
    }

    protected Collection<? extends WasmTestCase> filterTestCases(Collection<? extends WasmTestCase> testCases) {
        return testCases.stream().filter((WasmTestCase x) -> filterTestName().test(x.name)).collect(Collectors.toList());
    }

    protected Collection<WasmTestCase> collectFileTestCases(Path path) throws IOException {
        Collection<WasmTestCase> collectedCases = new ArrayList<>();
        if (path == null) {
            return collectedCases;
        }
        try (Stream<Path> walk = Files.list(path)) {
            List<Path> testFiles = walk.filter(isWatFile).collect(Collectors.toList());
            for (Path f : testFiles) {
                String baseFileName = f.toAbsolutePath().toString().split("\\.(?=[^.]+$)")[0];
                String testName = Paths.get(baseFileName).getFileName().toString();
                Path resultPath = Paths.get(baseFileName + ".result");
                String resultSpec = Files.lines(resultPath).limit(1).collect(Collectors.joining());
                String[] resultTypeValue = resultSpec.split("\\s+");
                String resultType = resultTypeValue[0];
                String resultValue = resultTypeValue[1];
                switch (resultType) {
                    case "int":
                        collectedCases.add(testCase(testName, expected(Integer.parseInt(resultValue)), f.toFile()));
                        break;
                    case "long":
                        collectedCases.add(testCase(testName, expected(Long.parseLong(resultValue)), f.toFile()));
                        break;
                    case "float":
                        collectedCases.add(testCase(testName, expectedFloat(Float.parseFloat(resultValue), 0.0001f), f.toFile()));
                        break;
                    case "double":
                        collectedCases.add(testCase(testName, expectedDouble(Double.parseDouble(resultValue), 0.0001f), f.toFile()));
                        break;
                    default:
                        // TODO: We should throw an exception here if the result type is not known,
                        //  and have an explicit type for exception throws.
                        collectedCases.add(testCase(testName, expectedThrows(resultValue), f.toFile()));
                        break;
                }
            }
        }
        return collectedCases;
    }

    protected String suiteName() {
        return getClass().getSimpleName();
    }

    protected static WasmStringTestCase testCase(String name, WasmTestCaseData data, String program) {
        return new WasmStringTestCase(name, data, program);
    }

    protected static WasmFileTestCase testCase(String name, WasmTestCaseData data, File program) {
        return new WasmFileTestCase(name, data, program);
    }

    protected static WasmTestCaseData expected(Object expectedValue) {
        return new WasmTestCaseData((Value result) -> Assert.assertEquals("Failure", expectedValue, result.as(Object.class)));
    }

    protected static WasmTestCaseData expectedFloat(float expectedValue, float delta) {
        return new WasmTestCaseData((Value result) -> Assert.assertEquals("Failure", expectedValue, result.as(Float.class), delta));
    }

    protected static WasmTestCaseData expectedDouble(double expectedValue, float delta) {
        return new WasmTestCaseData((Value result) -> Assert.assertEquals("Failure", expectedValue, result.as(Double.class), delta));
    }

    protected static WasmTestCaseData expectedThrows(String expectedErrorMessage) {
        return new WasmTestCaseData(expectedErrorMessage);
    }

    protected static abstract class WasmTestCase {
        private String name;
        private WasmTestCaseData data;

        WasmTestCase(String name, WasmTestCaseData data) {
            this.name = name;
            this.data = data;
        }

        public abstract byte[] selfCompile() throws IOException, InterruptedException;
    }

    protected static class WasmStringTestCase extends WasmTestCase {
        private String program;

        WasmStringTestCase(String name, WasmTestCaseData data, String program) {
            super(name, data);
            this.program = program;
        }

        @Override
        public byte[] selfCompile() throws IOException, InterruptedException {
            return WasmTestToolkit.compileWatString(program);
        }
    }

    protected static class WasmFileTestCase extends WasmTestCase {
        private File program;

        public WasmFileTestCase(String name, WasmTestCaseData data, File program) {
            super(name, data);
            this.program = program;
        }

        @Override
        public byte[] selfCompile() throws IOException, InterruptedException {
            return WasmTestToolkit.compileWatFile(program);
        }
    }
}
