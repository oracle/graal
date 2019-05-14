/*
 * Copyright (c) 2018, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.test.debug;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.oracle.truffle.llvm.test.options.TestOptions;
import org.graalvm.polyglot.Context;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.oracle.truffle.llvm.runtime.debug.debugexpr.parser.CocoInputStream;
import com.oracle.truffle.llvm.runtime.debug.debugexpr.parser.Parser;
import com.oracle.truffle.llvm.runtime.debug.debugexpr.parser.Scanner;

@RunWith(Parameterized.class)
public final class LLVMDebugExprTest {

    private static final String OPTION_ENABLE_LVI = "llvm.enableLVI";

    private static final String DIRECTORY = "com.oracle.truffle.llvm.tests.debugexpr";

    private final String testName;
    private final Boolean correctExpr;

// public LLVMDebugExprTest(String testName, String configuration) {
// this.testName = testName;
// this.configuration = configuration;
// }

// @Parameters(name = "{0}_{1}")
// public static Collection<Object[]> getConfigurations() {
// final Map<String, String[]> configs = new HashMap<>();
// configs.put("testPrimitives", new String[]{BC_O0, BC_MEM2REG});
// configs.put("testStructures", new String[]{BC_O0, BC_MEM2REG,
// BC_O1});
// configs.put("testUnions", new String[]{BC_O0, BC_MEM2REG, BC_O1});
// configs.put("testDecorators", new String[]{BC_O0, BC_MEM2REG, BC_O1});
// configs.put("testClasses", new String[]{BC_O0, BC_MEM2REG, BC_O1});
// configs.put("testScopes",
// new String[]{BC_O0, BC_MEM2REG, BC_O1});
// configs.put("testControlFlow", new String[]{BC_O0,
// BC_MEM2REG});
// configs.put("testReenterArgsAndVals", new String[]{BC_O0, BC_MEM2REG, BC_O1});
// configs.put("testFunctionPointer", new String[]{BC_O0, BC_MEM2REG, BC_O1});
// configs.put("testObjectPointer", new String[]{BC_O0, BC_MEM2REG});
// configs.put("testLongDouble", new String[]{BC_O0, BC_MEM2REG});
// configs.put("testBooleans",
// new String[]{BC_O0, BC_MEM2REG, BC_O1});
// configs.put("testBitFields", new String[]{BC_O0,
// BC_MEM2REG});
// return configs.entrySet().stream().flatMap(e -> Stream.of(e.getValue()).map(v -> new
// Object[]{e.getKey(), v})).collect(Collectors.toSet());
// }

    public LLVMDebugExprTest(String testName, Boolean correctExpr) {
        this.testName = testName;
        this.correctExpr = correctExpr;
    }

    @Parameters(name = "{0}_{1}")
    public static Collection<Object[]> getConfigurations() {
        final Map<String, Boolean> fileErrors = new HashMap<>();
        fileErrors.put("CorrectSyntax.txt", true);
        fileErrors.put("IncorrectSyntax.txt", false);
        return fileErrors.entrySet().stream().flatMap(e -> Stream.of(e.getValue()).map(v -> new Object[]{e.getKey(), v})).collect(Collectors.toSet());
    }

    @Test
    public void testSyntaxParsing() throws IOException {
        System.out.println("Testing " + testName);

        Files.lines(Paths.get(TestOptions.PROJECT_ROOT, "..", "tests", DIRECTORY, testName)).filter(s -> !s.startsWith("//")).forEach(s -> {
            CocoInputStream cis = new CocoInputStream(s);
            Scanner scanner = new Scanner(cis);
            Parser parser = new Parser(scanner);
            parser.Parse();
            int errors = parser.GetErrors();
            assertTrue(errors <= 0 == correctExpr);
        });

    }

    /*
     * @Test public void testCorrectSyntaxParsing() throws IOException {
     * System.out.println("Testing " + testName); String file = "CorrectSyntax.txt";
     * Files.lines(Paths.get(DIRECTORY, file)).filter(s -> !s.startsWith("//")).forEach(s -> {
     * CocoInputStream cis = new CocoInputStream(s); Scanner scanner = new Scanner(cis); Parser
     * parser = new Parser(scanner); parser.Parse(); int errors = parser.Errors(); assertEquals(0,
     * errors); }); }
     */

}
