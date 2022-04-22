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
package org.graalvm.bisect.test;

import org.graalvm.bisect.core.ExecutedMethod;
import org.graalvm.bisect.core.Experiment;
import org.graalvm.bisect.core.ExperimentId;
import org.graalvm.bisect.core.optimization.OptimizationImpl;
import org.graalvm.bisect.parser.experiment.ExperimentFiles;
import org.graalvm.bisect.parser.experiment.ExperimentParser;
import org.graalvm.bisect.parser.experiment.ExperimentParserException;
import org.junit.Test;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class ExperimentParserTest {
    private static class ExperimentFilesMock implements ExperimentFiles {
        @Override
        public ExperimentId getExperimentId() {
            return ExperimentId.ONE;
        }

        @Override
        public Reader getProftoolOutput() {
            return new StringReader("{\n" +
                    "    \"executionId\": \"16102\",\n" +
                    "    \"totalPeriod\": 263869257616,\n" +
                    "    \"code\": [\n" +
                    "        {\n" +
                    "            \"compileId\": null,\n" +
                    "            \"name\": \"flush_icache_stub\",\n" +
                    "            \"level\": null,\n" +
                    "            \"period\": 155671948\n" +
                    "        },\n" +
                    "        {\n" +
                    "            \"compileId\": \"2390\",\n" +
                    "            \"name\": \"2390: java.util.HashMap$HashIterator.nextNode()\",\n" +
                    "            \"level\": 4,\n" +
                    "            \"period\": 264224374\n" +
                    "        },\n" +
                    "        {\n" +
                    "            \"compileId\": \"3677\",\n" +
                    "            \"name\": \"3677: org.example.singleByteZero(org.example.Blackhole, org.example.CopyBenchmarkSimple$Context)\",\n" +
                    "            \"level\": 4,\n" +
                    "            \"period\": 158328120602\n" +
                    "        }\n" +
                    "    ]\n" +
                    "}");
        }

        @Override
        public List<Reader> getOptimizationLogs() {
            return List.of(
                    new StringReader("{\n" +
                            "    \"compilationId\": \"2390\",\n" +
                            "    \"executionId\": \"16102\",\n" +
                            "    \"compilationMethodName\": \"java.util.HashMap$HashIterator.nextNode()\",\n" +
                            "    \"resolvedMethodName\": \"nextNode\",\n" +
                            "    \"optimizations\": [\n" +
                            "        {\n" +
                            "            \"optimizationName\": \"LoopTransformation\",\n" +
                            "            \"eventName\": \"PartialUnroll\",\n" +
                            "            \"bci\": 68\n," +
                            "            \"unrollFactor\": 1\n" +
                            "        }\n" +
                            "    ]\n" +
                            "}"),
                    new StringReader("{\n" +
                            "    \"compilationId\": \"3677\",\n" +
                            "    \"executionId\": \"16102\",\n" +
                            "    \"compilationMethodName\": \"org.example.CopyBenchmarkSimple.singleByteZero(Blackhole, CopyBenchmarkSimple$Context)\",\n" +
                            "    \"resolvedMethodName\": \"singleByteZero\",\n" +
                            "    \"optimizations\": [\n" +
                            "        {\n" +
                            "            \"optimizationName\": \"LoopTransformation\",\n" +
                            "            \"eventName\": \"PartialUnroll\",\n" +
                            "            \"bci\": 2\n," +
                            "            \"unrollFactor\": 1\n" +
                            "        },\n" +
                            "        {\n" +
                            "            \"optimizationName\": \"LoopTransformation\",\n" +
                            "            \"eventName\": \"PartialUnroll\",\n" +
                            "            \"bci\": null\n," +
                            "            \"unrollFactor\": 2\n" +
                            "        }\n" +
                            "    ]\n" +
                            "}\n")
            );
        }
    }

    @Test
    public void testExperimentParser() throws ExperimentParserException, IOException {
        ExperimentFiles experimentFiles = new ExperimentFilesMock();
        ExperimentParser experimentParser = new ExperimentParser(experimentFiles);
        Experiment experiment = experimentParser.parse();
        assertEquals("16102", experiment.getExecutionId());
        assertEquals(2, experiment.getExecutedMethods().size());
        assertEquals(263869257616L, experiment.getTotalPeriod());
        assertEquals(264224374L + 158328120602L, experiment.getGraalPeriod());

        for (ExecutedMethod executedMethod : experiment.getExecutedMethods()) {
            switch (executedMethod.getCompilationId()) {
                case "2390":
                    assertEquals(
                            "java.util.HashMap$HashIterator.nextNode()",
                            executedMethod.getCompilationMethodName());
                    assertEquals(
                            Set.of(new OptimizationImpl("LoopTransformation", "PartialUnroll", 68, Map.of("unrollFactor", 1))),
                            Set.copyOf(executedMethod.getOptimizations())
                    );
                    break;
                case "3677":
                    assertEquals(
                            "org.example.CopyBenchmarkSimple.singleByteZero(Blackhole, CopyBenchmarkSimple$Context)",
                            executedMethod.getCompilationMethodName());
                    assertEquals(
                            Set.of(
                                    new OptimizationImpl("LoopTransformation", "PartialUnroll", 2, Map.of("unrollFactor", 1)),
                                    new OptimizationImpl("LoopTransformation", "PartialUnroll", null, Map.of("unrollFactor", 2))
                            ),
                            Set.copyOf(executedMethod.getOptimizations())
                    );
                    break;
                default:
                    fail();
                    break;
            }
        }
    }
}
