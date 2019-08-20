/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates.
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

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.oracle.truffle.llvm.tests.options.TestOptions;
import org.graalvm.polyglot.Context;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public final class LLVMDebugTest extends LLVMDebugTestBase {

    private static final Path BC_DIR_PATH = Paths.get(TestOptions.TEST_SUITE_PATH, "debug");
    private static final Path SRC_DIR_PATH = Paths.get(TestOptions.PROJECT_ROOT, "..", "tests", "com.oracle.truffle.llvm.tests.debug.native", "debug");
    private static final Path TRACE_DIR_PATH = Paths.get(TestOptions.PROJECT_ROOT, "..", "tests", "com.oracle.truffle.llvm.tests.debug.native", "trace");

    private static final String OPTION_ENABLE_LVI = "llvm.enableLVI";

    private static final String BC_O0 = "O0.bc";
    private static final String BC_O1 = "O1.bc";
    private static final String BC_MEM2REG = "O0_MEM2REG.bc";

    public LLVMDebugTest(String testName, String configuration) {
        super(testName, configuration);
    }

    @Parameters(name = "{0}_{1}")
    public static Collection<Object[]> getConfigurations() {
        final Map<String, String[]> configs = new HashMap<>();
        configs.put("testPrimitives", new String[]{BC_O0, BC_MEM2REG});
        configs.put("testStructures", new String[]{BC_O0, BC_MEM2REG, BC_O1});
        configs.put("testUnions", new String[]{BC_O0, BC_MEM2REG, BC_O1});
        configs.put("testDecorators", new String[]{BC_O0, BC_MEM2REG, BC_O1});
        configs.put("testClasses", new String[]{BC_O0, BC_MEM2REG, BC_O1});
        configs.put("testScopes", new String[]{BC_O0, BC_MEM2REG, BC_O1});
        configs.put("testControlFlow", new String[]{BC_O0, BC_MEM2REG});
        configs.put("testReenterArgsAndVals", new String[]{BC_O0, BC_MEM2REG, BC_O1});
        configs.put("testFunctionPointer", new String[]{BC_O0, BC_MEM2REG, BC_O1});
        configs.put("testObjectPointer", new String[]{BC_O0, BC_MEM2REG});
        configs.put("testLongDouble", new String[]{BC_O0, BC_MEM2REG});
        configs.put("testBooleans", new String[]{BC_O0, BC_MEM2REG, BC_O1});
        configs.put("testBitFields", new String[]{BC_O0, BC_MEM2REG});
        return configs.entrySet().stream().flatMap(e -> Stream.of(e.getValue()).map(v -> new Object[]{e.getKey(), v})).collect(Collectors.toSet());
    }

    @Override
    void setContextOptions(Context.Builder contextBuilder) {
        contextBuilder.option(OPTION_ENABLE_LVI, String.valueOf(true));
    }

    @Override
    Path getBitcodePath() {
        return BC_DIR_PATH;
    }

    @Override
    Path getSourcePath() {
        return SRC_DIR_PATH;
    }

    @Override
    Path getTracePath() {
        return TRACE_DIR_PATH;
    }
}
