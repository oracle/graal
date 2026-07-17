/*
 * Copyright (c) 2020, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.g1;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.jvmstat.PerfDataEntry;
import com.oracle.svm.core.jvmstat.PerfDataHolder;
import com.oracle.svm.core.jvmstat.PerfLongConstant;
import com.oracle.svm.core.jvmstat.PerfLongCounter;
import com.oracle.svm.core.jvmstat.PerfLongVariable;
import com.oracle.svm.core.jvmstat.PerfManager;
import com.oracle.svm.core.jvmstat.PerfStringConstant;
import com.oracle.svm.core.jvmstat.PerfStringVariable;
import com.oracle.svm.core.jvmstat.PerfUnit;

/**
 * Groups all performance data entries supported by G1. The G1 C++ code directly writes new values
 * into the {@link PerfDataEntry} objects that live in the image heap. A sampler thread (see
 * {@link PerfManager}) is used to periodically publish the updated values to the performance data
 * memory.
 *
 * G1 will write values into the {@link PerfDataEntry} objects before the performance data memory is
 * reserved. So, when allocating any mutable performance data entries in the performance data
 * memory, we must not pass a default value as this would destroy the value that G1 wrote earlier.
 *
 * NOTE: this class is tightly coupled with the G1 C++ code, i.e., the C++ code knows pretty much
 * every detail of this class. If anything is changed on the Java-level, the C++ code will need to
 * be adapted as well.
 */
public class G1PerfData implements PerfDataHolder {
    private final G1TLABPerfData tlab;
    private final G1GCPolicyPerfData gcPolicy;
    private final G1GCCausesPerfData gcCauses;
    private final G1CollectorPerfData[] collectors;
    private final G1GenerationPerfData[] generations;
    private final G1AgeTablePerfData ageTable;
    private final G1CpuTimePerfData cpuTime;

    @Platforms(Platform.HOSTED_ONLY.class)
    @SuppressWarnings("this-escape")
    public G1PerfData() {
        PerfManager manager = ImageSingletons.lookup(PerfManager.class);
        manager.register(this);

        tlab = new G1TLABPerfData();
        gcPolicy = new G1GCPolicyPerfData();
        gcCauses = new G1GCCausesPerfData();

        collectors = new G1CollectorPerfData[]{
                        new G1CollectorPerfData(0),
                        new G1CollectorPerfData(1),
                        new G1CollectorPerfData(2),
        };

        G1SpacePerfData[] youngGenSpaces = new G1SpacePerfData[]{
                        new G1SpacePerfData(0, 0),
                        new G1SpacePerfData(0, 1),
                        new G1SpacePerfData(0, 2)
        };

        G1SpacePerfData[] oldGenSpaces = new G1SpacePerfData[]{
                        new G1SpacePerfData(1, 3)
        };

        generations = new G1GenerationPerfData[]{
                        new G1GenerationPerfData(0, youngGenSpaces),
                        new G1GenerationPerfData(1, oldGenSpaces)
        };

        ageTable = new G1AgeTablePerfData(1 << G1Constants.ageBitCount());
        cpuTime = new G1CpuTimePerfData();
    }

    @Override
    public void allocate() {
        tlab.allocate();
        gcPolicy.allocate();
        gcCauses.allocate();

        collectors[0].allocate("G1 young collection pauses");
        collectors[1].allocate("G1 full collection pauses");
        collectors[2].allocate("G1 concurrent cycle pauses");
        assert collectors.length == 3 : collectors.length;

        generations[0].allocate("young");
        generations[0].spaces[0].allocate("eden");
        generations[0].spaces[1].allocate("s0");
        generations[0].spaces[2].allocate("s1");
        assert generations[0].spaces.length == 3 : generations[0].spaces.length;

        generations[1].allocate("old");
        generations[1].spaces[0].allocate("space");
        assert generations[1].spaces.length == 1 : generations[1].spaces.length;
        assert generations.length == 2 : generations.length;

        ageTable.allocate();
        cpuTime.allocate();
    }

    @Override
    public void update() {
        // nothing to do
    }

    public static class G1TLABPerfData {
        private final PerfLongVariable allocThreads;
        private final PerfLongVariable fills;
        private final PerfLongVariable maxFills;
        private final PerfLongVariable alloc;
        private final PerfLongVariable gcWaste;
        private final PerfLongVariable maxGcWaste;
        private final PerfLongVariable refillWaste;
        private final PerfLongVariable maxRefillWaste;
        private final PerfLongVariable slowAlloc;
        private final PerfLongVariable maxSlowAlloc;

        @Platforms(Platform.HOSTED_ONLY.class)
        G1TLABPerfData() {
            PerfManager manager = ImageSingletons.lookup(PerfManager.class);
            allocThreads = manager.createLongVariable("sun.gc.tlab.allocThreads", PerfUnit.NONE);
            fills = manager.createLongVariable("sun.gc.tlab.fills", PerfUnit.NONE);
            maxFills = manager.createLongVariable("sun.gc.tlab.maxFills", PerfUnit.NONE);
            alloc = manager.createLongVariable("sun.gc.tlab.alloc", PerfUnit.BYTES);
            gcWaste = manager.createLongVariable("sun.gc.tlab.gcWaste", PerfUnit.BYTES);
            maxGcWaste = manager.createLongVariable("sun.gc.tlab.maxGcWaste", PerfUnit.BYTES);
            refillWaste = manager.createLongVariable("sun.gc.tlab.refillWaste", PerfUnit.BYTES);
            maxRefillWaste = manager.createLongVariable("sun.gc.tlab.maxRefillWaste", PerfUnit.BYTES);
            slowAlloc = manager.createLongVariable("sun.gc.tlab.slowAlloc", PerfUnit.NONE);
            maxSlowAlloc = manager.createLongVariable("sun.gc.tlab.maxSlowAlloc", PerfUnit.NONE);
        }

        public void allocate() {
            allocThreads.allocate();
            fills.allocate();
            maxFills.allocate();
            alloc.allocate();
            gcWaste.allocate();
            maxGcWaste.allocate();
            refillWaste.allocate();
            maxRefillWaste.allocate();
            slowAlloc.allocate();
            maxSlowAlloc.allocate();
        }
    }

    public static class G1GCPolicyPerfData {
        private final PerfStringConstant name;
        private final PerfLongConstant collectors;
        private final PerfLongConstant generations;

        private final PerfLongVariable desiredSurvivorSize;
        private final PerfLongVariable gcTimeLimitExceeded;
        private final PerfLongVariable maxTenuringThreshold;
        private final PerfLongVariable tenuringThreshold;

        @Platforms(Platform.HOSTED_ONLY.class)
        G1GCPolicyPerfData() {
            PerfManager manager = ImageSingletons.lookup(PerfManager.class);
            name = manager.createStringConstant("sun.gc.policy.name");
            collectors = manager.createLongConstant("sun.gc.policy.collectors", PerfUnit.NONE);
            generations = manager.createLongConstant("sun.gc.policy.generations", PerfUnit.NONE);

            desiredSurvivorSize = manager.createLongVariable("sun.gc.policy.desiredSurvivorSize", PerfUnit.BYTES);
            gcTimeLimitExceeded = manager.createLongVariable("sun.gc.policy.gcTimeLimitExceeded", PerfUnit.EVENTS);
            maxTenuringThreshold = manager.createLongVariable("sun.gc.policy.maxTenuringThreshold", PerfUnit.NONE);
            tenuringThreshold = manager.createLongVariable("sun.gc.policy.tenuringThreshold", PerfUnit.NONE);
        }

        public void allocate() {
            name.allocate("GarbageFirst");
            collectors.allocate(1);
            generations.allocate(2);

            desiredSurvivorSize.allocate();
            gcTimeLimitExceeded.allocate();
            maxTenuringThreshold.allocate();
            tenuringThreshold.allocate();
        }
    }

    public static class G1GCCausesPerfData {
        private final PerfStringVariable cause;
        private final PerfStringVariable lastCause;

        @Platforms(Platform.HOSTED_ONLY.class)
        G1GCCausesPerfData() {
            PerfManager manager = ImageSingletons.lookup(PerfManager.class);
            cause = manager.createStringVariable("sun.gc.cause", 80);
            lastCause = manager.createStringVariable("sun.gc.lastCause", 80);
        }

        public void allocate() {
            cause.allocate();
            lastCause.allocate();
        }
    }

    public static class G1CollectorPerfData {
        private final PerfStringConstant name;

        private final PerfLongCounter invocations;
        private final PerfLongVariable lastEntryTime;
        private final PerfLongVariable lastExitTime;
        private final PerfLongCounter time;

        @Platforms(Platform.HOSTED_ONLY.class)
        G1CollectorPerfData(int index) {
            PerfManager manager = ImageSingletons.lookup(PerfManager.class);
            this.name = manager.createStringConstant("sun.gc.collector." + index + ".name");

            this.invocations = manager.createLongCounter("sun.gc.collector." + index + ".invocations", PerfUnit.EVENTS);
            this.lastEntryTime = manager.createLongVariable("sun.gc.collector." + index + ".lastEntryTime", PerfUnit.TICKS);
            this.lastExitTime = manager.createLongVariable("sun.gc.collector." + index + ".lastExitTime", PerfUnit.TICKS);
            this.time = manager.createLongCounter("sun.gc.collector." + index + ".time", PerfUnit.TICKS);
        }

        public void allocate(String collectorName) {
            name.allocate(collectorName);

            invocations.allocate();
            lastEntryTime.allocate();
            lastExitTime.allocate();
            time.allocate();
        }
    }

    public static class G1SpacePerfData {
        private final PerfStringConstant name;

        private final PerfLongVariable capacity;
        private final PerfLongVariable initCapacity;
        private final PerfLongVariable maxCapacity;
        private final PerfLongVariable used;

        @Platforms(Platform.HOSTED_ONLY.class)
        G1SpacePerfData(int generationIndex, int spaceIndex) {
            PerfManager manager = ImageSingletons.lookup(PerfManager.class);
            name = manager.createStringConstant("sun.gc.generation." + generationIndex + ".space." + spaceIndex + ".name");

            capacity = manager.createLongVariable("sun.gc.generation." + generationIndex + ".space." + spaceIndex + ".capacity", PerfUnit.BYTES);
            initCapacity = manager.createLongVariable("sun.gc.generation." + generationIndex + ".space." + spaceIndex + ".initCapacity", PerfUnit.BYTES);
            maxCapacity = manager.createLongVariable("sun.gc.generation." + generationIndex + ".space." + spaceIndex + ".maxCapacity", PerfUnit.BYTES);
            used = manager.createLongVariable("sun.gc.generation." + generationIndex + ".space." + spaceIndex + ".used", PerfUnit.BYTES);
        }

        public void allocate(String spaceName) {
            name.allocate(spaceName);

            capacity.allocate();
            initCapacity.allocate();
            maxCapacity.allocate();
            used.allocate();
        }
    }

    public static class G1GenerationPerfData {
        private final PerfStringConstant name;
        private final PerfLongConstant numSpaces;

        private final PerfLongVariable capacity;
        private final PerfLongVariable maxCapacity;
        private final PerfLongVariable minCapacity;
        private final G1SpacePerfData[] spaces;

        @Platforms(Platform.HOSTED_ONLY.class)
        G1GenerationPerfData(int generationIndex, G1SpacePerfData[] spaces) {
            PerfManager manager = ImageSingletons.lookup(PerfManager.class);
            this.name = manager.createStringConstant("sun.gc.generation." + generationIndex + ".name");
            this.numSpaces = manager.createLongConstant("sun.gc.generation." + generationIndex + ".spaces", PerfUnit.NONE);

            this.capacity = manager.createLongVariable("sun.gc.generation." + generationIndex + ".capacity", PerfUnit.BYTES);
            this.maxCapacity = manager.createLongVariable("sun.gc.generation." + generationIndex + ".maxCapacity", PerfUnit.BYTES);
            this.minCapacity = manager.createLongVariable("sun.gc.generation." + generationIndex + ".minCapacity", PerfUnit.BYTES);
            this.spaces = spaces;
        }

        public void allocate(String generationName) {
            name.allocate(generationName);
            numSpaces.allocate(spaces.length);

            capacity.allocate();
            maxCapacity.allocate();
            minCapacity.allocate();
        }
    }

    public static class G1AgeTablePerfData {
        private final PerfLongConstant size;

        private final PerfLongVariable[] table;

        @Platforms(Platform.HOSTED_ONLY.class)
        G1AgeTablePerfData(int length) {
            PerfManager manager = ImageSingletons.lookup(PerfManager.class);
            size = manager.createLongConstant("sun.gc.generation.0.agetable.size", PerfUnit.NONE);

            table = new PerfLongVariable[length];
            for (int i = 0; i < length; i++) {
                table[i] = manager.createLongVariable("sun.gc.generation.0.agetable.bytes." + String.format("%02d", i), PerfUnit.BYTES);
            }
        }

        public void allocate() {
            size.allocate(table.length);

            for (PerfLongVariable v : table) {
                v.allocate();
            }
        }
    }

    public static class G1CpuTimePerfData {
        private final PerfLongCounter gcTotal;
        private final PerfLongCounter gcParallelWorkers;
        private final PerfLongCounter gcConcMark;
        private final PerfLongCounter gcConcRefine;
        private final PerfLongCounter gcService;

        @Platforms(Platform.HOSTED_ONLY.class)
        public G1CpuTimePerfData() {
            PerfManager manager = ImageSingletons.lookup(PerfManager.class);
            this.gcTotal = manager.createLongCounter("sun.threads.total_gc_cpu_time", PerfUnit.TICKS);
            this.gcParallelWorkers = manager.createLongCounter("sun.threads.cpu_time.gc_parallel_workers", PerfUnit.TICKS);
            this.gcConcMark = manager.createLongCounter("sun.threads.cpu_time.gc_conc_mark", PerfUnit.TICKS);
            this.gcConcRefine = manager.createLongCounter("sun.threads.cpu_time.gc_conc_refine", PerfUnit.TICKS);
            this.gcService = manager.createLongCounter("sun.threads.cpu_time.gc_service", PerfUnit.TICKS);
        }

        public void allocate() {
            gcTotal.allocate();
            gcParallelWorkers.allocate();
            gcConcMark.allocate();
            gcConcRefine.allocate();
            gcService.allocate();
        }
    }
}
