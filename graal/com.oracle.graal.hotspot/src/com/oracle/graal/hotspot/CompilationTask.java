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
import static com.oracle.graal.compiler.common.GraalOptions.*;
import static com.oracle.graal.compiler.common.UnsafeAccess.*;
import static com.oracle.graal.hotspot.CompilationQueue.*;
import static com.oracle.graal.hotspot.HotSpotGraalRuntime.*;
import static com.oracle.graal.hotspot.InitTimer.*;
import static com.oracle.graal.nodes.StructuredGraph.*;
import static com.oracle.graal.phases.common.inlining.InliningUtil.*;

import java.io.*;
import java.lang.management.*;
import java.security.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.code.CallingConvention.Type;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.api.runtime.*;
import com.oracle.graal.baseline.*;
import com.oracle.graal.compiler.*;
import com.oracle.graal.compiler.common.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.debug.Debug.Scope;
import com.oracle.graal.debug.internal.*;
import com.oracle.graal.hotspot.CompilationQueue.Options;
import com.oracle.graal.hotspot.bridge.*;
import com.oracle.graal.hotspot.events.*;
import com.oracle.graal.hotspot.events.EventProvider.CompilationEvent;
import com.oracle.graal.hotspot.events.EventProvider.CompilerFailureEvent;
import com.oracle.graal.hotspot.meta.*;
import com.oracle.graal.hotspot.phases.*;
import com.oracle.graal.java.*;
import com.oracle.graal.lir.asm.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.phases.*;
import com.oracle.graal.phases.tiers.*;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

public class CompilationTask implements Runnable, Comparable<Object> {

    static {
        try (InitTimer t = timer("initialize CompilationTask")) {
            // Must be first to ensure any options accessed by the rest of the class
            // initializer are initialized from the command line.
            HotSpotOptions.initialize();
        }
    }

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
        Queued,
        Running,
        Finished
    }

    private final HotSpotBackend backend;
    private final HotSpotResolvedJavaMethod method;
    private final int entryBCI;
    private final int id;
    private final AtomicReference<CompilationStatus> status;

    /**
     * The executor processing the Graal compilation queue this task was placed on. This will be
     * null for blocking compilations or if compilations are scheduled as native HotSpot
     * {@linkplain #ctask CompileTask}s.
     */
    private final ExecutorService executor;

    private StructuredGraph graph;

    /**
     * A long representing the sequence number of this task. Used for sorting the compile queue.
     */
    private long taskId;

    private boolean blocking;

    /**
     * A {@link com.sun.management.ThreadMXBean} to be able to query some information about the
     * current compiler thread, e.g. total allocated bytes.
     */
    private static final com.sun.management.ThreadMXBean threadMXBean = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();

    /**
     * The address of the native CompileTask associated with this compilation. If 0L, then this
     * compilation is being managed by a Graal compilation queue otherwise its managed by a native
     * HotSpot compilation queue.
     */
    private final long ctask;

    public CompilationTask(ExecutorService executor, HotSpotBackend backend, HotSpotResolvedJavaMethod method, int entryBCI, long ctask, boolean blocking) {
        this.executor = executor;
        this.backend = backend;
        this.method = method;
        this.entryBCI = entryBCI;
        if (ctask == 0L) {
            this.id = method.allocateCompileId(entryBCI);
        } else {
            this.id = unsafe.getInt(ctask + backend.getRuntime().getConfig().compileTaskCompileIdOffset);
        }
        this.ctask = ctask;
        this.blocking = blocking;
        this.taskId = uniqueTaskIds.incrementAndGet();
        this.status = new AtomicReference<>(CompilationStatus.Queued);
    }

    public ResolvedJavaMethod getMethod() {
        return method;
    }

    /**
     * Returns the compilation id of this task.
     *
     * @return compile id
     */
    public int getId() {
        return id;
    }

    public int getEntryBCI() {
        return entryBCI;
    }

    @SuppressFBWarnings(value = "NN_NAKED_NOTIFY")
    public void run() {
        withinEnqueue.set(Boolean.FALSE);
        try {
            runCompilation();
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

    public void runCompilation() {
        if (executor != null && executor.isShutdown()) {
            // We don't want to do any unnecessary compilation if the Graal compilation
            // queue has been shutdown. Note that we leave the JVM_ACC_QUEUED bit set
            // for the method so that it won't be re-scheduled for compilation.
            return;
        }

        /*
         * no code must be outside this try/finally because it could happen otherwise that
         * clearQueuedForCompilation() is not executed
         */

        HotSpotVMConfig config = backend.getRuntime().getConfig();
        final long threadId = Thread.currentThread().getId();
        long previousInlinedBytecodes = InlinedBytecodes.getCurrentValue();
        long previousCompilationTime = CompilationTime.getCurrentValue();
        HotSpotInstalledCode installedCode = null;
        final boolean isOSR = entryBCI != StructuredGraph.INVOCATION_ENTRY_BCI;

        // Log a compilation event.
        EventProvider eventProvider = Graal.getRequiredCapability(EventProvider.class);
        CompilationEvent compilationEvent = eventProvider.newCompilationEvent();

        try (TimerCloseable a = CompilationTime.start()) {
            if (!tryToChangeStatus(CompilationStatus.Queued, CompilationStatus.Running)) {
                return;
            }

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
            final long start = System.currentTimeMillis();
            final long allocatedBytesBefore = threadMXBean.getThreadAllocatedBytes(threadId);

            try (Scope s = Debug.scope("Compiling", new DebugDumpScope(String.valueOf(id), true))) {
                // Begin the compilation event.
                compilationEvent.begin();

                if (UseBaselineCompiler.getValue() == true) {
                    HotSpotProviders providers = backend.getProviders();
                    BaselineCompiler baselineCompiler = new BaselineCompiler(GraphBuilderConfiguration.getDefault(), providers.getMetaAccess());
                    OptimisticOptimizations optimisticOpts = OptimisticOptimizations.ALL;
                    result = baselineCompiler.generate(method, -1, backend, new CompilationResult(), method, CompilationResultBuilderFactory.Default, optimisticOpts);
                } else {
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
                }
                result.setId(getId());
                result.setEntryBCI(entryBCI);
            } catch (Throwable e) {
                throw Debug.handle(e);
            } finally {
                // End the compilation event.
                compilationEvent.end();

                filter.remove();
                final boolean printAfterCompilation = PrintAfterCompilation.getValue() && !TTY.isSuppressed();

                if (printAfterCompilation || printCompilation) {
                    final long stop = System.currentTimeMillis();
                    final int targetCodeSize = result != null ? result.getTargetCodeSize() : -1;
                    final long allocatedBytesAfter = threadMXBean.getThreadAllocatedBytes(threadId);
                    final long allocatedBytes = (allocatedBytesAfter - allocatedBytesBefore) / 1024;

                    if (printAfterCompilation) {
                        TTY.println(getMethodDescription() + String.format(" | %4dms %5dB %5dkB", stop - start, targetCodeSize, allocatedBytes));
                    } else if (printCompilation) {
                        TTY.println(String.format("%-6d Graal %-70s %-45s %-50s | %4dms %5dB %5dkB", id, "", "", "", stop - start, targetCodeSize, allocatedBytes));
                    }
                }
            }

            try (TimerCloseable b = CodeInstallationTime.start()) {
                installedCode = (HotSpotInstalledCode) installMethod(result);
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

            // Log a failure event.
            CompilerFailureEvent event = eventProvider.newCompilerFailureEvent();
            if (event.shouldWrite()) {
                event.setCompileId(getId());
                event.setMessage(t.getMessage());
                event.commit();
            }

            if (ExitVMOnException.getValue()) {
                System.exit(-1);
            }
        } finally {
            final int processedBytes = (int) (InlinedBytecodes.getCurrentValue() - previousInlinedBytecodes);

            // Log a compilation event.
            if (compilationEvent.shouldWrite()) {
                compilationEvent.setMethod(MetaUtil.format("%H.%n(%p)", method));
                compilationEvent.setCompileId(getId());
                compilationEvent.setCompileLevel(config.compilationLevelFullOptimization);
                compilationEvent.setSucceeded(true);
                compilationEvent.setIsOsr(isOSR);
                compilationEvent.setCodeSize(installedCode.getSize());
                compilationEvent.setInlinedBytes(processedBytes);
                compilationEvent.commit();
            }

            if (ctask != 0L) {
                unsafe.putInt(ctask + config.compileTaskNumInlinedBytecodesOffset, processedBytes);
            }
            if ((config.ciTime || config.ciTimeEach || Options.PrintCompRate.getValue() != 0) && installedCode != null) {
                long time = CompilationTime.getCurrentValue() - previousCompilationTime;
                TimeUnit timeUnit = CompilationTime.getTimeUnit();
                long timeUnitsPerSecond = timeUnit.convert(1, TimeUnit.SECONDS);
                CompilerToVM c2vm = backend.getRuntime().getCompilerToVM();
                c2vm.notifyCompilationStatistics(id, method, entryBCI != INVOCATION_ENTRY_BCI, processedBytes, time, timeUnitsPerSecond, installedCode);
            }

            if (executor != null) {
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
        String compilerName = "";
        if (HotSpotCIPrintCompilerName.getValue()) {
            compilerName = "Graal:";
        }
        HotSpotVMConfig config = backend.getRuntime().getConfig();
        int compLevel = config.compilationLevelFullOptimization;
        String compLevelString;
        if (config.tieredCompilation) {
            compLevelString = "- ";
            if (compLevel != -1) {
                compLevelString = (char) ('0' + compLevel) + " ";
            }
        } else {
            compLevelString = "";
        }
        boolean hasExceptionHandlers = method.getExceptionHandlers().length > 0;
        TTY.println(String.format("%s%7d %4d %c%c%c%c%c %s      %s %s(%d bytes)", compilerName, backend.getRuntime().compilerToVm.getTimeStamp(), id, isOSR ? '%' : ' ', method.isSynchronized() ? 's'
                        : ' ', hasExceptionHandlers ? '!' : ' ', blocking ? 'b' : ' ', method.isNative() ? 'n' : ' ', compLevelString, MetaUtil.format("%H::%n(%p)", method), isOSR ? "@ " + entryBCI +
                        " " : "", method.getCodeSize()));
    }

    private InstalledCode installMethod(final CompilationResult compResult) {
        final HotSpotCodeCacheProvider codeCache = backend.getProviders().getCodeCache();
        InstalledCode installedCode = null;
        try (Scope s = Debug.scope("CodeInstall", new DebugDumpScope(String.valueOf(id), true), codeCache, method)) {
            installedCode = codeCache.installMethod(method, compResult, ctask);
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

    /**
     * Entry point for the VM to schedule a compilation for a metaspace Method.
     *
     * Called from the VM.
     */
    @SuppressWarnings("unused")
    private static void compileMetaspaceMethod(long metaspaceMethod, final int entryBCI, long ctask, final boolean blocking) {
        final HotSpotResolvedJavaMethod method = HotSpotResolvedJavaMethod.fromMetaspace(metaspaceMethod);
        if (ctask != 0L) {
            // This is on a VM CompilerThread - no user frames exist
            compileMethod(method, entryBCI, ctask, false);
        } else {
            // We have to use a privileged action here because compilations are
            // enqueued from user code which very likely contains unprivileged frames.
            AccessController.doPrivileged(new PrivilegedAction<Void>() {
                public Void run() {
                    compileMethod(method, entryBCI, 0L, blocking);
                    return null;
                }
            });
        }
    }

    /**
     * Compiles a method to machine code.
     */
    static void compileMethod(final HotSpotResolvedJavaMethod method, final int entryBCI, long ctask, final boolean blocking) {
        if (ctask != 0L) {
            HotSpotBackend backend = runtime().getHostBackend();
            CompilationTask task = new CompilationTask(null, backend, method, entryBCI, ctask, false);
            task.runCompilation();
            return;
        }

        if (isWithinEnqueue()) {
            // This is required to avoid deadlocking a compiler thread. The issue is that a
            // java.util.concurrent.BlockingQueue is used to implement the compilation worker
            // queues. If a compiler thread triggers a compilation, then it may be blocked trying
            // to add something to its own queue.
            return;
        }

        // Don't allow blocking compiles from CompilerThreads
        boolean block = blocking && !(Thread.currentThread() instanceof CompilerThread);
        try (Enqueueing enqueueing = new Enqueueing()) {
            if (method.tryToQueueForCompilation()) {
                assert method.isQueuedForCompilation();

                try {
                    CompilationQueue queue = queue();
                    if (!queue.executor.isShutdown()) {
                        HotSpotBackend backend = runtime().getHostBackend();
                        CompilationTask task = new CompilationTask(queue.executor, backend, method, entryBCI, ctask, block);
                        queue.execute(task);
                        if (block) {
                            task.block();
                        }
                    }
                } catch (RejectedExecutionException e) {
                    // The compile queue was already shut down.
                }
            }
        }
    }
}
