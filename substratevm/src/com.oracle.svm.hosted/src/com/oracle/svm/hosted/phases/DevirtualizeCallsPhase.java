/*
 * Copyright (c) 2017, 2017, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.svm.core.graal.snippets.DeoptHostedSnippets.AnalysisSpeculation;
import static com.oracle.svm.core.graal.snippets.DeoptHostedSnippets.AnalysisSpeculationReason;

import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.core.common.type.TypeReference;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.nodes.CallTargetNode.InvokeKind;
import org.graalvm.compiler.nodes.FixedGuardNode;
import org.graalvm.compiler.nodes.Invoke;
import org.graalvm.compiler.nodes.LogicConstantNode;
import org.graalvm.compiler.nodes.PiNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.extended.ValueAnchorNode;
import org.graalvm.compiler.phases.Phase;
import org.graalvm.compiler.phases.common.inlining.InliningUtil;

import com.oracle.svm.hosted.meta.HostedMethod;
import com.oracle.svm.hosted.nodes.SubstrateMethodCallTargetNode;

import jdk.vm.ci.meta.DeoptimizationAction;
import jdk.vm.ci.meta.DeoptimizationReason;
import jdk.vm.ci.meta.JavaMethodProfile;

/**
 * Devirtualize invokes based on Static Analysis results.
 * <p>
 * Specifically, if the Static Analysis determined that:
 * <ul>
 * <li>Invoke has no callees, it gets removed.
 * <li>Indirect invoke has a single callee, it gets converted to a special invoke.
 * </ul>
 */
public class DevirtualizeCallsPhase extends Phase {

    @Override
    protected void run(StructuredGraph graph) {
        for (Invoke invoke : graph.getInvokes()) {
            if (invoke.callTarget() instanceof SubstrateMethodCallTargetNode) {
                SubstrateMethodCallTargetNode callTarget = (SubstrateMethodCallTargetNode) invoke.callTarget();

                if (callTarget.invokeKind().isDirect() && !((HostedMethod) callTarget.targetMethod()).getWrapped().isSimplyImplementationInvoked()) {
                    /*
                     * This is a direct call to a method that the static analysis did not see as
                     * invoked. This can happen when the receiver is always null. In most cases, the
                     * method profile also has a length of 0 and the below code to kill the invoke
                     * would trigger. But not all methods have profiles, for example methods with
                     * manually constructed graphs.
                     */
                    unreachableInvoke(graph, invoke, callTarget);
                    continue;
                }

                JavaMethodProfile methodProfile = callTarget.getMethodProfile();
                if (methodProfile != null) {
                    if (methodProfile.getMethods().length == 0) {
                        unreachableInvoke(graph, invoke, callTarget);
                    } else if (methodProfile.getMethods().length == 1) {
                        if (callTarget.invokeKind().isIndirect()) {
                            singleCallee((HostedMethod) methodProfile.getMethods()[0].getMethod(), graph, invoke, callTarget);
                        }
                    }
                }
            }
        }
    }

    private static void unreachableInvoke(StructuredGraph graph, Invoke invoke, SubstrateMethodCallTargetNode callTarget) {
        /*
         * The invoke has no callee, i.e., it is unreachable. We just insert a always-failing guard
         * before the invoke and let dead code elimination remove the invoke and everything after
         * the invoke.
         */
        if (!callTarget.isStatic()) {
            InliningUtil.nonNullReceiver(invoke);
        }
        AnalysisSpeculation speculation = new AnalysisSpeculation(new AnalysisSpeculationReason("The call to " + callTarget.targetMethod().format("%H.%n(%P)") + " is not reachable."));
        FixedGuardNode node = new FixedGuardNode(LogicConstantNode.forBoolean(true, graph), DeoptimizationReason.UnreachedCode, DeoptimizationAction.None, speculation, true);
        graph.addBeforeFixed(invoke.asNode(), graph.add(node));
        graph.getDebug().dump(DebugContext.VERY_DETAILED_LEVEL, graph, "After dead invoke %s", invoke);
    }

    private static void singleCallee(HostedMethod singleCallee, StructuredGraph graph, Invoke invoke, SubstrateMethodCallTargetNode callTarget) {
        /*
         * The invoke has only one callee, i.e., the call can be devirtualized to this callee. This
         * allows later inlining of the callee.
         *
         * We have to be careful to guard the improvement of the receiver type that is implied by
         * the devirtualization: The callee assumes that the receiver type is the type that declares
         * the callee. While this is true for all parts of the callee, it does not necessarily hold
         * for all parts of the caller. So we need to ensure that after a possible inlining no parts
         * of the callee float out to parts of the caller where the receiver type assumption does
         * not hold. Since we do not know where in the caller a possible type check is performed, we
         * anchor the receiver to the place of the original invoke.
         */
        ValueAnchorNode anchor = graph.add(new ValueAnchorNode(null));
        graph.addBeforeFixed(invoke.asNode(), anchor);
        Stamp anchoredReceiverStamp = StampFactory.object(TypeReference.createWithoutAssumptions(singleCallee.getDeclaringClass()));
        ValueNode anchoredReceiver = graph.unique(new PiNode(invoke.getReceiver(), anchoredReceiverStamp, anchor));
        invoke.callTarget().replaceFirstInput(invoke.getReceiver(), anchoredReceiver);

        assert callTarget.invokeKind() == InvokeKind.Virtual || callTarget.invokeKind() == InvokeKind.Interface;
        callTarget.setInvokeKind(InvokeKind.Special);
        callTarget.setTargetMethod(singleCallee);
    }
}
