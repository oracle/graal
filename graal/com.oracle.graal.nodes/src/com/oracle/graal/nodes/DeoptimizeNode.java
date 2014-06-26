/*
 * Copyright (c) 2009, 2011, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.graal.graph.*;
import com.oracle.graal.nodes.spi.*;

@NodeInfo(shortName = "Deopt", nameTemplate = "Deopt {p#reason/s}")
public class DeoptimizeNode extends AbstractDeoptimizeNode implements Lowerable, LIRLowerable {

    private final DeoptimizationAction action;
    private final DeoptimizationReason reason;
    private final int debugId;
    private final Constant speculation;

    public DeoptimizeNode(DeoptimizationAction action, DeoptimizationReason reason) {
        this(action, reason, 0, Constant.NULL_OBJECT, null);
    }

    public DeoptimizeNode(DeoptimizationAction action, DeoptimizationReason reason, int debugId, Constant speculation, FrameState stateBefore) {
        super(stateBefore);
        assert action != null;
        assert reason != null;
        assert speculation.getKind() == Kind.Object;
        this.action = action;
        this.reason = reason;
        this.debugId = debugId;
        this.speculation = speculation;
    }

    public DeoptimizationAction action() {
        return action;
    }

    public DeoptimizationReason reason() {
        return reason;
    }

    @Override
    public void lower(LoweringTool tool) {
        tool.getLowerer().lower(this, tool);
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        gen.getLIRGeneratorTool().emitDeoptimize(gen.getLIRGeneratorTool().getMetaAccess().encodeDeoptActionAndReason(action, reason, debugId), speculation, gen.state(this));
    }

    @Override
    public ValueNode getActionAndReason(MetaAccessProvider metaAccess) {
        return ConstantNode.forConstant(metaAccess.encodeDeoptActionAndReason(action, reason, debugId), metaAccess, graph());
    }

    @Override
    public ValueNode getSpeculation(MetaAccessProvider metaAccess) {
        return ConstantNode.forConstant(speculation, metaAccess, graph());
    }

    public Constant getSpeculation() {
        return speculation;
    }

    @NodeIntrinsic
    public static native void deopt(@ConstantNodeParameter DeoptimizationAction action, @ConstantNodeParameter DeoptimizationReason reason);
}
