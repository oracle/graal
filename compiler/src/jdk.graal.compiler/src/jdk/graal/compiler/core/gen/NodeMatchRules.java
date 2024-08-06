/*
 * Copyright (c) 2009, 2023, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.core.gen;

import jdk.graal.compiler.core.match.MatchableNode;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.lir.LIRFrameState;
import jdk.graal.compiler.lir.LIRInstruction;
import jdk.graal.compiler.lir.LabelRef;
import jdk.graal.compiler.lir.gen.LIRGeneratorTool;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.DeoptimizingNode;
import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.IfNode;
import jdk.graal.compiler.nodes.PiNode;
import jdk.graal.compiler.nodes.calc.AddNode;
import jdk.graal.compiler.nodes.calc.AndNode;
import jdk.graal.compiler.nodes.calc.ConditionalNode;
import jdk.graal.compiler.nodes.calc.FloatConvertNode;
import jdk.graal.compiler.nodes.calc.FloatEqualsNode;
import jdk.graal.compiler.nodes.calc.FloatLessThanNode;
import jdk.graal.compiler.nodes.calc.IntegerBelowNode;
import jdk.graal.compiler.nodes.calc.IntegerEqualsNode;
import jdk.graal.compiler.nodes.calc.IntegerLessThanNode;
import jdk.graal.compiler.nodes.calc.IntegerTestNode;
import jdk.graal.compiler.nodes.calc.LeftShiftNode;
import jdk.graal.compiler.nodes.calc.MulNode;
import jdk.graal.compiler.nodes.calc.NarrowNode;
import jdk.graal.compiler.nodes.calc.NegateNode;
import jdk.graal.compiler.nodes.calc.NotNode;
import jdk.graal.compiler.nodes.calc.ObjectEqualsNode;
import jdk.graal.compiler.nodes.calc.OrNode;
import jdk.graal.compiler.nodes.calc.PointerEqualsNode;
import jdk.graal.compiler.nodes.calc.ReinterpretNode;
import jdk.graal.compiler.nodes.calc.RightShiftNode;
import jdk.graal.compiler.nodes.calc.SignExtendNode;
import jdk.graal.compiler.nodes.calc.SqrtNode;
import jdk.graal.compiler.nodes.calc.SubNode;
import jdk.graal.compiler.nodes.calc.UnsignedRightShiftNode;
import jdk.graal.compiler.nodes.calc.XorNode;
import jdk.graal.compiler.nodes.calc.ZeroExtendNode;
import jdk.graal.compiler.nodes.java.LogicCompareAndSwapNode;
import jdk.graal.compiler.nodes.java.ValueCompareAndSwapNode;
import jdk.graal.compiler.nodes.memory.FloatingReadNode;
import jdk.graal.compiler.nodes.memory.ReadNode;
import jdk.graal.compiler.nodes.memory.SideEffectFreeWriteNode;
import jdk.graal.compiler.nodes.memory.WriteNode;

import jdk.vm.ci.meta.Value;

@MatchableNode(nodeClass = ConstantNode.class, shareable = true, ignoresSideEffects = true)
@MatchableNode(nodeClass = FloatConvertNode.class, inputs = {"value"}, ignoresSideEffects = true)
@MatchableNode(nodeClass = FloatingReadNode.class, inputs = {"address"})
@MatchableNode(nodeClass = IfNode.class, inputs = {"condition"})
@MatchableNode(nodeClass = SubNode.class, inputs = {"x", "y"}, ignoresSideEffects = true)
@MatchableNode(nodeClass = LeftShiftNode.class, inputs = {"x", "y"}, ignoresSideEffects = true)
@MatchableNode(nodeClass = NarrowNode.class, inputs = {"value"}, ignoresSideEffects = true)
@MatchableNode(nodeClass = ReadNode.class, inputs = {"address"})
@MatchableNode(nodeClass = ReinterpretNode.class, inputs = {"value"}, ignoresSideEffects = true)
@MatchableNode(nodeClass = SignExtendNode.class, inputs = {"value"}, ignoresSideEffects = true)
@MatchableNode(nodeClass = UnsignedRightShiftNode.class, inputs = {"x", "y"}, ignoresSideEffects = true)
@MatchableNode(nodeClass = WriteNode.class, inputs = {"address", "value"})
@MatchableNode(nodeClass = SideEffectFreeWriteNode.class, inputs = {"address", "value"})
@MatchableNode(nodeClass = ZeroExtendNode.class, inputs = {"value"}, ignoresSideEffects = true)
@MatchableNode(nodeClass = AndNode.class, inputs = {"x", "y"}, commutative = true, ignoresSideEffects = true)
@MatchableNode(nodeClass = NegateNode.class, inputs = {"value"}, ignoresSideEffects = true)
@MatchableNode(nodeClass = NotNode.class, inputs = {"value"}, ignoresSideEffects = true)
@MatchableNode(nodeClass = FloatEqualsNode.class, inputs = {"x", "y"}, commutative = true, ignoresSideEffects = true)
@MatchableNode(nodeClass = FloatLessThanNode.class, inputs = {"x", "y"}, commutative = true, ignoresSideEffects = true)
@MatchableNode(nodeClass = PointerEqualsNode.class, inputs = {"x", "y"}, commutative = true, ignoresSideEffects = true)
@MatchableNode(nodeClass = AddNode.class, inputs = {"x", "y"}, commutative = true, ignoresSideEffects = true)
@MatchableNode(nodeClass = IntegerBelowNode.class, inputs = {"x", "y"}, commutative = true, ignoresSideEffects = true)
@MatchableNode(nodeClass = IntegerEqualsNode.class, inputs = {"x", "y"}, commutative = true, ignoresSideEffects = true)
@MatchableNode(nodeClass = IntegerLessThanNode.class, inputs = {"x", "y"}, commutative = true, ignoresSideEffects = true)
@MatchableNode(nodeClass = MulNode.class, inputs = {"x", "y"}, commutative = true, ignoresSideEffects = true)
@MatchableNode(nodeClass = IntegerTestNode.class, inputs = {"x", "y"}, commutative = true, ignoresSideEffects = true)
@MatchableNode(nodeClass = ObjectEqualsNode.class, inputs = {"x", "y"}, commutative = true, ignoresSideEffects = true)
@MatchableNode(nodeClass = OrNode.class, inputs = {"x", "y"}, commutative = true, ignoresSideEffects = true)
@MatchableNode(nodeClass = XorNode.class, inputs = {"x", "y"}, commutative = true, ignoresSideEffects = true)
@MatchableNode(nodeClass = PiNode.class, inputs = {"object"}, ignoresSideEffects = true)
@MatchableNode(nodeClass = LogicCompareAndSwapNode.class, inputs = {"address", "expectedValue", "newValue"})
@MatchableNode(nodeClass = ValueCompareAndSwapNode.class, inputs = {"address", "expectedValue", "newValue"})
@MatchableNode(nodeClass = RightShiftNode.class, inputs = {"x", "y"}, ignoresSideEffects = true)
@MatchableNode(nodeClass = SqrtNode.class, inputs = {"value"}, ignoresSideEffects = true)
@MatchableNode(nodeClass = ConditionalNode.class, inputs = {"condition", "trueValue", "falseValue"}, ignoresSideEffects = true)
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
