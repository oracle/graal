/*
 *  Copyright (c) 2019, Oracle and/or its affiliates.
 *
 *  All rights reserved.
 *
 *  Redistribution and use in source and binary forms, with or without modification, are
 *  permitted provided that the following conditions are met:
 *
 *  1. Redistributions of source code must retain the above copyright notice, this list of
 *  conditions and the following disclaimer.
 *
 *  2. Redistributions in binary form must reproduce the above copyright notice, this list of
 *  conditions and the following disclaimer in the documentation and/or other materials provided
 *  with the distribution.
 *
 *  3. Neither the name of the copyright holder nor the names of its contributors may be used to
 *  endorse or promote products derived from this software without specific prior written
 *  permission.
 *
 *  THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 *  OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 *  MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 *  COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 *  EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 *  GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 *  AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 *  NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 *  OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oracle.truffle.wasm.test.next;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.io.ByteSequence;
import org.hamcrest.CoreMatchers;
import org.junit.Assert;

import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.wasm.test.next.options.WasmTestOptions;

public abstract class WasmSuiteBase extends WasmTestBase {
    enum WasmTestStatus {
        OK, SKIPPED;
    }

    private static WasmTestStatus runTestCase(WasmTestCase testCase) {
        if (!filterTestName().test(testCase.name)) {
            return WasmTestStatus.SKIPPED;
        }
        try {
            byte[] binary = testCase.selfCompile();
            Context context = Context.create();
            Source source = Source.newBuilder("wasm", ByteSequence.create(binary), "test").build();
            context.eval(source);
            Value function = context.getBindings("wasm").getMember("0");
            if (WasmTestOptions.TRIGGER_GRAAL) {
                for (int i = 0; i !=  1_000_000; ++i) {
                    function.execute();
                }
            }
            validateResult(testCase.name, testCase.data.resultValidator, function.execute());
        } catch (InterruptedException | IOException e) {
            Assert.fail(String.format("Test %s failed.", testCase.name));
            e.printStackTrace();
        } catch (PolyglotException e) {
            validateThrown(testCase.name, testCase.data.expectedException, e);
        }
        return WasmTestStatus.OK;
    }

    private static void validateResult(String testName, Consumer<Value> validator, Value result) {
        if (validator != null) {
            validator.accept(result);
        } else {
            Assert.fail(String.format("Test %s was not expected to return a value.", testName));
        }
    }

    private static void validateThrown(String testName, Class<? extends PolyglotException> expected, PolyglotException e) throws PolyglotException{
        if (expected != null) {
            Assert.assertThat(String.format("Test %s threw an unexpected exception (%s instead of %s).", testName, e.toString(), expected.toString()), e, CoreMatchers.instanceOf(expected));
        } else {
            Assert.fail(String.format("Test %s was not expected to throw an exception.", testName));
        }
    }

    @Override
    public void test() {
        Collection<? extends WasmTestCase> testCases = collectTestCases();
        Map<WasmTestCase, Throwable> errors = new LinkedHashMap<>();
        System.out.println("");
        System.out.println("--------------------------------------------------------------------------------");
        System.out.println(String.format("Running: %s (%d tests)", suiteName(), testCases.size()));
        System.out.println("--------------------------------------------------------------------------------");
        System.out.println("Using runtime: " + Truffle.getRuntime().toString());
        for (WasmTestCase testCase : testCases) {
            try {
                WasmTestStatus status = runTestCase(testCase);
                if (status == WasmTestStatus.SKIPPED) {
                    continue;
                }
                System.out.print("\uD83D\uDE0D");
                System.out.flush();
            } catch (Throwable e) {
                System.out.print("\uD83D\uDE21");
                System.out.flush();
                errors.put(testCase, e);
            }
        }
        System.out.println("");
        System.out.println("Finished running: " + suiteName());
        if (!errors.isEmpty()) {
            for (Map.Entry<WasmTestCase, Throwable> entry : errors.entrySet()) {
                System.err.println(String.format("Failure in: %s.%s", suiteName(), entry.getKey().name));
                System.err.println(entry.getValue().getClass().getSimpleName() + ": " + entry.getValue().getMessage());
                entry.getValue().printStackTrace();
            }
            System.err.println(String.format("\uD83D\uDCA5\u001B[31m %d/%d Wasm tests passed.\u001B[0m", testCases.size() - errors.size(), testCases.size()));
        } else {
            System.out.println(String.format("\uD83C\uDF40\u001B[32m %d/%d Wasm tests passed.\u001B[0m", testCases.size() - errors.size(), testCases.size()));
        }
        System.out.println("");
    }

    protected abstract Collection<? extends WasmTestCase> collectTestCases();

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

    protected static WasmTestCaseData expectedThrows(Class<? extends PolyglotException> expectedExceptionClass) {
        return new WasmTestCaseData(expectedExceptionClass);
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
