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

package com.oracle.max.graal.hotspot.bridge;

import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;

import com.oracle.max.cri.ci.*;
import com.oracle.max.cri.ri.*;
import com.oracle.max.criutils.*;
import com.oracle.max.graal.compiler.*;
import com.oracle.max.graal.compiler.phases.*;
import com.oracle.max.graal.compiler.phases.PhasePlan.PhasePosition;
import com.oracle.max.graal.debug.*;
import com.oracle.max.graal.debug.internal.*;
import com.oracle.max.graal.hotspot.*;
import com.oracle.max.graal.hotspot.Compiler;
import com.oracle.max.graal.hotspot.ri.*;
import com.oracle.max.graal.hotspot.server.*;
import com.oracle.max.graal.hotspot.snippets.*;
import com.oracle.max.graal.java.*;
import com.oracle.max.graal.snippets.*;

/**
 * Exits from the HotSpot VM into Java code.
 */
public class VMToCompilerImpl implements VMToCompiler, Remote {

    private final Compiler compiler;
    private int compiledMethodCount;
    private IntrinsifyArrayCopyPhase intrinsifyArrayCopy;

    public final HotSpotTypePrimitive typeBoolean;
    public final HotSpotTypePrimitive typeChar;
    public final HotSpotTypePrimitive typeFloat;
    public final HotSpotTypePrimitive typeDouble;
    public final HotSpotTypePrimitive typeByte;
    public final HotSpotTypePrimitive typeShort;
    public final HotSpotTypePrimitive typeInt;
    public final HotSpotTypePrimitive typeLong;
    public final HotSpotTypePrimitive typeVoid;

    ThreadFactory compilerThreadFactory = new ThreadFactory() {

        @Override
        public Thread newThread(Runnable r) {
            return new CompilerThread(r);
        }
    };

    private final class CompilerThread extends Thread {

        public CompilerThread(Runnable r) {
            super(r);
            this.setName("GraalCompilerThread-" + this.getId());
            this.setDaemon(true);
        }

        @Override
        public void run() {
            if (GraalOptions.Debug) {
                Debug.enable();
                HotSpotDebugConfig hotspotDebugConfig = new HotSpotDebugConfig(GraalOptions.Log, GraalOptions.Meter, GraalOptions.Time, GraalOptions.Dump, GraalOptions.MethodFilter);
                Debug.setConfig(hotspotDebugConfig);
            }
            super.run();
        }
    }

    private ThreadPoolExecutor compileQueue;

    public VMToCompilerImpl(Compiler compiler) {
        this.compiler = compiler;

        typeBoolean = new HotSpotTypePrimitive(compiler, CiKind.Boolean);
        typeChar = new HotSpotTypePrimitive(compiler, CiKind.Char);
        typeFloat = new HotSpotTypePrimitive(compiler, CiKind.Float);
        typeDouble = new HotSpotTypePrimitive(compiler, CiKind.Double);
        typeByte = new HotSpotTypePrimitive(compiler, CiKind.Byte);
        typeShort = new HotSpotTypePrimitive(compiler, CiKind.Short);
        typeInt = new HotSpotTypePrimitive(compiler, CiKind.Int);
        typeLong = new HotSpotTypePrimitive(compiler, CiKind.Long);
        typeVoid = new HotSpotTypePrimitive(compiler, CiKind.Void);
    }

    public void startCompiler() throws Throwable {
        // Make sure TTY is initialized here such that the correct System.out is used for TTY.
        TTY.initialize();

        // Install intrinsics.
        HotSpotRuntime runtime = (HotSpotRuntime) compiler.getCompiler().runtime;
        if (GraalOptions.Intrinsify) {
            this.intrinsifyArrayCopy = new IntrinsifyArrayCopyPhase(runtime);
            GraalIntrinsics.installIntrinsics(runtime, runtime.getCompiler().getTarget(), PhasePlan.DEFAULT);
            Snippets.install(runtime, runtime.getCompiler().getTarget(), new SystemSnippets(), PhasePlan.DEFAULT);
            Snippets.install(runtime, runtime.getCompiler().getTarget(), new UnsafeSnippets(), PhasePlan.DEFAULT);
            Snippets.install(runtime, runtime.getCompiler().getTarget(), new ArrayCopySnippets(), PhasePlan.DEFAULT);
        }

        // Create compilation queue.
        compileQueue = new ThreadPoolExecutor(GraalOptions.Threads, GraalOptions.Threads, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<Runnable>(), compilerThreadFactory);

        // Create queue status printing thread.
        if (GraalOptions.PrintQueue) {
            Thread t = new Thread() {

                @Override
                public void run() {
                    while (true) {
                        TTY.println(compileQueue.toString());
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
     * that are otherwise no exercised during bootstrapping and lead to later deoptimization when application code is
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

        // Initialize compile queue with a selected set of methods.
        Class<Object> objectKlass = Object.class;
        enqueue(getClass().getDeclaredMethod("compileWarmup"));
        enqueue(objectKlass.getDeclaredMethod("equals", Object.class));
        enqueue(objectKlass.getDeclaredMethod("toString"));

        // Compile until the queue is empty.
        int z = 0;
        while (compileQueue.getCompletedTaskCount() < Math.max(3, compileQueue.getTaskCount())) {
            Thread.sleep(100);
            while (z < compileQueue.getCompletedTaskCount() / 100) {
                ++z;
                TTY.print(".");
                TTY.flush();
            }
        }

        TTY.println(" in %d ms", System.currentTimeMillis() - startTime);
        System.gc();
    }

    private void enqueue(Method m) throws Throwable {
        RiMethod riMethod = compiler.getRuntime().getRiMethod(m);
        assert !Modifier.isAbstract(((HotSpotMethodResolved) riMethod).accessFlags()) && !Modifier.isNative(((HotSpotMethodResolved) riMethod).accessFlags()) : riMethod;
        compileMethod((HotSpotMethodResolved) riMethod, 0, false);
    }

    public void shutdownCompiler() throws Throwable {
// compiler.getCompiler().context.print();
        // TODO(tw): Print context results.
        compileQueue.shutdown();

        if (Debug.isEnabled()) {
            List<DebugValueMap> topLevelMaps = DebugValueMap.getTopLevelMaps();
            List<DebugValue> debugValues = KeyRegistry.getDebugValues();
            if (debugValues.size() > 0) {
                for (DebugValueMap map : topLevelMaps) {
                    TTY.println("Showing the results for thread: " + map.getName());
                    map.group();
                    map.normalize();
                    printMap(map, debugValues, 0);
                }
            }
        }
    }

    private void printMap(DebugValueMap map, List<DebugValue> debugValues, int level) {

        printIndent(level);
        TTY.println(map.getName());
        for (DebugValue value : debugValues) {
            long l = map.getCurrentValue(value.getIndex());
            if (l != 0) {
                printIndent(level + 1);
                TTY.println(value.getName() + "=" + l);
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
    public void compileMethod(final HotSpotMethodResolved method, final int entryBCI, boolean blocking) throws Throwable {
        try {
            if (Thread.currentThread() instanceof CompilerThread) {
                if (method.holder().name().contains("java/util/concurrent")) {
                    // This is required to avoid deadlocking a compiler thread. The issue is that a
                    // java.util.concurrent.BlockingQueue is used to implement the compilation worker
                    // queues. If a compiler thread triggers a compilation, then it may be blocked trying
                    // to add something to its own queue.
                    return;
                }
            } else {
                if (GraalOptions.Debug) {
                    Debug.enable();
                    HotSpotDebugConfig hotspotDebugConfig = new HotSpotDebugConfig(GraalOptions.Log, GraalOptions.Meter, GraalOptions.Time, GraalOptions.Dump, GraalOptions.MethodFilter);
                    Debug.setConfig(hotspotDebugConfig);
                }
            }

            Runnable runnable = new Runnable() {

                public void run() {
                    try {
                        PhasePlan plan = getDefaultPhasePlan();
                        GraphBuilderPhase graphBuilderPhase = new GraphBuilderPhase(compiler.getRuntime());
                        plan.addPhase(PhasePosition.AFTER_PARSING, graphBuilderPhase);
                        long startTime = 0;
                        int index = compiledMethodCount++;
                        final boolean printCompilation = GraalOptions.PrintCompilation && !TTY.isSuppressed();
                        if (printCompilation) {
                            TTY.println(String.format("Graal %4d %-70s %-45s %-50s ...", index, method.holder().name(), method.name(), method.signature().asString()));
                            startTime = System.nanoTime();
                        }

                        CiTargetMethod result = null;
                        TTY.Filter filter = new TTY.Filter(GraalOptions.PrintFilter, method);
                        try {
                            result = compiler.getCompiler().compileMethod(method, -1, plan);
                        } finally {
                            filter.remove();
                            if (printCompilation) {
                                long time = (System.nanoTime() - startTime) / 100000;
                                TTY.println(String.format("Graal %4d %-70s %-45s %-50s | %3d.%dms %4dnodes %5dB", index, "", "", "", time / 10, time % 10, 0, (result != null ? result.targetCodeSize()
                                                : -1)));
                            }
                        }
                        compiler.getRuntime().installMethod(method, result);
                    } catch (CiBailout bailout) {
                        if (GraalOptions.ExitVMOnBailout) {
                            bailout.printStackTrace(TTY.cachedOut);
                            System.exit(-1);
                        }
                    } catch (Throwable t) {
                        if (GraalOptions.ExitVMOnException) {
                            t.printStackTrace(TTY.cachedOut);
                            System.exit(-1);
                        }
                    }
                }
            };

            if (blocking) {
                runnable.run();
            } else {
                compileQueue.execute(runnable);
            }
        } catch (RejectedExecutionException e) {
            // The compile queue was already shut down.
            return;
        }
    }

    @Override
    public RiMethod createRiMethodUnresolved(String name, String signature, RiType holder) {
        return new HotSpotMethodUnresolved(compiler, name, signature, holder);
    }

    @Override
    public RiSignature createRiSignature(String signature) {
        return new HotSpotSignature(compiler, signature);
    }

    @Override
    public RiField createRiField(RiType holder, String name, RiType type, int offset, int flags) {
        if (offset != -1) {
            HotSpotTypeResolved resolved = (HotSpotTypeResolved) holder;
            return resolved.createRiField(name, type, offset, flags);
        }
        return new BaseUnresolvedField(holder, name, type);
    }

    @Override
    public RiType createRiType(HotSpotConstantPool pool, String name) {
        throw new RuntimeException("not implemented");
    }

    @Override
    public RiType createRiTypePrimitive(int basicType) {
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
    public RiType createRiTypeUnresolved(String name) {
        return new HotSpotTypeUnresolved(compiler, name);
    }

    @Override
    public CiConstant createCiConstant(CiKind kind, long value) {
        if (kind == CiKind.Long) {
            return CiConstant.forLong(value);
        } else if (kind == CiKind.Int) {
            return CiConstant.forInt((int) value);
        } else if (kind == CiKind.Short) {
            return CiConstant.forShort((short) value);
        } else if (kind == CiKind.Char) {
            return CiConstant.forChar((char) value);
        } else if (kind == CiKind.Byte) {
            return CiConstant.forByte((byte) value);
        } else if (kind == CiKind.Boolean) {
            return (value == 0) ? CiConstant.FALSE : CiConstant.TRUE;
        } else {
            throw new IllegalArgumentException();
        }
    }

    @Override
    public CiConstant createCiConstantFloat(float value) {
        return CiConstant.forFloat(value);
    }

    @Override
    public CiConstant createCiConstantDouble(double value) {
        return CiConstant.forDouble(value);
    }

    @Override
    public CiConstant createCiConstantObject(Object object) {
        return CiConstant.forObject(object);
    }

    private PhasePlan getDefaultPhasePlan() {
        PhasePlan phasePlan = new PhasePlan();
        phasePlan.addPhase(PhasePosition.HIGH_LEVEL, intrinsifyArrayCopy);
        return phasePlan;
    }
}
