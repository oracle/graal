/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.interpreter.ristretto.compile;

import java.lang.reflect.Field;
import java.util.LinkedList;
import java.util.List;

import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.hub.DynamicHubCompanion;
import com.oracle.svm.interpreter.metadata.InterpreterResolvedJavaMethod;
import com.oracle.svm.interpreter.metadata.InterpreterResolvedJavaType;
import com.oracle.svm.interpreter.metadata.InterpreterResolvedObjectType;
import com.oracle.svm.interpreter.ristretto.RistrettoOptions;
import com.oracle.svm.interpreter.ristretto.meta.RistrettoMethod;
import com.oracle.svm.interpreter.ristretto.meta.RistrettoType;
import com.oracle.svm.shared.util.ReflectionUtil;

import jdk.graal.compiler.core.common.calc.CanonicalCondition;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.FixedWithNextNode;
import jdk.graal.compiler.nodes.Invoke;
import jdk.graal.compiler.nodes.LogicNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.CompareNode;
import jdk.graal.compiler.nodes.extended.LoadHubNode;
import jdk.graal.compiler.nodes.java.LoadFieldNode;
import jdk.graal.compiler.nodes.java.LoadIndexedNode;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.common.priorityinline.DefaultInliningProvider;
import jdk.graal.compiler.phases.common.priorityinline.Expander;
import jdk.graal.compiler.phases.common.priorityinline.Inliner;
import jdk.graal.compiler.phases.common.priorityinline.PolicyFactory;
import jdk.graal.compiler.phases.common.priorityinline.tuning.DomainSpecificTuningPolicy;
import jdk.graal.compiler.phases.tiers.HighTierContext;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

/** Supplies Ristretto's closed priority-inlining policy and runtime-specific checks. */
public final class RistrettoInliningProvider extends DefaultInliningProvider {
    /** Minimum polymorphic dispatch width retained for Ristretto call sites. */
    public static final int RISTRETTO_MAX_POLYMORPHIC_DISPATCHES = 4;

    private static final Field DYNAMIC_HUB_COMPANION_FIELD = ReflectionUtil.lookupField(DynamicHub.class, "companion");
    private static final Field DYNAMIC_HUB_COMPANION_INTERPRETER_TYPE_FIELD = ReflectionUtil.lookupField(DynamicHubCompanion.class, "interpreterType");
    private static final Field INTERPRETER_RESOLVED_OBJECT_TYPE_VTABLE_HOLDER_FIELD = ReflectionUtil.lookupField(InterpreterResolvedObjectType.class, "vtableHolder");
    private static final Field VTABLE_HOLDER_VTABLE_FIELD = ReflectionUtil.lookupField(InterpreterResolvedObjectType.VTableHolder.class, "vtable");

    private final List<Invoke> rootInvokes;
    private StructuredGraph rootGraph;

    public RistrettoInliningProvider(List<Invoke> rootInvokes) {
        this.rootInvokes = rootInvokes == null ? null : new LinkedList<>(rootInvokes);
    }

    @Override
    public RistrettoPolicyFactory policy(@SuppressWarnings("unused") OptionValues options) {
        return new RistrettoPolicyFactory();
    }

    /** Stateless factory that must not capture Ristretto compilation state into the image heap. */
    static final class RistrettoPolicyFactory implements PolicyFactory {
        @Override
        public Expander.Policy createExpanderPolicy(OptionValues options, HighTierContext context) {
            return new Expander.DefaultPolicy();
        }

        @Override
        public Inliner.Policy createInlinerPolicy(OptionValues options) {
            return new Inliner.DefaultPolicy();
        }

        @Override
        public DomainSpecificTuningPolicy createTuningPolicy(OptionValues options) {
            return new DomainSpecificTuningPolicy();
        }
    }

    @Override
    public List<Invoke> rootInvokeAllowed(StructuredGraph graph) {
        if (rootInvokes == null) {
            return null;
        }
        if (rootGraph == null) {
            rootGraph = graph;
        }
        GraalError.guarantee(rootGraph == graph, "Ristretto invoke allow list can only be used for the root graph");
        return new LinkedList<>(rootInvokes);
    }

    @Override
    public boolean useMethodChecks(OptionValues options) {
        return RistrettoOptions.useDeoptimization();
    }

    @Override
    public ResolvedJavaMethod methodForDevirtualizationCheck(ResolvedJavaMethod originalTargetMethod, ResolvedJavaMethod targetMethod, ResolvedJavaMethod concreteMethod,
                    ResolvedJavaType receiverType) {
        RistrettoMethod checkedMethod = methodForRistrettoDevirtualizationCheck(originalTargetMethod, targetMethod, concreteMethod, receiverType);
        GraalError.guarantee(checkedMethod != null, "Ristretto method check requested for unsupported method: original=%s, target=%s, concrete=%s, receiver=%s",
                        originalTargetMethod, targetMethod, concreteMethod, receiverType);
        return checkedMethod;
    }

    @Override
    public boolean isMethodForDevirtualizationInTable(ResolvedJavaMethod originalTargetMethod, ResolvedJavaMethod targetMethod, ResolvedJavaMethod concreteMethod, ResolvedJavaType receiverType) {
        return methodForRistrettoDevirtualizationCheck(originalTargetMethod, targetMethod, concreteMethod, receiverType) != null;
    }

    @Override
    public boolean isSameMethodForDevirtualizationCheck(ResolvedJavaMethod existingMethod, ResolvedJavaMethod candidateMethod) {
        RistrettoMethod existingRistrettoMethod = asRistrettoMethod(existingMethod);
        RistrettoMethod candidateRistrettoMethod = asRistrettoMethod(candidateMethod);
        if (existingRistrettoMethod != null && candidateRistrettoMethod != null) {
            return existingRistrettoMethod.getInterpreterMethod().equals(candidateRistrettoMethod.getInterpreterMethod());
        }
        return super.isSameMethodForDevirtualizationCheck(existingMethod, candidateMethod);
    }

    @Override
    public LogicNode createMethodCheckCondition(CoreProviders coreProviders, StructuredGraph graph, Invoke virtualInvoke, ValueNode nonNullReceiver,
                    ResolvedJavaMethod checkedMethod, ResolvedJavaMethod concreteMethod, ResolvedJavaType receiverType) {
        RistrettoMethod checkedRistrettoMethod = asRistrettoMethod(checkedMethod);
        RistrettoMethod concreteRistrettoMethod = asRistrettoMethod(concreteMethod);
        GraalError.guarantee(checkedRistrettoMethod != null && concreteRistrettoMethod != null,
                        "Ristretto method check requires Ristretto methods: checked=%s, concrete=%s", checkedMethod, concreteMethod);

        ValueNode loadedInterpreterMethod = loadInterpreterVTableEntry(coreProviders, graph, virtualInvoke, nonNullReceiver, checkedRistrettoMethod.getInterpreterMethod().getVTableIndex());
        ConstantNode expectedInterpreterMethod = ConstantNode.forConstant(coreProviders.getSnippetReflection().forObject(concreteRistrettoMethod.getInterpreterMethod()),
                        coreProviders.getMetaAccess(), graph);
        return CompareNode.createCompareNode(graph, CanonicalCondition.EQ, loadedInterpreterMethod, expectedInterpreterMethod, null, NodeView.DEFAULT);
    }

    private static RistrettoMethod methodForRistrettoDevirtualizationCheck(ResolvedJavaMethod originalTargetMethod, ResolvedJavaMethod targetMethod, ResolvedJavaMethod concreteMethod,
                    ResolvedJavaType receiverType) {
        if (isInterfaceDispatch(originalTargetMethod) || isInterfaceDispatch(targetMethod)) {
            return null;
        }
        RistrettoMethod concreteRistrettoMethod = asRistrettoMethod(concreteMethod);
        if (isUsableRistrettoMethodCheck(concreteRistrettoMethod, receiverType)) {
            return concreteRistrettoMethod;
        }
        RistrettoMethod targetRistrettoMethod = asRistrettoMethod(targetMethod);
        if (isUsableRistrettoMethodCheck(targetRistrettoMethod, receiverType)) {
            return targetRistrettoMethod;
        }
        RistrettoMethod originalRistrettoMethod = asRistrettoMethod(originalTargetMethod);
        if (isUsableRistrettoMethodCheck(originalRistrettoMethod, receiverType)) {
            return originalRistrettoMethod;
        }
        return null;
    }

    private static boolean isInterfaceDispatch(ResolvedJavaMethod method) {
        return method != null && method.getDeclaringClass().isInterface();
    }

    private static boolean isUsableRistrettoMethodCheck(RistrettoMethod method, ResolvedJavaType receiverType) {
        if (method == null) {
            return false;
        }
        InterpreterResolvedJavaType interpreterReceiverType = receiverType instanceof RistrettoType rReceiverType ? rReceiverType.getInterpreterType() : null;
        if (interpreterReceiverType == null) {
            ResolvedJavaType declaringClass = method.getDeclaringClass();
            if (declaringClass instanceof RistrettoType rDeclaringType) {
                interpreterReceiverType = rDeclaringType.getInterpreterType();
            }
        }
        if (!(interpreterReceiverType instanceof InterpreterResolvedObjectType) || interpreterReceiverType.isArray()) {
            return false;
        }
        InterpreterResolvedJavaMethod interpreterMethod = method.getInterpreterMethod();
        if (interpreterMethod.getVTableIndex() < 0) {
            return false;
        }
        return !interpreterMethod.getDeclaringClass().isInterface();
    }

    private static RistrettoMethod asRistrettoMethod(ResolvedJavaMethod method) {
        return method instanceof RistrettoMethod rMethod ? rMethod : null;
    }

    private static ValueNode loadInterpreterVTableEntry(CoreProviders coreProviders, StructuredGraph graph, Invoke virtualInvoke, ValueNode nonNullReceiver, int vTableIndex) {
        MetaAccessProvider metaAccess = coreProviders.getMetaAccess();
        ValueNode hub = graph.unique(new LoadHubNode(coreProviders.getStampProvider(), nonNullReceiver));
        ValueNode companion = addFixedLoad(graph, virtualInvoke, LoadFieldNode.create(graph.getAssumptions(), hub, lookupField(metaAccess, DYNAMIC_HUB_COMPANION_FIELD)));
        ValueNode interpreterType = addFixedLoad(graph, virtualInvoke,
                        LoadFieldNode.create(graph.getAssumptions(), companion, lookupField(metaAccess, DYNAMIC_HUB_COMPANION_INTERPRETER_TYPE_FIELD)));
        ValueNode vtableHolder = addFixedLoad(graph, virtualInvoke,
                        LoadFieldNode.create(graph.getAssumptions(), interpreterType, lookupField(metaAccess, INTERPRETER_RESOLVED_OBJECT_TYPE_VTABLE_HOLDER_FIELD)));
        ValueNode vtable = addFixedLoad(graph, virtualInvoke, LoadFieldNode.create(graph.getAssumptions(), vtableHolder, lookupField(metaAccess, VTABLE_HOLDER_VTABLE_FIELD)));
        ValueNode vtableIndexNode = ConstantNode.forInt(vTableIndex, graph);
        ValueNode loadedMethod = LoadIndexedNode.create(graph.getAssumptions(), vtable, vtableIndexNode, null, JavaKind.Object, metaAccess, coreProviders.getConstantReflection());
        if (loadedMethod instanceof FixedWithNextNode fixedLoadedMethod) {
            loadedMethod = graph.add(fixedLoadedMethod);
            graph.addBeforeFixed(virtualInvoke.asFixedNode(), fixedLoadedMethod);
        } else {
            loadedMethod = graph.addOrUniqueWithInputs(loadedMethod);
        }
        return loadedMethod;
    }

    private static ResolvedJavaField lookupField(MetaAccessProvider metaAccess, Field field) {
        return metaAccess.lookupJavaField(field);
    }

    private static ValueNode addFixedLoad(StructuredGraph graph, Invoke virtualInvoke, LoadFieldNode loadFieldNode) {
        LoadFieldNode added = graph.add(loadFieldNode);
        graph.addBeforeFixed(virtualInvoke.asFixedNode(), added);
        return added;
    }

    @Override
    public boolean areDeoptsAllowed() {
        return RistrettoOptions.useDeoptimization();
    }

    @Override
    public boolean supportsExceptionHandlerMetadata(ResolvedJavaMethod method) {
        return method instanceof RistrettoMethod;
    }

    @Override
    public int getMaxPolymorphicDispatches(OptionValues options) {
        return Math.max(super.getMaxPolymorphicDispatches(options), RISTRETTO_MAX_POLYMORPHIC_DISPATCHES);
    }
}
