/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */
package com.oracle.svm.core.genscavenge;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.jvmstat.PerfDataHolder;
import com.oracle.svm.core.jvmstat.PerfLongConstant;
import com.oracle.svm.core.jvmstat.PerfLongCounter;
import com.oracle.svm.core.jvmstat.PerfLongVariable;
import com.oracle.svm.core.jvmstat.PerfManager;
import com.oracle.svm.core.jvmstat.PerfStringConstant;
import com.oracle.svm.core.jvmstat.PerfUnit;

public class GenScavengePerfData implements PerfDataHolder {
    private final PerfDataGCPolicy gcPolicy;
    private final PerfDataCollector[] collectors;
    private final PerfDataGeneration[] generations;

    @Platforms(Platform.HOSTED_ONLY.class)
    public GenScavengePerfData() {
        gcPolicy = new PerfDataGCPolicy();

        collectors = new PerfDataCollector[]{
                        new PerfDataCollector(0),
                        new PerfDataCollector(1)
        };

        int youngSpaceCount = 1 + HeapPolicy.getMaxSurvivorSpaces();
        SpacePerfData[] youngGenSpaces = new SpacePerfData[youngSpaceCount];
        for (int i = 0; i < youngSpaceCount; i++) {
            youngGenSpaces[i] = new SpacePerfData(0, i);
        }

        int oldSpaceIndex = youngSpaceCount;
        SpacePerfData[] oldGenSpaces = new SpacePerfData[]{
                        new SpacePerfData(1, oldSpaceIndex)
        };

        generations = new PerfDataGeneration[]{
                        new PerfDataGeneration(0, youngGenSpaces),
                        new PerfDataGeneration(1, oldGenSpaces)
        };

// // young GC
// collectors[0].invocations.inc();
// collectors[0].time.add(youngCollectionTime);
//
// // old GC
// collectors[1].invocations.inc();
// collectors[1].time.add(oldCollectionTime);
//
// // every GC & partially after value parsing
// generations[0].capacity.setValue(usedYoung);
// generations[0].maxCapacity.setValue(MaxNewSize);
// generations[1].capacity.setValue(oldSize);
// generations[1].maxCapacity.setValue(MaxHeapSize - maxNewSize);;
//
// generations[0].spaces[0].used.setValue(edenSize);
// generations[0].spaces[1].used.setValue(survivor0Size);
// ...
//
// generations[1].spaces[0].used.setValue(usedOld);
    }

    @Override
    public void allocate() {
        gcPolicy.allocate();

        collectors[0].allocate("Serial young collection pauses");
        collectors[1].allocate("Serial full collection pauses");
        assert collectors.length == 2;

        generations[0].allocate("young");
        generations[0].spaces[0].allocate("eden");
        for (int i = 1; i < generations[0].spaces.length; i++) {
            generations[0].spaces[i].allocate("s" + (i - 1));
        }

        generations[1].allocate("old");
        generations[1].spaces[1].allocate("old");
        assert generations[1].spaces.length == 1;
        assert generations.length == 2;
    }

    @Override
    public void update() {
        // nothing to do
    }

    private static class PerfDataGCPolicy {
        private final PerfLongConstant collectors;
        private final PerfLongConstant generations;
        private final PerfStringConstant name;

        @Platforms(Platform.HOSTED_ONLY.class)
        public PerfDataGCPolicy() {
            PerfManager manager = ImageSingletons.lookup(PerfManager.class);
            collectors = manager.createPerfLongConstant("sun.gc.policy.collectors", PerfUnit.NONE);
            generations = manager.createPerfLongConstant("sun.gc.policy.generations", PerfUnit.NONE);
            name = manager.createPerfStringConstant("sun.gc.policy.name");
        }

        public void allocate() {
            collectors.allocate(1);
            generations.allocate(2);
            name.allocate("Serial");
        }
    }

    private static class PerfDataCollector {
        private final PerfStringConstant name;

        private final PerfLongCounter invocations;
        private final PerfLongCounter time;

        @Platforms(Platform.HOSTED_ONLY.class)
        public PerfDataCollector(int index) {
            PerfManager manager = ImageSingletons.lookup(PerfManager.class);
            this.name = manager.createPerfStringConstant("sun.gc.collector." + index + ".name");

            this.invocations = manager.createPerfLongCounter("sun.gc.collector." + index + ".invocations", PerfUnit.EVENTS);
            this.time = manager.createPerfLongCounter("sun.gc.collector." + index + ".time", PerfUnit.TICKS);
        }

        public void allocate(String collectorName) {
            name.allocate(collectorName);

            invocations.allocate();
            time.allocate();
        }
    }

    private static class PerfDataGeneration {
        private final PerfStringConstant name;
        private final PerfLongConstant numSpaces;

        private final PerfLongVariable capacity;
        private final PerfLongVariable maxCapacity;
        private final SpacePerfData[] spaces;

        @Platforms(Platform.HOSTED_ONLY.class)
        public PerfDataGeneration(int generationIndex, SpacePerfData[] spaces) {
            PerfManager manager = ImageSingletons.lookup(PerfManager.class);
            this.name = manager.createPerfStringConstant("sun.gc.generation." + generationIndex + ".name");
            this.numSpaces = manager.createPerfLongConstant("sun.gc.generation." + generationIndex + ".spaces", PerfUnit.NONE);

            this.capacity = manager.createPerfLongVariable("sun.gc.generation." + generationIndex + ".capacity", PerfUnit.BYTES);
            this.maxCapacity = manager.createPerfLongVariable("sun.gc.generation." + generationIndex + ".maxCapacity", PerfUnit.BYTES);
            this.spaces = spaces;
        }

        public void allocate(String generationName) {
            name.allocate(generationName);
            numSpaces.allocate(spaces.length);

            capacity.allocate();
            maxCapacity.allocate();
        }
    }

    private static class SpacePerfData {
        private final PerfStringConstant name;

        private final PerfLongVariable used;

        @Platforms(Platform.HOSTED_ONLY.class)
        public SpacePerfData(int generationIndex, int spaceIndex) {
            PerfManager manager = ImageSingletons.lookup(PerfManager.class);
            name = manager.createPerfStringConstant("sun.gc.generation." + generationIndex + ".space." + spaceIndex + ".name");

            used = manager.createPerfLongVariable("sun.gc.generation." + generationIndex + ".space." + spaceIndex + ".used", PerfUnit.BYTES);
        }

        public void allocate(String spaceName) {
            name.allocate(spaceName);

            used.allocate();
        }
    }
}
