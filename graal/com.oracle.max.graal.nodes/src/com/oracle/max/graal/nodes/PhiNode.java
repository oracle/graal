/*
 * Copyright (c) 2009, 2011, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.max.graal.graph.*;
import com.oracle.max.graal.nodes.calc.*;
import com.oracle.max.graal.nodes.spi.*;
import com.oracle.max.graal.nodes.type.*;
import com.sun.cri.ci.*;

/**
 * The {@code PhiNode} represents the merging of dataflow in the graph. It refers to a merge
 * and a variable.
 */
public final class PhiNode extends FloatingNode implements Canonicalizable, Node.IterableNodeType {

    @Input(notDataflow = true) private MergeNode merge;

    @Input private final NodeInputList<ValueNode> values = new NodeInputList<ValueNode>(this);

    public MergeNode merge() {
        return merge;
    }

    public static enum PhiType {
        Value, // normal value phis
        Memory, // memory phis
        Virtual // phis used for VirtualObjectField merges
    }

    private final PhiType type;

    private PhiNode(CiKind kind, PhiType type) {
        this(kind, null, type);
    }

    public PhiNode(CiKind kind, MergeNode merge, PhiType type) {
        super(StampFactory.forKind(kind));
        this.type = type;
        this.merge = merge;
    }

    public PhiType type() {
        return type;
    }

    public NodeInputList<ValueNode> values() {
        return values;
    }

    public boolean inferStamp() {
        Stamp newStamp = StampFactory.or(values());
        if (stamp().equals(newStamp)) {
            return false;
        } else {
            setStamp(newStamp);
            return true;
        }
    }

    @Override
    public boolean verify() {
        assertTrue(merge() != null, "missing merge");
        assertTrue(merge().phiPredecessorCount() == valueCount(), "mismatch between merge predecessor count and phi value count: %d != %d", merge().phiPredecessorCount(), valueCount());
        if (type == PhiType.Value) {
            for (ValueNode v : values()) {
                assertTrue(v.kind() == kind(), "all phi values must have same kind");
            }
        }
        return super.verify();
    }

    /**
     * Get the instruction that produces the value associated with the i'th predecessor of the merge.
     *
     * @param i the index of the predecessor
     * @return the instruction that produced the value in the i'th predecessor
     */
    public ValueNode valueAt(int i) {
        return values.get(i);
    }

    public void setValueAt(int i, ValueNode x) {
        values.set(i, x);
    }

    public ValueNode valueAt(FixedNode pred) {
        return valueAt(merge().phiPredecessorIndex(pred));
    }

    /**
     * Get the number of inputs to this phi (i.e. the number of predecessors to the merge).
     *
     * @return the number of inputs in this phi
     */
    public int valueCount() {
        return values.size();
    }

    public void clearValues() {
        values.clear();
    }

    @Override
    public String toString(Verbosity verbosity) {
        if (verbosity == Verbosity.Name) {
            StringBuilder str = new StringBuilder();
            for (int i = 0; i < valueCount(); ++i) {
                if (i != 0) {
                    str.append(' ');
                }
                str.append(valueAt(i) == null ? "-" : valueAt(i).toString(Verbosity.Id));
            }
            if (type == PhiType.Value) {
                return super.toString(Verbosity.Name) + "(" + str + ")";
            } else {
                return type + super.toString(Verbosity.Name) + "(" + str + ")";
            }
        } else {
            return super.toString(verbosity);
        }
    }

    public void addInput(ValueNode x) {
        values.add(x);
    }

    public void removeInput(int index) {
        values.remove(index);
    }

    public ValueNode singleValue() {
        ValueNode differentValue = null;
        for (ValueNode n : values()) {
            if (n != this) {
                if (differentValue == null) {
                    differentValue = n;
                } else if (differentValue != n) {
                    return null;
                }
            }
        }
        return differentValue;
    }

    @Override
    public Node canonical(CanonicalizerTool tool) {
        ValueNode singleValue = singleValue();

        if (singleValue != null) {
            return singleValue;
        }

        return canonicalizeMaterializationPhi();
    }

    private Node canonicalizeMaterializationPhi() {
        if (merge().endCount() != 2 || merge() instanceof LoopBeginNode) {
            return this;
        }
        if (merge().usages().size() > 1) { // TODO(gd) disable canonicalization of multiple conditional while we are not able to fuse them and the potentially leftover If in the backend
            return this;
        }

        Node end0 = merge().endAt(0);
        Node end1 = merge().endAt(1);
        Node endPred0 = end0.predecessor();
        Node endPred1 = end1.predecessor();
        if (!(endPred0 instanceof BeginNode) || !(endPred1 instanceof BeginNode)) {
            return this;
        }
        if (endPred0.predecessor() != endPred1.predecessor() || !(endPred0.predecessor() instanceof IfNode)) {
            return this;
        }

        // Get true/false value.
        IfNode ifNode = (IfNode) endPred0.predecessor();
        boolean inverted = ifNode.trueSuccessor() == endPred1;
        ValueNode trueValue = valueAt(inverted ? 1 : 0);
        ValueNode falseValue = valueAt(inverted ? 0 : 1);
        if (trueValue.kind() != falseValue.kind()) {
            return this;
        }

        // Only allow int constants.
        if (trueValue.kind() != CiKind.Int || !trueValue.isConstant() || !falseValue.isConstant()) {
            return this;
        }

        ConstantNode trueConstantNode = (ConstantNode) trueValue;
        ConstantNode falseConstantNode = (ConstantNode) falseValue;
        BooleanNode compare = ifNode.compare();
        removeIfNode(ifNode);
        return MaterializeNode.create(compare, ifNode.graph(), trueConstantNode, falseConstantNode);
    }

    private void removeIfNode(IfNode ifNode) {
        FixedNode next = merge().next();
        MergeNode merge = this.merge;
        EndNode end1 = merge.endAt(0);
        EndNode end2 = merge.endAt(1);
        BeginNode trueSuccessor = ifNode.trueSuccessor();
        BeginNode falseSuccessor = ifNode.falseSuccessor();
        merge().setNext(null);
        ifNode.setTrueSuccessor(null);
        ifNode.setFalseSuccessor(null);
        ifNode.replaceAndDelete(next);
        updateUsages(this.merge, null);
        this.merge = null;
        merge.safeDelete();
        trueSuccessor.safeDelete();
        falseSuccessor.safeDelete();
        end1.safeDelete();
        end2.safeDelete();
    }

    public ValueNode firstValue() {
        return valueAt(0);
    }

    public boolean isLoopPhi() {
        return merge() instanceof LoopBeginNode;
    }
}
