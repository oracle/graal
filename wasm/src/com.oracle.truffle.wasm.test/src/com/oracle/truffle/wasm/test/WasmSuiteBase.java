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

import com.oracle.truffle.wasm.binary.WasmContext;
import com.oracle.truffle.wasm.binary.WasmOptions;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.io.ByteSequence;
import org.junit.Assert;

import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.wasm.test.options.WasmTestOptions;

public abstract class WasmSuiteBase extends WasmTestBase {
    private WasmTestStatus runTestCase(WasmTestCase testCase) {
        try {
            byte[] binary = testCase.selfCompile();
            Context.Builder contextBuilder = Context.newBuilder("wasm");
            if (WasmTestOptions.LOG_LEVEL != null) {
                contextBuilder.option("log.wasm.level", WasmTestOptions.LOG_LEVEL);
            }
            // TODO: Readd.
            // contextBuilder.option("wasm.PredefinedModules", includedExternalModules());
            Context context = contextBuilder.build();
            Source source = Source.newBuilder("wasm", ByteSequence.create(binary), "test").build();
            context.eval(source);
            Value function = context.getBindings("wasm").getMember("_main");
            if (WasmTestOptions.TRIGGER_GRAAL) {
                for (int i = 0; i !=  10_000_000; ++i) {
                    function.execute();
                }
            }
            validateResult(testCase.data.resultValidator, function.execute());
        } catch (InterruptedException | IOException e) {
            Assert.fail(String.format("Test %s failed: %s", testCase.name, e.getMessage()));
        } catch (PolyglotException e) {
            validateThrown(testCase.data.expectedErrorMessage, e);
        }
        return WasmTestStatus.OK;
    }

    protected String includedExternalModules() {
        return "";
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
            try {
                runTestCase(testCase);
                System.out.print("\uD83D\uDE0D");
                System.out.flush();
            } catch (Throwable e) {
                System.out.print("\uD83D\uDE21");
                System.out.flush();
                errors.put(testCase, e);
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
                        collectedCases.add(testCase(testName, expected(Float.parseFloat(resultValue), 0.0001f), f.toFile()));
                        break;
                    case "double":
                        collectedCases.add(testCase(testName, expected(Double.parseDouble(resultValue), 0.0001f), f.toFile()));
                        break;
                    default:
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

    protected static WasmTestCaseData expected(float expectedValue, float delta) {
        return new WasmTestCaseData((Value result) -> Assert.assertEquals("Failure", expectedValue, result.as(Float.class), delta));
    }

    protected static WasmTestCaseData expected(double expectedValue, float delta) {
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
