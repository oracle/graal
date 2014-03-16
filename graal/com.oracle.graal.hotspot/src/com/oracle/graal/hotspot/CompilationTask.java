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

import static com.oracle.graal.api.code.CallingConvention.Type.*;
import static com.oracle.graal.api.code.CodeUtil.*;
import static com.oracle.graal.compiler.GraalCompiler.*;
import static com.oracle.graal.hotspot.bridge.VMToCompilerImpl.*;
import static com.oracle.graal.nodes.StructuredGraph.*;
import static com.oracle.graal.phases.GraalOptions.*;
import static com.oracle.graal.phases.common.InliningUtil.*;

import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.code.CallingConvention.Type;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.CompilerThreadFactory.CompilerThread;
import com.oracle.graal.debug.*;
import com.oracle.graal.debug.Debug.Scope;
import com.oracle.graal.debug.internal.*;
import com.oracle.graal.hotspot.bridge.*;
import com.oracle.graal.hotspot.meta.*;
import com.oracle.graal.hotspot.phases.*;
import com.oracle.graal.lir.asm.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.phases.*;
import com.oracle.graal.phases.tiers.*;

public class CompilationTask implements Runnable, Comparable {

    private static final long TIMESTAMP_START = System.currentTimeMillis();

    // Keep static finals in a group with withinEnqueue as the last one. CompilationTask can be
    // called from within it's own clinit so it needs to be careful about accessing state. Once
    // withinEnqueue is non-null we assume that CompilationTask is fully initialized.
    private static final AtomicLong uniqueTaskIds = new AtomicLong();

    private static final DebugMetric BAILOUTS = Debug.metric("Bailouts");

    private static final ThreadLocal<Boolean> withinEnqueue = new ThreadLocal<Boolean>() {

        @Override
        protected Boolean initialValue() {
            return Boolean.valueOf(Thread.currentThread() instanceof CompilerThread);
        }
    };

    public static final boolean isWithinEnqueue() {
        // It's possible this can be called before the <clinit> has completed so check for null
        return withinEnqueue == null || withinEnqueue.get();
    }

    public static class Enqueueing implements Closeable {
        public Enqueueing() {
            assert !withinEnqueue.get();
            withinEnqueue.set(Boolean.TRUE);
        }

        public void close() {
            withinEnqueue.set(Boolean.FALSE);
        }
    }

    private enum CompilationStatus {
        Queued, Running, Finished
    }

    private final HotSpotBackend backend;
    private final HotSpotResolvedJavaMethod method;
    private final int entryBCI;
    private final int id;
    private final AtomicReference<CompilationStatus> status;

    private StructuredGraph graph;

    /**
     * A long representing the sequence number of this task. Used for sorting the compile queue.
     */
    private long taskId;

    private boolean blocking;

    public CompilationTask(HotSpotBackend backend, HotSpotResolvedJavaMethod method, int entryBCI, boolean blocking) {
        this.backend = backend;
        this.method = method;
        this.entryBCI = entryBCI;
        this.id = method.allocateCompileId(entryBCI);
        this.blocking = blocking;
        this.taskId = uniqueTaskIds.incrementAndGet();
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
            runCompilation(true);
        } finally {
            withinEnqueue.set(Boolean.TRUE);
            status.set(CompilationStatus.Finished);
            synchronized (this) {
                notifyAll();
            }
        }
    }

    /**
     * Block waiting till the compilation completes.
     */
    public synchronized void block() {
        while (status.get() != CompilationStatus.Finished) {
            try {
                wait();
            } catch (InterruptedException e) {
                // Ignore and retry
            }
        }
    }

    /**
     * Sort blocking tasks before non-blocking ones and then by lowest taskId within the group.
     */
    public int compareTo(Object o) {
        if (!(o instanceof CompilationTask)) {
            return 1;
        }
        CompilationTask task2 = (CompilationTask) o;
        if (this.blocking != task2.blocking) {
            // Blocking CompilationTasks are always higher than CompilationTasks
            return task2.blocking ? 1 : -1;
        }
        // Within the two groups sort by sequence id, so they are processed in insertion order.
        return this.taskId > task2.taskId ? 1 : -1;
    }

    /**
     * Time spent in compilation.
     */
    public static final DebugTimer CompilationTime = Debug.timer("CompilationTime");

    public static final DebugTimer CodeInstallationTime = Debug.timer("CodeInstallation");

    protected Suites getSuites(HotSpotProviders providers) {
        return providers.getSuites().getDefaultSuites();
    }

    protected PhaseSuite<HighTierContext> getGraphBuilderSuite(HotSpotProviders providers) {
        PhaseSuite<HighTierContext> suite = providers.getSuites().getDefaultGraphBuilderSuite();

        boolean osrCompilation = entryBCI != StructuredGraph.INVOCATION_ENTRY_BCI;
        if (osrCompilation) {
            suite = suite.copy();
            suite.appendPhase(new OnStackReplacementPhase());
        }
        return suite;
    }

    protected OptimisticOptimizations getOptimisticOpts(ProfilingInfo profilingInfo) {
        return new OptimisticOptimizations(profilingInfo);
    }

    protected ProfilingInfo getProfilingInfo() {
        boolean osrCompilation = entryBCI != StructuredGraph.INVOCATION_ENTRY_BCI;
        return method.getCompilationProfilingInfo(osrCompilation);
    }

    public void runCompilation(boolean clearFromCompilationQueue) {
        /*
         * no code must be outside this try/finally because it could happen otherwise that
         * clearQueuedForCompilation() is not executed
         */

        HotSpotVMConfig config = backend.getRuntime().getConfig();
        long previousInlinedBytecodes = InlinedBytecodes.getCurrentValue();
        long previousCompilationTime = CompilationTime.getCurrentValue();
        HotSpotInstalledCode installedCode = null;
        try (TimerCloseable a = CompilationTime.start()) {
            if (!tryToChangeStatus(CompilationStatus.Queued, CompilationStatus.Running)) {
                return;
            }
            boolean isOSR = entryBCI != StructuredGraph.INVOCATION_ENTRY_BCI;

            // If there is already compiled code for this method on our level we simply return.
            // Graal compiles are always at the highest compile level, even in non-tiered mode so we
            // only need to check for that value.
            if (method.hasCodeAtLevel(entryBCI, config.compilationLevelFullOptimization)) {
                return;
            }

            CompilationStatistics stats = CompilationStatistics.create(method, isOSR);
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
            try (Scope s = Debug.scope("Compiling", new DebugDumpScope(String.valueOf(id), true))) {
                Map<ResolvedJavaMethod, StructuredGraph> graphCache = null;
                if (GraalOptions.CacheGraphs.getValue()) {
                    graphCache = new HashMap<>();
                }

                HotSpotProviders providers = backend.getProviders();
                Replacements replacements = providers.getReplacements();
                graph = replacements.getMethodSubstitution(method);
                if (graph == null || entryBCI != INVOCATION_ENTRY_BCI) {
                    graph = new StructuredGraph(method, entryBCI);
                } else {
                    // Compiling method substitution - must clone the graph
                    graph = graph.copy();
                }
                InlinedBytecodes.add(method.getCodeSize());
                CallingConvention cc = getCallingConvention(providers.getCodeCache(), Type.JavaCallee, graph.method(), false);
                if (graph.getEntryBCI() != StructuredGraph.INVOCATION_ENTRY_BCI) {
                    // for OSR, only a pointer is passed to the method.
                    JavaType[] parameterTypes = new JavaType[]{providers.getMetaAccess().lookupJavaType(long.class)};
                    CallingConvention tmp = providers.getCodeCache().getRegisterConfig().getCallingConvention(JavaCallee, providers.getMetaAccess().lookupJavaType(void.class), parameterTypes,
                                    backend.getTarget(), false);
                    cc = new CallingConvention(cc.getStackSize(), cc.getReturn(), tmp.getArgument(0));
                }
                Suites suites = getSuites(providers);
                ProfilingInfo profilingInfo = getProfilingInfo();
                OptimisticOptimizations optimisticOpts = getOptimisticOpts(profilingInfo);
                result = compileGraph(graph, null, cc, method, providers, backend, backend.getTarget(), graphCache, getGraphBuilderSuite(providers), optimisticOpts, profilingInfo,
                                method.getSpeculationLog(), suites, new CompilationResult(), CompilationResultBuilderFactory.Default);
                result.setId(getId());
                result.setEntryBCI(entryBCI);
            } catch (Throwable e) {
                throw Debug.handle(e);
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
                installedCode = installMethod(result);
                if (!isOSR) {
                    ProfilingInfo profile = method.getProfilingInfo();
                    profile.setCompilerIRSize(StructuredGraph.class, graph.getNodeCount());
                }
            }
            stats.finish(method, installedCode);
        } catch (BailoutException bailout) {
            BAILOUTS.increment();
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
            if ((config.ciTime || config.ciTimeEach || PrintCompRate.getValue() != 0) && installedCode != null) {
                long processedBytes = InlinedBytecodes.getCurrentValue() - previousInlinedBytecodes;
                long time = CompilationTime.getCurrentValue() - previousCompilationTime;
                TimeUnit timeUnit = CompilationTime.getTimeUnit();
                long timeUnitsPerSecond = timeUnit.convert(1, TimeUnit.SECONDS);
                CompilerToVM c2vm = backend.getRuntime().getCompilerToVM();
                c2vm.notifyCompilationStatistics(id, method, entryBCI != INVOCATION_ENTRY_BCI, (int) processedBytes, time, timeUnitsPerSecond, installedCode);
            }

            if (clearFromCompilationQueue) {
                assert method.isQueuedForCompilation();
                method.clearQueuedForCompilation();
            }
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
        String compilerName = "";
        if (HotSpotCIPrintCompilerName.getValue()) {
            compilerName = "Graal:";
        }
        TTY.println(String.format("%s%7d %4d %c%c%c%c%c       %s %s(%d bytes)", compilerName, (System.currentTimeMillis() - TIMESTAMP_START), id, isOSR ? '%' : ' ', Modifier.isSynchronized(mod) ? 's'
                        : ' ', ' ', ' ', Modifier.isNative(mod) ? 'n' : ' ', MetaUtil.format("%H::%n(%p)", method), isOSR ? "@ " + entryBCI + " " : "", method.getCodeSize()));
    }

    private HotSpotInstalledCode installMethod(final CompilationResult compResult) {
        final HotSpotCodeCacheProvider codeCache = backend.getProviders().getCodeCache();
        HotSpotInstalledCode installedCode = null;
        try (Scope s = Debug.scope("CodeInstall", new DebugDumpScope(String.valueOf(id), true), codeCache, method)) {
            installedCode = codeCache.installMethod(method, compResult);
        } catch (Throwable e) {
            throw Debug.handle(e);
        }
        return installedCode;
    }

    private boolean tryToChangeStatus(CompilationStatus from, CompilationStatus to) {
        return status.compareAndSet(from, to);
    }

    @Override
    public String toString() {
        return "Compilation[id=" + id + ", " + MetaUtil.format("%H.%n(%p)", method) + (entryBCI == StructuredGraph.INVOCATION_ENTRY_BCI ? "" : "@" + entryBCI) + "]";
    }
}
