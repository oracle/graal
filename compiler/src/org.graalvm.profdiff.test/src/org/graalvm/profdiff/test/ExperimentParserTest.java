/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package org.graalvm.profdiff.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.stream.StreamSupport;

import org.graalvm.collections.EconomicMap;
import org.graalvm.profdiff.core.CompilationUnit;
import org.graalvm.profdiff.core.Experiment;
import org.graalvm.profdiff.core.ExperimentId;
import org.graalvm.profdiff.core.Method;
import org.graalvm.profdiff.core.OptionValues;
import org.graalvm.profdiff.core.inlining.InliningTreeNode;
import org.graalvm.profdiff.core.inlining.ReceiverTypeProfile;
import org.graalvm.profdiff.core.optimization.Optimization;
import org.graalvm.profdiff.core.optimization.OptimizationPhase;
import org.graalvm.profdiff.core.optimization.Position;
import org.graalvm.profdiff.parser.ExperimentFiles;
import org.graalvm.profdiff.parser.ExperimentParser;
import org.graalvm.profdiff.parser.ExperimentParserError;
import org.graalvm.profdiff.parser.FileView;
import org.graalvm.profdiff.core.Writer;
import org.junit.Test;

public class ExperimentParserTest {

    private static final String COMPILATION_UNIT_MOCK_1 = """
                    {
                        "methodName": "foo.bar.Foo$Bar.methodName()",
                        "compilationId": "1",
                        "inliningTree": {
                            "methodName": "foo.bar.Foo$Bar.methodName()",
                            "callsiteBci": -1,
                            "inlined": true,
                            "indirect": false,
                            "alive": false,
                            "reason": null,
                            "invokes": [
                                {
                                    "methodName": "java.lang.String.equals(Object)",
                                    "callsiteBci": 44,
                                    "inlined": false,
                                    "indirect": false,
                                    "alive": true,
                                    "reason": [
                                        "not inlined"
                                    ]
                                }
                            ]
                        },
                        "optimizationTree": {
                            "phaseName": "RootPhase",
                            "optimizations": [
                                {
                                    "phaseName": "SomeTier",
                                    "optimizations": [
                                        {
                                            "optimizationName": "LoopTransformation",
                                            "eventName": "PartialUnroll",
                                            "position": {
                                                "foo.bar.Foo$Bar.innerMethod()": 30,
                                                "foo.bar.Foo$Bar.methodName()": 68
                                            },
                                            "unrollFactor": 1
                                        },
                                        {
                                            "phaseName": "EmptyPhase",
                                            "optimizations": null
                                        }
                                    ]
                                }
                            ]
                        }
                    }
                    """;

    private static final String COMPILATION_UNIT_MOCK_2 = """
                    {
                        "methodName": "Klass.someMethod()",
                        "compilationId": "2",
                        "inliningTree": {
                            "methodName": "Klass.someMethod()",
                            "callsiteBci": -1,
                            "inlined": true,
                            "indirect": false,
                            "alive": false,
                            "reason": null,
                            "invokes": [
                                {
                                    "methodName": "Klass.abstractMethod()",
                                    "callsiteBci": 1,
                                    "inlined": false,
                                    "indirect": true,
                                    "alive": true,
                                    "reason": null,
                                    "receiverTypeProfile": {
                                        "mature": true,
                                        "profiledTypes": [
                                            {
                                                "typeName": "KlassImpl",
                                                "probability": 1.0,
                                                "concreteMethodName": "KlassImpl.abstractMethod()"
                                            }
                                        ]
                                    }
                                }
                            ]
                        },
                        "optimizationTree": {
                            "phaseName": "RootPhase",
                            "optimizations": [
                                {
                                    "optimizationName": "LoopTransformation",
                                    "eventName": "PartialUnroll",
                                    "position": {
                                        "Klass.someMethod()": 2
                                    },
                                    "unrollFactor": 1
                                },
                                {
                                    "optimizationName": "LoopTransformation",
                                    "eventName": "PartialUnroll",
                                    "position": null,
                                    "unrollFactor": 2
                                }
                            ]
                        }
                    }
                    """;

    private static final String OPTIMIZATION_LOG_MOCK = COMPILATION_UNIT_MOCK_1.replace("\n", "") + "\n" + COMPILATION_UNIT_MOCK_2.replace("\n", "");

    private static final String PROFILE_MOCK = """
                    {
                        "executionId": "16102",
                        "totalPeriod": 263869257616,
                        "code": [
                            {
                                "compileId": null,
                                "name": "stub",
                                "level": null,
                                "period": 155671948
                            },
                            {
                                "compileId": "1",
                                "name": "1: foo.bar.Foo$Bar.methodName()",
                                "level": 4,
                                "period": 264224374
                            },
                            {
                                "compileId": "2%",
                                "name": "2: org.example.myMethod(org.example.Foo, org.example.Class$Context)",
                                "level": 4,
                                "period": 158328120602
                            }
                        ]
                    }
                    """;

    /**
     * Mocks a file view backed by a string instead of a file.
     *
     * @param path a symbolic path
     * @param source the content of the file
     * @return a file view backed by a string
     */
    private static FileView fileViewFromString(String path, String source) {
        return new FileView() {
            @Override
            public String getSymbolicPath() {
                return path;
            }

            @Override
            public void forEachLine(BiConsumer<String, FileView> consumer) {
                source.lines().forEach(line -> consumer.accept(line, fileViewFromString(path, line)));
            }

            @Override
            public String readFully() {
                return source;
            }
        };
    }

    /**
     * Experiment files mocked using a string.
     */
    private static final class ExperimentString implements ExperimentFiles {
        private final String optimizationLogString;

        private final String profileString;

        private ExperimentString(String optimizationLogString, String profileString) {
            this.optimizationLogString = optimizationLogString;
            this.profileString = profileString;
        }

        @Override
        public ExperimentId getExperimentId() {
            return ExperimentId.ONE;
        }

        @Override
        public Optional<FileView> getProftoolOutput() {
            if (profileString == null) {
                return Optional.empty();
            }
            return Optional.of(fileViewFromString("<string>", profileString));
        }

        @Override
        public Iterable<FileView> getOptimizationLogs() {
            return List.of(fileViewFromString("<string>", optimizationLogString));
        }

        @Override
        public Experiment.CompilationKind getCompilationKind() {
            return Experiment.CompilationKind.JIT;
        }
    }

    @Test
    public void testExperimentParser() throws Exception {
        ExperimentFiles experimentFiles = new ExperimentString(OPTIMIZATION_LOG_MOCK, PROFILE_MOCK);
        ExperimentParser experimentParser = new ExperimentParser(experimentFiles, Writer.standardOutput(new OptionValues()));
        Experiment experiment = experimentParser.parse();
        assertEquals("16102", experiment.getExecutionId());
        assertEquals(2, StreamSupport.stream(experiment.getCompilationUnits().spliterator(), false).count());
        assertEquals(263869257616L, experiment.getTotalPeriod());
        assertEquals(264224374L + 158328120602L, experiment.getGraalPeriod());

        for (CompilationUnit compilationUnit : experiment.getCompilationUnits()) {
            CompilationUnit.TreePair trees = compilationUnit.loadTrees();
            switch (compilationUnit.getCompilationId()) {
                case "1" -> {
                    assertEquals("foo.bar.Foo$Bar.methodName()",
                                    compilationUnit.getMethod().getMethodName());
                    InliningTreeNode inliningTreeRoot = new InliningTreeNode(compilationUnit.getMethod().getMethodName(), -1, true, null, false, null, false);
                    inliningTreeRoot.addChild(new InliningTreeNode("java.lang.String.equals(Object)", 44, false, List.of("not inlined"), false, null, true));
                    assertEquals(inliningTreeRoot, trees.getInliningTree().getRoot());
                    OptimizationPhase rootPhase = new OptimizationPhase("RootPhase");
                    OptimizationPhase someTier = new OptimizationPhase("SomeTier");
                    rootPhase.addChild(someTier);
                    someTier.addChild(new Optimization("LoopTransformation",
                                    "PartialUnroll",
                                    Position.of("foo.bar.Foo$Bar.innerMethod()", 30, "foo.bar.Foo$Bar.methodName()", 68),
                                    EconomicMap.of("unrollFactor", 1)));
                    someTier.addChild(new OptimizationPhase("EmptyPhase"));
                    assertEquals(rootPhase, trees.getOptimizationTree().getRoot());
                }
                case "2" -> {
                    assertEquals("Klass.someMethod()",
                                    compilationUnit.getMethod().getMethodName());
                    InliningTreeNode inliningTreeRoot = new InliningTreeNode(compilationUnit.getMethod().getMethodName(), -1, true, null, false, null, false);
                    ReceiverTypeProfile receiverTypeProfile = new ReceiverTypeProfile(true, List.of(new ReceiverTypeProfile.ProfiledType("KlassImpl", 1.0, "KlassImpl.abstractMethod()")));
                    inliningTreeRoot.addChild(new InliningTreeNode("Klass.abstractMethod()", 1, false, null, true, receiverTypeProfile, true));
                    assertEquals(inliningTreeRoot, trees.getInliningTree().getRoot());
                    OptimizationPhase rootPhase = new OptimizationPhase("RootPhase");
                    rootPhase.addChild(new Optimization(
                                    "LoopTransformation",
                                    "PartialUnroll",
                                    Position.of("Klass.someMethod()", 2),
                                    EconomicMap.of("unrollFactor", 1)));
                    rootPhase.addChild(new Optimization(
                                    "LoopTransformation",
                                    "PartialUnroll",
                                    null,
                                    EconomicMap.of("unrollFactor", 2)));
                    assertEquals(rootPhase, trees.getOptimizationTree().getRoot());
                }
                default -> fail();
            }
        }
    }

    /**
     * Verifies that multi-method keys are separated from multi-method names.
     *
     * The optimization logs from Graal contain method names with multi-method keys. In profdiff, we
     * want to be able to compare two compilation units even if they come from a different
     * multi-method. For this reason the parser removes multi-method keys from positions and
     * inlining-tree nodes.
     */
    @Test
    public void multiMethodKeys() throws ExperimentParserError, IOException {
        String compilationUnitJSON = """
                        {
                            "methodName": "foo.Bar%%MultiMethodKey(Baz)",
                            "compilationId": "100",
                            "inliningTree": {
                                "methodName": "foo.Bar%%MultiMethodKey(Baz)",
                                "callsiteBci": -1,
                                "inlined": false,
                                "indirect": false,
                                "alive": false,
                                "reason": null,
                                "invokes": []
                            },
                            "optimizationTree": {
                                "phaseName": "RootPhase",
                                "optimizations": [
                                    {
                                        "optimizationName": "DeadCodeElimination",
                                        "eventName": "NodeRemoval",
                                        "position": {"foo.Bar%%MultiMethodKey(Baz)": 10}
                                    }
                                ]
                            }
                        }""".replace("\n", "");
        Experiment experiment = new ExperimentParser(new ExperimentString(compilationUnitJSON, null), Writer.stringBuilder(new OptionValues())).parse();
        String methodName = "foo.Bar(Baz)";
        Method method = experiment.getMethodsByName().get(methodName);
        assertNotNull(method);
        CompilationUnit compilationUnit = method.getCompilationUnits().get(0);
        assertEquals("MultiMethodKey", compilationUnit.getMultiMethodKey());
        CompilationUnit.TreePair treePair = compilationUnit.loadTrees();
        assertEquals(methodName, treePair.getInliningTree().getRoot().getName());
        Optimization optimization = treePair.getOptimizationTree().getRoot().getOptimizationsRecursive().get(0);
        assertEquals(methodName, optimization.getPosition().enclosingMethodPath().get(0).methodName());
    }

    @Test(expected = ExperimentParserError.class)
    public void compilationKindMismatch() throws ExperimentParserError, IOException {
        ExperimentFiles files = new ExperimentString("", """
                        {
                            "compilationKind": "AOT",
                            "totalPeriod": 0,
                            "code": []
                        }
                        """);
        new ExperimentParser(files, Writer.stringBuilder(new OptionValues())).parse();
    }

    @Test(expected = ExperimentParserError.class)
    public void invalidCompilationKind() throws ExperimentParserError, IOException {
        ExperimentFiles files = new ExperimentString("", """
                        {
                            "compilationKind": "INVALID",
                            "totalPeriod": 0,
                            "code": []
                        }
                        """);
        new ExperimentParser(files, Writer.stringBuilder(new OptionValues())).parse();
    }

    @Test
    public void missingCompilationUnit() throws ExperimentParserError, IOException {
        ExperimentFiles files = new ExperimentString("", """
                        {
                            "compilationKind": "JIT",
                            "totalPeriod": 100,
                            "code": [
                                {
                                    "compileId": "1000",
                                    "name": "1000: foo()",
                                    "level": 4,
                                    "period": 100
                                }
                            ]
                        }
                        """);
        var writer = Writer.stringBuilder(new OptionValues());
        new ExperimentParser(files, writer).parse();
        assertTrue(writer.getOutput().contains("not found"));
    }

    @Test
    public void invalidCompilationUnit() throws ExperimentParserError, IOException {
        ExperimentFiles files = new ExperimentString("{}", null);
        var writer = Writer.stringBuilder(new OptionValues());
        new ExperimentParser(files, writer).parse();
        assertTrue(writer.getOutput().contains("Invalid compilation unit"));
    }

    @Test
    public void invalidCompilationUnitJSON() throws ExperimentParserError, IOException {
        ExperimentFiles files = new ExperimentString("{", null);
        var writer = Writer.stringBuilder(new OptionValues());
        new ExperimentParser(files, writer).parse();
        assertTrue(writer.getOutput().contains("Invalid compilation unit"));
    }

    @Test(expected = ExperimentParserError.class)
    public void invalidProfileStringJSON() throws ExperimentParserError, IOException {
        ExperimentFiles files = new ExperimentString("", "{");
        new ExperimentParser(files, Writer.stringBuilder(new OptionValues())).parse();
    }
}
