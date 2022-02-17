/*
 * Copyright (c) 2020, 2022, Oracle and/or its affiliates. All rights reserved.
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

package org.graalvm.compiler.truffle.test;

import org.graalvm.compiler.core.test.GraalCompilerTest;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.StructuredGraph.AllowAssumptions;
import org.graalvm.compiler.nodes.calc.NarrowNode;
import org.graalvm.compiler.nodes.calc.ReinterpretNode;
import org.graalvm.compiler.nodes.calc.SignExtendNode;
import org.graalvm.compiler.nodes.calc.ZeroExtendNode;
import org.graalvm.compiler.nodes.spi.CoreProviders;
import org.graalvm.compiler.truffle.compiler.phases.PhiTransformPhase;
import org.graalvm.compiler.virtual.phases.ea.PartialEscapePhase;
import org.junit.Assert;
import org.junit.Test;

public class PhiTransformTest extends GraalCompilerTest {

    public static float narrowPhiLoopSnippet(int count) {
        long[] values = new long[2];

        do {
            values[0] = ((int) values[0] + 1) & 0xffffffffL;
            values[1] = Float.floatToRawIntBits(Float.intBitsToFloat((int) values[1]) + 1) & 0xffffffffL;
        } while ((int) values[0] < count);
        return Float.intBitsToFloat((int) values[1]);
    }

    @Test
    public void narrowLoopPhiTest() {
        StructuredGraph graph = parseEager("narrowPhiLoopSnippet", AllowAssumptions.YES);

        CoreProviders context = getProviders();
        createCanonicalizerPhase().apply(graph, context);
        new PartialEscapePhase(false, createCanonicalizerPhase(), getInitialOptions()).apply(graph, getDefaultHighTierContext());
        new PhiTransformPhase(createCanonicalizerPhase()).apply(graph, getDefaultHighTierContext());
        Assert.assertEquals(0, graph.getNodes().filter(ReinterpretNode.class).count());
        Assert.assertEquals(0, graph.getNodes().filter(NarrowNode.class).count());
        // one ZeroExtendNode remains for the loop proxy
        Assert.assertEquals(1, graph.getNodes().filter(ZeroExtendNode.class).count());
        Assert.assertEquals(0, graph.getNodes().filter(SignExtendNode.class).count());

        test("narrowPhiLoopSnippet", 100);
    }
}
