/*
 * Copyright (c) 2013, 2025, Oracle and/or its affiliates. All rights reserved.
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

import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.DebugDumpScope;
import jdk.graal.compiler.nodes.ParameterNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.StructuredGraph.AllowAssumptions;
import jdk.graal.compiler.nodes.memory.FloatingReadNode;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.phases.common.CanonicalizerPhase;
import jdk.graal.compiler.phases.common.FloatingReadPhase;
import jdk.graal.compiler.phases.common.HighTierLoweringPhase;
import org.junit.Assert;
import org.junit.Test;

/* consider
 *     B b = (B) a;
 *     return b.x10;
 *
 * With snippets a typecheck is performed and if it was successful, a PiNode is created.
 * For the read node, however, there is only a dependency to the PiNode, but not to the
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
        DebugContext debug = getDebugContext();
        try (DebugContext.Scope _ = debug.scope("ReadAfterCheckCastTest", new DebugDumpScope(snippet))) {
            // check shape of graph, with lots of assumptions. will probably fail if graph
            // structure changes significantly
            StructuredGraph graph = parseEager(snippet, AllowAssumptions.YES);
            CoreProviders context = getProviders();
            CanonicalizerPhase canonicalizer = createCanonicalizerPhase();
            new HighTierLoweringPhase(canonicalizer).apply(graph, context);
            new FloatingReadPhase(canonicalizer).apply(graph, context);
            canonicalizer.apply(graph, context);

            debug.dump(DebugContext.BASIC_LEVEL, graph, "After lowering");

            for (FloatingReadNode node : graph.getNodes(ParameterNode.TYPE).first().usages().filter(FloatingReadNode.class)) {
                // Checking that the parameter a is not directly used for the access to field
                // x10 (because x10 must be guarded by the checkcast).
                Assert.assertTrue(node.getLocationIdentity().isImmutable());
            }
        } catch (Throwable e) {
            throw debug.handle(e);
        }
    }
}
