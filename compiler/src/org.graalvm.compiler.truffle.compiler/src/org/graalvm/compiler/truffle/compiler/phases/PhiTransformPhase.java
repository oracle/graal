/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

package org.graalvm.compiler.truffle.compiler.phases;

import org.graalvm.collections.EconomicSet;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.graph.Graph.NodeEvent;
import org.graalvm.compiler.graph.Graph.NodeEventScope;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeInputList;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.ValuePhiNode;
import org.graalvm.compiler.nodes.ValueProxyNode;
import org.graalvm.compiler.nodes.calc.IntegerConvertNode;
import org.graalvm.compiler.nodes.calc.NarrowNode;
import org.graalvm.compiler.nodes.calc.ReinterpretNode;
import org.graalvm.compiler.nodes.calc.UnaryNode;
import org.graalvm.compiler.nodes.calc.ZeroExtendNode;
import org.graalvm.compiler.nodes.spi.CoreProviders;
import org.graalvm.compiler.nodes.virtual.VirtualArrayNode;
import org.graalvm.compiler.nodes.virtual.VirtualObjectNode;
import org.graalvm.compiler.nodes.virtual.VirtualObjectState;
import org.graalvm.compiler.phases.BasePhase;
import org.graalvm.compiler.phases.common.CanonicalizerPhase;
import org.graalvm.compiler.phases.common.util.EconomicSetNodeEventListener;
import org.graalvm.compiler.phases.util.GraphOrder;

import jdk.vm.ci.meta.ResolvedJavaType;

public final class PhiTransformPhase extends BasePhase<CoreProviders> {

    private final CanonicalizerPhase canonicalizer;

    public PhiTransformPhase(CanonicalizerPhase canonicalizer) {
        this.canonicalizer = canonicalizer;
    }

    private enum ValidTransformation {
        Valid,
        Invalid,
        WithState
    }

    private static ValidTransformation collectNodes(ValueNode node, EconomicSet<ValueNode> nodes, UnaryNode transformation, ResolvedJavaType longClass) {
        ValidTransformation valid = ValidTransformation.Valid;
        nodes.add(node);
        for (Node usage : node.usages()) {
            if (usage instanceof VirtualObjectState) {
                if (!isValidStateUsage(usage, longClass)) {
                    return ValidTransformation.Invalid;
                }
                valid = ValidTransformation.WithState;
            } else if (usage instanceof NarrowNode) {
                if (!(transformation instanceof NarrowNode)) {
                    return ValidTransformation.Invalid;
                }
                NarrowNode n1 = (NarrowNode) usage;
                NarrowNode n2 = (NarrowNode) transformation;
                if (n1.getInputBits() != n2.getInputBits() || n1.getResultBits() != n2.getResultBits()) {
                    return ValidTransformation.Invalid;
                }
            } else if (usage instanceof ReinterpretNode) {
                if (!(transformation instanceof ReinterpretNode)) {
                    return ValidTransformation.Invalid;
                }
            } else if (usage.getClass() == ValuePhiNode.class || usage.getClass() == ValueProxyNode.class) {
                if (nodes.add((ValueNode) usage)) {
                    ValidTransformation result = collectNodes((ValueNode) usage, nodes, transformation, longClass);
                    if (result == ValidTransformation.Invalid) {
                        return ValidTransformation.Invalid;
                    } else if (result == ValidTransformation.WithState) {
                        valid = result;
                    }
                }
            } else {
                return ValidTransformation.Invalid;
            }
        }
        return valid;
    }

    private static boolean isValidStateUsage(Node usage, ResolvedJavaType longClass) {
        if (usage instanceof VirtualObjectState) {
            VirtualObjectNode object = ((VirtualObjectState) usage).object();
            if (object instanceof VirtualArrayNode) {
                return longClass.equals(((VirtualArrayNode) object).componentType());
            }
        }
        return false;
    }

    private static boolean isValidInput(ValueNode value, UnaryNode transformation, boolean usedInState, EconomicSet<ValueNode> nodes) {
        if (nodes.contains(value)) {
            // unmodified loop phi or proxy
            return true;
        } else if (value.isJavaConstant()) {
            return true;
        }
        if (transformation instanceof NarrowNode) {
            NarrowNode narrow = (NarrowNode) transformation;
            if (value instanceof IntegerConvertNode<?>) {
                IntegerConvertNode<?> convert = (IntegerConvertNode<?>) value;
                if (convert.getInputBits() != narrow.getResultBits() || convert.getResultBits() != narrow.getInputBits()) {
                    // not the exact reverse of the narrow
                    return false;
                }
                return !usedInState || convert instanceof ZeroExtendNode;
            }
        } else {
            assert transformation instanceof ReinterpretNode;
            return value instanceof ReinterpretNode;
        }
        return false;
    }

    private static ValueNode transformInputValue(StructuredGraph graph, ValueNode value, UnaryNode transformation, EconomicSet<ValueNode> nodes, EconomicSetNodeEventListener ec) {
        ValueNode newValue;
        if (nodes.contains(value)) {
            newValue = value;
        } else {
            if (transformation instanceof NarrowNode) {
                NarrowNode narrow = (NarrowNode) transformation;
                newValue = graph.unique(new NarrowNode(value, narrow.getInputBits(), narrow.getResultBits()));
            } else {
                assert transformation instanceof ReinterpretNode;
                newValue = graph.maybeAddOrUnique(ReinterpretNode.create(transformation.stamp(NodeView.DEFAULT), value, NodeView.DEFAULT));
            }
            // make sure the new value will be processed by the canonicalizer
            ec.changed(NodeEvent.INPUT_CHANGED, newValue);
        }
        return newValue;
    }

    private static boolean checkTransformedNode(StructuredGraph graph, EconomicSetNodeEventListener ec, ResolvedJavaType longClass, Node node) {
        if (node.getClass() == ValuePhiNode.class || node.getClass() == ValueProxyNode.class) {
            UnaryNode transformation = null; // NarrowNode or ReinterpretNode
            for (Node usage : node.usages()) {
                if (usage instanceof NarrowNode || usage instanceof ReinterpretNode) {
                    if (transformation == null) {
                        transformation = (UnaryNode) usage;
                    } else {
                        return false;
                    }
                } else if (usage.getClass() == ValuePhiNode.class || usage.getClass() == ValueProxyNode.class || usage.getClass() == VirtualObjectState.class) {
                    // valid usage
                } else {
                    // invalid usage, no need to look further
                    return false;
                }
            }
            // can this phi/proxy be the root of an optimization opportunity?
            if (transformation == null) {
                // look at the inputs
                if (node.getClass() == ValuePhiNode.class) {
                    ReinterpretNode reinterpret = ((ValuePhiNode) node).values().filter(ReinterpretNode.class).first();
                    IntegerConvertNode<?> convert = ((ValuePhiNode) node).values().filter(IntegerConvertNode.class).first();

                    if (reinterpret != null && convert == null) {
                        transformation = new ReinterpretNode(reinterpret.getValue().getStackKind(), (ValueNode) node);
                    } else if (reinterpret == null && convert != null) {
                        if (convert.getInputBits() == 64 && convert.getResultBits() == 64) {
                            transformation = new NarrowNode((ValueNode) node, 64, 32);
                        }
                    }
                }
                if (transformation == null) {
                    return false;
                }
            }
            assert transformation instanceof NarrowNode || transformation instanceof ReinterpretNode;

            // collect all nodes in this cluster and ensure that all their usages are valid
            EconomicSet<ValueNode> nodes = EconomicSet.create();
            ValidTransformation valid = collectNodes((ValueNode) node, nodes, transformation, longClass);
            if (valid == ValidTransformation.Invalid) {
                return false;
            }

            // check that all inputs are valid
            for (ValueNode target : nodes) {
                if (target.getClass() == ValuePhiNode.class) {
                    for (ValueNode value : ((ValuePhiNode) target).values()) {
                        if (!isValidInput(value, transformation, valid == ValidTransformation.WithState, nodes)) {
                            return false;
                        }
                    }
                } else {
                    if (!isValidInput(((ValueProxyNode) target).value(), transformation, valid == ValidTransformation.WithState, nodes)) {
                        return false;
                    }
                }
            }

            // all preconditions are met, duplicate transformed nodes
            for (ValueNode target : EconomicSet.create(nodes)) {
                ValueNode duplicate;
                if (target.getClass() == ValuePhiNode.class) {
                    ValuePhiNode phi = (ValuePhiNode) target;
                    NodeInputList<ValueNode> phiValues = phi.values();
                    ValueNode[] values = new ValueNode[phiValues.count()];
                    for (int i = 0; i < phiValues.count(); i++) {
                        values[i] = transformInputValue(graph, phiValues.get(i), transformation, nodes, ec);
                    }
                    Stamp stamp = values[0].stamp(NodeView.DEFAULT).unrestricted();
                    ValuePhiNode duplicatePhi = graph.unique(new ValuePhiNode(stamp, phi.merge(), values));
                    nodes.add(duplicatePhi);
                    duplicate = duplicatePhi;
                } else {
                    ValueProxyNode proxy = (ValueProxyNode) target;
                    duplicate = proxy.duplicateOn(proxy.proxyPoint(), transformInputValue(graph, proxy.value(), transformation, nodes, ec));
                }
                nodes.add(duplicate);
                // now replace all usages of the original phi/proxy
                for (Node usage : target.usages()) {
                    if (usage instanceof NarrowNode || usage instanceof ReinterpretNode) {
                        usage.replaceAtUsagesAndDelete(duplicate);
                        // assumption: there's at most one such usage, so we can break
                        break;
                    }
                }
                if (duplicate != target) {
                    target.replaceAndDelete(duplicate);
                }
            }
            return true;
        } else if (node.getClass() == ZeroExtendNode.class) {
            ZeroExtendNode extend = (ZeroExtendNode) node;
            if (node.hasExactlyOneUsage() && extend.getInputBits() == 32 && extend.getResultBits() == 64 && isValidStateUsage(node.singleUsage(), longClass)) {
                node.replaceAtUsagesAndDelete(extend.getValue());
            }
        } else if (node.getClass() == ReinterpretNode.class) {
            if (node.hasExactlyOneUsage() && isValidStateUsage(node.singleUsage(), longClass)) {
                node.replaceAtUsagesAndDelete(((ReinterpretNode) node).getValue());
            }
        }
        return false;
    }

    @Override
    @SuppressWarnings("try")
    protected void run(StructuredGraph graph, CoreProviders context) {
        ResolvedJavaType longClass = context.getMetaAccess().lookupJavaType(long.class);
        EconomicSetNodeEventListener ec = new EconomicSetNodeEventListener();
        try (NodeEventScope nes = graph.trackNodeEvents(ec)) {
            boolean progress = false;
            int iteration = 0;
            do {
                if (!ec.getNodes().isEmpty()) {
                    canonicalizer.applyIncremental(graph, context, ec.getNodes());
                }
                ec.getNodes().clear();

                progress = false;
                for (Node node : graph.getNodes()) {
                    progress |= checkTransformedNode(graph, ec, longClass, node);
                }
            } while (progress && iteration++ < 3);
        }
        canonicalizer.applyIncremental(graph, context, ec.getNodes());
        assert GraphOrder.assertNonCyclicGraph(graph);
        assert GraphOrder.assertSchedulableGraph(graph);
    }

}
