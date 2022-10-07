/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.StreamSupport;

import org.graalvm.collections.EconomicMap;
import org.graalvm.profdiff.core.CompilationUnit;
import org.graalvm.profdiff.core.Experiment;
import org.graalvm.profdiff.core.ExperimentId;
import org.graalvm.profdiff.core.VerbosityLevel;
import org.graalvm.profdiff.core.inlining.InliningTreeNode;
import org.graalvm.profdiff.core.optimization.Optimization;
import org.graalvm.profdiff.core.optimization.OptimizationPhase;
import org.graalvm.profdiff.parser.experiment.ExperimentFiles;
import org.graalvm.profdiff.parser.experiment.ExperimentParser;
import org.graalvm.profdiff.util.StdoutWriter;
import org.graalvm.profdiff.util.Writer;
import org.junit.Test;

public class ExperimentParserTest {
    private static class ExperimentResources implements ExperimentFiles {
        private static final String RESOURCE_DIR = "org/graalvm/profdiff/test/resources/";

        private File getFileForResource(String name) {
            try {
                try (InputStream resourceAsStream = getClass().getClassLoader().getResourceAsStream(name)) {
                    assert resourceAsStream != null;
                    Path path = Files.createTempFile(null, null);
                    Files.write(path, resourceAsStream.readAllBytes());
                    return path.toFile();
                }
            } catch (IOException exception) {
                throw new RuntimeException(exception.getMessage());
            }
        }

        @Override
        public ExperimentId getExperimentId() {
            return ExperimentId.ONE;
        }

        @Override
        public Optional<File> getProftoolOutput() {
            return Optional.of(getFileForResource(RESOURCE_DIR + "profile.json"));
        }

        @Override
        public File[] getOptimizationLogs() {
            return new File[]{getFileForResource(RESOURCE_DIR + "optimization-log.txt")};
        }

        @Override
        public Experiment.CompilationKind getCompilationKind() {
            return Experiment.CompilationKind.JIT;
        }
    }

    @Test
    public void testExperimentParser() throws Exception {
        ExperimentFiles experimentFiles = new ExperimentResources();
        Writer writer = new StdoutWriter(VerbosityLevel.DEFAULT);
        ExperimentParser experimentParser = new ExperimentParser(experimentFiles, writer);
        Experiment experiment = experimentParser.parse();
        assertEquals("16102", experiment.getExecutionId());
        assertEquals(2, StreamSupport.stream(experiment.getCompilationUnits().spliterator(), false).count());
        assertEquals(263869257616L, experiment.getTotalPeriod());
        assertEquals(264224374L + 158328120602L, experiment.getGraalPeriod());

        for (CompilationUnit compilationUnit : experiment.getCompilationUnits()) {
            CompilationUnit.TreePair trees = compilationUnit.loadTrees();
            switch (compilationUnit.getCompilationId()) {
                case "1": {
                    assertEquals("foo.bar.Foo$Bar.methodName()",
                                    compilationUnit.getMethod().getMethodName());

                    InliningTreeNode inliningTreeRoot = new InliningTreeNode(compilationUnit.getMethod().getMethodName(), -1, true, null);
                    inliningTreeRoot.addChild(new InliningTreeNode("java.lang.String.equals(Object)", 44, false, List.of("not inlined")));
                    assertEquals(inliningTreeRoot, trees.getInliningTree().getRoot());
                    OptimizationPhase rootPhase = new OptimizationPhase("RootPhase");
                    OptimizationPhase someTier = new OptimizationPhase("SomeTier");
                    rootPhase.addChild(someTier);
                    someTier.addChild(new Optimization("LoopTransformation",
                                    "PartialUnroll",
                                    EconomicMap.of("foo.bar.Foo$Bar.innerMethod()", 30, "foo.bar.Foo$Bar.methodName()", 68),
                                    EconomicMap.of("unrollFactor", 1)));
                    someTier.addChild(new OptimizationPhase("EmptyPhase"));
                    assertEquals(rootPhase, trees.getOptimizationTree().getRoot());
                    break;
                }
                case "2": {
                    assertEquals("org.example.myMethod(org.example.Foo, org.example.Class$Context)",
                                    compilationUnit.getMethod().getMethodName());
                    OptimizationPhase rootPhase = new OptimizationPhase("RootPhase");
                    rootPhase.addChild(new Optimization(
                                    "LoopTransformation",
                                    "PartialUnroll",
                                    EconomicMap.of("org.example.myMethod(org.example.Foo, org.example.Class$Context)", 2),
                                    EconomicMap.of("unrollFactor", 1)));
                    rootPhase.addChild(new Optimization(
                                    "LoopTransformation",
                                    "PartialUnroll",
                                    null,
                                    EconomicMap.of("unrollFactor", 2)));
                    assertEquals(rootPhase, trees.getOptimizationTree().getRoot());
                    assertNull(trees.getInliningTree().getRoot());
                    break;
                }
                default:
                    fail();
                    break;
            }
        }
    }
}
