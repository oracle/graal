/*
 * Copyright (c) 2011, 2021, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package jdk.compiler.graal.nodes.extended;

import static jdk.compiler.graal.nodeinfo.NodeCycles.CYCLES_2;
import static jdk.compiler.graal.nodeinfo.NodeSize.SIZE_1;

import jdk.compiler.graal.core.common.type.ObjectStamp;
import jdk.compiler.graal.core.common.type.Stamp;
import jdk.compiler.graal.core.common.type.TypeReference;
import jdk.compiler.graal.graph.NodeClass;
import jdk.compiler.graal.nodeinfo.NodeInfo;
import jdk.compiler.graal.nodes.virtual.VirtualObjectNode;
import jdk.compiler.graal.nodes.ConstantNode;
import jdk.compiler.graal.nodes.NodeView;
import jdk.compiler.graal.nodes.ValueNode;
import jdk.compiler.graal.nodes.calc.FloatingNode;
import jdk.compiler.graal.nodes.spi.Canonicalizable;
import jdk.compiler.graal.nodes.spi.CanonicalizerTool;
import jdk.compiler.graal.nodes.spi.Lowerable;
import jdk.compiler.graal.nodes.spi.Virtualizable;
import jdk.compiler.graal.nodes.spi.VirtualizerTool;

import jdk.vm.ci.meta.Assumptions;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.MetaAccessProvider;

/**
 * Loads an object's class (i.e., this node can be created for {@code object.getClass()}).
 */
@NodeInfo(cycles = CYCLES_2, size = SIZE_1)
public final class GetClassNode extends FloatingNode implements Lowerable, Canonicalizable, Virtualizable {

    public static final NodeClass<GetClassNode> TYPE = NodeClass.create(GetClassNode.class);
    @Input ValueNode object;

    public ValueNode getObject() {
        return object;
    }

    public GetClassNode(Stamp stamp, ValueNode object) {
        super(TYPE, stamp);
        this.object = object;
        assert ((ObjectStamp) object.stamp(NodeView.DEFAULT)).nonNull();
    }

    public static ValueNode tryFold(Assumptions assumptions, MetaAccessProvider metaAccess, ConstantReflectionProvider constantReflection, NodeView view, ValueNode object) {
        if (metaAccess != null && object != null && object.stamp(view) instanceof ObjectStamp) {
            ObjectStamp objectStamp = (ObjectStamp) object.stamp(view);
            if (objectStamp.isExactType()) {
                return ConstantNode.forConstant(constantReflection.asJavaClass(objectStamp.type()), metaAccess);
            }
            TypeReference maybeExactType = TypeReference.createTrusted(assumptions, objectStamp.type());
            if (maybeExactType != null && maybeExactType.isExact()) {
                return ConstantNode.forConstant(constantReflection.asJavaClass(maybeExactType.getType()), metaAccess);
            }
        }
        return null;
    }

    @Override
    public ValueNode canonical(CanonicalizerTool tool) {
        NodeView view = NodeView.from(tool);
        ValueNode folded = tryFold(tool.getAssumptions(), tool.getMetaAccess(), tool.getConstantReflection(), view, getObject());
        return folded == null ? this : folded;
    }

    @Override
    public void virtualize(VirtualizerTool tool) {
        ValueNode alias = tool.getAlias(getObject());
        if (alias instanceof VirtualObjectNode) {
            VirtualObjectNode virtual = (VirtualObjectNode) alias;
            Constant javaClass = tool.getConstantReflection().asJavaClass(virtual.type());
            tool.replaceWithValue(ConstantNode.forConstant(stamp(NodeView.DEFAULT), javaClass, tool.getMetaAccess(), graph()));
        }
    }
}
