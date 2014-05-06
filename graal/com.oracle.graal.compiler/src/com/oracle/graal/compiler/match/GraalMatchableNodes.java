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

import com.oracle.graal.compiler.common.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.calc.*;
import com.oracle.graal.nodes.extended.*;

/**
 * Helper class to describe the matchable nodes in the core Graal IR. These could possibly live in
 * their respective classes but for simplicity in the {@link MatchProcessor} they are grouped here.
 */
@MatchableNode(nodeClass = ConstantNode.class, inputs = 0, shareable = true)
@MatchableNode(nodeClass = FloatConvertNode.class, inputs = 1, adapter = GraalMatchableNodes.ConvertNodeAdapter.class)
@MatchableNode(nodeClass = FloatSubNode.class, inputs = 2, adapter = GraalMatchableNodes.BinaryNodeAdapter.class)
@MatchableNode(nodeClass = FloatingReadNode.class, inputs = 2, adapter = GraalMatchableNodes.AccessAdapter.class)
@MatchableNode(nodeClass = IfNode.class, inputs = 1, adapter = GraalMatchableNodes.IfNodeAdapter.class)
@MatchableNode(nodeClass = IntegerSubNode.class, inputs = 2, adapter = GraalMatchableNodes.BinaryNodeAdapter.class)
@MatchableNode(nodeClass = LeftShiftNode.class, inputs = 2, adapter = GraalMatchableNodes.BinaryNodeAdapter.class)
@MatchableNode(nodeClass = NarrowNode.class, inputs = 1, adapter = GraalMatchableNodes.ConvertNodeAdapter.class)
@MatchableNode(nodeClass = ReadNode.class, inputs = 2, adapter = GraalMatchableNodes.AccessAdapter.class)
@MatchableNode(nodeClass = ReinterpretNode.class, inputs = 1, adapter = GraalMatchableNodes.ReinterpretNodeAdapter.class)
@MatchableNode(nodeClass = SignExtendNode.class, inputs = 1, adapter = GraalMatchableNodes.ConvertNodeAdapter.class)
@MatchableNode(nodeClass = UnsignedRightShiftNode.class, inputs = 2, adapter = GraalMatchableNodes.BinaryNodeAdapter.class)
@MatchableNode(nodeClass = WriteNode.class, inputs = 3, adapter = GraalMatchableNodes.WriteNodeAdapter.class)
@MatchableNode(nodeClass = ZeroExtendNode.class, inputs = 1, adapter = GraalMatchableNodes.ConvertNodeAdapter.class)
@MatchableNode(nodeClass = AndNode.class, inputs = 2, adapter = GraalMatchableNodes.BinaryNodeAdapter.class, commutative = true)
@MatchableNode(nodeClass = FloatAddNode.class, inputs = 2, adapter = GraalMatchableNodes.BinaryNodeAdapter.class, commutative = true)
@MatchableNode(nodeClass = FloatEqualsNode.class, inputs = 2, adapter = GraalMatchableNodes.BinaryOpLogicNodeAdapter.class, commutative = true)
@MatchableNode(nodeClass = FloatLessThanNode.class, inputs = 2, adapter = GraalMatchableNodes.BinaryOpLogicNodeAdapter.class, commutative = true)
@MatchableNode(nodeClass = FloatMulNode.class, inputs = 2, adapter = GraalMatchableNodes.BinaryNodeAdapter.class, commutative = true)
@MatchableNode(nodeClass = IntegerAddNode.class, inputs = 2, adapter = GraalMatchableNodes.BinaryNodeAdapter.class, commutative = true)
@MatchableNode(nodeClass = IntegerBelowThanNode.class, inputs = 2, adapter = GraalMatchableNodes.BinaryOpLogicNodeAdapter.class, commutative = true)
@MatchableNode(nodeClass = IntegerEqualsNode.class, inputs = 2, adapter = GraalMatchableNodes.BinaryOpLogicNodeAdapter.class, commutative = true)
@MatchableNode(nodeClass = IntegerLessThanNode.class, inputs = 2, adapter = GraalMatchableNodes.BinaryOpLogicNodeAdapter.class, commutative = true)
@MatchableNode(nodeClass = IntegerMulNode.class, inputs = 2, adapter = GraalMatchableNodes.BinaryNodeAdapter.class, commutative = true)
@MatchableNode(nodeClass = IntegerTestNode.class, inputs = 2, adapter = GraalMatchableNodes.BinaryOpLogicNodeAdapter.class, commutative = true)
@MatchableNode(nodeClass = ObjectEqualsNode.class, inputs = 2, adapter = GraalMatchableNodes.BinaryOpLogicNodeAdapter.class, commutative = true)
@MatchableNode(nodeClass = OrNode.class, inputs = 2, adapter = GraalMatchableNodes.BinaryNodeAdapter.class, commutative = true)
@MatchableNode(nodeClass = XorNode.class, inputs = 2, adapter = GraalMatchableNodes.BinaryNodeAdapter.class, commutative = true)
@MatchableNode(nodeClass = PiNode.class, inputs = 1, adapter = GraalMatchableNodes.PiNodeAdapter.class)
@MatchableNode(nodeClass = ConstantLocationNode.class, shareable = true)
public class GraalMatchableNodes {
    public static class PiNodeAdapter extends MatchNodeAdapter {
        @Override
        public ValueNode getInput(int input, ValueNode node) {
            if (input == 0) {
                return ((PiNode) node).object();
            }
            throw GraalInternalError.shouldNotReachHere();
        }
    }

    public static class BinaryNodeAdapter extends MatchNodeAdapter {
        @Override
        public ValueNode getInput(int input, ValueNode node) {
            if (input == 0) {
                return ((BinaryNode) node).x();
            }
            if (input == 1) {
                return ((BinaryNode) node).y();
            }
            throw GraalInternalError.shouldNotReachHere();
        }
    }

    public static class WriteNodeAdapter extends MatchNodeAdapter {
        @Override
        public ValueNode getInput(int input, ValueNode node) {
            if (input == 0) {
                return ((WriteNode) node).object();
            }
            if (input == 1) {
                return ((WriteNode) node).location();
            }
            if (input == 2) {
                return ((WriteNode) node).value();
            }
            throw GraalInternalError.shouldNotReachHere();
        }
    }

    public static class ConvertNodeAdapter extends MatchNodeAdapter {
        @Override
        public ValueNode getInput(int input, ValueNode node) {
            if (input == 0) {
                return ((ConvertNode) node).getInput();
            }
            throw GraalInternalError.shouldNotReachHere();
        }
    }

    public static class ReinterpretNodeAdapter extends MatchNodeAdapter {
        @Override
        public ValueNode getInput(int input, ValueNode node) {
            if (input == 0) {
                return ((ReinterpretNode) node).value();
            }
            throw GraalInternalError.shouldNotReachHere();
        }
    }

    public static class IfNodeAdapter extends MatchNodeAdapter {
        @Override
        public ValueNode getInput(int input, ValueNode node) {
            if (input == 0) {
                return ((IfNode) node).condition();
            }
            throw GraalInternalError.shouldNotReachHere();
        }
    }

    public static class AccessAdapter extends MatchNodeAdapter {

        @Override
        public ValueNode getInput(int input, ValueNode node) {
            if (input == 0) {
                return ((Access) node).object();
            }
            if (input == 1) {
                return ((Access) node).accessLocation();
            }
            throw GraalInternalError.shouldNotReachHere();
        }
    }

    public static class BinaryOpLogicNodeAdapter extends MatchNodeAdapter {
        @Override
        public ValueNode getInput(int input, ValueNode node) {
            if (input == 0) {
                return ((BinaryOpLogicNode) node).x();
            }
            if (input == 1) {
                return ((BinaryOpLogicNode) node).y();
            }
            throw GraalInternalError.shouldNotReachHere();
        }
    }
}
