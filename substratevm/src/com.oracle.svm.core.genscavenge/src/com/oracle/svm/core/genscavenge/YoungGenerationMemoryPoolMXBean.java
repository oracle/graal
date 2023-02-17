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

import java.lang.management.MemoryUsage;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

/**
 * A MemoryPoolMXBean for the young generation.
 */
public final class YoungGenerationMemoryPoolMXBean extends AbstractMemoryPoolMXBean {

    @Platforms(Platform.HOSTED_ONLY.class)
    public YoungGenerationMemoryPoolMXBean() {
        super("young generation space", "young generation scavenger", "complete scavenger");
    }

    @Override
    void beforeCollection() {
        updatePeakUsage(gcAccounting.getYoungChunkBytesBefore());
    }

    @Override
    public MemoryUsage getUsage() {
        return memoryUsage(getCurrentUsage());
    }

    @Override
    public MemoryUsage getPeakUsage() {
        long current = getCurrentUsage();
        long peak = peakUsage.get().rawValue();
        return memoryUsage(Math.max(current, peak));
    }

    @Override
    public MemoryUsage getCollectionUsage() {
        long used = gcAccounting.getYoungChunkBytesAfter().rawValue();
        return memoryUsage(used);
    }

    private static long getCurrentUsage() {
        return HeapImpl.getHeapImpl().getAccounting().getYoungUsedBytes().rawValue();
    }

    private static MemoryUsage memoryUsage(long usedAndCommitted) {
        long max = GCImpl.getPolicy().getMaximumYoungGenerationSize().rawValue();
        return new MemoryUsage(0L, usedAndCommitted, usedAndCommitted, max);
    }
}
