/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.guards.optimistic;

import static jdk.vm.ci.meta.DeoptimizationAction.InvalidateRecompile;
import static jdk.vm.ci.meta.DeoptimizationAction.InvalidateReprofile;
import static jdk.vm.ci.meta.DeoptimizationReason.NullCheckException;
import static jdk.vm.ci.meta.DeoptimizationReason.OptimizedTypeCheckViolated;

import java.util.Optional;

import org.graalvm.collections.EconomicSet;

import jdk.graal.compiler.core.common.type.ObjectStamp;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.core.common.type.TypeReference;
import jdk.graal.compiler.debug.DebugCloseable;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.FixedGuardNode;
import jdk.graal.compiler.nodes.FrameState;
import jdk.graal.compiler.nodes.GraphState;
import jdk.graal.compiler.nodes.LogicNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.PiNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.ValuePhiNode;
import jdk.graal.compiler.nodes.calc.IsNullNode;
import jdk.graal.compiler.nodes.calc.PointerEqualsNode;
import jdk.graal.compiler.nodes.extended.LoadHubNode;
import jdk.graal.compiler.nodes.java.StoreIndexedNode;
import jdk.graal.compiler.nodes.spi.StampProvider;
import jdk.graal.compiler.nodes.spi.UncheckedInterfaceProvider;
import jdk.graal.compiler.nodes.type.StampTool;
import jdk.graal.compiler.nodes.util.GraphUtil;
import jdk.graal.compiler.phases.BasePhase;
import jdk.graal.compiler.phases.RecursivePhase;
import jdk.graal.compiler.phases.Speculative;
import jdk.graal.compiler.phases.tiers.HighTierContext;
import jdk.graal.compiler.serviceprovider.SpeculationReasonGroup;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.SpeculationLog;
import jdk.vm.ci.meta.SpeculationLog.SpeculationReason;

/**
 * This phase tries to remove array store-checks by speculating that the <b>declared</b> type of the
 * array is actually its <b>exact</b> type.
 *
 * <p>
 * When we have an exact type for the array, the array store check's outcome can be decided at
 * compile time. This is interesting because the speculation check (that verifies that array's type)
 * is very likely to be loop invariant and float out of loops that manipulate an array.
 * </p>
 *
 * <p>
 * A typical example are the generic collection types in the JDK that are backed by a
 * <code>T[]</code> which gets erased to <code>Object[]</code> such as {@link java.util.ArrayList}.
 * Setting an element in such a collection requires an array store check since the backing array
 * could be a sub-type of <code>Object[]</code>. However, in practice it's always exactly an
 * <code>Object[]</code>.
 * </p>
 */
public class SpeculativeStoreChecksPhase extends BasePhase<HighTierContext> implements Speculative, RecursivePhase {

    private static final SpeculationReasonGroup EXACT_ARRAY_TYPE_SPECULATIONS = new SpeculationReasonGroup("ExactArrayType", ResolvedJavaMethod.class, int.class);

    @Override
    public Optional<NotApplicable> notApplicableTo(GraphState graphState) {
        return NotApplicable.ifAny(
                        NotApplicable.withoutSpeculationLog(this, graphState),
                        NotApplicable.when(graphState.getGuardsStage().areDeoptsFixed(), "All deoptimizing nodes are already fixed"));
    }

    private static final Stamp INCONSISTENT_STAMP = StampFactory.empty(JavaKind.Object);

    @Override
    @SuppressWarnings("try")
    protected void run(StructuredGraph graph, HighTierContext context) {
        SpeculationLog speculationLog = graph.getSpeculationLog();
        StampProvider stampProvider = context.getStampProvider();
        MetaAccessProvider metaAccess = context.getMetaAccess();
        EconomicSet<ValueNode> phiStack = null;

        for (StoreIndexedNode storeIndexed : graph.getNodes().filter(StoreIndexedNode.class)) {
            try (DebugCloseable position = storeIndexed.withNodeSourcePosition()) {
                ValueNode array = storeIndexed.array();
                if (StampTool.isExactType(array)) {
                    continue;
                }
                ResolvedJavaType arrayType = StampTool.typeOrNull(array);
                if (arrayType == null || !arrayType.isArray()) {
                    continue;
                }
                if (StampTool.isPointerAlwaysNull(storeIndexed.value())) {
                    continue;
                }
                DebugContext debug = graph.getDebug();

                FrameState stateAfter = storeIndexed.stateAfter();
                SpeculationReason reason = EXACT_ARRAY_TYPE_SPECULATIONS.createSpeculationReason(stateAfter.getMethod(), stateAfter.bci);
                if (speculationLog.maySpeculate(reason)) {
                    if (phiStack == null) {
                        phiStack = EconomicSet.create();
                    } else {
                        phiStack.clear();
                    }
                    Stamp speculativeStamp = getSpeculativeStamp(array, phiStack, context.getMetaAccess());
                    if (speculativeStamp == null) {
                        speculativeStamp = StampFactory.objectNonNull(TypeReference.createExactTrusted(arrayType));
                    } else if (speculativeStamp == INCONSISTENT_STAMP) {
                        // don't speculate on inconsistent stamps
                        continue;
                    } else {
                        arrayType = StampTool.typeOrNull(speculativeStamp);
                        GraalError.guarantee(arrayType != null, "Array type from speculative stamp is null");
                    }
                    ValueNode nonNullArray = array;
                    ObjectStamp objectStamp = (ObjectStamp) array.stamp(NodeView.DEFAULT);
                    if (!objectStamp.nonNull()) {
                        LogicNode isNull = graph.unique(IsNullNode.create(array));
                        FixedGuardNode nullCheck = graph.add(new FixedGuardNode(isNull, NullCheckException, InvalidateReprofile, true));
                        graph.addBeforeFixed(storeIndexed, nullCheck);
                        nonNullArray = graph.unique(new PiNode(array, objectStamp.join(StampFactory.objectNonNull()), nullCheck));
                    }
                    ValueNode arrayClass = LoadHubNode.create(nonNullArray, stampProvider, metaAccess, context.getConstantReflection());
                    if (arrayClass.graph() == null) {
                        arrayClass = graph.addOrUniqueWithInputs(arrayClass);
                    }

                    ValueNode clazz = graph.unique(ConstantNode.forConstant(stampProvider.createHubStamp((ObjectStamp) speculativeStamp), context.getConstantReflection().asObjectHub(arrayType),
                                    metaAccess));

                    LogicNode objectEquals = graph.unique(PointerEqualsNode.create(arrayClass, clazz, NodeView.DEFAULT));
                    FixedGuardNode guard = graph.add(new FixedGuardNode(objectEquals, OptimizedTypeCheckViolated, InvalidateRecompile,
                                    speculationLog.speculate(reason), false));
                    graph.addBeforeFixed(storeIndexed, guard);
                    PiNode piArray = graph.unique(new PiNode(nonNullArray, speculativeStamp, guard));
                    storeIndexed.setArray(piArray);
                    graph.getOptimizationLog().report(getClass(), "SpeculativeStoreCheck", storeIndexed);
                } else {
                    debug.log("Can not speculate on exact array type for store check with array type = %s", arrayType);
                }
            }
        }
    }

    /**
     * May return a speculative stamp by searching for unchecked stamps through proxy and phi nodes.
     * Returns {@code null} if no unchecked stamp is available. For phis, an unchecked stamp is only
     * returned if at least one of its inputs has an unchecked stamp. All unchecked stamps from phi
     * inputs have to have the same type which also has to match the checked type of the remaining
     * phi inputs. An {@link #INCONSISTENT_STAMP} is returned for any contradiction, indicating,
     * that no speculation should be performed.
     *
     * <pre>
     * Common patterns:
     *
     * 1) Proxy
     *
     * UncheckedInterfaceProvider
     *              |
     *            Proxy
     *  --> return the unchecked stamp from the unproxified value
     *
     * 2) Phi with unchecked stamp
     *
     * UncheckedInterfaceProvider    OtherNode
     *              |                   |
     *              ---------------------
     *                         |
     *                        Phi
     *  --> return the unchecked stamp if OtherNode has the same type,
     *      INCONSISTENT_STAMP otherwise
     *
     * 3) Phi without unchecked stamp
     *
     *          OtherNode           OtherNode
     *              |                   |
     *              ---------------------
     *                         |
     *                        Phi
     *  --> return null (no unchecked stamp)
     *
     * 4) Phi with unchecked stamp and null
     *
     *  UncheckedInterfaceProvider   ConstNull
     *              |                   |
     *              ---------------------
     *                         |
     *                        Phi
     *  --> return unchecked stamp, ignore null constant
     *
     * 5) Recursive Phis             ... ------
     *                                 | |    |
     *  UncheckedInterfaceProvider     Phi1   |
     *              |                   |     |
     *              ---------------------     |
     *                         |              |
     *                        Phi2            |
     *                        | |             |
     *                      ... ---------------
     *  --> recursively resolves Phi1's stamp, but ignore the input from Phi2
     * </pre>
     */
    private Stamp getSpeculativeStamp(ValueNode value, EconomicSet<ValueNode> phiStack, MetaAccessProvider metaAccess) {
        ValueNode unproxified = GraphUtil.unproxify(value);
        if (unproxified instanceof UncheckedInterfaceProvider uncheckedInterfaceProvider) {
            return uncheckedInterfaceProvider.uncheckedStamp();
        } else if (unproxified instanceof ValuePhiNode phi) {
            phiStack.add(phi);

            Stamp maybeUncheckedStamp = null;
            Stamp curStamp;
            boolean uncheckedStampSeen = false;

            for (ValueNode input : phi.values()) {
                unproxified = GraphUtil.unproxify(input);
                if (phiStack.contains(unproxified)) {
                    // break up phi loops
                    continue;
                }

                curStamp = getSpeculativeStamp(unproxified, phiStack, metaAccess);
                if (curStamp == INCONSISTENT_STAMP) {
                    return INCONSISTENT_STAMP;
                } else if (curStamp == null) {
                    // use proven stamp
                    curStamp = input.stamp(NodeView.DEFAULT);
                } else {
                    uncheckedStampSeen = true;
                    GraalError.guarantee(!((ObjectStamp) curStamp).alwaysNull(), "Unchecked stamp must not be always null: %s", curStamp);
                }

                if (((ObjectStamp) curStamp).alwaysNull()) {
                    // no information to gain from null stamps
                    continue;
                }

                if (maybeUncheckedStamp == null) {
                    maybeUncheckedStamp = curStamp;
                } else if (!maybeUncheckedStamp.javaType(metaAccess).equals(curStamp.javaType(metaAccess))) {
                    return INCONSISTENT_STAMP;
                }
            }

            phiStack.remove(phi);

            if (uncheckedStampSeen && maybeUncheckedStamp != null && !maybeUncheckedStamp.javaType(metaAccess).equals(StampTool.typeOrNull(value, metaAccess))) {
                // all phi input's have the same (unchecked) type
                ObjectStamp valueStamp = (ObjectStamp) value.stamp(NodeView.DEFAULT);
                return new ObjectStamp((maybeUncheckedStamp.javaType(metaAccess)), valueStamp.isExactType(), valueStamp.nonNull(), valueStamp.alwaysNull(), valueStamp.isAlwaysArray());
            }
        }
        return null;
    }

    @Override
    public float codeSizeIncrease() {
        return 2.0f;
    }
}
