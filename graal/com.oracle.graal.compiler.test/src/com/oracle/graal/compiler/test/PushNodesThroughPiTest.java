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
import com.oracle.graal.graph.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.phases.common.*;

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
        return ret;
    }

    @Test
    public void test1() {
        test("test1Snippet");
    }

    private void test(final String snippet) {
        Debug.scope("PushThroughPi", new DebugDumpScope(snippet), new Runnable() {

            public void run() {
                StructuredGraph graph = parse(snippet);
                new LoweringPhase(null, runtime(), new Assumptions(false)).apply(graph);
                new CanonicalizerPhase(runtime(), null).apply(graph);
                new PushNodesThroughPi().apply(graph);
                new CanonicalizerPhase(runtime(), null).apply(graph);

                for (Node n : graph.getNodes()) {
                    if (n instanceof ReadNode) {
                        ReadNode rn = (ReadNode) n;
                        Object locId = rn.location().locationIdentity();
                        if (locId instanceof ResolvedJavaField) {
                            ResolvedJavaField field = (ResolvedJavaField) locId;
                            if (field.getName().equals("x")) {
                                Assert.assertTrue(rn.object() instanceof LocalNode);
                            } else {
                                Assert.assertTrue(rn.object() instanceof UnsafeCastNode);
                            }
                        }
                    }
                }
            }
        });
    }
}
