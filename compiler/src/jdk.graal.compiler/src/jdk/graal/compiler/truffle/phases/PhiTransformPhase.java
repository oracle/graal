/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
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

package jdk.graal.compiler.truffle.phases;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.EconomicSet;
import org.graalvm.collections.Equivalence;

import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.debug.Assertions;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.Graph.NodeEvent;
import jdk.graal.compiler.graph.Graph.NodeEventScope;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeInputList;
import jdk.graal.compiler.nodes.GraphState.StageFlag;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.ValuePhiNode;
import jdk.graal.compiler.nodes.ValueProxyNode;
import jdk.graal.compiler.nodes.calc.IntegerConvertNode;
import jdk.graal.compiler.nodes.calc.NarrowNode;
import jdk.graal.compiler.nodes.calc.ReinterpretNode;
import jdk.graal.compiler.nodes.calc.SignExtendNode;
import jdk.graal.compiler.nodes.calc.UnaryNode;
import jdk.graal.compiler.nodes.calc.ZeroExtendNode;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.nodes.virtual.VirtualArrayNode;
import jdk.graal.compiler.nodes.virtual.VirtualObjectNode;
import jdk.graal.compiler.nodes.virtual.VirtualObjectState;
import jdk.graal.compiler.phases.BasePhase;
import jdk.graal.compiler.phases.common.CanonicalizerPhase;
import jdk.graal.compiler.phases.common.util.EconomicSetNodeEventListener;
import jdk.graal.compiler.truffle.nodes.AnyExtendNode;
import jdk.graal.compiler.truffle.nodes.AnyNarrowNode;
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
 * long v2 = Float.floatToRawIntBits(0f) &amp; 0xFFFFFFFFL; // v2 = zeroextend(reinterpret(0.0))
 * for (...) {
 *   v = (((int) v) + 123) &amp; 0xFFFFFFFFL; // v = zeroextend(narrow(v) + 123)
 *   v2 = Float.floatToRawIntBits(Float.intBitsToFloat((int) v2) + 1f) &amp; 0xFFFFFFFFL; // v2 = zeroextend(reinterpret(reinterpret(narrow(v2)) + 1f))
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
            } else if (usage instanceof NarrowNode n1) {
                if (!(transformation instanceof NarrowNode n2)) {
                    return ValidTransformation.Invalid;
                }
                if (n1.getInputBits() != n2.getInputBits() || n1.getResultBits() != n2.getResultBits()) {
                    return ValidTransformation.Invalid;
                }
            } else if (usage instanceof AnyNarrowNode) {
                if (!(transformation instanceof NarrowNode n2)) {
                    return ValidTransformation.Invalid;
                }
                if (AnyNarrowNode.INPUT_BITS != n2.getInputBits() || AnyNarrowNode.OUTPUT_BITS != n2.getResultBits()) {
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
            assert transformation instanceof ReinterpretNode : Assertions.errorMessage(transformation);
            return value instanceof ReinterpretNode;
        }
        return false;
    }

    private static ValueNode transformInputValue(StructuredGraph graph, ValueNode value, UnaryNode transformation, EconomicSet<ValueNode> nodes,
                    EconomicMap<ValueNode, ValueNode> duplicates, EconomicSetNodeEventListener ec) {
        ValueNode newValue;
        if (nodes.contains(value)) {
            newValue = duplicates.get(value);
            GraalError.guarantee(newValue != null, "missing transformed duplicate for %s", value);
        } else {
            if (transformation instanceof NarrowNode) {
                NarrowNode narrow = (NarrowNode) transformation;
                if (value instanceof AnyExtendNode && narrow.getInputBits() == AnyExtendNode.OUTPUT_BITS && narrow.getResultBits() == AnyExtendNode.INPUT_BITS) {
                    return ((AnyExtendNode) value).getValue();
                }
                newValue = graph.unique(new NarrowNode(value, narrow.getInputBits(), narrow.getResultBits()));
            } else {
                assert transformation instanceof ReinterpretNode : Assertions.errorMessage(transformation);
                newValue = graph.addOrUnique(ReinterpretNode.create(transformation.stamp(NodeView.DEFAULT), value, NodeView.DEFAULT));
            }
            // make sure the new value will be processed by the canonicalizer
            ec.changed(NodeEvent.INPUT_CHANGED, newValue);
        }
        return newValue;
    }

    private static void replaceValidStateUsagesAndDelete(ValueNode oldValue, ValueNode newValue, ResolvedJavaType longClass) {
        for (Node usage : oldValue.usages()) {
            GraalError.guarantee(isValidStateUsage(usage, longClass), "expected only virtual state usages of a long array: %s", usage);
        }
        oldValue.replaceAtUsagesAndDeleteWithoutCheckingInvariants(newValue);
    }

    private static boolean isTransformationUsage(Node usage) {
        return usage instanceof NarrowNode || usage instanceof ReinterpretNode || usage instanceof AnyNarrowNode;
    }

    /**
     * Replaces all conversion usages of {@code target} with {@code duplicate}.
     */
    private static void replaceTransformationUsages(ValueNode target, ValueNode duplicate) {
        for (Node usage : target.usages().snapshot()) {
            if (usage.isAlive() && isTransformationUsage(usage)) {
                usage.replaceAtUsagesAndDelete(duplicate);
            }
        }
    }

    private static boolean checkTransformedNode(StructuredGraph graph, EconomicSetNodeEventListener ec, ResolvedJavaType longClass, Node node) {
        if (node.getClass() == ValuePhiNode.class || node.getClass() == ValueProxyNode.class) {
            /*
             * "transformation" is either a NarrowNode or a ReinterpretNode and represents the
             * transformation that will be applied to the whole group of nodes.
             */
            boolean narrowUsage = false;
            UnaryNode transformation = null;
            for (Node usage : node.usages()) {
                if (usage instanceof NarrowNode || usage instanceof ReinterpretNode) {
                    narrowUsage = usage instanceof NarrowNode;
                    if (transformation == null) {
                        transformation = (UnaryNode) usage;
                    } else {
                        return false;
                    }
                } else if (usage instanceof AnyNarrowNode) {
                    if (transformation == null) {
                        transformation = new NarrowNode((ValueNode) node, AnyNarrowNode.INPUT_BITS, AnyNarrowNode.OUTPUT_BITS);
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
                        transformation = new NarrowNode((ValueNode) node, AnyNarrowNode.INPUT_BITS, AnyNarrowNode.OUTPUT_BITS);
                    }
                }
                if (transformation == null) {
                    return false;
                }
            }
            assert transformation instanceof NarrowNode || transformation instanceof ReinterpretNode : transformation;

            // collect all nodes in this cluster and ensure that all their usages are valid
            EconomicSet<ValueNode> originalNodes = EconomicSet.create();
            ValidTransformation valid = collectNodes((ValueNode) node, originalNodes, transformation, longClass);
            if (valid == ValidTransformation.Invalid) {
                return false;
            } else if (valid == ValidTransformation.WithState && narrowUsage) {
                /*
                 * Phi has both state usages and a Narrow usage that does not come from Truffle
                 * Frame intrinsics. We need to preserve the wide value in frame states because
                 * deoptimization may not zero-extend the narrow value but fill the upper half with
                 * 0xdeaddeaf which the interpreted code may observe as a garbled long value.
                 *
                 * Implies that if the phi is associated with a Truffle frame slot, that slot is
                 * currently holding a long or double value (narrowed by the usage).
                 */
                return false;
            }

            // check that all inputs are valid
            for (ValueNode target : originalNodes) {
                if (target.getClass() == ValuePhiNode.class) {
                    for (ValueNode value : ((ValuePhiNode) target).values()) {
                        if (!isValidInput(value, transformation, valid == ValidTransformation.WithState, originalNodes)) {
                            return false;
                        }
                    }
                } else {
                    if (!isValidInput(((ValueProxyNode) target).value(), transformation, valid == ValidTransformation.WithState, originalNodes)) {
                        return false;
                    }
                }
            }

            // all preconditions are met, duplicate transformed nodes

            // initialize with unrestricted stamp and let the canonicalizer deal with it:
            Stamp stamp = transformation.stamp(NodeView.DEFAULT).unrestricted();
            EconomicMap<ValueNode, ValueNode> duplicates = EconomicMap.create(Equivalence.IDENTITY);
            for (ValueNode target : originalNodes) {
                if (target.getClass() == ValuePhiNode.class) {
                    ValuePhiNode phi = (ValuePhiNode) target;
                    duplicates.put(target, graph.addWithoutUnique(new ValuePhiNode(stamp, phi.merge())));
                }
            }
            boolean changed;
            do {
                changed = false;
                for (ValueNode target : originalNodes) {
                    if (target instanceof ValueProxyNode proxy && duplicates.get(target) == null) {
                        ValueNode proxyValue = proxy.value();
                        if (originalNodes.contains(proxyValue) && duplicates.get(proxyValue) == null) {
                            GraalError.guarantee(proxyValue instanceof ValueProxyNode, "expected missing duplicate only while resolving a proxy chain: %s -> %s",
                                            proxy, proxyValue);
                            continue;
                        }
                        duplicates.put(target, graph.addWithoutUnique(
                                        new ValueProxyNode(stamp, transformInputValue(graph, proxyValue, transformation, originalNodes, duplicates, ec), proxy.proxyPoint())));
                        changed = true;
                    }
                }
            } while (changed);
            for (ValueNode target : originalNodes) {
                ValueNode duplicate = duplicates.get(target);
                GraalError.guarantee(duplicate != null, "missing transformed duplicate for %s", target);
                if (target.getClass() == ValuePhiNode.class) {
                    ValuePhiNode phi = (ValuePhiNode) target;
                    ValuePhiNode duplicatePhi = (ValuePhiNode) duplicate;
                    NodeInputList<ValueNode> phiValues = phi.values();
                    for (int i = 0; i < phiValues.count(); i++) {
                        duplicatePhi.addInput(transformInputValue(graph, phiValues.get(i), transformation, originalNodes, duplicates, ec));
                    }
                }
                replaceTransformationUsages(target, duplicate);
            }
            /*
             * The originalNodes can now be deleted: disconnect them from each other and delete.
             * Because the conversion usages were replaced above and collectNodes rejected all other
             * external usages, all remaining usages after the disconnection below are valid state
             * usages.
             */
            for (ValueNode target : originalNodes) {
                target.replaceAtUsages(null, usage -> usage instanceof ValueNode && originalNodes.contains((ValueNode) usage));
            }
            for (ValueNode target : originalNodes) {
                replaceValidStateUsagesAndDelete(target, duplicates.get(target), longClass);
            }
            return true;
        } else if (node.getClass() == AnyExtendNode.class) {
            AnyExtendNode extend = (AnyExtendNode) node;
            if (node.hasExactlyOneUsage() && isValidStateUsage(node.singleUsage(), longClass)) {
                replaceValidStateUsagesAndDelete(extend, extend.getValue(), longClass);
                return true;
            }
        } else if (node.getClass() == ReinterpretNode.class) {
            if (node.hasExactlyOneUsage() && isValidStateUsage(node.singleUsage(), longClass)) {
                replaceValidStateUsagesAndDelete((ValueNode) node, ((ReinterpretNode) node).getValue(), longClass);
                return true;
            }
        } else if (node.getClass() == AnyNarrowNode.class) {
            AnyNarrowNode narrow = (AnyNarrowNode) node;
            if (node.hasExactlyOneUsage() && isValidStateUsage(node.singleUsage(), longClass)) {
                replaceValidStateUsagesAndDelete(narrow, narrow.getValue(), longClass);
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
