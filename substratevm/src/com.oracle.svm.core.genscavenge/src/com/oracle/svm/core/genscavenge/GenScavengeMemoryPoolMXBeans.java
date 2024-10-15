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

import java.lang.management.MemoryUsage;


import com.oracle.svm.core.gc.AbstractMemoryPoolMXBean;
import com.oracle.svm.core.gc.MemoryPoolMXBeansProvider;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.util.VMError;

public class GenScavengeMemoryPoolMXBeans implements MemoryPoolMXBeansProvider {
    public static final String YOUNG_GEN_SCAVENGER = "young generation scavenger";
    public static final String COMPLETE_SCAVENGER = "complete scavenger";
    static final String EPSILON_SCAVENGER = "epsilon scavenger";

    static final String EDEN_SPACE = "eden space";
    static final String SURVIVOR_SPACE = "survivor space";
    public static final String OLD_GEN_SPACE = "old generation space";
    static final String EPSILON_HEAP = "epsilon heap";

    private final AbstractMemoryPoolMXBean[] mxBeans;

    @Platforms(Platform.HOSTED_ONLY.class)
    public GenScavengeMemoryPoolMXBeans() {
        if (SubstrateOptions.useSerialGC()) {
            mxBeans = new AbstractMemoryPoolMXBean[]{
                            new EdenMemoryPoolMXBean(YOUNG_GEN_SCAVENGER, COMPLETE_SCAVENGER),
                            new SurvivorMemoryPoolMXBean(YOUNG_GEN_SCAVENGER, COMPLETE_SCAVENGER),
                            new OldGenerationMemoryPoolMXBean(COMPLETE_SCAVENGER)
            };
        } else {
            assert SubstrateOptions.useEpsilonGC();
            mxBeans = new AbstractMemoryPoolMXBean[]{
                            new EpsilonMemoryPoolMXBean(EPSILON_SCAVENGER)
            };
        }
    }

    @Override
    public AbstractMemoryPoolMXBean[] getMXBeans() {
        return mxBeans;
    }

    @Override
    public String getCollectorName(boolean isIncremental) {
        return isIncremental ? YOUNG_GEN_SCAVENGER : COMPLETE_SCAVENGER;
    }

    @Override
    public void notifyBeforeCollection() {
        for (AbstractMemoryPoolMXBean mxBean : mxBeans) {
            mxBean.beforeCollection();
        }
    }

    @Override
    public void notifyAfterCollection() {
        for (AbstractMemoryPoolMXBean mxBean : mxBeans) {
            mxBean.afterCollection();
        }
    }

    static final class EdenMemoryPoolMXBean extends AbstractMemoryPoolMXBean {

        @Platforms(Platform.HOSTED_ONLY.class)
        EdenMemoryPoolMXBean(String... managerNames) {
            super(EDEN_SPACE, managerNames);
        }

        @Override
        public void beforeCollection() {
            updatePeakUsage(HeapImpl.getAccounting().getEdenUsedBytes());
        }

        @Override
        public void afterCollection() {
            /* Nothing to do. */
        }

        @Override
        protected UnsignedWord computeInitialValue() {
            return GCImpl.getPolicy().getInitialEdenSize();
        }

        @Override
        public MemoryUsage getUsage() {
            return memoryUsage(getUsedBytes());
        }

        @Override
        public MemoryUsage getPeakUsage() {
            updatePeakUsage(getUsedBytes());
            return memoryUsage(peakUsage.get());
        }

        @Override
        public MemoryUsage getCollectionUsage() {
            return memoryUsage(WordFactory.zero());
        }

        @Override
        public UnsignedWord getUsedBytes() {
            return HeapImpl.getAccounting().getEdenUsedBytes();
        }
    }

    static final class SurvivorMemoryPoolMXBean extends AbstractMemoryPoolMXBean {

        @Platforms(Platform.HOSTED_ONLY.class)
        SurvivorMemoryPoolMXBean(String... managerNames) {
            super(SURVIVOR_SPACE, managerNames);
        }

        @Override
        public void beforeCollection() {
            /* Nothing to do. */
        }

        @Override
        public void afterCollection() {
            updatePeakUsage(HeapImpl.getAccounting().getSurvivorUsedBytes());
        }

        @Override
        protected UnsignedWord computeInitialValue() {
            return GCImpl.getPolicy().getInitialSurvivorSize();
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
            return memoryUsage(HeapImpl.getAccounting().getSurvivorUsedBytes());
        }

        @Override
        public UnsignedWord getUsedBytes() {
            return HeapImpl.getAccounting().getSurvivorUsedBytes();
        }
    }

    static final class OldGenerationMemoryPoolMXBean extends AbstractMemoryPoolMXBean {

        @Platforms(Platform.HOSTED_ONLY.class)
        OldGenerationMemoryPoolMXBean(String... managerNames) {
            super(OLD_GEN_SPACE, managerNames);
        }

        @Override
        public void beforeCollection() {
            /* Nothing to do. */
        }

        @Override
        public void afterCollection() {
            updatePeakUsage(HeapImpl.getAccounting().getOldUsedBytes());
        }

        @Override
        protected UnsignedWord computeInitialValue() {
            return GCImpl.getPolicy().getInitialOldSize();
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
            return memoryUsage(HeapImpl.getAccounting().getOldUsedBytes());
        }

        @Override
        public UnsignedWord getUsedBytes() {
            return HeapImpl.getAccounting().getOldUsedBytes();
        }
    }

    static final class EpsilonMemoryPoolMXBean extends AbstractMemoryPoolMXBean {

        @Platforms(Platform.HOSTED_ONLY.class)
        EpsilonMemoryPoolMXBean(String... managerNames) {
            super(EPSILON_HEAP, managerNames);
        }

        @Override
        public void beforeCollection() {
            throw VMError.shouldNotReachHereAtRuntime(); // ExcludeFromJacocoGeneratedReport
        }

        @Override
        public void afterCollection() {
            throw VMError.shouldNotReachHereAtRuntime(); // ExcludeFromJacocoGeneratedReport
        }

        @Override
        protected UnsignedWord computeInitialValue() {
            return GCImpl.getPolicy().getMinimumHeapSize();
        }

        @Override
        public MemoryUsage getUsage() {
            HeapAccounting accounting = HeapImpl.getAccounting();
            return memoryUsage(accounting.getUsedBytes(), accounting.getCommittedBytes());
        }

        @Override
        public MemoryUsage getPeakUsage() {
            return getUsage();
        }

        @Override
        public MemoryUsage getCollectionUsage() {
            return memoryUsage(WordFactory.zero());
        }

        @Override
        public UnsignedWord getUsedBytes() {
            return HeapImpl.getAccounting().getUsedBytes();
        }

        @Override
        public UnsignedWord getCommittedBytes() {
            return HeapImpl.getAccounting().getCommittedBytes();
        }
    }
}
