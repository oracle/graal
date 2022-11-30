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
package com.oracle.graal.reachability;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.nodes.GraphEncoder;
import org.graalvm.compiler.nodes.Invoke;
import org.graalvm.compiler.nodes.StructuredGraph;

import com.oracle.graal.pointsto.flow.AnalysisParsedGraph;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisUniverse;
import com.oracle.graal.pointsto.meta.InvokeInfo;
import com.oracle.graal.pointsto.phases.InlineBeforeAnalysis;
import com.oracle.graal.pointsto.util.AnalysisError;

import jdk.vm.ci.code.BytecodePosition;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Reachability specific extension of AnalysisMethod. Contains mainly information necessary to
 * traverse the call graph - get callees and callers of the method.
 *
 * @see ReachabilityInvokeInfo
 */
public class ReachabilityAnalysisMethod extends AnalysisMethod {

    /**
     * Invokes inside this method.
     */
    private final List<InvokeInfo> invokeInfos = Collections.synchronizedList(new ArrayList<>());

    /**
     * Callers of this method.
     */
    private final List<BytecodePosition> calledFrom = Collections.synchronizedList(new ArrayList<>());

    /**
     * The first callee of this method, to construct the parsing context.
     */
    private BytecodePosition reason;

    public ReachabilityAnalysisMethod(AnalysisUniverse universe, ResolvedJavaMethod wrapped) {
        super(universe, wrapped);
    }

    @Override
    public void startTrackInvocations() {
    }

    void addInvoke(InvokeInfo invoke) {
        this.invokeInfos.add(invoke);
    }

    @Override
    public Collection<InvokeInfo> getInvokes() {
        return invokeInfos;
    }

    @Override
    public BytecodePosition getParsingReason() {
        return reason;
    }

    public void setReason(BytecodePosition reason) {
        GraalError.guarantee(this.reason == null, "Reason already set.");
        this.reason = reason;
    }

    @Override
    public List<BytecodePosition> getInvokeLocations() {
        return calledFrom;
    }

    public void addCaller(BytecodePosition bytecodePosition) {
        calledFrom.add(bytecodePosition);
    }

    @Override
    public boolean registerAsInvoked(Object invokeReason) {
        if (super.registerAsInvoked(invokeReason)) {
            if (!isStatic()) {
                getDeclaringClass().addInvokedVirtualMethod(this);
            }
            return true;
        }
        return false;
    }

    @Override
    public ReachabilityAnalysisType getDeclaringClass() {
        return ((ReachabilityAnalysisType) super.getDeclaringClass());
    }

    /**
     * Utility method which contains all the steps that have to be taken when parsing methods for
     * the analysis.
     */
    public static StructuredGraph getDecodedGraph(ReachabilityAnalysisEngine bb, ReachabilityAnalysisMethod method) {
        AnalysisParsedGraph analysisParsedGraph = method.ensureGraphParsed(bb);
        if (analysisParsedGraph.isIntrinsic()) {
            method.registerAsIntrinsicMethod("reachability analysis engine");
        }
        AnalysisError.guarantee(analysisParsedGraph.getEncodedGraph() != null, "Cannot provide  a summary for %s.", method.getQualifiedName());

        StructuredGraph decoded = InlineBeforeAnalysis.decodeGraph(bb, method, analysisParsedGraph);
        AnalysisError.guarantee(decoded != null, "Failed to decode a graph for %s.", method.getQualifiedName());

        bb.getHostVM().methodBeforeTypeFlowCreationHook(bb, method, decoded);

        // to preserve the graphs for compilation
        method.setAnalyzedGraph(GraphEncoder.encodeSingleGraph(decoded, AnalysisParsedGraph.HOST_ARCHITECTURE));

        return decoded;
    }

    /**
     * Returns a bytecode position for a given invoke.
     */
    public static BytecodePosition sourcePosition(Invoke node, ReachabilityAnalysisMethod method) {
        BytecodePosition position = node.asFixedNode().getNodeSourcePosition();
        if (position == null) {
            position = new BytecodePosition(null, method, node.bci());
        }
        return position;
    }
}
