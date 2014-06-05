/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.compiler.GraalDebugConfig.*;
import static com.oracle.graal.hotspot.HotSpotGraalRuntime.*;
import static com.oracle.graal.hotspot.CompilationQueue.Options.*;
import static com.oracle.graal.hotspot.InitTimer.*;

import java.lang.reflect.*;
import java.util.concurrent.*;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.*;
import com.oracle.graal.compiler.CompilerThreadFactory.DebugConfigAccess;
import com.oracle.graal.debug.*;
import com.oracle.graal.hotspot.CompilationTask.Enqueueing;
import com.oracle.graal.hotspot.meta.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.options.*;
import com.oracle.graal.printer.*;

/**
 * A queue for running {@link CompilationTask}s on background compilation thread(s). The singleton
 * {@linkplain #queue() instance} is created the first time this class is accessed.
 * <p>
 * Note that this is independent of the HotSpot C++ {@code CompileQueue} used by the
 * {@code CompileBroker}.
 */
public class CompilationQueue {

    static {
        try (InitTimer t = timer("initialize CompilationQueue")) {

            // Must be first to ensure any options accessed by the rest of the class
            // initializer are initialized from the command line.
            HotSpotOptions.initialize();

            // The singleton queue instance is created the first time this class
            queue = new CompilationQueue();
        }
    }

    public static class Options {

        //@formatter:off
        @Option(help = "Print compilation queue activity periodically")
        private static final OptionValue<Boolean> PrintQueue = new OptionValue<>(false);

        @Option(help = "Interval in milliseconds at which to print compilation rate periodically. " +
                       "The compilation statistics are reset after each print out so this option " +
                       "is incompatible with -XX:+CITime and -XX:+CITimeEach.")
        public static final OptionValue<Integer> PrintCompRate = new OptionValue<>(0);

        @Option(help = "Print bootstrap progress and summary")
        static final OptionValue<Boolean> PrintBootstrap = new OptionValue<>(true);

        @Option(help = "Time limit in milliseconds for bootstrap (-1 for no limit)")
        static final OptionValue<Integer> TimedBootstrap = new OptionValue<>(-1);

        @Option(help = "Number of compilation threads to use")
        static final StableOptionValue<Integer> Threads = new StableOptionValue<Integer>() {

            @Override
            public Integer initialValue() {
                return Runtime.getRuntime().availableProcessors();
            }
        };
        //@formatter:on

    }

    /**
     * Prints and resets the Graal compilation rate statistics.
     */
    private static native void printAndResetCompRate();

    /**
     * The singleton queue instance.
     */
    private static final CompilationQueue queue;

    /**
     * Gets the singleton queue instance.
     */
    public static final CompilationQueue queue() {
        assert queue != null;
        return queue;
    }

    /**
     * The thread pool used to service the queue.
     *
     * Most access to the thread pool is wrapped to ensure that
     * {@link CompilationTask#isWithinEnqueue} is in the proper state.
     */
    final ThreadPoolExecutor executor;

    private CompilationQueue() {
        CompilerThreadFactory factory = new CompilerThreadFactory("GraalCompilerThread", new DebugConfigAccess() {
            public GraalDebugConfig getDebugConfig() {
                return Debug.isEnabled() ? DebugEnvironment.initialize(HotSpotGraalRuntime.Options.LogFile.getStream()) : null;
            }
        });

        executor = new ThreadPoolExecutor(Options.Threads.getValue(), Options.Threads.getValue(), 0L, TimeUnit.MILLISECONDS, new PriorityBlockingQueue<Runnable>(), factory);

        // Create queue status printing thread.
        if (Options.PrintQueue.getValue()) {
            Thread t = new Thread() {

                @Override
                public void run() {
                    while (true) {
                        TTY.println(CompilationQueue.this.toString());
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

        if (Options.PrintCompRate.getValue() != 0) {
            Thread t = new Thread() {

                @Override
                public void run() {
                    while (true) {
                        printAndResetCompRate();
                        try {
                            Thread.sleep(Options.PrintCompRate.getValue());
                        } catch (InterruptedException e) {
                        }
                    }
                }
            };
            t.setDaemon(true);
            t.start();
        }
    }

    public long getCompletedTaskCount() {
        try (Enqueueing enqueueing = new Enqueueing()) {
            // Don't allow new enqueues while reading the state of queue.
            return executor.getCompletedTaskCount();
        }
    }

    public long getTaskCount() {
        try (Enqueueing enqueueing = new Enqueueing()) {
            // Don't allow new enqueues while reading the state of queue.
            return executor.getTaskCount();
        }
    }

    public void execute(CompilationTask task) {
        // The caller is expected to have set the within enqueue state.
        assert CompilationTask.isWithinEnqueue();
        executor.execute(task);
    }

    /**
     * Shuts down the compilation queue.
     *
     * Called from VM.
     */
    private static void shutdown() throws Exception {
        queue.executor.shutdownNow();
        if (Debug.isEnabled() && (Dump.getValue() != null || areMetricsOrTimersEnabled())) {
            // Wait up to 2 seconds to flush out all graph dumps and stop metrics/timers
            // being updated.
            queue.executor.awaitTermination(2, TimeUnit.SECONDS);
        }
    }

    /**
     * This method is the first method compiled during {@linkplain #bootstrap() bootstrapping}. Put
     * any code in there that warms up compiler paths that are otherwise not exercised during
     * bootstrapping and lead to later deoptimization when application code is compiled.
     */
    @Deprecated
    private synchronized void compileWarmup() {
        // Method is synchronized to exercise the synchronization code in the compiler.
    }

    /**
     * Adds some compilation tasks to the queue and then loops until the queue has completed all its
     * scheduled tasks or the timeout specified by {@link Options#TimedBootstrap} expires.
     *
     * Called from VM.
     */
    private static void bootstrap() throws Throwable {
        if (PrintBootstrap.getValue()) {
            TTY.print("Bootstrapping Graal");
            TTY.flush();
        }

        long boostrapStartTime = System.currentTimeMillis();

        boolean firstRun = true;
        do {
            // Initialize compile queue with a selected set of methods.
            Class<Object> objectKlass = Object.class;
            if (firstRun) {
                enqueue(CompilationQueue.class.getDeclaredMethod("compileWarmup"));
                enqueue(objectKlass.getDeclaredMethod("equals", Object.class));
                enqueue(objectKlass.getDeclaredMethod("toString"));
                firstRun = false;
            } else {
                for (int i = 0; i < 100; i++) {
                    enqueue(CompilationQueue.class.getDeclaredMethod("bootstrap"));
                }
            }

            // Compile until the queue is empty.
            int z = 0;
            while (true) {
                if (queue().getCompletedTaskCount() >= Math.max(3, queue().getTaskCount())) {
                    break;
                }

                Thread.sleep(100);
                while (z < queue().getCompletedTaskCount() / 100) {
                    ++z;
                    if (PrintBootstrap.getValue()) {
                        TTY.print(".");
                        TTY.flush();
                    }
                }

                // Are we out of time?
                final int timedBootstrap = TimedBootstrap.getValue();
                if (timedBootstrap != -1) {
                    if ((System.currentTimeMillis() - boostrapStartTime) > timedBootstrap) {
                        break;
                    }
                }
            }
        } while ((System.currentTimeMillis() - boostrapStartTime) <= TimedBootstrap.getValue());

        if (ResetDebugValuesAfterBootstrap.getValue()) {
            new DebugValuesPrinter().printDebugValues("bootstrap", true);
            runtime().getCompilerToVM().resetCompilationStatistics();
        }
        phaseTransition("bootstrap");

        if (PrintBootstrap.getValue()) {
            TTY.println(" in %d ms (compiled %d methods)", System.currentTimeMillis() - boostrapStartTime, queue().getCompletedTaskCount());
        }

        System.gc();
        phaseTransition("bootstrap2");
    }

    private static void enqueue(Method m) throws Throwable {
        JavaMethod javaMethod = runtime().getHostProviders().getMetaAccess().lookupJavaMethod(m);
        assert !((HotSpotResolvedJavaMethod) javaMethod).isAbstract() && !((HotSpotResolvedJavaMethod) javaMethod).isNative() : javaMethod;
        CompilationTask.compileMethod((HotSpotResolvedJavaMethod) javaMethod, StructuredGraph.INVOCATION_ENTRY_BCI, 0L, false);
    }

    @Override
    public String toString() {
        return executor.toString();
    }
}
