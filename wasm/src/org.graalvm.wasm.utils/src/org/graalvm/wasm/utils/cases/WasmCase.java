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
package org.graalvm.wasm.utils.cases;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.function.BiConsumer;

import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.io.ByteSequence;
import org.graalvm.wasm.WasmLanguage;
import org.graalvm.wasm.utils.SystemProperties;
import org.graalvm.wasm.utils.WasmBinaryTools;
import org.graalvm.wasm.utils.WasmResource;
import org.junit.Assert;

/**
 * Instances of this class are used for WebAssembly test/benchmark cases.
 */
public abstract class WasmCase {
    private final String name;
    private final WasmCaseData data;
    private final Properties options;

    public WasmCase(String name, WasmCaseData data, Properties options) {
        this.name = name;
        this.data = data;
        this.options = options;
    }

    public String name() {
        return name;
    }

    public WasmCaseData data() {
        return data;
    }

    public Properties options() {
        return options;
    }

    public ArrayList<Source> getSources(EnumSet<WasmBinaryTools.WabtOption> wabtOptions) throws IOException, InterruptedException {
        ArrayList<Source> sources = new ArrayList<>();
        for (Map.Entry<String, byte[]> entry : createBinaries(wabtOptions).entrySet()) {
            Source.Builder sourceBuilder = Source.newBuilder(WasmLanguage.ID, ByteSequence.create(entry.getValue()), entry.getKey());
            sourceBuilder.cached(false);
            Source source = sourceBuilder.build();
            sources.add(source);
        }
        return sources;
    }

    public abstract Map<String, byte[]> createBinaries(EnumSet<WasmBinaryTools.WabtOption> wabtOptions) throws IOException, InterruptedException;

    public static WasmStringCase create(String name, WasmCaseData data, String program) {
        return new WasmStringCase(name, data, program, new Properties());
    }

    public static WasmStringCase create(String name, WasmCaseData data, String program, Properties options) {
        return new WasmStringCase(name, data, program, options);
    }

    public static WasmBinaryCase create(String name, WasmCaseData data, byte[] binary, Properties options) {
        return new WasmBinaryCase(name, data, binary, options);
    }

    public static WasmCaseData expectedStdout(String expectedOutput) {
        return new WasmCaseData((Value result, String output) -> {
            Assert.assertEquals("Failure: stdout:", expectedOutput, output);

            // When an output is expected, we also check that the main function returns is 0 if it
            // returns a number.
            if (result != null && result.isNumber()) {
                Assert.assertEquals("Failure: exit code:", 0, result.asInt());
            }
        });
    }

    public static WasmCaseData expected(Object expectedValue) {
        return new WasmCaseData((Value result, String output) -> Assert.assertEquals("Failure: result:", expectedValue, result.as(Object.class)));
    }

    public static WasmCaseData expectedFloat(float expectedValue, float delta) {
        return new WasmCaseData((Value result, String output) -> Assert.assertEquals("Failure: result:", expectedValue, result.as(Float.class), delta));
    }

    public static WasmCaseData expectedDouble(double expectedValue, double delta) {
        return new WasmCaseData((Value result, String output) -> Assert.assertEquals("Failure: result:", expectedValue, result.as(Double.class), delta));
    }

    public static WasmCaseData expectedThrows(String expectedErrorMessage, WasmCaseData.ErrorType phase) {
        return new WasmCaseData(expectedErrorMessage.trim(), phase);
    }

    public static WasmCaseData expectedMultiValue(Object[] expectedValues) {
        return new WasmCaseData((Value result, String output) -> {
            for (int i = 0; i < expectedValues.length; i++) {
                Assert.assertEquals("Failure: result: ", expectedValues[i], result.getArrayElement(i).as(Object.class));
            }
        });
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
            String caseSpec = indexReader.readLine().trim();

            if (caseSpec.equals("") || caseSpec.startsWith("#")) {
                // Skip empty lines or lines starting with a hash (treat as a comment).
                continue;
            } else {
                collectedCases.add(collectFileCase(type, resource, caseSpec));
            }
        }

        return collectedCases;
    }

    public static WasmCase collectFileCase(String type, String resource, String caseSpec) throws IOException {
        Map<String, Object> mainContents = new LinkedHashMap<>();
        String caseName;
        if (caseSpec.contains("/")) {
            // Collect multi-module test case.
            final String[] dirFiles = caseSpec.split("/");
            final String dir = dirFiles[0];
            final String[] moduleFiles = dirFiles[1].split(";");
            for (String file : moduleFiles) {
                mainContents.put(file, WasmResource.getResourceAsTest(String.format("/%s/%s/%s/%s", type, resource, dir, file), true));
            }
            caseName = dir;
        } else {
            mainContents.put(caseSpec, WasmResource.getResourceAsTest(String.format("/%s/%s/%s", type, resource, caseSpec), true));
            caseName = caseSpec;
        }
        String resultContent = WasmResource.getResourceAsString(String.format("/%s/%s/%s.result", type, resource, caseName), true);
        String optsContent = WasmResource.getResourceAsString(String.format("/%s/%s/%s.opts", type, resource, caseName), false);
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
            case "multi-value":
                String[] values = resultValue.split("\\s+");
                Object[] expectedValues = new Object[values.length / 2];
                for (int i = 0; i < values.length; i += 2) {
                    switch (values[i]) {
                        case "int":
                            expectedValues[i / 2] = Integer.parseInt(values[i + 1]);
                            break;
                        case "long":
                            expectedValues[i / 2] = Long.parseLong(values[i + 1]);
                            break;
                        case "float":
                            expectedValues[i / 2] = Float.parseFloat(values[i + 1]);
                            break;
                        case "double":
                            expectedValues[i / 2] = Double.parseDouble(values[i + 1]);
                            break;
                    }
                }
                caseData = WasmCase.expectedMultiValue(expectedValues);
                break;
            case "validation":
                caseData = WasmCase.expectedThrows(resultValue, WasmCaseData.ErrorType.Validation);
                break;
            case "exception":
                caseData = WasmCase.expectedThrows(resultValue, WasmCaseData.ErrorType.Runtime);
                break;
            default:
                throw new RuntimeException(String.format("Unknown type in result specification: %s", resultType));
        }

        if (mainContents.size() == 1) {
            Object content = mainContents.values().iterator().next();
            if (content instanceof String) {
                return WasmCase.create(caseName, caseData, (String) content, options);
            } else if (content instanceof byte[]) {
                return WasmCase.create(caseName, caseData, (byte[]) content, options);
            } else {
                throw new RuntimeException("Unknown content type: " + content.getClass());
            }
        } else if (mainContents.size() > 1) {
            return new WasmMultiCase(caseName, caseData, mainContents, options);
        }

        return null;
    }

    public static WasmCase loadBenchmarkCase(String resource) throws IOException {
        final String name = SystemProperties.BENCHMARK_NAME;

        Assert.assertNotNull("Please select a benchmark by setting -D" + SystemProperties.BENCHMARK_NAME_PROPERTY_NAME, name);
        Assert.assertFalse("Benchmark name must not be empty", name.trim().isEmpty());

        final WasmCase result = WasmCase.collectFileCase("bench", resource, name);
        Assert.assertNotNull(String.format("Benchmark %s.%s not found", name, name), result);

        return result;
    }

    public static void validateResult(BiConsumer<Value, String> validator, Value result, ByteArrayOutputStream capturedStdout) {
        if (validator != null) {
            validator.accept(result, capturedStdout.toString(StandardCharsets.UTF_8));
        } else {
            throw new RuntimeException("Test was not expected to return a value.");
        }
    }

    public boolean isSkipped() {
        return options().getProperty("skip-on-windows", "false").equals("true") && System.getProperty("os.arch", "").contains("Windows");
    }
}
