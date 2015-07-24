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
package com.oracle.graal.nodes.extended;

import jdk.internal.jvmci.meta.*;

import com.oracle.graal.compiler.common.type.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.nodeinfo.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.java.*;
import com.oracle.graal.nodes.memory.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.virtual.*;

/**
 * Store of a value at a location specified as an offset relative to an object. No null check is
 * performed before the store.
 */
@NodeInfo
public final class UnsafeStoreNode extends UnsafeAccessNode implements StateSplit, Lowerable, Virtualizable, MemoryCheckpoint.Single {

    public static final NodeClass<UnsafeStoreNode> TYPE = NodeClass.create(UnsafeStoreNode.class);
    @Input ValueNode value;
    @OptionalInput(InputType.State) FrameState stateAfter;

    public UnsafeStoreNode(ValueNode object, ValueNode offset, ValueNode value, Kind accessKind, LocationIdentity locationIdentity) {
        this(object, offset, value, accessKind, locationIdentity, null);
    }

    public UnsafeStoreNode(ValueNode object, ValueNode offset, ValueNode value, Kind accessKind, LocationIdentity locationIdentity, FrameState stateAfter) {
        super(TYPE, StampFactory.forVoid(), object, offset, accessKind, locationIdentity);
        this.value = value;
        this.stateAfter = stateAfter;
        assert accessKind != Kind.Void && accessKind != Kind.Illegal;
    }

    public FrameState stateAfter() {
        return stateAfter;
    }

    public void setStateAfter(FrameState x) {
        assert x == null || x.isAlive() : "frame state must be in a graph";
        updateUsages(stateAfter, x);
        stateAfter = x;
    }

    public boolean hasSideEffect() {
        return true;
    }

    public ValueNode value() {
        return value;
    }

    @Override
    public void lower(LoweringTool tool) {
        tool.getLowerer().lower(this, tool);
    }

    @Override
    public void virtualize(VirtualizerTool tool) {
        ValueNode alias = tool.getAlias(object());
        if (alias instanceof VirtualObjectNode) {
            VirtualObjectNode virtual = (VirtualObjectNode) alias;
            ValueNode indexValue = tool.getAlias(offset());
            if (indexValue.isConstant()) {
                long off = indexValue.asJavaConstant().asLong();
                int entryIndex = virtual.entryIndexForOffset(off, accessKind());
                if (entryIndex != -1) {
                    Kind entryKind = virtual.entryKind(entryIndex);
                    ValueNode entry = tool.getEntry(virtual, entryIndex);
                    if (entry.getKind() == value.getKind() || entryKind == accessKind()) {
                        tool.setVirtualEntry(virtual, entryIndex, value(), true);
                        tool.delete();
                    } else {
                        if ((accessKind() == Kind.Long || accessKind() == Kind.Double) && entryKind == Kind.Int) {
                            int nextIndex = virtual.entryIndexForOffset(off + 4, entryKind);
                            if (nextIndex != -1) {
                                Kind nextKind = virtual.entryKind(nextIndex);
                                if (nextKind == Kind.Int) {
                                    tool.setVirtualEntry(virtual, entryIndex, value(), true);
                                    tool.setVirtualEntry(virtual, nextIndex, ConstantNode.forConstant(JavaConstant.forIllegal(), tool.getMetaAccessProvider(), graph()), true);
                                    tool.delete();
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Override
    protected ValueNode cloneAsFieldAccess(ResolvedJavaField field) {
        return new StoreFieldNode(object(), field, value(), stateAfter());
    }

    @Override
    protected ValueNode cloneAsArrayAccess(ValueNode location, LocationIdentity identity) {
        return new UnsafeStoreNode(object(), location, value, accessKind(), identity, stateAfter());
    }

    public FrameState getState() {
        return stateAfter;
    }
}
