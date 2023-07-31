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

import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodes.AbstractBeginNode;
import org.graalvm.compiler.nodes.AbstractEndNode;
import org.graalvm.compiler.nodes.CallTargetNode;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.FrameState;
import org.graalvm.compiler.nodes.FullInfopointNode;
import org.graalvm.compiler.nodes.Invoke;
import org.graalvm.compiler.nodes.LogicConstantNode;
import org.graalvm.compiler.nodes.ParameterNode;
import org.graalvm.compiler.nodes.ReturnNode;
import org.graalvm.compiler.nodes.StartNode;
import org.graalvm.compiler.nodes.UnwindNode;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.extended.ValueAnchorNode;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import org.graalvm.compiler.nodes.java.AbstractNewObjectNode;
import org.graalvm.compiler.nodes.java.NewArrayNode;
import org.graalvm.compiler.nodes.spi.ValueProxy;
import org.graalvm.compiler.nodes.virtual.AllocatedObjectNode;
import org.graalvm.compiler.nodes.virtual.CommitAllocationNode;
import org.graalvm.compiler.nodes.virtual.VirtualArrayNode;
import org.graalvm.compiler.nodes.virtual.VirtualObjectNode;
import org.graalvm.compiler.options.Option;
import org.graalvm.nativeimage.AnnotationAccess;

import com.oracle.graal.pointsto.meta.AnalysisMetaAccess;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.phases.InlineBeforeAnalysisPolicy;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.heap.RestrictHeapAccess;
import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.ReachabilityRegistrationNode;
import com.oracle.svm.hosted.SVMHost;

import jdk.vm.ci.meta.ResolvedJavaMethod;

public class InlineBeforeAnalysisPolicyUtils {
    public static class Options {
        @Option(help = "Maximum number of computation nodes for method inlined before static analysis")//
        public static final HostedOptionKey<Integer> InlineBeforeAnalysisAllowedNodes = new HostedOptionKey<>(1);

        @Option(help = "Maximum number of invokes for method inlined before static analysis")//
        public static final HostedOptionKey<Integer> InlineBeforeAnalysisAllowedInvokes = new HostedOptionKey<>(1);

        @Option(help = "Maximum number of invokes for method inlined before static analysis")//
        public static final HostedOptionKey<Integer> InlineBeforeAnalysisAllowedDepth = new HostedOptionKey<>(20);
    }

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

    static class AccumulativeCounters {
        final int maxNodes = Options.InlineBeforeAnalysisAllowedNodes.getValue();
        final int maxInvokes = Options.InlineBeforeAnalysisAllowedInvokes.getValue();
        final int maxInliningDepth = Options.InlineBeforeAnalysisAllowedDepth.getValue();

        int numNodes = 0;
        int numInvokes = 0;
    }

    /**
     * This scope tracks the total number of nodes inlined. Once the total number of inlined nodes
     * has exceeded a specified count, or an illegal node is inlined, then the process will be
     * aborted.
     */
    public static AccumulativeInlineScope createAccumulativeInlineScope(AccumulativeInlineScope outer, InlineBeforeAnalysisPolicyUtils inliningUtils) {
        AccumulativeCounters accumulativeCounters;
        int depth;
        if (outer == null) {
            /*
             * The first level of method inlining, i.e., the top scope from the inlining policy
             * point of view.
             */
            accumulativeCounters = new AccumulativeCounters();
            depth = 1;
        } else {
            /* Nested inlining. */
            accumulativeCounters = outer.accumulativeCounters;
            depth = outer.inliningDepth + 1;
        }
        return new AccumulativeInlineScope(accumulativeCounters, depth, inliningUtils);
    }

    public static class AccumulativeInlineScope extends InlineBeforeAnalysisPolicy.AbstractPolicyScope {
        final AccumulativeCounters accumulativeCounters;
        final InlineBeforeAnalysisPolicyUtils inliningUtils;

        /**
         * The number of nodes and invokes which have been inlined from this method (and also
         * committed child methods). This must be kept track of to ensure correct accounting when
         * aborts occur.
         */
        int numNodes = 0;
        int numInvokes = 0;

        AccumulativeInlineScope(AccumulativeCounters accumulativeCounters, int inliningDepth, InlineBeforeAnalysisPolicyUtils inliningUtils) {
            super(inliningDepth);
            this.accumulativeCounters = accumulativeCounters;
            this.inliningUtils = inliningUtils;
        }

        @Override
        public boolean allowAbort() {
            // when too many nodes are inlined any abort can occur
            return true;
        }

        @Override
        public void commitCalleeScope(InlineBeforeAnalysisPolicy.AbstractPolicyScope callee) {
            AccumulativeInlineScope calleeScope = (AccumulativeInlineScope) callee;
            assert accumulativeCounters == calleeScope.accumulativeCounters;
            numNodes += calleeScope.numNodes;
            numInvokes += calleeScope.numInvokes;
        }

        @Override
        public void abortCalleeScope(InlineBeforeAnalysisPolicy.AbstractPolicyScope callee) {
            AccumulativeInlineScope calleeScope = (AccumulativeInlineScope) callee;
            assert accumulativeCounters == calleeScope.accumulativeCounters;
            accumulativeCounters.numNodes -= calleeScope.numNodes;
            accumulativeCounters.numInvokes -= calleeScope.numInvokes;
        }

        @Override
        public boolean processNode(AnalysisMetaAccess metaAccess, ResolvedJavaMethod method, Node node) {
            if (inliningUtils.alwaysInlineInvoke(metaAccess, method)) {
                return true;
            }

            if (inliningDepth > accumulativeCounters.maxInliningDepth) {
                // too deep to continue inlining
                return false;
            }

            if (node instanceof StartNode || node instanceof ParameterNode || node instanceof ReturnNode || node instanceof UnwindNode) {
                /* Infrastructure nodes that are not even visible to the policy. */
                throw VMError.shouldNotReachHere("Node must not be visible to policy: " + node.getClass().getTypeName());
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

            if (node instanceof AbstractNewObjectNode) {
                /*
                 * We never allow to inline any kind of allocations, because the machine code size
                 * is large.
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
                return false;
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
                return false;
            } else if (node instanceof CommitAllocationNode || node instanceof AllocatedObjectNode) {
                /*
                 * Ignore nodes created by escape analysis in addition to the VirtualInstanceNode.
                 */
                return true;
            }

            if (node instanceof CallTargetNode) {
                throw VMError.shouldNotReachHere("Node must not be visible to policy: " + node.getClass().getTypeName());
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

            return true;
        }

        @Override
        public String toString() {
            return "AccumulativeInlineScope: " + numNodes + "/" + numInvokes + " (" + accumulativeCounters.numNodes + "/" + accumulativeCounters.numInvokes + ")";
        }
    }
}
