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

import jdk.graal.compiler.core.common.type.StampPair;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.nodes.CallTargetNode;
import jdk.graal.compiler.nodes.DirectCallTargetNode;
import jdk.graal.compiler.nodes.Invoke;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.serviceprovider.GraalServices;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

public interface InliningProvider {
    List<Invoke> rootInvokeAllowed(StructuredGraph graph);

    /*
     * Allow inlining of methods on uninitialized classes. In AOT compilation we can inline calls on
     * an uninitialized class since they are always dominated by an EnsureClassInitialized node.
     */
    boolean canInlineUninitialized();

    @SuppressWarnings("unused")
    default ResolvedJavaMethod methodForDevirtualizationCheck(ResolvedJavaMethod originalTargetMethod, ResolvedJavaMethod targetMethod, ResolvedJavaMethod concreteMethod,
                    ResolvedJavaType receiverType) {
        return concreteMethod.isInVirtualMethodTable(receiverType) ? concreteMethod : targetMethod;
    }

    @SuppressWarnings("unused")
    default boolean isMethodForDevirtualizationInTable(ResolvedJavaMethod originalTargetMethod, ResolvedJavaMethod targetMethod, ResolvedJavaMethod concreteMethod, ResolvedJavaType receiverType) {
        return targetMethod.isInVirtualMethodTable(receiverType) || concreteMethod.isInVirtualMethodTable(receiverType);
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
