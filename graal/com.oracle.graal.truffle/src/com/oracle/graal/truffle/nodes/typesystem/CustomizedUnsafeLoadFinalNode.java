/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.truffle.nodes.typesystem;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.graph.spi.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.calc.*;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.nodes.java.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.truffle.api.*;

/**
 * Macro node for method {@link CompilerDirectives#unsafeGetFinalObject} and friends.
 */
public class CustomizedUnsafeLoadFinalNode extends FixedWithNextNode implements Canonicalizable, Virtualizable, Lowerable {
    private static final int ARGUMENT_COUNT = 4;
    private static final int OBJECT_ARGUMENT_INDEX = 0;
    private static final int OFFSET_ARGUMENT_INDEX = 1;
    private static final int CONDITION_ARGUMENT_INDEX = 2;
    private static final int LOCATION_ARGUMENT_INDEX = 3;

    @Input private ValueNode object;
    @Input private ValueNode offset;
    @Input private ValueNode condition;
    @Input private ValueNode location;
    private final Kind accessKind;

    public CustomizedUnsafeLoadFinalNode(Invoke invoke) {
        super(invoke.asNode().stamp());
        NodeInputList<ValueNode> arguments = invoke.callTarget().arguments();
        assert arguments.size() == ARGUMENT_COUNT;
        this.object = arguments.get(OBJECT_ARGUMENT_INDEX);
        this.offset = arguments.get(OFFSET_ARGUMENT_INDEX);
        this.condition = arguments.get(CONDITION_ARGUMENT_INDEX);
        this.location = arguments.get(LOCATION_ARGUMENT_INDEX);
        this.accessKind = ((MethodCallTargetNode) invoke.callTarget()).targetMethod().getSignature().getReturnKind();
    }

    @Override
    public Node canonical(CanonicalizerTool tool) {
        if (object.isConstant() && !object.isNullConstant() && offset.isConstant()) {
            Constant constant = tool.getConstantReflection().readUnsafeConstant(accessKind, object.asConstant().asObject(), offset.asConstant().asLong(), accessKind == Kind.Object);
            return ConstantNode.forConstant(constant, tool.getMetaAccess(), graph());
        }
        return this;
    }

    /**
     * @see UnsafeLoadNode#virtualize(VirtualizerTool)
     */
    @Override
    public void virtualize(VirtualizerTool tool) {
        State state = tool.getObjectState(object);
        if (state != null && state.getState() == EscapeState.Virtual) {
            ValueNode offsetValue = tool.getReplacedValue(offset);
            if (offsetValue.isConstant()) {
                long constantOffset = offsetValue.asConstant().asLong();
                int entryIndex = state.getVirtualObject().entryIndexForOffset(constantOffset);
                if (entryIndex != -1) {
                    ValueNode entry = state.getEntry(entryIndex);
                    if (entry.kind() == kind() || state.getVirtualObject().entryKind(entryIndex) == accessKind) {
                        tool.replaceWith(entry);
                    }
                }
            }
        }
    }

    @Override
    public void lower(LoweringTool tool) {
        CompareNode compare = CompareNode.createCompareNode(graph(), Condition.EQ, condition, ConstantNode.forBoolean(true, graph()));
        Object locationIdentityObject = location.asConstant().asObject();
        LocationIdentity locationIdentity;
        if (locationIdentityObject == null) {
            locationIdentity = LocationIdentity.ANY_LOCATION;
        } else {
            locationIdentity = ObjectLocationIdentity.create(locationIdentityObject);
        }
        UnsafeLoadNode result = graph().add(new UnsafeLoadNode(object, offset, accessKind, locationIdentity, compare));
        graph().replaceFixedWithFixed(this, result);
        result.lower(tool);
    }
}
