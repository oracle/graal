/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.max.graal.compiler.util;

import java.lang.reflect.*;
import java.util.*;

import com.oracle.max.cri.ci.*;
import com.oracle.max.cri.ri.*;
import com.oracle.max.criutils.*;
import com.oracle.max.graal.compiler.*;
import com.oracle.max.graal.cri.*;
import com.oracle.max.graal.graph.*;
import com.oracle.max.graal.nodes.*;
import com.oracle.max.graal.nodes.DeoptimizeNode.DeoptAction;
import com.oracle.max.graal.nodes.PhiNode.PhiType;
import com.oracle.max.graal.nodes.calc.*;
import com.oracle.max.graal.nodes.extended.*;
import com.oracle.max.graal.nodes.java.*;
import com.oracle.max.graal.nodes.java.MethodCallTargetNode.InvokeKind;
import com.oracle.max.graal.nodes.util.*;

public class InliningUtil {

    public interface InliningCallback {
        StructuredGraph buildGraph(RiResolvedMethod method);
        double inliningWeight(RiResolvedMethod caller, RiResolvedMethod method, Invoke invoke);
        void recordConcreteMethodAssumption(RiResolvedMethod method, RiResolvedType context, RiResolvedMethod impl);
    }

    public static String methodName(RiResolvedMethod method) {
        return CiUtil.format("%H.%n(%p):%r", method) + " (" + method.codeSize() + " bytes)";
    }

    private static String methodName(RiResolvedMethod method, Invoke invoke) {
        if (invoke != null && invoke.stateAfter() != null) {
            RiMethod parent = invoke.stateAfter().method();
            return parent.name() + "@" + invoke.bci() + ": " + CiUtil.format("%H.%n(%p):%r", method) + " (" + method.codeSize() + " bytes)";
        } else {
            return CiUtil.format("%H.%n(%p):%r", method) + " (" + method.codeSize() + " bytes)";
        }
    }

    /**
     * Represents an opportunity for inlining at the given invoke, with the given weight and level.
     * The weight is the amortized weight of the additional code - so smaller is better.
     * The level is the number of nested inlinings that lead to this invoke.
     */
    public abstract static class InlineInfo implements Comparable<InlineInfo> {
        public final Invoke invoke;
        public final double weight;
        public final int level;

        public InlineInfo(Invoke invoke, double weight, int level) {
            this.invoke = invoke;
            this.weight = weight;
            this.level = level;
        }

        @Override
        public int compareTo(InlineInfo o) {
            return (weight < o.weight) ? -1 : (weight > o.weight) ? 1 : 0;
        }

        protected static StructuredGraph getGraph(Invoke invoke, RiResolvedMethod concrete, InliningCallback callback) {
// TODO: Solve graph caching differently! GraphBuilderPhase.cachedGraphs.get(concrete);
//          if (graph != null) {
//              if (GraalOptions.TraceInlining) {
//                  TTY.println("Reusing graph for %s", methodName(concrete, invoke));
//              }
//          } else {
              if (GraalOptions.TraceInlining) {
                  TTY.println("Building graph for %s, locals: %d, stack: %d", methodName(concrete, invoke), concrete.maxLocals(), concrete.maxStackSize());
              }
              return callback.buildGraph(concrete);
//          }
        }

        public abstract boolean canDeopt();

        /**
         * Performs the inlining described by this object and returns the node that represents the return value of the
         * inlined method (or null for void methods and methods that have no non-exceptional exit).
         *
         * @param graph
         * @param runtime
         * @param callback
         */
        public abstract void inline(StructuredGraph graph, GraalRuntime runtime, InliningCallback callback);
    }

    /**
     * Represents an inlining opportunity where the compiler can statically determine a monomorphic target method and
     * therefore is able to determine the called method exactly.
     */
    private static class ExactInlineInfo extends InlineInfo {
        public final RiResolvedMethod concrete;

        public ExactInlineInfo(Invoke invoke, double weight, int level, RiResolvedMethod concrete) {
            super(invoke, weight, level);
            this.concrete = concrete;
        }

        @Override
        public void inline(StructuredGraph graph, GraalRuntime runtime, InliningCallback callback) {
            StructuredGraph calleeGraph = getGraph(invoke, concrete, callback);
            InliningUtil.inline(invoke, calleeGraph, true);
        }

        @Override
        public String toString() {
            return "exact inlining " + CiUtil.format("%H.%n(%p):%r", concrete);
        }

        @Override
        public boolean canDeopt() {
            return false;
        }
    }

    /**
     * Represents an inlining opportunity for which profiling information suggests a monomorphic receiver, but for which
     * the receiver type cannot be proven. A type check guard will be generated if this inlining is performed.
     */
    private static class TypeGuardInlineInfo extends InlineInfo {
        public final RiResolvedMethod concrete;
        public final RiResolvedType type;

        public TypeGuardInlineInfo(Invoke invoke, double weight, int level, RiResolvedMethod concrete, RiResolvedType type) {
            super(invoke, weight, level);
            this.concrete = concrete;
            this.type = type;
        }

        @Override
        public void inline(StructuredGraph graph, GraalRuntime runtime, InliningCallback callback) {
            // receiver null check must be before the type check
            InliningUtil.receiverNullCheck(invoke);
            ReadClassNode objectClass = graph.add(new ReadClassNode(invoke.callTarget().receiver()));
            IsTypeNode isTypeNode = graph.unique(new IsTypeNode(objectClass, type));
            FixedGuardNode guard = graph.add(new FixedGuardNode(isTypeNode));
            assert invoke.predecessor() != null;

            graph.addBeforeFixed(invoke.node(), objectClass);
            graph.addBeforeFixed(invoke.node(), guard);

            if (GraalOptions.TraceInlining) {
                TTY.println("inlining 1 method using 1 type check");
            }

            StructuredGraph calleeGraph = getGraph(invoke, concrete, callback);
            InliningUtil.inline(invoke, calleeGraph, false);
        }

        @Override
        public String toString() {
            return "type-checked inlining " + CiUtil.format("%H.%n(%p):%r", concrete);
        }

        @Override
        public boolean canDeopt() {
            return true;
        }
    }

    /**
     * Polymorphic inlining of m methods with n type checks (n >= m) in case that the profiling information suggests a reasonable
     * amounts of different receiver types and different methods.
     */
    private static class MultiTypeGuardInlineInfo extends InlineInfo {
        public final List<RiResolvedMethod> concretes;
        public final RiResolvedType[] types;
        public final int[] typesToConcretes;
        public final double[] probabilities;

        public MultiTypeGuardInlineInfo(Invoke invoke, double weight, int level, List<RiResolvedMethod> concretes, RiResolvedType[] types, int[] typesToConcretes, double[] probabilities) {
            super(invoke, weight, level);
            assert concretes.size() > 0 && concretes.size() <= types.length : "must have at least one method but no more than types methods";
            assert types.length == typesToConcretes.length && types.length == probabilities.length : "array length must match";

            this.concretes = concretes;
            this.types = types;
            this.typesToConcretes = typesToConcretes;
            this.probabilities = probabilities;
        }

        @Override
        public void inline(StructuredGraph graph, GraalRuntime runtime, InliningCallback callback) {
            MethodCallTargetNode callTargetNode = invoke.callTarget();
            int numberOfMethods = concretes.size();
            boolean hasReturnValue = callTargetNode.kind() != CiKind.Void;

            // receiver null check must be the first node
            InliningUtil.receiverNullCheck(invoke);

            // save node after invoke so that invoke can be deleted safely
            FixedNode continuation = invoke.next();
            invoke.setNext(null);

            // setup a merge node and a phi node for the result
            MergeNode merge = null;
            PhiNode returnValuePhi = null;
            Node returnValue = null;
            if (numberOfMethods > 1) {
                merge = graph.add(new MergeNode());
                merge.setNext(continuation);
                if (hasReturnValue) {
                    returnValuePhi = graph.unique(new PhiNode(callTargetNode.kind(), merge, PhiType.Value));
                    returnValue = returnValuePhi;
                }
            }

            // TODO (ch) do not create merge nodes if not necessary. Otherwise GraphVisualizer seems to loose half of the phase outputs?

            // create a separate block for each invoked method
            MergeNode[] successorMethods = new MergeNode[numberOfMethods];
            for (int i = 0; i < numberOfMethods; i++) {
                Invoke duplicatedInvoke = duplicateInvoke(invoke);
                MergeNode calleeEntryNode = graph.add(new MergeNode());
                calleeEntryNode.setNext(duplicatedInvoke.node());
                successorMethods[i] = calleeEntryNode;

                if (merge != null) {
                    EndNode endNode = graph.add(new EndNode());
                    duplicatedInvoke.setNext(endNode);
                    merge.addEnd(endNode);
                    if (returnValuePhi != null) {
                        returnValuePhi.addInput(duplicatedInvoke.node());
                    }
                } else {
                    duplicatedInvoke.setNext(continuation);
                    if (hasReturnValue) {
                        returnValue = (Node) duplicatedInvoke;
                    }
                }
            }

            // create a cascade of ifs with the type checks
            ReadClassNode objectClassNode = graph.add(new ReadClassNode(invoke.callTarget().receiver()));
            graph.addBeforeFixed(invoke.node(), objectClassNode);

            int lastIndex = types.length - 1;
            MergeNode tsux = successorMethods[typesToConcretes[lastIndex]];
            IsTypeNode isTypeNode = graph.unique(new IsTypeNode(objectClassNode, types[lastIndex]));
            EndNode endNode = graph.add(new EndNode());
            FixedGuardNode guardNode = graph.add(new FixedGuardNode(isTypeNode));
            guardNode.setNext(endNode);
            tsux.addEnd(endNode);

            FixedNode nextNode = guardNode;
            for (int i = lastIndex - 1; i >= 0; i--) {
                tsux = successorMethods[typesToConcretes[i]];
                isTypeNode = graph.unique(new IsTypeNode(objectClassNode, types[i]));
                endNode = graph.add(new EndNode());
                nextNode = graph.add(new IfNode(isTypeNode, endNode, nextNode, probabilities[i]));
                tsux.addEnd(endNode);
            }

            // replace the original invocation with a cascade of if nodes and replace the usages of invoke with the return value (phi or duplicatedInvoke)
            invoke.node().replaceAtUsages(returnValue);
            invoke.node().replaceAndDelete(nextNode);
            GraphUtil.killCFG(invoke.node());

            // do the actual inlining for every invocation
            for (int i = 0; i < successorMethods.length; i++) {
                MergeNode node = successorMethods[i];
                Invoke invokeForInlining = (Invoke) node.next();
                StructuredGraph calleeGraph = getGraph(invokeForInlining, concretes.get(i), callback);
                InliningUtil.inline(invokeForInlining, calleeGraph, false);
            }

            if (GraalOptions.TraceInlining) {
                TTY.println("inlining %d methods with %d type checks", numberOfMethods, types.length);
            }
        }

        private static Invoke duplicateInvoke(Invoke invoke) {
            Invoke result = (Invoke) invoke.node().copyWithInputs();
            if (invoke instanceof InvokeWithExceptionNode) {
               InvokeWithExceptionNode invokeWithException = (InvokeWithExceptionNode) invoke;
               BeginNode exceptionEdge = invokeWithException.exceptionEdge();
               ExceptionObjectNode exceptionObject = (ExceptionObjectNode) exceptionEdge.next();

               BeginNode newExceptionEdge = (BeginNode) exceptionEdge.copyWithInputs();
               ExceptionObjectNode newExceptionObject = (ExceptionObjectNode) exceptionObject.copyWithInputs();
               newExceptionEdge.setNext(newExceptionObject);
               newExceptionObject.setNext(exceptionObject.next());

               ((InvokeWithExceptionNode) result).setExceptionEdge(newExceptionEdge);
            }
            return result;
        }

        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder("type-checked inlining of multiple methods: ");
            for (int i = 0; i < concretes.size(); i++) {
                CiUtil.format("\n%H.%n(%p):%r", concretes.get(i));
            }
            return builder.toString();
        }

        @Override
        public boolean canDeopt() {
            return true;
        }
    }


    /**
     * Represents an inlining opportunity where the current class hierarchy leads to a monomorphic target method,
     * but for which an assumption has to be registered because of non-final classes.
     */
    private static class AssumptionInlineInfo extends ExactInlineInfo {
        public final RiResolvedType context;

        public AssumptionInlineInfo(Invoke invoke, double weight, int level, RiResolvedType context, RiResolvedMethod concrete) {
            super(invoke, weight, level, concrete);
            this.context = context;
        }

        @Override
        public void inline(StructuredGraph graph, GraalRuntime runtime, InliningCallback callback) {
            if (GraalOptions.TraceInlining) {
                String targetName = CiUtil.format("%H.%n(%p):%r", invoke.callTarget().targetMethod());
                String concreteName = CiUtil.format("%H.%n(%p):%r", concrete);
                TTY.println("recording concrete method assumption: %s on receiver type %s -> %s", targetName, context, concreteName);
            }
            callback.recordConcreteMethodAssumption(invoke.callTarget().targetMethod(), context, concrete);

            super.inline(graph, runtime, callback);
        }

        @Override
        public String toString() {
            return "inlining with assumption " + CiUtil.format("%H.%n(%p):%r", concrete);
        }

        @Override
        public boolean canDeopt() {
            return true;
        }
    }

    /**
     * Determines if inlining is possible at the given invoke node.
     * @param invoke the invoke that should be inlined
     * @param level the number of nested inlinings that lead to this invoke, or 0 if the invoke was part of the initial graph
     * @param runtime a GraalRuntime instance used to determine of the invoke can be inlined and/or should be intrinsified
     * @param callback a callback that is used to determine the weight of a specific inlining
     * @return an instance of InlineInfo, or null if no inlining is possible at the given invoke
     */
    public static InlineInfo getInlineInfo(Invoke invoke, int level, GraalRuntime runtime, CiAssumptions assumptions, InliningCallback callback) {
        if (!checkInvokeConditions(invoke)) {
            return null;
        }
        RiResolvedMethod parent = invoke.stateAfter().method();
        MethodCallTargetNode callTarget = invoke.callTarget();

        if (callTarget.invokeKind() == InvokeKind.Special || callTarget.targetMethod().canBeStaticallyBound()) {
            if (checkTargetConditions(callTarget.targetMethod())) {
                double weight = callback == null ? 0 : callback.inliningWeight(parent, callTarget.targetMethod(), invoke);
                return new ExactInlineInfo(invoke, weight, level, callTarget.targetMethod());
            }
            return null;
        }
        if (callTarget.receiver().exactType() != null) {
            RiResolvedType exact = callTarget.receiver().exactType();
            assert exact.isSubtypeOf(callTarget.targetMethod().holder()) : exact + " subtype of " + callTarget.targetMethod().holder();
            RiResolvedMethod resolved = exact.resolveMethodImpl(callTarget.targetMethod());
            if (checkTargetConditions(resolved)) {
                double weight = callback == null ? 0 : callback.inliningWeight(parent, resolved, invoke);
                return new ExactInlineInfo(invoke, weight, level, resolved);
            }
            return null;
        }
        RiResolvedType holder = callTarget.targetMethod().holder();

        if (callTarget.receiver().declaredType() != null) {
            RiResolvedType declared = callTarget.receiver().declaredType();
            // the invoke target might be more specific than the holder (happens after inlining: locals lose their declared type...)
            // TODO (ls) fix this
            if (declared != null && declared.isSubtypeOf(holder)) {
                holder = declared;
            }
        }
        // TODO (tw) fix this
        if (assumptions == null) {
            return null;
        }
        RiResolvedMethod concrete = holder.uniqueConcreteMethod(callTarget.targetMethod());
        if (concrete != null) {
            if (checkTargetConditions(concrete)) {
                double weight = callback == null ? 0 : callback.inliningWeight(parent, concrete, invoke);
                return new AssumptionInlineInfo(invoke, weight, level, holder, concrete);
            }
            return null;
        }

        RiProfilingInfo profilingInfo = parent.profilingInfo();
        RiTypeProfile typeProfile = profilingInfo.getTypeProfile(invoke.bci());
        if (typeProfile != null) {
            RiResolvedType[] types = typeProfile.getTypes();
            double[] probabilities = typeProfile.getProbabilities();
            if (types != null && probabilities != null && types.length > 0) {
                assert types.length == probabilities.length : "length must match";
                if (GraalOptions.InlineWithTypeCheck) {
                    // type check and inlining...
                    if (types.length == 1) {
                        RiResolvedType type = types[0];
                        concrete = type.resolveMethodImpl(callTarget.targetMethod());
                        if (concrete != null && checkTargetConditions(concrete)) {
                            double weight = callback == null ? 0 : callback.inliningWeight(parent, concrete, invoke);
                            return new TypeGuardInlineInfo(invoke, weight, level, concrete, type);
                        }
                        return null;
                    } else {
                        // TODO (ch) sort types by probability
                        // determine concrete methods and map type to specific method
                        ArrayList<RiResolvedMethod> concreteMethods = new ArrayList<>();
                        int[] typesToConcretes = new int[types.length];
                        for (int i = 0; i < types.length; i++) {
                            concrete = types[i].resolveMethodImpl(callTarget.targetMethod());

                            int index = concreteMethods.indexOf(concrete);
                            if (index < 0) {
                                index = concreteMethods.size();
                                concreteMethods.add(concrete);
                            }
                            typesToConcretes[index] = index;
                        }

                        double totalWeight = 0;
                        boolean canInline = true;
                        for (RiResolvedMethod method: concreteMethods) {
                            if (method == null || !checkTargetConditions(method)) {
                                canInline = false;
                                break;
                            }
                            totalWeight += callback == null ? 0 : callback.inliningWeight(parent, method, invoke);
                        }

                        if (canInline) {
                            return new MultiTypeGuardInlineInfo(invoke, totalWeight, level, concreteMethods, types, typesToConcretes, probabilities);
                        } else {
                            if (GraalOptions.TraceInlining) {
                                TTY.println("not inlining %s because it is a polymorphic method call and at least one invoked method cannot be inlined", methodName(callTarget.targetMethod(), invoke));
                            }
                            return null;
                        }
                    }
                } else {
                    if (GraalOptions.TraceInlining) {
                        TTY.println("not inlining %s because GraalOptions.InlineWithTypeCheck == false", methodName(callTarget.targetMethod(), invoke));
                    }
                    return null;
                }
            }
            return null;
        } else {
            if (GraalOptions.TraceInlining) {
                TTY.println("not inlining %s because no type profile exists", methodName(callTarget.targetMethod(), invoke));
            }
            return null;
        }
    }


    private static boolean checkInvokeConditions(Invoke invoke) {
        if (invoke.stateAfter() == null) {
            if (GraalOptions.TraceInlining) {
                TTY.println("not inlining %s because the invoke has no after state", methodName(invoke.callTarget().targetMethod(), invoke));
            }
            return false;
        }
        if (invoke.predecessor() == null) {
            if (GraalOptions.TraceInlining) {
                TTY.println("not inlining %s because the invoke is dead code", methodName(invoke.callTarget().targetMethod(), invoke));
            }
            return false;
        }
        return true;
    }

    private static boolean checkTargetConditions(RiMethod method) {
        if (!(method instanceof RiResolvedMethod)) {
            if (GraalOptions.TraceInlining) {
                TTY.println("not inlining %s because it is unresolved", method.toString());
            }
            return false;
        }
        RiResolvedMethod resolvedMethod = (RiResolvedMethod) method;
        if (Modifier.isNative(resolvedMethod.accessFlags())) {
            if (GraalOptions.TraceInlining) {
                TTY.println("not inlining %s because it is a native method", methodName(resolvedMethod));
            }
            return false;
        }
        if (Modifier.isAbstract(resolvedMethod.accessFlags())) {
            if (GraalOptions.TraceInlining) {
                TTY.println("not inlining %s because it is an abstract method", methodName(resolvedMethod));
            }
            return false;
        }
        if (!resolvedMethod.holder().isInitialized()) {
            if (GraalOptions.TraceInlining) {
                TTY.println("not inlining %s because of non-initialized class", methodName(resolvedMethod));
            }
            return false;
        }
        if (!resolvedMethod.canBeInlined()) {
            if (GraalOptions.TraceInlining) {
                TTY.println("not inlining %s because it is marked non-inlinable", methodName(resolvedMethod));
            }
            return false;
        }
        return true;
    }

    /**
     * Performs an actual inlining, thereby replacing the given invoke with the given inlineGraph.
     * @param invoke the invoke that will be replaced
     * @param inlineGraph the graph that the invoke will be replaced with
     * @param receiverNullCheck true if a null check needs to be generated for non-static inlinings, false if no such check is required
     * @return The node that represents the return value, or null for void methods and methods that have no non-exceptional exit.
     */
    public static void inline(Invoke invoke, StructuredGraph inlineGraph, boolean receiverNullCheck) {
        NodeInputList<ValueNode> parameters = invoke.callTarget().arguments();
        StructuredGraph graph = (StructuredGraph) invoke.node().graph();

        FrameState stateAfter = invoke.stateAfter();
        assert stateAfter.isAlive();

        IdentityHashMap<Node, Node> replacements = new IdentityHashMap<>();
        ArrayList<Node> nodes = new ArrayList<>();
        ReturnNode returnNode = null;
        UnwindNode unwindNode = null;
        BeginNode entryPointNode = inlineGraph.start();
        FixedNode firstCFGNode = entryPointNode.next();
        for (Node node : inlineGraph.getNodes()) {
            if (node == entryPointNode || node == entryPointNode.stateAfter()) {
                // Do nothing.
            } else if (node instanceof LocalNode) {
                replacements.put(node, parameters.get(((LocalNode) node).index()));
            } else {
                nodes.add(node);
                if (node instanceof ReturnNode) {
                    returnNode = (ReturnNode) node;
                } else if (node instanceof UnwindNode) {
                    unwindNode = (UnwindNode) node;
                }
            }
        }

        assert invoke.node().successors().first() != null : invoke;
        assert invoke.node().predecessor() != null;

        Map<Node, Node> duplicates = graph.addDuplicates(nodes, replacements);
        FixedNode firstCFGNodeDuplicate = (FixedNode) duplicates.get(firstCFGNode);
        if (receiverNullCheck) {
            receiverNullCheck(invoke);
        }
        invoke.node().replaceAtPredecessors(firstCFGNodeDuplicate);

        FrameState stateAtExceptionEdge = null;
        if (invoke instanceof InvokeWithExceptionNode) {
            InvokeWithExceptionNode invokeWithException = ((InvokeWithExceptionNode) invoke);
            if (unwindNode != null) {
                assert unwindNode.predecessor() != null;
                assert invokeWithException.exceptionEdge().successors().explicitCount() == 1;
                ExceptionObjectNode obj = (ExceptionObjectNode) invokeWithException.exceptionEdge().next();
                stateAtExceptionEdge = obj.stateAfter();
                UnwindNode unwindDuplicate = (UnwindNode) duplicates.get(unwindNode);
                for (Node usage : obj.usages().snapshot()) {
                    usage.replaceFirstInput(obj, unwindDuplicate.exception());
                }
                unwindDuplicate.clearInputs();
                Node n = obj.next();
                obj.setNext(null);
                unwindDuplicate.replaceAndDelete(n);
            } else {
                invokeWithException.killExceptionEdge();
            }
        } else {
            if (unwindNode != null) {
                UnwindNode unwindDuplicate = (UnwindNode) duplicates.get(unwindNode);
                unwindDuplicate.replaceAndDelete(graph.add(new DeoptimizeNode(DeoptAction.InvalidateRecompile)));
            }
        }

        FrameState stateBefore = null;
        FrameState outerFrameState = null;
        double invokeProbability = invoke.node().probability();
        for (Node node : duplicates.values()) {
            if (GraalOptions.ProbabilityAnalysis) {
                if (node instanceof FixedNode) {
                    FixedNode fixed = (FixedNode) node;
                    fixed.setProbability(fixed.probability() * invokeProbability);
                }
            }
            if (node instanceof FrameState) {
                FrameState frameState = (FrameState) node;
                if (frameState.bci == FrameState.BEFORE_BCI) {
                    if (stateBefore == null) {
                        stateBefore = stateAfter.duplicateModified(invoke.bci(), false, invoke.node().kind(), parameters.toArray(new ValueNode[parameters.size()]));
                    }
                    frameState.replaceAndDelete(stateBefore);
                } else if (frameState.bci == FrameState.AFTER_BCI) {
                    frameState.replaceAndDelete(stateAfter);
                } else if (frameState.bci == FrameState.AFTER_EXCEPTION_BCI) {
                    assert stateAtExceptionEdge != null;
                    frameState.replaceAndDelete(stateAtExceptionEdge);
                } else {
                    if (outerFrameState == null) {
                        outerFrameState = stateAfter.duplicateModified(invoke.bci(), stateAfter.rethrowException(), invoke.node().kind());
                    }
                    frameState.setOuterFrameState(outerFrameState);
                }
            }
        }

        Node returnValue = null;
        if (returnNode != null) {
            if (returnNode.result() instanceof LocalNode) {
                returnValue = replacements.get(returnNode.result());
            } else {
                returnValue = duplicates.get(returnNode.result());
            }
            for (Node usage : invoke.node().usages().snapshot()) {
                usage.replaceFirstInput(invoke.node(), returnValue);
            }
            Node returnDuplicate = duplicates.get(returnNode);
            returnDuplicate.clearInputs();
            Node n = invoke.next();
            invoke.setNext(null);
            returnDuplicate.replaceAndDelete(n);
        }

        invoke.node().clearInputs();
        invoke.node().replaceAtUsages(null);
        GraphUtil.killCFG(invoke.node());

        if (stateAfter.usages().isEmpty()) {
            stateAfter.safeDelete();
        }
    }

    public static void receiverNullCheck(Invoke invoke) {
        MethodCallTargetNode callTarget = invoke.callTarget();
        StructuredGraph graph = (StructuredGraph) invoke.graph();
        NodeInputList<ValueNode> parameters = callTarget.arguments();
        ValueNode firstParam = parameters.size() <= 0 ? null : parameters.get(0);
        if (!callTarget.isStatic() && firstParam.kind() == CiKind.Object && !firstParam.stamp().nonNull()) {
            graph.addBeforeFixed(invoke.node(), graph.add(new FixedGuardNode(graph.unique(new NullCheckNode(firstParam, false)))));
        }
    }
}
