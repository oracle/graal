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
package org.graalvm.compiler.nodes;

import java.util.Map;

import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.graph.NodeFlood;
import org.graalvm.compiler.graph.NodeInputList;
import org.graalvm.compiler.nodeinfo.InputType;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.type.StampTool;
import org.graalvm.util.CollectionsUtil;

/**
 * Value {@link PhiNode}s merge data flow values at control flow merges.
 */
@NodeInfo(nameTemplate = "Phi({i#values}, {p#valueDescription})")
public class ValuePhiNode extends PhiNode {

    public static final NodeClass<ValuePhiNode> TYPE = NodeClass.create(ValuePhiNode.class);
    @Input(InputType.Value) protected NodeInputList<ValueNode> values;

    public ValuePhiNode(Stamp stamp, AbstractMergeNode merge) {
        this(TYPE, stamp, merge);
    }

    @SuppressWarnings("this-escape")
    protected ValuePhiNode(NodeClass<? extends ValuePhiNode> c, Stamp stamp, AbstractMergeNode merge) {
        super(c, stamp, merge);
        assert stamp != StampFactory.forVoid();
        values = new NodeInputList<>(this);
    }

    public ValuePhiNode(Stamp stamp, AbstractMergeNode merge, ValueNode... values) {
        this(TYPE, stamp, merge, values);
    }

    @SuppressWarnings("this-escape")
    public ValuePhiNode(NodeClass<? extends ValuePhiNode> c, Stamp stamp, AbstractMergeNode merge, ValueNode... values) {
        super(c, stamp, merge);
        assert stamp != StampFactory.forVoid();
        this.values = new NodeInputList<>(this, values);
    }

    @Override
    public InputType valueInputType() {
        return InputType.Value;
    }

    @Override
    public NodeInputList<ValueNode> values() {
        return values;
    }

    @Override
    public boolean inferStamp() {
        /*
         * Meet all the values feeding this Phi but don't use the stamp of this Phi since that's
         * what's being computed.
         */
        Stamp valuesStamp = StampTool.meetOrNull(values(), this);
        if (valuesStamp == null) {
            valuesStamp = stamp;
        }
        valuesStamp = tryInferLoopPhiStamp(valuesStamp);
        if (stamp.isCompatible(valuesStamp)) {
            valuesStamp = stamp.join(valuesStamp);
        }

        return updateStamp(valuesStamp);
    }

    /**
     * Tries to strengthen a loop phi's stamp by recursively taking other phis' inputs into account.
     * This is necessary for cases like the following, where we initially build the individual phis
     * with unrestricted object stamps:
     *
     * <pre>
     *     LoopBegin   <value of type C>
     *         .   \     |
     *         .   ValuePhi <-----------------------------+
     *         .        |                                 |
     *       Merge      |  <non-null value of type C>     |
     *            \     |  /                              |
     *             ValuePhi                               |
     *                  |                                 |
     *                  +---------------------------------+
     * </pre>
     *
     * By recursively looking through the loop phi's direct phi inputs, we can discover that only
     * values of type {@code C} can enter the cycle of mutually recursive phis, therefore the loop
     * phi's stamp must be of type {@code C} as well. Canonicalization will propagate this refined
     * stamp to the other phis in the cycle.
     * <p/>
     *
     * The implementation only considers pointer stamps, but it is generic. Besides types of object
     * stamps it derives any other relevant pointer stamp information. For example, the result will
     * be non-{@code null} if all recursive inputs are non-{@code null}.
     *
     * @param valuesStamp a stamp derived from this phi's direct inputs
     * @return a stronger stamp than {@code valuesStamp} if the recursive search found one;
     *         {@code valuesStamp} otherwise
     */
    private Stamp tryInferLoopPhiStamp(Stamp valuesStamp) {
        if (isAlive() && isLoopPhi() && valuesStamp.isPointerStamp()) {
            Stamp firstValueStamp = firstValue().stamp(NodeView.DEFAULT);
            if (firstValueStamp.meet(valuesStamp).equals(firstValueStamp)) {
                /*
                 * Even the first value's stamp is not more precise than the current stamp, we won't
                 * be able to refine anything.
                 */
                return valuesStamp;
            }
            boolean hasDirectPhiInput = false;
            for (ValueNode value : values()) {
                if (value instanceof ValuePhiNode) {
                    hasDirectPhiInput = true;
                    break;
                }
            }
            if (!hasDirectPhiInput) {
                // Nothing to recurse over.
                return valuesStamp;
            }
            Stamp currentStamp = firstValueStamp;
            // Check input phis recursively.
            NodeFlood flood = new NodeFlood(graph());
            flood.addAll(values());
            for (Node node : flood) {
                if (node == this) {
                    // Don't use this value's stamp as that is what we are computing.
                } else if (node instanceof ValuePhiNode phi) {
                    flood.addAll(phi.values());
                } else if (node instanceof ValueNode value) {
                    currentStamp = currentStamp.meet(value.stamp(NodeView.DEFAULT));
                    if (currentStamp.equals(valuesStamp)) {
                        // We won't become more precise.
                        return valuesStamp;
                    }
                }
            }
            if (!currentStamp.equals(valuesStamp) && currentStamp.meet(valuesStamp).equals(valuesStamp)) {
                // Success: currentStamp is strictly more precise than valuesStamp.
                return currentStamp;
            }
        }
        return valuesStamp;
    }

    @Override
    public boolean verify() {
        Stamp s = null;
        for (ValueNode input : values()) {
            assert input != null;
            if (s == null) {
                s = input.stamp(NodeView.DEFAULT);
            } else {
                if (!s.isCompatible(input.stamp(NodeView.DEFAULT))) {
                    fail("Phi Input Stamps are not compatible. Phi:%s inputs:%s", this,
                                    CollectionsUtil.mapAndJoin(values(), x -> x.toString() + ":" + x.stamp(NodeView.DEFAULT), ", "));
                }
            }
        }
        return super.verify();
    }

    @Override
    protected String valueDescription() {
        return stamp(NodeView.DEFAULT).unrestricted().toString();
    }

    @Override
    public Map<Object, Object> getDebugProperties(Map<Object, Object> map) {
        Map<Object, Object> properties = super.getDebugProperties(map);
        properties.put("valueDescription", valueDescription());
        return properties;
    }

    @Override
    public PhiNode duplicateOn(AbstractMergeNode newMerge) {
        return graph().addWithoutUnique(new ValuePhiNode(stamp(NodeView.DEFAULT), newMerge));
    }

    @Override
    public ValuePhiNode duplicateWithValues(AbstractMergeNode newMerge, ValueNode... newValues) {
        return new ValuePhiNode(stamp(NodeView.DEFAULT), newMerge, newValues);
    }

    @Override
    public ProxyNode createProxyFor(LoopExitNode lex) {
        return graph().addWithoutUnique(new ValueProxyNode(this, lex));
    }
}
