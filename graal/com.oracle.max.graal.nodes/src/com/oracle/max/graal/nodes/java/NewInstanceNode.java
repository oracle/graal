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
import com.sun.cri.ri.*;

/**
 * The {@code NewInstance} instruction represents the allocation of an instance class object.
 */
public final class NewInstanceNode extends FixedWithNextNode {

    final RiType instanceClass;
    public final int cpi;
    public final RiConstantPool constantPool;

    /**
     * Constructs a NewInstance instruction.
     * @param type the class being allocated
     * @param cpi the constant pool index
     * @param graph
     */
    public NewInstanceNode(RiType type, int cpi, RiConstantPool constantPool, Graph graph) {
        super(CiKind.Object, graph);
        this.instanceClass = type;
        this.cpi = cpi;
        this.constantPool = constantPool;
    }

    /**
     * Gets the instance class being allocated by this instruction.
     * @return the instance class allocated
     */
    public RiType instanceClass() {
        return instanceClass;
    }

    /**
     * Gets the exact type produced by this instruction. For allocations of instance classes, this is
     * always the class allocated.
     * @return the exact type produced by this instruction
     */
    @Override
    public RiType exactType() {
        return instanceClass;
    }

    @Override
    public void accept(ValueVisitor v) {
        v.visitNewInstance(this);
    }

    @Override
    public Map<Object, Object> getDebugProperties() {
        Map<Object, Object> properties = super.getDebugProperties();
        properties.put("instanceClass", instanceClass);
        properties.put("cpi", cpi);
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
            return ((NewInstanceNode) node).instanceClass().isResolved();
        }

        @Override
        public boolean escape(Node node, Node usage) {
            if (usage instanceof LoadFieldNode) {
                LoadFieldNode x = (LoadFieldNode) usage;
                assert x.object() == node;
                return x.field().isResolved() == false;
            } else if (usage instanceof StoreFieldNode) {
                StoreFieldNode x = (StoreFieldNode) usage;
                return x.value() == node && x.object() != node;
            } else if (usage instanceof StoreIndexedNode) {
                StoreIndexedNode x = (StoreIndexedNode) usage;
                assert x.value() == node;
                return true;
            } else if (usage instanceof VirtualObjectFieldNode) {
                return false;
            } else if (usage instanceof RegisterFinalizerNode) {
                RegisterFinalizerNode x = (RegisterFinalizerNode) usage;
                assert x.object() == node;
                return false;
            } else {
                return super.escape(node, usage);
            }
        }

        @Override
        public EscapeField[] fields(Node node) {
            NewInstanceNode x = (NewInstanceNode) node;
            RiField[] riFields = x.instanceClass().fields();
            EscapeField[] fields = new EscapeField[riFields.length];
            for (int i = 0; i < riFields.length; i++) {
                RiField field = riFields[i];
                fields[i] = new EscapeField(field.name(), field, field.kind().stackKind());
            }
            return fields;
        }

        @Override
        public void beforeUpdate(Node node, Node usage) {
            if (usage instanceof RegisterFinalizerNode) {
                RegisterFinalizerNode x = (RegisterFinalizerNode) usage;
                x.replaceAndDelete(x.next());
            } else {
                super.beforeUpdate(node, usage);
            }
        }

        @Override
        public int updateState(Node node, Node current, Map<Object, Integer> fieldIndex, ValueNode[] fieldState) {
            if (current instanceof AccessFieldNode) {
                AccessFieldNode x = (AccessFieldNode) current;
                if (x.object() == node) {
                    int field = fieldIndex.get(((AccessFieldNode) current).field());
                    if (current instanceof LoadFieldNode) {
                        assert fieldState[field] != null : field + ", " + ((AccessFieldNode) current).field();
                        x.replaceAtUsages(fieldState[field]);
                        assert x.usages().size() == 0;
                        x.replaceAndDelete(x.next());
                    } else if (current instanceof StoreFieldNode) {
                        fieldState[field] = ((StoreFieldNode) x).value();
                        assert x.usages().size() == 0;
                        x.replaceAndDelete(x.next());
                        return field;
                    }
                }
            }
            return -1;
        }
    };
}
