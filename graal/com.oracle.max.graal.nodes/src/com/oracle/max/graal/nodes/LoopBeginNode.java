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
package com.oracle.max.graal.nodes;

import java.util.*;

import com.oracle.max.cri.ci.*;
import com.oracle.max.graal.graph.*;
import com.oracle.max.graal.nodes.loop.*;
import com.oracle.max.graal.nodes.spi.*;


public class LoopBeginNode extends MergeNode implements Node.IterableNodeType, LIRLowerable {

    private double loopFrequency;

    public LoopBeginNode() {
        loopFrequency = 1;
    }

    public double loopFrequency() {
        return loopFrequency;
    }

    public void setLoopFrequency(double loopFrequency) {
        this.loopFrequency = loopFrequency;
    }

    public LoopEndNode loopEnd() {
        for (LoopEndNode end : usages().filter(LoopEndNode.class)) {
            if (end.loopBegin() == this) {
                return end;
            }
        }
        return null;
    }

    @Override
    public void generate(LIRGeneratorTool gen) {
        // Nothing to emit, since this is node is used for structural purposes only.
    }

    @Override
    public int phiPredecessorCount() {
        return endCount() + 1;
    }

    @Override
    public int phiPredecessorIndex(FixedNode pred) {
        if (pred == forwardEdge()) {
            return 0;
        } else if (pred == this.loopEnd()) {
            return 1;
        }
        throw ValueUtil.shouldNotReachHere("unknown pred : " + pred + "(sp=" + forwardEdge() + ", le=" + this.loopEnd() + ")");
    }

    @Override
    public FixedNode phiPredecessorAt(int index) {
        if (index == 0) {
            return forwardEdge();
        } else if (index == 1) {
            return loopEnd();
        }
        throw ValueUtil.shouldNotReachHere();
    }

    public Collection<InductionVariableNode> inductionVariables() {
        // TODO (gd) produces useless garbage
        List<InductionVariableNode> list = new LinkedList<>();
        collectInductionVariables(this, list);
        return list;
    }

    private static void collectInductionVariables(Node node, Collection<InductionVariableNode> collection) {
        for (InductionVariableNode iv : node.usages().filter(InductionVariableNode.class)) {
            collection.add(iv);
            collectInductionVariables(iv, collection);
        }
    }

    @Override
    public Iterable<? extends Node> phiPredecessors() {
        return Arrays.asList(new Node[]{this.forwardEdge(), this.loopEnd()});
    }

    public EndNode forwardEdge() {
        return this.endAt(0);
    }

    public LoopCounterNode loopCounter() {
        return loopCounter(CiKind.Long);
    }

    public LoopCounterNode loopCounter(CiKind kind) {
        for (LoopCounterNode counter : usages().filter(LoopCounterNode.class)) {
            if (counter.kind() == kind) {
                return counter;
            }
        }
        return graph().add(new LoopCounterNode(kind, this));
    }

    @Override
    public boolean verify() {
        assertTrue(loopEnd() != null, "missing loopEnd");
        assertTrue(forwardEdge() != null, "missing forwardEdge");
        assertTrue(usages().filter(LoopEndNode.class).snapshot().size() == 1, "multiple loop ends");
        return super.verify();
    }

    @Override
    public Map<Object, Object> getDebugProperties() {
        Map<Object, Object> properties = super.getDebugProperties();
        properties.put("loopFrequency", String.format("%7.1f", loopFrequency));
        return properties;
    }
}
