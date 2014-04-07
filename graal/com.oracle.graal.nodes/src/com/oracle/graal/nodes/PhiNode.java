/*
 * Copyright (c) 2009, 2014, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.graal.nodes.type.*;

public abstract class PhiNode extends FloatingNode {

    @Input(InputType.Association) private MergeNode merge;

    protected PhiNode(Stamp stamp, MergeNode merge) {
        super(stamp);
        this.merge = merge;
    }

    public abstract NodeInputList<ValueNode> values();

    public MergeNode merge() {
        return merge;
    }

    public void setMerge(MergeNode x) {
        updateUsages(merge, x);
        merge = x;
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
        return values().get(i);
    }

    /**
     * Sets the value at the given index and makes sure that the values list is large enough.
     *
     * @param i the index at which to set the value
     * @param x the new phi input value for the given location
     */
    public void initializeValueAt(int i, ValueNode x) {
        while (values().size() <= i) {
            values().add(null);
        }
        values().set(i, x);
    }

    public void setValueAt(int i, ValueNode x) {
        values().set(i, x);
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
        return values().size();
    }

    public void clearValues() {
        values().clear();
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
            return super.toString(Verbosity.Name) + "(" + str + ")";
        } else {
            return super.toString(verbosity);
        }
    }

    public void addInput(ValueNode x) {
        assert !(x instanceof ValuePhiNode) || ((ValuePhiNode) x).merge() instanceof LoopBeginNode || ((ValuePhiNode) x).merge() != this.merge();
        assert !(this instanceof ValuePhiNode) || x.stamp().isCompatible(stamp());
        values().add(x);
    }

    public void removeInput(int index) {
        values().remove(index);
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
