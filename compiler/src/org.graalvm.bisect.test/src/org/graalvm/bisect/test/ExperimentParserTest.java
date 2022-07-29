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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.List;

import org.graalvm.bisect.core.ExecutedMethod;
import org.graalvm.bisect.core.Experiment;
import org.graalvm.bisect.core.ExperimentId;
import org.graalvm.bisect.core.optimization.OptimizationImpl;
import org.graalvm.bisect.core.optimization.OptimizationPhaseImpl;
import org.graalvm.bisect.parser.experiment.ExperimentFiles;
import org.graalvm.bisect.parser.experiment.ExperimentParser;
import org.graalvm.bisect.parser.experiment.ExperimentParserTypeError;
import org.graalvm.bisect.util.EconomicMapUtil;
import org.junit.Test;

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
                            "            \"name\": \"stub\",\n" +
                            "            \"level\": null,\n" +
                            "            \"period\": 155671948\n" +
                            "        },\n" +
                            "        {\n" +
                            "            \"compileId\": \"2390\",\n" +
                            "            \"name\": \"2390: foo.bar.Foo$Bar.methodName()\",\n" +
                            "            \"level\": 4,\n" +
                            "            \"period\": 264224374\n" +
                            "        },\n" +
                            "        {\n" +
                            "            \"compileId\": \"3677%\",\n" +
                            "            \"name\": \"3677: org.example.myMethod(org.example.Foo, org.example.Class$Context)\"\n," +
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
                                            "    \"compilationMethodName\": \"foo.bar.Foo$Bar.methodName()\",\n" +
                                            "    \"rootPhase\": {\n" +
                                            "        \"phaseName\": \"RootPhase\",\n" +
                                            "        \"optimizations\": [\n" +
                                            "           {\n" +
                                            "               \"phaseName\": \"SomeTier\",\n" +
                                            "               \"optimizations\": [\n" +
                                            "                   {\n" +
                                            "                       \"optimizationName\": \"LoopTransformation\",\n" +
                                            "                       \"eventName\": \"PartialUnroll\",\n" +
                                            "                       \"position\": {\"foo.bar.Foo$Bar.innerMethod()\": 30, \"foo.bar.Foo$Bar.methodName()\": 68},\n" +
                                            "                       \"unrollFactor\": 1\n" +
                                            "                   },\n" +
                                            "                   {\n" +
                                            "                       \"phaseName\": \"EmptyPhase\",\n" +
                                            "                       \"optimizations\": null\n" +
                                            "                   }\n" +
                                            "               ]\n" +
                                            "           }\n" +
                                            "       ]\n" +
                                            "   }\n" +
                                            "}"),
                            new StringReader("{\n" +
                                            "    \"compilationId\": \"3677\",\n" +
                                            "    \"compilationMethodName\": \"org.example.myMethod(org.example.Foo, org.example.Class$Context)\",\n" +
                                            "    \"rootPhase\": {\n" +
                                            "        \"phaseName\": \"RootPhase\",\n" +
                                            "        \"optimizations\": [\n" +
                                            "            {\n" +
                                            "                \"optimizationName\": \"LoopTransformation\",\n" +
                                            "                \"eventName\": \"PartialUnroll\",\n" +
                                            "                \"position\": {\"org.example.myMethod(org.example.Foo, org.example.Class$Context)\": 2},\n" +
                                            "                \"unrollFactor\": 1\n" +
                                            "            },\n" +
                                            "            {\n" +
                                            "                \"optimizationName\": \"LoopTransformation\",\n" +
                                            "                \"eventName\": \"PartialUnroll\",\n" +
                                            "                \"position\": null,\n" +
                                            "                \"unrollFactor\": 2\n" +
                                            "            }\n" +
                                            "        ]\n" +
                                            "    }\n" +
                                            "}"));
        }
    }

    @Test
    public void testExperimentParser() throws ExperimentParserTypeError, IOException {
        ExperimentFiles experimentFiles = new ExperimentFilesMock();
        ExperimentParser experimentParser = new ExperimentParser(experimentFiles);
        Experiment experiment = experimentParser.parse();
        assertEquals("16102", experiment.getExecutionId());
        assertEquals(2, experiment.getExecutedMethods().size());
        assertEquals(263869257616L, experiment.getTotalPeriod());
        assertEquals(264224374L + 158328120602L, experiment.getGraalPeriod());

        for (ExecutedMethod executedMethod : experiment.getExecutedMethods()) {
            switch (executedMethod.getCompilationId()) {
                case "2390": {
                    assertEquals("foo.bar.Foo$Bar.methodName()",
                                    executedMethod.getCompilationMethodName());
                    OptimizationPhaseImpl rootPhase = new OptimizationPhaseImpl("RootPhase");
                    OptimizationPhaseImpl someTier = new OptimizationPhaseImpl("SomeTier");
                    rootPhase.addChild(someTier);
                    someTier.addChild(new OptimizationImpl("LoopTransformation",
                                    "PartialUnroll",
                                    EconomicMapUtil.of("foo.bar.Foo$Bar.innerMethod()", 30, "foo.bar.Foo$Bar.methodName()", 68),
                                    EconomicMapUtil.of("unrollFactor", 1)));
                    someTier.addChild(new OptimizationPhaseImpl("EmptyPhase"));
                    assertEquals(rootPhase, executedMethod.getRootPhase());
                    break;
                }
                case "3677": {
                    assertEquals("org.example.myMethod(org.example.Foo, org.example.Class$Context)",
                                    executedMethod.getCompilationMethodName());
                    OptimizationPhaseImpl rootPhase = new OptimizationPhaseImpl("RootPhase");
                    rootPhase.addChild(new OptimizationImpl(
                                    "LoopTransformation",
                                    "PartialUnroll",
                                    EconomicMapUtil.of("org.example.myMethod(org.example.Foo, org.example.Class$Context)", 2),
                                    EconomicMapUtil.of("unrollFactor", 1)));
                    rootPhase.addChild(new OptimizationImpl(
                                    "LoopTransformation",
                                    "PartialUnroll",
                                    null,
                                    EconomicMapUtil.of("unrollFactor", 2)));
                    assertEquals(rootPhase, executedMethod.getRootPhase());
                    break;
                }
                default:
                    fail();
                    break;
            }
        }
    }
}
