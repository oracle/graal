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

import com.oracle.graal.graph.*;
import com.oracle.graal.graph.spi.*;
import com.oracle.graal.nodes.calc.*;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.nodes.type.*;

/**
 * The {@code PhiNode} represents the merging of dataflow in the graph. It refers to a merge and a
 * variable.
 */
@NodeInfo(nameTemplate = "{p#type/s}Phi({i#values})")
public class PhiNode extends FloatingNode implements Canonicalizable, GuardingNode {

    public static enum PhiType {
        Value(null), // normal value phis
        Guard(StampFactory.dependency()),
        Memory(StampFactory.dependency());

        public final Stamp stamp;

        PhiType(Stamp stamp) {
            this.stamp = stamp;
        }
    }

    @Input(notDataflow = true) private MergeNode merge;
    @Input private final NodeInputList<ValueNode> values = new NodeInputList<>(this);
    private final PhiType type;

    /**
     * Create a value phi ({@link PhiType#Value}) with the specified stamp.
     * 
     * @param stamp the stamp of the value
     * @param merge the merge that the new phi belongs to
     */
    public PhiNode(Stamp stamp, MergeNode merge) {
        super(stamp);
        assert stamp != StampFactory.forVoid();
        this.type = PhiType.Value;
        this.merge = merge;
    }

    /**
     * Create a non-value phi ({@link PhiType#Memory} with the specified kind.
     * 
     * @param type the type of the new phi
     * @param merge the merge that the new phi belongs to
     */
    public PhiNode(PhiType type, MergeNode merge) {
        super(type.stamp);
        assert type.stamp != null : merge + " " + type;
        this.type = type;
        this.merge = merge;
    }

    public PhiType type() {
        return type;
    }

    public MergeNode merge() {
        return merge;
    }

    public void setMerge(MergeNode x) {
        updateUsages(merge, x);
        merge = x;
    }

    public NodeInputList<ValueNode> values() {
        return values;
    }

    @Override
    public boolean inferStamp() {
        if (type == PhiType.Value) {
            return inferPhiStamp();
        } else {
            return false;
        }
    }

    public boolean inferPhiStamp() {
        return updateStamp(StampTool.meet(values()));
    }

    @Override
    public boolean verify() {
        assertTrue(merge() != null, "missing merge");
        assertTrue(merge().phiPredecessorCount() == valueCount(), "mismatch between merge predecessor count and phi value count: %d != %d", merge().phiPredecessorCount(), valueCount());
        return super.verify();
    }

    /**
     * Get the instruction that produces the value associated with the i'th predecessor of the
     * merge.
     * 
     * @param i the index of the predecessor
     * @return the instruction that produced the value in the i'th predecessor
     */
    public ValueNode valueAt(int i) {
        return values.get(i);
    }

    /**
     * Sets the value at the given index and makes sure that the values list is large enough.
     * 
     * @param i the index at which to set the value
     * @param x the new phi input value for the given location
     */
    public void initializeValueAt(int i, ValueNode x) {
        while (values().size() <= i) {
            values.add(null);
        }
        values.set(i, x);
    }

    public void setValueAt(int i, ValueNode x) {
        values.set(i, x);
    }

    public ValueNode valueAt(AbstractEndNode pred) {
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
        assert !(x instanceof PhiNode) || ((PhiNode) x).merge() instanceof LoopBeginNode || ((PhiNode) x).merge() != this.merge();
        assert x.stamp().isCompatible(stamp()) || type != PhiType.Value;
        values.add(x);
    }

    public void removeInput(int index) {
        values.remove(index);
    }

    public ValueNode singleValue() {
        ValueNode differentValue = null;
        for (ValueNode n : values()) {
            assert n != null : "Must have input value!";
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

    public ValueNode singleBackValue() {
        assert merge() instanceof LoopBeginNode;
        ValueNode differentValue = null;
        for (ValueNode n : values().subList(merge().forwardEndCount(), values().size())) {
            if (differentValue == null) {
                differentValue = n;
            } else if (differentValue != n) {
                return null;
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

        return this;
    }

    public ValueNode firstValue() {
        return valueAt(0);
    }

    public boolean isLoopPhi() {
        return merge() instanceof LoopBeginNode;
    }
}
