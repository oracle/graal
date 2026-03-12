/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.nodes.virtual;

import static jdk.graal.compiler.nodeinfo.InputType.Memory;
import static jdk.graal.compiler.nodeinfo.NodeCycles.CYCLES_0;
import static jdk.graal.compiler.nodeinfo.NodeSize.SIZE_0;

import org.graalvm.word.LocationIdentity;

import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.FieldLocationIdentity;
import jdk.graal.compiler.nodes.FixedWithNextNode;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.memory.MemoryAccess;
import jdk.graal.compiler.nodes.memory.MemoryKill;
import jdk.graal.compiler.nodes.spi.Lowerable;
import jdk.graal.compiler.nodes.spi.LoweringTool;
import jdk.graal.compiler.nodes.spi.Virtualizable;
import jdk.graal.compiler.nodes.spi.VirtualizerTool;
import jdk.vm.ci.meta.ResolvedJavaField;

/**
 * Represents a node in the graph that establishes an aliasing relationship between the value stored
 * in the {@link #field} of {@link #receiver} and the {@link #aliasValue}. It allows read
 * elimination to replace future {@link #field} accesses with the {@link #aliasValue}.
 */
@NodeInfo(cycles = CYCLES_0, size = SIZE_0)
public final class FieldAliasNode extends FixedWithNextNode implements MemoryAccess, Virtualizable, Lowerable {

    public static final NodeClass<FieldAliasNode> TYPE = NodeClass.create(FieldAliasNode.class);

    @Input ValueNode receiver;
    @Input ValueNode aliasValue;
    private final ResolvedJavaField field;
    private final FieldLocationIdentity location;

    @OptionalInput(Memory) MemoryKill lastLocationAccess;

    public FieldAliasNode(ValueNode receiver, ResolvedJavaField field, ValueNode aliasValue) {
        super(TYPE, StampFactory.forVoid());
        this.receiver = receiver;
        this.field = field;
        this.aliasValue = aliasValue;
        this.location = new FieldLocationIdentity(field);
    }

    public ResolvedJavaField getField() {
        return field;
    }

    public ValueNode getReceiver() {
        return receiver;
    }

    public ValueNode getAlias() {
        return aliasValue;
    }

    @Override
    public LocationIdentity getLocationIdentity() {
        return location;
    }

    @Override
    public void setLastLocationAccess(MemoryKill lla) {
        updateUsagesInterface(lastLocationAccess, lla);
        this.lastLocationAccess = lla;
    }

    @Override
    public MemoryKill getLastLocationAccess() {
        return lastLocationAccess;
    }

    @Override
    public void virtualize(VirtualizerTool tool) {
        if (tool.getAlias(getReceiver()) instanceof VirtualInstanceNode virtualObject) {
            int fieldIndex = virtualObject.fieldIndex(getField());
            if (fieldIndex != -1) {
                // Injects aliasing relationship
                tool.setVirtualEntry(virtualObject, fieldIndex, getAlias());
            }
        }
    }

    @Override
    public void lower(LoweringTool tool) {
        // We assume the aliasing relationship is injected into read elimination optimizations
        // before the first lowering.
        graph().removeFixed(this);
    }
}
