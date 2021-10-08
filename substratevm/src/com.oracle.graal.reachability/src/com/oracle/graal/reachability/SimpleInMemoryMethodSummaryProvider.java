/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.graal.pointsto.BigBang;
import com.oracle.graal.pointsto.flow.AnalysisParsedGraph;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.meta.AnalysisUniverse;
import com.oracle.graal.pointsto.phases.InlineBeforeAnalysis;
import com.oracle.graal.pointsto.util.AnalysisError;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodes.CallTargetNode;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.Invoke;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.java.AccessFieldNode;
import org.graalvm.compiler.nodes.java.InstanceOfNode;
import org.graalvm.compiler.nodes.java.NewArrayNode;
import org.graalvm.compiler.nodes.java.NewInstanceNode;
import org.graalvm.compiler.nodes.java.NewMultiArrayNode;
import org.graalvm.util.GuardedAnnotationAccess;

import java.util.ArrayList;
import java.util.List;

public class SimpleInMemoryMethodSummaryProvider implements MethodSummaryProvider {

    private final AnalysisUniverse universe;

    public SimpleInMemoryMethodSummaryProvider(AnalysisUniverse universe) {
        this.universe = universe;
    }

    @Override
    public MethodSummary getSummary(BigBang bb, AnalysisMethod method) {
        if (method.isIntrinsicMethod()) {
            System.err.println("this is intrinsic: " + method);
// return MethodSummary.EMPTY;
        }
        AnalysisParsedGraph analysisParsedGraph = method.ensureGraphParsed(bb);
        if (analysisParsedGraph.getEncodedGraph() == null) {
            System.err.println("Encoded empty for " + method);
            return MethodSummary.EMPTY;
        }
        if (GuardedAnnotationAccess.isAnnotationPresent(method, Node.NodeIntrinsic.class)) {
            System.err.println("parsing an intrinsic: " + method);
// return MethodSummary.EMPTY;
        }

        StructuredGraph decoded = InlineBeforeAnalysis.decodeGraph(bb, method, analysisParsedGraph);

        if (decoded == null) {
            throw AnalysisError.shouldNotReachHere("Failed to decode a graph for " + method.format("%H.%n(%p)"));
        }

        // to preserve the graphs for compilation
        method.setAnalyzedGraph(decoded);

        List<AnalysisType> accessedTypes = new ArrayList<>();
        List<AnalysisType> instantiatedTypes = new ArrayList<>();
        List<AnalysisField> accessedFields = new ArrayList<>();
        List<AnalysisMethod> invokedMethods = new ArrayList<>();
        List<AnalysisMethod> implementationInvokedMethods = new ArrayList<>();
        List<JavaConstant> embeddedConstants = new ArrayList<>();
        for (Node n : decoded.getNodes()) {
            if (n instanceof NewInstanceNode) {
                NewInstanceNode node = (NewInstanceNode) n;
                instantiatedTypes.add(analysisType(node.instanceClass()));
            } else if (n instanceof NewArrayNode) {
                NewArrayNode node = (NewArrayNode) n;
                instantiatedTypes.add(analysisType(node.elementType()).getArrayClass());
            } else if (n instanceof NewMultiArrayNode) {
                NewMultiArrayNode node = (NewMultiArrayNode) n;
                ResolvedJavaType type = node.type();
                for (int i = 0; i < node.dimensionCount(); i++) {
                    instantiatedTypes.add(analysisType(type));
                    type = type.getComponentType();
                }
            } else if (n instanceof ConstantNode) {
                ConstantNode node = (ConstantNode) n;
                if (!(node.getValue() instanceof JavaConstant)) {
                    /*
                     * The bytecode parser sometimes embeds low-level VM constants for types into
                     * the high-level graph. Since these constants are the result of type lookups,
                     * these types are already marked as reachable. Eventually, the bytecode parser
                     * should be changed to only use JavaConstant.
                     */
                    continue;
                }
                embeddedConstants.add(((JavaConstant) node.getValue()));
            } else if (n instanceof InstanceOfNode) {
                InstanceOfNode node = (InstanceOfNode) n;
                accessedTypes.add(analysisType(node.type().getType()));
            } else if (n instanceof AccessFieldNode) {
                AccessFieldNode node = (AccessFieldNode) n;
                accessedFields.add(analysisField(node.field()));
            } else if (n instanceof Invoke) {
                Invoke node = (Invoke) n;
                CallTargetNode.InvokeKind kind = node.getInvokeKind();
                AnalysisMethod targetMethod = analysisMethod(node.getTargetMethod());
                if (targetMethod == null) {
                    continue;
                }
                if (kind.isDirect()) {
                    implementationInvokedMethods.add(targetMethod);
                } else {
                    invokedMethods.add(targetMethod);
                }
            }
        }
        return new MethodSummary(invokedMethods.toArray(new AnalysisMethod[0]), implementationInvokedMethods.toArray(new AnalysisMethod[0]),
                        accessedTypes.toArray(new AnalysisType[0]),
                        instantiatedTypes.toArray(new AnalysisType[0]), accessedFields.toArray(new AnalysisField[0]), embeddedConstants.toArray(new JavaConstant[0]));
    }

    private AnalysisType analysisType(ResolvedJavaType type) {
        return type instanceof AnalysisType ? ((AnalysisType) type) : universe.lookup(type);
    }

    private AnalysisMethod analysisMethod(ResolvedJavaMethod method) {
        return method instanceof AnalysisMethod ? ((AnalysisMethod) method) : universe.lookup(method);
    }

    private AnalysisField analysisField(ResolvedJavaField field) {
        return field instanceof AnalysisField ? ((AnalysisField) field) : universe.lookup(field);
    }
}
