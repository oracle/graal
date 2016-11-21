/*
 * Copyright (c) 2016, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.test;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import java.util.stream.Collectors;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.llvm.parser.base.model.blocks.InstructionBlock;
import com.oracle.truffle.llvm.parser.base.model.functions.FunctionDefinition;
import com.oracle.truffle.llvm.parser.base.model.visitors.ModelVisitor;
import com.oracle.truffle.llvm.parser.bc.impl.LLVMBitcodeVisitor;
import com.oracle.truffle.llvm.parser.bc.impl.LLVMLifetimeAnalysis;
import com.oracle.truffle.llvm.runtime.LLVMLogger;
import com.oracle.truffle.llvm.test.options.SulongTestOptions;
import com.oracle.truffle.llvm.test.util.LifetimeFileFormat;
import com.oracle.truffle.llvm.test.util.LifetimeFileParserEventListener;

@RunWith(Parameterized.class)
public class TestLifetimeAnalysisGCC extends TestSuiteBase {

    // TODO in the long term we should replace this
    private static final String[] excludedFiles = new String[]{
                    "projects/com.oracle.truffle.llvm.test/suites/gcc/gcc-5.2.0/gcc/testsuite/gcc.dg/pr43419.c",
                    "projects/com.oracle.truffle.llvm.test/suites/gcc/gcc-5.2.0/gcc/testsuite/gfortran.fortran-torture/execute/integer_select.f90"
    };

    private TestCaseFiles tuple;
    private File referenceOutputFile;
    private final LifetimeFileFormat.Writer fileWriter;
    private BufferedReader referenceFileReader;
    private Map<String, LLVMLifetimeAnalysis> referenceResults;

    public TestLifetimeAnalysisGCC(TestCaseFiles tuple) throws IOException {
        this.tuple = tuple;
        setUpReferenceFilePath(tuple);
        if (SulongTestOptions.TEST.generateLifetimeReferenceOutput()) {
            referenceOutputFile.getParentFile().mkdirs();
            fileWriter = new LifetimeFileFormat.Writer(new PrintStream(referenceOutputFile));
        } else {
            fileWriter = null;
            FileInputStream fis = new FileInputStream(referenceOutputFile);
            referenceFileReader = new BufferedReader(new InputStreamReader(fis));
            referenceResults = parseReferenceResults();
        }
    }

    private void setUpReferenceFilePath(TestCaseFiles tuple) {
        String referenceFileRelative = tuple.getOriginalFile().getAbsolutePath().substring(LLVMPaths.GCC_TEST_SUITE.getAbsolutePath().length() + 1) + ".lifetime";
        referenceOutputFile = new File(LLVMPaths.LIFETIME_ANALYSIS_REFERENCE_FILES, referenceFileRelative);
    }

    @Parameterized.Parameters
    public static List<TestCaseFiles[]> getTestFiles() throws IOException {
        File configFile = LLVMPaths.GCC_TEST_SUITE_CONFIG;
        File testSuite = LLVMPaths.GCC_TEST_SUITE;
        List<TestCaseFiles[]> files = getTestCasesFromConfigFile(configFile, testSuite, new TestCaseGeneratorImpl(false, true), true);

        final List<TestCaseFiles[]> filteredFiles = new ArrayList<>();
        for (int i = 0; i < files.size(); i++) {
            TestCaseFiles[] testCaseFilesArray = files.get(i);
            int index = 0;
            while (index < testCaseFilesArray.length) {
                if (isExcluded(testCaseFilesArray[index])) {
                    final TestCaseFiles[] newTestCaseFiles = new TestCaseFiles[testCaseFilesArray.length - 1];
                    int targetIndex = 0;
                    for (int j = 0; j < testCaseFilesArray.length; j++) {
                        if (index != j) {
                            newTestCaseFiles[targetIndex++] = testCaseFilesArray[j];
                        }
                    }
                    testCaseFilesArray = newTestCaseFiles;
                } else {
                    index++;
                }
            }
            if (testCaseFilesArray.length != 0) {
                filteredFiles.add(testCaseFilesArray);
            }
        }
        return filteredFiles;
    }

    private static boolean isExcluded(TestCaseFiles testCase) {
        for (String excludedFilename : excludedFiles) {
            if (testCase.getOriginalFile().getAbsolutePath().endsWith(excludedFilename)) {
                return true;
            }
        }
        return false;
    }

    @Test
    public void test() throws Throwable {
        try {
            LLVMLogger.info("original file: " + tuple.getOriginalFile());

            final LLVMBitcodeVisitor.BitcodeParserResult parserResult = LLVMBitcodeVisitor.BitcodeParserResult.getFromSource(Source.newBuilder(tuple.getBitCodeFile().getAbsoluteFile()).build());
            parserResult.getModel().accept(new ModelVisitor() {
                @Override
                public void ifVisitNotOverwritten(Object obj) {
                }

                @Override
                public void visit(FunctionDefinition method) {
                    final String functionName = method.getName();
                    final LLVMLifetimeAnalysis lifetimes = LLVMLifetimeAnalysis.getResult(method, parserResult.getStackAllocation().getFrame(functionName),
                                    parserResult.getPhis().getPhiMap(functionName));

                    if (SulongTestOptions.TEST.generateLifetimeReferenceOutput()) {
                        fileWriter.writeFunctionName(functionName);
                        fileWriter.writeBeginDead();
                        writeInstructionBlockVariables(lifetimes.getNullableBefore());
                        fileWriter.writeEndDead();
                        writeInstructionBlockVariables(lifetimes.getNullableAfter());

                    } else {
                        LLVMLifetimeAnalysis expected = referenceResults.get(functionName);
                        assertResultsEqual(functionName, expected, lifetimes);
                    }

                }

                private void writeInstructionBlockVariables(Map<InstructionBlock, FrameSlot[]> beginDead) {
                    for (InstructionBlock b : beginDead.keySet()) {
                        fileWriter.writeBasicBlockIndent(b.getName());
                        for (FrameSlot slot : beginDead.get(b)) {
                            if (slot != null) {
                                fileWriter.writeVariableIndent(slot.getIdentifier());
                            }
                        }
                    }
                }
            });

        } catch (Throwable e) {
            recordError(tuple, e);
            throw e;
        }
    }

    private void assertResultsEqual(String functionName, LLVMLifetimeAnalysis expected, LLVMLifetimeAnalysis actual) {
        if (expected == null) {
            LLVMLogger.unconditionalInfo(String.format("No reference result for test %s", tuple.getBitCodeFile().getAbsolutePath()));
            return;
        }
        final Map<String, Set<String>> expectedBeginDead = getCommonFromInstructionBlocks(expected.getNullableBefore());
        final Map<String, Set<String>> actualBeginDead = getCommonFromInstructionBlocks(actual.getNullableBefore());
        assertMapsEqual(functionName, expectedBeginDead, actualBeginDead);

        final Map<String, Set<String>> expectedEndDead = getCommonFromInstructionBlocks(expected.getNullableAfter());
        final Map<String, Set<String>> actualEndDead = getCommonFromInstructionBlocks(actual.getNullableAfter());
        assertMapsEqual(functionName, expectedEndDead, actualEndDead);
    }

    private static Map<String, Set<String>> getCommonFromInstructionBlocks(Map<InstructionBlock, FrameSlot[]> original) {
        final Map<String, Set<String>> commonMap = new HashMap<>(original.size());
        for (Map.Entry<InstructionBlock, FrameSlot[]> entry : original.entrySet()) {
            final String name = getQuotedName(entry.getKey().getName());
            final Set<String> slots = getQuotedNames(entry.getValue());
            commonMap.put(name, slots);
        }
        return commonMap;
    }

    private static Set<String> getQuotedNames(FrameSlot[] slots) {
        return Arrays.stream(slots).map(frameSlot -> getQuotedName((String) frameSlot.getIdentifier())).collect(Collectors.toSet());
    }

    private static String getQuotedName(String originalName) {
        // make sure all names are quoted to avoid naming differences between parsers
        if (originalName.charAt(1) == '"') {
            return originalName;
        } else {
            return String.format("%%\"%s\"", originalName.substring(1));
        }
    }

    private void assertMapsEqual(String functionName, Map<String, Set<String>> expected, Map<String, Set<String>> actual) {
        if (expected.size() != actual.size()) {
            throw new AssertionError(buildErrorMessage(functionName, "Different Map Sizes!"));
        }

        for (final String name : expected.keySet()) {
            if (!actual.containsKey(name)) {
                throw new AssertionError(buildErrorMessage(functionName, String.format("Cannot find block %s in %s", name, asString(actual.keySet()))));
            }

            final Set<String> expectedFrameSlots = expected.get(name);
            final Set<String> actualFrameSlots = actual.get(name);
            if (!setsEqual(expectedFrameSlots, actualFrameSlots)) {
                throw new AssertionError(buildErrorMessage(functionName, String.format("Nullers do not match: should be %s, but are %s", asString(expectedFrameSlots), asString(actualFrameSlots))));
            }
        }
    }

    private static boolean setsEqual(Set<String> expected, Set<String> actual) {
        for (String expectedString : expected) {
            if (!actual.contains(expectedString)) {
                return false;
            }
        }
        return expected.size() == actual.size();
    }

    private static String asString(Set<String> names) {
        final StringJoiner joiner = new StringJoiner(", ", "[", "]");
        for (final String name : names) {
            joiner.add(name);
        }
        return joiner.toString();
    }

    private String buildErrorMessage(String functionName, String message) {
        return String.format("Error in Function %s in File %s: %s", functionName, tuple.getBitCodeFile().getAbsolutePath(), message);
    }

    private static InstructionBlock createInstructionBlock(String name) {
        InstructionBlock namedBlock = new InstructionBlock(null, 0);
        namedBlock.setName(name);
        return namedBlock;
    }

    private Map<String, LLVMLifetimeAnalysis> parseReferenceResults() throws IOException {
        Map<String, LLVMLifetimeAnalysis> results = new HashMap<>();

        LifetimeFileFormat.parse(referenceFileReader, new LifetimeFileParserEventListener() {
            private FrameDescriptor descr = new FrameDescriptor();
            private Map<InstructionBlock, FrameSlot[]> beginDead = new HashMap<>();
            private Map<InstructionBlock, FrameSlot[]> endDead = new HashMap<>();
            Set<FrameSlot> slots = new HashSet<>();

            private boolean deadAtBeginning = true;

            @Override
            public void startFile() {
            }

            @Override
            public void endFile(String functionName) {
                results.put(functionName, result(beginDead, endDead));
            }

            @Override
            public void variableIndent(String variableName) {
                slots.add(descr.findOrAddFrameSlot(variableName));
            }

            @Override
            public void functionIndent(String functionName) {
                results.put(functionName, result(beginDead, endDead));
                beginDead = new HashMap<>();
                endDead = new HashMap<>();
            }

            @Override
            public void finishEntry(String block) {
                String entryName = block.substring(1); // Remove '%' at beginning of name
                if (deadAtBeginning) {
                    beginDead.put(createInstructionBlock(entryName), slots.toArray(new FrameSlot[slots.size()]));
                } else {
                    endDead.put(createInstructionBlock(entryName), slots.toArray(new FrameSlot[slots.size()]));
                }
                slots.clear();
            }

            @Override
            public void endDead() {
                deadAtBeginning = false;
            }

            @Override
            public void beginDead() {
                deadAtBeginning = true;
            }
        });

        return results;
    }

    private static LLVMLifetimeAnalysis result(Map<InstructionBlock, FrameSlot[]> beginDead, Map<InstructionBlock, FrameSlot[]> endDead) {
        return new LLVMLifetimeAnalysis(beginDead, endDead);
    }

}
