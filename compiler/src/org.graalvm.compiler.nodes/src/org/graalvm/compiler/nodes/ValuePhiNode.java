/*
 * Copyright (c) 2009, 2021, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.compiler.core.common.type.AbstractPointerStamp;
import org.graalvm.compiler.core.common.type.IntegerStamp;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.graph.NodeFlood;
import org.graalvm.compiler.graph.NodeInputList;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.calc.AddNode;
import org.graalvm.compiler.nodes.calc.BinaryArithmeticNode;
import org.graalvm.compiler.nodes.spi.CanonicalizerTool;
import org.graalvm.compiler.nodes.type.StampTool;
import org.graalvm.util.CollectionsUtil;

/**
 * Value {@link PhiNode}s merge data flow values at control flow merges.
 */
@NodeInfo(nameTemplate = "Phi({i#values}, {p#valueDescription})")
public class ValuePhiNode extends PhiNode {

    public static final NodeClass<ValuePhiNode> TYPE = NodeClass.create(ValuePhiNode.class);
    @Input protected NodeInputList<ValueNode> values;

    public ValuePhiNode(Stamp stamp, AbstractMergeNode merge) {
        this(TYPE, stamp, merge);
    }

    protected ValuePhiNode(NodeClass<? extends ValuePhiNode> c, Stamp stamp, AbstractMergeNode merge) {
        super(c, stamp, merge);
        assert stamp != StampFactory.forVoid();
        values = new NodeInputList<>(this);
    }

    public ValuePhiNode(Stamp stamp, AbstractMergeNode merge, ValueNode[] values) {
        super(TYPE, stamp, merge);
        assert stamp != StampFactory.forVoid();
        this.values = new NodeInputList<>(this, values);
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
        } else if (stamp.isCompatible(valuesStamp)) {
            valuesStamp = stamp.join(valuesStamp);
        }

        Stamp maybeNonNullStamp = tryInferNonNullStamp(valuesStamp);
        if (maybeNonNullStamp != valuesStamp) {
            valuesStamp = maybeNonNullStamp;
        }

        return updateStamp(valuesStamp);
    }

    /**
     * See if this phi has a pointer stamp and is part of a set of mutually recursive phis that only
     * have each other or non-null values as inputs. In this case we can infer a non-null stamp.
     *
     * @return a non-null version of the original {@code valuesStamp} if it could be improved, the
     *         unchanged {@code valuesStamp} otherwise
     */
    private Stamp tryInferNonNullStamp(Stamp valuesStamp) {
        if (isAlive() && isLoopPhi() && valuesStamp instanceof AbstractPointerStamp) {
            AbstractPointerStamp pointerStamp = (AbstractPointerStamp) valuesStamp;
            if (!pointerStamp.alwaysNull() && !pointerStamp.nonNull() && StampTool.isPointerNonNull(firstValue())) {
                // Fail early if this phi already has possibly null non-phi inputs.
                for (ValueNode value : values()) {
                    if (value == this || value instanceof ValuePhiNode) {
                        continue;
                    } else if (!StampTool.isPointerNonNull(value)) {
                        return valuesStamp;
                    }
                }
                // Check input phis recursively.
                NodeFlood flood = new NodeFlood(graph());
                flood.addAll(values().filter(ValuePhiNode.class));
                for (Node node : flood) {
                    if (node instanceof ValuePhiNode) {
                        for (ValueNode value : ((ValuePhiNode) node).values()) {
                            if (value == this || value instanceof ValuePhiNode) {
                                flood.add(value);
                            } else if (!StampTool.isPointerNonNull(value)) {
                                return valuesStamp;
                            }
                        }
                    }
                }
                // All transitive inputs are non-null.
                return ((AbstractPointerStamp) valuesStamp).asNonNull();
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
    public ProxyNode createProxyFor(LoopExitNode lex) {
        return graph().addWithoutUnique(new ValueProxyNode(this, lex));
    }

    @Override
    public ValueNode canonical(CanonicalizerTool tool) {
        ValueNode canonical = super.canonical(tool);

        if (canonical == this && isLoopPhi() && stamp(NodeView.DEFAULT) instanceof IntegerStamp) {
            ValueNode[] canonicalInputs = new ValueNode[valueCount()];
            canonicalInputs[0] = valueAt(0);
            boolean changedInput = false;
            for (int i = 1; i < valueCount(); i++) {
                ValueNode inputValue = valueAt(i);
                ValueNode value = inputValue;
                if (value instanceof AddNode && ((AddNode) value).isAssociative()) {
                    // Find repeated additions to simplify to phi = n * a + phi.
                    int count = 0;
                    AddNode add = (AddNode) value;
                    ValueNode addend = null;
                    if (add.getY() instanceof AddNode && ((AddNode) add.getY()).getX() == add.getX()) {
                        addend = ((AddNode) value).getX();
                        while (value instanceof AddNode && ((AddNode) value).getX() == addend) {
                            // phi = a + (a + ... + (a + phi))
                            count++;
                            value = ((AddNode) value).getY();
                        }
                    } else if (add.getX() instanceof AddNode && ((AddNode) add.getX()).getY() == add.getY()) {
                        addend = ((AddNode) value).getY();
                        while (value instanceof AddNode && ((AddNode) value).getY() == addend) {
                            // phi = ((phi + a) + ... + a)
                            count++;
                            value = ((AddNode) value).getX();
                        }
                    }
                    if (addend != null && count > 1 && value == this) {
                        ConstantNode n = ConstantNode.forIntegerStamp(stamp(NodeView.DEFAULT), count);
                        inputValue = BinaryArithmeticNode.add(BinaryArithmeticNode.mul(n, addend), this);
                        changedInput = true;
                    }
                }
                canonicalInputs[i] = inputValue;
            }
            if (changedInput) {
                canonical = new ValuePhiNode(stamp(NodeView.DEFAULT), merge(), canonicalInputs);
            }
        }

        return canonical;
    }
}
