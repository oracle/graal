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
import com.oracle.graal.pointsto.meta.AnalysisMetaAccess;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.meta.AnalysisUniverse;
import com.oracle.graal.pointsto.phases.InlineBeforeAnalysis;
import com.oracle.graal.pointsto.util.AnalysisError;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import org.graalvm.compiler.core.common.spi.ForeignCallDescriptor;
import org.graalvm.compiler.core.common.spi.ForeignCallSignature;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodes.CallTargetNode;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.FrameState;
import org.graalvm.compiler.nodes.Invoke;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.extended.ForeignCall;
import org.graalvm.compiler.nodes.java.AccessFieldNode;
import org.graalvm.compiler.nodes.java.InstanceOfNode;
import org.graalvm.compiler.nodes.java.LoadFieldNode;
import org.graalvm.compiler.nodes.java.NewArrayNode;
import org.graalvm.compiler.nodes.java.NewInstanceNode;
import org.graalvm.compiler.nodes.java.NewMultiArrayNode;
import org.graalvm.compiler.nodes.java.StoreFieldNode;
import org.graalvm.compiler.nodes.virtual.VirtualArrayNode;
import org.graalvm.compiler.nodes.virtual.VirtualInstanceNode;
import org.graalvm.compiler.replacements.nodes.BinaryMathIntrinsicNode;
import org.graalvm.compiler.replacements.nodes.MacroInvokable;
import org.graalvm.compiler.replacements.nodes.UnaryMathIntrinsicNode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class SimpleInMemoryMethodSummaryProvider implements MethodSummaryProvider {

    protected final AnalysisUniverse universe;
    protected final AnalysisMetaAccess metaAccess;

    public SimpleInMemoryMethodSummaryProvider(AnalysisUniverse universe, AnalysisMetaAccess metaAccess) {
        this.universe = universe;
        this.metaAccess = metaAccess;
    }

    @Override
    public MethodSummary getSummary(BigBang bb, AnalysisMethod method) {
        AnalysisParsedGraph analysisParsedGraph = method.ensureGraphParsed(bb);
        if (analysisParsedGraph.getEncodedGraph() == null) {
            System.err.println("Encoded empty for " + method);
            List<AnalysisType> accessedTypes = new ArrayList<>();
            try {
                accessedTypes = Arrays.stream(method.getParameters()).map(param -> analysisType(((ResolvedJavaType) param.getType()))).collect(Collectors.toList());
            } catch (UnsupportedOperationException ex) {
                ex.printStackTrace();
            }
            accessedTypes.add(analysisType((ResolvedJavaType) method.getSignature().getReturnType(null)));
            return MethodSummary.accessed(accessedTypes);
        }

        StructuredGraph decoded = InlineBeforeAnalysis.decodeGraph(bb, method, analysisParsedGraph);

        if (decoded == null) {
            throw AnalysisError.shouldNotReachHere("Failed to decode a graph for " + method.format("%H.%n(%p)"));
        }

        // to preserve the graphs for compilation
        method.setAnalyzedGraph(decoded);

        return new Instance().createSummaryFromGraph(decoded);
    }

    @Override
    public MethodSummary getSummary(BigBang bigBang, StructuredGraph graph) {
        return new Instance().createSummaryFromGraph(graph);
    }

    @SuppressWarnings("unused")
    protected void delegateNodeProcessing(Instance instance, Node node) {
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

    protected class Instance {
        public final List<AnalysisType> accessedTypes = new ArrayList<>();
        public final List<AnalysisType> instantiatedTypes = new ArrayList<>();
        public final List<AnalysisField> readFields = new ArrayList<>();
        public final List<AnalysisField> writtenFields = new ArrayList<>();
        public final List<AnalysisMethod> invokedMethods = new ArrayList<>();
        public final List<AnalysisMethod> implementationInvokedMethods = new ArrayList<>();
        public final List<JavaConstant> embeddedConstants = new ArrayList<>();
        public final List<ForeignCallDescriptor> foreignCallDescriptors = new ArrayList<>();
        public final List<ForeignCallSignature> foreignCallSignatures = new ArrayList<>();

        private MethodSummary createSummaryFromGraph(StructuredGraph graph) {
            for (Node n : graph.getNodes()) {
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
                } else if (n instanceof VirtualInstanceNode) {
                    VirtualInstanceNode node = (VirtualInstanceNode) n;
                    instantiatedTypes.add(analysisType(node.type()));
// for (ResolvedJavaField field : node.getFields()) {
// readFields.add(analysisField(field));
// writtenFields.add(analysisField(field));
// }
                } else if (n instanceof VirtualArrayNode) {
                    VirtualArrayNode node = (VirtualArrayNode) n;
                    instantiatedTypes.add(analysisType(node.componentType()).getArrayClass());
                } else if (n instanceof ConstantNode) {
                    ConstantNode node = (ConstantNode) n;
                    if (!(node.getValue() instanceof JavaConstant)) {
                        /*
                         * The bytecode parser sometimes embeds low-level VM constants for types
                         * into the high-level graph. Since these constants are the result of type
                         * lookups, these types are already marked as reachable. Eventually, the
                         * bytecode parser should be changed to only use JavaConstant.
                         */
                        continue;
                    }
                    embeddedConstants.add(((JavaConstant) node.getValue()));
                } else if (n instanceof InstanceOfNode) {
                    InstanceOfNode node = (InstanceOfNode) n;
                    accessedTypes.add(analysisType(node.type().getType()));
                } else if (n instanceof AccessFieldNode) {
                    if (n instanceof LoadFieldNode) {
                        LoadFieldNode node = (LoadFieldNode) n;
                        readFields.add(analysisField(node.field()));
                    } else if (n instanceof StoreFieldNode) {
                        StoreFieldNode node = (StoreFieldNode) n;
                        writtenFields.add(analysisField(node.field()));
                    } else {
                        throw AnalysisError.shouldNotReachHere("Unhalded AccessFieldNode Type");
                    }
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
                } else if (n instanceof FrameState) {
                    FrameState node = (FrameState) n;
                    ResolvedJavaMethod method = node.getMethod();
                    if (method != null) {
                        AnalysisMethod analysisMethod = analysisMethod(method);
                        accessedTypes.add(analysisMethod.getDeclaringClass());
                    }
                } else if (n instanceof MacroInvokable) {
                    MacroInvokable node = (MacroInvokable) n;
                    AnalysisMethod targetMethod = analysisMethod(node.getTargetMethod());
                    if (node.getInvokeKind().isDirect()) {
                        implementationInvokedMethods.add(targetMethod);
                    } else {
                        invokedMethods.add(targetMethod);
                    }
                } else if (n instanceof ForeignCall) {
                    foreignCallDescriptors.add(((ForeignCall) n).getDescriptor());
                } else if (n instanceof UnaryMathIntrinsicNode) {
                    foreignCallSignatures.add(((UnaryMathIntrinsicNode) n).getOperation().foreignCallSignature);
                } else if (n instanceof BinaryMathIntrinsicNode) {
                    foreignCallSignatures.add(((BinaryMathIntrinsicNode) n).getOperation().foreignCallSignature);
                }
                delegateNodeProcessing(this, n);
            }
            return new MethodSummary(invokedMethods.toArray(new AnalysisMethod[0]), implementationInvokedMethods.toArray(new AnalysisMethod[0]),
                            accessedTypes.toArray(new AnalysisType[0]),
                            instantiatedTypes.toArray(new AnalysisType[0]), readFields.toArray(new AnalysisField[0]), writtenFields.toArray(new AnalysisField[0]),
                            embeddedConstants.toArray(new JavaConstant[0]), foreignCallDescriptors.toArray(new ForeignCallDescriptor[0]), foreignCallSignatures.toArray(new ForeignCallSignature[0]));
        }
    }
}
