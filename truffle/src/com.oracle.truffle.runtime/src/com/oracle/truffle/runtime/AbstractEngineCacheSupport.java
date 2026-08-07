/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.api.TruffleOptions;
import org.graalvm.options.OptionKey;
import org.graalvm.options.OptionValues;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.function.BooleanSupplier;
import java.util.function.Function;

public abstract class AbstractEngineCacheSupport implements EngineCacheSupport {

    protected static final String COMPILE_HELP = ""//
                    + "Policy to use to force compilation for executed call targets before persisting the engine. "//
                    + "Possible values are:%n"//
                    + "  - 'none':     No compilations will be persisted and existing compilations will be invalidated.%n"//
                    + "  - 'compiled': No compilations will be forced but finished compilations will be persisted.%n"//
                    + "  - 'hot':      (default) All started compilations will be completed and then persisted.%n"//
                    + "  - 'aot':      All started and AOT compilable roots will be forced to compile and persisted.%n"//
                    + "  - 'executed': All executed and all AOT compilable roots will be forced to compile.";

    protected static final String COMPILE_USAGE_SYNTAX = "none|compiled|hot|aot|executed";

    public enum CompilePolicy {
        executed,
        aot,
        hot,
        compiled,
        none
    }

    public AbstractEngineCacheSupport() {
    }

    protected abstract OptionKey<Boolean> getTraceOption();

    protected final void trace(EngineData e, String message, Object... arguments) {
        if (e.getEngineOptions().get(getTraceOption())) {
            e.getLogger("engine").info("[cache] " + String.format(message, arguments));
        }
    }

    protected final void traceLoad(OptionValues options, Function<String, TruffleLogger> loggerFactory, String message, Object... arguments) {
        if (options.get(getTraceOption())) {
            loggerFactory.apply("engine").info("[cache] " + String.format(message, arguments));
        }
    }

    protected record FinalizationResult(Object result) {
    }

    protected final Object prepareEngine(EngineData e, CompilePolicy mode, boolean useLastTier, boolean preinitializeContext, BooleanSupplier cancelledPredicate) throws CancellationException {
        if (preinitializeContext) {
            e.preinitializeContext();
        }
        Object result = e.finalizeStore();
        e.putEngineLocal(FinalizationResult.class, new FinalizationResult(result));

        Collection<OptimizedCallTarget> callTargets = e.getCallTargets();
        for (OptimizedCallTarget target : callTargets) {
            if (target.engine != e) {
                throw new IllegalStateException("CallTargets from different engines cannot be compiled together.");
            }
        }
        BooleanSupplier previousCancelledPredicate = e.cancelledPredicate;
        e.cancelledPredicate = cancelledPredicate;
        try {
            List<OptimizedCallTarget> compileQueue = prepareTargetsForCompilation(e, callTargets, mode, cancelledPredicate);
            if (compileQueue != null && !compileQueue.isEmpty()) {
                compileTargets(e, compileQueue, useLastTier, cancelledPredicate);
            }
        } finally {
            e.cancelledPredicate = previousCancelledPredicate;
        }
        return e.getPolyglotEngine();
    }

    @SuppressWarnings("unchecked")
    protected final Object prepareEngine(EngineData e, CompilePolicy mode, boolean useLastTier, boolean preinitializeContext) {
        return prepareEngine(e, mode, useLastTier, preinitializeContext, null);
    }

    /**
     * Invoked when the engine should be reset after persist to be usable again.
     */
    @SuppressWarnings("static-method")
    protected final void restoreEngine(EngineData e) {
        FinalizationResult result = e.getEngineLocal(FinalizationResult.class);
        if (result != null) {
            e.restoreStore(result.result);
            e.clearEngineLocal(FinalizationResult.class);
        }
    }

    private void compileTargets(EngineData e, Collection<OptimizedCallTarget> compileQueue, boolean useLastTier, BooleanSupplier cancelledPredicate) {
        trace(e, "Force compiling %s roots for engine caching.", compileQueue.size());
        // enqueue missing compilations.
        List<OptimizedCallTarget> waitForTargets = new ArrayList<>();
        for (OptimizedCallTarget compilation : compileQueue) {
            checkCancellation(cancelledPredicate);
            if (compilation.isValid()) {
                continue;
            }
            if (!compilation.isInitialized()) {
                throw new AssertionError("Should not reach here.");
            }

            boolean alreadyCompiled = compilation.compile(useLastTier);
            if (!alreadyCompiled) {
                waitForTargets.add(compilation);
            }
        }
        for (OptimizedCallTarget waitTarget : waitForTargets) {
            try {
                OptimizedTruffleRuntime.getRuntime().waitForCompilation(waitTarget, Long.MAX_VALUE, cancelledPredicate);
            } catch (ExecutionException | TimeoutException ex) {
                throw new AssertionError(ex);
            }
            checkCancellation(cancelledPredicate);
        }
        for (OptimizedCallTarget waitTarget : waitForTargets) {
            if (waitTarget.isSubmittedForCompilation()) {
                throw new AssertionError("Cannot continue if compilation is still ongoing. Still compiling:" + waitTarget);
            }
        }
    }

    private List<OptimizedCallTarget> prepareTargetsForCompilation(EngineData e, Collection<OptimizedCallTarget> callTargets, CompilePolicy mode, BooleanSupplier cancelledPredicate)
                    throws AssertionError {
        List<OptimizedCallTarget> compileQueue;
        trace(e, "Force compile targets mode: %s", mode);
        switch (mode) {
            case none:
                cancelCompilations(e, callTargets);
                invalidateCompilations(e, callTargets, cancelledPredicate);
                compileQueue = null;
                break;
            case compiled:
                cancelCompilations(e, callTargets);
                compileQueue = null;
                break;
            case hot:
                waitForCompilations(callTargets, cancelledPredicate);
                compileQueue = initializeCompileQueue(callTargets);
                compileQueue.removeIf(c -> !c.isInitialized() || !c.shouldCompile());
                break;
            case aot:
                waitForCompilations(callTargets, cancelledPredicate);
                compileQueue = initializeCompileQueue(callTargets);
                // keep all uninitialized calls in the queue for prepareForAOT
                compileQueue.removeIf(c -> c.isInitialized() && !c.shouldCompile());
                prepareForAOT(e, compileQueue, true, cancelledPredicate);
                break;
            case executed:
                waitForCompilations(callTargets, cancelledPredicate);
                compileQueue = initializeCompileQueue(callTargets);
                prepareForAOT(e, compileQueue, false, cancelledPredicate);
                break;
            default:
                throw new AssertionError("Invalid compile mode.");
        }
        return compileQueue;
    }

    protected static void checkCancellation(BooleanSupplier cancelledPredicate) {
        if (!TruffleOptions.AOT) {
            return;
        }
        if (cancelledPredicate == null) {
            return;
        }

        if (cancelledPredicate.getAsBoolean()) {
            throw new CancellationException("Storing the cache was cancelled.");
        }
    }

    private static List<OptimizedCallTarget> initializeCompileQueue(Collection<OptimizedCallTarget> callTargets) {
        resetSplitting(callTargets);
        return sortCallTargetsByHotness(callTargets);
    }

    private static void prepareForAOT(EngineData e, List<OptimizedCallTarget> triggerCompile, boolean filterForAOT, BooleanSupplier cancelledPredicate) {
        TruffleLanguage<?> currentLanguage = null;
        Object prev = null;
        try {
            ListIterator<OptimizedCallTarget> iterator = triggerCompile.listIterator();
            while (iterator.hasNext()) {
                checkCancellation(cancelledPredicate);
                OptimizedCallTarget target = iterator.next();
                if (!target.wasExecuted()) {
                    TruffleLanguage<?> language = e.getLanguage(target);
                    if (language != currentLanguage) {
                        // we need to enter and leave the engine for AOT preparation.
                        // already initialized language instance for the root node should be
                        // available.
                        if (currentLanguage != null) {
                            e.leaveLanguage(currentLanguage, prev);
                        }
                        if (language != null) {
                            prev = e.enterLanguage(language);
                        }
                        currentLanguage = language;
                    }

                    if (!target.prepareForAOT()) {
                        /*
                         * Seems this call target has no support for AOT compilation. No need to try
                         * to compile it.
                         */
                        iterator.remove();
                    }
                } else if (filterForAOT) {
                    iterator.remove();
                }
            }
        } finally {
            // we should always ensure to leave the language we are currently entered in
            if (currentLanguage != null) {
                e.leaveLanguage(currentLanguage, prev);
            }
        }

    }

    private void cancelCompilations(EngineData e, Collection<OptimizedCallTarget> callTargets) {
        int cancelled = 0;
        for (OptimizedCallTarget target : callTargets) {
            if (target.cancelCompilation("Engine cache compile mode none cancels all compiles if they are not yet completed.")) {
                cancelled++;
            }
        }
        if (cancelled > 0) {
            trace(e, "Cancelled %s compilations.", cancelled);
            waitForCompilations(callTargets, null);
        }
    }

    private void invalidateCompilations(EngineData e, Collection<OptimizedCallTarget> callTargets, BooleanSupplier cancelledPredicate) {
        int cancelled = 0;
        for (OptimizedCallTarget target : callTargets) {
            if (target.isSubmittedForCompilation()) {
                throw new IllegalStateException("A call target that will be persisted is currently compiling.");
            }
            if (target.invalidate("CacheCompile mode is none")) {
                cancelled++;
            }
            checkCancellation(cancelledPredicate);
        }
        if (cancelled > 0) {
            waitForCompilations(callTargets, null);
            trace(e, "%s compilations invalidated.", cancelled);
        }
    }

    private static void waitForCompilations(Collection<OptimizedCallTarget> callTargets, BooleanSupplier cancelledPredicate) {
        OptimizedTruffleRuntime runtime = OptimizedTruffleRuntime.getRuntime();
        for (OptimizedCallTarget callTarget : callTargets) {
            try {
                runtime.waitForCompilation(callTarget, Long.MAX_VALUE, cancelledPredicate);
            } catch (ExecutionException | TimeoutException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private static void resetSplitting(Collection<OptimizedCallTarget> targets) {
        for (OptimizedCallTarget target : targets) {
            /*
             * Resetting split decisions. Splits might otherwise lead to immediate deoptimizations.
             */
            target.resetNeedsSplit();
        }
    }

    private static List<OptimizedCallTarget> sortCallTargetsByHotness(Collection<OptimizedCallTarget> originalTargets) {
        OptimizedCallTarget[] targets = originalTargets.toArray(new OptimizedCallTarget[0]);
        Arrays.sort(targets, new Comparator<OptimizedCallTarget>() {
            @Override
            public int compare(OptimizedCallTarget o1, OptimizedCallTarget o2) {
                int count1 = o1.getCallAndLoopCount();
                int count2 = o2.getCallAndLoopCount();
                // order descending
                return Integer.compare(count2, count1);
            }
        });
        // we remove often, and we don't do random access, so linked list is best here
        return new LinkedList<>(Arrays.asList(targets));
    }

}
