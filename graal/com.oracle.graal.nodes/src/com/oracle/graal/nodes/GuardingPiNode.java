/*
 * Copyright (c) 2012, 2013, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.common.type.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.graph.spi.*;
import com.oracle.graal.nodeinfo.*;
import com.oracle.graal.nodes.calc.*;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.type.*;

/**
 * A node that changes the stamp of its input based on some condition being true.
 */
@NodeInfo(nameTemplate = "GuardingPi(!={p#negated}) {p#reason/s}")
public class GuardingPiNode extends FixedWithNextNode implements Lowerable, Virtualizable, Canonicalizable, ValueProxy {

    @Input ValueNode object;
    @Input(InputType.Condition) LogicNode condition;
    private final DeoptimizationReason reason;
    private final DeoptimizationAction action;
    private final Stamp piStamp;
    private boolean negated;

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

    /**
     * Constructor for {@link #guardingNonNull(Object)} node intrinsic.
     */
    public static GuardingPiNode create(ValueNode object) {
        return USE_GENERATED_NODES ? new GuardingPiNodeGen(object) : new GuardingPiNode(object);
    }

    GuardingPiNode(ValueNode object) {
        this(object, object.graph().unique(IsNullNode.create(object)), true, DeoptimizationReason.NullCheckException, DeoptimizationAction.None, object.stamp().join(StampFactory.objectNonNull()));
    }

    /**
     * Creates a guarding pi node.
     *
     * @param object the object whose type is refined if this guard succeeds
     * @param condition the condition to test
     * @param negateCondition the guard succeeds if {@code condition != negateCondition}
     * @param stamp the refined type of the object if the guard succeeds
     */
    public static GuardingPiNode create(ValueNode object, ValueNode condition, boolean negateCondition, DeoptimizationReason reason, DeoptimizationAction action, Stamp stamp) {
        return USE_GENERATED_NODES ? new GuardingPiNodeGen(object, condition, negateCondition, reason, action, stamp) : new GuardingPiNode(object, condition, negateCondition, reason, action, stamp);
    }

    protected GuardingPiNode(ValueNode object, ValueNode condition, boolean negateCondition, DeoptimizationReason reason, DeoptimizationAction action, Stamp stamp) {
        super(stamp);
        assert stamp != null;
        this.piStamp = stamp;
        this.object = object;
        this.condition = (LogicNode) condition;
        this.reason = reason;
        this.action = action;
        this.negated = negateCondition;
    }

    @Override
    public void lower(LoweringTool tool) {
        GuardingNode guard = tool.createGuard(next(), condition, reason, action, negated);
        ValueAnchorNode anchor = graph().add(ValueAnchorNode.create((ValueNode) guard));
        if (usages().isNotEmpty()) {
            PiNode pi = graph().unique(PiNode.create(object, stamp(), (ValueNode) guard));
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
        if (stamp() == StampFactory.illegal(object.getKind())) {
            // The guard always fails
            return DeoptimizeNode.create(action, reason);
        }
        if (condition instanceof LogicConstantNode) {
            LogicConstantNode c = (LogicConstantNode) condition;
            if (c.getValue() == negated) {
                // The guard always fails
                return DeoptimizeNode.create(action, reason);
            } else if (stamp().equals(object().stamp())) {
                // The guard always succeeds, and does not provide new type information
                return object;
            } else {
                // The guard always succeeds, and provides new type information
                return PiNode.create(object, stamp());
            }
        }
        return this;
    }

    @NodeIntrinsic
    public static <T> T guardingNonNull(T object) {
        if (object == null) {
            throw new NullPointerException();
        }
        return object;
    }

    @NodeIntrinsic
    public static native Object guardingPi(Object object, LogicNode condition, @ConstantNodeParameter boolean negateCondition, @ConstantNodeParameter DeoptimizationReason reason,
                    @ConstantNodeParameter DeoptimizationAction action, @ConstantNodeParameter Stamp stamp);

    @Override
    public ValueNode getOriginalNode() {
        return object;
    }
}
