/*
 * Copyright (c) 2013, 2013, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.graal.graph.spi.*;
import com.oracle.graal.nodes.spi.*;

@NodeInfo
public class DynamicDeoptimizeNode extends AbstractDeoptimizeNode implements LIRLowerable, Canonicalizable {
    @Input private ValueNode actionAndReason;
    @Input private ValueNode speculation;

    public DynamicDeoptimizeNode(ValueNode actionAndReason, ValueNode speculation) {
        this.actionAndReason = actionAndReason;
        this.speculation = speculation;
    }

    public ValueNode getActionAndReason() {
        return actionAndReason;
    }

    public ValueNode getSpeculation() {
        return speculation;
    }

    @Override
    public ValueNode getActionAndReason(MetaAccessProvider metaAccess) {
        return getActionAndReason();
    }

    @Override
    public ValueNode getSpeculation(MetaAccessProvider metaAccess) {
        return getSpeculation();
    }

    public void generate(NodeLIRBuilderTool generator) {
        generator.getLIRGeneratorTool().emitDeoptimize(generator.operand(actionAndReason), generator.operand(speculation), generator.state(this));
    }

    @Override
    public Node canonical(CanonicalizerTool tool) {
        if (actionAndReason.isConstant() && speculation.isConstant()) {
            Constant constant = actionAndReason.asConstant();
            Constant speculationConstant = speculation.asConstant();
            DeoptimizeNode newDeopt = new DeoptimizeNode(tool.getMetaAccess().decodeDeoptAction(constant), tool.getMetaAccess().decodeDeoptReason(constant), tool.getMetaAccess().decodeDebugId(
                            constant), speculationConstant, stateBefore());
            return newDeopt;
        }
        return this;
    }
}
