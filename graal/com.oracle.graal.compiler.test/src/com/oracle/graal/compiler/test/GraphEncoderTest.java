/*
 * Copyright (c) 2015, 2015, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.graal.compiler.test;

import java.lang.reflect.*;
import java.util.*;

import jdk.internal.jvmci.meta.*;

import org.junit.*;

import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.StructuredGraph.AllowAssumptions;
import com.oracle.graal.phases.common.*;
import com.oracle.graal.phases.tiers.*;

public class GraphEncoderTest extends GraalCompilerTest {

    @Test
    public void test01() {
        testStringMethods(false);
    }

    @Test
    public void test02() {
        testStringMethods(true);
    }

    public void testStringMethods(boolean canonicalize) {
        /* Encode and decode all methods of java.lang.String. */
        List<StructuredGraph> originalGraphs = new ArrayList<>();
        for (Method method : String.class.getDeclaredMethods()) {
            ResolvedJavaMethod javaMethod = getMetaAccess().lookupJavaMethod(method);
            if (javaMethod.hasBytecodes()) {
                StructuredGraph originalGraph = parseEager(javaMethod, AllowAssumptions.YES);
                if (canonicalize) {
                    PhaseContext context = new PhaseContext(getProviders());
                    new CanonicalizerPhase().apply(originalGraph, context);
                }
                originalGraphs.add(originalGraph);
            }
        }

        GraphEncoder encoder = new GraphEncoder(getTarget().arch);
        for (StructuredGraph originalGraph : originalGraphs) {
            encoder.prepare(originalGraph);
        }
        encoder.finishPrepare();
        Map<StructuredGraph, Long> startOffsets = new HashMap<>();
        for (StructuredGraph originalGraph : originalGraphs) {
            startOffsets.put(originalGraph, encoder.encode(originalGraph));
        }

        for (StructuredGraph originalGraph : originalGraphs) {
            EncodedGraph encodedGraph = new EncodedGraph(encoder.getEncoding(), startOffsets.get(originalGraph), encoder.getObjects(), encoder.getNodeClasses(), originalGraph.getAssumptions(),
                            originalGraph.getInlinedMethods());
            GraphEncoder.verifyEncoding(originalGraph, encodedGraph, getTarget().arch);
        }
    }
}
