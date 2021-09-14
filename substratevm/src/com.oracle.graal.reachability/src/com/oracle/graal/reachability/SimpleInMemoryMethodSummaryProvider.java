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

import com.oracle.graal.pointsto.flow.AnalysisParsedGraph;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisMetaAccess;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.meta.AnalysisUniverse;
import com.oracle.graal.pointsto.phases.InlineBeforeAnalysis;
import com.oracle.graal.pointsto.util.AnalysisError;
import com.oracle.svm.util.GuardedAnnotationAccess;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import org.graalvm.collections.EconomicSet;
import org.graalvm.compiler.core.common.spi.ForeignCallDescriptor;
import org.graalvm.compiler.core.common.spi.ForeignCallSignature;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodes.CallTargetNode;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.FrameState;
import org.graalvm.compiler.nodes.GraphEncoder;
import org.graalvm.compiler.nodes.Invoke;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.extended.ForeignCall;
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

import java.lang.reflect.Modifier;
import java.util.Optional;

/**
 * Extracts method summaries from methods by parsing their bytecode and walking the structured
 * graphs.
 */
public class SimpleInMemoryMethodSummaryProvider implements MethodSummaryProvider {

    protected final AnalysisUniverse universe;
    protected final AnalysisMetaAccess metaAccess;

    public SimpleInMemoryMethodSummaryProvider(AnalysisUniverse universe, AnalysisMetaAccess metaAccess) {
        this.universe = universe;
        this.metaAccess = metaAccess;
    }

    @Override
    public MethodSummary getSummary(ReachabilityAnalysisEngine bb, ReachabilityAnalysisMethod method) {
        AnalysisParsedGraph analysisParsedGraph = method.ensureGraphParsed(bb);
        if (analysisParsedGraph.isIntrinsic()) {
            method.registerAsIntrinsicMethod();
        }
        AnalysisError.guarantee(analysisParsedGraph.getEncodedGraph() != null, "Cannot provide  a summary for %s.", method.getQualifiedName());

        StructuredGraph decoded = InlineBeforeAnalysis.decodeGraph(bb, method, analysisParsedGraph);
        AnalysisError.guarantee(decoded != null, "Failed to decode a graph for %s.", method.getQualifiedName());

        bb.getHostVM().methodBeforeTypeFlowCreationHook(bb, method, decoded);

        // to preserve the graphs for compilation
        method.setAnalyzedGraph(GraphEncoder.encodeSingleGraph(decoded, AnalysisParsedGraph.HOST_ARCHITECTURE));

        return new Instance(bb).createSummaryFromGraph(decoded, method);
    }

    @Override
    public MethodSummary getSummary(ReachabilityAnalysisEngine bb, StructuredGraph graph) {
        return new Instance(bb).createSummaryFromGraph(graph, null);
    }

    /**
     * Callback for specialized subtypes to handle more nodes.
     */
    @SuppressWarnings("unused")
    protected void delegateNodeProcessing(Instance instance, Node node) {
    }

    private AnalysisType analysisType(JavaType type) {
        return type instanceof AnalysisType ? ((AnalysisType) type) : universe.lookup(type);
    }

    private AnalysisMethod analysisMethod(ResolvedJavaMethod method) {
        return method instanceof AnalysisMethod ? ((AnalysisMethod) method) : universe.lookup(method);
    }

    private AnalysisField analysisField(ResolvedJavaField field) {
        return field instanceof AnalysisField ? ((AnalysisField) field) : universe.lookup(field);
    }

    protected class Instance {
        public final EconomicSet<AnalysisType> accessedTypes = EconomicSet.create();
        public final EconomicSet<AnalysisType> instantiatedTypes = EconomicSet.create();
        public final EconomicSet<AnalysisField> readFields = EconomicSet.create();
        public final EconomicSet<AnalysisField> writtenFields = EconomicSet.create();
        public final EconomicSet<AnalysisMethod> invokedMethods = EconomicSet.create();
        public final EconomicSet<AnalysisMethod> implementationInvokedMethods = EconomicSet.create();
        public final EconomicSet<JavaConstant> embeddedConstants = EconomicSet.create();
        public final EconomicSet<AnalysisMethod> foreignCallTargets = EconomicSet.create();
        private final ReachabilityAnalysisEngine bb;

        public Instance(ReachabilityAnalysisEngine bb) {
            this.bb = bb;
        }

        private MethodSummary createSummaryFromGraph(StructuredGraph graph, ReachabilityAnalysisMethod method) {
            if (method != null) {
                boolean isStatic = Modifier.isStatic(method.getModifiers());
                int parameterCount = method.getSignature().getParameterCount(!isStatic);
                int offset = isStatic ? 0 : 1;
                for (int i = offset; i < parameterCount; i++) {
                    accessedTypes.add(analysisType(method.getSignature().getParameterType(i - offset, method.getDeclaringClass())));
                }

                accessedTypes.add(analysisType(method.getSignature().getReturnType(method.getDeclaringClass())));
            }

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
                } else if (n instanceof LoadFieldNode) {
                    LoadFieldNode node = (LoadFieldNode) n;
                    readFields.add(analysisField(node.field()));
                } else if (n instanceof StoreFieldNode) {
                    StoreFieldNode node = (StoreFieldNode) n;
                    writtenFields.add(analysisField(node.field()));
                } else if (n instanceof Invoke) {
                    Invoke node = (Invoke) n;
                    CallTargetNode.InvokeKind kind = node.getInvokeKind();
                    AnalysisMethod targetMethod = analysisMethod(node.getTargetMethod());
                    if (targetMethod == null || GuardedAnnotationAccess.isAnnotationPresent(targetMethod, Node.NodeIntrinsic.class)) {
                        continue;
                    }
                    if (method != null) {
                        method.addInvoke(new ReachabilityInvokeInfo(((ReachabilityAnalysisMethod) targetMethod), node.asFixedNode().getNodeSourcePosition(), kind.isDirect()));
                    }
                    if (kind.isDirect()) {
                        implementationInvokedMethods.add(targetMethod);
                    } else {
                        invokedMethods.add(targetMethod);
                    }
                } else if (n instanceof FrameState) {
                    FrameState node = (FrameState) n;
                    ResolvedJavaMethod frameMethod = node.getMethod();
                    if (frameMethod != null) {
                        /*
                         * All types referenced in (possibly inlined) frame states must be
                         * reachable, because these classes will be reachable from stack walking
                         * metadata. This metadata is only constructed after AOT compilation, so the
                         * image heap scanning during static analysis does not see these classes.
                         */
                        AnalysisMethod analysisMethod = analysisMethod(frameMethod);
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
                    handleForeignCall(((ForeignCall) n).getDescriptor());
                } else if (n instanceof UnaryMathIntrinsicNode) {
                    ForeignCallSignature signature = ((UnaryMathIntrinsicNode) n).getOperation().foreignCallSignature;
                    handleForeignCall(bb.getProviders().getForeignCalls().getDescriptor(signature));
                } else if (n instanceof BinaryMathIntrinsicNode) {
                    ForeignCallSignature signature = ((BinaryMathIntrinsicNode) n).getOperation().foreignCallSignature;
                    handleForeignCall(bb.getProviders().getForeignCalls().getDescriptor(signature));

                }
                delegateNodeProcessing(this, n);
            }

            return new MethodSummary(invokedMethods, implementationInvokedMethods, accessedTypes, instantiatedTypes, readFields, writtenFields, embeddedConstants, foreignCallTargets);
        }

        private void handleForeignCall(ForeignCallDescriptor descriptor) {
            Optional<AnalysisMethod> targetMethod = bb.getHostVM().handleForeignCall(descriptor, bb.getProviders().getForeignCalls());
            targetMethod.ifPresent(foreignCallTargets::add);
        }
    }
}
