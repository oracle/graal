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
import static com.oracle.graal.compiler.GraalCompiler.*;
import static com.oracle.graal.hotspot.bridge.VMToCompilerImpl.*;
import static com.oracle.graal.nodes.StructuredGraph.*;
import static com.oracle.graal.phases.GraalOptions.*;
import static com.oracle.graal.phases.common.InliningUtil.*;

import java.lang.reflect.*;
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

public class CompilationTask implements Runnable {

    public static final ThreadLocal<Boolean> withinEnqueue = new ThreadLocal<Boolean>() {

        @Override
        protected Boolean initialValue() {
            return Boolean.valueOf(Thread.currentThread() instanceof CompilerThread);
        }
    };

    private enum CompilationStatus {
        Queued, Running
    }

    private final HotSpotBackend backend;
    private final HotSpotResolvedJavaMethod method;
    private final int entryBCI;
    private final int id;
    private final AtomicReference<CompilationStatus> status;

    private StructuredGraph graph;

    public CompilationTask(HotSpotBackend backend, HotSpotResolvedJavaMethod method, int entryBCI, int id) {
        assert id >= 0;
        this.backend = backend;
        this.method = method;
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
            runCompilation(true);
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
            try (Scope s = Debug.scope("Compiling", new DebugDumpScope(String.valueOf(id), true))) {
                GraphCache graphCache = backend.getRuntime().getGraphCache();
                if (graphCache != null) {
                    graphCache.removeStaleGraphs();
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
                Suites suites = getSuites(providers);
                ProfilingInfo profilingInfo = getProfilingInfo();
                OptimisticOptimizations optimisticOpts = getOptimisticOpts(profilingInfo);
                result = compileGraph(graph, cc, method, providers, backend, backend.getTarget(), graphCache, getGraphBuilderSuite(providers), optimisticOpts, profilingInfo,
                                method.getSpeculationLog(), suites, true, new CompilationResult(), CompilationResultBuilderFactory.Default);
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
        TTY.println(String.format("%7d %4d %c%c%c%c%c       %s %s(%d bytes)", 0, id, isOSR ? '%' : ' ', Modifier.isSynchronized(mod) ? 's' : ' ', ' ', ' ', Modifier.isNative(mod) ? 'n' : ' ',
                        MetaUtil.format("%H::%n(%p)", method), isOSR ? "@ " + entryBCI + " " : "", method.getCodeSize()));
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
