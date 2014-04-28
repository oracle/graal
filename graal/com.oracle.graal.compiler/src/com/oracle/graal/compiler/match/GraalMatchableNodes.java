/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.compiler.match;

import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.calc.*;
import com.oracle.graal.nodes.extended.*;

/**
 * Helper class to describe the matchable nodes in the core Graal IR. These could possibly live in
 * their respective classes but for simplicity in the {@link MatchProcessor} they are grouped here.
 */
@MatchableNode(shortName = "Constant", value = ConstantNode.class, inputs = 0)
@MatchableNode(shortName = "FloatConvert", value = FloatConvertNode.class, inputs = 1, adapter = GraalMatchableNodes.ConvertNodeAdapter.class)
@MatchableNode(shortName = "FloatSub", value = FloatSubNode.class, inputs = 2, adapter = GraalMatchableNodes.BinaryNodeAdapter.class)
@MatchableNode(shortName = "FloatingRead", value = FloatingReadNode.class, inputs = 1, adapter = GraalMatchableNodes.ReadNodeAdapter.class)
@MatchableNode(shortName = "If", value = IfNode.class, inputs = 1, adapter = GraalMatchableNodes.IfNodeAdapter.class)
@MatchableNode(shortName = "IntegerSub", value = IntegerSubNode.class, inputs = 2, adapter = GraalMatchableNodes.BinaryNodeAdapter.class)
@MatchableNode(shortName = "LeftShift", value = LeftShiftNode.class, inputs = 2, adapter = GraalMatchableNodes.BinaryNodeAdapter.class)
@MatchableNode(shortName = "Narrow", value = NarrowNode.class, inputs = 1, adapter = GraalMatchableNodes.ConvertNodeAdapter.class)
@MatchableNode(shortName = "Read", value = ReadNode.class, inputs = 1, adapter = GraalMatchableNodes.ReadNodeAdapter.class)
@MatchableNode(shortName = "Reinterpret", value = ReinterpretNode.class, inputs = 1, adapter = GraalMatchableNodes.ReinterpretNodeAdapter.class)
@MatchableNode(shortName = "SignExtend", value = SignExtendNode.class, inputs = 1, adapter = GraalMatchableNodes.ConvertNodeAdapter.class)
@MatchableNode(shortName = "UnsignedRightShift", value = UnsignedRightShiftNode.class, inputs = 2, adapter = GraalMatchableNodes.BinaryNodeAdapter.class)
@MatchableNode(shortName = "Write", value = WriteNode.class, inputs = 2, adapter = GraalMatchableNodes.WriteNodeAdapter.class)
@MatchableNode(shortName = "ZeroExtend", value = ZeroExtendNode.class, inputs = 1, adapter = GraalMatchableNodes.ConvertNodeAdapter.class)
@MatchableNode(shortName = "And", value = AndNode.class, inputs = 2, adapter = GraalMatchableNodes.BinaryNodeAdapter.class, commutative = true)
@MatchableNode(shortName = "FloatAdd", value = FloatAddNode.class, inputs = 2, adapter = GraalMatchableNodes.BinaryNodeAdapter.class, commutative = true)
@MatchableNode(shortName = "FloatEquals", value = FloatEqualsNode.class, inputs = 2, adapter = GraalMatchableNodes.BinaryOpLogicNodeAdapter.class, commutative = true)
@MatchableNode(shortName = "FloatLessThan", value = FloatLessThanNode.class, inputs = 2, adapter = GraalMatchableNodes.BinaryOpLogicNodeAdapter.class, commutative = true)
@MatchableNode(shortName = "FloatMul", value = FloatMulNode.class, inputs = 2, adapter = GraalMatchableNodes.BinaryNodeAdapter.class, commutative = true)
@MatchableNode(shortName = "IntegerAdd", value = IntegerAddNode.class, inputs = 2, adapter = GraalMatchableNodes.BinaryNodeAdapter.class, commutative = true)
@MatchableNode(shortName = "IntegerBelowThan", value = IntegerBelowThanNode.class, inputs = 2, adapter = GraalMatchableNodes.BinaryOpLogicNodeAdapter.class, commutative = true)
@MatchableNode(shortName = "IntegerEquals", value = IntegerEqualsNode.class, inputs = 2, adapter = GraalMatchableNodes.BinaryOpLogicNodeAdapter.class, commutative = true)
@MatchableNode(shortName = "IntegerLessThan", value = IntegerLessThanNode.class, inputs = 2, adapter = GraalMatchableNodes.BinaryOpLogicNodeAdapter.class, commutative = true)
@MatchableNode(shortName = "IntegerMul", value = IntegerMulNode.class, inputs = 2, adapter = GraalMatchableNodes.BinaryNodeAdapter.class, commutative = true)
@MatchableNode(shortName = "IntegerTest", value = IntegerTestNode.class, inputs = 2, adapter = GraalMatchableNodes.BinaryOpLogicNodeAdapter.class, commutative = true)
@MatchableNode(shortName = "ObjectEquals", value = ObjectEqualsNode.class, inputs = 2, adapter = GraalMatchableNodes.BinaryOpLogicNodeAdapter.class, commutative = true)
@MatchableNode(shortName = "Or", value = OrNode.class, inputs = 2, adapter = GraalMatchableNodes.BinaryNodeAdapter.class, commutative = true)
@MatchableNode(shortName = "Xor", value = XorNode.class, inputs = 2, adapter = GraalMatchableNodes.BinaryNodeAdapter.class, commutative = true)
public class GraalMatchableNodes {
    public static class BinaryNodeAdapter extends MatchNodeAdapter {
        @Override
        protected ValueNode getFirstInput(ValueNode node) {
            return ((BinaryNode) node).x();
        }

        @Override
        protected ValueNode getSecondInput(ValueNode node) {
            return ((BinaryNode) node).y();
        }
    }

    public static class WriteNodeAdapter extends MatchNodeAdapter {

        @Override
        protected ValueNode getFirstInput(ValueNode node) {
            return ((WriteNode) node).object();
        }

        @Override
        protected ValueNode getSecondInput(ValueNode node) {
            return ((WriteNode) node).value();
        }
    }

    public static class ConvertNodeAdapter extends MatchNodeAdapter {

        @Override
        protected ValueNode getFirstInput(ValueNode node) {
            return ((ConvertNode) node).getInput();
        }
    }

    public static class ReinterpretNodeAdapter extends MatchNodeAdapter {

        @Override
        protected ValueNode getFirstInput(ValueNode node) {
            return ((ReinterpretNode) node).value();
        }
    }

    public static class IfNodeAdapter extends MatchNodeAdapter {

        @Override
        protected ValueNode getFirstInput(ValueNode node) {
            return ((IfNode) node).condition();
        }
    }

    public static class ReadNodeAdapter extends MatchNodeAdapter {

        @Override
        protected ValueNode getFirstInput(ValueNode node) {
            return ((Access) node).object();
        }
    }

    public static class BinaryOpLogicNodeAdapter extends MatchNodeAdapter {

        @Override
        protected ValueNode getFirstInput(ValueNode node) {
            return ((BinaryOpLogicNode) node).x();
        }

        @Override
        protected ValueNode getSecondInput(ValueNode node) {
            return ((BinaryOpLogicNode) node).y();
        }
    }
}
