/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.core.amd64.test;

import static org.junit.Assume.assumeTrue;

import jdk.graal.compiler.core.common.Stride;
import jdk.graal.compiler.core.amd64.AMD64AddressLowering;
import jdk.graal.compiler.core.amd64.AMD64AddressNode;
import jdk.graal.compiler.core.test.GraalCompilerTest;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.AddNode;
import jdk.graal.compiler.nodes.calc.LeftShiftNode;
import jdk.graal.compiler.nodes.calc.NegateNode;
import jdk.graal.compiler.nodes.memory.address.AddressNode;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import jdk.vm.ci.amd64.AMD64;

public class AMD64AddressLoweringTest extends GraalCompilerTest {

    private StructuredGraph graph;
    private AMD64AddressLowering lowering;

    @Before
    public void checkAMD64() {
        assumeTrue("skipping AMD64 specific test", getTarget().arch instanceof AMD64);
        graph = new StructuredGraph.Builder(getInitialOptions(), getDebugContext()).build();
        lowering = new AMD64AddressLowering();
    }

    @Test
    public void convertBaseAndIndexToDisplacement() {
        ValueNode base = graph.unique(const64(1000));
        ValueNode index = graph.unique(const64(10));
        AddressNode result = lowering.lower(base, index);
        assertAddress(result, null, null, Stride.S1, 1010);
    }

    @Test
    public void convertBaseToDisplacement() {
        ValueNode constantAddress = graph.addOrUniqueWithInputs(const64(1000));
        AddressNode result = lowering.lower(constantAddress, null);
        assertAddress(result, null, null, Stride.S1, 1000);
    }

    @Test
    public void convertBaseAndShiftedIndexToDisplacement() {
        ValueNode base = graph.addOrUniqueWithInputs(const64(1000));
        ValueNode index = graph.addOrUniqueWithInputs(new LeftShiftNode(const64(10), const32(1)));
        AddressNode result = lowering.lower(base, index);
        assertAddress(result, null, null, Stride.S2, 1020);
    }

    @Test
    public void convertBaseAndNegatedShiftedIndexToDisplacement() {
        ValueNode base = graph.addOrUniqueWithInputs(const64(1000));
        ValueNode index = graph.addOrUniqueWithInputs(new NegateNode(new LeftShiftNode(const64(10), const32(2))));
        AddressNode result = lowering.lower(base, index);
        assertAddress(result, null, null, Stride.S4, 960);
    }

    @Test
    public void convertNegatedBaseAndNegatedShiftedIndexToDisplacement() {
        ValueNode base = graph.addOrUniqueWithInputs(new NegateNode(const64(1000)));
        ValueNode index = graph.addOrUniqueWithInputs(new NegateNode(new LeftShiftNode(const64(10), const32(2))));
        AddressNode result = lowering.lower(base, index);
        assertAddress(result, null, null, Stride.S4, -1040);
    }

    @Test
    public void convertNegatedShiftedBaseAndNegatedIndexToDisplacement() {
        ValueNode base = graph.addOrUniqueWithInputs(new NegateNode(new LeftShiftNode(const64(10), const32(2))));
        ValueNode index = graph.addOrUniqueWithInputs(new NegateNode(const64(1000)));
        AddressNode result = lowering.lower(base, index);
        assertAddress(result, null, null, Stride.S4, -1040);
    }

    @Test
    public void convertTwoLevelsOfNegatedShiftedBaseAndNegatedIndexToDisplacement() {
        ValueNode base = graph.addOrUniqueWithInputs(new NegateNode(new LeftShiftNode(new NegateNode(new LeftShiftNode(const64(500), const32(1))), const32(1))));
        ValueNode index = graph.addOrUniqueWithInputs(new NegateNode(new AddNode(new NegateNode(const64(13)), const64(3))));
        AddressNode result = lowering.lower(base, index);
        assertAddress(result, null, null, Stride.S4, 2010);
    }

    private static ConstantNode const64(long value) {
        return ConstantNode.forIntegerBits(Long.SIZE, value);
    }

    private static ConstantNode const32(long value) {
        return ConstantNode.forIntegerBits(Integer.SIZE, value);
    }

    private static void assertAddress(AddressNode actual, ValueNode expectedBase, ValueNode expectedIndex, Stride expectedStride, int expectedDisplacement) {
        AMD64AddressNode address = (AMD64AddressNode) actual;
        Assert.assertEquals(expectedBase, address.getBase());
        Assert.assertEquals(expectedIndex, address.getIndex());
        Assert.assertEquals(expectedStride, address.getScale());
        Assert.assertEquals(expectedDisplacement, address.getDisplacement());
    }
}
