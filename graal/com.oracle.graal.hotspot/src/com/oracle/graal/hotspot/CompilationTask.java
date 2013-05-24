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

import java.lang.reflect.*;
import java.util.concurrent.*;

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

public final class CompilationTask implements Runnable, Comparable<CompilationTask> {

    public static final ThreadLocal<Boolean> withinEnqueue = new ThreadLocal<Boolean>() {

        @Override
        protected Boolean initialValue() {
            return Boolean.valueOf(Thread.currentThread() instanceof CompilerThread);
        }
    };

    private volatile boolean cancelled;
    private volatile boolean inProgress;

    private final HotSpotGraalRuntime graalRuntime;
    private final PhasePlan plan;
    private final OptimisticOptimizations optimisticOpts;
    private final HotSpotResolvedJavaMethod method;
    private final int entryBCI;
    private final int id;
    private final int priority;

    private StructuredGraph graph;

    public static CompilationTask create(HotSpotGraalRuntime graalRuntime, PhasePlan plan, OptimisticOptimizations optimisticOpts, HotSpotResolvedJavaMethod method, int entryBCI, int id, int priority) {
        return new CompilationTask(graalRuntime, plan, optimisticOpts, method, entryBCI, id, priority);
    }

    private CompilationTask(HotSpotGraalRuntime graalRuntime, PhasePlan plan, OptimisticOptimizations optimisticOpts, HotSpotResolvedJavaMethod method, int entryBCI, int id, int priority) {
        this.graalRuntime = graalRuntime;
        this.plan = plan;
        this.method = method;
        this.optimisticOpts = optimisticOpts;
        this.entryBCI = entryBCI;
        this.id = id;
        this.priority = priority;
    }

    public ResolvedJavaMethod getMethod() {
        return method;
    }

    public int getPriority() {
        return priority;
    }

    public void cancel() {
        cancelled = true;
    }

    public boolean isInProgress() {
        return inProgress;
    }

    public boolean isCancelled() {
        return cancelled;
    }

    public int getEntryBCI() {
        return entryBCI;
    }

    public void run() {
        withinEnqueue.set(Boolean.FALSE);
        try {
            if (cancelled) {
                return;
            }
            inProgress = true;
            if (GraalOptions.DynamicCompilePriority) {
                int threadPriority = priority < GraalOptions.SlowQueueCutoff ? Thread.NORM_PRIORITY : Thread.MIN_PRIORITY;
                if (Thread.currentThread().getPriority() != threadPriority) {
                    Thread.currentThread().setPriority(threadPriority);
                }
            }
            runCompilation();
        } finally {
            if (method.currentTask() == this) {
                method.setCurrentTask(null);
            }
            graalRuntime.getCompilerToVM().clearQueuedForCompilation(method);
            inProgress = false;
            withinEnqueue.set(Boolean.TRUE);
        }
    }

    /**
     * Time spent in compilation.
     */
    public static final DebugTimer CompilationTime = Debug.timer("CompilationTime");

    public void runCompilation() {
        CompilationStatistics stats = CompilationStatistics.create(method, entryBCI != StructuredGraph.INVOCATION_ENTRY_BCI);
        try (TimerCloseable a = CompilationTime.start()) {
            final boolean printCompilation = GraalOptions.PrintCompilation && !TTY.isSuppressed();
            if (printCompilation) {
                TTY.println(String.format("%-6d Graal %-70s %-45s %-50s %s...", id, method.getDeclaringClass().getName(), method.getName(), method.getSignature(),
                                entryBCI == StructuredGraph.INVOCATION_ENTRY_BCI ? "" : "(OSR@" + entryBCI + ") "));
            }
            if (GraalOptions.HotSpotPrintCompilation) {
                printCompilation();
            }

            CompilationResult result = null;
            TTY.Filter filter = new TTY.Filter(GraalOptions.PrintFilter, method);
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
                        return GraalCompiler.compileGraph(graph, cc, method, runtime, replacements, graalRuntime.getBackend(), graalRuntime.getTarget(), graalRuntime.getCache(), plan, optimisticOpts,
                                        method.getSpeculationLog());
                    }
                });
            } finally {
                filter.remove();
                if (printCompilation) {
                    TTY.println(String.format("%-6d Graal %-70s %-45s %-50s | %4dms %5dB", id, "", "", "", System.currentTimeMillis() - start, (result != null ? result.getTargetCodeSize() : -1)));
                }
            }

            installMethod(result);
        } catch (BailoutException bailout) {
            Debug.metric("Bailouts").increment();
            if (GraalOptions.ExitVMOnBailout) {
                TTY.cachedOut.println(MetaUtil.format("Bailout in %H.%n(%p)", method));
                bailout.printStackTrace(TTY.cachedOut);
                System.exit(-1);
            } else if (GraalOptions.PrintBailout) {
                TTY.cachedOut.println(MetaUtil.format("Bailout in %H.%n(%p)", method));
                bailout.printStackTrace(TTY.cachedOut);
            }
        } catch (Throwable t) {
            if (GraalOptions.PrintStackTraceOnException || GraalOptions.ExitVMOnException) {
                t.printStackTrace(TTY.cachedOut);
            }
            if (GraalOptions.ExitVMOnException) {
                System.exit(-1);
            }
        }
        stats.finish(method);
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
                HotSpotInstalledCode installedCode = graalRuntime.getRuntime().installMethod(method, graph, entryBCI, compResult);
                if (Debug.isDumpEnabled()) {
                    Debug.dump(new Object[]{compResult, installedCode}, "After code installation");
                }
                if (Debug.isLogEnabled()) {
                    Debug.log("%s", graalRuntime.getRuntime().disassemble(installedCode));
                }
            }

        });
    }

    @Override
    public int compareTo(CompilationTask o) {
        if (priority < o.priority) {
            return -1;
        }
        if (priority > o.priority) {
            return 1;
        }
        return id < o.id ? -1 : (id > o.id ? 1 : 0);
    }

    @Override
    public String toString() {
        return "Compilation[id=" + id + ", prio=" + priority + " " + MetaUtil.format("%H.%n(%p)", method) + (entryBCI == StructuredGraph.INVOCATION_ENTRY_BCI ? "" : "@" + entryBCI) + "]";
    }
}
