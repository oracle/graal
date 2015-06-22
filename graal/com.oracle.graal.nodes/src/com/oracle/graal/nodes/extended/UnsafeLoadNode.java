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
import com.oracle.graal.nodes.spi.*;

/**
 * Load of a value from a location specified as an offset relative to an object. No null check is
 * performed before the load.
 */
@NodeInfo
public final class UnsafeLoadNode extends UnsafeAccessNode implements Lowerable, Virtualizable {
    public static final NodeClass<UnsafeLoadNode> TYPE = NodeClass.create(UnsafeLoadNode.class);
    @OptionalInput(InputType.Condition) LogicNode guardingCondition;

    public UnsafeLoadNode(ValueNode object, ValueNode offset, Kind accessKind, LocationIdentity locationIdentity) {
        this(object, offset, accessKind, locationIdentity, null);
    }

    public UnsafeLoadNode(ValueNode object, ValueNode offset, Kind accessKind, LocationIdentity locationIdentity, LogicNode condition) {
        super(TYPE, StampFactory.forKind(accessKind.getStackKind()), object, offset, accessKind, locationIdentity);
        this.guardingCondition = condition;
    }

    public LogicNode getGuardingCondition() {
        return guardingCondition;
    }

    @Override
    public void lower(LoweringTool tool) {
        tool.getLowerer().lower(this, tool);
    }

    @Override
    public void virtualize(VirtualizerTool tool) {
        State state = tool.getObjectState(object());
        if (state != null && state.getState() == EscapeState.Virtual) {
            ValueNode offsetValue = tool.getReplacedValue(offset());
            if (offsetValue.isConstant()) {
                long off = offsetValue.asJavaConstant().asLong();
                int entryIndex = state.getVirtualObject().entryIndexForOffset(off, accessKind());

                if (entryIndex != -1) {
                    ValueNode entry = state.getEntry(entryIndex);
                    Kind entryKind = state.getVirtualObject().entryKind(entryIndex);
                    if (entry.getKind() == getKind() || entryKind == accessKind()) {
                        tool.replaceWith(entry);
                    }
                }
            }
        }
    }

    @Override
    protected ValueNode cloneAsFieldAccess(ResolvedJavaField field) {
        return new LoadFieldNode(object(), field);
    }

    @Override
    protected ValueNode cloneAsArrayAccess(ValueNode location, LocationIdentity identity) {
        return new UnsafeLoadNode(object(), location, accessKind(), identity, guardingCondition);
    }

    @NodeIntrinsic
    public static native Object load(Object object, long offset, @ConstantNodeParameter Kind kind, @ConstantNodeParameter LocationIdentity locationIdentity);
}
