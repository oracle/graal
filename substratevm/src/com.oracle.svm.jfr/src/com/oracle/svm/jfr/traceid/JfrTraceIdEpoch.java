/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.svm.jfr.traceid;

import com.oracle.svm.core.annotate.Uninterruptible;

public class JfrTraceIdEpoch {
    public static final long BIT = 1;
    public static final long METHOD_BIT = (BIT << 2);
    public static final long EPOCH_0_SHIFT = 0;
    public static final long EPOCH_1_SHIFT = 1;
    public static final long EPOCH_0_BIT = (BIT << EPOCH_0_SHIFT);
    public static final long EPOCH_1_BIT = (BIT << EPOCH_1_SHIFT);
    public static final long EPOCH_0_METHOD_BIT = (METHOD_BIT << EPOCH_0_SHIFT);
    public static final long EPOCH_1_METHOD_BIT = (METHOD_BIT << EPOCH_1_SHIFT);
    public static final long METHOD_AND_CLASS_BITS = (METHOD_BIT | BIT);
    public static final long EPOCH_0_METHOD_AND_CLASS_BITS = (METHOD_AND_CLASS_BITS << EPOCH_0_SHIFT);
    public static final long EPOCH_1_METHOD_AND_CLASS_BITS = (METHOD_AND_CLASS_BITS << EPOCH_1_SHIFT);

    private static boolean epoch;
    private static boolean synchronizing;
    private static volatile boolean changedTag;

    public static void beginEpochShift() {
        synchronizing = true;
    }

    public static void endEpochShift() {
        epoch = !epoch;
        synchronizing = false;
    }

    public static boolean isChangedTag() {
        return changedTag;
    }

    public static void setChangedTag(boolean changedTag) {
        JfrTraceIdEpoch.changedTag = changedTag;
    }

    public static boolean hasChangedTag() {
        if (isChangedTag()) {
            setChangedTag(false);
            return true;
        }
        return false;
    }

    public static void setChangedTag() {
        if (!isChangedTag()) {
            setChangedTag(true);
        }
    }

    public static boolean getEpoch() {
        return epoch;
    }

    static long thisEpochBit() {
        return epoch ? EPOCH_1_BIT : EPOCH_0_BIT;
    }

    @Uninterruptible(reason = "Called by uninterruptible code")
    public static boolean currentEpoch() {
        return epoch;
    }

    public static boolean previousEpoch() {
        return !epoch;
    }
}
