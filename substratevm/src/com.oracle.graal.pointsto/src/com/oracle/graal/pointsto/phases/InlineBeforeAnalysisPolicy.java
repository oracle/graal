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
package com.oracle.graal.pointsto.phases;

import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodes.FixedWithNextNode;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import org.graalvm.compiler.nodes.graphbuilderconf.InlineInvokePlugin.InlineInfo;
import org.graalvm.compiler.nodes.graphbuilderconf.NodePlugin;

import com.oracle.graal.pointsto.meta.AnalysisMetaAccess;
import com.oracle.graal.pointsto.util.AnalysisError;

import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Provides the policy which methods are inlined by {@link InlineBeforeAnalysis}. If
 * {@link #shouldInlineInvoke} returns true for an invocation, the graph decoding goes into callees
 * and starts decoding. A new {@link #openCalleeScope scope is opened} for each callee so that the
 * policy implementation can track each inlined method. As long as
 * {@link AbstractPolicyScope#processNode} returns true, inlining is continued. If
 * {@link AbstractPolicyScope#processNode} returns false, the inlining is
 * {@link AbstractPolicyScope#abortCalleeScope aborted}. If {@link AbstractPolicyScope#processNode}
 * returns true for all nodes of the callee, the inlining is
 * {@link AbstractPolicyScope#commitCalleeScope committed}.
 */
@SuppressWarnings("unused")
public abstract class InlineBeforeAnalysisPolicy {

    /**
     * A place for policy implementations to store per-callee information like the number of nodes
     * seen in the callee.
     */
    public abstract static class AbstractPolicyScope {
        public final int inliningDepth;

        protected AbstractPolicyScope(int inliningDepth) {
            this.inliningDepth = inliningDepth;
        }

        public abstract boolean allowAbort();

        public abstract void commitCalleeScope(AbstractPolicyScope callee);

        public abstract void abortCalleeScope(AbstractPolicyScope callee);

        /**
         * Invoked for each node of the callee during graph decoding. If the method returns true,
         * inlining is continued. If the method returns false, inlining is aborted.
         *
         * This method is called during graph decoding. The provided node itself is already fully
         * decoded and canonicalized, i.e., all properties and predecessors of the node are
         * available. But usages have not been decoded yet, so the implementation must not base any
         * decision on the current list of usages. The list of usages is often but not always empty.
         */
        public abstract boolean processNode(AnalysisMetaAccess metaAccess, ResolvedJavaMethod method, Node node);
    }

    protected final NodePlugin[] nodePlugins;

    protected InlineBeforeAnalysisPolicy(NodePlugin[] nodePlugins) {
        this.nodePlugins = nodePlugins;
    }

    protected abstract boolean shouldInlineInvoke(GraphBuilderContext b, ResolvedJavaMethod method, ValueNode[] args);

    protected abstract InlineInfo createInvokeInfo(ResolvedJavaMethod method);

    protected abstract boolean needsExplicitExceptions();

    protected abstract boolean tryInvocationPlugins();

    protected abstract FixedWithNextNode processInvokeArgs(ResolvedJavaMethod targetMethod, FixedWithNextNode insertionPoint, ValueNode[] arguments);

    protected abstract AbstractPolicyScope createRootScope();

    protected abstract AbstractPolicyScope openCalleeScope(AbstractPolicyScope outer, AnalysisMetaAccess metaAccess,
                    ResolvedJavaMethod method, boolean[] constArgsWithReceiver, boolean intrinsifiedMethodHandle);

    /** @see InlineBeforeAnalysisGraphDecoder#shouldOmitIntermediateMethodInStates */
    protected boolean shouldOmitIntermediateMethodInState(ResolvedJavaMethod method) {
        return false;
    }

    public static final InlineBeforeAnalysisPolicy NO_INLINING = new InlineBeforeAnalysisPolicy(new NodePlugin[0]) {

        @Override
        protected boolean shouldInlineInvoke(GraphBuilderContext b, ResolvedJavaMethod method, ValueNode[] args) {
            return false;
        }

        @Override
        protected InlineInfo createInvokeInfo(ResolvedJavaMethod method) {
            throw AnalysisError.shouldNotReachHere("NO_INLINING policy should not try to inline");
        }

        @Override
        protected boolean needsExplicitExceptions() {
            return true;
        }

        @Override
        protected boolean tryInvocationPlugins() {
            /*
             * If an invocation plugin was unable to be used during bytecode parsing, then it will
             * be retried during graph decoding. In the default case this should not happen.
             */
            return false;
        }

        @Override
        protected FixedWithNextNode processInvokeArgs(ResolvedJavaMethod targetMethod, FixedWithNextNode insertionPoint, ValueNode[] arguments) {
            throw AnalysisError.shouldNotReachHere("NO_INLINING policy should not try to inline");
        }

        @Override
        protected AbstractPolicyScope createRootScope() {
            // No inlining is performed; nothing to keep track of
            return null;
        }

        @Override
        protected AbstractPolicyScope openCalleeScope(AbstractPolicyScope outer, AnalysisMetaAccess metaAccess,
                        ResolvedJavaMethod method, boolean[] constArgsWithReceiver, boolean intrinsifiedMethodHandle) {
            throw AnalysisError.shouldNotReachHere("NO_INLINING policy should not try to inline");
        }
    };
}
