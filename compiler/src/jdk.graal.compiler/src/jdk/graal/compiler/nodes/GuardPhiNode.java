/*
 * Copyright (c) 2009, 2023, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.nodes;

import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.graph.NodeInputList;
import jdk.graal.compiler.nodeinfo.InputType;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.extended.GuardingNode;
import jdk.graal.compiler.nodes.spi.CanonicalizerTool;

/**
 * Guard {@link PhiNode}s merge guard dependencies at control flow merges.
 */
@NodeInfo(nameTemplate = "GuardPhi({i#values})", allowedUsageTypes = {InputType.Guard})
public final class GuardPhiNode extends PhiNode implements GuardingNode {

    public static final NodeClass<GuardPhiNode> TYPE = NodeClass.create(GuardPhiNode.class);
    @OptionalInput(InputType.Guard) NodeInputList<ValueNode> values;

    public GuardPhiNode(AbstractMergeNode merge) {
        super(TYPE, StampFactory.forVoid(), merge);
        this.values = new NodeInputList<>(this);
    }

    public GuardPhiNode(AbstractMergeNode merge, ValueNode... values) {
        super(TYPE, StampFactory.forVoid(), merge);
        this.values = new NodeInputList<>(this, values);
    }

    @Override
    public InputType valueInputType() {
        return InputType.Guard;
    }

    @Override
    public NodeInputList<ValueNode> values() {
        return values;
    }

    @Override
    public PhiNode duplicateOn(AbstractMergeNode newMerge) {
        return graph().addWithoutUnique(new GuardPhiNode(newMerge));
    }

    @Override
    public GuardPhiNode duplicateWithValues(AbstractMergeNode newMerge, ValueNode... newValues) {
        return new GuardPhiNode(newMerge, newValues);
    }

    @Override
    public ProxyNode createProxyFor(LoopExitNode lex) {
        return graph().addWithoutUnique(new GuardProxyNode(this, lex));
    }

    @Override
    public ValueNode canonical(CanonicalizerTool tool) {
        if (isLoopPhi()) {
            boolean allBackValuesNull = true;
            for (int i = 1; i < valueCount(); i++) {
                ValueNode value = valueAt(i);
                if (value != null) {
                    allBackValuesNull = false;
                    break;
                }
            }
            if (allBackValuesNull) {
                // all values but the first are null, an allowed value for the guard phi, return the
                // first value instead
                return valueAt(0);
            }
        }
        return super.canonical(tool);
    }
}
