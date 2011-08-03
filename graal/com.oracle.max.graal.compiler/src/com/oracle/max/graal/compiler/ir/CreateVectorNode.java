/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.max.graal.compiler.ir;

import java.util.*;

import com.oracle.max.graal.compiler.debug.*;
import com.oracle.max.graal.compiler.gen.*;
import com.oracle.max.graal.compiler.ir.Phi.PhiType;
import com.oracle.max.graal.compiler.phases.LoweringPhase.*;
import com.oracle.max.graal.graph.*;
import com.sun.cri.ci.*;


public final class CreateVectorNode extends AbstractVectorNode {

    @NodeInput
    private Value length;

    public Value length() {
        return length;
    }

    public void setLength(Value x) {
        updateUsages(length, x);
        length = x;
    }

    private boolean reversed;

    public boolean reversed() {
        return reversed;
    }

    public void setReversed(boolean r) {
        reversed = r;
    }

    public CreateVectorNode(boolean reversed, Value length, Graph graph) {
        super(CiKind.Illegal, null, graph);
        setLength(length);
        setReversed(reversed);
    }

    @Override
    public Map<Object, Object> getDebugProperties() {
        Map<Object, Object> debugProperties = super.getDebugProperties();
        debugProperties.put("reversed", reversed);
        return debugProperties;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T extends Op> T lookup(Class<T> clazz) {
        if (clazz == LIRGenerator.LIRGeneratorOp.class) {
            return null;
        } else if (clazz == LoweringOp.class) {
            return (T) LOWERING_OP;
        }
        return super.lookup(clazz);
    }

    @Override
    public void print(LogStream out) {
        out.print("vector with length ").print(length().toString());
    }

    @Override
    public Node copy(Graph into) {
        CreateVectorNode x = new CreateVectorNode(reversed, null, into);
        super.copyInto(x);
        return x;
    }

    @Override
    public boolean valueEqual(Node i) {
        return (i instanceof CreateVectorNode);
    }

    private LoopBegin createLoop(Map<AbstractVectorNode, Value> map) {
        EndNode end = new EndNode(graph());
        LoopBegin loopBegin = new LoopBegin(graph());
        loopBegin.addEnd(end);
        Phi loopVariable = new Phi(CiKind.Int, loopBegin, PhiType.Value, graph());

        if (reversed) {
            IntegerSub add = new IntegerSub(CiKind.Int, loopVariable, Constant.forInt(1, graph()), graph());
            loopVariable.addInput(new IntegerSub(CiKind.Int, length(), Constant.forInt(1, graph()), graph()));
            loopVariable.addInput(add);
        } else {
            IntegerAdd add = new IntegerAdd(CiKind.Int, loopVariable, Constant.forInt(1, graph()), graph());
            loopVariable.addInput(Constant.forInt(0, graph()));
            loopVariable.addInput(add);
        }

        LoopEnd loopEnd = new LoopEnd(graph());
        loopEnd.setLoopBegin(loopBegin);
        loopBegin.setStateAfter(stateAfter());
        Compare condition;
        if (reversed) {
            condition = new Compare(loopVariable, Condition.GE, Constant.forInt(0, graph()), graph());
        } else {
            condition = new Compare(loopVariable, Condition.LT, length(), graph());
        }
        int expectedLength = 100; // TODO: it may be possible to get a more accurate estimate...?
        if (length().isConstant()) {
            expectedLength = length().asConstant().asInt();
        }
        If ifNode = new If(condition, 1.0 / expectedLength, graph());
        loopBegin.setNext(ifNode);
        ifNode.setTrueSuccessor(loopEnd);
        this.replaceAtPredecessors(end);
        ifNode.setFalseSuccessor(this);
        map.put(this, loopVariable);
        return loopBegin;
    }

    private static final LoweringOp LOWERING_OP = new LoweringOp() {
        @Override
        public void lower(Node n, CiLoweringTool tool) {
            CreateVectorNode vectorNode = (CreateVectorNode) n;

            IdentityHashMap<AbstractVectorNode, Value> nodes = new IdentityHashMap<AbstractVectorNode, Value>();
            LoopBegin begin = vectorNode.createLoop(nodes);
            for (Node use : vectorNode.usages()) {
                processUse(begin, use, nodes);
            }
        }

        private void processUse(LoopBegin loop, Node use, IdentityHashMap<AbstractVectorNode, Value> nodes) {
            AbstractVectorNode vectorNode = (AbstractVectorNode) use;
            if (nodes.containsKey(vectorNode)) {
                return;
            }
            nodes.put(vectorNode, null);

            // Make sure inputs are evaluated.
            for (Node input : use.inputs()) {
                if (input instanceof AbstractVectorNode) {
                    AbstractVectorNode abstractVectorNodeInput = (AbstractVectorNode) input;
                    processUse(loop, abstractVectorNodeInput, nodes);
                }
            }

            vectorNode.addToLoop(loop, nodes);

            // Go on to usages.
            for (Node usage : use.usages()) {
                processUse(loop, usage, nodes);
            }
        }
    };
}
