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
package jdk.graal.compiler.phases.common.priorityinline.nodes.devirtualization;

import static jdk.vm.ci.meta.DeoptimizationAction.InvalidateReprofile;
import static jdk.vm.ci.meta.DeoptimizationReason.NullCheckException;

import jdk.graal.compiler.core.common.type.ObjectStamp;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.core.common.type.TypeReference;
import jdk.graal.compiler.nodes.AbstractBeginNode;
import jdk.graal.compiler.nodes.FixedGuardNode;
import jdk.graal.compiler.nodes.Invoke;
import jdk.graal.compiler.nodes.LogicConstantNode;
import jdk.graal.compiler.nodes.LogicNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.PiNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.IsNullNode;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.nodes.type.StampTool;
import jdk.graal.compiler.phases.common.inlining.InliningUtil;

import jdk.graal.compiler.phases.common.priorityinline.InliningProvider;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Devirtualization that dispatches a direct call based on a method-check. This devirtualization
 * loads the method address from the vtable, and compares it against an expected dispatched method.
 */
public abstract class MethodBasedDevirtualization extends ToDirectCallDevirtualization {

    /**
     * Method on which the virtual invoke under consideration for devirtualization is targeting.
     */
    protected abstract ResolvedJavaMethod originalTargetMethod();

    /**
     * The duplicated direct invoke, which can be called only after
     * <code>createInvocationBlock</code> is called.
     */
    protected abstract Invoke duplicatedInvoke();

    @Override
    public LogicNode createDevirtualizationCondition(CoreProviders coreProviders, InliningProvider inliningProvider, StructuredGraph graph, Invoke virtualInvoke,
                    AbstractBeginNode invocationBlock) {
        ResolvedJavaMethod concreteMethod = dispatchedMethod();
        ResolvedJavaType receiverType = virtualInvoke.getReceiverType();
        Invoke newInvoke = duplicatedInvoke();
        ValueNode receiver = newInvoke.callTarget().arguments().get(0);

        LogicNode condition;
        ValueNode nonNullReceiver;
        ValueNode checkedReceiver;
        ObjectStamp piStamp;
        if (inliningProvider.isMethodForDevirtualizationInTable(originalTargetMethod(), virtualInvoke.getTargetMethod(), concreteMethod, receiverType)) {
            piStamp = StampFactory.objectNonNull(TypeReference.createWithoutAssumptions(dispatchedMethod().getDeclaringClass()));
            if (!StampTool.isPointerNonNull(receiver)) {
                LogicNode isNull = graph.unique(IsNullNode.create(receiver));
                FixedGuardNode fixedGuard = graph.add(new FixedGuardNode(isNull, NullCheckException, InvalidateReprofile, true));
                nonNullReceiver = graph.unique(new PiNode(receiver, StampFactory.objectNonNull(), fixedGuard));
                graph.addBeforeFixed(virtualInvoke.asFixedNode(), fixedGuard);
            } else {
                nonNullReceiver = receiver;
            }
            final ResolvedJavaMethod checkedMethod = inliningProvider.methodForDevirtualizationCheck(originalTargetMethod(), virtualInvoke.getTargetMethod(), concreteMethod, receiverType);
            condition = inliningProvider.createMethodCheckCondition(coreProviders, graph, virtualInvoke, nonNullReceiver, checkedMethod, concreteMethod, receiverType);
            checkedReceiver = graph.addOrUnique(PiNode.create(nonNullReceiver, piStamp, invocationBlock));
        } else {
            // The method is not in the virtual table of the receiver, indicating that the
            // receiver either has the bottom type (indicating that a value of this type
            // will not exist at this inline-site), or there is an error somewhere else
            // while creating this subgraph.
            piStamp = (ObjectStamp) StampFactory.empty(JavaKind.Object);
            nonNullReceiver = InliningUtil.nonNullReceiver(virtualInvoke);
            condition = LogicConstantNode.contradiction(graph);
            checkedReceiver = nonNullReceiver;
        }
        // Assert that if condition is constant "true" then we did not change the original
        // receiver.
        assert !condition.isTautology() || receiver == checkedReceiver : "pi: " + piStamp + "; non-null receiver: " + nonNullReceiver.stamp(NodeView.DEFAULT);
        // The Pi must be on nonNullReceiver since it might contain Pi+guards necessary
        // to prove piStamp.
        newInvoke.callTarget().arguments().set(0, checkedReceiver);
        return condition;
    }
}
