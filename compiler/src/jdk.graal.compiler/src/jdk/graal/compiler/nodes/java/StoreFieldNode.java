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
package jdk.graal.compiler.nodes.java;

import static jdk.graal.compiler.nodeinfo.NodeCycles.CYCLES_8;

import jdk.graal.compiler.core.common.memory.MemoryOrderMode;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodeinfo.InputType;
import jdk.graal.compiler.nodeinfo.NodeCycles;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.virtual.VirtualInstanceNode;
import jdk.graal.compiler.nodes.virtual.VirtualObjectNode;
import jdk.graal.compiler.nodes.DeoptimizeNode;
import jdk.graal.compiler.nodes.FrameState;
import jdk.graal.compiler.nodes.StateSplit;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.memory.SingleMemoryKill;
import jdk.graal.compiler.nodes.spi.Canonicalizable;
import jdk.graal.compiler.nodes.spi.CanonicalizerTool;
import jdk.graal.compiler.nodes.spi.Virtualizable;
import jdk.graal.compiler.nodes.spi.VirtualizerTool;
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
