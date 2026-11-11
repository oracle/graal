/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

import jdk.graal.compiler.core.common.Stride;
import jdk.graal.compiler.core.common.StrideUtil;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.lir.StandardOp.CallOp;
import jdk.graal.compiler.lir.amd64.AMD64ArrayRegionCompareToOp;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.ReturnNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.spi.NodeLIRBuilderTool;
import jdk.graal.compiler.replacements.nodes.ArrayRegionCompareToNode;
import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.meta.JavaKind;
import org.graalvm.word.LocationIdentity;

public class ArrayRegionCompareToEmptyTest extends MatchRuleTest {

    @NodeInfo
    public static final class DirectCompareNode extends ArrayRegionCompareToNode {
        public static final NodeClass<DirectCompareNode> TYPE = NodeClass.create(DirectCompareNode.class);

        public DirectCompareNode(ValueNode arrayA, ValueNode offsetA, ValueNode arrayB, ValueNode offsetB, ValueNode length, ValueNode dynamicStrides, Stride strideA, Stride strideB) {
            super(TYPE, arrayA, offsetA, arrayB, offsetB, length, dynamicStrides, strideA, strideB, null, LocationIdentity.any());
        }

        @Override
        public void generate(NodeLIRBuilderTool gen) {
            emitIntrinsic(prepareArguments(gen));
        }
    }

    public static int seed(byte[] arrayA, byte[] arrayB, int length, int dynamicStrides) {
        return arrayA.length + arrayB.length + length + dynamicStrides;
    }

    private InstalledCode compileCompare(Stride strideA, Stride strideB, boolean dynamic, boolean directEmission) {
        StructuredGraph graph = parseEager("seed", StructuredGraph.AllowAssumptions.NO);
        ValueNode offset = ConstantNode.forLong(getMetaAccess().getArrayBaseOffset(JavaKind.Byte), graph);
        ArrayRegionCompareToNode compare;
        if (directEmission) {
            compare = new DirectCompareNode(graph.getParameter(0), offset, graph.getParameter(1), offset, graph.getParameter(2),
                            dynamic ? graph.getParameter(3) : null, dynamic ? null : strideA, dynamic ? null : strideB);
        } else if (dynamic) {
            compare = new ArrayRegionCompareToNode(graph.getParameter(0), offset, graph.getParameter(1), offset, graph.getParameter(2), graph.getParameter(3), LocationIdentity.any());
        } else {
            compare = new ArrayRegionCompareToNode(graph.getParameter(0), offset, graph.getParameter(1), offset, graph.getParameter(2), strideA, strideB, LocationIdentity.any());
        }
        graph.add(compare);
        ReturnNode ret = graph.getNodes(ReturnNode.TYPE).first();
        graph.addBeforeFixed(ret, compare);
        ret.replaceFirstInput(ret.result(), compare);
        InstalledCode code = getCode(getResolvedJavaMethod("seed"), graph, true);
        boolean direct = false;
        boolean callsStub = false;
        for (var block : getLIR().getControlFlowGraph().getBlocks()) {
            for (var instruction : getLIR().getLIRforBlock(block)) {
                direct |= instruction instanceof AMD64ArrayRegionCompareToOp;
                callsStub |= instruction instanceof CallOp;
            }
        }
        Assert.assertEquals("The requested AMD64 emission path must be used", directEmission, direct);
        Assert.assertEquals("The requested stub path must be used", !directEmission, callsStub);
        return code;
    }

    @Test
    public void testEmptyAndNonEmptyRegions() throws Exception {
        Assume.assumeTrue(getTarget().arch instanceof AMD64);
        for (boolean directEmission : new boolean[]{true, false}) {
            for (boolean dynamic : new boolean[]{false, true}) {
                for (Stride[] strides : new Stride[][]{{Stride.S1, Stride.S1}, {Stride.S2, Stride.S1}, {Stride.S2, Stride.S2},
                                {Stride.S4, Stride.S1}, {Stride.S4, Stride.S2}, {Stride.S4, Stride.S4}}) {
                    int dispatch = StrideUtil.getDirectStubCallIndex(strides[0], strides[1]);
                    InstalledCode code = compileCompare(strides[0], strides[1], dynamic, directEmission);
                    try {
                        byte[] first = {7, 0, 0, 0};
                        byte[] second = {3, 0, 0, 0};
                        Assert.assertEquals("Empty regions must ignore differing backing arrays", 0, code.executeVarargs(first, second, 0, dispatch));
                        Assert.assertEquals(0, code.executeVarargs(first, first, 0, dispatch));
                        Assert.assertEquals(0, code.executeVarargs(new byte[0], new byte[0], 0, dispatch));
                        Assert.assertEquals(4, code.executeVarargs(first, second, 1, dispatch));
                        Assert.assertEquals(0, code.executeVarargs(first, first, 1, dispatch));
                        byte[] lastA = new byte[256];
                        byte[] lastB = new byte[256];
                        lastA[63 * strides[0].value] = 7;
                        lastB[63 * strides[1].value] = 3;
                        Assert.assertEquals(4, code.executeVarargs(lastA, lastB, 64, dispatch));
                        lastB[63 * strides[1].value] = 7;
                        Assert.assertEquals(0, code.executeVarargs(lastA, lastB, 64, dispatch));
                    } finally {
                        code.invalidate();
                    }
                }
            }
        }
    }
}
