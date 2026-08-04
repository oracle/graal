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
package jdk.graal.compiler.phases.common.priorityinline;

import static jdk.graal.compiler.phases.common.priorityinline.PriorityInliningPhase.Options.MaxPolymorphicDispatches;

import java.util.List;

import jdk.graal.compiler.core.common.calc.CanonicalCondition;
import jdk.graal.compiler.core.common.type.StampPair;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.nodes.CallTargetNode;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.DirectCallTargetNode;
import jdk.graal.compiler.nodes.Invoke;
import jdk.graal.compiler.nodes.LogicNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.CompareNode;
import jdk.graal.compiler.nodes.extended.LoadHubNode;
import jdk.graal.compiler.nodes.extended.LoadMethodNode;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.nodes.spi.StampProvider;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.serviceprovider.GraalServices;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

public interface InliningProvider {
    List<Invoke> rootInvokeAllowed(StructuredGraph graph);

    /*
     * Allow inlining of methods on uninitialized classes. In AOT compilation we can inline calls on
     * an uninitialized class since they are always dominated by an EnsureClassInitialized node.
     */
    boolean canInlineUninitialized();

    /** Returns whether {@code method} exposes exception-handler metadata to the compiler. */
    default boolean supportsExceptionHandlerMetadata(@SuppressWarnings("unused") ResolvedJavaMethod method) {
        return true;
    }

    /**
     * Selects the method whose receiver-table entry is used to prove the direct target for a
     * devirtualized invoke. The result identifies the table entry to load and can therefore differ
     * from {@code concreteMethod}. It is used only after
     * {@link #isMethodForDevirtualizationInTable} succeeds.
     */
    @SuppressWarnings("unused")
    default ResolvedJavaMethod methodForDevirtualizationCheck(ResolvedJavaMethod originalTargetMethod, ResolvedJavaMethod targetMethod, ResolvedJavaMethod concreteMethod,
                    ResolvedJavaType receiverType) {
        return concreteMethod.isInVirtualMethodTable(receiverType) ? concreteMethod : targetMethod;
    }

    /**
     * Creates the condition that guards replacing {@code virtualInvoke} with a direct call to
     * {@code concreteMethod}. For a non-null receiver of {@code receiverType}, the condition is
     * true exactly when invoking {@code checkedMethod} on that receiver dispatches to
     * {@code concreteMethod}:
     *
     * <pre>
     * R nonNullReceiver = ...; // R is receiverType
     * nonNullReceiver.checkedMethod(); // dispatches to concreteMethod
     * </pre>
     *
     * {@code checkedMethod} identifies the receiver-table entry to load from
     * {@code nonNullReceiver}.
     * <p>
     * Implementations may add nodes before {@code virtualInvoke} while loading and comparing the
     * dispatch-table entry. The caller guarantees that {@code nonNullReceiver} is non-null.
     *
     * @return the condition guarding the direct call to {@code concreteMethod}
     */
    default LogicNode createMethodCheckCondition(CoreProviders coreProviders, StructuredGraph graph, Invoke virtualInvoke, ValueNode nonNullReceiver,
                    ResolvedJavaMethod checkedMethod, ResolvedJavaMethod concreteMethod, ResolvedJavaType receiverType) {
        StampProvider stampProvider = coreProviders.getStampProvider();
        MetaAccessProvider metaAccess = coreProviders.getMetaAccess();
        LoadHubNode hub = graph.unique(new LoadHubNode(stampProvider, nonNullReceiver));
        Constant methodConstant = concreteMethod.getEncoding();
        ConstantNode expectedMethod = ConstantNode.forConstant(stampProvider.createMethodStamp(), methodConstant, metaAccess, graph);
        ResolvedJavaType callerType = virtualInvoke.getContextType();
        LoadMethodNode method = graph.add(new LoadMethodNode(stampProvider.createMethodStamp(), checkedMethod, receiverType, callerType, hub));
        graph.addBeforeFixed(virtualInvoke.asFixedNode(), method);
        return CompareNode.createCompareNode(graph, CanonicalCondition.EQ, method, expectedMethod, null, NodeView.DEFAULT);
    }

    /** Returns whether a receiver-table entry can safely guard this devirtualization. */
    @SuppressWarnings("unused")
    default boolean isMethodForDevirtualizationInTable(ResolvedJavaMethod originalTargetMethod, ResolvedJavaMethod targetMethod, ResolvedJavaMethod concreteMethod, ResolvedJavaType receiverType) {
        return targetMethod.isInVirtualMethodTable(receiverType) || concreteMethod.isInVirtualMethodTable(receiverType);
    }

    /** Compares two methods using the identity relevant to receiver-table checks. */
    @SuppressWarnings("unused")
    default boolean isSameMethodForDevirtualizationCheck(ResolvedJavaMethod existingMethod, ResolvedJavaMethod candidateMethod) {
        return existingMethod.equals(candidateMethod);
    }

    @SuppressWarnings("unused")
    default boolean useMethodChecks(OptionValues options) {
        return true;
    }

    default PolicyFactory policy(OptionValues options) {
        return resolvePolicyFactory(PriorityInliningPhase.Options.PriorityInliningPolicy.getValue(options));
    }

    static PolicyFactory resolvePolicyFactory(String policy) {
        try {
            Iterable<PolicyFactory> policies = GraalServices.load(PolicyFactory.class);
            if (policy.isEmpty()) {
                PolicyFactory selected = null;
                for (PolicyFactory factory : policies) {
                    if (factory.isAllowed()) {
                        if (selected == null) {
                            selected = factory;
                        } else {
                            int p1 = selected.priority();
                            int p2 = factory.priority();
                            if (p1 == p2) {
                                throw new GraalError("Unique %s instances have same priority of %d: %s and %s",
                                                PolicyFactory.class.getName(), p1, selected, factory); // ExcludeFromJacocoGeneratedReport
                            }
                            if (p2 > p1) {
                                selected = factory;
                            }
                        }
                    }
                }
                GraalError.guarantee(selected != null, "no policy available");
                return selected;
            } else {
                for (PolicyFactory factory : policies) {
                    if (factory.getClass().getName().equals(policy)) {
                        return factory;
                    }
                }
                throw GraalError.shouldNotReachHere("Policy not found: " + policy); // ExcludeFromJacocoGeneratedReport
            }
        } catch (Exception e) {
            throw GraalError.shouldNotReachHere(e); // ExcludeFromJacocoGeneratedReport
        }
    }

    DirectCallTargetNode createDirectCallTarget(ValueNode[] toArray, StampPair returnStamp, JavaType[] signature, ResolvedJavaMethod dispatchedMethod, CallTargetNode.InvokeKind invokeKind);

    default int getMaxPolymorphicDispatches(OptionValues options) {
        return MaxPolymorphicDispatches.getValue(options);
    }

    default boolean areDeoptsAllowed() {
        return true;
    }
}
