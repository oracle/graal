/*
 * Copyright (c) 2009, 2021, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.compiler.graal.nodeinfo.InputType.State;
import static jdk.compiler.graal.nodeinfo.NodeCycles.CYCLES_8;
import static jdk.compiler.graal.nodeinfo.NodeSize.SIZE_8;

import jdk.compiler.graal.core.common.type.StampFactory;
import jdk.compiler.graal.debug.GraalError;
import jdk.compiler.graal.graph.Node;
import jdk.compiler.graal.graph.NodeClass;
import jdk.compiler.graal.nodeinfo.InputType;
import jdk.compiler.graal.nodeinfo.NodeInfo;
import jdk.compiler.graal.nodes.virtual.VirtualArrayNode;
import jdk.compiler.graal.nodes.virtual.VirtualObjectNode;
import jdk.compiler.graal.nodes.DeoptimizeNode;
import jdk.compiler.graal.nodes.FrameState;
import jdk.compiler.graal.nodes.StateSplit;
import jdk.compiler.graal.nodes.ValueNode;
import jdk.compiler.graal.nodes.extended.GuardingNode;
import jdk.compiler.graal.nodes.memory.SingleMemoryKill;
import jdk.compiler.graal.nodes.spi.Canonicalizable;
import jdk.compiler.graal.nodes.spi.CanonicalizerTool;
import jdk.compiler.graal.nodes.spi.Lowerable;
import jdk.compiler.graal.nodes.spi.Virtualizable;
import jdk.compiler.graal.nodes.spi.VirtualizerTool;
import jdk.compiler.graal.nodes.type.StampTool;
import org.graalvm.word.LocationIdentity;

import jdk.vm.ci.meta.DeoptimizationAction;
import jdk.vm.ci.meta.DeoptimizationReason;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * The {@code StoreIndexedNode} represents a write to an array element.
 */
@NodeInfo(cycles = CYCLES_8, size = SIZE_8)
public final class StoreIndexedNode extends AccessIndexedNode implements StateSplit, Lowerable, Virtualizable, Canonicalizable, SingleMemoryKill {

    public static final NodeClass<StoreIndexedNode> TYPE = NodeClass.create(StoreIndexedNode.class);

    @OptionalInput(InputType.Guard) private GuardingNode storeCheck;
    @Input ValueNode value;
    @OptionalInput(State) FrameState stateAfter;

    public GuardingNode getStoreCheck() {
        return storeCheck;
    }

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
    public LocationIdentity getKilledLocationIdentity() {
        return getLocationIdentity();
    }

    @Override
    public boolean hasSideEffect() {
        return true;
    }

    public ValueNode value() {
        return value;
    }

    public StoreIndexedNode(ValueNode array, ValueNode index, GuardingNode boundsCheck, GuardingNode storeCheck, JavaKind elementKind, ValueNode value) {
        super(TYPE, StampFactory.forVoid(), array, index, boundsCheck, elementKind);
        this.storeCheck = storeCheck;
        this.value = value;
    }

    public LocationIdentity getKilledLocation() {
        return getLocationIdentity();
    }

    @Override
    public void virtualize(VirtualizerTool tool) {
        ValueNode alias = tool.getAlias(array());
        if (alias instanceof VirtualObjectNode) {
            ValueNode indexValue = tool.getAlias(index());
            int idx = indexValue.isConstant() ? indexValue.asJavaConstant().asInt() : -1;
            VirtualArrayNode virtual = (VirtualArrayNode) alias;
            if (idx >= 0 && idx < virtual.entryCount()) {
                ResolvedJavaType componentType = virtual.type().getComponentType();
                if (elementKind.isPrimitive() || StampTool.isPointerAlwaysNull(value) || componentType.isJavaLangObject() ||
                                (StampTool.typeReferenceOrNull(value) != null && componentType.isAssignableFrom(StampTool.typeOrNull(value)))) {
                    boolean success = tool.setVirtualEntry(virtual, idx, value(), elementKind(), 0);
                    if (success) {
                        tool.delete();
                    } else {
                        GraalError.guarantee(virtual.isVirtualByteArray(tool.getMetaAccessExtensionProvider()), "only stores to virtual byte arrays can fail: %s", virtual);
                    }
                }
            }
        }
    }

    public FrameState getState() {
        return stateAfter;
    }

    @Override
    public Node canonical(CanonicalizerTool tool) {
        if (array().isNullConstant()) {
            return new DeoptimizeNode(DeoptimizationAction.InvalidateReprofile, DeoptimizationReason.NullCheckException);
        }
        return this;
    }
}
