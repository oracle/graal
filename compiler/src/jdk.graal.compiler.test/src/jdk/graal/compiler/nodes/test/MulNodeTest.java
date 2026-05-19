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
package jdk.graal.compiler.nodes.test;

import jdk.graal.compiler.core.common.type.IntegerStamp;
import jdk.graal.compiler.core.common.type.StampPair;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ParameterNode;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.LeftShiftNode;
import jdk.graal.compiler.nodes.calc.MulNode;
import jdk.graal.compiler.nodes.calc.SubNode;
import org.junit.Assert;
import org.junit.Test;

public class MulNodeTest {

    @Test
    public void canonicalConstantDecomposesLongMultiplierRoundedAboveIntRange() {
        IntegerStamp stamp = IntegerStamp.create(Long.SIZE);
        ParameterNode x = new ParameterNode(0, StampPair.createSingle(stamp));

        ValueNode result = MulNode.canonical(stamp, x, 0xFFFF_FFF0L, NodeView.DEFAULT);

        Assert.assertTrue(result instanceof SubNode);
        SubNode sub = (SubNode) result;
        assertLeftShift(sub.getX(), x, 32);
        assertLeftShift(sub.getY(), x, 4);
    }

    @Test
    public void canonicalConstantDecomposesLongMultiplierOneBelowPowerOfTwo() {
        IntegerStamp stamp = IntegerStamp.create(Long.SIZE);
        ParameterNode x = new ParameterNode(0, StampPair.createSingle(stamp));

        ValueNode result = MulNode.canonical(stamp, x, Long.MAX_VALUE, NodeView.DEFAULT);

        Assert.assertTrue(result instanceof SubNode);
        SubNode sub = (SubNode) result;
        assertLeftShift(sub.getX(), x, 63);
        Assert.assertSame(x, sub.getY());
    }

    private static void assertLeftShift(ValueNode node, ValueNode input, int shiftAmount) {
        Assert.assertTrue(node instanceof LeftShiftNode);
        LeftShiftNode leftShift = (LeftShiftNode) node;
        Assert.assertSame(input, leftShift.getX());
        Assert.assertTrue(leftShift.getY().isJavaConstant());
        Assert.assertEquals(shiftAmount, leftShift.getY().asJavaConstant().asInt());
    }
}
