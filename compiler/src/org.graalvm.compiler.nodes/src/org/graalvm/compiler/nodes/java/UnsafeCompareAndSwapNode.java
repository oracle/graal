/*
 * Copyright (c) 2011, 2016, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.nodes.java;

import static org.graalvm.compiler.core.common.calc.CanonicalCondition.EQ;
import static org.graalvm.compiler.nodeinfo.InputType.Memory;
import static org.graalvm.compiler.nodeinfo.InputType.Value;
import static org.graalvm.compiler.nodeinfo.NodeCycles.CYCLES_8;
import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_8;

import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.LogicConstantNode;
import org.graalvm.compiler.nodes.LogicNode;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.CompareNode;
import org.graalvm.compiler.nodes.calc.ConditionalNode;
import org.graalvm.compiler.nodes.memory.AbstractMemoryCheckpoint;
import org.graalvm.compiler.nodes.memory.MemoryCheckpoint;
import org.graalvm.compiler.nodes.spi.Lowerable;
import org.graalvm.compiler.nodes.spi.LoweringTool;
import org.graalvm.compiler.nodes.spi.Virtualizable;
import org.graalvm.compiler.nodes.spi.VirtualizerTool;
import org.graalvm.compiler.nodes.virtual.VirtualInstanceNode;
import org.graalvm.compiler.nodes.virtual.VirtualObjectNode;
import org.graalvm.word.LocationIdentity;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaField;

/**
 * Represents an atomic compare-and-swap operation. The result is a boolean that contains whether
 * the value matched the expected value.
 */
@NodeInfo(allowedUsageTypes = {Value, Memory}, cycles = CYCLES_8, size = SIZE_8)
public final class UnsafeCompareAndSwapNode extends AbstractMemoryCheckpoint implements Lowerable, MemoryCheckpoint.Single, Virtualizable {

    public static final NodeClass<UnsafeCompareAndSwapNode> TYPE = NodeClass.create(UnsafeCompareAndSwapNode.class);
    @Input ValueNode object;
    @Input ValueNode offset;
    @Input ValueNode expected;
    @Input ValueNode newValue;

    private final JavaKind valueKind;
    private final LocationIdentity locationIdentity;

    public UnsafeCompareAndSwapNode(ValueNode object, ValueNode offset, ValueNode expected, ValueNode newValue, JavaKind valueKind, LocationIdentity locationIdentity) {
        super(TYPE, StampFactory.forKind(JavaKind.Boolean.getStackKind()));
        assert expected.stamp(NodeView.DEFAULT).isCompatible(newValue.stamp(NodeView.DEFAULT));
        this.object = object;
        this.offset = offset;
        this.expected = expected;
        this.newValue = newValue;
        this.valueKind = valueKind;
        this.locationIdentity = locationIdentity;
    }

    public ValueNode object() {
        return object;
    }

    public ValueNode offset() {
        return offset;
    }

    public ValueNode expected() {
        return expected;
    }

    public ValueNode newValue() {
        return newValue;
    }

    public JavaKind getValueKind() {
        return valueKind;
    }

    @Override
    public LocationIdentity getLocationIdentity() {
        return locationIdentity;
    }

    @Override
    public void lower(LoweringTool tool) {
        tool.getLowerer().lower(this, tool);
    }

    @Override
    public void virtualize(VirtualizerTool tool) {
        ValueNode alias = tool.getAlias(object);
        if (alias instanceof VirtualInstanceNode && offset.isConstant()) {

            VirtualInstanceNode obj = (VirtualInstanceNode) alias;

            int index = resolveFieldIndex(obj);

            if (index >= 0) {

                ValueNode currentValue = tool.getEntry(obj, index);
                ValueNode expectedAlias = tool.getAlias(this.expected);
                ValueNode newValueAlias = tool.getAlias(this.newValue);

                LogicNode equalsNode = CompareNode.createCompareNode(EQ, expectedAlias, currentValue, tool.getConstantReflection(), NodeView.DEFAULT);
                if (equalsNode instanceof LogicConstantNode) {

                    boolean equals = ((LogicConstantNode) equalsNode).getValue();
                    if (equals) {
                        tool.setVirtualEntry(obj, index, newValueAlias);
                    }
                    tool.replaceWith(ConstantNode.forBoolean(equals));

                } else if (!(currentValue instanceof VirtualInstanceNode) &&
                                !(expectedAlias instanceof VirtualObjectNode) &&
                                !(newValueAlias instanceof VirtualObjectNode)) {
                    ValueNode fieldValue = ConditionalNode.create(equalsNode, newValueAlias, currentValue, NodeView.DEFAULT);
                    ValueNode result = ConditionalNode.create(equalsNode, ConstantNode.forBoolean(true), ConstantNode.forBoolean(false), NodeView.DEFAULT);

                    tool.setVirtualEntry(obj, index, fieldValue);
                    tool.addNode(equalsNode);
                    tool.addNode(fieldValue);
                    tool.addNode(result);
                    tool.replaceWith(result);
                }
            }
        }
    }

    private int resolveFieldIndex(VirtualInstanceNode obj) {
        long fieldOffset = offset.asJavaConstant().asLong();
        ResolvedJavaField field = obj.type().findInstanceFieldWithOffset(fieldOffset, expected.getStackKind());
        return obj.fieldIndex(field);
    }
}
