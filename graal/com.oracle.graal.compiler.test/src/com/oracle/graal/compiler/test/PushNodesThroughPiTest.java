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

import junit.framework.Assert;

import org.junit.Test;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.calc.*;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.nodes.spi.Lowerable.LoweringType;
import com.oracle.graal.nodes.type.*;
import com.oracle.graal.phases.common.*;
import com.oracle.graal.phases.tiers.*;

public class PushNodesThroughPiTest extends GraalCompilerTest {

    public static class A {

        public long x = 20;
    }

    public static class B extends A {

        public long y = 10;
    }

    public static long test1Snippet(A a) {
        B b = (B) a;
        long ret = b.x; // this can be moved before the checkcast
        ret += b.y;
        // the null-check should be canonicalized with the null-check of the checkcast
        ret += b != null ? 100 : 200;
        return ret;
    }

    @Test
    public void test1() {
        final String snippet = "test1Snippet";
        Debug.scope("PushThroughPi", new DebugDumpScope(snippet), new Runnable() {

            public void run() {
                StructuredGraph graph = compileTestSnippet(snippet);

                int counter = 0;
                for (ReadNode rn : graph.getNodes().filter(ReadNode.class)) {
                    if (rn.location() instanceof ConstantLocationNode && rn.object().stamp() instanceof ObjectStamp) {
                        long disp = ((ConstantLocationNode) rn.location()).getDisplacement();
                        ResolvedJavaType receiverType = rn.object().objectStamp().type();
                        ResolvedJavaField field = receiverType.findInstanceFieldWithOffset(disp);

                        if (field != null) {
                            if (field.getName().equals("x")) {
                                Assert.assertTrue(rn.object() instanceof LocalNode);
                            } else {
                                Assert.assertTrue(rn.object() instanceof UnsafeCastNode);
                            }
                            counter++;
                        }
                    }
                }
                Assert.assertEquals(2, counter);

                Assert.assertTrue(graph.getNodes().filter(IsNullNode.class).count() == 1);
            }
        });
    }

    private StructuredGraph compileTestSnippet(final String snippet) {
        StructuredGraph graph = parse(snippet);
        HighTierContext context = new HighTierContext(runtime(), new Assumptions(false), replacements);
        CanonicalizerPhase canonicalizer = new CanonicalizerPhase(true);
        new LoweringPhase(LoweringType.BEFORE_GUARDS).apply(graph, context);
        canonicalizer.apply(graph, context);
        new PushThroughPiPhase().apply(graph);
        canonicalizer.apply(graph, context);

        return graph;
    }
}
