/*
 * Copyright (c) 2015, 2019, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.core.test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.graalvm.collections.EconomicSet;
import jdk.graal.compiler.debug.DebugOptions;
import jdk.graal.compiler.nodes.EncodedGraph;
import jdk.graal.compiler.nodes.GraphEncoder;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.StructuredGraph.AllowAssumptions;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.options.OptionValues;
import org.junit.Test;

import jdk.vm.ci.meta.ResolvedJavaMethod;

public class GraphEncoderTest extends GraalCompilerTest {

    @Test
    public void test01() {
        testStringMethods(false, getInitialOptions());
    }

    @Test
    public void test02() {
        testStringMethods(true, getInitialOptions());
    }

    @Test
    public void inliningLogAndOptimizationLogDecodedCorrectly() {
        EconomicSet<DebugOptions.OptimizationLogTarget> targets = EconomicSet.create();
        targets.add(DebugOptions.OptimizationLogTarget.Stdout);
        OptionValues optionValues = new OptionValues(getInitialOptions(), DebugOptions.OptimizationLog, targets);
        testStringMethods(true, optionValues);
    }

    public void testStringMethods(boolean canonicalize, OptionValues optionValues) {
        /* Encode and decode all methods of java.lang.String. */
        List<StructuredGraph> originalGraphs = new ArrayList<>();
        for (Method method : String.class.getDeclaredMethods()) {
            ResolvedJavaMethod javaMethod = getMetaAccess().lookupJavaMethod(method);
            if (javaMethod.hasBytecodes()) {
                StructuredGraph originalGraph = parseEager(javaMethod, AllowAssumptions.YES, optionValues);
                if (canonicalize) {
                    CoreProviders context = getProviders();
                    createCanonicalizerPhase().apply(originalGraph, context);
                }
                originalGraphs.add(originalGraph);
            }
        }

        GraphEncoder encoder = new GraphEncoder(getTarget().arch);
        for (StructuredGraph originalGraph : originalGraphs) {
            encoder.prepare(originalGraph);
        }
        encoder.finishPrepare();
        Map<StructuredGraph, Integer> startOffsets = new HashMap<>();
        for (StructuredGraph originalGraph : originalGraphs) {
            startOffsets.put(originalGraph, encoder.encode(originalGraph));
        }

        for (StructuredGraph originalGraph : originalGraphs) {
            EncodedGraph encodedGraph = new EncodedGraph(encoder.getEncoding(), startOffsets.get(originalGraph), encoder.getObjects(), encoder.getNodeClasses(), originalGraph);
            encoder.verifyEncoding(originalGraph, encodedGraph);
        }
    }
}
