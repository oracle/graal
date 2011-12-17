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
package com.oracle.max.graal.nodes.java;

import java.util.*;

import com.oracle.max.graal.graph.*;
import com.oracle.max.graal.nodes.*;
import com.oracle.max.graal.nodes.spi.*;
import com.oracle.max.graal.nodes.type.*;
import com.sun.cri.ci.*;
import com.sun.cri.ri.*;

/**
 * The {@code NewArrayNode} class is the base of all instructions that allocate arrays.
 */
public abstract class NewArrayNode extends FixedWithNextNode implements EscapeAnalyzable{

    @Input private ValueNode length;

    public static final int MaximumEscapeAnalysisArrayLength = 32;

    public ValueNode length() {
        return length;
    }

    /**
     * Constructs a new NewArrayNode.
     * @param length the node that produces the length for this allocation
     */
    protected NewArrayNode(Stamp stamp, ValueNode length) {
        super(stamp);
        this.length = length;
    }

    /**
     * The list of node which produce input for this instruction.
     */
    public ValueNode dimension(int index) {
        assert index == 0;
        return length();
    }

    /**
     * The rank of the array allocated by this node, i.e. how many array dimensions.
     */
    public int dimensionCount() {
        return 1;
    }

    /**
     * Gets the element type of the array.
     * @return the element type of the array
     */
    public abstract RiResolvedType elementType();

    @Override
    public Map<Object, Object> getDebugProperties() {
        Map<Object, Object> properties = super.getDebugProperties();
        properties.put("exactType", exactType());
        return properties;
    }

    public EscapeOp getEscapeOp() {
        return ESCAPE;
    }

    private static final EscapeOp ESCAPE = new EscapeOp() {

        @Override
        public boolean canAnalyze(Node node) {
            NewArrayNode x = (NewArrayNode) node;
            CiConstant length = x.dimension(0).asConstant();
            return length != null && length.asInt() >= 0 && length.asInt() < MaximumEscapeAnalysisArrayLength;
        }

        @Override
        public EscapeField[] fields(Node node) {
            NewArrayNode x = (NewArrayNode) node;
            int length = x.dimension(0).asConstant().asInt();
            EscapeField[] fields = new EscapeField[length];
            for (int i = 0; i < length; i++) {
                Integer representation = i;
                fields[i] = new EscapeField("[" + i + "]", representation, ((NewArrayNode) node).elementType());
            }
            return fields;
        }

        @Override
        public void beforeUpdate(Node node, Node usage) {
            if (usage instanceof ArrayLengthNode) {
                ArrayLengthNode x = (ArrayLengthNode) usage;
                FixedNode next = x.next();
                x.setNext(null);
                x.replaceAtPredecessors(next);
                x.replaceAndDelete(((NewArrayNode) node).dimension(0));
            } else {
                super.beforeUpdate(node, usage);
            }
        }

        @Override
        public int updateState(Node node, Node current, Map<Object, Integer> fieldIndex, ValueNode[] fieldState) {
            if (current instanceof AccessIndexedNode) {
                AccessIndexedNode x = (AccessIndexedNode) current;
                if (x.array() == node) {
                    int index = ((AccessIndexedNode) current).index().asConstant().asInt();
                    if (current instanceof LoadIndexedNode) {
                        x.replaceAtUsages(fieldState[index]);
                        assert x.usages().size() == 0;
                        x.replaceAndDelete(x.next());
                    } else if (current instanceof StoreIndexedNode) {
                        fieldState[index] = ((StoreIndexedNode) x).value();
                        assert x.usages().size() == 0;
                        x.replaceAndDelete(x.next());
                        return index;
                    }
                }
            }
            return -1;
        }
    };
}
