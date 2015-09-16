/*
 * Copyright (c) 2011, 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.nodes.calc;

import jdk.internal.jvmci.common.JVMCIError;
import jdk.internal.jvmci.meta.Constant;
import jdk.internal.jvmci.meta.ConstantReflectionProvider;
import jdk.internal.jvmci.meta.JavaConstant;
import jdk.internal.jvmci.meta.JavaKind;
import jdk.internal.jvmci.meta.MetaAccessProvider;
import jdk.internal.jvmci.meta.ResolvedJavaType;

import com.oracle.graal.compiler.common.calc.Condition;
import com.oracle.graal.compiler.common.type.AbstractObjectStamp;
import com.oracle.graal.compiler.common.type.AbstractPointerStamp;
import com.oracle.graal.compiler.common.type.ObjectStamp;
import com.oracle.graal.graph.NodeClass;
import com.oracle.graal.graph.spi.CanonicalizerTool;
import com.oracle.graal.nodeinfo.NodeInfo;
import com.oracle.graal.nodes.ConstantNode;
import com.oracle.graal.nodes.LogicConstantNode;
import com.oracle.graal.nodes.LogicNode;
import com.oracle.graal.nodes.ValueNode;
import com.oracle.graal.nodes.extended.GetClassNode;
import com.oracle.graal.nodes.java.TypeCheckNode;
import com.oracle.graal.nodes.spi.Virtualizable;
import com.oracle.graal.nodes.spi.VirtualizerTool;
import com.oracle.graal.nodes.virtual.VirtualObjectNode;

@NodeInfo(shortName = "==")
public final class ObjectEqualsNode extends PointerEqualsNode implements Virtualizable {

    public static final NodeClass<ObjectEqualsNode> TYPE = NodeClass.create(ObjectEqualsNode.class);

    public ObjectEqualsNode(ValueNode x, ValueNode y) {
        super(TYPE, x, y);
        assert x.stamp() instanceof AbstractObjectStamp;
        assert y.stamp() instanceof AbstractObjectStamp;
    }

    public static LogicNode create(ValueNode x, ValueNode y, ConstantReflectionProvider constantReflection) {
        LogicNode result = CompareNode.tryConstantFold(Condition.EQ, x, y, constantReflection, false);
        if (result != null) {
            return result;
        } else {
            result = findSynonym(x, y);
            if (result != null) {
                return result;
            }
            return new ObjectEqualsNode(x, y);
        }
    }

    @Override
    protected ValueNode canonicalizeSymmetricConstant(CanonicalizerTool tool, Constant constant, ValueNode nonConstant, boolean mirrored) {
        ResolvedJavaType type = tool.getConstantReflection().asJavaType(constant);
        if (type != null && nonConstant instanceof GetClassNode) {
            if (!type.isPrimitive() && (type.isConcrete() || type.isArray())) {
                return TypeCheckNode.create(type, ((GetClassNode) nonConstant).getObject());
            }
            return LogicConstantNode.forBoolean(false);
        }
        return super.canonicalizeSymmetricConstant(tool, constant, nonConstant, mirrored);
    }

    private void virtualizeNonVirtualComparison(VirtualObjectNode virtual, ValueNode other, VirtualizerTool tool) {
        if (!virtual.hasIdentity() && virtual.entryKind(0) == JavaKind.Boolean) {
            if (other.isConstant()) {
                JavaConstant otherUnboxed = tool.getConstantReflectionProvider().unboxPrimitive(other.asJavaConstant());
                if (otherUnboxed != null && otherUnboxed.getJavaKind() == JavaKind.Boolean) {
                    int expectedValue = otherUnboxed.asBoolean() ? 1 : 0;
                    IntegerEqualsNode equals = new IntegerEqualsNode(tool.getEntry(virtual, 0), ConstantNode.forInt(expectedValue, graph()));
                    tool.addNode(equals);
                    tool.replaceWithValue(equals);
                } else {
                    tool.replaceWithValue(LogicConstantNode.contradiction(graph()));
                }
            }
        } else {
            // one of them is virtual: they can never be the same objects
            tool.replaceWithValue(LogicConstantNode.contradiction(graph()));
        }
    }

    @Override
    public void virtualize(VirtualizerTool tool) {
        ValueNode xAlias = tool.getAlias(getX());
        ValueNode yAlias = tool.getAlias(getY());

        VirtualObjectNode xVirtual = xAlias instanceof VirtualObjectNode ? (VirtualObjectNode) xAlias : null;
        VirtualObjectNode yVirtual = yAlias instanceof VirtualObjectNode ? (VirtualObjectNode) yAlias : null;

        if (xVirtual != null && yVirtual == null) {
            virtualizeNonVirtualComparison(xVirtual, yAlias, tool);
        } else if (xVirtual == null && yVirtual != null) {
            virtualizeNonVirtualComparison(yVirtual, xAlias, tool);
        } else if (xVirtual != null && yVirtual != null) {
            if (xVirtual.hasIdentity() ^ yVirtual.hasIdentity()) {
                /*
                 * One of the two objects has identity, the other doesn't. In code, this looks like
                 * "Integer.valueOf(a) == new Integer(b)", which is always false.
                 * 
                 * In other words: an object created via valueOf can never be equal to one created
                 * by new in the same compilation unit.
                 */
                tool.replaceWithValue(LogicConstantNode.contradiction(graph()));
            } else if (!xVirtual.hasIdentity() && !yVirtual.hasIdentity()) {
                ResolvedJavaType type = xVirtual.type();
                if (type.equals(yVirtual.type())) {
                    MetaAccessProvider metaAccess = tool.getMetaAccessProvider();
                    if (type.equals(metaAccess.lookupJavaType(Integer.class)) || type.equals(metaAccess.lookupJavaType(Long.class))) {
                        // both are virtual without identity: check contents
                        assert xVirtual.entryCount() == 1 && yVirtual.entryCount() == 1;
                        assert xVirtual.entryKind(0).getStackKind() == JavaKind.Int || xVirtual.entryKind(0) == JavaKind.Long;
                        IntegerEqualsNode equals = new IntegerEqualsNode(tool.getEntry(xVirtual, 0), tool.getEntry(yVirtual, 0));
                        tool.addNode(equals);
                        tool.replaceWithValue(equals);
                    }
                }
            } else {
                // both are virtual with identity: check if they refer to the same object
                tool.replaceWithValue(LogicConstantNode.forBoolean(xVirtual == yVirtual, graph()));
            }
        }
    }

    @Override
    protected CompareNode duplicateModified(ValueNode newX, ValueNode newY) {
        if (newX.stamp() instanceof ObjectStamp && newY.stamp() instanceof ObjectStamp) {
            return new ObjectEqualsNode(newX, newY);
        } else if (newX.stamp() instanceof AbstractPointerStamp && newY.stamp() instanceof AbstractPointerStamp) {
            return new PointerEqualsNode(newX, newY);
        }
        throw JVMCIError.shouldNotReachHere();
    }
}
