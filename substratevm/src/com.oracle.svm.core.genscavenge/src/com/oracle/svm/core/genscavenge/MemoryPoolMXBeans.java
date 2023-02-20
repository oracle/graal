/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2022, 2022, BELLSOFT. All rights reserved.
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
package com.oracle.svm.core.genscavenge;

import com.oracle.svm.core.SubstrateOptions;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryUsage;
import java.util.List;

public class MemoryPoolMXBeans {

    private static final String YOUNG_GEN_SCAVENGER = "young generation scavenger";
    private static final String COMPLETE_SCAVENGER = "complete scavenger";
    private static final String EPSILON_SCAVENGER = "epsilon scavenger";

    private static AbstractMemoryPoolMXBean[] mxBeans;

    @Platforms(Platform.HOSTED_ONLY.class)
    public static List<MemoryPoolMXBean> createMemoryPoolMXBeans() {
        List<MemoryPoolMXBean> beanList;
        if (SubstrateOptions.UseSerialGC.getValue()) {
            beanList = List.of(
                    new EdenMemoryPoolMXBean(YOUNG_GEN_SCAVENGER, COMPLETE_SCAVENGER),
                    new SurvivorMemoryPoolMXBean(YOUNG_GEN_SCAVENGER, COMPLETE_SCAVENGER),
                    new OldGenerationMemoryPoolMXBean(COMPLETE_SCAVENGER));
        } else {
            assert SubstrateOptions.UseEpsilonGC.getValue();
            beanList = List.of(new EpsilonMemoryPoolMXBean(EPSILON_SCAVENGER));
        }
        mxBeans = beanList.toArray(AbstractMemoryPoolMXBean[]::new);
        return beanList;
    }

    public static void notifyAfterCollection() {
        for (AbstractMemoryPoolMXBean bean: mxBeans) {
            bean.afterCollection();
        }
    }

    /**
     * A MemoryPoolMXBean for the eden space.
     */
    static final class EdenMemoryPoolMXBean extends AbstractMemoryPoolMXBean {

        @Platforms(Platform.HOSTED_ONLY.class)
        public EdenMemoryPoolMXBean(String... managerNames) {
            super("eden space", managerNames);
        }

        @Override
        void afterCollection() {
            updatePeakUsage(GCImpl.getGCImpl().getAccounting().getEdenChunkBytesBefore());
        }

        @Override
        public MemoryUsage getUsage() {
            return memoryUsage(getCurrentUsage());
        }

        @Override
        public MemoryUsage getPeakUsage() {
            updatePeakUsage(getCurrentUsage());
            return memoryUsage(peakUsage.get());
        }

        @Override
        public MemoryUsage getCollectionUsage() {
            return memoryUsage(WordFactory.zero());
        }

        private static UnsignedWord getCurrentUsage() {
            return HeapImpl.getHeapImpl().getAccounting().getEdenUsedBytes();
        }

        private static MemoryUsage memoryUsage(UnsignedWord usedAndCommitted) {
            CollectionPolicy policy = GCImpl.getPolicy();
            UnsignedWord max = policy.getMaximumYoungGenerationSize().subtract(policy.getMaximumSurvivorSize());
            return new MemoryUsage(0L, usedAndCommitted.rawValue(), usedAndCommitted.rawValue(), max.rawValue());
        }
    }

    /**
     * A MemoryPoolMXBean for the survivor spaces.
     */
    static final class SurvivorMemoryPoolMXBean extends AbstractMemoryPoolMXBean {

        @Platforms(Platform.HOSTED_ONLY.class)
        public SurvivorMemoryPoolMXBean(String... managerNames) {
            super("survivor space", managerNames);
        }

        @Override
        void afterCollection() {
            updatePeakUsage(GCImpl.getGCImpl().getAccounting().getYoungChunkBytesAfter());
        }

        @Override
        public MemoryUsage getUsage() {
            return getCollectionUsage();
        }

        @Override
        public MemoryUsage getPeakUsage() {
            return memoryUsage(peakUsage.get());
        }

        @Override
        public MemoryUsage getCollectionUsage() {
            return memoryUsage(GCImpl.getGCImpl().getAccounting().getYoungChunkBytesAfter());
        }

        private static MemoryUsage memoryUsage(UnsignedWord usedAndCommitted) {
            UnsignedWord max = GCImpl.getPolicy().getMaximumSurvivorSize();
            return new MemoryUsage(0L, usedAndCommitted.rawValue(), usedAndCommitted.rawValue(), max.rawValue());
        }
    }

    /**
     * A MemoryPoolMXBean for the old generation.
     */
    static final class OldGenerationMemoryPoolMXBean extends AbstractMemoryPoolMXBean {

        @Platforms(Platform.HOSTED_ONLY.class)
        public OldGenerationMemoryPoolMXBean(String... managerNames) {
            super("old generation space", managerNames);
        }

        @Override
        void afterCollection() {
            updatePeakUsage(GCImpl.getGCImpl().getAccounting().getOldGenerationAfterChunkBytes());
        }

        @Override
        public MemoryUsage getUsage() {
            return getCollectionUsage();
        }

        @Override
        public MemoryUsage getPeakUsage() {
            return memoryUsage(peakUsage.get());
        }

        @Override
        public MemoryUsage getCollectionUsage() {
            return memoryUsage(GCImpl.getGCImpl().getAccounting().getOldGenerationAfterChunkBytes());
        }

        private static MemoryUsage memoryUsage(UnsignedWord usedAndCommitted) {
            CollectionPolicy policy = GCImpl.getPolicy();
            UnsignedWord max = policy.getMaximumHeapSize().subtract(policy.getMaximumYoungGenerationSize());
            return new MemoryUsage(0L, usedAndCommitted.rawValue(), usedAndCommitted.rawValue(), max.rawValue());
        }
    }

    /**
     * A MemoryPoolMXBean for the Epsilon collector.
     */
    static final class EpsilonMemoryPoolMXBean extends AbstractMemoryPoolMXBean {

        private final long MAX_HEAP_SIZE = GCImpl.getPolicy().getMaximumHeapSize().rawValue();

        @Platforms(Platform.HOSTED_ONLY.class)
        public EpsilonMemoryPoolMXBean(String... managerNames) {
            super("epsilon heap", managerNames);
        }

        @Override
        void afterCollection() {
        }

        @Override
        public MemoryUsage getUsage() {
            HeapImpl heapImpl = HeapImpl.getHeapImpl();
            long used = heapImpl.getUsedBytes().rawValue();
            long committed = heapImpl.getCommittedBytes().rawValue();
            return memoryUsage(used, committed);
        }

        @Override
        public MemoryUsage getPeakUsage() {
            return getUsage();
        }

        @Override
        public MemoryUsage getCollectionUsage() {
            return memoryUsage(0L, 0L);
        }

        private MemoryUsage memoryUsage(long used, long committed) {
            return new MemoryUsage(0L, used, committed, MAX_HEAP_SIZE);
        }
    }
}
