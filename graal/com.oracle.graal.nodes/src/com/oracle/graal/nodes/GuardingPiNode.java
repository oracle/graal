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

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.type.*;

/**
 * A node that changes the stamp of its input based on some condition being true.
 */
@NodeInfo(nameTemplate = "GuardingPi(!={p#negated}) {p#reason/s}")
public class GuardingPiNode extends FixedWithNextNode implements Lowerable, GuardingNode, Canonicalizable {

    @Input private ValueNode object;
    @Input private LogicNode condition;
    private final DeoptimizationReason reason;
    private final DeoptimizationAction action;
    private boolean negated;

    public ValueNode object() {
        return object;
    }

    public GuardingPiNode(ValueNode object, ValueNode condition, boolean negateCondition, DeoptimizationReason reason, DeoptimizationAction action, Stamp stamp) {
        super(object.stamp().join(stamp));
        assert stamp() != null;
        this.object = object;
        this.condition = (LogicNode) condition;
        this.reason = reason;
        this.action = action;
        this.negated = negateCondition;
    }

    @Override
    public void lower(LoweringTool tool, LoweringType loweringType) {
        if (loweringType == LoweringType.AFTER_GUARDS) {
            throw new GraalInternalError("Cannot create guards in after-guard lowering");
        }
        FixedGuardNode guard = graph().add(new FixedGuardNode(condition, reason, action, negated));
        PiNode pi = graph().add(new PiNode(object, stamp(), guard));
        replaceAtUsages(pi);
        graph().replaceFixedWithFixed(this, guard);
    }

    @Override
    public boolean inferStamp() {
        return updateStamp(stamp().join(object().stamp()));
    }

    @Override
    public ValueNode canonical(CanonicalizerTool tool) {
        if (condition instanceof LogicConstantNode) {
            LogicConstantNode c = (LogicConstantNode) condition;
            if (c.getValue() != negated && stamp().equals(object().stamp())) {
                return object;
            }
        }
        return this;
    }

    @NodeIntrinsic
    public static native Object guardingPi(Object object, LogicNode condition, @ConstantNodeParameter boolean negateCondition, @ConstantNodeParameter DeoptimizationReason reason,
                    @ConstantNodeParameter DeoptimizationAction action, @ConstantNodeParameter Stamp stamp);

    @Override
    public ValueNode asNode() {
        return this;
    }
}
