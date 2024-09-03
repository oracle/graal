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

class NmtMallocMemoryInfo {
    private final AtomicLong count = new AtomicLong(0);
    private final AtomicLong used = new AtomicLong(0);
    private final AtomicLong countAtPeakUsage = new AtomicLong(0);
    private final AtomicLong peakUsed = new AtomicLong(0);

    @Platforms(Platform.HOSTED_ONLY.class)
    NmtMallocMemoryInfo() {
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    void track(UnsignedWord allocationSize) {
        long newUsed = used.addAndGet(allocationSize.rawValue());
        long newCount = count.incrementAndGet();
        updatePeak(newUsed, newCount);
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private void updatePeak(long newUsed, long newCount) {
        long oldUsed = peakUsed.get();
        while (oldUsed < newUsed) {
            if (peakUsed.compareAndSet(oldUsed, newUsed)) {
                /* Recording the count at peak usage is racy (similar to Hotspot). */
                countAtPeakUsage.set(newCount);
                return;
            }
            oldUsed = peakUsed.get();
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
        return peakUsed.get();
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    long getCountAtPeakUsage() {
        return countAtPeakUsage.get();
    }
}
