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
import com.sun.cri.ri.*;

/**
 * The {@code NewInstanceNode} represents the allocation of an instance class object.
 */
public final class NewInstanceNode extends FixedWithNextNode implements EscapeAnalyzable, LIRLowerable, Node.IterableNodeType {

    private final RiResolvedType instanceClass;

    /**
     * Constructs a NewInstanceNode.
     * @param type the class being allocated
     */
    public NewInstanceNode(RiResolvedType type) {
        super(StampFactory.exactNonNull(type));
        this.instanceClass = type;
    }

    /**
     * Gets the instance class being allocated by this node.
     * @return the instance class allocated
     */
    public RiResolvedType instanceClass() {
        return instanceClass;
    }

    @Override
    public void generate(LIRGeneratorTool gen) {
        gen.visitNewInstance(this);
    }

    @Override
    public Map<Object, Object> getDebugProperties() {
        Map<Object, Object> properties = super.getDebugProperties();
        properties.put("instanceClass", instanceClass);
        return properties;
    }

    public EscapeOp getEscapeOp() {
        return ESCAPE;
    }

    private static final EscapeOp ESCAPE = new EscapeOp() {

        @Override
        public boolean canAnalyze(Node node) {
            return true;
        }

        private void fillEscapeFields(RiResolvedType type, List<EscapeField> escapeFields) {
            if (type != null) {
                fillEscapeFields(type.superType(), escapeFields);
                RiField[] declaredFields = type.declaredFields();
                assert declaredFields != null : "the runtime must specify the declared fields of that type";
                for (RiField field : declaredFields) {
                    escapeFields.add(new EscapeField(field.name(), field, field.type()));
                }
            }
        }

        @Override
        public EscapeField[] fields(Node node) {
            NewInstanceNode x = (NewInstanceNode) node;
            List<EscapeField> escapeFields = new ArrayList<EscapeField>();
            fillEscapeFields(x.instanceClass(), escapeFields);
            return escapeFields.toArray(new EscapeField[escapeFields.size()]);
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
