/*
 * Copyright (c) 2011, 2012, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.graal.hotspot.bridge;

import static com.oracle.graal.graph.FieldIntrospection.*;

import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.debug.internal.*;
import com.oracle.graal.hotspot.*;
import com.oracle.graal.hotspot.meta.*;
import com.oracle.graal.hotspot.phases.*;
import com.oracle.graal.java.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.phases.*;
import com.oracle.graal.phases.PhasePlan.PhasePosition;
import com.oracle.graal.snippets.*;

/**
 * Exits from the HotSpot VM into Java code.
 */
public class VMToCompilerImpl implements VMToCompiler {

    private final HotSpotGraalRuntime graalRuntime;

    public final HotSpotTypePrimitive typeBoolean;
    public final HotSpotTypePrimitive typeChar;
    public final HotSpotTypePrimitive typeFloat;
    public final HotSpotTypePrimitive typeDouble;
    public final HotSpotTypePrimitive typeByte;
    public final HotSpotTypePrimitive typeShort;
    public final HotSpotTypePrimitive typeInt;
    public final HotSpotTypePrimitive typeLong;
    public final HotSpotTypePrimitive typeVoid;

    private ThreadPoolExecutor compileQueue;
    private ThreadPoolExecutor slowCompileQueue;
    private AtomicInteger compileTaskIds = new AtomicInteger();

    private volatile boolean bootstrapRunning;

    private PrintStream log = System.out;

    public VMToCompilerImpl(HotSpotGraalRuntime compiler) {
        this.graalRuntime = compiler;

        typeBoolean = new HotSpotTypePrimitive(Kind.Boolean);
        typeChar = new HotSpotTypePrimitive(Kind.Char);
        typeFloat = new HotSpotTypePrimitive(Kind.Float);
        typeDouble = new HotSpotTypePrimitive(Kind.Double);
        typeByte = new HotSpotTypePrimitive(Kind.Byte);
        typeShort = new HotSpotTypePrimitive(Kind.Short);
        typeInt = new HotSpotTypePrimitive(Kind.Int);
        typeLong = new HotSpotTypePrimitive(Kind.Long);
        typeVoid = new HotSpotTypePrimitive(Kind.Void);
    }

    private static void initMirror(HotSpotTypePrimitive type, long offset) {
        Class<?> mirror = type.mirror();
        unsafe.putObject(mirror, offset, type);
        assert unsafe.getObject(mirror, offset) == type;
    }


    public void startCompiler() throws Throwable {

        long offset = HotSpotGraalRuntime.getInstance().getConfig().graalMirrorInClassOffset;
        initMirror(typeBoolean, offset);
        initMirror(typeChar, offset);
        initMirror(typeFloat, offset);
        initMirror(typeDouble, offset);
        initMirror(typeByte, offset);
        initMirror(typeShort, offset);
        initMirror(typeInt, offset);
        initMirror(typeLong, offset);
        initMirror(typeVoid, offset);

        if (GraalOptions.LogFile != null) {
            try {
                final boolean enableAutoflush = true;
                log = new PrintStream(new FileOutputStream(GraalOptions.LogFile), enableAutoflush);
            } catch (FileNotFoundException e) {
                throw new RuntimeException("couldn't open log file: " + GraalOptions.LogFile, e);
            }
        }

        TTY.initialize(log);

        if (GraalOptions.Log == null && GraalOptions.Meter == null && GraalOptions.Time == null && GraalOptions.Dump == null) {
            if (GraalOptions.MethodFilter != null) {
                TTY.println("WARNING: Ignoring MethodFilter option since Log, Meter, Time and Dump options are all null");
            }
        }

        if (GraalOptions.Debug) {
            Debug.enable();
            HotSpotDebugConfig hotspotDebugConfig = new HotSpotDebugConfig(GraalOptions.Log, GraalOptions.Meter, GraalOptions.Time, GraalOptions.Dump, GraalOptions.MethodFilter, log);
            Debug.setConfig(hotspotDebugConfig);
        }
        // Install intrinsics.
        GraalCompiler compiler = graalRuntime.getCompiler();
        final HotSpotRuntime runtime = (HotSpotRuntime) compiler.runtime;
        if (GraalOptions.Intrinsify) {
            Debug.scope("InstallSnippets", new Object[] {new DebugDumpScope("InstallSnippets"), compiler}, new Runnable() {

                @Override
                public void run() {
                    Assumptions assumptions = new Assumptions(GraalOptions.OptAssumptions);
                    SnippetInstaller installer = new SnippetInstaller(runtime, assumptions, runtime.getGraalRuntime().getTarget());
                    GraalIntrinsics.installIntrinsics(installer);
                    runtime.installSnippets(installer, assumptions);
                }
            });

        }

        // Create compilation queue.
        BlockingQueue<Runnable> queue = GraalOptions.PriorityCompileQueue ? new PriorityBlockingQueue<Runnable>() : new LinkedBlockingQueue<Runnable>();
        compileQueue = new ThreadPoolExecutor(GraalOptions.Threads, GraalOptions.Threads, 0L, TimeUnit.MILLISECONDS, queue, CompilerThread.FACTORY);

        if (GraalOptions.SlowCompileThreads) {
            BlockingQueue<Runnable> slowQueue = GraalOptions.PriorityCompileQueue ? new PriorityBlockingQueue<Runnable>() : new LinkedBlockingQueue<Runnable>();
            slowCompileQueue = new ThreadPoolExecutor(GraalOptions.Threads, GraalOptions.Threads, 0L, TimeUnit.MILLISECONDS, slowQueue, CompilerThread.LOW_PRIORITY_FACTORY);
        }

        // Create queue status printing thread.
        if (GraalOptions.PrintQueue) {
            Thread t = new Thread() {

                @Override
                public void run() {
                    while (true) {
                        if (slowCompileQueue == null) {
                            TTY.println(compileQueue.toString());
                        } else {
                            TTY.println("fast: " + compileQueue.toString() + " slow: " + slowCompileQueue);
                        }
                        try {
                            Thread.sleep(1000);
                        } catch (InterruptedException e) {
                        }
                    }
                }
            };
            t.setDaemon(true);
            t.start();
        }
    }

    /**
     * This method is the first method compiled during bootstrapping. Put any code in there that warms up compiler paths
     * that are otherwise not exercised during bootstrapping and lead to later deoptimization when application code is
     * compiled.
     */
    @SuppressWarnings("unused")
    @Deprecated
    private synchronized void compileWarmup() {
        // Method is synchronized to exercise the synchronization code in the compiler.
    }

    public void bootstrap() throws Throwable {
        TTY.print("Bootstrapping Graal");
        TTY.flush();
        long startTime = System.currentTimeMillis();

        bootstrapRunning = true;
        boolean firstRun = true;
        do {
            // Initialize compile queue with a selected set of methods.
            Class<Object> objectKlass = Object.class;
            if (firstRun) {
                enqueue(getClass().getDeclaredMethod("compileWarmup"));
                enqueue(objectKlass.getDeclaredMethod("equals", Object.class));
                enqueue(objectKlass.getDeclaredMethod("toString"));
                firstRun = false;
            } else {
                for (int i = 0; i < 100; i++) {
                    enqueue(getClass().getDeclaredMethod("bootstrap"));
                }
            }

            // Compile until the queue is empty.
            int z = 0;
            while (true) {
                try {
                    assert !CompilationTask.withinEnqueue.get();
                    CompilationTask.withinEnqueue.set(Boolean.TRUE);
                    if (slowCompileQueue == null) {
                        if (compileQueue.getCompletedTaskCount() >= Math.max(3, compileQueue.getTaskCount())) {
                            break;
                        }
                    } else {
                        if (compileQueue.getCompletedTaskCount() + slowCompileQueue.getCompletedTaskCount() >= Math.max(3, compileQueue.getTaskCount() + slowCompileQueue.getTaskCount())) {
                            break;
                        }
                    }
                } finally {
                    CompilationTask.withinEnqueue.set(Boolean.FALSE);
                }

                Thread.sleep(100);
                while (z < compileQueue.getCompletedTaskCount() / 100) {
                    ++z;
                    TTY.print(".");
                    TTY.flush();
                }
            }
        } while ((System.currentTimeMillis() - startTime) <= GraalOptions.TimedBootstrap);
        CompilationStatistics.clear("bootstrap");
        bootstrapRunning = false;

        TTY.println(" in %d ms", System.currentTimeMillis() - startTime);
        if (graalRuntime.getCache() != null) {
            graalRuntime.getCache().clear();
        }
        System.gc();
        CompilationStatistics.clear("bootstrap2");
    }

    private void enqueue(Method m) throws Throwable {
        JavaMethod javaMethod = graalRuntime.getRuntime().lookupJavaMethod(m);
        assert !Modifier.isAbstract(((HotSpotResolvedJavaMethod) javaMethod).getModifiers()) && !Modifier.isNative(((HotSpotResolvedJavaMethod) javaMethod).getModifiers()) : javaMethod;
        compileMethod((HotSpotResolvedJavaMethod) javaMethod, StructuredGraph.INVOCATION_ENTRY_BCI, false, 10);
    }

    private static void shutdownCompileQueue(ThreadPoolExecutor queue) throws InterruptedException {
        if (queue != null) {
            queue.shutdown();
            if (Debug.isEnabled() && GraalOptions.Dump != null) {
                // Wait 2 seconds to flush out all graph dumps that may be of interest
                queue.awaitTermination(2, TimeUnit.SECONDS);
            }
        }
    }

    public void shutdownCompiler() throws Throwable {
        try {
            assert !CompilationTask.withinEnqueue.get();
            CompilationTask.withinEnqueue.set(Boolean.TRUE);
            shutdownCompileQueue(compileQueue);
            shutdownCompileQueue(slowCompileQueue);
        } finally {
            CompilationTask.withinEnqueue.set(Boolean.FALSE);
        }


        if (Debug.isEnabled()) {
            List<DebugValueMap> topLevelMaps = DebugValueMap.getTopLevelMaps();
            List<DebugValue> debugValues = KeyRegistry.getDebugValues();
            if (debugValues.size() > 0) {
                ArrayList<DebugValue> sortedValues = new ArrayList<>(debugValues);
                Collections.sort(sortedValues, DebugValue.ORDER_BY_NAME);

                if (GraalOptions.SummarizeDebugValues) {
                    printSummary(topLevelMaps, sortedValues);
                } else if (GraalOptions.PerThreadDebugValues) {
                    for (DebugValueMap map : topLevelMaps) {
                        TTY.println("Showing the results for thread: " + map.getName());
                        map.group();
                        map.normalize();
                        printMap(map, sortedValues, 0);
                    }
                } else {
                    DebugValueMap globalMap = new DebugValueMap("Global");
                    for (DebugValueMap map : topLevelMaps) {
                        if (GraalOptions.SummarizePerPhase) {
                            flattenChildren(map, globalMap);
                        } else {
                            for (DebugValueMap child : map.getChildren()) {
                                globalMap.addChild(child);
                            }
                        }
                    }
                    if (!GraalOptions.SummarizePerPhase) {
                        globalMap.group();
                    }
                    globalMap.normalize();
                    printMap(globalMap, sortedValues, 0);
                }
            }
        }
        CompilationStatistics.clear("final");
        SnippetCounter.printGroups(TTY.out().out());
    }

    private void flattenChildren(DebugValueMap map, DebugValueMap globalMap) {
        globalMap.addChild(map);
        for (DebugValueMap child : map.getChildren()) {
            flattenChildren(child, globalMap);
        }
        map.clearChildren();
    }

    private static void printSummary(List<DebugValueMap> topLevelMaps, List<DebugValue> debugValues) {
        DebugValueMap result = new DebugValueMap("Summary");
        for (int i = debugValues.size() - 1; i >= 0; i--) {
            DebugValue debugValue = debugValues.get(i);
            int index = debugValue.getIndex();
            long total = collectTotal(topLevelMaps, index);
            result.setCurrentValue(index, total);
        }
        printMap(result, debugValues, 0);
    }

    private static long collectTotal(List<DebugValueMap> maps, int index) {
        long total = 0;
        for (int i = 0; i < maps.size(); i++) {
            DebugValueMap map = maps.get(i);
            // the top level accumulates some counters -> do not process the children if we find a value
            long value = map.getCurrentValue(index);
            if (value == 0) {
                total += collectTotal(map.getChildren(), index);
            } else {
                total += value;
            }
        }
        return total;
    }

    private static void printMap(DebugValueMap map, List<DebugValue> debugValues, int level) {

        printIndent(level);
        TTY.println("%s", map.getName());

        for (DebugValue value : debugValues) {
            long l = map.getCurrentValue(value.getIndex());
            if (l != 0) {
                printIndent(level + 1);
                TTY.println(value.getName() + "=" + value.toString(l));
            }
        }

        for (DebugValueMap child : map.getChildren()) {
            printMap(child, debugValues, level + 1);
        }
    }

    private static void printIndent(int level) {
        for (int i = 0; i < level; ++i) {
            TTY.print("    ");
        }
        TTY.print("|-> ");
    }

    @Override
    public boolean compileMethod(long metaspaceMethod, final HotSpotResolvedJavaType holder, final int entryBCI, boolean blocking, int priority) throws Throwable {
        HotSpotResolvedJavaMethod method = holder.createMethod(metaspaceMethod);
        return compileMethod(method, entryBCI, blocking, priority);
    }

    public boolean compileMethod(final HotSpotResolvedJavaMethod method, final int entryBCI, boolean blocking, int priority) throws Throwable {
        boolean osrCompilation = entryBCI != StructuredGraph.INVOCATION_ENTRY_BCI;
        if (osrCompilation && bootstrapRunning) {
            // no OSR compilations during bootstrap - the compiler is just too slow at this point, and we know that there are no endless loops
            return true;
        }

        if (CompilationTask.withinEnqueue.get()) {
            // This is required to avoid deadlocking a compiler thread. The issue is that a
            // java.util.concurrent.BlockingQueue is used to implement the compilation worker
            // queues. If a compiler thread triggers a compilation, then it may be blocked trying
            // to add something to its own queue.
            return false;
        }
        CompilationTask.withinEnqueue.set(Boolean.TRUE);

        try {
            CompilationTask current = method.currentTask();
            if (!blocking && current != null) {
                if (current.isInProgress()) {
                    if (current.getEntryBCI() == entryBCI) {
                        // a compilation with the correct bci is already in progress, so just return true
                        return true;
                    }
                } else {
                    if (GraalOptions.PriorityCompileQueue) {
                        // normally compilation tasks will only be re-queued when they get a priority boost, so cancel the old task and add a new one
                        current.cancel();
                    } else {
                        // without a prioritizing compile queue it makes no sense to re-queue the compilation task
                        return true;
                    }
                }
            }

            final OptimisticOptimizations optimisticOpts = new OptimisticOptimizations(method);
            int id = compileTaskIds.incrementAndGet();
            // OSR compilations need to be finished quickly, so they get max priority
            int queuePriority = osrCompilation ? -1 : priority;
            CompilationTask task = CompilationTask.create(graalRuntime, createPhasePlan(optimisticOpts, osrCompilation), optimisticOpts, method, entryBCI, id, queuePriority);
            if (blocking) {
                task.runCompilation();
            } else {
                try {
                    method.setCurrentTask(task);
                    if (GraalOptions.SlowCompileThreads && priority > GraalOptions.SlowQueueCutoff) {
                        slowCompileQueue.execute(task);
                    } else {
                        compileQueue.execute(task);
                    }
                } catch (RejectedExecutionException e) {
                    // The compile queue was already shut down.
                    return false;
                }
            }
            return true;
        } finally {
            CompilationTask.withinEnqueue.set(Boolean.FALSE);
        }
    }

    @Override
    public JavaMethod createUnresolvedJavaMethod(String name, String signature, JavaType holder) {
        return new HotSpotMethodUnresolved(name, signature, holder);
    }

    @Override
    public JavaField createJavaField(JavaType holder, String name, JavaType type, int offset, int flags, boolean internal) {
        if (offset != -1) {
            HotSpotResolvedJavaType resolved = (HotSpotResolvedJavaType) holder;
            return resolved.createField(name, type, offset, flags, internal);
        }
        return new HotSpotUnresolvedField(holder, name, type);
    }

    @Override
    public ResolvedJavaMethod createResolvedJavaMethod(JavaType holder, long metaspaceMethod) {
        HotSpotResolvedJavaType type = (HotSpotResolvedJavaType) holder;
        return type.createMethod(metaspaceMethod);
    }

    @Override
    public ResolvedJavaType createPrimitiveJavaType(int basicType) {
        switch (basicType) {
            case 4:
                return typeBoolean;
            case 5:
                return typeChar;
            case 6:
                return typeFloat;
            case 7:
                return typeDouble;
            case 8:
                return typeByte;
            case 9:
                return typeShort;
            case 10:
                return typeInt;
            case 11:
                return typeLong;
            case 14:
                return typeVoid;
            default:
                throw new IllegalArgumentException("Unknown basic type: " + basicType);
        }
    }

    @Override
    public HotSpotTypeUnresolved createUnresolvedJavaType(String name) {
        int dims = 0;
        int startIndex = 0;
        while (name.charAt(startIndex) == '[') {
            startIndex++;
            dims++;
        }

        // Decode name if necessary.
        if (name.charAt(name.length() - 1) == ';') {
            assert name.charAt(startIndex) == 'L';
            return new HotSpotTypeUnresolved(name, name.substring(startIndex + 1, name.length() - 1), dims);
        } else {
            return new HotSpotTypeUnresolved(HotSpotTypeUnresolved.getFullName(name, dims), name, dims);
        }
    }

    @Override
    public HotSpotResolvedJavaType createResolvedJavaType(long metaspaceKlass,
                    String name,
                    String simpleName,
                    Class javaMirror,
                    boolean hasFinalizableSubclass,
                    int sizeOrSpecies) {
        HotSpotResolvedJavaType type = new HotSpotResolvedJavaType(
                                        metaspaceKlass,
                                        name,
                                        simpleName,
                                        javaMirror,
                                        hasFinalizableSubclass,
                                        sizeOrSpecies);

        long offset = HotSpotGraalRuntime.getInstance().getConfig().graalMirrorInClassOffset;
        if (!unsafe.compareAndSwapObject(javaMirror, offset, null, type)) {
            // lost the race - return the existing value instead
            type = (HotSpotResolvedJavaType) unsafe.getObject(javaMirror, offset);
        }
        return type;
    }

    @Override
    public Constant createConstant(Kind kind, long value) {
        if (kind == Kind.Long) {
            return Constant.forLong(value);
        } else if (kind == Kind.Int) {
            return Constant.forInt((int) value);
        } else if (kind == Kind.Short) {
            return Constant.forShort((short) value);
        } else if (kind == Kind.Char) {
            return Constant.forChar((char) value);
        } else if (kind == Kind.Byte) {
            return Constant.forByte((byte) value);
        } else if (kind == Kind.Boolean) {
            return (value == 0) ? Constant.FALSE : Constant.TRUE;
        } else {
            throw new IllegalArgumentException();
        }
    }

    @Override
    public Constant createConstantFloat(float value) {
        return Constant.forFloat(value);
    }

    @Override
    public Constant createConstantDouble(double value) {
        return Constant.forDouble(value);
    }

    @Override
    public Constant createConstantObject(Object object) {
        return Constant.forObject(object);
    }

    public PhasePlan createPhasePlan(OptimisticOptimizations optimisticOpts, boolean onStackReplacement) {
        PhasePlan phasePlan = new PhasePlan();
        phasePlan.addPhase(PhasePosition.AFTER_PARSING, new GraphBuilderPhase(graalRuntime.getRuntime(), GraphBuilderConfiguration.getDefault(), optimisticOpts));
        if (onStackReplacement) {
            phasePlan.addPhase(PhasePosition.AFTER_PARSING, new OnStackReplacementPhase());
        }
        return phasePlan;
    }

    @Override
    public PrintStream log() {
        return log;
    }
}
