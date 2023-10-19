/*
 * Copyright (c) 2018, 2021, Oracle and/or its affiliates. All rights reserved.
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

import jdk.compiler.graal.core.common.type.Stamp;
import jdk.compiler.graal.graph.Node;
import jdk.compiler.graal.graph.NodeClass;
import jdk.compiler.graal.nodes.spi.Canonicalizable;
import jdk.compiler.graal.nodes.spi.CanonicalizerTool;
import jdk.compiler.graal.nodeinfo.NodeInfo;
import jdk.compiler.graal.nodes.ConstantNode;
import jdk.compiler.graal.nodes.FixedWithNextNode;
import jdk.compiler.graal.nodes.ValueNode;
import jdk.compiler.graal.nodes.spi.Lowerable;
import jdk.compiler.graal.nodes.spi.StampProvider;

import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Loads the component hub for of the provided array hub.
 *
 * This is a fixed node because on certain VMs, for example the HotSpot VM, the read is only valid
 * when the provided hub is actually an array hub (and not, e.g., a primitive hub), so this node
 * must not float above a possible type check. Properly guarding this node would be possible too,
 * but it is unlikely that the guard would be more flexible than just fixing the node in the control
 * flow.
 */
@NodeInfo(cycles = CYCLES_2, size = SIZE_1)
public final class LoadArrayComponentHubNode extends FixedWithNextNode implements Lowerable, Canonicalizable.Unary<ValueNode> {

    public static final NodeClass<LoadArrayComponentHubNode> TYPE = NodeClass.create(LoadArrayComponentHubNode.class);

    private @Input ValueNode value;

    public static ValueNode create(ValueNode value, StampProvider stampProvider, MetaAccessProvider metaAccess, ConstantReflectionProvider constantReflection) {
        Stamp stamp = stampProvider.createHubStamp(null);
        return findSynonym(null, value, stamp, metaAccess, constantReflection);
    }

    protected LoadArrayComponentHubNode(Stamp stamp, ValueNode value) {
        super(TYPE, stamp);
        this.value = value;
    }

    @Override
    public ValueNode getValue() {
        return value;
    }

    @Override
    public Node canonical(CanonicalizerTool tool, ValueNode forValue) {
        return findSynonym(this, forValue, stamp, tool.getMetaAccess(), tool.getConstantReflection());
    }

    private static ValueNode findSynonym(LoadArrayComponentHubNode self, ValueNode forValue, Stamp stamp, MetaAccessProvider metaAccess, ConstantReflectionProvider constantReflection) {
        if (forValue.isConstant()) {
            ResolvedJavaType type = constantReflection.asJavaType(forValue.asConstant());
            if (type != null) {
                return ConstantNode.forConstant(stamp, constantReflection.asObjectHub(type.getComponentType()), metaAccess);
            }
        }
        return self != null ? self : new LoadArrayComponentHubNode(stamp, forValue);
    }
}
