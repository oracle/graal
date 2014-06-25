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

import static com.oracle.graal.graph.iterators.NodePredicates.*;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.common.type.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.graph.spi.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.type.*;
import com.oracle.graal.nodes.virtual.*;

/**
 * The {@code LoadFieldNode} represents a read of a static or instance field.
 */
@NodeInfo(nameTemplate = "LoadField#{p#field/s}")
public final class LoadFieldNode extends AccessFieldNode implements Canonicalizable, VirtualizableRoot {

    /**
     * Creates a new LoadFieldNode instance.
     *
     * @param object the receiver object
     * @param field the compiler interface field
     */
    public LoadFieldNode(ValueNode object, ResolvedJavaField field) {
        super(createStamp(field), object, field);
    }

    private static Stamp createStamp(ResolvedJavaField field) {
        Kind kind = field.getKind();
        if (kind == Kind.Object && field.getType() instanceof ResolvedJavaType) {
            return StampFactory.declared((ResolvedJavaType) field.getType());
        } else {
            return StampFactory.forKind(kind);
        }
    }

    @Override
    public Node canonical(CanonicalizerTool tool) {
        if (usages().isEmpty() && !isVolatile() && (isStatic() || StampTool.isObjectNonNull(object().stamp()))) {
            return null;
        }
        MetaAccessProvider metaAccess = tool.getMetaAccess();
        if (tool.canonicalizeReads() && metaAccess != null) {
            ConstantNode constant = asConstant(metaAccess);
            if (constant != null) {
                return constant;
            }
            PhiNode phi = asPhi(metaAccess);
            if (phi != null) {
                return phi;
            }
        }
        if (!isStatic() && object().isNullConstant()) {
            return graph().add(new DeoptimizeNode(DeoptimizationAction.None, DeoptimizationReason.NullCheckException));
        }
        return this;
    }

    /**
     * Gets a constant value for this load if possible.
     */
    public ConstantNode asConstant(MetaAccessProvider metaAccess) {
        Constant constant = null;
        if (isStatic()) {
            constant = field().readConstantValue(null);
        } else if (object().isConstant() && !object().isNullConstant()) {
            constant = field().readConstantValue(object().asConstant());
        }
        if (constant != null) {
            return ConstantNode.forConstant(constant, metaAccess, graph());
        }
        return null;
    }

    private PhiNode asPhi(MetaAccessProvider metaAccess) {
        if (!isStatic() && field.isFinal() && object() instanceof ValuePhiNode && ((ValuePhiNode) object()).values().filter(isNotA(ConstantNode.class)).isEmpty()) {
            PhiNode phi = (PhiNode) object();
            Constant[] constants = new Constant[phi.valueCount()];
            for (int i = 0; i < phi.valueCount(); i++) {
                Constant constantValue = field().readConstantValue(phi.valueAt(i).asConstant());
                if (constantValue == null) {
                    return null;
                }
                constants[i] = constantValue;
            }
            PhiNode newPhi = graph().addWithoutUnique(new ValuePhiNode(stamp(), phi.merge()));
            for (int i = 0; i < phi.valueCount(); i++) {
                newPhi.addInput(ConstantNode.forConstant(constants[i], metaAccess, graph()));
            }
            return newPhi;
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
