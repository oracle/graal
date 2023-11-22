/*
 * Copyright (c) 2011, 2022, Oracle and/or its affiliates. All rights reserved.
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

import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.spi.Canonicalizable;
import jdk.graal.compiler.nodes.spi.ValueProxy;
import jdk.graal.compiler.nodes.spi.Virtualizable;
import jdk.graal.compiler.nodes.spi.VirtualizerTool;

import jdk.vm.ci.meta.TriState;

@NodeInfo
public abstract class UnaryOpLogicNode extends LIRLowerableLogicNode implements Canonicalizable.Unary<ValueNode>, Virtualizable {

    public static final NodeClass<UnaryOpLogicNode> TYPE = NodeClass.create(UnaryOpLogicNode.class);
    @Input protected ValueNode value;

    @Override
    public ValueNode getValue() {
        return value;
    }

    public UnaryOpLogicNode(NodeClass<? extends UnaryOpLogicNode> c, ValueNode value) {
        super(c);
        assert value != null;
        this.value = value;
    }

    @Override
    public void virtualize(VirtualizerTool tool) {
        ValueNode alias = tool.getAlias(getValue());
        TriState fold = tryFold(alias.stamp(NodeView.DEFAULT));
        if (fold != TriState.UNKNOWN) {
            tool.replaceWithValue(LogicConstantNode.forBoolean(fold.isTrue(), graph()));
        }
    }

    /**
     * The input stamp cannot be trusted, the returned stamp cannot use the input stamp to narrow
     * itself or derive any assumptions. This method does not use the input stamp and is considered
     * safe.
     *
     * It's responsibility of the caller to determine when it's "safe" to "trust" the input stamp.
     */
    public abstract Stamp getSucceedingStampForValue(boolean negated);

    public abstract TriState tryFold(Stamp valueStamp);

    @Override
    public TriState implies(boolean thisNegated, LogicNode other) {
        if (other instanceof UnaryOpLogicNode) {
            UnaryOpLogicNode unaryY = (UnaryOpLogicNode) other;
            if (this.getValue() == unaryY.getValue() || // fast path
                            skipThroughPisAndProxies(this.getValue()) == skipThroughPisAndProxies(unaryY.getValue())) {
                Stamp succStamp = this.getSucceedingStampForValue(thisNegated);
                TriState fold = unaryY.tryFold(succStamp);
                if (fold.isKnown()) {
                    return fold;
                }
            }
        }
        return super.implies(thisNegated, other);
    }

    private static ValueNode skipThroughPisAndProxies(ValueNode node) {
        ValueNode n = node;
        while (n != null) {
            if (n instanceof PiNode) {
                n = ((PiNode) n).getOriginalNode();
            } else if (n instanceof ValueProxy) {
                n = ((ValueProxy) n).getOriginalNode();
            } else {
                break;
            }
        }
        return n;
    }
}
