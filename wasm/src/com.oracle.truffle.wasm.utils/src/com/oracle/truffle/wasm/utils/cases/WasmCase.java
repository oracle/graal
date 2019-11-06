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
package com.oracle.truffle.wasm.utils.cases;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Properties;
import java.util.function.BiConsumer;

import com.oracle.truffle.wasm.utils.Assert;
import com.oracle.truffle.wasm.utils.SystemProperties;
import com.oracle.truffle.wasm.utils.WasmInitialization;
import com.oracle.truffle.wasm.utils.WasmResource;
import org.graalvm.polyglot.Value;

/**
 * Instances of this class are used for WebAssembly test/benchmark cases.
 */
public abstract class WasmCase {
    private final String name;
    private final WasmCaseData data;
    private final WasmInitialization initialization;
    private final Properties options;

    public WasmCase(String name, WasmCaseData data, WasmInitialization initialization, Properties options) {
        this.name = name;
        this.data = data;
        this.initialization = initialization;
        this.options = options;
    }

    public String name() {
        return name;
    }

    public WasmCaseData data() {
        return data;
    }

    public WasmInitialization initialization() {
        return initialization;
    }

    public Properties options() {
        return options;
    }

    public abstract byte[] createBinary() throws IOException, InterruptedException;

    public static WasmStringCase create(String name, WasmCaseData data, String program) {
        return new WasmStringCase(name, data, program, null, new Properties());
    }

    public static WasmStringCase create(String name, WasmCaseData data, String program, WasmInitialization initializer, Properties options) {
        return new WasmStringCase(name, data, program, initializer, options);
    }

    public static WasmBinaryCase create(String name, WasmCaseData data, byte[] binary, WasmInitialization initializer, Properties options) {
        return new WasmBinaryCase(name, data, binary, initializer, options);
    }

    public static WasmCaseData expectedStdout(String expectedOutput) {
        return new WasmCaseData((Value result, String output) -> Assert.assertEquals("Failure: stdout:", expectedOutput, output));
    }

    public static WasmCaseData expected(Object expectedValue) {
        return new WasmCaseData((Value result, String output) -> Assert.assertEquals("Failure: result:", expectedValue, result.as(Object.class)));
    }

    public static WasmCaseData expectedFloat(float expectedValue, float delta) {
        return new WasmCaseData((Value result, String output) -> Assert.assertFloatEquals("Failure: result:", expectedValue, result.as(Float.class), delta));
    }

    public static WasmCaseData expectedDouble(double expectedValue, double delta) {
        return new WasmCaseData((Value result, String output) -> Assert.assertDoubleEquals("Failure: result:", expectedValue, result.as(Double.class), delta));
    }

    public static WasmCaseData expectedThrows(String expectedErrorMessage) {
        return new WasmCaseData(expectedErrorMessage);
    }

    public static Collection<WasmCase> collectFileCases(String type, String resource) throws IOException {
        Collection<WasmCase> collectedCases = new ArrayList<>();
        if (resource == null) {
            return collectedCases;
        }

        // Open the wasm_test_index file of the bundle. The wasm_test_index file contains the
        // available cases for that bundle.
        InputStream index = WasmCase.class.getResourceAsStream(String.format("/%s/%s/wasm_test_index", type, resource));
        BufferedReader indexReader = new BufferedReader(new InputStreamReader(index));

        // Iterate through the available test of the bundle.
        while (indexReader.ready()) {
            String caseName = indexReader.readLine().trim();

            if (caseName.equals("") || caseName.startsWith("#")) {
                // Skip empty lines or lines starting with a hash (treat as a comment).
                continue;
            }
            collectedCases.add(collectFileCase(type, resource, caseName));
        }

        return collectedCases;
    }

    public static WasmCase collectFileCase(String type, String resource, String caseName) throws IOException {
        Object mainContent = WasmResource.getResourceAsTest(String.format("/%s/%s/%s", type, resource, caseName), true);
        String resultContent = WasmResource.getResourceAsString(String.format("/%s/%s/%s.result", type, resource, caseName), true);
        String initContent = WasmResource.getResourceAsString(String.format("/%s/%s/%s.init", type, resource, caseName), false);
        String optsContent = WasmResource.getResourceAsString(String.format("/%s/%s/%s.opts", type, resource, caseName), false);
        WasmInitialization initializer = WasmInitialization.create(initContent);
        Properties options = SystemProperties.createFromOptions(optsContent);

        String[] resultTypeValue = resultContent.split("\\s+", 2);
        String resultType = resultTypeValue[0];
        String resultValue = resultTypeValue[1];

        WasmCaseData caseData = null;
        switch (resultType) {
            case "stdout":
                caseData = WasmCase.expectedStdout(resultValue);
                break;
            case "int":
                caseData = WasmCase.expected(Integer.parseInt(resultValue.trim()));
                break;
            case "long":
                caseData = WasmCase.expected(Long.parseLong(resultValue.trim()));
                break;
            case "float":
                caseData = WasmCase.expected(Float.parseFloat(resultValue.trim()));
                break;
            case "double":
                caseData = WasmCase.expected(Double.parseDouble(resultValue.trim()));
                break;
            case "exception":
                caseData = WasmCase.expectedThrows(resultValue);
                break;
            default:
                Assert.fail(String.format("Unknown type in result specification: %s", resultType));
        }

        if (mainContent instanceof String) {
            return WasmCase.create(caseName, caseData, (String) mainContent, initializer, options);
        } else if (mainContent instanceof byte[]) {
            return WasmCase.create(caseName, caseData, (byte[]) mainContent, initializer, options);
        } else {
            Assert.fail("Unknown content type: " + mainContent.getClass());
        }

        return null;
    }

    public static void validateResult(BiConsumer<Value, String> validator, Value result, OutputStream capturedStdout) {
        if (validator != null) {
            validator.accept(result, capturedStdout.toString());
        } else {
            Assert.fail("Test was not expected to return a value.");
        }
    }
}
