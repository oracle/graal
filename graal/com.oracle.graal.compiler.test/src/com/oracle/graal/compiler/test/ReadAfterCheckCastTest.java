/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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

import org.junit.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.debug.Debug.Scope;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.phases.common.*;
import com.oracle.graal.phases.tiers.*;

/* consider
 *     B b = (B) a;
 *     return b.x10;
 *
 * With snippets a typecheck is performed and if it was successful, a UnsafeCastNode is created.
 * For the read node, however, there is only a dependency to the UnsafeCastNode, but not to the
 * typecheck itself. With special crafting, it's possible to get the scheduler moving the
 * FloatingReadNode before the typecheck. Assuming the object is of the wrong type (here for
 * example A), an invalid field read is done.
 *
 * In order to avoid this situation, an anchor node is introduced in CheckCastSnippts.
 */

public class ReadAfterCheckCastTest extends GraphScheduleTest {

    public static long foo = 0;

    public static class A {

        public long x1;
    }

    public static class B extends A {

        public long x10;
    }

    public static long test1Snippet(A a) {
        if (foo > 4) {
            B b = (B) a;
            b.x10 += 1;
            return b.x10;
        } else {
            B b = (B) a;
            b.x10 += 1;
            return b.x10;
        }
    }

    @Test
    public void test1() {
        test("test1Snippet");
    }

    private void test(final String snippet) {
        try (Scope s = Debug.scope("ReadAfterCheckCastTest", new DebugDumpScope(snippet))) {
            // check shape of graph, with lots of assumptions. will probably fail if graph
            // structure changes significantly
            StructuredGraph graph = parse(snippet);
            PhaseContext context = new PhaseContext(getProviders(), new Assumptions(false));
            CanonicalizerPhase canonicalizer = new CanonicalizerPhase(true);
            new LoweringPhase(canonicalizer, LoweringTool.StandardLoweringStage.HIGH_TIER).apply(graph, context);
            new FloatingReadPhase().apply(graph);
            new OptimizeGuardAnchorsPhase().apply(graph);
            canonicalizer.apply(graph, context);

            Debug.dump(graph, "After lowering");

            for (FloatingReadNode node : graph.getNodes(ParameterNode.class).first().usages().filter(FloatingReadNode.class)) {
                // Checking that the parameter a is not directly used for the access to field
                // x10 (because x10 must be guarded by the checkcast).
                Assert.assertTrue(node.location().getLocationIdentity() == LocationIdentity.FINAL_LOCATION);
            }
        } catch (Throwable e) {
            throw Debug.handle(e);
        }
    }
}
