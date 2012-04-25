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
package com.oracle.graal.nodes;

import com.oracle.max.cri.ci.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.nodes.calc.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.type.*;

/**
 * The {@code PhiNode} represents the merging of dataflow in the graph. It refers to a merge
 * and a variable.
 */
public final class PhiNode extends FloatingNode implements Canonicalizable, Node.IterableNodeType {
    public static enum PhiType {
        Value, // normal value phis
        Memory, // memory phis
        Virtual // phis used for VirtualObjectField merges
    }

    @Input(notDataflow = true) private MergeNode merge;
    @Input private final NodeInputList<ValueNode> values = new NodeInputList<>(this);
    private final PhiType type;

    public PhiNode(CiKind kind, MergeNode merge, PhiType type) {
        super(StampFactory.forKind(kind));
        this.type = type;
        this.merge = merge;
    }

    public PhiType type() {
        return type;
    }

    public MergeNode merge() {
        return merge;
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

    public ValueNode valueAt(EndNode pred) {
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
    public ValueNode canonical(CanonicalizerTool tool) {
        ValueNode singleValue = singleValue();

        if (singleValue != null) {
            return singleValue;
        }

        return this;
    }

    public ValueNode firstValue() {
        return valueAt(0);
    }

    public boolean isLoopPhi() {
        return merge() instanceof LoopBeginNode;
    }
}
