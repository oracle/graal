/*
 * Copyright (c) 2011, 2022, Oracle and/or its affiliates. All rights reserved.
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

import jdk.compiler.graal.core.common.type.AbstractPointerStamp;
import jdk.compiler.graal.core.common.type.ObjectStamp;
import jdk.compiler.graal.core.common.type.Stamp;
import jdk.compiler.graal.core.common.type.TypeReference;
import jdk.compiler.graal.graph.NodeClass;
import jdk.compiler.graal.nodeinfo.NodeInfo;
import jdk.compiler.graal.nodes.ConstantNode;
import jdk.compiler.graal.nodes.NodeView;
import jdk.compiler.graal.nodes.ValueNode;
import jdk.compiler.graal.nodes.calc.FloatingNode;
import jdk.compiler.graal.nodes.spi.Canonicalizable;
import jdk.compiler.graal.nodes.spi.CanonicalizerTool;
import jdk.compiler.graal.nodes.spi.Lowerable;
import jdk.compiler.graal.nodes.spi.StampProvider;
import jdk.compiler.graal.nodes.spi.Virtualizable;
import jdk.compiler.graal.nodes.spi.VirtualizerTool;
import jdk.compiler.graal.nodes.type.StampTool;

import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.MetaAccessProvider;

/**
 * Loads an object's hub, or null if the object is null.
 */
@NodeInfo(cycles = CYCLES_2, size = SIZE_1)
public final class LoadHubOrNullNode extends FloatingNode implements Lowerable, Canonicalizable, Virtualizable {

    public static final NodeClass<LoadHubOrNullNode> TYPE = NodeClass.create(LoadHubOrNullNode.class);
    @Input ValueNode value;

    public ValueNode getValue() {
        return value;
    }

    private static AbstractPointerStamp hubStamp(StampProvider stampProvider, ValueNode value) {
        assert value.stamp(NodeView.DEFAULT) instanceof ObjectStamp;
        return stampProvider.createHubStamp(((ObjectStamp) value.stamp(NodeView.DEFAULT))).asMaybeNull();
    }

    public LoadHubOrNullNode(@InjectedNodeParameter StampProvider stampProvider, ValueNode value) {
        this(hubStamp(stampProvider, value), value);
    }

    public LoadHubOrNullNode(Stamp stamp, ValueNode value) {
        super(TYPE, stamp);
        this.value = value;
    }

    @Override
    public ValueNode canonical(CanonicalizerTool tool) {
        NodeView view = NodeView.from(tool);
        MetaAccessProvider metaAccess = tool.getMetaAccess();
        ValueNode curValue = getValue();
        ValueNode newNode = findSynonym(curValue, (AbstractPointerStamp) stamp(view), metaAccess, tool.getConstantReflection());
        if (newNode != null) {
            return newNode;
        }
        return this;
    }

    public static ValueNode findSynonym(ValueNode curValue, AbstractPointerStamp stamp, MetaAccessProvider metaAccess, ConstantReflectionProvider constantReflection) {
        if (StampTool.isPointerNonNull(stamp)) {
            return LoadHubNode.create(curValue, stamp.asNonNull(), metaAccess, constantReflection);
        }
        return null;
    }

    @Override
    public void virtualize(VirtualizerTool tool) {
        ValueNode alias = tool.getAlias(getValue());
        TypeReference type = StampTool.typeReferenceOrNull(alias);
        if (type != null && type.isExact()) {
            tool.replaceWithValue(ConstantNode.forConstant(stamp(NodeView.DEFAULT), tool.getConstantReflection().asObjectHub(type.getType()), tool.getMetaAccess(), graph()));
        }
    }
}
