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
package com.oracle.graal.hotspot;

import static com.oracle.graal.api.code.CodeUtil.*;
import static com.oracle.graal.nodes.StructuredGraph.*;
import static com.oracle.graal.phases.GraalOptions.*;

import java.lang.reflect.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.code.CallingConvention.Type;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.debug.internal.*;
import com.oracle.graal.hotspot.meta.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.phases.*;
import com.oracle.graal.phases.common.*;
import com.oracle.graal.phases.tiers.*;
import com.oracle.graal.phases.util.*;

public final class CompilationTask implements Runnable {

    public static final ThreadLocal<Boolean> withinEnqueue = new ThreadLocal<Boolean>() {

        @Override
        protected Boolean initialValue() {
            return Boolean.valueOf(Thread.currentThread() instanceof CompilerThread);
        }
    };

    private enum CompilationStatus {
        Queued, Running
    }

    private final HotSpotGraalRuntime graalRuntime;
    private final PhasePlan plan;
    private final SuitesProvider suitesProvider;
    private final OptimisticOptimizations optimisticOpts;
    private final HotSpotResolvedJavaMethod method;
    private final int entryBCI;
    private final int id;
    private final AtomicReference<CompilationStatus> status;

    private StructuredGraph graph;

    public static CompilationTask create(HotSpotGraalRuntime graalRuntime, PhasePlan plan, OptimisticOptimizations optimisticOpts, HotSpotResolvedJavaMethod method, int entryBCI, int id) {
        return new CompilationTask(graalRuntime, plan, optimisticOpts, method, entryBCI, id);
    }

    private CompilationTask(HotSpotGraalRuntime graalRuntime, PhasePlan plan, OptimisticOptimizations optimisticOpts, HotSpotResolvedJavaMethod method, int entryBCI, int id) {
        assert id >= 0;
        this.graalRuntime = graalRuntime;
        this.plan = plan;
        this.suitesProvider = graalRuntime.getCapability(SuitesProvider.class);
        this.method = method;
        this.optimisticOpts = optimisticOpts;
        this.entryBCI = entryBCI;
        this.id = id;
        this.status = new AtomicReference<>(CompilationStatus.Queued);
    }

    public ResolvedJavaMethod getMethod() {
        return method;
    }

    public int getId() {
        return id;
    }

    public int getEntryBCI() {
        return entryBCI;
    }

    public void run() {
        withinEnqueue.set(Boolean.FALSE);
        try {
            runCompilation();
        } finally {
            if (method.currentTask() == this) {
                method.setCurrentTask(null);
            }
            withinEnqueue.set(Boolean.TRUE);
        }
    }

    /**
     * Time spent in compilation.
     */
    public static final DebugTimer CompilationTime = Debug.timer("CompilationTime");

    public static final DebugTimer CodeInstallationTime = Debug.timer("CodeInstallation");

    public void runCompilation() {
        /*
         * no code must be outside this try/finally because it could happen otherwise that
         * clearQueuedForCompilation() is not executed
         */
        try (TimerCloseable a = CompilationTime.start()) {
            if (!tryToChangeStatus(CompilationStatus.Queued, CompilationStatus.Running) || method.hasCompiledCode()) {
                return;
            }

            CompilationStatistics stats = CompilationStatistics.create(method, entryBCI != StructuredGraph.INVOCATION_ENTRY_BCI);
            final boolean printCompilation = PrintCompilation.getValue() && !TTY.isSuppressed();
            if (printCompilation) {
                TTY.println(getMethodDescription() + "...");
            }
            if (HotSpotPrintCompilation.getValue()) {
                printCompilation();
            }

            CompilationResult result = null;
            TTY.Filter filter = new TTY.Filter(PrintFilter.getValue(), method);
            long start = System.currentTimeMillis();
            try {
                result = Debug.scope("Compiling", new DebugDumpScope(String.valueOf(id), true), new Callable<CompilationResult>() {

                    @Override
                    public CompilationResult call() throws Exception {
                        graalRuntime.evictDeoptedGraphs();
                        Replacements replacements = graalRuntime.getReplacements();
                        graph = replacements.getMethodSubstitution(method);
                        if (graph == null || entryBCI != INVOCATION_ENTRY_BCI) {
                            graph = new StructuredGraph(method, entryBCI);
                        } else {
                            // Compiling method substitution - must clone the graph
                            graph = graph.copy();
                        }
                        InliningUtil.InlinedBytecodes.add(method.getCodeSize());
                        HotSpotRuntime runtime = graalRuntime.getRuntime();
                        CallingConvention cc = getCallingConvention(runtime, Type.JavaCallee, graph.method(), false);
                        Providers providers = new Providers(runtime, runtime, runtime, runtime, replacements);
                        return GraalCompiler.compileGraph(graph, cc, method, providers, graalRuntime.getBackend(), graalRuntime.getTarget(), graalRuntime.getCache(), plan, optimisticOpts,
                                        method.getSpeculationLog(), suitesProvider.getDefaultSuites(), new CompilationResult());
                    }
                });
            } finally {
                filter.remove();
                final boolean printAfterCompilation = PrintAfterCompilation.getValue() && !TTY.isSuppressed();
                if (printAfterCompilation) {
                    TTY.println(getMethodDescription() + String.format(" | %4dms %5dB", System.currentTimeMillis() - start, (result != null ? result.getTargetCodeSize() : -1)));
                } else if (printCompilation) {
                    TTY.println(String.format("%-6d Graal %-70s %-45s %-50s | %4dms %5dB", id, "", "", "", System.currentTimeMillis() - start, (result != null ? result.getTargetCodeSize() : -1)));
                }
            }

            try (TimerCloseable b = CodeInstallationTime.start()) {
                installMethod(result);
            }
            stats.finish(method);
        } catch (BailoutException bailout) {
            Debug.metric("Bailouts").increment();
            if (ExitVMOnBailout.getValue()) {
                TTY.cachedOut.println(MetaUtil.format("Bailout in %H.%n(%p)", method));
                bailout.printStackTrace(TTY.cachedOut);
                System.exit(-1);
            } else if (PrintBailout.getValue()) {
                TTY.cachedOut.println(MetaUtil.format("Bailout in %H.%n(%p)", method));
                bailout.printStackTrace(TTY.cachedOut);
            }
        } catch (Throwable t) {
            if (PrintStackTraceOnException.getValue() || ExitVMOnException.getValue()) {
                t.printStackTrace(TTY.cachedOut);
            }
            if (ExitVMOnException.getValue()) {
                System.exit(-1);
            }
        } finally {
            assert method.isQueuedForCompilation();
            method.clearQueuedForCompilation();
        }
    }

    private String getMethodDescription() {
        return String.format("%-6d Graal %-70s %-45s %-50s %s", id, method.getDeclaringClass().getName(), method.getName(), method.getSignature().getMethodDescriptor(),
                        entryBCI == StructuredGraph.INVOCATION_ENTRY_BCI ? "" : "(OSR@" + entryBCI + ") ");
    }

    /**
     * Print a HotSpot-style compilation message to the console.
     */
    private void printCompilation() {
        final boolean isOSR = entryBCI != StructuredGraph.INVOCATION_ENTRY_BCI;
        final int mod = method.getModifiers();
        TTY.println(String.format("%7d %4d %c%c%c%c%c       %s %s(%d bytes)", 0, id, isOSR ? '%' : ' ', Modifier.isSynchronized(mod) ? 's' : ' ', ' ', ' ', Modifier.isNative(mod) ? 'n' : ' ',
                        MetaUtil.format("%H::%n(%p)", method), isOSR ? "@ " + entryBCI + " " : "", method.getCodeSize()));
    }

    private void installMethod(final CompilationResult compResult) {
        Debug.scope("CodeInstall", new Object[]{new DebugDumpScope(String.valueOf(id), true), graalRuntime.getRuntime(), method}, new Runnable() {

            @Override
            public void run() {
                HotSpotInstalledCode installedCode = graalRuntime.getRuntime().installMethod(method, entryBCI, compResult);
                if (Debug.isDumpEnabled()) {
                    Debug.dump(new Object[]{compResult, installedCode}, "After code installation");
                }
                if (Debug.isLogEnabled()) {
                    Debug.log("%s", graalRuntime.getRuntime().disassemble(installedCode));
                }
            }

        });
    }

    private boolean tryToChangeStatus(CompilationStatus from, CompilationStatus to) {
        return status.compareAndSet(from, to);
    }

    @Override
    public String toString() {
        return "Compilation[id=" + id + ", " + MetaUtil.format("%H.%n(%p)", method) + (entryBCI == StructuredGraph.INVOCATION_ENTRY_BCI ? "" : "@" + entryBCI) + "]";
    }
}
