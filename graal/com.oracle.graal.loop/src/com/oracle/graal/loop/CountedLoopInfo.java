/*
 * Copyright (c) 2012, 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.loop;

import static com.oracle.graal.nodes.calc.IntegerArithmeticNode.*;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.common.type.*;
import com.oracle.graal.loop.InductionVariable.Direction;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.calc.*;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.nodes.type.*;

public class CountedLoopInfo {

    private final LoopEx loop;
    private InductionVariable iv;
    private ValueNode end;
    private boolean oneOff;
    private BeginNode body;

    CountedLoopInfo(LoopEx loop, InductionVariable iv, ValueNode end, boolean oneOff, BeginNode body) {
        this.loop = loop;
        this.iv = iv;
        this.end = end;
        this.oneOff = oneOff;
        this.body = body;
    }

    public ValueNode maxTripCountNode() {
        return maxTripCountNode(false);
    }

    public ValueNode maxTripCountNode(boolean assumePositive) {
        StructuredGraph graph = iv.valueNode().graph();
        Stamp stamp = iv.valueNode().stamp();
        IntegerArithmeticNode range = IntegerArithmeticNode.sub(graph, end, iv.initNode());
        if (oneOff) {
            if (iv.direction() == Direction.Up) {
                range = IntegerArithmeticNode.add(graph, range, ConstantNode.forIntegerStamp(stamp, 1, graph));
            } else {
                range = IntegerArithmeticNode.sub(graph, range, ConstantNode.forIntegerStamp(stamp, 1, graph));
            }
        }
        IntegerDivNode div = graph.add(new IntegerDivNode(iv.valueNode().stamp().unrestricted(), range, iv.strideNode()));
        graph.addBeforeFixed(loop.entryPoint(), div);
        ConstantNode zero = ConstantNode.forIntegerStamp(stamp, 0, graph);
        if (assumePositive) {
            return div;
        }
        return graph.unique(new ConditionalNode(graph.unique(new IntegerLessThanNode(zero, div)), div, zero));
    }

    public boolean isConstantMaxTripCount() {
        return end instanceof ConstantNode && iv.isConstantInit() && iv.isConstantStride();
    }

    public long constantMaxTripCount() {
        long off = oneOff ? iv.direction() == Direction.Up ? 1 : -1 : 0;
        long max = (((ConstantNode) end).asConstant().asLong() + off - iv.constantInit()) / iv.constantStride();
        return Math.max(0, max);
    }

    public boolean isExactTripCount() {
        return loop.loopBegin().loopExits().count() == 1;
    }

    public ValueNode exactTripCountNode() {
        assert isExactTripCount();
        return maxTripCountNode();
    }

    public boolean isConstantExactTripCount() {
        assert isExactTripCount();
        return isConstantMaxTripCount();
    }

    public long constantExactTripCount() {
        assert isExactTripCount();
        return constantMaxTripCount();
    }

    @Override
    public String toString() {
        return "iv=" + iv + " until " + end + (oneOff ? iv.direction() == Direction.Up ? "+1" : "-1" : "");
    }

    public ValueNode getLimit() {
        return end;
    }

    public ValueNode getStart() {
        return iv.initNode();
    }

    public boolean isLimitIncluded() {
        return oneOff;
    }

    public BeginNode getBody() {
        return body;
    }

    public Direction getDirection() {
        return iv.direction();
    }

    public InductionVariable getCounter() {
        return iv;
    }

    public GuardingNode getOverFlowGuard() {
        return loop.loopBegin().getOverflowGuard();
    }

    public GuardingNode createOverFlowGuard() {
        GuardingNode overflowGuard = getOverFlowGuard();
        if (overflowGuard != null) {
            return overflowGuard;
        }
        IntegerStamp stamp = (IntegerStamp) iv.valueNode().stamp();
        StructuredGraph graph = iv.valueNode().graph();
        CompareNode cond; // we use a negated guard with a < condition to achieve a >=
        ConstantNode one = ConstantNode.forIntegerStamp(stamp, 1, graph);
        if (iv.direction() == Direction.Up) {
            IntegerArithmeticNode v1 = sub(graph, ConstantNode.forIntegerStamp(stamp, IntegerStamp.defaultMaxValue(stamp.getBits()), graph), sub(graph, iv.strideNode(), one));
            if (oneOff) {
                v1 = sub(graph, v1, one);
            }
            cond = graph.unique(new IntegerLessThanNode(v1, end));
        } else {
            assert iv.direction() == Direction.Down;
            IntegerArithmeticNode v1 = add(graph, ConstantNode.forIntegerStamp(stamp, IntegerStamp.defaultMinValue(stamp.getBits()), graph), sub(graph, one, iv.strideNode()));
            if (oneOff) {
                v1 = add(graph, v1, one);
            }
            cond = graph.unique(new IntegerLessThanNode(end, v1));
        }
        overflowGuard = graph.unique(new GuardNode(cond, BeginNode.prevBegin(loop.entryPoint()), DeoptimizationReason.LoopLimitCheck, DeoptimizationAction.InvalidateRecompile, true,
                        Constant.NULL_OBJECT)); // TODO gd: use speculation
        loop.loopBegin().setOverflowGuard(overflowGuard);
        return overflowGuard;
    }

    public IntegerStamp getStamp() {
        return (IntegerStamp) iv.valueNode().stamp();
    }
}
