/*
 * Copyright (c) 2009, 2022, Oracle and/or its affiliates. All rights reserved.
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
package jdk.compiler.graal.nodes.java;

import static jdk.compiler.graal.nodeinfo.NodeCycles.CYCLES_8;

import jdk.compiler.graal.core.common.memory.MemoryOrderMode;
import jdk.compiler.graal.core.common.type.StampFactory;
import jdk.compiler.graal.graph.Node;
import jdk.compiler.graal.graph.NodeClass;
import jdk.compiler.graal.nodeinfo.InputType;
import jdk.compiler.graal.nodeinfo.NodeCycles;
import jdk.compiler.graal.nodeinfo.NodeInfo;
import jdk.compiler.graal.nodes.virtual.VirtualInstanceNode;
import jdk.compiler.graal.nodes.virtual.VirtualObjectNode;
import jdk.compiler.graal.nodes.DeoptimizeNode;
import jdk.compiler.graal.nodes.FrameState;
import jdk.compiler.graal.nodes.StateSplit;
import jdk.compiler.graal.nodes.ValueNode;
import jdk.compiler.graal.nodes.memory.SingleMemoryKill;
import jdk.compiler.graal.nodes.spi.Canonicalizable;
import jdk.compiler.graal.nodes.spi.CanonicalizerTool;
import jdk.compiler.graal.nodes.spi.Virtualizable;
import jdk.compiler.graal.nodes.spi.VirtualizerTool;
import org.graalvm.word.LocationIdentity;

import jdk.vm.ci.meta.DeoptimizationAction;
import jdk.vm.ci.meta.DeoptimizationReason;
import jdk.vm.ci.meta.ResolvedJavaField;

/**
 * The {@code StoreFieldNode} represents a write to a static or instance field.
 */
@NodeInfo(nameTemplate = "StoreField#{p#field/s}")
public final class StoreFieldNode extends AccessFieldNode implements StateSplit, Virtualizable, Canonicalizable, SingleMemoryKill {
    public static final NodeClass<StoreFieldNode> TYPE = NodeClass.create(StoreFieldNode.class);

    @Input ValueNode value;
    @OptionalInput(InputType.State) FrameState stateAfter;

    @Override
    public FrameState stateAfter() {
        return stateAfter;
    }

    @Override
    public void setStateAfter(FrameState x) {
        assert x == null || x.isAlive() : "frame state must be in a graph";
        updateUsages(stateAfter, x);
        stateAfter = x;
    }

    @Override
    public boolean hasSideEffect() {
        return true;
    }

    public ValueNode value() {
        return value;
    }

    public StoreFieldNode(ValueNode object, ResolvedJavaField field, ValueNode value) {
        this(object, field, value, MemoryOrderMode.getMemoryOrder(field));
    }

    public StoreFieldNode(ValueNode object, ResolvedJavaField field, ValueNode value, MemoryOrderMode memoryOrder) {
        super(TYPE, StampFactory.forVoid(), object, field, memoryOrder, false);
        this.value = value;
    }

    public StoreFieldNode(ValueNode object, ResolvedJavaField field, ValueNode value, FrameState stateAfter) {
        super(TYPE, StampFactory.forVoid(), object, field, MemoryOrderMode.getMemoryOrder(field), false);
        this.value = value;
        this.stateAfter = stateAfter;
    }

    public StoreFieldNode(ValueNode object, ResolvedJavaField field, ValueNode value, FrameState stateAfter, MemoryOrderMode memoryOrder) {
        this(object, field, value, stateAfter, memoryOrder, false);
    }

    public StoreFieldNode(ValueNode object, ResolvedJavaField field, ValueNode value, FrameState stateAfter, MemoryOrderMode memoryOrder, boolean immutable) {
        super(TYPE, StampFactory.forVoid(), object, field, memoryOrder, immutable);
        this.value = value;
        this.stateAfter = stateAfter;
    }

    @Override
    public LocationIdentity getKilledLocationIdentity() {
        return ordersMemoryAccesses() ? LocationIdentity.ANY_LOCATION : getLocationIdentity();
    }

    @Override
    public void virtualize(VirtualizerTool tool) {
        ValueNode alias = tool.getAlias(object());
        if (alias instanceof VirtualObjectNode) {
            VirtualInstanceNode virtual = (VirtualInstanceNode) alias;
            int fieldIndex = virtual.fieldIndex(field());
            if (fieldIndex != -1) {
                tool.setVirtualEntry(virtual, fieldIndex, value());
                tool.delete();
            }
        }
    }

    public FrameState getState() {
        return stateAfter;
    }

    @Override
    public NodeCycles estimatedNodeCycles() {
        if (ordersMemoryAccesses()) {
            return CYCLES_8;
        }
        return super.estimatedNodeCycles();
    }

    @Override
    public Node canonical(CanonicalizerTool tool) {
        if (!field.isStatic() && object.isNullConstant()) {
            return new DeoptimizeNode(DeoptimizationAction.InvalidateReprofile, DeoptimizationReason.NullCheckException);
        }
        return this;
    }
}
