/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.graph.Graph.NodeEvent;
import org.graalvm.compiler.graph.Graph.NodeEventScope;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeInputList;
import org.graalvm.compiler.nodes.GraphState.StageFlag;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.ValuePhiNode;
import org.graalvm.compiler.nodes.ValueProxyNode;
import org.graalvm.compiler.nodes.calc.IntegerConvertNode;
import org.graalvm.compiler.nodes.calc.NarrowNode;
import org.graalvm.compiler.nodes.calc.ReinterpretNode;
import org.graalvm.compiler.nodes.calc.SignExtendNode;
import org.graalvm.compiler.nodes.calc.UnaryNode;
import org.graalvm.compiler.nodes.calc.ZeroExtendNode;
import org.graalvm.compiler.nodes.spi.CoreProviders;
import org.graalvm.compiler.nodes.virtual.VirtualArrayNode;
import org.graalvm.compiler.nodes.virtual.VirtualObjectNode;
import org.graalvm.compiler.nodes.virtual.VirtualObjectState;
import org.graalvm.compiler.phases.BasePhase;
import org.graalvm.compiler.phases.common.CanonicalizerPhase;
import org.graalvm.compiler.phases.common.util.EconomicSetNodeEventListener;
import org.graalvm.compiler.truffle.compiler.nodes.AnyExtendNode;

import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * This phase recognizes {@link ValuePhiNode phis} that are repeatedly converted back and forth with
 * lossless conversions. The main use case is i64 phis that actually contain i32/f32/f64 values and
 * that use {@link NarrowNode}, {@link ZeroExtendNode}, {@link SignExtendNode},
 * {@link AnyExtendNode} and {@link ReinterpretNode} for conversion. In loop nests and complex
 * control flow, multiple phis may need to be transformed as a group.<br/>
 *
 * In order to be considered, phis can only have constants, other phis in the group, or an
 * appropriate conversion as an input (see
 * {@link #isValidInput(ValueNode, UnaryNode, boolean, EconomicSet)}. Also, usages can only be other
 * phis in the group, appropriate conversions, or {@link VirtualObjectState}s (see
 * {@link #collectNodes(ValueNode, EconomicSet, UnaryNode, ResolvedJavaType)}).<br/>
 *
 * If there is any virtual object state usage in the group, the transformation cannot be a
 * {@link SignExtendNode}, because only zero extend can be expressed in virtual state.<br/>
 *
 * As this phase is intended to run on graphs that still contain proxies, it takes the proxies
 * between phis into account.<br/>
 *
 * <pre>
 * long v = 0L;
 * long v2 = Float.floatToRawIntBits(0f) & 0xFFFFFFFFL; // v2 = zeroextend(reinterpret(0.0))
 * for (...) {
 *   v = (((int) v) + 123) & 0xFFFFFFFFL; // v = zeroextend(narrow(v) + 123)
 *   v2 = Float.floatToRawIntBits(Float.intBitsToFloat((int) v2) + 1f) & 0xFFFFFFFFL; // v2 = zeroextend(reinterpret(reinterpret(narrow(v2)) + 1f))
 * }
 * float s = Float.intBitsToFloat((int) v2); // s = reinterpret(narrow(v2))
 * return (int) v; // narrow(v)
 * </pre>
 *
 * will be transformed to:
 *
 * <pre>
 * int v = 0;
 * float v2 = 0f;
 * for (...) {
 *   v = v + 123;
 *   v2 = v2 + 1f;
 * }
 * float s = v2;
 * return v;
 * </pre>
 */
public final class PhiTransformPhase extends BasePhase<CoreProviders> {

    /**
     * It's possible to construct corner cases that expose one opportunity after the other via
     * canonicalization, but we need to stop after a certain number of iterations to have a bound on
     * compile time.
     */
    private static final int MAX_ITERATIONS = 3;

    private final CanonicalizerPhase canonicalizer;

    public PhiTransformPhase(CanonicalizerPhase canonicalizer) {
        this.canonicalizer = canonicalizer;
    }

    private enum ValidTransformation {
        Valid,
        Invalid,
        WithState
    }

    /**
     * Collects all nodes that are (transitive) usages of the given node. This method also checks
     * whether the usages are valid, i.e., whether they contain only virtual object states,
     * transformations and other value/proxy nodes. Returns {@link ValidTransformation#Invalid} if
     * there are invalid usages, {@link ValidTransformation#Valid} if there are no invalid usages
     * and no virtual state usages, and {@link ValidTransformation#WithState} if there are no
     * invalid usages but virtual state usages.
     */
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
                return !usedInState;
            }
            if (value instanceof AnyExtendNode) {
                if (narrow.getResultBits() != AnyExtendNode.INPUT_BITS || narrow.getInputBits() != AnyExtendNode.OUTPUT_BITS) {
                    // not the exact reverse of the narrow
                    return false;
                }
                return true;
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
                if (value instanceof AnyExtendNode && narrow.getInputBits() == AnyExtendNode.OUTPUT_BITS && narrow.getResultBits() == AnyExtendNode.INPUT_BITS) {
                    return ((AnyExtendNode) value).getValue();
                }
                newValue = graph.unique(new NarrowNode(value, narrow.getInputBits(), narrow.getResultBits()));
            } else {
                assert transformation instanceof ReinterpretNode;
                newValue = graph.addOrUnique(ReinterpretNode.create(transformation.stamp(NodeView.DEFAULT), value, NodeView.DEFAULT));
            }
            // make sure the new value will be processed by the canonicalizer
            ec.changed(NodeEvent.INPUT_CHANGED, newValue);
        }
        return newValue;
    }

    private static boolean checkTransformedNode(StructuredGraph graph, EconomicSetNodeEventListener ec, ResolvedJavaType longClass, Node node) {
        if (node.getClass() == ValuePhiNode.class || node.getClass() == ValueProxyNode.class) {
            /*
             * "transformation" is either a NarrowNode or a ReinterpretNode and represents the
             * transformation that will be applied to the whole group of nodes.
             */
            UnaryNode transformation = null;
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
                    AnyExtendNode convert = ((ValuePhiNode) node).values().filter(AnyExtendNode.class).first();

                    if (reinterpret != null && convert == null) {
                        transformation = new ReinterpretNode(reinterpret.getValue().getStackKind(), (ValueNode) node);
                    } else if (reinterpret == null && convert != null) {
                        transformation = new NarrowNode((ValueNode) node, AnyExtendNode.OUTPUT_BITS, AnyExtendNode.INPUT_BITS);
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

            // initialize with unrestricted stamp and let the canonicalizer deal with it:
            Stamp stamp = transformation.stamp(NodeView.DEFAULT).unrestricted();
            for (ValueNode target : EconomicSet.create(nodes)) {
                ValueNode duplicate;
                if (target.getClass() == ValuePhiNode.class) {
                    ValuePhiNode phi = (ValuePhiNode) target;
                    NodeInputList<ValueNode> phiValues = phi.values();
                    ValueNode[] values = new ValueNode[phiValues.count()];
                    for (int i = 0; i < phiValues.count(); i++) {
                        values[i] = transformInputValue(graph, phiValues.get(i), transformation, nodes, ec);
                    }
                    ValuePhiNode duplicatePhi = graph.addWithoutUnique(new ValuePhiNode(stamp, phi.merge(), values));
                    nodes.add(duplicatePhi);
                    duplicate = duplicatePhi;
                } else {
                    ValueProxyNode proxy = (ValueProxyNode) target;
                    duplicate = graph.addWithoutUnique(new ValueProxyNode(stamp, transformInputValue(graph, proxy.value(), transformation, nodes, ec), proxy.proxyPoint()));
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
                target.replaceAndDelete(duplicate);
            }
            return true;
        } else if (node.getClass() == AnyExtendNode.class) {
            AnyExtendNode extend = (AnyExtendNode) node;
            if (node.hasExactlyOneUsage() && isValidStateUsage(node.singleUsage(), longClass)) {
                node.replaceAtUsagesAndDelete(extend.getValue());
                return true;
            }
        } else if (node.getClass() == ReinterpretNode.class) {
            if (node.hasExactlyOneUsage() && isValidStateUsage(node.singleUsage(), longClass)) {
                node.replaceAtUsagesAndDelete(((ReinterpretNode) node).getValue());
                return true;
            }
        }
        return false;
    }

    @Override
    @SuppressWarnings("try")
    protected void run(StructuredGraph graph, CoreProviders context) {
        GraalError.guarantee(graph.isBeforeStage(StageFlag.VALUE_PROXY_REMOVAL), "not intended to run without loop proxies");
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
            } while (progress && iteration++ < MAX_ITERATIONS);
        }
        canonicalizer.applyIncremental(graph, context, ec.getNodes());
    }
}
