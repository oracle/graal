/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2023, 2023, BELLSOFT. All rights reserved.
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
        EdenMemoryPoolMXBean(String... managerNames) {
            super("eden space", managerNames);
        }

        @Override
        UnsignedWord computeInitialValue() {
            return GCImpl.getPolicy() instanceof AbstractCollectionPolicy policy ? policy.sizes.initialEdenSize : WordFactory.signed(-1);
        }

        @Override
        UnsignedWord getMaximumValue() {
            CollectionPolicy policy = GCImpl.getPolicy();
            return policy.getMaximumYoungGenerationSize().subtract(policy.getMaximumSurvivorSize());
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
    }

    /**
     * A MemoryPoolMXBean for the survivor spaces.
     */
    static final class SurvivorMemoryPoolMXBean extends AbstractMemoryPoolMXBean {

        @Platforms(Platform.HOSTED_ONLY.class)
        SurvivorMemoryPoolMXBean(String... managerNames) {
            super("survivor space", managerNames);
        }

        @Override
        UnsignedWord computeInitialValue() {
            return GCImpl.getPolicy() instanceof AbstractCollectionPolicy policy ? policy.sizes.initialSurvivorSize : WordFactory.signed(-1);
        }

        @Override
        UnsignedWord getMaximumValue() {
            return GCImpl.getPolicy().getMaximumSurvivorSize();
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
    }

    /**
     * A MemoryPoolMXBean for the old generation.
     */
    static final class OldGenerationMemoryPoolMXBean extends AbstractMemoryPoolMXBean {

        @Platforms(Platform.HOSTED_ONLY.class)
        OldGenerationMemoryPoolMXBean(String... managerNames) {
            super("old generation space", managerNames);
        }

        @Override
        UnsignedWord computeInitialValue() {
            return GCImpl.getPolicy() instanceof AbstractCollectionPolicy policy ? policy.sizes.initialOldSize() : WordFactory.signed(-1);
        }

        @Override
        UnsignedWord getMaximumValue() {
            CollectionPolicy policy = GCImpl.getPolicy();
            return policy.getMaximumHeapSize().subtract(policy.getMaximumYoungGenerationSize());
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
    }

    /**
     * A MemoryPoolMXBean for the Epsilon collector.
     */
    static final class EpsilonMemoryPoolMXBean extends AbstractMemoryPoolMXBean {

        @Platforms(Platform.HOSTED_ONLY.class)
        EpsilonMemoryPoolMXBean(String... managerNames) {
            super("epsilon heap", managerNames);
        }

        @Override
        UnsignedWord computeInitialValue() {
            return GCImpl.getPolicy().getMinimumHeapSize();
        }

        @Override
        UnsignedWord getMaximumValue() {
            return GCImpl.getPolicy().getMaximumHeapSize();
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
            return new MemoryUsage(getInitialValue().rawValue(), used, committed, getMaximumValue().rawValue());
        }
    }
}
