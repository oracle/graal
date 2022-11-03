/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.pointsto.flow;

import static jdk.vm.ci.common.JVMCIError.shouldNotReachHere;

import java.util.ArrayList;
import java.util.List;

import org.graalvm.collections.EconomicMap;

import com.oracle.graal.pointsto.PointsToAnalysis;
import com.oracle.graal.pointsto.flow.context.AnalysisContext;
import com.oracle.graal.pointsto.meta.PointsToAnalysisMethod;
import com.oracle.graal.pointsto.util.AnalysisError;

public class MethodFlowsGraphClone extends MethodFlowsGraph {

    private final AnalysisContext context;
    private final MethodFlowsGraph originalFlowsGraph;

    public MethodFlowsGraphClone(PointsToAnalysisMethod method, AnalysisContext context) {
        super(method);
        this.context = context;
        this.originalFlowsGraph = method.getTypeFlow().getMethodFlowsGraph();
        assert this.originalFlowsGraph.isLinearized();
    }

    public AnalysisContext context() {
        return context;
    }

    public void cloneOriginalFlows(PointsToAnalysis bb) {
        assert context != null;

        /*
         * The original method flows represent the source for cloning.
         */
        assert originalFlowsGraph != null && originalFlowsGraph.isLinearized() : " Method " + this + " is not linearized";

        linearizedGraph = new TypeFlow<?>[originalFlowsGraph.linearizedGraph.length];

        // parameters
        parameters = new FormalParamTypeFlow[originalFlowsGraph.parameters.length];
        for (int i = 0; i < originalFlowsGraph.parameters.length; i++) {
            // copy the flow
            if (originalFlowsGraph.getParameter(i) != null) {
                parameters[i] = lookupCloneOf(bb, originalFlowsGraph.getParameter(i));
            }
        }

        nodeFlows = lookupClonesOf(bb, originalFlowsGraph.nodeFlows);
        returnFlow = originalFlowsGraph.getReturnFlow() != null ? lookupCloneOf(bb, originalFlowsGraph.getReturnFlow()) : null;
        instanceOfFlows = lookupClonesOf(bb, originalFlowsGraph.instanceOfFlows);
        miscEntryFlows = lookupClonesOf(bb, originalFlowsGraph.miscEntryFlows);
        invokeFlows = lookupClonesOf(bb, originalFlowsGraph.invokeFlows);

        /* At this point all the clones should have been created. */
        sealed = true;
    }

    private <K, V extends TypeFlow<?>> EconomicMap<K, V> lookupClonesOf(PointsToAnalysis bb, EconomicMap<K, V> original) {
        if (original == null) {
            return null;
        }
        EconomicMap<K, V> result = EconomicMap.create(original.size());
        var cursor = original.getEntries();
        while (cursor.advance()) {
            result.put(cursor.getKey(), lookupCloneOf(bb, cursor.getValue()));
        }
        return result;
    }

    private <V extends TypeFlow<?>> List<V> lookupClonesOf(PointsToAnalysis bb, List<V> original) {
        if (original == null) {
            return null;
        }
        List<V> result = new ArrayList<>(original.size());
        for (V value : original) {
            result.add(lookupCloneOf(bb, value));
        }
        return result;
    }

    @Override
    public void init(final PointsToAnalysis bb) {
        // the cloning mechanism does all the initialization
        throw AnalysisError.shouldNotReachHere();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends TypeFlow<?>> T lookupCloneOf(PointsToAnalysis bb, T original) {
        assert original != null : "Looking for the clone of a 'null' flow in " + this;
        assert !original.isClone() : "Looking for the clone of the already cloned flow " + original + " in " + this;
        assert !(original instanceof FieldTypeFlow) : "Trying to clone a field type flow";
        assert !(original instanceof ArrayElementsTypeFlow) : "Trying to clone an mixed elements type flow";

        if (original instanceof AllInstantiatedTypeFlow || original instanceof AllSynchronizedTypeFlow) {
            /* All instantiated is not cloneable. */
            return original;
        }
        if (original instanceof ProxyTypeFlow) {
            /* The ProxyTypeFlow is just a place holder in the original graph for its input. */
            return (T) ((ProxyTypeFlow) original).getInput();
        }

        int slot = original.getSlot();

        assert slot >= 0 && slot < linearizedGraph.length : "Slot index out of bounds " + slot + " : " + original + " [" + original.getSource() + "]";

        TypeFlow<?> clone = linearizedGraph[slot];
        if (clone == null) {

            if (sealed) {
                shouldNotReachHere("Trying to create a clone after the method flows have been sealed.");
            }

            // copy only makes a shallow copy of the original flows;
            // it does not copy it's uses or inputs (for those flows that have inputs)
            clone = original.copy(bb, this);
            assert slot == clone.getSlot();

            assert linearizedGraph[slot] == null : "Clone already exists: " + slot + " : " + original;
            linearizedGraph[slot] = clone;
        }

        return (T) clone;
    }

    public void linkClones(final PointsToAnalysis bb) {

        for (TypeFlow<?> original : originalFlowsGraph.linearizedGraph) {
            TypeFlow<?> clone = lookupCloneOf(bb, original);

            /*
             * Run initialization code for corner case type flows. This can be used to add link from
             * 'outside' into the graph.
             */
            clone.initFlow(bb);

            /* Link all 'internal' observers. */
            for (TypeFlow<?> originalObserver : original.getObservers()) {
                // only clone the original observers
                assert !(originalObserver instanceof AllInstantiatedTypeFlow);
                assert !(originalObserver.isClone());

                if (nonCloneableFlow(originalObserver)) {
                    clone.addObserver(bb, originalObserver);
                } else if (crossMethodUse(original, originalObserver)) {
                    // cross method uses (parameters, return and unwind) are linked by
                    // InvokeTypeFlow.linkCallee
                } else {
                    TypeFlow<?> clonedObserver = lookupCloneOf(bb, originalObserver);
                    clone.addObserver(bb, clonedObserver);
                }
            }

            /* Link all 'internal' uses. */
            for (TypeFlow<?> originalUse : original.getUses()) {
                // only clone the original uses
                assert !(originalUse instanceof AllInstantiatedTypeFlow);
                assert !(originalUse.isClone()) : "Original use " + originalUse + " should not be a clone. Reached from: " + original;

                if (nonCloneableFlow(originalUse)) {
                    clone.addUse(bb, originalUse);
                } else if (crossMethodUse(original, originalUse)) {
                    // cross method uses (parameters, return and unwind) are linked by
                    // InvokeTypeFlow.linkCallee
                } else {
                    TypeFlow<?> clonedUse = lookupCloneOf(bb, originalUse);
                    clone.addUse(bb, clonedUse);
                }
            }

            if (clone instanceof AbstractStaticInvokeTypeFlow) {
                /* Trigger the update for static invokes, there is no receiver to trigger it. */
                AbstractStaticInvokeTypeFlow invokeFlow = (AbstractStaticInvokeTypeFlow) clone;
                bb.postFlow(invokeFlow);
            }
        }
    }

    public static boolean nonCloneableFlow(TypeFlow<?> flow) {
        /*
         * References to field flows and to array elements flows are not part of the method itself;
         * field and indexed load and store flows will instead be cloned, and used to access the
         * field flow.
         */
        return flow instanceof FieldTypeFlow || flow instanceof ArrayElementsTypeFlow;
    }

    public static boolean crossMethodUse(TypeFlow<?> flow, TypeFlow<?> use) {
        /*
         * Formal returns and unwinds are method exit points. Formal parameters are entry points
         * into callees.
         */
        return flow instanceof FormalReturnTypeFlow || use instanceof FormalParamTypeFlow;
    }

    @Override
    public String toString() {
        return "MethodFlowsGraphClone<" + method.format("%h.%n(%p)") + " " + context + ">";
    }
}
