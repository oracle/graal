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
package com.oracle.graal.nodes.java;

import java.util.*;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.type.*;
import com.oracle.graal.nodes.util.*;

/**
 * The {@code NewInstanceNode} represents the allocation of an instance class object.
 */
@NodeInfo(nameTemplate = "New {p#instanceClass/s}")
public final class NewInstanceNode extends FixedWithNextNode implements EscapeAnalyzable, Lowerable, LIRLowerable, Node.IterableNodeType {

    private final ResolvedJavaType instanceClass;

    /**
     * Constructs a NewInstanceNode.
     * @param type the class being allocated
     */
    public NewInstanceNode(ResolvedJavaType type) {
        super(StampFactory.exactNonNull(type));
        this.instanceClass = type;
    }

    /**
     * Gets the instance class being allocated by this node.
     * @return the instance class allocated
     */
    public ResolvedJavaType instanceClass() {
        return instanceClass;
    }

    @Override
    public void lower(LoweringTool tool) {
        tool.getRuntime().lower(this, tool);
    }

    @Override
    public void generate(LIRGeneratorTool gen) {
        gen.visitNewInstance(this);
    }

    public EscapeOp getEscapeOp() {
        return instanceClass == null ? null : ESCAPE;
    }

    private static final EscapeOp ESCAPE = new EscapeOp() {

        @Override
        public boolean canAnalyze(Node node) {
            return true;
        }

        private void fillEscapeFields(ResolvedJavaType type, List<EscapeField> escapeFields) {
            if (type != null) {
                fillEscapeFields(type.superType(), escapeFields);
                JavaField[] declaredFields = type.declaredFields();
                assert declaredFields != null : "the runtime must specify the declared fields of that type";
                for (JavaField field : declaredFields) {
                    escapeFields.add(new EscapeField(field.name(), field, field.type()));
                }
            }
        }

        @Override
        public ResolvedJavaType type(Node node) {
            NewInstanceNode x = (NewInstanceNode) node;
            return x.instanceClass();
        }

        @Override
        public EscapeField[] fields(Node node) {
            NewInstanceNode x = (NewInstanceNode) node;
            List<EscapeField> escapeFields = new ArrayList<>();
            fillEscapeFields(x.instanceClass(), escapeFields);
            return escapeFields.toArray(new EscapeField[escapeFields.size()]);
        }

        @Override
        public void beforeUpdate(Node node, Node usage) {
            if (usage instanceof RegisterFinalizerNode) {
                RegisterFinalizerNode x = (RegisterFinalizerNode) usage;
                ((StructuredGraph) x.graph()).removeFixed(x);
            } else {
                super.beforeUpdate(node, usage);
            }
        }

        @Override
        public int updateState(Node node, Node current, Map<Object, Integer> fieldIndex, ValueNode[] fieldState) {
            if (current instanceof AccessFieldNode) {
                AccessFieldNode x = (AccessFieldNode) current;
                if (GraphUtil.unProxify(x.object()) == node) {
                    int field = fieldIndex.get(x.field());
                    StructuredGraph graph = (StructuredGraph) x.graph();
                    if (current instanceof LoadFieldNode) {
                        assert fieldState[field] != null : field + ", " + x.field();
                        x.replaceAtUsages(fieldState[field]);
                        graph.removeFixed(x);
                    } else if (current instanceof StoreFieldNode) {
                        fieldState[field] = ((StoreFieldNode) x).value();
                        graph.removeFixed(x);
                        return field;
                    }
                }
            }
            return -1;
        }
    };
}
