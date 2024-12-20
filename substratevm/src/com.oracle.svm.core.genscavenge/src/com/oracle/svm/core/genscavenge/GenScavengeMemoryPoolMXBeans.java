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

import jdk.graal.compiler.word.Word;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.word.UnsignedWord;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.util.VMError;

import jdk.graal.compiler.api.replacements.Fold;

public class GenScavengeMemoryPoolMXBeans {
    static final String YOUNG_GEN_SCAVENGER = "young generation scavenger";
    static final String COMPLETE_SCAVENGER = "complete scavenger";
    static final String EPSILON_SCAVENGER = "epsilon scavenger";

    static final String EDEN_SPACE = "eden space";
    static final String SURVIVOR_SPACE = "survivor space";
    static final String OLD_GEN_SPACE = "old generation space";
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

    @Fold
    public static GenScavengeMemoryPoolMXBeans singleton() {
        return ImageSingletons.lookup(GenScavengeMemoryPoolMXBeans.class);
    }

    public AbstractMemoryPoolMXBean[] getMXBeans() {
        return mxBeans;
    }

    public void notifyBeforeCollection() {
        for (AbstractMemoryPoolMXBean mxBean : mxBeans) {
            mxBean.beforeCollection();
        }
    }

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
        void beforeCollection() {
            updatePeakUsage(HeapImpl.getAccounting().getEdenUsedBytes());
        }

        @Override
        void afterCollection() {
            /* Nothing to do. */
        }

        @Override
        UnsignedWord computeInitialValue() {
            return GCImpl.getPolicy().getInitialEdenSize();
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
            return memoryUsage(Word.zero());
        }

        private static UnsignedWord getCurrentUsage() {
            return HeapImpl.getAccounting().getEdenUsedBytes();
        }
    }

    static final class SurvivorMemoryPoolMXBean extends AbstractMemoryPoolMXBean {

        @Platforms(Platform.HOSTED_ONLY.class)
        SurvivorMemoryPoolMXBean(String... managerNames) {
            super(SURVIVOR_SPACE, managerNames);
        }

        @Override
        void beforeCollection() {
            /* Nothing to do. */
        }

        @Override
        void afterCollection() {
            updatePeakUsage(HeapImpl.getAccounting().getSurvivorUsedBytes());
        }

        @Override
        UnsignedWord computeInitialValue() {
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
    }

    static final class OldGenerationMemoryPoolMXBean extends AbstractMemoryPoolMXBean {

        @Platforms(Platform.HOSTED_ONLY.class)
        OldGenerationMemoryPoolMXBean(String... managerNames) {
            super(OLD_GEN_SPACE, managerNames);
        }

        @Override
        void beforeCollection() {
            /* Nothing to do. */
        }

        @Override
        void afterCollection() {
            updatePeakUsage(HeapImpl.getAccounting().getOldUsedBytes());
        }

        @Override
        UnsignedWord computeInitialValue() {
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
    }

    static final class EpsilonMemoryPoolMXBean extends AbstractMemoryPoolMXBean {

        @Platforms(Platform.HOSTED_ONLY.class)
        EpsilonMemoryPoolMXBean(String... managerNames) {
            super(EPSILON_HEAP, managerNames);
        }

        @Override
        void beforeCollection() {
            throw VMError.shouldNotReachHereAtRuntime(); // ExcludeFromJacocoGeneratedReport
        }

        @Override
        void afterCollection() {
            throw VMError.shouldNotReachHereAtRuntime(); // ExcludeFromJacocoGeneratedReport
        }

        @Override
        UnsignedWord computeInitialValue() {
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
            return memoryUsage(Word.zero());
        }
    }
}
