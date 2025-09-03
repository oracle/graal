/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.nodes.foreign;

import static jdk.graal.compiler.nodeinfo.InputType.Memory;

import java.lang.reflect.Field;

import org.graalvm.word.LocationIdentity;

import com.oracle.svm.core.nodes.ClusterNode;
import com.oracle.svm.util.ReflectionUtil;

import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.graph.NodeInputList;
import jdk.graal.compiler.nodeinfo.NodeCycles;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodeinfo.NodeSize;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.FixedWithNextNode;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.debug.ControlFlowAnchored;
import jdk.graal.compiler.nodes.memory.MemoryAccess;
import jdk.graal.compiler.nodes.memory.MemoryKill;
import jdk.internal.foreign.MemorySessionImpl;
import jdk.internal.misc.ScopedMemoryAccess;

/**
 * See {@link ClusterNode} for details.
 *
 * Mark the beginning of a {@link ScopedMemoryAccess} checking the validity of a memory session.
 */
@NodeInfo(cycles = NodeCycles.CYCLES_UNKNOWN, size = NodeSize.SIZE_UNKNOWN)
public class MemoryArenaValidInScopeNode extends FixedWithNextNode implements MemoryAccess, ControlFlowAnchored {
    public static final Field STATE_FIELD = ReflectionUtil.lookupField(MemorySessionImpl.class, "state");

    public static final NodeClass<MemoryArenaValidInScopeNode> TYPE = NodeClass.create(MemoryArenaValidInScopeNode.class);
    @Node.OptionalInput ValueNode value;
    private final LocationIdentity fieldLocation;
    @OptionalInput(Memory) MemoryKill lastLocationAccess;

    public MemoryArenaValidInScopeNode(ValueNode value, LocationIdentity fieldLocation) {
        super(TYPE, StampFactory.forInteger(64));
        this.value = value;
        this.fieldLocation = fieldLocation;
    }

    @OptionalInput NodeInputList<ValueNode> scopeAssociatedValues;

    public void addScopeAssociatedValue(ValueNode memorySession) {
        if (this.scopeAssociatedValues == null) {
            this.scopeAssociatedValues = new NodeInputList<>(this);
        }
        this.scopeAssociatedValues.add(memorySession);
    }

    public ValueNode getValue() {
        return value;
    }

    public void delete(int val) {
        this.replaceAtUsages(ConstantNode.forLong(val, graph()));
        this.graph().removeFixed(this);
    }

    @Override
    public MemoryKill getLastLocationAccess() {
        return lastLocationAccess;
    }

    @Override
    public void setLastLocationAccess(MemoryKill lla) {
        updateUsagesInterface(lastLocationAccess, lla);
        lastLocationAccess = lla;
    }

    @Override
    public LocationIdentity getLocationIdentity() {
        return fieldLocation;
    }

}
