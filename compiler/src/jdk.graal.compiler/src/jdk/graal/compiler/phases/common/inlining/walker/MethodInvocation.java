/*
 * Copyright (c) 2011, 2023, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.phases.common.inlining.walker;

import java.util.BitSet;

import jdk.graal.compiler.debug.Assertions;
import jdk.graal.compiler.nodes.CallTargetNode;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.java.MethodCallTargetNode;
import jdk.graal.compiler.phases.common.inlining.info.InlineInfo;
import jdk.graal.compiler.phases.common.inlining.info.elem.Inlineable;
import jdk.graal.compiler.phases.common.inlining.info.elem.InlineableGraph;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * <p>
 * An instance of this class denotes a callsite being analyzed for inlining.
 * </p>
 * <p>
 * Each element of the {@link InliningData} stack contains one such instance, the accompanying
 * {@link CallsiteHolder}s in that element represent feasible targets for the callsite in question.
 * </p>
 *
 * @see InliningData#moveForward()
 */
public class MethodInvocation {

    private final InlineInfo callee;
    private final double probability;
    private final double relevance;

    private int processedGraphs;

    /**
     * <p>
     * The immutable positions of freshly instantiated arguments (ie, positions in
     * <code>callee.invoke.callTarget.arguments</code>).
     * </p>
     *
     * <p>
     * A freshly instantiated argument is either:
     * <uL>
     * <li>an {@link InliningData#isFreshInstantiation(ValueNode)}</li>
     * <li>a fixed-param of the graph containing the callsite (ie, of <code>callee.graph()</code>
     * that contains <code>callee.invoke</code>)</li>
     * </uL>
     * </p>
     *
     * <p>
     * Given those positions, the {@link CallsiteHolderExplorable} instantiated in
     * {@link #buildCallsiteHolderForElement(int)} can determine which of <i>its</i> parameters are
     * fixed.
     * </p>
     */
    private final BitSet freshlyInstantiatedArguments;

    public MethodInvocation(InlineInfo info, double probability, double relevance, BitSet freshlyInstantiatedArguments) {
        this.callee = info;
        this.probability = probability;
        this.relevance = relevance;
        this.freshlyInstantiatedArguments = freshlyInstantiatedArguments;
    }

    public void incrementProcessedGraphs() {
        processedGraphs++;
        assert processedGraphs <= callee.numberOfMethods() : Assertions.errorMessageContext("processedGraphs", processedGraphs, "callee", callee);
    }

    public int processedGraphs() {
        assert processedGraphs <= callee.numberOfMethods() : Assertions.errorMessageContext("processedGraphs", processedGraphs, "callee", callee);
        return processedGraphs;
    }

    public int totalGraphs() {
        return callee.numberOfMethods();
    }

    public InlineInfo callee() {
        return callee;
    }

    public double probability() {
        return probability;
    }

    public double relevance() {
        return relevance;
    }

    public boolean isRoot() {
        return callee == null;
    }

    public CallsiteHolder buildCallsiteHolderForElement(int index) {
        Inlineable elem = callee.inlineableElementAt(index);
        assert elem instanceof InlineableGraph : Assertions.errorMessage(elem);
        InlineableGraph ig = (InlineableGraph) elem;
        final double invokeProbability = probability * callee.probabilityAt(index);
        final double invokeRelevance = relevance * callee.relevanceAt(index);
        return new CallsiteHolderExplorable(ig.getGraph(), invokeProbability, invokeRelevance, freshlyInstantiatedArguments, null);
    }

    @Override
    public String toString() {
        if (isRoot()) {
            return "<root>";
        }
        CallTargetNode callTarget = callee.invoke().callTarget();
        if (callTarget instanceof MethodCallTargetNode) {
            ResolvedJavaMethod calleeMethod = ((MethodCallTargetNode) callTarget).targetMethod();
            return calleeMethod.format("Invoke#%H.%n(%p)");
        } else {
            return "Invoke#" + callTarget.targetName();
        }
    }
}
