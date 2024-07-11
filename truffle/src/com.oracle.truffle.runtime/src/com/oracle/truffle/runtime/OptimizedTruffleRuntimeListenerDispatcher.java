/*
 * Copyright (c) 2018, 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Supplier;

import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.compiler.TruffleCompilable;
import com.oracle.truffle.compiler.TruffleCompilationTask;
import com.oracle.truffle.compiler.TruffleCompilerListener;

/**
 * A collection for broadcasting {@link OptimizedTruffleRuntimeListener} events and converting
 * {@link TruffleCompilerListener} events to {@link OptimizedTruffleRuntimeListener} events.
 */
@SuppressWarnings("serial")
final class OptimizedTruffleRuntimeListenerDispatcher extends CopyOnWriteArrayList<OptimizedTruffleRuntimeListener> implements OptimizedTruffleRuntimeListener, TruffleCompilerListener {

    @Override
    public boolean add(OptimizedTruffleRuntimeListener e) {
        if (e != this && !contains(e)) {
            return super.add(e);
        }
        return false;
    }

    @Override
    public void onCompilationSplit(OptimizedDirectCallNode callNode) {
        invokeListeners((l) -> l.onCompilationSplit(callNode));
    }

    @Override
    public void onCompilationSplitFailed(OptimizedDirectCallNode callNode, CharSequence reason) {
        invokeListeners((l) -> l.onCompilationSplitFailed(callNode, reason));
    }

    @Override
    public void onCompilationQueued(OptimizedCallTarget target, int tier) {
        invokeListeners((l) -> l.onCompilationQueued(target, tier));
    }

    @Override
    public void onCompilationDequeued(OptimizedCallTarget target, Object source, CharSequence reason, int tier) {
        invokeListeners((l) -> l.onCompilationDequeued(target, source, reason, tier));
    }

    @Override
    public void onCompilationFailed(OptimizedCallTarget target, String reason, boolean bailout, boolean permanent, int tier, Supplier<String> lazyStackTrace) {
        invokeListeners((l) -> l.onCompilationFailed(target, reason, bailout, permanent, tier, lazyStackTrace));
    }

    @Override
    public void onCompilationStarted(OptimizedCallTarget target, AbstractCompilationTask task) {
        invokeListeners((l) -> l.onCompilationStarted(target, task));
    }

    @Override
    public void onCompilationTruffleTierFinished(OptimizedCallTarget target, AbstractCompilationTask task, GraphInfo graph) {
        invokeListeners((l) -> l.onCompilationTruffleTierFinished(target, task, graph));
    }

    @Override
    public void onCompilationGraalTierFinished(OptimizedCallTarget target, GraphInfo graph) {
        invokeListeners((l) -> l.onCompilationGraalTierFinished(target, graph));
    }

    @Override
    public void onCompilationSuccess(OptimizedCallTarget target, AbstractCompilationTask task, GraphInfo graph, CompilationResultInfo result) {
        invokeListeners((l) -> l.onCompilationSuccess(target, task, graph, result));
    }

    @Override
    public void onCompilationInvalidated(OptimizedCallTarget target, Object source, CharSequence reason) {
        invokeListeners((l) -> l.onCompilationInvalidated(target, source, reason));
    }

    @Override
    public void onCompilationDeoptimized(OptimizedCallTarget target, Frame frame) {
        invokeListeners((l) -> l.onCompilationDeoptimized(target, frame));
    }

    @Override
    public void onShutdown() {
        invokeListeners((l) -> l.onShutdown());
    }

    @Override
    public void onEngineClosed(EngineData runtimeData) {
        invokeListeners((l) -> l.onEngineClosed(runtimeData));
    }

    private void invokeListeners(Consumer<? super OptimizedTruffleRuntimeListener> action) {
        Throwable exception = null;
        for (OptimizedTruffleRuntimeListener l : this) {
            try {
                action.accept(l);
            } catch (ThreadDeath t) {
                throw t;
            } catch (Throwable t) {
                if (exception == null) {
                    exception = t;
                } else {
                    exception.addSuppressed(t);
                }
            }
        }
        if (exception != null) {
            throw sthrow(RuntimeException.class, exception);
        }
    }

    @SuppressWarnings({"unchecked", "unused"})
    private static <E extends Throwable> RuntimeException sthrow(Class<E> type, Throwable ex) throws E {
        throw (E) ex;
    }

    // Conversion from TruffleCompilerListener events to OptimizedTruffleRuntimeListener events

    @Override
    public void onTruffleTierFinished(TruffleCompilable compilable, TruffleCompilationTask task, GraphInfo graph) {
        onCompilationTruffleTierFinished((OptimizedCallTarget) compilable, (AbstractCompilationTask) task, graph);
    }

    @Override
    public void onGraalTierFinished(TruffleCompilable compilable, GraphInfo graph) {
        onCompilationGraalTierFinished((OptimizedCallTarget) compilable, graph);
    }

    @Override
    public void onSuccess(TruffleCompilable compilable, TruffleCompilationTask task, GraphInfo graph, CompilationResultInfo result, int tier) {
        onCompilationSuccess((OptimizedCallTarget) compilable, (AbstractCompilationTask) task, graph, result);
    }

    /**
     * On GraalVM, the {@link TruffleCompilerListener} interface is loaded from the JDK's jimage
     * rather than from a Maven dependency. The {@code TruffleCompilerListener} loaded from the LTS
     * GraalVM-23.1.3 JDK does not delegate the {@code onFailure} method to the newer version, so we
     * need to handle the delegation here.
     * <p>
     * GR-54187: Remove in graalvm-25.1
     * </p>
     */
    @Override
    @SuppressWarnings("deprecation")
    public void onFailure(TruffleCompilable compilable, String reason, boolean bailout, boolean permanentBailout, int tier) {
        onCompilationFailed((OptimizedCallTarget) compilable, reason, bailout, permanentBailout, tier, null);
    }

    @Override
    public void onFailure(TruffleCompilable compilable, String reason, boolean bailout, boolean permanentBailout, int tier, Supplier<String> lazyStackTrace) {
        onCompilationFailed((OptimizedCallTarget) compilable, reason, bailout, permanentBailout, tier, lazyStackTrace);
    }

    @Override
    public void onCompilationRetry(TruffleCompilable compilable, TruffleCompilationTask task) {
        onCompilationQueued((OptimizedCallTarget) compilable, task.tier());
        onCompilationStarted((OptimizedCallTarget) compilable, (AbstractCompilationTask) task);
    }
}
