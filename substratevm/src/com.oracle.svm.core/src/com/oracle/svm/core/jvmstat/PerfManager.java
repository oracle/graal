/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package com.oracle.svm.core.jvmstat;

import static com.oracle.svm.core.jvmstat.PerfManager.Options.PerfDataSamplingInterval;
import static com.oracle.svm.core.option.RuntimeOptionKey.RuntimeOptionKeyFlag.Immutable;

import java.util.ArrayList;

import org.graalvm.collections.EconomicMap;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.type.CLongPointer;

import com.oracle.svm.core.IsolateArgumentParser;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.VMInspectionOptions;
import com.oracle.svm.core.heap.Heap;
import com.oracle.svm.core.jdk.RuntimeSupport;
import com.oracle.svm.core.locks.VMCondition;
import com.oracle.svm.core.locks.VMMutex;
import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.core.option.RuntimeOptionKey;
import com.oracle.svm.core.thread.RecurringCallbackSupport;
import com.oracle.svm.core.util.ImageHeapMap;
import com.oracle.svm.core.util.VMError;

import jdk.graal.compiler.options.Option;
import jdk.graal.compiler.word.Word;

/**
 * Used to create and manage performance data entries.
 */
public class PerfManager {
    private final ArrayList<PerfDataHolder> perfDataHolders;
    private final ArrayList<MutablePerfDataEntry> mutablePerfDataEntries;
    private final EconomicMap<String, PerfLong> longEntries;
    private final PerfDataThread perfDataThread;

    private long startTime;

    @Platforms(Platform.HOSTED_ONLY.class)
    public PerfManager() {
        perfDataHolders = new ArrayList<>();
        mutablePerfDataEntries = new ArrayList<>();
        longEntries = ImageHeapMap.createNonLayeredMap();
        perfDataThread = new PerfDataThread(this);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public void register(PerfDataHolder perfDataHolder) {
        perfDataHolders.add(perfDataHolder);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public PerfLongConstant createLongConstant(String name, PerfUnit unit) {
        PerfLongConstant result = new PerfLongConstant(name, unit);
        longEntries.put(name, result);
        return result;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public PerfLongCounter createLongCounter(String name, PerfUnit unit) {
        PerfLongCounter result = new PerfLongCounter(name, unit);
        mutablePerfDataEntries.add(result);
        longEntries.put(name, result);
        return result;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public PerfLongVariable createLongVariable(String name, PerfUnit unit) {
        PerfLongVariable result = new PerfLongVariable(name, unit);
        mutablePerfDataEntries.add(result);
        longEntries.put(name, result);
        return result;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public PerfStringConstant createStringConstant(String name) {
        return new PerfStringConstant(name);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public PerfStringVariable createStringVariable(String name, int lengthInBytes) {
        PerfStringVariable result = new PerfStringVariable(name, lengthInBytes);
        mutablePerfDataEntries.add(result);
        return result;
    }

    public static boolean usePerfData() {
        int optionIndex = IsolateArgumentParser.getOptionIndex(SubstrateOptions.ConcealedOptions.UsePerfData);
        return VMInspectionOptions.hasJvmstatSupport() && IsolateArgumentParser.singleton().getBooleanOptionValue(optionIndex);
    }

    /** Returns a pointer into the image heap. */
    public CLongPointer getLongPerfEntry(String name) {
        waitForInitialization();
        PerfLong entry = longEntries.get(name);
        assert Heap.getHeap().isInImageHeap(entry);
        return (CLongPointer) Word.objectToUntrackedPointer(entry).add(Word.unsigned(PerfLong.VALUE_OFFSET));
    }

    public boolean hasLongPerfEntry(String name) {
        waitForInitialization();
        PerfLong entry = longEntries.get(name);
        return Heap.getHeap().isInImageHeap(entry);
    }

    public void waitForInitialization() {
        if (!usePerfData() || !perfDataThread.waitForInitialization()) {
            throw new IllegalArgumentException("Performance data support is disabled.");
        }
    }

    private void allocate() {
        for (PerfDataHolder perfDataHolder : perfDataHolders) {
            perfDataHolder.allocate();
        }
    }

    public long elapsedTicks() {
        return System.nanoTime() - startTime;
    }

    public RuntimeSupport.Hook initializationHook() {
        return isFirstIsolate -> {
            if (usePerfData()) {
                startTime = System.nanoTime();
                perfDataThread.start();
            }
        };
    }

    public RuntimeSupport.Hook teardownHook() {
        return isFirstIsolate -> {
            if (usePerfData()) {
                perfDataThread.shutdown();

                PerfMemory memory = ImageSingletons.lookup(PerfMemory.class);
                memory.teardown();
            }
        };
    }

    private static class PerfDataThread extends Thread {
        private static final String ERROR_DURING_INITIALIZATION = "Failed while initializing the performance data.";

        private final PerfManager manager;
        private final VMMutex initializationMutex;
        private final VMCondition initializationCondition;
        private volatile boolean initialized;

        @Platforms(Platform.HOSTED_ONLY.class)
        PerfDataThread(PerfManager manager) {
            super("Performance data");
            this.manager = manager;
            this.initializationMutex = new VMMutex("perfDataInitialization");
            this.initializationCondition = new VMCondition(initializationMutex);
            this.initialized = false;

            setDaemon(true);
        }

        @Override
        public void run() {
            RecurringCallbackSupport.suspendCallbackTimer("Performance data thread must not execute recurring callbacks.");

            initializeMemory();
            try {
                sampleData();
                ImageSingletons.lookup(PerfMemory.class).setAccessible();

                // Publish the data regularly.
                while (true) {
                    Thread.sleep(PerfDataSamplingInterval.getValue());
                    sampleData();
                }
            } catch (InterruptedException e) {
                // The normal way how this thread exits.
            }
        }

        private void initializeMemory() {
            initializationMutex.lock();
            try {
                PerfMemory memory = ImageSingletons.lookup(PerfMemory.class);
                boolean success = memory.initialize();
                VMError.guarantee(success, ERROR_DURING_INITIALIZATION);

                // Allocate the data, write values to the shared memory and mark the shared memory
                // as accessible.
                manager.allocate();

                initialized = true;
                initializationCondition.broadcast();
            } catch (OutOfMemoryError e) {
                /* For now, we can only rethrow the error to terminate the thread (see GR-40601). */
                throw e;
            } catch (Throwable e) {
                throw VMError.shouldNotReachHere(ERROR_DURING_INITIALIZATION, e);
            } finally {
                initializationMutex.unlock();
            }
        }

        public void sampleData() {
            ArrayList<PerfDataHolder> perfDataHolders = manager.perfDataHolders;
            for (int i = 0; i < perfDataHolders.size(); i++) {
                perfDataHolders.get(i).update();
            }

            ArrayList<MutablePerfDataEntry> mutablePerfDataEntries = manager.mutablePerfDataEntries;
            for (int i = 0; i < mutablePerfDataEntries.size(); i++) {
                mutablePerfDataEntries.get(i).publish();
            }
        }

        public boolean waitForInitialization() {
            if (initialized) {
                return true;
            }

            initializationMutex.lock();
            try {
                if (!initialized) {
                    if (!this.isAlive()) {
                        /*
                         * It can happen that the performance data support is enabled but that the
                         * startup hook is not executed for some reason. In that case, we must not
                         * block as this would result in a deadlock otherwise.
                         */
                        return false;
                    }

                    // We use a VMCondition to avoid that anyone can interrupt us while waiting. As
                    // this method is also called from JDK code, this makes it easier as the JDK
                    // code doesn't expect an InterruptedException.
                    initializationCondition.block();
                }
                return true;
            } finally {
                initializationMutex.unlock();
            }
        }

        public void shutdown() {
            /*
             * We disallow interrupting until the initialization is finished. Otherwise, the
             * platform-dependent PerfMemoryProvider may get interrupted and throw some IOException.
             * This is not easy to deal with as we must not call PerfMemoryProvider.teardown()
             * without a clean state as this could have security implications.
             */
            waitForInitialization();
            this.interrupt();
            try {
                this.join();
            } catch (InterruptedException e) {
                throw VMError.shouldNotReachHere("Shutdown hook should not be interrupted");
            }
        }
    }

    public static class Options {
        @Option(help = "Determines if the collected performance data should be written to a memory-mapped file so that it can be accessed by external tools.")//
        public static final HostedOptionKey<Boolean> PerfDataMemoryMappedFile = new HostedOptionKey<>(true);

        @Option(help = "Size of performance data memory region. Will be rounded up to a multiple of the native os page size.")//
        public static final RuntimeOptionKey<Integer> PerfDataMemorySize = new RuntimeOptionKey<>(32 * 1024, Immutable);

        @Option(help = "Jvmstat instrumentation sampling interval (in milliseconds)")//
        public static final RuntimeOptionKey<Integer> PerfDataSamplingInterval = new RuntimeOptionKey<>(200);

        @Option(help = "Maximum PerfStringConstant string length before truncation")//
        public static final RuntimeOptionKey<Integer> PerfMaxStringConstLength = new RuntimeOptionKey<>(1024, Immutable);
    }
}
