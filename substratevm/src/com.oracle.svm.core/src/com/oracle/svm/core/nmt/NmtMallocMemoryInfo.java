/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2023, 2024, Red Hat Inc. All rights reserved.
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

package com.oracle.svm.core.nmt;

import static com.oracle.svm.core.Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.word.UnsignedWord;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.jdk.UninterruptibleUtils.AtomicLong;

import jdk.internal.misc.Unsafe;

class NmtMallocMemoryInfo {
    private static final Unsafe U = Unsafe.getUnsafe();
    protected static final long PEAK_USAGE_OFFSET = U.objectFieldOffset(NmtMallocMemoryInfo.class, "peakUsage");
    private final AtomicLong count = new AtomicLong(0);
    private final AtomicLong used = new AtomicLong(0);
    private volatile long peakUsage;
    private volatile long peakCount;

    @Platforms(Platform.HOSTED_ONLY.class)
    NmtMallocMemoryInfo() {
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    void track(UnsignedWord allocationSize) {
        /*
         * Similar to Hotspot, we only make a best effort to record the count at the time of the
         * peak. Observing the memory used and count is not together atomic.
         */
        updatePeak(used.addAndGet(allocationSize.rawValue()), count.incrementAndGet());
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private void updatePeak(long newUsed, long newCount) {
        long expectedPeakUsage = peakUsage;
        while (expectedPeakUsage < newUsed) {
            if (U.compareAndSetLong(this, PEAK_USAGE_OFFSET, expectedPeakUsage, newUsed)) {
                peakCount = newCount;
                return;
            }
            expectedPeakUsage = peakUsage;
        }
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    void untrack(UnsignedWord allocationSize) {
        long lastCount = count.decrementAndGet();
        long lastSize = used.addAndGet(-allocationSize.rawValue());
        assert lastSize >= 0 && lastCount >= 0;
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    long getUsed() {
        return used.get();
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    long getCount() {
        return count.get();
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    long getPeakUsed() {
        return peakUsage;
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    long getCountAtPeakUsage() {
        return peakCount;
    }
}
