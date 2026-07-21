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

import java.util.List;
import java.util.function.Consumer;

import org.graalvm.collections.EconomicSet;

import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.core.common.type.VoidStamp;
import jdk.graal.compiler.debug.DebugCloseable;
import jdk.graal.compiler.graph.Graph;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodeinfo.InputType;
import jdk.graal.compiler.nodes.AbstractBeginNode;
import jdk.graal.compiler.nodes.AbstractMergeNode;
import jdk.graal.compiler.nodes.ControlSinkNode;
import jdk.graal.compiler.nodes.DeadEndNode;
import jdk.graal.compiler.nodes.DeoptimizeNode;
import jdk.graal.compiler.nodes.EndNode;
import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.FixedWithNextNode;
import jdk.graal.compiler.nodes.FrameState;
import jdk.graal.compiler.nodes.IfNode;
import jdk.graal.compiler.nodes.Invoke;
import jdk.graal.compiler.nodes.InvokeWithExceptionNode;
import jdk.graal.compiler.nodes.LogicNode;
import jdk.graal.compiler.nodes.MergeNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.PhiNode;
import jdk.graal.compiler.nodes.ProfileData;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValuePhiNode;
import jdk.graal.compiler.nodes.java.ExceptionObjectNode;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.nodes.util.GraphUtil;
import jdk.graal.compiler.phases.common.priorityinline.InliningProvider;
import jdk.vm.ci.meta.DeoptimizationReason;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.SpeculationLog;

public class DevirtualizationUtil {
    /**
     * Creates a devirtualization cascade for a given virtual call.
     * <p>
     * A devirtualization cascade is a sequence of if-then-else checks, where each true branch
     * typically ends with a direct call, and each false branch does the subsequent check. The final
     * false branch is a deopt or a virtual call.
     * <p>
     * Here is an example of a virtual call:
     *
     * <pre>
     *       .          [..receiver..]
     *       |                |
     *    [invoke]-----[MethodCallTarget BaseType.method]
     *       |
     *       .
     * </pre>
     *
     * Here is an example of a devirtualization cascade that dispatches to three methods based on
     * the type of the receiver:
     *
     * <pre>
     *                .                    [..receiver..]
     *                |                      |       |
     *               [if]---[instanceof ImplType1]   |
     *               /  \                            |
     *              /    \                           |
     *             /      \                          |
     *            /        \                         |
     *           /          [if]---[instanceof ImplType2]
     *          /            | \
     *   [ direct invoke  ]  |  \
     *   [ImplType1.method]  |   \
     *          |            |    \
     *          |            |     \
     *          |            |      \
     *          |            |       \
     *          |            |        \
     *          |            |         \
     *          | [ direct invoke  ]  [ virtual invoke  ]
     *          | [ImplType2.invoke]  [ BaseType.method ]
     *          |            |          |
     *          \            |          |
     *           \           |          |
     *            \          |          /
     *             \         |         /
     *              \        |        /
     *               \       |       /
     *                \      |      /
     *                 \     |     /
     *                  \    |    /
     *                   \   |   /
     *                    [merge]
     *                       |
     *                       .
     * </pre>
     *
     * The above example shows a type-based check, but the check can generally be anything that
     * proves that the direct call is equivalent to the virtual call. Note that no inlining happens
     * in the cascade, that is up to the client of this utility to take care off.
     * <p>
     * </p>
     * Each of the checks tests the arguments of the invoke node (for example, using an address, the
     * receiver type-check, or the virtual-table lookup), to prove that in the case of a successful
     * check, the virtual call is equivalent to a direct call corresponding to that check. The check
     * and the creation of the direct-call block are abstracted in a {@link Devirtualization}
     * strategy object.
     *
     * @param invoke Invoke that represents a virtual call that must be devirtualized.
     * @param devirtualizations Non-empty list of devirtualization strategies that will get applied
     *            one after the other. The sum of the probabilities of the devirtualizations must be
     *            less than or equal to 1.0.
     * @param useDeoptAsFallback Whether fallback must be a deopt.
     * @param isFallbackPolymorphic If the fallback is not a deopt, should the fallback invoke be
     *            marked as a polymorphic invoke.
     * @param deoptSpeculation Speculation under which to emit the deopt, if deoptimization is
     *            enabled.
     * @param trackCanonicalizable Tracker for new nodes created during devirtualization.
     */
    @SuppressWarnings("try")
    public static void createDevirtualizationCascade(CoreProviders coreProviders, InliningProvider inliningProvider, Invoke invoke,
                    List<Devirtualization> devirtualizations, boolean useDeoptAsFallback, boolean isFallbackPolymorphic, SpeculationLog.Speculation deoptSpeculation,
                    Consumer<EconomicSet<Node>> trackCanonicalizable) {
        // TODO(GR-42091): Merge useDeoptAsFallback and isFallbackPolymorphic to an enum with Deopt,
        // Monomorphic and Polymorphic.
        assert !devirtualizations.isEmpty() : "Devirtualizations must not be empty";

        // Setup merge and Phi nodes for results and exceptions.
        StructuredGraph graph = invoke.asNode().graph();
        Graph.Mark newNodesMark = graph.getMark();
        FixedNode continuation = invoke.next();
        MergeNode returnMerge = graph.add(new MergeNode());
        returnMerge.setStateAfter((FrameState) invoke.stateAfter().copyWithInputs());
        EndNode endNode = graph.add(new EndNode());
        invoke.setNext(endNode);
        returnMerge.addForwardEnd(endNode);
        returnMerge.setNext(continuation);

        PhiNode returnValuePhi = null;
        if (!(invoke.asNode().stamp(NodeView.DEFAULT) instanceof VoidStamp)) {
            returnValuePhi = graph.addWithoutUnique(new ValuePhiNode(invoke.asNode().stamp(NodeView.DEFAULT).unrestricted(), returnMerge));
            invoke.asNode().replaceAtUsages(returnValuePhi);
            returnValuePhi.addInput(invoke.asNode());
            invoke.stateAfter().replaceFirstInput(returnValuePhi, invoke.asNode());
        }

        AbstractMergeNode exceptionMerge = null;
        PhiNode exceptionObjectPhi = null;
        if (invoke instanceof InvokeWithExceptionNode) {
            InvokeWithExceptionNode invokeWithException = (InvokeWithExceptionNode) invoke;
            ExceptionObjectNode exceptionEdge = (ExceptionObjectNode) invokeWithException.exceptionEdge();
            exceptionMerge = graph.add(new MergeNode());
            FixedNode exceptionSux = exceptionEdge.next();
            EndNode exceptionEnd = graph.add(new EndNode());
            exceptionEdge.setNext(exceptionEnd);
            exceptionMerge.addForwardEnd(exceptionEnd);
            exceptionMerge.setNext(exceptionSux);
            exceptionObjectPhi = graph.addWithoutUnique(new ValuePhiNode(StampFactory.forKind(JavaKind.Object), exceptionMerge));

            exceptionEdge.replaceAtUsages(exceptionMerge, InputType.Anchor, InputType.Guard);
            exceptionEdge.replaceAtUsages(exceptionObjectPhi, InputType.Value);

            assert exceptionEdge.hasNoUsages() : "Must not have usages during rewire " + exceptionEdge;
            exceptionObjectPhi.addInput(exceptionEdge);
            exceptionEdge.stateAfter().replaceFirstInput(exceptionObjectPhi, exceptionEdge);

            int invokeBCI = invoke.bci();
            int exceptionEdgeBCI = exceptionEdge.stateAfter().bci;
            assert exceptionEdgeBCI == invokeBCI : exceptionEdgeBCI + "!=" + invokeBCI;
            assert exceptionEdge.stateAfter().rethrowException() : "Must be a rethrow state " + exceptionEdge.stateAfter();
            exceptionMerge.setStateAfter(exceptionEdge.stateAfter().duplicateModified(JavaKind.Object, JavaKind.Object, exceptionObjectPhi, null));
        }

        // Create one separate block for each invoked method.
        double totalProbability = 1.0;
        for (Devirtualization devirtualization : devirtualizations) {
            double invokeRelativeProbability = devirtualization.probability();
            double trueSuccessorProbability = Math.min(1.0, invokeRelativeProbability / totalProbability);
            totalProbability -= invokeRelativeProbability;
            devirtualizeAndCreateBranch(coreProviders, inliningProvider, graph, invoke, returnMerge, returnValuePhi, exceptionMerge, exceptionObjectPhi, trueSuccessorProbability,
                            devirtualization);
        }

        if (useDeoptAsFallback) {
            // We need no fallback => remove original invoke and replace with a deopt or dead end.
            ControlSinkNode sink;
            if (inliningProvider.areDeoptsAllowed()) {
                sink = graph.add(new DeoptimizeNode(InvalidateReprofile, DeoptimizationReason.TypeCheckedInliningViolated, deoptSpeculation));
            } else {
                sink = graph.add(new DeadEndNode());
            }
            FixedWithNextNode invokePred = (FixedWithNextNode) invoke.asNode().predecessor();
            invokePred.setNext(sink);
            GraphUtil.killCFG(invoke.asFixedNode());
        }
        invoke.setPolymorphic(isFallbackPolymorphic);

        if (trackCanonicalizable != null) {
            EconomicSet<Node> newNodes = EconomicSet.create();
            newNodes.addAll(graph.getNewNodes(newNodesMark));
            trackCanonicalizable.accept(newNodes);
        }
    }

    @SuppressWarnings("try")
    private static void devirtualizeAndCreateBranch(CoreProviders coreProviders, InliningProvider inliningProvider, StructuredGraph graph, Invoke invoke, MergeNode returnMerge, PhiNode returnValuePhi,
                    AbstractMergeNode exceptionMerge, PhiNode exceptionObjectPhi, double trueSuccessorProbability, Devirtualization devirtualization) {
        try (DebugCloseable ignored = graph.withNodeSourcePosition(devirtualization.callerPosition())) {
            AbstractBeginNode invocationBlock = devirtualization.createInvocationBlock(graph, invoke, returnMerge, returnValuePhi, exceptionMerge, exceptionObjectPhi, inliningProvider);
            LogicNode condition = devirtualization.createDevirtualizationCondition(coreProviders, inliningProvider, graph, invoke, invocationBlock);
            FixedWithNextNode invokePred = (FixedWithNextNode) invoke.asNode().predecessor();
            invokePred.setNext(null);
            ProfileData.ProfileSource source = graph.getProfilingInfo() != null && graph.getProfilingInfo().isMature() ? ProfileData.ProfileSource.PROFILED : ProfileData.ProfileSource.UNKNOWN;
            IfNode ifNode = graph.add(new IfNode(condition, invocationBlock, invoke.asFixedNode(), ProfileData.BranchProbabilityData.create(trueSuccessorProbability, source)));
            invokePred.setNext(ifNode);
        }
    }

}
