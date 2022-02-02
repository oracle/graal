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
package com.oracle.svm.hosted.phases;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodes.FixedNode;
import org.graalvm.compiler.nodes.FrameState;
import org.graalvm.compiler.nodes.FullInfopointNode;
import org.graalvm.compiler.nodes.Invoke;
import org.graalvm.compiler.nodes.InvokeNode;
import org.graalvm.compiler.nodes.InvokeWithExceptionNode;
import org.graalvm.compiler.nodes.PhiNode;
import org.graalvm.compiler.nodes.StateSplit;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.UnwindNode;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.extended.BytecodeExceptionNode;
import org.graalvm.compiler.nodes.extended.BytecodeExceptionNode.BytecodeExceptionKind;
import org.graalvm.compiler.nodes.java.MethodCallTargetNode;
import org.graalvm.compiler.nodes.java.NewInstanceNode;
import org.graalvm.compiler.nodes.spi.CoreProviders;
import org.graalvm.compiler.phases.BasePhase;
import org.graalvm.util.GuardedAnnotationAccess;

import com.oracle.svm.core.code.FactoryMethodMarker;
import com.oracle.svm.core.snippets.ImplicitExceptions;
import com.oracle.svm.util.ReflectionUtil;

import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Code that must be allocation free cannot throw new {@link AssertionError}. Therefore we convert
 * the allocation of the error and the constructor invocation to a {@link BytecodeExceptionNode}
 * that is later lowered to either the allocation-free or the allocating variants in
 * {@link ImplicitExceptions}.
 * 
 * We only intrinsify the two most common constructors: the nullary constructor (no parameters), and
 * the constructor that takes a single Object parameter. The other variants are not used in
 * low-level VM code that must be allocation free, and are rarely used in general.
 * 
 * A side-benefit of this phase is reduced code size of images with assertions enabled, since the
 * pretty complex machine code for allocation is not inlined at every assertion.
 */
public class ImplicitAssertionsPhase extends BasePhase<CoreProviders> {

    @Override
    protected void run(StructuredGraph graph, CoreProviders context) {
        if (GuardedAnnotationAccess.isAnnotationPresent(graph.method().getDeclaringClass(), FactoryMethodMarker.class)) {
            /*
             * Factory methods, which includes methods in ImplicitExceptions, are the methods that
             * actually perform the allocations at run time.
             */
            return;
        }

        HashMap<ResolvedJavaMethod, BytecodeExceptionKind> constructorReplacements = new HashMap<>();
        constructorReplacements.put(context.getMetaAccess().lookupJavaMethod(ReflectionUtil.lookupConstructor(AssertionError.class)), BytecodeExceptionKind.ASSERTION_ERROR_NULLARY);
        constructorReplacements.put(context.getMetaAccess().lookupJavaMethod(ReflectionUtil.lookupConstructor(AssertionError.class, Object.class)), BytecodeExceptionKind.ASSERTION_ERROR_OBJECT);

        for (Invoke invoke : graph.getInvokes()) {
            tryOptimize(graph, invoke, context, constructorReplacements);
        }
    }

    private static void tryOptimize(StructuredGraph graph, Invoke constructorInvoke, CoreProviders context, HashMap<ResolvedJavaMethod, BytecodeExceptionKind> constructorReplacements) {
        BytecodeExceptionKind bytecodeExceptionKind = constructorReplacements.get(constructorInvoke.getTargetMethod());
        if (bytecodeExceptionKind == null) {
            /* Not an optimizable exception constructor call. */
            return;
        }

        if (!(constructorInvoke.callTarget() instanceof MethodCallTargetNode)) {
            return;
        }
        MethodCallTargetNode callTargetNode = (MethodCallTargetNode) constructorInvoke.callTarget();
        if (!(callTargetNode.receiver() instanceof NewInstanceNode)) {
            return;
        }
        NewInstanceNode exceptionAllocation = (NewInstanceNode) callTargetNode.receiver();

        /*
         * Ensure that there is a simple control flow path from the constructor to the allocation of
         * the exception.
         */
        Set<FrameState> usagesToDelete = new HashSet<>();
        if (!hasSimpleControlFlow(constructorInvoke.predecessor(), exceptionAllocation, usagesToDelete)) {
            /*
             * No simple control flow path found. This can happen for example when a ...?...:...
             * conditional is used to construct the exception message. This case is not important
             * enough for now to handle.
             */
            return;
        }

        /*
         * Ensure that there are no usages of the allocation. Such usages could lead to Graal IR
         * that is not schedulable. We require a simple control flow path from the constructor to
         * either the UnwindNode of the graph, or a phi function that merges a simple control flow
         * path from the constructor.
         */
        for (Node exceptionUsage : exceptionAllocation.usages()) {
            if (exceptionUsage == callTargetNode || exceptionUsage == constructorInvoke.stateAfter()) {
                /* The constructor invocation that is going to be replaced. */
            } else if (usagesToDelete.contains(exceptionUsage)) {
                /* Frame state between constructor and allocation. */
            } else if (exceptionUsage instanceof UnwindNode) {
                if (!hasSimpleControlFlow(exceptionUsage, constructorInvoke.asFixedNode(), null)) {
                    /* No simple control flow path found to the UnwindNode. */
                    return;
                }
            } else if (exceptionUsage instanceof PhiNode) {
                PhiNode phi = (PhiNode) exceptionUsage;
                for (int i = 0; i < phi.valueCount(); i++) {
                    if (phi.valueAt(i) == exceptionAllocation && !hasSimpleControlFlow(phi.merge().phiPredecessorAt(i), constructorInvoke.asFixedNode(), null)) {
                        /* No simple control flow path found to the PhiNode. */
                        return;
                    }
                }
            } else if (exceptionUsage instanceof FrameState && exceptionUsage.usages().count() == 1 && exceptionUsage.usages().first() instanceof FullInfopointNode) {
                /*
                 * Infopoint for source-level debugging, can be cleared without any functional side
                 * effects.
                 */
                usagesToDelete.add((FrameState) exceptionUsage);
            } else {
                /* Any other kind of usage is disallowed. */
                return;
            }
        }

        /*
         * Success, we can optimize.
         */

        for (FrameState usageToDelete : usagesToDelete) {
            usageToDelete.replaceAllInputs(exceptionAllocation, null);
        }

        /*
         * The BytecodeExceptionNode replaces both the allocation and the constructor invocation. It
         * requires all original parameters of the exception constructor, except the receiver object
         * (the exception allocation).
         */
        List<ValueNode> args = new ArrayList<>(callTargetNode.arguments());
        args.remove(0);
        BytecodeExceptionNode replacement = graph.add(new BytecodeExceptionNode(context.getMetaAccess(), bytecodeExceptionKind, args.toArray(new ValueNode[0])));
        replacement.setStateAfter(constructorInvoke.stateAfter());

        InvokeNode invokeNode;
        if (constructorInvoke instanceof InvokeWithExceptionNode) {
            /*
             * Get rid of the exception edge of the constructor invocation. The easiest is to first
             * replace with an invocation without an exception edge, and in a second step replace
             * that with our actual BytecodeExceptionNode.
             */
            invokeNode = ((InvokeWithExceptionNode) constructorInvoke).replaceWithNonThrowing();
        } else {
            invokeNode = (InvokeNode) constructorInvoke;
        }
        graph.replaceFixedWithFixed(invokeNode, replacement);
        graph.replaceFixedWithFloating(exceptionAllocation, replacement);
    }

    private static boolean hasSimpleControlFlow(Node sink, FixedNode source, Set<FrameState> collectedFrameStates) {
        Node cur = sink;
        while (true) {
            if (cur == null) {
                return false;
            } else if (cur == source) {
                return true;
            }
            if (collectedFrameStates != null && cur instanceof StateSplit) {
                collectedFrameStates.add(((StateSplit) cur).stateAfter());
            }
            cur = cur.predecessor();
        }
    }
}
