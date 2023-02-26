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

import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.Node.NodeIntrinsic;
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

import com.oracle.graal.pointsto.meta.AnalysisMetaAccess;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.phases.InlineBeforeAnalysisPolicy;
import com.oracle.svm.core.heap.RestrictHeapAccess;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.SVMHost;

import jdk.vm.ci.meta.ResolvedJavaMethod;
import org.graalvm.nativeimage.AnnotationAccess;

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
public class InlineBeforeAnalysisPolicyImpl extends InlineBeforeAnalysisPolicy<InlineBeforeAnalysisPolicyImpl.CountersScope> {

    public static class Options {
        @Option(help = "Maximum number of computation nodes for method inlined before static analysis")//
        public static final HostedOptionKey<Integer> InlineBeforeAnalysisAllowedNodes = new HostedOptionKey<>(1);

        @Option(help = "Maximum number of invokes for method inlined before static analysis")//
        public static final HostedOptionKey<Integer> InlineBeforeAnalysisAllowedInvokes = new HostedOptionKey<>(1);

        @Option(help = "Maximum number of invokes for method inlined before static analysis")//
        public static final HostedOptionKey<Integer> InlineBeforeAnalysisAllowedDepth = new HostedOptionKey<>(20);
    }

    private final SVMHost hostVM;

    private final int allowedNodes = Options.InlineBeforeAnalysisAllowedNodes.getValue();
    private final int allowedInvokes = Options.InlineBeforeAnalysisAllowedInvokes.getValue();
    private final int allowedDepth = Options.InlineBeforeAnalysisAllowedDepth.getValue();

    protected static final class CountersScope implements InlineBeforeAnalysisPolicy.Scope {
        final CountersScope accumulated;

        int numNodes;
        int numInvokes;

        CountersScope(CountersScope accumulated) {
            this.accumulated = accumulated;
        }

        @Override
        public String toString() {
            return numNodes + "/" + numInvokes + " (" + accumulated.numNodes + "/" + accumulated.numInvokes + ")";
        }
    }

    public InlineBeforeAnalysisPolicyImpl(SVMHost hostVM) {
        this.hostVM = hostVM;
    }

    protected boolean alwaysInlineInvoke(@SuppressWarnings("unused") AnalysisMetaAccess metaAccess, @SuppressWarnings("unused") ResolvedJavaMethod method) {
        return false;
    }

    @Override
    protected boolean shouldInlineInvoke(GraphBuilderContext b, ResolvedJavaMethod method, ValueNode[] args) {
        if (alwaysInlineInvoke((AnalysisMetaAccess) b.getMetaAccess(), method)) {
            return true;
        }
        if (b.getDepth() > allowedDepth) {
            return false;
        }
        if (b.recursiveInliningDepth(method) > 0) {
            /* Prevent recursive inlining. */
            return false;
        }

        AnalysisMethod caller = (AnalysisMethod) b.getMethod();
        AnalysisMethod callee = (AnalysisMethod) method;
        if (hostVM.neverInlineTrivial(caller, callee)) {
            return false;
        }
        if (AnnotationAccess.isAnnotationPresent(callee, Fold.class) || AnnotationAccess.isAnnotationPresent(callee, NodeIntrinsic.class)) {
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
        return true;
    }

    @Override
    protected CountersScope createTopScope() {
        CountersScope accumulated = new CountersScope(null);
        return new CountersScope(accumulated);
    }

    @Override
    protected CountersScope openCalleeScope(CountersScope outer) {
        return new CountersScope(outer.accumulated);
    }

    @Override
    protected void commitCalleeScope(CountersScope outer, CountersScope callee) {
        assert outer.accumulated == callee.accumulated;
        outer.numNodes += callee.numNodes;
        outer.numInvokes += callee.numInvokes;
    }

    @Override
    protected void abortCalleeScope(CountersScope outer, CountersScope callee) {
        assert outer.accumulated == callee.accumulated;
        outer.accumulated.numNodes -= callee.numNodes;
        outer.accumulated.numInvokes -= callee.numInvokes;
    }

    @Override
    protected boolean processNode(AnalysisMetaAccess metaAccess, ResolvedJavaMethod method, CountersScope scope, Node node) {
        if (node instanceof StartNode || node instanceof ParameterNode || node instanceof ReturnNode || node instanceof UnwindNode) {
            /* Infrastructure nodes that are not even visible to the policy. */
            throw VMError.shouldNotReachHere("Node must not be visible to policy: " + node.getClass().getTypeName());
        }
        if (node instanceof FullInfopointNode || node instanceof ValueProxy || node instanceof ValueAnchorNode || node instanceof FrameState ||
                        node instanceof AbstractBeginNode || node instanceof AbstractEndNode) {
            /*
             * Infrastructure nodes that are never counted. We could look at the NodeSize annotation
             * of a node, but that is a bit unreliable. For example, FrameState and
             * ExceptionObjectNode have size != 0 but we do not want to count them; CallTargetNode
             * has size 0 but we need to count it.
             */
            return true;
        }

        if (node instanceof ConstantNode || node instanceof LogicConstantNode) {
            /* An unlimited number of constants is allowed. We like constants. */
            return true;
        }

        if (alwaysInlineInvoke(metaAccess, method)) {
            return true;
        }

        if (node instanceof AbstractNewObjectNode) {
            /*
             * We never allow to inline any kind of allocations, because the machine code size is
             * large.
             *
             * With one important exception: we allow (and do not even count) arrays allocated with
             * length 0. Such allocations occur when a method has a Java vararg parameter but the
             * caller does not provide any vararg. Without this exception, important vararg usages
             * like Class.getDeclaredConstructor would not be considered for inlining.
             *
             * Note that we are during graph decoding, so usages of the node are not decoded yet. So
             * we cannot base the decision on a certain usage pattern of the allocation.
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
            /* Ignore nodes created by escape analysis in addition to the VirtualInstanceNode. */
            return true;
        }

        if (node instanceof Invoke) {
            throw VMError.shouldNotReachHere("Node must not visible to policy: " + node.getClass().getTypeName());
        } else if (node instanceof CallTargetNode) {
            if (scope.accumulated.numInvokes >= allowedInvokes) {
                return false;
            }
            scope.numInvokes++;
            scope.accumulated.numInvokes++;
        }

        if (scope.accumulated.numNodes >= allowedNodes) {
            return false;
        }
        scope.numNodes++;
        scope.accumulated.numNodes++;

        return true;
    }
}
