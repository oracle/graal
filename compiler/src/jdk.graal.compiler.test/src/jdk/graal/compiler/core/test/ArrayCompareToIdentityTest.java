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

import java.lang.reflect.Proxy;

import org.junit.Assert;
import org.junit.Test;

import jdk.graal.compiler.core.common.Stride;
import jdk.graal.compiler.nodes.ComputeObjectAddressNode;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.ReturnNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.StructuredGraph.AllowAssumptions;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.spi.CanonicalizerTool;
import jdk.graal.compiler.nodes.spi.VirtualizerTool;
import jdk.graal.compiler.replacements.nodes.ArrayCompareToNode;
import jdk.vm.ci.meta.JavaKind;

public class ArrayCompareToIdentityTest extends GraalCompilerTest {
    public static int seed(byte[] array) {
        return array.length;
    }

    private ArrayCompareToNode comparison(int lengthA, int lengthB, Stride strideA, Stride strideB, boolean sameAddress) {
        StructuredGraph graph = parseEager("seed", AllowAssumptions.NO);
        ValueNode array = graph.getParameter(0);
        ConstantNode offset = ConstantNode.forLong(getMetaAccess().getArrayBaseOffset(JavaKind.Byte), graph);
        ComputeObjectAddressNode first = graph.add(new ComputeObjectAddressNode(array, offset));
        graph.addAfterFixed(graph.start(), first);
        ComputeObjectAddressNode second = first;
        if (!sameAddress) {
            second = graph.add(new ComputeObjectAddressNode(array, offset));
            graph.addAfterFixed(first, second);
        }
        ArrayCompareToNode compare = graph.add(new ArrayCompareToNode(first, ConstantNode.forInt(lengthA, graph), second, ConstantNode.forInt(lengthB, graph), strideA, strideB));
        graph.addAfterFixed(second, compare);
        ReturnNode ret = graph.getNodes(ReturnNode.TYPE).first();
        ret.replaceFirstInput(ret.result(), compare);
        Assert.assertTrue(graph.verify());
        return compare;
    }

    private static void checkCanonical(ArrayCompareToNode compare, boolean folds) {
        CanonicalizerTool tool = (CanonicalizerTool) Proxy.newProxyInstance(CanonicalizerTool.class.getClassLoader(), new Class<?>[]{CanonicalizerTool.class}, (proxy, method, args) -> {
            if (method.getName().equals("allUsagesAvailable")) {
                return true;
            }
            throw new AssertionError("Unexpected canonicalizer service: " + method);
        });
        ValueNode result = (ValueNode) compare.canonical(tool);
        if (folds) {
            Assert.assertTrue(result.isJavaConstant());
            Assert.assertEquals(0, result.asJavaConstant().asInt());
        } else {
            Assert.assertSame(compare, result);
        }
    }

    private static void checkVirtualize(ArrayCompareToNode compare, boolean folds) {
        ValueNode[] replacement = new ValueNode[1];
        VirtualizerTool tool = (VirtualizerTool) Proxy.newProxyInstance(VirtualizerTool.class.getClassLoader(), new Class<?>[]{VirtualizerTool.class}, (proxy, method, args) -> {
            switch (method.getName()) {
                case "getAlias":
                    return args[0];
                case "replaceWithValue":
                    replacement[0] = (ValueNode) args[0];
                    return null;
                default:
                    throw new AssertionError("Unexpected virtualizer service: " + method);
            }
        });
        compare.virtualize(tool);
        if (folds) {
            Assert.assertNotNull(replacement[0]);
            Assert.assertEquals(0, replacement[0].asJavaConstant().asInt());
        } else {
            Assert.assertNull(replacement[0]);
        }
    }

    @Test
    public void testIdentity() {
        for (Stride strideA : new Stride[]{Stride.S1, Stride.S2}) {
            for (Stride strideB : new Stride[]{Stride.S1, Stride.S2}) {
                for (int lengthB : new int[]{1, 2, 3}) {
                    for (boolean sameAddress : new boolean[]{false, true}) {
                        boolean folds = sameAddress && lengthB == 2 && strideA == strideB;
                        checkCanonical(comparison(2, lengthB, strideA, strideB, sameAddress), folds);
                        checkVirtualize(comparison(2, lengthB, strideA, strideB, sameAddress), folds);
                    }
                }
            }
        }
    }
}
