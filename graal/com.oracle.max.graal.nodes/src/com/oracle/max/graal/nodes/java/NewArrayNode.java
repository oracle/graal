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
import com.oracle.max.graal.nodes.virtual.*;
import com.sun.cri.ci.*;

/**
 * The {@code NewArray} class is the base of all instructions that allocate arrays.
 */
public abstract class NewArrayNode extends FixedWithNextNode {

    @Input private ValueNode length;

    public static final int MaximumEscapeAnalysisArrayLength = 32;

    public ValueNode length() {
        return length;
    }

    public void setLength(ValueNode x) {
        updateUsages(this.length, x);
        this.length = x;
    }

    /**
     * Constructs a new NewArray instruction.
     * @param length the instruction that produces the length for this allocation
     * @param graph
     */
    protected NewArrayNode(ValueNode length, Graph graph) {
        super(CiKind.Object, graph);
        setLength(length);
    }

    /**
     * The list of instructions which produce input for this instruction.
     */
    public ValueNode dimension(int index) {
        assert index == 0;
        return length();
    }

    /**
     * The rank of the array allocated by this instruction, i.e. how many array dimensions.
     */
    public int dimensionCount() {
        return 1;
    }

    public abstract CiKind elementKind();

    @Override
    public Map<Object, Object> getDebugProperties() {
        Map<Object, Object> properties = super.getDebugProperties();
        properties.put("exactType", exactType());
        return properties;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T extends Op> T lookup(Class<T> clazz) {
        if (clazz == EscapeOp.class) {
            return (T) ESCAPE;
        }
        return super.lookup(clazz);
    }

    private static final EscapeOp ESCAPE = new EscapeOp() {

        @Override
        public boolean canAnalyze(Node node) {
            NewArrayNode x = (NewArrayNode) node;
            CiConstant length = x.dimension(0).asConstant();
            return length != null && length.asInt() >= 0 && length.asInt() < MaximumEscapeAnalysisArrayLength;
        }

        @Override
        public boolean escape(Node node, Node usage) {
            if (usage instanceof LoadIndexedNode) {
                LoadIndexedNode x = (LoadIndexedNode) usage;
                assert x.array() == node;
                CiConstant index = x.index().asConstant();
                CiConstant length = ((NewArrayNode) node).dimension(0).asConstant();
                if (index == null || length == null || index.asInt() < 0 || index.asInt() >= length.asInt()) {
                    return true;
                }
                return false;
            } else if (usage instanceof StoreFieldNode) {
                StoreFieldNode x = (StoreFieldNode) usage;
                assert x.value() == node;
                return true;
            } else if (usage instanceof StoreIndexedNode) {
                StoreIndexedNode x = (StoreIndexedNode) usage;
                CiConstant index = x.index().asConstant();
                CiConstant length = ((NewArrayNode) node).dimension(0).asConstant();
                if (index == null || length == null || index.asInt() < 0 || index.asInt() >= length.asInt()) {
                    return true;
                }
                return x.value() == node && x.array() != node;
            } else if (usage instanceof ArrayLengthNode) {
                ArrayLengthNode x = (ArrayLengthNode) usage;
                assert x.array() == node;
                return false;
            } else if (usage instanceof VirtualObjectFieldNode) {
                return false;
            } else {
                return super.escape(node, usage);
            }
        }

        @Override
        public EscapeField[] fields(Node node) {
            NewArrayNode x = (NewArrayNode) node;
            int length = x.dimension(0).asConstant().asInt();
            EscapeField[] fields = new EscapeField[length];
            for (int i = 0; i < length; i++) {
                Integer representation = i;
                fields[i] = new EscapeField("[" + i + "]", representation, ((NewArrayNode) node).elementKind());
            }
            return fields;
        }

        @Override
        public void beforeUpdate(Node node, Node usage) {
            if (usage instanceof ArrayLengthNode) {
                ArrayLengthNode x = (ArrayLengthNode) usage;
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
