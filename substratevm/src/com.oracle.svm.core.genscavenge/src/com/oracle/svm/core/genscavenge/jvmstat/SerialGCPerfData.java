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
package com.oracle.svm.core.genscavenge.jvmstat;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.genscavenge.CollectionPolicy;
import com.oracle.svm.core.genscavenge.GCAccounting;
import com.oracle.svm.core.genscavenge.GCImpl;
import com.oracle.svm.core.genscavenge.HeapAccounting;
import com.oracle.svm.core.genscavenge.HeapImpl;
import com.oracle.svm.core.genscavenge.HeapParameters;
import com.oracle.svm.core.jvmstat.PerfDataHolder;
import com.oracle.svm.core.jvmstat.PerfLongConstant;
import com.oracle.svm.core.jvmstat.PerfLongCounter;
import com.oracle.svm.core.jvmstat.PerfLongVariable;
import com.oracle.svm.core.jvmstat.PerfManager;
import com.oracle.svm.core.jvmstat.PerfStringConstant;
import com.oracle.svm.core.jvmstat.PerfUnit;

/**
 * Performance data for our serial GC.
 */
public class SerialGCPerfData implements PerfDataHolder {
    private final PerfDataGCPolicy gcPolicy;
    private final PerfDataCollector youngCollector;
    private final PerfDataCollector oldCollector;
    private final PerfDataGeneration youngGen;
    private final PerfDataGeneration oldGen;

    @Platforms(Platform.HOSTED_ONLY.class)
    public SerialGCPerfData() {
        gcPolicy = new PerfDataGCPolicy();

        youngCollector = new PerfDataCollector(0);
        oldCollector = new PerfDataCollector(1);

        int youngSpaceCount = 1 + HeapParameters.getMaxSurvivorSpaces();
        SpacePerfData[] youngGenSpaces = new SpacePerfData[youngSpaceCount];
        for (int i = 0; i < youngSpaceCount; i++) {
            youngGenSpaces[i] = new SpacePerfData(0, i);
        }
        youngGen = new PerfDataGeneration(0, youngGenSpaces);

        int oldSpaceIndex = youngSpaceCount;
        SpacePerfData[] oldGenSpaces = new SpacePerfData[]{
                        new SpacePerfData(1, oldSpaceIndex)
        };
        oldGen = new PerfDataGeneration(1, oldGenSpaces);
    }

    @Override
    public void allocate() {
        gcPolicy.allocate();

        youngCollector.allocate("Serial young collection pauses");
        oldCollector.allocate("Serial full collection pauses");

        youngGen.allocate("young");
        youngGen.spaces[0].allocate("eden");
        for (int i = 1; i < youngGen.spaces.length; i++) {
            youngGen.spaces[i].allocate("s" + (i - 1));
        }

        oldGen.allocate("old");
        oldGen.spaces[0].allocate("old");
        assert oldGen.spaces.length == 1;
    }

    @Override
    public void update() {
        GCAccounting accounting = GCImpl.getGCImpl().getAccounting();
        HeapAccounting heapAccounting = HeapImpl.getHeapImpl().getAccounting();
        CollectionPolicy policy = GCImpl.getPolicy();
        policy.ensureSizeParametersInitialized();

        long maxNewSize = policy.getMaximumYoungGenerationSize().rawValue();
        youngCollector.invocations.setValue(accounting.getIncrementalCollectionCount());
        youngCollector.time.setValue(accounting.getIncrementalCollectionTotalNanos());

        youngGen.capacity.setValue(policy.getYoungGenerationCapacity().rawValue());
        youngGen.maxCapacity.setValue(maxNewSize);

        youngGen.spaces[0].used.setValue(heapAccounting.getEdenUsedBytes().rawValue());
        for (int i = 1; i < youngGen.spaces.length; i++) {
            youngGen.spaces[i].used.setValue(heapAccounting.getSurvivorSpaceAfterChunkBytes(i - 1).rawValue());
        }

        long maxOldSize = policy.getMaximumHeapSize().rawValue() - maxNewSize;
        oldCollector.invocations.setValue(accounting.getCompleteCollectionCount());
        oldCollector.time.setValue(accounting.getCompleteCollectionTotalNanos());

        oldGen.capacity.setValue(policy.getOldGenerationCapacity().rawValue());
        oldGen.maxCapacity.setValue(maxOldSize);

        oldGen.spaces[0].used.setValue(accounting.getOldGenerationAfterChunkBytes().rawValue());
    }

    private static class PerfDataGCPolicy {
        private final PerfLongConstant collectors;
        private final PerfLongConstant generations;
        private final PerfStringConstant name;

        @Platforms(Platform.HOSTED_ONLY.class)
        PerfDataGCPolicy() {
            PerfManager manager = ImageSingletons.lookup(PerfManager.class);
            collectors = manager.createLongConstant("sun.gc.policy.collectors", PerfUnit.NONE);
            generations = manager.createLongConstant("sun.gc.policy.generations", PerfUnit.NONE);
            name = manager.createStringConstant("sun.gc.policy.name");
        }

        public void allocate() {
            collectors.allocate(1);
            generations.allocate(2);
            name.allocate(GCImpl.getPolicy().getName());
        }
    }

    private static class PerfDataCollector {
        private final PerfStringConstant name;

        private final PerfLongCounter invocations;
        private final PerfLongCounter time;

        @Platforms(Platform.HOSTED_ONLY.class)
        PerfDataCollector(int index) {
            PerfManager manager = ImageSingletons.lookup(PerfManager.class);
            this.name = manager.createStringConstant("sun.gc.collector." + index + ".name");

            this.invocations = manager.createLongCounter("sun.gc.collector." + index + ".invocations", PerfUnit.EVENTS);
            this.time = manager.createLongCounter("sun.gc.collector." + index + ".time", PerfUnit.TICKS);
        }

        public void allocate(String collectorName) {
            name.allocate(collectorName);

            invocations.allocate();
            time.allocate();
        }
    }

    static class PerfDataGeneration {
        private final PerfStringConstant name;
        private final PerfLongConstant numSpaces;

        final PerfLongVariable capacity;
        final PerfLongVariable maxCapacity;
        private final SpacePerfData[] spaces;

        @Platforms(Platform.HOSTED_ONLY.class)
        PerfDataGeneration(int generationIndex, SpacePerfData[] spaces) {
            PerfManager manager = ImageSingletons.lookup(PerfManager.class);
            this.name = manager.createStringConstant("sun.gc.generation." + generationIndex + ".name");
            this.numSpaces = manager.createLongConstant("sun.gc.generation." + generationIndex + ".spaces", PerfUnit.NONE);

            this.capacity = manager.createLongVariable("sun.gc.generation." + generationIndex + ".capacity", PerfUnit.BYTES);
            this.maxCapacity = manager.createLongVariable("sun.gc.generation." + generationIndex + ".maxCapacity", PerfUnit.BYTES);
            this.spaces = spaces;
        }

        public void allocate(String generationName) {
            name.allocate(generationName);
            numSpaces.allocate(spaces.length);

            capacity.allocate();
            maxCapacity.allocate();
        }
    }

    static class SpacePerfData {
        private final PerfStringConstant name;

        final PerfLongVariable used;

        @Platforms(Platform.HOSTED_ONLY.class)
        SpacePerfData(int generationIndex, int spaceIndex) {
            PerfManager manager = ImageSingletons.lookup(PerfManager.class);
            name = manager.createStringConstant("sun.gc.generation." + generationIndex + ".space." + spaceIndex + ".name");

            used = manager.createLongVariable("sun.gc.generation." + generationIndex + ".space." + spaceIndex + ".used", PerfUnit.BYTES);
        }

        public void allocate(String spaceName) {
            name.allocate(spaceName);

            used.allocate();
        }
    }
}
