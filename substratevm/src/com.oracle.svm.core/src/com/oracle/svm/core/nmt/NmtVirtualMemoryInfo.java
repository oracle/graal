/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2024, 2024, Red Hat Inc. All rights reserved.
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

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.jdk.UninterruptibleUtils.AtomicLong;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.word.UnsignedWord;
import jdk.internal.misc.Unsafe;

class NmtVirtualMemoryInfo {

    private static final Unsafe U = Unsafe.getUnsafe();
    protected static final long PEAK_RESERVED_OFFSET = U.objectFieldOffset(NmtVirtualMemoryInfo.class, "peakReservedSize");
    protected static final long PEAK_COMMITTED_OFFSET = U.objectFieldOffset(NmtVirtualMemoryInfo.class, "peakCommittedSize");
    private AtomicLong reservedSize;
    private AtomicLong committedSize;
    private volatile long peakReservedSize;
    private volatile long peakCommittedSize;

    @Platforms(Platform.HOSTED_ONLY.class)
    NmtVirtualMemoryInfo() {
        reservedSize = new AtomicLong(0);
        committedSize = new AtomicLong(0);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    void trackReserved(UnsignedWord size) {
        updatePeakReserved(reservedSize.addAndGet(size.rawValue()));

    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    void trackCommitted(UnsignedWord size) {
        updatePeakCommitted(committedSize.addAndGet(size.rawValue()));
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    void trackUncommit(long size) {
        long lastSize = committedSize.addAndGet(-size);
        assert lastSize >= 0;

    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    void trackFree(long size) {
        long lastSize = reservedSize.addAndGet(-size);
        assert lastSize >= 0;
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private void updatePeakReserved(long newValue) {
        long expectedPeak = peakReservedSize;
        while (expectedPeak < newValue) {
            if (U.compareAndSetLong(this, PEAK_RESERVED_OFFSET, expectedPeak, newValue)) { // TODO
                                                                                           // use
                                                                                           // compare
                                                                                           // and
                                                                                           // set in
                                                                                           // other
                                                                                           // PR
                                                                                           // too!
                return;
            }
            expectedPeak = peakReservedSize;
        }
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private void updatePeakCommitted(long newValue) {
        long expectedPeak = peakCommittedSize;
        while (expectedPeak < newValue) {
            if (U.compareAndSetLong(this, PEAK_COMMITTED_OFFSET, expectedPeak, newValue)) {
                return;
            }
            expectedPeak = peakCommittedSize;
        }
    }

    long getReservedSize() {
        return reservedSize.get();
    }

    long getCommittedSize() {
        return committedSize.get();
    }

    long getPeakReservedSize() {
        return peakReservedSize;
    }

    long getPeakCommittedSize() {
        return peakCommittedSize;
    }
}
