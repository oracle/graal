/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.compiler.test.ea;

import org.junit.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.compiler.test.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.debug.Debug.Scope;
import com.oracle.graal.graph.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.java.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.phases.*;
import com.oracle.graal.phases.common.*;
import com.oracle.graal.phases.common.inlining.*;
import com.oracle.graal.phases.tiers.*;

/**
 * Tests {@link AbstractNewObjectNode#simplify(com.oracle.graal.graph.spi.SimplifierTool)}.
 * 
 */
public class PoorMansEATest extends GraalCompilerTest {
    public static class A {
        public A obj;
    }

    public static A test1Snippet() {
        A a = new A();
        a.obj = a;
        return null;
    }

    @Test
    public void test1() {
        test("test1Snippet");
    }

    private void test(final String snippet) {
        try (Scope s = Debug.scope("PoorMansEATest", new DebugDumpScope(snippet))) {
            StructuredGraph graph = parseEager(snippet);
            Assumptions assumptions = new Assumptions(false);
            HighTierContext highTierContext = new HighTierContext(getProviders(), assumptions, null, getDefaultGraphBuilderSuite(), OptimisticOptimizations.ALL);
            new InliningPhase(new CanonicalizerPhase(true)).apply(graph, highTierContext);
            PhaseContext context = new PhaseContext(getProviders(), assumptions);
            new LoweringPhase(new CanonicalizerPhase(true), LoweringTool.StandardLoweringStage.HIGH_TIER).apply(graph, context);

            // remove framestates in order to trigger the simplification.
            cleanup: for (FrameState fs : graph.getNodes(FrameState.class).snapshot()) {
                for (Node input : fs.inputs()) {
                    if (input instanceof NewInstanceNode) {
                        fs.replaceAtUsages(null);
                        fs.safeDelete();
                        continue cleanup;
                    }
                }
            }
            new CanonicalizerPhase(true).apply(graph, context);
        } catch (Throwable e) {
            throw Debug.handle(e);
        }
    }
}
