/*
 * Copyright (c) 2012, 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.nodes;

import com.oracle.graal.compiler.common.type.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.graph.spi.*;
import com.oracle.graal.nodeinfo.*;
import com.oracle.graal.nodes.calc.*;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.type.*;
import com.oracle.jvmci.meta.*;

/**
 * A node that changes the stamp of its input based on some condition being true.
 */
@NodeInfo(nameTemplate = "GuardingPi(!={p#negated}) {p#reason/s}")
public final class GuardingPiNode extends FixedWithNextNode implements Lowerable, Virtualizable, Canonicalizable, ValueProxy {

    public static final NodeClass<GuardingPiNode> TYPE = NodeClass.create(GuardingPiNode.class);
    @Input ValueNode object;
    @Input(InputType.Condition) LogicNode condition;
    protected final DeoptimizationReason reason;
    protected final DeoptimizationAction action;
    protected final Stamp piStamp;
    protected boolean negated;

    public ValueNode object() {
        return object;
    }

    public LogicNode condition() {
        return condition;
    }

    public boolean isNegated() {
        return negated;
    }

    public DeoptimizationReason getReason() {
        return reason;
    }

    public DeoptimizationAction getAction() {
        return action;
    }

    public GuardingPiNode(ValueNode object) {
        this(object, object.graph().unique(new IsNullNode(object)), true, DeoptimizationReason.NullCheckException, DeoptimizationAction.None, object.stamp().join(StampFactory.objectNonNull()));
    }

    public GuardingPiNode(ValueNode object, ValueNode condition, boolean negateCondition, DeoptimizationReason reason, DeoptimizationAction action, Stamp stamp) {
        super(TYPE, stamp);
        assert stamp != null;
        this.piStamp = stamp;
        this.object = object;
        this.condition = (LogicNode) condition;
        this.reason = reason;
        this.action = action;
        this.negated = negateCondition;
    }

    public static ValueNode createNullCheck(ValueNode object) {
        ObjectStamp objectStamp = (ObjectStamp) object.stamp();
        if (objectStamp.nonNull()) {
            return object;
        } else {
            return new GuardingPiNode(object);
        }
    }

    @Override
    public void lower(LoweringTool tool) {
        GuardingNode guard = tool.createGuard(next(), condition, reason, action, negated);
        ValueAnchorNode anchor = graph().add(new ValueAnchorNode((ValueNode) guard));
        if (usages().isNotEmpty()) {
            PiNode pi = graph().unique(new PiNode(object, stamp(), (ValueNode) guard));
            replaceAtUsages(pi);
        }
        graph().replaceFixedWithFixed(this, anchor);
    }

    @Override
    public void virtualize(VirtualizerTool tool) {
        State state = tool.getObjectState(object);
        if (state != null && state.getState() == EscapeState.Virtual && StampTool.typeOrNull(this) != null && StampTool.typeOrNull(this).isAssignableFrom(state.getVirtualObject().type())) {
            tool.replaceWithVirtual(state.getVirtualObject());
        }
    }

    @Override
    public boolean inferStamp() {
        return updateStamp(piStamp.join(object().stamp()));
    }

    @Override
    public Node canonical(CanonicalizerTool tool) {
        if (stamp().isEmpty()) {
            // The guard always fails
            return new DeoptimizeNode(action, reason);
        }
        if (condition instanceof LogicConstantNode) {
            LogicConstantNode c = (LogicConstantNode) condition;
            if (c.getValue() == negated) {
                // The guard always fails
                return new DeoptimizeNode(action, reason);
            } else if (stamp().equals(object().stamp())) {
                // The guard always succeeds, and does not provide new type information
                return object;
            } else {
                // The guard always succeeds, and provides new type information
                return new PiNode(object, stamp());
            }
        }
        return this;
    }

    @NodeIntrinsic
    public static native Object guardingNonNull(Object object);

    @Override
    public ValueNode getOriginalNode() {
        return object;
    }

    /**
     * Casts a value to have an exact, non-null stamp representing {@link Class} that is guarded by
     * a null check.
     */
    @NodeIntrinsic(GuardingPiNode.class)
    public static native Class<?> asNonNullClass(Class<?> c);
}
