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

import jdk.graal.compiler.core.common.type.ObjectStamp;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.core.common.type.TypeReference;
import jdk.graal.compiler.nodes.AbstractBeginNode;
import jdk.graal.compiler.nodes.Invoke;
import jdk.graal.compiler.nodes.LogicNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.PiNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.java.InstanceOfNode;
import jdk.graal.compiler.nodes.spi.CoreProviders;

import jdk.graal.compiler.phases.common.priorityinline.InliningProvider;

import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Devirtualization that dispatches a direct call based on a receiver-type check.
 */
public abstract class ReceiverBasedDevirtualization extends ToDirectCallDevirtualization {

    /**
     * The receiver type to check against to prove that the direct call is equivalent.
     */
    protected abstract ResolvedJavaType dispatchedType();

    /**
     * The duplicated direct invoke, which can be called only after
     * <code>createInvocationBlock</code> is called.
     */
    protected abstract Invoke duplicatedInvoke();

    @Override
    public LogicNode createDevirtualizationCondition(CoreProviders coreProviders, InliningProvider inliningProvider, StructuredGraph graph, Invoke virtualInvoke,
                    AbstractBeginNode invocationBlock) {
        ResolvedJavaType dispatchedType = dispatchedType();
        assert dispatchedType != null : "Dispatched type cannot be null for receiver-based devirtualization.";
        Invoke newInvoke = duplicatedInvoke();
        ValueNode receiver = newInvoke.callTarget().arguments().get(0);
        ObjectStamp piStamp = StampFactory.objectNonNull(TypeReference.createExactTrusted(dispatchedType));
        ValueNode nonNullReceiver = graph.addOrUnique(PiNode.create(receiver, piStamp, invocationBlock));
        LogicNode condition = graph.addOrUniqueWithInputs(InstanceOfNode.createHelper(piStamp, receiver, null, null));
        // Assert that if condition is constant "true" then we did not change the original
        // receiver.
        assert !condition.isTautology() || receiver == nonNullReceiver : "pi: " + piStamp + "; non-null receiver: " + nonNullReceiver.stamp(NodeView.DEFAULT);
        // The Pi must be on nonNullReceiver since it might contain Pi+guards necessary
        // to prove piStamp.
        newInvoke.callTarget().arguments().set(0, nonNullReceiver);
        return condition;
    }
}
