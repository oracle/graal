/*
 * Copyright (c) 2014, 2020, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.runtime;

import java.util.Map;

import org.graalvm.compiler.truffle.common.TruffleCompilationTask;
import org.graalvm.compiler.truffle.common.TruffleCompilerListener.CompilationResultInfo;
import org.graalvm.compiler.truffle.common.TruffleCompilerListener.GraphInfo;

import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.Node;

/**
 * A listener for events related to the execution and compilation phases of a
 * {@link OptimizedCallTarget}. The states for a {@link OptimizedCallTarget} instance can be
 * described using the following deterministic automata: * <code>
 * <pre>
 * ( (split | (queue . unqueue))*
 *    . queue . started
 *    . (truffleTierFinished . graalTierFinished . success)
 *      | ([truffleTierFinished] . [graalTierFinished] . failed)
 *    . invalidate )*
 * </pre>
 * </code>
 * <p>
 * Note: <code>|</code> is the 'or' and <code>.</code> is the sequential operator. The
 * <code>*</code> represents the Kleene Closure.
 * </p>
 */
public interface GraalTruffleRuntimeListener {

    /**
     * Notifies this object when the target of a Truffle call node is
     * {@linkplain DirectCallNode#cloneCallTarget() cloned}.
     *
     * @param callNode the call node whose {@linkplain OptimizedDirectCallNode#getCallTarget()
     *            target} has just been cloned
     */
    default void onCompilationSplit(OptimizedDirectCallNode callNode) {
    }

    /**
     * Notifies this object when the target of a Truffle call node should be split but, for given
     * reason, could not be.
     *
     * @param callNode the call node whose where splitting could not occur.
     * @param reason why splitting of this node could not occur
     */
    default void onCompilationSplitFailed(OptimizedDirectCallNode callNode, CharSequence reason) {
    }

    /**
     * @deprecated Use {@link #onCompilationQueued(OptimizedCallTarget, int)}
     */
    @Deprecated
    default void onCompilationQueued(OptimizedCallTarget target) {
        onCompilationQueued(target, 0);
    }

    /**
     * Notifies this object after {@code target} is added to the compilation queue.
     *
     * @param target the call target that has just been enqueued for compilation
     * @param tier Which compilation tier is in question.
     */
    default void onCompilationQueued(OptimizedCallTarget target, int tier) {
    }

    /**
     * @deprecated Use
     *             {@link #onCompilationDequeued(OptimizedCallTarget, Object, CharSequence, int)}
     */
    @Deprecated
    default void onCompilationDequeued(OptimizedCallTarget target, Object source, CharSequence reason) {
        onCompilationDequeued(target, source, reason, 0);
    }

    /**
     * Notifies this object after {@code target} is removed from the compilation queue.
     *
     * @param target the call target that has just been removed from the compilation queue
     * @param source the source object that caused the compilation to be unqueued. For example the
     *            source {@link Node} object. May be {@code null}.
     * @param reason a textual description of the reason why the compilation was unqueued. May be
     *            {@code null}.
     * @param tier Which compilation tier is in question.
     */
    default void onCompilationDequeued(OptimizedCallTarget target, Object source, CharSequence reason, int tier) {
    }

    /**
     * @param target the call target about to be compiled
     * @param tier Which compilation tier is in question.
     *
     * @deprecated Use {@link #onCompilationStarted(OptimizedCallTarget, int)}
     */
    @Deprecated
    @SuppressWarnings("unused")
    default void onCompilationStarted(OptimizedCallTarget target, int tier) {
    }

    /**
     * Notifies this object when compilation of {@code target} is about to start.
     *
     * @param target the call target about to be compiled
     * @param task which compilation task is in question.
     */
    @SuppressWarnings({"unused", "deprecated"})
    default void onCompilationStarted(OptimizedCallTarget target, TruffleCompilationTask task) {
        onCompilationStarted(target, task.tier());
    }

    /**
     * Notifies this object when compilation of {@code target} has completed partial evaluation and
     * is about to perform compilation of the graph produced by partial evaluation.
     *
     * @param target the call target being compiled
     * @param inliningDecision the inlining plan used during partial evaluation
     * @param graph access to compiler graph info
     */
    default void onCompilationTruffleTierFinished(OptimizedCallTarget target, TruffleInlining inliningDecision, GraphInfo graph) {
    }

    /**
     * Notifies this object when Graal compilation of a call target completes. Graal compilation
     * occurs between {@link #onCompilationTruffleTierFinished} and code installation.
     *
     * @param target the call target that was compiled
     * @param graph the graph representing {@code target}
     */
    default void onCompilationGraalTierFinished(OptimizedCallTarget target, GraphInfo graph) {
    }

    /**
     * @deprecated Use
     *             {@link #onCompilationSuccess(OptimizedCallTarget, TruffleInlining, GraphInfo, CompilationResultInfo)}
     */
    @Deprecated
    default void onCompilationSuccess(OptimizedCallTarget target, TruffleInlining inliningDecision, GraphInfo graph, CompilationResultInfo result) {
        onCompilationSuccess(target, inliningDecision, graph, result, 0);
    }

    /**
     * Notifies this object when compilation of {@code target} succeeds.
     *
     * @param target the call target whose compilation succeeded
     * @param inliningDecision the inlining plan used during the compilation
     * @param graph access to compiler graph info
     * @param result access to compilation result info
     * @param tier Which compilation tier is in question.
     */
    default void onCompilationSuccess(OptimizedCallTarget target, TruffleInlining inliningDecision, GraphInfo graph, CompilationResultInfo result, int tier) {
    }

    /**
     * @deprecated Use
     *             {@link #onCompilationFailed(OptimizedCallTarget, String, boolean, boolean, int)}
     */
    @Deprecated
    default void onCompilationFailed(OptimizedCallTarget target, String reason, boolean bailout, boolean permanentBailout) {
        onCompilationFailed(target, reason, bailout, permanentBailout, 0);
    }

    /**
     * Notifies this object when compilation of {@code target} fails.
     *
     * @param target the call target whose compilation failed
     * @param reason a description of the failure
     * @param bailout specifies whether the failure was a bailout or an error in the compiler. A
     *            bailout means the compiler aborted the compilation based on some of property of
     *            {@code target} (e.g., too big). A non-bailout means an unexpected error in the
     *            compiler itself.
     * @param permanentBailout specifies if a bailout is due to a condition that probably won't
     *            change if the {@code target} is compiled again. This value is meaningless if
     *            {@code bailout == false}.
     * @param tier Which compilation tier is in question.
     */
    default void onCompilationFailed(OptimizedCallTarget target, String reason, boolean bailout, boolean permanentBailout, int tier) {
    }

    /**
     * Notifies this object when {@code target} is invalidated.
     *
     * @param target the call target whose compiled code was just invalidated
     * @param source the source object that caused the compilation to be invalidated. For example
     *            the source {@link Node} object. May be {@code null}.
     * @param reason a textual description of the reason why the compilation was invalidated. May be
     *            {@code null}.
     */
    default void onCompilationInvalidated(OptimizedCallTarget target, Object source, CharSequence reason) {
    }

    /**
     * Notifies this object when {@code target} has just deoptimized and is now executing in the
     * Truffle interpreter instead of executing compiled code.
     *
     * @param target the call target whose compiled code was just deoptimized
     * @param frame
     */
    default void onCompilationDeoptimized(OptimizedCallTarget target, Frame frame) {
    }

    /**
     * Notifies this object the {@link GraalTruffleRuntime} is being shut down.
     */
    default void onShutdown() {
    }

    /**
     * Notifies this object an engine using the {@link GraalTruffleRuntime} was closed.
     *
     * @param runtimeData the engine's compiler configuration
     */
    default void onEngineClosed(EngineData runtimeData) {
    }

    static void addASTSizeProperty(OptimizedCallTarget target, Map<String, Object> properties) {
        int nodeCount = target.getNonTrivialNodeCount();
        properties.put("AST", String.format("%4d", nodeCount));
    }

    /**
     * Determines if a failure is permanent.
     */
    static boolean isPermanentFailure(boolean bailout, boolean permanentBailout) {
        return !bailout || permanentBailout;
    }
}
