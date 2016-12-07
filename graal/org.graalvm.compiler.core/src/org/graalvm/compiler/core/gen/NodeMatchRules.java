/*
 * Copyright (c) 2009, 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.compiler.gen;

import jdk.vm.ci.meta.Value;

import com.oracle.graal.compiler.match.MatchableNode;
import com.oracle.graal.graph.Node;
import com.oracle.graal.lir.LIRFrameState;
import com.oracle.graal.lir.LIRInstruction;
import com.oracle.graal.lir.LabelRef;
import com.oracle.graal.lir.gen.LIRGeneratorTool;
import com.oracle.graal.nodes.ConstantNode;
import com.oracle.graal.nodes.DeoptimizingNode;
import com.oracle.graal.nodes.FixedNode;
import com.oracle.graal.nodes.IfNode;
import com.oracle.graal.nodes.PiNode;
import com.oracle.graal.nodes.calc.AddNode;
import com.oracle.graal.nodes.calc.AndNode;
import com.oracle.graal.nodes.calc.FloatConvertNode;
import com.oracle.graal.nodes.calc.FloatEqualsNode;
import com.oracle.graal.nodes.calc.FloatLessThanNode;
import com.oracle.graal.nodes.calc.IntegerBelowNode;
import com.oracle.graal.nodes.calc.IntegerEqualsNode;
import com.oracle.graal.nodes.calc.IntegerLessThanNode;
import com.oracle.graal.nodes.calc.IntegerTestNode;
import com.oracle.graal.nodes.calc.LeftShiftNode;
import com.oracle.graal.nodes.calc.MulNode;
import com.oracle.graal.nodes.calc.NarrowNode;
import com.oracle.graal.nodes.calc.ObjectEqualsNode;
import com.oracle.graal.nodes.calc.OrNode;
import com.oracle.graal.nodes.calc.PointerEqualsNode;
import com.oracle.graal.nodes.calc.ReinterpretNode;
import com.oracle.graal.nodes.calc.SignExtendNode;
import com.oracle.graal.nodes.calc.SubNode;
import com.oracle.graal.nodes.calc.UnsignedRightShiftNode;
import com.oracle.graal.nodes.calc.XorNode;
import com.oracle.graal.nodes.calc.ZeroExtendNode;
import com.oracle.graal.nodes.memory.FloatingReadNode;
import com.oracle.graal.nodes.memory.ReadNode;
import com.oracle.graal.nodes.memory.WriteNode;

@MatchableNode(nodeClass = ConstantNode.class, shareable = true)
@MatchableNode(nodeClass = FloatConvertNode.class, inputs = {"value"})
@MatchableNode(nodeClass = FloatingReadNode.class, inputs = {"address"})
@MatchableNode(nodeClass = IfNode.class, inputs = {"condition"})
@MatchableNode(nodeClass = SubNode.class, inputs = {"x", "y"})
@MatchableNode(nodeClass = LeftShiftNode.class, inputs = {"x", "y"})
@MatchableNode(nodeClass = NarrowNode.class, inputs = {"value"})
@MatchableNode(nodeClass = ReadNode.class, inputs = {"address"})
@MatchableNode(nodeClass = ReinterpretNode.class, inputs = {"value"})
@MatchableNode(nodeClass = SignExtendNode.class, inputs = {"value"})
@MatchableNode(nodeClass = UnsignedRightShiftNode.class, inputs = {"x", "y"})
@MatchableNode(nodeClass = WriteNode.class, inputs = {"address", "value"})
@MatchableNode(nodeClass = ZeroExtendNode.class, inputs = {"value"})
@MatchableNode(nodeClass = AndNode.class, inputs = {"x", "y"}, commutative = true)
@MatchableNode(nodeClass = FloatEqualsNode.class, inputs = {"x", "y"}, commutative = true)
@MatchableNode(nodeClass = FloatLessThanNode.class, inputs = {"x", "y"}, commutative = true)
@MatchableNode(nodeClass = PointerEqualsNode.class, inputs = {"x", "y"}, commutative = true)
@MatchableNode(nodeClass = AddNode.class, inputs = {"x", "y"}, commutative = true)
@MatchableNode(nodeClass = IntegerBelowNode.class, inputs = {"x", "y"}, commutative = true)
@MatchableNode(nodeClass = IntegerEqualsNode.class, inputs = {"x", "y"}, commutative = true)
@MatchableNode(nodeClass = IntegerLessThanNode.class, inputs = {"x", "y"}, commutative = true)
@MatchableNode(nodeClass = MulNode.class, inputs = {"x", "y"}, commutative = true)
@MatchableNode(nodeClass = IntegerTestNode.class, inputs = {"x", "y"}, commutative = true)
@MatchableNode(nodeClass = ObjectEqualsNode.class, inputs = {"x", "y"}, commutative = true)
@MatchableNode(nodeClass = OrNode.class, inputs = {"x", "y"}, commutative = true)
@MatchableNode(nodeClass = XorNode.class, inputs = {"x", "y"}, commutative = true)
@MatchableNode(nodeClass = PiNode.class, inputs = {"object"})
public abstract class NodeMatchRules {

    NodeLIRBuilder lirBuilder;
    protected final LIRGeneratorTool gen;

    protected NodeMatchRules(LIRGeneratorTool gen) {
        this.gen = gen;
    }

    protected LIRGeneratorTool getLIRGeneratorTool() {
        return gen;
    }

    /*
     * For now we do not want to expose the full lirBuilder to subclasses, so we delegate the few
     * methods that are actually needed. If the list grows too long, exposing lirBuilder might be
     * the better approach.
     */

    protected final Value operand(Node node) {
        return lirBuilder.operand(node);
    }

    protected final LIRFrameState state(DeoptimizingNode deopt) {
        return lirBuilder.state(deopt);
    }

    protected final LabelRef getLIRBlock(FixedNode b) {
        return lirBuilder.getLIRBlock(b);
    }

    protected final void append(LIRInstruction op) {
        lirBuilder.append(op);
    }
}
