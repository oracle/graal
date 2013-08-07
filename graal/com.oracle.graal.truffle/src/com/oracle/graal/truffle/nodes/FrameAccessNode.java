/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.truffle.nodes;

import java.lang.reflect.*;
import java.util.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.calc.*;
import com.oracle.graal.nodes.java.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.type.*;
import com.oracle.graal.truffle.*;
import com.oracle.truffle.api.frame.*;

/**
 * Base node class for the intrinsic nodes for read and write access to a Truffle frame.
 */
public abstract class FrameAccessNode extends FixedWithNextNode implements Simplifiable {

    @Input private ValueNode frame;
    @Input private ValueNode slot;
    protected final ResolvedJavaField field;
    protected final Kind slotKind;

    public FrameAccessNode(Stamp stamp, Kind slotKind, ValueNode frame, ValueNode slot, ResolvedJavaField field) {
        super(stamp);
        this.slotKind = slotKind;
        this.frame = frame;
        this.slot = slot;
        this.field = field;
    }

    public ValueNode getFrame() {
        return frame;
    }

    public ValueNode getSlot() {
        return slot;
    }

    public Kind getSlotKind() {
        return slotKind;
    }

    protected int getSlotIndex() {
        return getConstantFrameSlot().getIndex();
    }

    protected boolean isConstantFrameSlot() {
        return slot.isConstant() && !slot.isNullConstant();
    }

    protected FrameSlot getConstantFrameSlot() {
        assert isConstantFrameSlot() : slot;
        return (FrameSlot) slot.asConstant().asObject();
    }

    protected final void insertDeoptimization(VirtualizerTool tool) {
        LogicNode contradiction = LogicConstantNode.contradiction(graph());
        FixedGuardNode fixedGuard = new FixedGuardNode(contradiction, DeoptimizationReason.UnreachedCode, DeoptimizationAction.InvalidateReprofile);
        tool.addNode(fixedGuard);
    }

    @Override
    public String toString(Verbosity verbosity) {
        if (verbosity == Verbosity.Name) {
            return super.toString(verbosity) + getSlotKind().name() + (isConstantFrameSlot() ? " " + getConstantFrameSlot() : "");
        } else {
            return super.toString(verbosity);
        }
    }

    protected final ValueNode getSlotOffset(int scale, MetaAccessProvider metaAccessProvider) {
        if (isConstantFrameSlot()) {
            return ConstantNode.forInt(getSlotIndex() * scale, graph());
        } else {
            LoadFieldNode loadFrameSlotIndex = graph().add(new LoadFieldNode(getSlot(), metaAccessProvider.lookupJavaField(getFrameSlotIndexField())));
            graph().addBeforeFixed(this, loadFrameSlotIndex);
            return scale == 1 ? loadFrameSlotIndex : IntegerArithmeticNode.mul(loadFrameSlotIndex, ConstantNode.forInt(scale, graph()));
        }
    }

    private static Field getFrameSlotIndexField() {
        try {
            return FrameSlotImpl.class.getDeclaredField("index");
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
    }

    protected final boolean isValidAccessKind() {
        if (getSlotKind() == Kind.Byte) {
            // tag access
            return true;
        }

        return getSlotKind() == getGraalKind(getConstantFrameSlot().getKind());
    }

    private static Kind getGraalKind(FrameSlotKind kind) {
        switch (kind) {
            case Object:
                return Kind.Object;
            case Long:
                return Kind.Long;
            case Int:
                return Kind.Int;
            case Double:
                return Kind.Double;
            case Float:
                return Kind.Float;
            case Boolean:
                return Kind.Boolean;
            case Illegal:
            default:
                return Kind.Illegal;
        }
    }

    @Override
    public final void simplify(SimplifierTool tool) {
        if (isConstantFrameSlot()) {
            if (!isValidAccessKind()) {
                tool.deleteBranch(this.next());
                this.replaceAndDelete(graph().add(new DeoptimizeNode(DeoptimizationAction.InvalidateReprofile, DeoptimizationReason.UnreachedCode)));
            } else {
                tool.assumptions().record(new AssumptionValidAssumption((OptimizedAssumption) getConstantFrameSlot().getFrameDescriptor().getVersion()));
            }
        }
    }

    @Override
    public Map<Object, Object> getDebugProperties(Map<Object, Object> map) {
        Map<Object, Object> properties = super.getDebugProperties(map);
        if (isConstantFrameSlot()) {
            properties.put("frameSlot", getConstantFrameSlot().toString());
        }
        return properties;
    }
}
