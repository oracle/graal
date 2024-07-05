/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.core.test.ea;

import org.junit.Assert;
import org.junit.Test;

import jdk.graal.compiler.core.test.GraalCompilerTest;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.StructuredGraph.AllowAssumptions;
import jdk.graal.compiler.nodes.java.LoadFieldNode;
import jdk.graal.compiler.virtual.phases.ea.ReadEliminationPhase;

public class FinalReadEliminationTest extends GraalCompilerTest {

    static class A {
        final int x;

        A(int x) {
            this.x = x;
        }
    }

    static volatile int accross;

    static int S;

    public static int snippetAccessVolatile1(A a) {
        int load1 = a.x;
        S = accross;
        int load2 = a.x;
        return load1 + load2;
    }

    @Test
    public void testAccrossVolatile01() {
        StructuredGraph g = parseEager(getResolvedJavaMethod("snippetAccessVolatile1"), AllowAssumptions.NO);
        createCanonicalizerPhase().apply(g, getDefaultHighTierContext());
        new ReadEliminationPhase(createCanonicalizerPhase()).apply(g, getDefaultHighTierContext());
        Assert.assertEquals(1, g.getNodes().filter(LoadFieldNode.class).filter(x -> !((LoadFieldNode) x).field().isVolatile()).count());
    }

    public static int snippetAccessVolatile2(A a) {
        int load1 = a.x;
        accross = load1;
        int load2 = a.x;
        return load1 + load2;
    }

    @Test
    public void testAccrossVolatile02() {
        StructuredGraph g = parseEager(getResolvedJavaMethod("snippetAccessVolatile2"), AllowAssumptions.NO);
        createCanonicalizerPhase().apply(g, getDefaultHighTierContext());
        new ReadEliminationPhase(createCanonicalizerPhase()).apply(g, getDefaultHighTierContext());
        Assert.assertEquals(2, g.getNodes().filter(LoadFieldNode.class).filter(x -> !((LoadFieldNode) x).field().isVolatile()).count());
    }
}
