/*
 * Copyright (c) 2011, 2020, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Map;

import org.junit.Test;

import jdk.graal.compiler.nodes.Invoke;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.StructuredGraph.AllowAssumptions;
import jdk.graal.compiler.phases.common.DeadCodeEliminationPhase;
import jdk.graal.compiler.phases.tiers.HighTierContext;
import jdk.graal.compiler.util.EconomicHashMap;

public class InvokeExceptionTest extends GraalCompilerTest {

    public static synchronized void throwException(int i) {
        if (i == 1) {
            throw new RuntimeException();
        }
    }

    @Test
    public void test1() {
        // fill the profiling data...
        for (int i = 0; i < 10000; i++) {
            try {
                throwException(i & 1);
                test1Snippet(0);
            } catch (Throwable t) {
                // nothing to do...
            }
        }
        test("test1Snippet");
    }

    @SuppressWarnings("all")
    public static void test1Snippet(int a) {
        throwException(a);
    }

    private void test(String snippet) {
        StructuredGraph graph = parseProfiled(snippet, AllowAssumptions.NO);
        Map<Invoke, Double> hints = new EconomicHashMap<>();
        for (Invoke invoke : graph.getInvokes()) {
            hints.put(invoke, 1000d);
        }
        HighTierContext context = getDefaultHighTierContext();
        createInliningPhase(hints, createCanonicalizerPhase()).apply(graph, context);
        createCanonicalizerPhase().apply(graph, context);
        new DeadCodeEliminationPhase().apply(graph);
    }
}
