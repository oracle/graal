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
package com.oracle.graal.nodes.java;

import static com.oracle.graal.graph.iterators.NodePredicates.*;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.common.type.*;
import com.oracle.graal.graph.spi.*;
import com.oracle.graal.nodeinfo.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.type.*;
import com.oracle.graal.nodes.virtual.*;

/**
 * The {@code LoadFieldNode} represents a read of a static or instance field.
 */
@NodeInfo(nameTemplate = "LoadField#{p#field/s}")
public class LoadFieldNode extends AccessFieldNode implements Canonicalizable.Unary<ValueNode>, VirtualizableRoot {

    /**
     * Creates a new LoadFieldNode instance.
     *
     * @param object the receiver object
     * @param field the compiler interface field
     */
    public static LoadFieldNode create(ValueNode object, ResolvedJavaField field) {
        return new LoadFieldNodeGen(object, field);
    }

    protected LoadFieldNode(ValueNode object, ResolvedJavaField field) {
        super(createStamp(field), object, field);
    }

    public ValueNode getValue() {
        return object();
    }

    private static Stamp createStamp(ResolvedJavaField field) {
        Kind kind = field.getKind();
        if (kind == Kind.Object && field.getType() instanceof ResolvedJavaType) {
            return StampFactory.declared((ResolvedJavaType) field.getType());
        } else {
            return StampFactory.forKind(kind);
        }
    }

    public ValueNode canonical(CanonicalizerTool tool, ValueNode forObject) {
        if (usages().isEmpty() && !isVolatile() && (isStatic() || StampTool.isObjectNonNull(forObject.stamp()))) {
            return null;
        }
        MetaAccessProvider metaAccess = tool.getMetaAccess();
        if (tool.canonicalizeReads() && metaAccess != null) {
            ConstantNode constant = asConstant(metaAccess, forObject);
            if (constant != null) {
                return constant;
            }
            PhiNode phi = asPhi(metaAccess, forObject);
            if (phi != null) {
                return phi;
            }
        }
        if (!isStatic() && forObject.isNullConstant()) {
            return DeoptimizeNode.create(DeoptimizationAction.None, DeoptimizationReason.NullCheckException);
        }
        return this;
    }

    /**
     * Gets a constant value for this load if possible.
     */
    public ConstantNode asConstant(MetaAccessProvider metaAccess, ValueNode forObject) {
        Constant constant = null;
        if (isStatic()) {
            constant = field().readConstantValue(null);
        } else if (forObject.isConstant() && !forObject.isNullConstant()) {
            constant = field().readConstantValue(forObject.asConstant());
        }
        if (constant != null) {
            return ConstantNode.forConstant(constant, metaAccess);
        }
        return null;
    }

    private PhiNode asPhi(MetaAccessProvider metaAccess, ValueNode forObject) {
        if (!isStatic() && field.isFinal() && forObject instanceof ValuePhiNode && ((ValuePhiNode) forObject).values().filter(isNotA(ConstantNode.class)).isEmpty()) {
            PhiNode phi = (PhiNode) forObject;
            Constant[] constants = new Constant[phi.valueCount()];
            for (int i = 0; i < phi.valueCount(); i++) {
                Constant constantValue = field().readConstantValue(phi.valueAt(i).asConstant());
                if (constantValue == null) {
                    return null;
                }
                constants[i] = constantValue;
            }
            ConstantNode[] constantNodes = new ConstantNode[phi.valueCount()];
            for (int i = 0; i < phi.valueCount(); i++) {
                constantNodes[i] = ConstantNode.forConstant(constants[i], metaAccess);
            }
            return ValuePhiNode.create(stamp(), phi.merge(), constantNodes);
        }
        return null;
    }

    @Override
    public void virtualize(VirtualizerTool tool) {
        State state = tool.getObjectState(object());
        if (state != null && state.getState() == EscapeState.Virtual) {
            int fieldIndex = ((VirtualInstanceNode) state.getVirtualObject()).fieldIndex(field());
            if (fieldIndex != -1) {
                tool.replaceWith(state.getEntry(fieldIndex));
            }
        }
    }
}
