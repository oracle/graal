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
import java.lang.reflect.Executable;
import java.util.Set;

import org.graalvm.nativeimage.AnnotationAccess;

import com.oracle.graal.pointsto.api.PointstoOptions;
import com.oracle.graal.pointsto.meta.AnalysisMetaAccess;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.phases.InlineBeforeAnalysisPolicy;
import com.oracle.svm.core.AlwaysInline;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.heap.RestrictHeapAccess;
import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.core.option.HostedOptionValues;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.ReachabilityRegistrationNode;
import com.oracle.svm.hosted.SVMHost;
import com.oracle.svm.hosted.SharedArenaSupport;
import com.oracle.svm.hosted.code.FactoryMethodSupport;
import com.oracle.svm.hosted.methodhandles.MethodHandleInvokerRenamingSubstitutionProcessor;
import com.oracle.svm.util.ReflectionUtil;

import jdk.graal.compiler.api.replacements.Fold;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.iterators.NodePredicate;
import jdk.graal.compiler.nodes.AbstractBeginNode;
import jdk.graal.compiler.nodes.AbstractEndNode;
import jdk.graal.compiler.nodes.CallTargetNode;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.FrameState;
import jdk.graal.compiler.nodes.FullInfopointNode;
import jdk.graal.compiler.nodes.Invoke;
import jdk.graal.compiler.nodes.LogicConstantNode;
import jdk.graal.compiler.nodes.ParameterNode;
import jdk.graal.compiler.nodes.ReturnNode;
import jdk.graal.compiler.nodes.StartNode;
import jdk.graal.compiler.nodes.UnwindNode;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.ConditionalNode;
import jdk.graal.compiler.nodes.extended.ValueAnchorNode;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import jdk.graal.compiler.nodes.java.AbstractNewObjectNode;
import jdk.graal.compiler.nodes.java.NewArrayNode;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.nodes.spi.ValueProxy;
import jdk.graal.compiler.nodes.virtual.AllocatedObjectNode;
import jdk.graal.compiler.nodes.virtual.CommitAllocationNode;
import jdk.graal.compiler.nodes.virtual.VirtualArrayNode;
import jdk.graal.compiler.nodes.virtual.VirtualObjectNode;
import jdk.graal.compiler.options.Option;
import jdk.graal.compiler.replacements.nodes.MethodHandleWithExceptionNode;
import jdk.internal.vm.annotation.ForceInline;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * The defaults for node limits are very conservative. Only small methods should be inlined. The
 * only exception are constants - an arbitrary number of constants is always allowed. Limiting to 1
 * node (which can be also 1 invoke) means that field accessors can be inlined and forwarding
 * methods can be inlined. But null checks and class initialization checks are already putting a
 * method above the limit. On the other hand, the inlining depth is generous because we do do not
 * need to limit it. Note that more experimentation is necessary to come up with the optimal
 * configuration.
 *
 * Important: the implementation details of this class are publicly observable API. Since
 * {@link java.lang.reflect.Method} constants can be produced by inlining lookup methods with
 * constant arguments, reducing inlining can break customer code. This means we can never reduce the
 * amount of inlining in a future version without breaking compatibility. This also means that we
 * must be conservative and only inline what is necessary for known use cases.
 */
public class InlineBeforeAnalysisPolicyUtils {
    public static class Options {
        @Option(help = "Maximum number of computation nodes for method inlined before static analysis")//
        public static final HostedOptionKey<Integer> InlineBeforeAnalysisAllowedNodes = new HostedOptionKey<>(1);

        @Option(help = "Maximum number of invokes for method inlined before static analysis")//
        public static final HostedOptionKey<Integer> InlineBeforeAnalysisAllowedInvokes = new HostedOptionKey<>(1);

        @Option(help = "Maximum call depth for method inlined before static analysis")//
        public static final HostedOptionKey<Integer> InlineBeforeAnalysisAllowedDepth = new HostedOptionKey<>(20);

        @Option(help = "Maximum number of methods inlined for method inlined before static analysis")//
        public static final HostedOptionKey<Integer> InlineBeforeAnalysisAllowedInlinings = new HostedOptionKey<>(10_000);

        @Option(help = "Maximum number of computation nodes for method handle internals inlined before static analysis")//
        public static final HostedOptionKey<Integer> InlineBeforeAnalysisMethodHandleAllowedNodes = new HostedOptionKey<>(10_000);

        @Option(help = "Maximum number of invokes for method handle internals inlined before static analysis")//
        public static final HostedOptionKey<Integer> InlineBeforeAnalysisMethodHandleAllowedInvokes = new HostedOptionKey<>(1_000);

        @Option(help = "Maximum call depth for method handle internals inlined before static analysis")//
        public static final HostedOptionKey<Integer> InlineBeforeAnalysisMethodHandleAllowedDepth = new HostedOptionKey<>(1_000);

        @Option(help = "Maximum number of methods inlined for method handle internals before static analysis")//
        public static final HostedOptionKey<Integer> InlineBeforeAnalysisMethodHandleAllowedInlinings = new HostedOptionKey<>(10_000);

        @Option(help = "Maximum number of computation nodes for constructors inlined into factory methods before static analysis")//
        public static final HostedOptionKey<Integer> InlineBeforeAnalysisConstructorAllowedNodes = new HostedOptionKey<>(200);

        @Option(help = "Maximum number of invokes for constructors inlined into factory methods before static analysis")//
        public static final HostedOptionKey<Integer> InlineBeforeAnalysisConstructorAllowedInvokes = new HostedOptionKey<>(50);

        @Option(help = "Maximum number of computation nodes for methods inlined into scoped methods before static analysis")//
        public static final HostedOptionKey<Integer> InlineBeforeAnalysisScopedAllowedNodes = new HostedOptionKey<>(1_000);

        @Option(help = "Maximum number of invokes for methods inlined into scoped methods before static analysis")//
        public static final HostedOptionKey<Integer> InlineBeforeAnalysisScopedAllowedInvokes = new HostedOptionKey<>(50);
    }

    /* Cached values of options, to avoid repeated option lookup. */
    public final int optionAllowedNodes = Options.InlineBeforeAnalysisAllowedNodes.getValue();
    public final int optionAllowedInvokes = Options.InlineBeforeAnalysisAllowedInvokes.getValue();
    public final int optionAllowedDepth = Options.InlineBeforeAnalysisAllowedDepth.getValue();
    public final int optionAllowedInlinings = Options.InlineBeforeAnalysisAllowedInlinings.getValue();
    public final int optionMethodHandleAllowedNodes = Options.InlineBeforeAnalysisMethodHandleAllowedNodes.getValue();
    public final int optionMethodHandleAllowedInvokes = Options.InlineBeforeAnalysisMethodHandleAllowedInvokes.getValue();
    public final int optionMethodHandleAllowedDepth = Options.InlineBeforeAnalysisMethodHandleAllowedDepth.getValue();
    public final int optionMethodHandleAllowedInlinings = Options.InlineBeforeAnalysisMethodHandleAllowedInlinings.getValue();

    public final boolean optionTrackNeverNullInstanceFields = PointstoOptions.TrackNeverNullInstanceFields.getValue(HostedOptionValues.singleton());
    public final int optionConstructorAllowedNodes = Options.InlineBeforeAnalysisConstructorAllowedNodes.getValue();
    public final int optionConstructorAllowedInvokes = Options.InlineBeforeAnalysisConstructorAllowedInvokes.getValue();
    public final int optionScopedAllowedNodes = Options.InlineBeforeAnalysisScopedAllowedNodes.getValue();
    public final int optionScopedAllowedInvokes = Options.InlineBeforeAnalysisScopedAllowedInvokes.getValue();

    public final boolean optionForeignAPISupport = SubstrateOptions.isForeignAPIEnabled();

    @SuppressWarnings("unchecked") //
    private static final Class<? extends Annotation> COMPILED_LAMBDA_FORM_ANNOTATION = //
                    (Class<? extends Annotation>) ReflectionUtil.lookupClass("java.lang.invoke.LambdaForm$Compiled");

    public static boolean isMethodHandleIntrinsificationRoot(ResolvedJavaMethod method) {
        return AnnotationAccess.isAnnotationPresent(method, COMPILED_LAMBDA_FORM_ANNOTATION);
    }

    public boolean isScopedMethod(ResolvedJavaMethod method) {
        return optionForeignAPISupport && SharedArenaSupport.isScopedMethod(method);
    }

    public boolean shouldInlineInvoke(GraphBuilderContext b, SVMHost hostVM, AccumulativeInlineScope policyScope, AnalysisMethod method) {
        boolean result = shouldInlineInvoke0(b, hostVM, policyScope, method);
        if (result && policyScope != null) {
            policyScope.accumulativeCounters.totalInlinedMethods++;
        }
        return result;
    }

    private boolean shouldInlineInvoke0(GraphBuilderContext b, SVMHost hostVM, AccumulativeInlineScope policyScope, AnalysisMethod method) {
        if (!inliningAllowed(hostVM, b, method)) {
            /*
             * Inlining this method can lead to incorrect code, so this condition must always be
             * checked first.
             */
            return false;
        } else if (alwaysInlineInvoke((AnalysisMetaAccess) b.getMetaAccess(), method)) {
            /* Manual override of the regular inlining depth checks. */
            return true;
        }

        /*
         * Calls to methods annotated with @AlwaysInline or @ForceInline should not be inlined if
         * the current method is a scoped method. The inlining of callees with such annotations is
         * left to later phases. Here, we just want to inline callees which won't be inlined by
         * other phases.
         */
        if (isScopedMethod(b.getMethod()) &&
                        (AnnotationAccess.isAnnotationPresent(method, AlwaysInline.class) || AnnotationAccess.isAnnotationPresent(method, ForceInline.class))) {
            return false;
        }

        boolean inMethodHandleIntrinsification = policyScope != null && policyScope.accumulativeCounters.inMethodHandleIntrinsification();
        int allowedInlinings = inMethodHandleIntrinsification ? optionMethodHandleAllowedInlinings : optionAllowedInlinings;
        if (policyScope != null && policyScope.accumulativeCounters.totalInlinedMethods >= allowedInlinings) {
            return false;
        }

        int allowedDepth = inMethodHandleIntrinsification ? optionMethodHandleAllowedDepth : optionAllowedDepth;
        /*
         * Note that we do not use the inlining depth from the GraphBuilderContext: If we are in a
         * regular inlining scope, but nested into a deep method handle intrinsification, then the
         * total inlining depth is high but our local depth for the scope can still be low enough to
         * do inlining.
         */
        int actualDepth = policyScope == null ? 0 : policyScope.inliningDepth;
        if (actualDepth >= allowedDepth) {
            return false;
        }

        if (!inMethodHandleIntrinsification) {
            if (b.recursiveInliningDepth(method) > 0) {
                /*
                 * There is no need to make recursive inlining configurable via an option for now,
                 * it is always just disallowed. Except when we are in a method handle
                 * intrinsification, because method handles are often deeply recursive. The
                 * "total inlined methods" check above prevents excessive recursive inlining during
                 * method handle intrinsification.
                 */
                return false;
            }
            if (policyScope != null && isMethodHandleIntrinsificationRoot(method)) {
                /*
                 * We are not in a method handle intrinsification i.e., the method handle was not
                 * the root inlining context. Do not inline compiled lambda forms at all. Since the
                 * inlining limits are low, this could lead to partially inlined method handle
                 * chains, which leads to slow code because the non-inlined part is executed in the
                 * method handle interpreter. It is likely that the method handle is fully inlined
                 * when processing the callee, where the method handle is then the root inlining
                 * context.
                 */
                return false;
            }
        }
        return true;
    }

    public static boolean inliningAllowed(SVMHost hostVM, GraphBuilderContext b, AnalysisMethod callee) {
        AnalysisMethod caller = (AnalysisMethod) b.getMethod();
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
        if (callee.hasOpaqueReturn()) {
            /*
             * When a callee has opaque return then it cannot be inlined. Inlining the method would
             * expose the method's return values, which might change e.g. due to later
             * intrinsification of the invoke.
             */
            return false;
        }
        return true;
    }

    public boolean alwaysInlineInvoke(@SuppressWarnings("unused") AnalysisMetaAccess metaAccess, @SuppressWarnings("unused") AnalysisMethod method) {
        return false;
    }

    enum InliningScopeType {
        None,
        MethodHandleIntrinsification,
        ConstructorInlining,
        ScopedMethod
    }

    static final class AccumulativeCounters {
        int maxNodes;
        int maxInvokes;
        final InliningScopeType inliningScopeType;

        int numNodes;
        int numInvokes;
        int totalInlinedMethods;

        private AccumulativeCounters(int maxNodes, int maxInvokes, InliningScopeType inliningScopeType) {
            this.maxNodes = maxNodes;
            this.maxInvokes = maxInvokes;
            this.inliningScopeType = inliningScopeType;
        }

        public boolean inMethodHandleIntrinsification() {
            return inliningScopeType == InliningScopeType.MethodHandleIntrinsification;
        }

        public boolean inConstructorInlining() {
            return inliningScopeType == InliningScopeType.ConstructorInlining;
        }

        public boolean inAnyInliningScope() {
            return inliningScopeType != InliningScopeType.None;
        }
    }

    /**
     * This scope tracks the total number of nodes inlined. Once the total number of inlined nodes
     * has exceeded a specified count, or an illegal node is inlined, then the process will be
     * aborted.
     */
    public AccumulativeInlineScope createAccumulativeInlineScope(AccumulativeInlineScope outer, AnalysisMethod caller, AnalysisMethod method, NodePredicate invalidNodePredicate) {
        AccumulativeCounters accumulativeCounters;
        int depth;
        if (isScopedMethod(caller)) {
            /*
             * Inlining into @Scope-annotated methods is required for correctness since in general,
             * no calls may remain. Therefore, regardless if there is already an outer scope, those
             * methods are always treated as inlining root.
             */
            depth = 1;
            accumulativeCounters = new AccumulativeCounters(optionScopedAllowedNodes, optionScopedAllowedInvokes, InliningScopeType.ScopedMethod);

        } else if (outer == null) {
            /*
             * The first level of method inlining, i.e., the top scope from the inlining policy
             * point of view.
             */
            depth = 1;

            if (isMethodHandleIntrinsificationRoot(method)) {
                /*
                 * Method handle intrinsification root: create counters with relaxed limits and
                 * permit more types of nodes, but not recursively, i.e., not if we are already in a
                 * method handle intrinsification context.
                 */
                accumulativeCounters = new AccumulativeCounters(optionMethodHandleAllowedNodes, optionMethodHandleAllowedInvokes, InliningScopeType.MethodHandleIntrinsification);

            } else if (optionTrackNeverNullInstanceFields && FactoryMethodSupport.isFactoryMethod(caller)) {
                accumulativeCounters = new AccumulativeCounters(optionConstructorAllowedNodes, optionConstructorAllowedInvokes, InliningScopeType.ConstructorInlining);

            } else {
                accumulativeCounters = new AccumulativeCounters(optionAllowedNodes, optionAllowedInvokes, InliningScopeType.None);
            }

        } else if (outer.accumulativeCounters.inMethodHandleIntrinsification() && !inlineForMethodHandleIntrinsification(method)) {
            /*
             * Method which is invoked in method handle intrinsification but which is not part of
             * the method handle apparatus, for example, the target method of a direct method
             * handle: create a scope with the restrictive regular limits (although it is an
             * additional scope, therefore still permitting more nodes in total)
             *
             * This assumes that the regular limits are strict enough to prevent excessive inlining
             * triggered by method handles. We could also use alternative fixed values or the option
             * defaults instead of any set option values.
             *
             * We start again with an inlining depth of 1, i.e., we behave as if that method is the
             * inlining root.
             */
            depth = 1;
            accumulativeCounters = new AccumulativeCounters(optionAllowedNodes, optionAllowedInvokes, InliningScopeType.None);

        } else if (outer.accumulativeCounters.inConstructorInlining() && !method.isConstructor()) {
            depth = 1;
            accumulativeCounters = new AccumulativeCounters(optionAllowedNodes, optionAllowedInvokes, InliningScopeType.None);

        } else {
            /* Nested inlining (potentially during method handle intrinsification). */
            depth = outer.inliningDepth + 1;
            accumulativeCounters = outer.accumulativeCounters;
        }
        return new AccumulativeInlineScope(accumulativeCounters, depth, invalidNodePredicate);
    }

    public final class AccumulativeInlineScope extends InlineBeforeAnalysisPolicy.AbstractPolicyScope {
        final AccumulativeCounters accumulativeCounters;
        final NodePredicate invalidNodePredicate;

        /**
         * The number of nodes and invokes which have been inlined from this method (and also
         * committed child methods). This must be kept track of to ensure correct accounting when
         * aborts occur.
         */
        int numNodes = 0;
        int numInvokes = 0;

        AccumulativeInlineScope(AccumulativeCounters accumulativeCounters, int inliningDepth, NodePredicate invalidNodePredicate) {
            super(inliningDepth);
            this.accumulativeCounters = accumulativeCounters;
            this.invalidNodePredicate = invalidNodePredicate;
        }

        @Override
        public void commitCalleeScope(InlineBeforeAnalysisPolicy.AbstractPolicyScope callee) {
            AccumulativeInlineScope calleeScope = (AccumulativeInlineScope) callee;
            if (accumulativeCounters != calleeScope.accumulativeCounters) {
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
            }
        }

        @Override
        public boolean processNode(AnalysisMetaAccess metaAccess, AnalysisMethod method, Node node) {
            if (invalidNodePredicate.test(node)) {
                return false;
            }

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

            if (node instanceof ConditionalNode) {
                /*
                 * A ConditionalNode is used to "materialize" a prior logic node when returning a
                 * condition in a boolean method. We do not want to count it separately.
                 */
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

            /*
             * During inlining (i.e. method handle intrinsification, constructor/scoped method
             * inlining), we permit all node types to become more effective.
             */
            return allow || accumulativeCounters.inAnyInliningScope();
        }

        @Override
        public boolean processNonInlinedInvoke(CoreProviders providers, CallTargetNode node) {
            if (node.targetMethod() != null && isMethodHandleIntrinsificationRoot(node.targetMethod())) {
                /*
                 * Prevent partial inlining of method handle code, both with and without
                 * "intrinsification of method handles". We rather want to keep a top-level call to
                 * the original method handle.
                 */
                return false;
            }
            return true;
        }

        @Override
        public String toString() {
            return "AccumulativeInlineScope: " + numNodes + "/" + numInvokes + " (" + accumulativeCounters.numNodes + "/" + accumulativeCounters.numInvokes + ")";
        }
    }

    private static final Set<Class<?>> INLINE_METHOD_HANDLE_CLASSES = Set.of(
                    /* Inline trivial helper methods for value conversion. */
                    sun.invoke.util.ValueConversions.class);

    private static final Set<Executable> INLINE_METHOD_HANDLE_METHODS = Set.of(
                    /*
                     * Important methods in the method handle implementation that do not have
                     * a @ForceInline annotation.
                     */
                    ReflectionUtil.lookupMethod(ReflectionUtil.lookupClass(false, "java.lang.invoke.DirectMethodHandle"), "allocateInstance", Object.class),
                    ReflectionUtil.lookupMethod(ReflectionUtil.lookupClass(false, "java.lang.invoke.DirectMethodHandle$Accessor"), "checkCast", Object.class),
                    ReflectionUtil.lookupMethod(ReflectionUtil.lookupClass(false, "java.lang.invoke.DirectMethodHandle$StaticAccessor"), "checkCast", Object.class));

    private static boolean inlineForMethodHandleIntrinsification(AnalysisMethod method) {
        return AnnotationAccess.isAnnotationPresent(method, ForceInline.class) ||
                        isMethodHandleIntrinsificationRoot(method) ||
                        INLINE_METHOD_HANDLE_CLASSES.contains(method.getDeclaringClass().getJavaClass()) ||
                        isManuallyListed(method.getJavaMethod());
    }

    private static boolean isManuallyListed(Executable method) {
        return method != null && INLINE_METHOD_HANDLE_METHODS.contains(method);
    }

    /**
     * Discard information on inlined calls to generated classes of LambdaForms, not all of which
     * are assigned names that are stable between executions and would cause mismatches in collected
     * profile-guided optimization data which prevent optimizations.
     *
     * @see MethodHandleInvokerRenamingSubstitutionProcessor
     */
    protected boolean shouldOmitIntermediateMethodInState(AnalysisMethod method) {
        return isMethodHandleIntrinsificationRoot(method);
    }
}
