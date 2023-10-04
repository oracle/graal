/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.annotation.Annotation;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.util.Map;
import java.util.Set;

import jdk.compiler.graal.api.replacements.Fold;
import jdk.compiler.graal.graph.Node;
import jdk.compiler.graal.nodes.AbstractBeginNode;
import jdk.compiler.graal.nodes.AbstractEndNode;
import jdk.compiler.graal.nodes.CallTargetNode;
import jdk.compiler.graal.nodes.ConstantNode;
import jdk.compiler.graal.nodes.FrameState;
import jdk.compiler.graal.nodes.FullInfopointNode;
import jdk.compiler.graal.nodes.Invoke;
import jdk.compiler.graal.nodes.LogicConstantNode;
import jdk.compiler.graal.nodes.ParameterNode;
import jdk.compiler.graal.nodes.ReturnNode;
import jdk.compiler.graal.nodes.StartNode;
import jdk.compiler.graal.nodes.UnwindNode;
import jdk.compiler.graal.nodes.ValueNode;
import jdk.compiler.graal.nodes.extended.ValueAnchorNode;
import jdk.compiler.graal.nodes.graphbuilderconf.GraphBuilderContext;
import jdk.compiler.graal.nodes.java.AbstractNewObjectNode;
import jdk.compiler.graal.nodes.java.NewArrayNode;
import jdk.compiler.graal.nodes.spi.ValueProxy;
import jdk.compiler.graal.nodes.virtual.AllocatedObjectNode;
import jdk.compiler.graal.nodes.virtual.CommitAllocationNode;
import jdk.compiler.graal.nodes.virtual.VirtualArrayNode;
import jdk.compiler.graal.nodes.virtual.VirtualObjectNode;
import jdk.compiler.graal.options.Option;
import jdk.compiler.graal.replacements.nodes.MethodHandleWithExceptionNode;
import org.graalvm.nativeimage.AnnotationAccess;

import com.oracle.graal.pointsto.infrastructure.OriginalClassProvider;
import com.oracle.graal.pointsto.meta.AnalysisMetaAccess;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.phases.InlineBeforeAnalysisPolicy;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.heap.RestrictHeapAccess;
import com.oracle.svm.core.jdk.VarHandleFeature;
import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.ReachabilityRegistrationNode;
import com.oracle.svm.hosted.SVMHost;
import com.oracle.svm.hosted.methodhandles.MethodHandleInvokerRenamingSubstitutionProcessor;
import com.oracle.svm.util.ReflectionUtil;

import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

public class InlineBeforeAnalysisPolicyUtils {
    public static class Options {
        @Option(help = "Maximum number of computation nodes for method inlined before static analysis")//
        public static final HostedOptionKey<Integer> InlineBeforeAnalysisAllowedNodes = new HostedOptionKey<>(1);

        @Option(help = "Maximum number of invokes for method inlined before static analysis")//
        public static final HostedOptionKey<Integer> InlineBeforeAnalysisAllowedInvokes = new HostedOptionKey<>(1);

        @Option(help = "Maximum call depth for method inlined before static analysis")//
        public static final HostedOptionKey<Integer> InlineBeforeAnalysisAllowedDepth = new HostedOptionKey<>(20);

        @Option(help = "Maximum number of computation nodes for method handle internals inlined before static analysis")//
        public static final HostedOptionKey<Integer> InlineBeforeAnalysisMethodHandleAllowedNodes = new HostedOptionKey<>(100);

        @Option(help = "Maximum number of invokes for method handle internals inlined before static analysis")//
        public static final HostedOptionKey<Integer> InlineBeforeAnalysisMethodHandleAllowedInvokes = new HostedOptionKey<>(20);
    }

    @SuppressWarnings("unchecked") //
    private static final Class<? extends Annotation> COMPILED_LAMBDA_FORM_ANNOTATION = //
                    (Class<? extends Annotation>) ReflectionUtil.lookupClass(false, "java.lang.invoke.LambdaForm$Compiled");

    private static final Class<?> INVOKERS_CLASS = ReflectionUtil.lookupClass(false, "java.lang.invoke.Invokers");

    private static final Map<Class<?>, Set<String>> IGNORED_METHOD_HANDLE_METHODS = Map.of(
                    MethodHandle.class, Set.of("bindTo"),
                    MethodHandles.class, Set.of("dropArguments", "filterReturnValue", "foldArguments", "insertArguments"),
                    INVOKERS_CLASS, Set.of("spreadInvoker"));

    private AnalysisType methodHandleType;
    private AnalysisType varHandleGuardsType;

    public static boolean inliningAllowed(SVMHost hostVM, GraphBuilderContext b, ResolvedJavaMethod method) {
        AnalysisMethod caller = (AnalysisMethod) b.getMethod();
        AnalysisMethod callee = (AnalysisMethod) method;
        if (hostVM.neverInlineTrivial(caller, callee)) {
            return false;
        }
        if (AnnotationAccess.isAnnotationPresent(callee, Fold.class) || AnnotationAccess.isAnnotationPresent(callee, Node.NodeIntrinsic.class)) {
            /*
             * We should never see a call to such a method. But if we do, do not inline them
             * otherwise we miss the opportunity later to report it as an error.
             */
            return false;
        }
        if (AnnotationAccess.isAnnotationPresent(callee, RestrictHeapAccess.class)) {
            /*
             * This is conservative. We do not know the caller's heap restriction state yet because
             * that can only be computed after static analysis (it relies on the call graph produced
             * by the static analysis).
             */
            return false;
        }
        if (!Uninterruptible.Utils.inliningAllowed(caller, callee)) {
            return false;
        }
        if (callee.getReturnsAllInstantiatedTypes()) {
            /*
             * When a callee returns all instantiated types then it cannot be inlined. Inlining the
             * method would expose the method's return values instead of treating it as an
             * AllInstantiatedTypeFlow.
             */
            return false;
        }
        return true;
    }

    public boolean alwaysInlineInvoke(@SuppressWarnings("unused") AnalysisMetaAccess metaAccess, @SuppressWarnings("unused") ResolvedJavaMethod method) {
        return false;
    }

    /**
     * This scope will always allow nodes to be inlined.
     */
    public static class AlwaysInlineScope extends InlineBeforeAnalysisPolicy.AbstractPolicyScope {

        public AlwaysInlineScope(int inliningDepth) {
            super(inliningDepth);
        }

        @Override
        public boolean allowAbort() {
            // should not be able to abort
            return false;
        }

        @Override
        public void commitCalleeScope(InlineBeforeAnalysisPolicy.AbstractPolicyScope callee) {
            // nothing to do
        }

        @Override
        public void abortCalleeScope(InlineBeforeAnalysisPolicy.AbstractPolicyScope callee) {
            // nothing to do
        }

        @Override
        public boolean processNode(AnalysisMetaAccess metaAccess, ResolvedJavaMethod method, Node node) {
            // always inlining
            return true;
        }

        @Override
        public String toString() {
            return "AlwaysInlineScope";
        }
    }

    static final class AccumulativeCounters {
        static AccumulativeCounters create(AccumulativeCounters outer) {
            int maxDepth = (outer != null) ? outer.maxInliningDepth : Options.InlineBeforeAnalysisAllowedDepth.getValue();
            return new AccumulativeCounters(Options.InlineBeforeAnalysisAllowedNodes.getValue(),
                            Options.InlineBeforeAnalysisAllowedInvokes.getValue(),
                            maxDepth,
                            false);
        }

        static AccumulativeCounters createForMethodHandleIntrinsification(AccumulativeCounters outer) {
            return new AccumulativeCounters(Options.InlineBeforeAnalysisMethodHandleAllowedNodes.getValue(),
                            Options.InlineBeforeAnalysisMethodHandleAllowedInvokes.getValue(),
                            outer.maxInliningDepth,
                            true);
        }

        int maxNodes;
        int maxInvokes;
        final int maxInliningDepth;
        final boolean inMethodHandleIntrinsification;

        int numNodes = 0;
        int numInvokes = 0;

        private AccumulativeCounters(int maxNodes, int maxInvokes, int maxInliningDepth, boolean inMethodHandleIntrinsification) {
            this.maxNodes = maxNodes;
            this.maxInvokes = maxInvokes;
            this.maxInliningDepth = maxInliningDepth;
            this.inMethodHandleIntrinsification = inMethodHandleIntrinsification;
        }
    }

    /**
     * This scope tracks the total number of nodes inlined. Once the total number of inlined nodes
     * has exceeded a specified count, or an illegal node is inlined, then the process will be
     * aborted.
     */
    public AccumulativeInlineScope createAccumulativeInlineScope(AccumulativeInlineScope outer, AnalysisMetaAccess metaAccess,
                    ResolvedJavaMethod method, boolean[] constArgsWithReceiver, boolean intrinsifiedMethodHandle) {
        AccumulativeCounters accumulativeCounters;
        int depth;
        if (outer == null) {
            /*
             * The first level of method inlining, i.e., the top scope from the inlining policy
             * point of view.
             */
            depth = 1;
            accumulativeCounters = AccumulativeCounters.create(null);

        } else if (!outer.accumulativeCounters.inMethodHandleIntrinsification && (intrinsifiedMethodHandle || isMethodHandleIntrinsificationRoot(metaAccess, method, constArgsWithReceiver))) {
            /*
             * Method handle intrinsification root: create counters with relaxed limits and permit
             * more types of nodes, but not recursively, i.e., not if we are already in a method
             * handle intrinsification context.
             */
            depth = outer.inliningDepth + 1;
            accumulativeCounters = AccumulativeCounters.createForMethodHandleIntrinsification(outer.accumulativeCounters);

        } else if (outer.accumulativeCounters.inMethodHandleIntrinsification && !inlineForMethodHandleIntrinsification(method)) {
            /*
             * Method which is invoked in method handle intrinsification but which is not part of
             * the method handle apparatus, for example, the target method of a direct method
             * handle: create a scope with the restrictive regular limits (although it is an
             * additional scope, therefore still permitting more nodes in total)
             *
             * This assumes that the regular limits are strict enough to prevent excessive inlining
             * triggered by method handles. We could also use alternative fixed values or the option
             * defaults instead of any set option values.
             */
            depth = outer.inliningDepth + 1;
            accumulativeCounters = AccumulativeCounters.create(outer.accumulativeCounters);

        } else {
            /* Nested inlining (potentially during method handle intrinsification). */
            depth = outer.inliningDepth + 1;
            accumulativeCounters = outer.accumulativeCounters;
        }
        return new AccumulativeInlineScope(accumulativeCounters, depth);
    }

    private boolean isMethodHandleIntrinsificationRoot(AnalysisMetaAccess metaAccess, ResolvedJavaMethod method, boolean[] constArgsWithReceiver) {
        return (isVarHandleMethod(metaAccess, method) || hasConstantMethodHandleParameter(metaAccess, method, constArgsWithReceiver)) && !isIgnoredMethodHandleMethod(method);
    }

    private boolean hasConstantMethodHandleParameter(AnalysisMetaAccess metaAccess, ResolvedJavaMethod method, boolean[] constArgsWithReceiver) {
        if (methodHandleType == null) {
            methodHandleType = metaAccess.lookupJavaType(MethodHandle.class);
        }
        for (int i = 0; i < constArgsWithReceiver.length; i++) {
            if (constArgsWithReceiver[i] && methodHandleType.isAssignableFrom(getParameterType(method, i))) {
                return true;
            }
        }
        return false;
    }

    private static ResolvedJavaType getParameterType(ResolvedJavaMethod method, int index) {
        int i = index;
        if (!method.isStatic()) {
            if (i == 0) { // receiver
                return method.getDeclaringClass();
            }
            i--;
        }
        return (ResolvedJavaType) method.getSignature().getParameterType(i, null);
    }

    /**
     * Checks if the method is the intrinsification root for a VarHandle. In the current VarHandle
     * implementation, all guards are in the automatically generated class VarHandleGuards. All
     * methods do have a VarHandle argument, and we expect it to be a compile-time constant.
     * <p>
     * See the documentation in {@link VarHandleFeature} for more information on the overall
     * VarHandle support.
     */
    private boolean isVarHandleMethod(AnalysisMetaAccess metaAccess, ResolvedJavaMethod method) {
        if (varHandleGuardsType == null) {
            varHandleGuardsType = metaAccess.lookupJavaType(ReflectionUtil.lookupClass(false, "java.lang.invoke.VarHandleGuards"));
        }
        return method.getDeclaringClass().equals(varHandleGuardsType);
    }

    private static boolean isIgnoredMethodHandleMethod(ResolvedJavaMethod method) {
        Class<?> declaringClass = OriginalClassProvider.getJavaClass(method.getDeclaringClass());
        Set<String> ignoredMethods = IGNORED_METHOD_HANDLE_METHODS.get(declaringClass);
        return ignoredMethods != null && ignoredMethods.contains(method.getName());
    }

    public final class AccumulativeInlineScope extends InlineBeforeAnalysisPolicy.AbstractPolicyScope {
        final AccumulativeCounters accumulativeCounters;

        /**
         * The number of nodes and invokes which have been inlined from this method (and also
         * committed child methods). This must be kept track of to ensure correct accounting when
         * aborts occur.
         */
        int numNodes = 0;
        int numInvokes = 0;

        AccumulativeInlineScope(AccumulativeCounters accumulativeCounters, int inliningDepth) {
            super(inliningDepth);
            this.accumulativeCounters = accumulativeCounters;
        }

        @Override
        public boolean allowAbort() {
            // when too many nodes are inlined any abort can occur
            return true;
        }

        @Override
        public void commitCalleeScope(InlineBeforeAnalysisPolicy.AbstractPolicyScope callee) {
            AccumulativeInlineScope calleeScope = (AccumulativeInlineScope) callee;
            if (accumulativeCounters != calleeScope.accumulativeCounters) {
                assert accumulativeCounters.inMethodHandleIntrinsification != calleeScope.accumulativeCounters.inMethodHandleIntrinsification;

                // Expand limits to hold the method handle intrinsification, but not more.
                accumulativeCounters.maxNodes += calleeScope.numNodes;
                accumulativeCounters.maxInvokes += calleeScope.numInvokes;

                accumulativeCounters.numNodes += calleeScope.numNodes;
                accumulativeCounters.numInvokes += calleeScope.numInvokes;
            }
            numNodes += calleeScope.numNodes;
            numInvokes += calleeScope.numInvokes;
        }

        @Override
        public void abortCalleeScope(InlineBeforeAnalysisPolicy.AbstractPolicyScope callee) {
            AccumulativeInlineScope calleeScope = (AccumulativeInlineScope) callee;
            if (accumulativeCounters == calleeScope.accumulativeCounters) {
                accumulativeCounters.numNodes -= calleeScope.numNodes;
                accumulativeCounters.numInvokes -= calleeScope.numInvokes;
            } else {
                assert accumulativeCounters.inMethodHandleIntrinsification != calleeScope.accumulativeCounters.inMethodHandleIntrinsification;
            }
        }

        @Override
        public boolean processNode(AnalysisMetaAccess metaAccess, ResolvedJavaMethod method, Node node) {
            if (node instanceof StartNode || node instanceof ParameterNode || node instanceof ReturnNode || node instanceof UnwindNode ||
                            node instanceof CallTargetNode || node instanceof MethodHandleWithExceptionNode) {
                /*
                 * Infrastructure nodes and call targets are not intended to be visible to the
                 * policy. Method handle calls must have been transformed to an invoke already.
                 */
                throw VMError.shouldNotReachHere("Node must not be visible to policy: " + node.getClass().getTypeName());
            }

            if (alwaysInlineInvoke(metaAccess, method)) {
                return true;
            }

            if (inliningDepth > accumulativeCounters.maxInliningDepth) {
                // too deep to continue inlining
                return false;
            }

            if (node instanceof FullInfopointNode || node instanceof ValueProxy || node instanceof ValueAnchorNode || node instanceof FrameState ||
                            node instanceof AbstractBeginNode || node instanceof AbstractEndNode) {
                /*
                 * Infrastructure nodes that are never counted. We could look at the NodeSize
                 * annotation of a node, but that is a bit unreliable. For example, FrameState and
                 * ExceptionObjectNode have size != 0 but we do not want to count them;
                 * CallTargetNode has size 0 but we need to count it.
                 */
                return true;
            }

            if (node instanceof ConstantNode || node instanceof LogicConstantNode) {
                /* An unlimited number of constants is allowed. We like constants. */
                return true;
            }

            if (node instanceof ReachabilityRegistrationNode) {
                /*
                 * These nodes do not affect compilation and are only used to execute handlers
                 * depending on their reachability.
                 */
                return true;
            }

            boolean allow = true;

            if (node instanceof AbstractNewObjectNode) {
                /*
                 * We do not inline any kind of allocations because the machine code size is large.
                 *
                 * With one important exception: we allow (and do not even count) arrays allocated
                 * with length 0. Such allocations occur when a method has a Java vararg parameter
                 * but the caller does not provide any vararg. Without this exception, important
                 * vararg usages like Class.getDeclaredConstructor would not be considered for
                 * inlining.
                 *
                 * Note that we are during graph decoding, so usages of the node are not decoded
                 * yet. So we cannot base the decision on a certain usage pattern of the allocation.
                 */
                if (node instanceof NewArrayNode) {
                    ValueNode newArrayLength = ((NewArrayNode) node).length();
                    if (newArrayLength.isJavaConstant() && newArrayLength.asJavaConstant().asInt() == 0) {
                        return true;
                    }
                }
                allow = false;

            } else if (node instanceof VirtualObjectNode) {
                /*
                 * Same as the explicit allocation nodes above, but this time for the virtualized
                 * allocations created when escape analysis runs immediately after bytecode parsing.
                 */
                if (node instanceof VirtualArrayNode) {
                    int newArrayLength = ((VirtualArrayNode) node).entryCount();
                    if (newArrayLength == 0) {
                        return true;
                    }
                }
                allow = false;

            } else if (node instanceof CommitAllocationNode || node instanceof AllocatedObjectNode) {
                /* Ignore nodes created by escape analysis in addition to the VirtualObjectNode. */
                return true;
            }

            if (node instanceof Invoke) {
                if (accumulativeCounters.numInvokes >= accumulativeCounters.maxInvokes) {
                    return false;
                }
                numInvokes++;
                accumulativeCounters.numInvokes++;
            }

            if (accumulativeCounters.numNodes >= accumulativeCounters.maxNodes) {
                return false;
            }
            numNodes++;
            accumulativeCounters.numNodes++;

            // With method handle intrinsification we permit all node types to become more effective
            return allow || accumulativeCounters.inMethodHandleIntrinsification;
        }

        @Override
        public String toString() {
            return "AccumulativeInlineScope: " + numNodes + "/" + numInvokes + " (" + accumulativeCounters.numNodes + "/" + accumulativeCounters.numInvokes + ")";
        }
    }

    private static boolean inlineForMethodHandleIntrinsification(ResolvedJavaMethod method) {
        String className = method.getDeclaringClass().toJavaName(true);
        if (className.startsWith("java.lang.invoke") && !className.contains("InvokerBytecodeGenerator")) {
            /*
             * Inline all helper methods used by method handles. We do not know exactly which ones
             * they are, but they are all from the same package.
             */
            return true;
        } else if (className.equals("sun.invoke.util.ValueConversions")) {
            /* Inline trivial helper methods for value conversion. */
            return true;
        }
        return false;
    }

    /**
     * Discard information on inlined calls to generated classes of LambdaForms, not all of which
     * are assigned names that are stable between executions and would cause mismatches in collected
     * profile-guided optimization data which prevent optimizations.
     *
     * @see MethodHandleInvokerRenamingSubstitutionProcessor
     */
    protected boolean shouldOmitIntermediateMethodInState(ResolvedJavaMethod method) {
        return method.isAnnotationPresent(COMPILED_LAMBDA_FORM_ANNOTATION);
    }
}
