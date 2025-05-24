/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

import jdk.graal.compiler.core.common.type.IntegerStamp;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.calc.AddNode;
import jdk.graal.compiler.nodes.memory.ReadNode;
import jdk.graal.compiler.test.AddModules;
import jdk.graal.compiler.vector.architecture.VectorArchitecture;
import jdk.graal.compiler.vector.architecture.VectorLoweringProvider;
import jdk.graal.compiler.vector.nodes.simd.SimdBroadcastNode;
import jdk.graal.compiler.vector.nodes.simd.SimdStamp;
import jdk.incubator.vector.IntVector;
import jdk.vm.ci.meta.JavaKind;

/** Basic testing for intrinsification of Vector API operations. */
@AddModules("jdk.incubator.vector")
public class VectorAPISmokeTest extends GraalCompilerTest {

    public static int[] testSnippet(int[] ints, int x, int[] output) {
        IntVector.fromArray(IntVector.SPECIES_PREFERRED, ints, 0).add(x).intoArray(output, 0);
        return output;
    }

    @Override
    protected void checkLowTierGraph(StructuredGraph graph) {
        /* Expect a SIMD load, a SIMD broadcast, and a SIMD add. */
        int simdLoads = graph.getNodes().filter(ReadNode.class).filter(read -> ((ReadNode) read).stamp(NodeView.DEFAULT) instanceof SimdStamp).count();
        Assert.assertEquals("simd loads", 1, simdLoads);
        int simdBroadcasts = graph.getNodes().filter(SimdBroadcastNode.class).count();
        Assert.assertEquals("simd broadcasts", 1, simdBroadcasts);
        int simdAdds = graph.getNodes().filter(AddNode.class).filter(add -> ((AddNode) add).stamp(NodeView.DEFAULT) instanceof SimdStamp).count();
        Assert.assertEquals("simd adds", 1, simdAdds);
    }

    @Test
    public void runTest() {
        int vectorLength = IntVector.SPECIES_PREFERRED.length();
        VectorArchitecture vectorArch = ((VectorLoweringProvider) getProviders().getLowerer()).getVectorArchitecture();
        Assume.assumeTrue("test needs minimal vectorization support",
                        vectorArch.getSupportedVectorArithmeticLength(StampFactory.forKind(JavaKind.Int), vectorLength, IntegerStamp.OPS.getAdd()) == vectorLength);

        int[] ints = new int[vectorLength];
        for (int i = 0; i < ints.length; i++) {
            ints[i] = i;
        }
        ArgSupplier output = () -> new int[ints.length];
        test("testSnippet", ints, 7, output);
    }
}
