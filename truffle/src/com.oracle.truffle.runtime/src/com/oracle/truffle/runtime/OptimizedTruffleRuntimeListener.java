/*
 * Copyright (c) 2014, 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.runtime;

import java.util.Map;
import java.util.function.Supplier;

import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.compiler.TruffleCompilable;
import com.oracle.truffle.compiler.TruffleCompilationTask;
import com.oracle.truffle.compiler.TruffleCompilerListener.CompilationResultInfo;
import com.oracle.truffle.compiler.TruffleCompilerListener.GraphInfo;

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
public interface OptimizedTruffleRuntimeListener {

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
     * Notifies this object after {@code target} is added to the compilation queue.
     *
     * @param target the call target that has just been enqueued for compilation
     * @param tier Which compilation tier is in question.
     */
    default void onCompilationQueued(OptimizedCallTarget target, int tier) {
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
     * @deprecated Use {@link #onCompilationStarted(OptimizedCallTarget, AbstractCompilationTask)}
     *             instead
     */
    @Deprecated(since = "21.0")
    @SuppressWarnings("unused")
    default void onCompilationStarted(OptimizedCallTarget target, int tier) {
    }

    /**
     * Notifies this object when compilation of {@code target} is about to start.
     *
     * @param target the call target about to be compiled
     * @param task which compilation task is in question.
     *
     * @deprecated Use {@link #onCompilationStarted(OptimizedCallTarget, AbstractCompilationTask)}
     *             instead
     */
    @Deprecated(since = "23.0")
    @SuppressWarnings({"unused", "deprecation"})
    default void onCompilationStarted(OptimizedCallTarget target, TruffleCompilationTask task) {
        onCompilationStarted(target, task.tier());
    }

    /**
     * Notifies this object when compilation of {@code target} is about to start.
     *
     * @param target the call target about to be compiled
     * @param task which compilation task is in question.
     */
    @SuppressWarnings({"unused", "deprecation"})
    default void onCompilationStarted(OptimizedCallTarget target, AbstractCompilationTask task) {
        onCompilationStarted(target, (TruffleCompilationTask) task);
    }

    /**
     * Notifies this object when compilation of {@code target} has completed partial evaluation and
     * is about to perform compilation of the graph produced by partial evaluation.
     *
     * @param target the call target being compiled
     * @param inliningDecision the inlining plan used during partial evaluation
     * @param graph access to compiler graph info
     * @deprecated use
     *             {@link #onCompilationTruffleTierFinished(OptimizedCallTarget, AbstractCompilationTask, GraphInfo)}
     *             instead
     */
    @Deprecated
    @SuppressWarnings("deprecation")
    default void onCompilationTruffleTierFinished(OptimizedCallTarget target, TruffleInlining inliningDecision, GraphInfo graph) {
    }

    /**
     * Notifies this object when compilation of {@code target} has completed partial evaluation and
     * is about to perform compilation of the graph produced by partial evaluation.
     *
     * @param target the call target being compiled
     * @param task the compilation task
     * @param graph access to compiler graph info
     */
    @SuppressWarnings("deprecation")
    default void onCompilationTruffleTierFinished(OptimizedCallTarget target, AbstractCompilationTask task, GraphInfo graph) {
        onCompilationTruffleTierFinished(target, task.getInlining(), graph);
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
     *             {@link #onCompilationSuccess(OptimizedCallTarget, AbstractCompilationTask, GraphInfo, CompilationResultInfo)}
     */
    @Deprecated(since = "21.0")
    @SuppressWarnings("deprecation")
    default void onCompilationSuccess(OptimizedCallTarget target, TruffleInlining inliningDecision, GraphInfo graph, CompilationResultInfo result) {
        onCompilationSuccess(target, inliningDecision, graph, result, 0);
    }

    /**
     * @deprecated Use
     *             {@link #onCompilationSuccess(OptimizedCallTarget, AbstractCompilationTask, GraphInfo, CompilationResultInfo)}
     */
    @Deprecated(since = "23.0")
    @SuppressWarnings({"unused", "deprecation"})
    default void onCompilationSuccess(OptimizedCallTarget target, TruffleInlining inliningDecision, GraphInfo graph, CompilationResultInfo result, int tier) {
    }

    /**
     * Notifies this object when compilation of {@code target} succeeds.
     *
     * @param target the call target whose compilation succeeded
     * @param task the task
     * @param graph access to compiler graph info
     * @param result access to compilation result info
     */
    @SuppressWarnings("deprecation")
    default void onCompilationSuccess(OptimizedCallTarget target, AbstractCompilationTask task, GraphInfo graph, CompilationResultInfo result) {
        onCompilationSuccess(target, task.getInlining(), graph, result);
    }

    /**
     * @deprecated Use
     *             {@link #onCompilationFailed(OptimizedCallTarget, String, boolean, boolean, int, Supplier)}
     *             )}
     */
    @Deprecated(since = "21.0")
    default void onCompilationFailed(OptimizedCallTarget target, String reason, boolean bailout, boolean permanentBailout) {
        onCompilationFailed(target, reason, bailout, permanentBailout, 0, null);
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
     *
     * @deprecated Use
     *             {@link #onCompilationFailed(OptimizedCallTarget, String, boolean, boolean, int, Supplier)}
     *             )}
     */
    @Deprecated
    default void onCompilationFailed(OptimizedCallTarget target, String reason, boolean bailout, boolean permanentBailout, int tier) {
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
     * @param lazyStackTrace a serialized representation of the exception indicating the reason and
     *            stack trace for a compilation failure, or {@code null} in the case of a bailout or
     *            when the compiler does not provide a stack trace. See
     *            {@link TruffleCompilable#serializeException(Throwable)}.
     *
     */
    default void onCompilationFailed(OptimizedCallTarget target, String reason, boolean bailout, boolean permanentBailout, int tier, Supplier<String> lazyStackTrace) {
        onCompilationFailed(target, reason, bailout, permanentBailout, tier);
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
     * Notifies this object the {@link OptimizedTruffleRuntime} is being shut down.
     */
    default void onShutdown() {
    }

    /**
     * Notifies this object an engine using the {@link OptimizedTruffleRuntime} was closed.
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
