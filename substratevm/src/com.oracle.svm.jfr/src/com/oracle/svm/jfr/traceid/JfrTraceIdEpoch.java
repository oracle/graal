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

import java.lang.reflect.Field;

import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.compiler.serviceprovider.GraalUnsafeAccess;
import org.graalvm.compiler.word.Word;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.thread.VMOperation;
import com.oracle.svm.util.ReflectionUtil;

import sun.misc.Unsafe;

/**
 * Class holding the current JFR epoch. JFR uses an epoch system to safely separate constant pool
 * entries between adjacent chunks. Used to get the current or previous epoch and switch from one
 * epoch to another across an uninterruptible safepoint operation.
 */
public class JfrTraceIdEpoch {
    private static final Unsafe UNSAFE = GraalUnsafeAccess.getUnsafe();
    private static final Field EPOCH_FIELD = ReflectionUtil.lookupField(JfrTraceIdEpoch.class, "epoch");

    private static final long EPOCH_0_BIT = 0b01;
    private static final long EPOCH_1_BIT = 0b10;

    private boolean epoch;

    @Fold
    public static JfrTraceIdEpoch getInstance() {
        return ImageSingletons.lookup(JfrTraceIdEpoch.class);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public JfrTraceIdEpoch() {
    }

    public long getEpochAddress() {
        UnsignedWord epochFieldOffset = WordFactory.unsigned(UNSAFE.objectFieldOffset(EPOCH_FIELD));
        return Word.objectToUntrackedPointer(this).add(epochFieldOffset).rawValue();
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public void changeEpoch() {
        assert VMOperation.isInProgressAtSafepoint();
        epoch = !epoch;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    long thisEpochBit() {
        return epoch ? EPOCH_1_BIT : EPOCH_0_BIT;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    long previousEpochBit() {
        return epoch ? EPOCH_0_BIT : EPOCH_1_BIT;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public boolean currentEpoch() {
        return epoch;
    }

    @Uninterruptible(reason = "Called by uninterruptible code.", mayBeInlined = true)
    public boolean previousEpoch() {
        return !epoch;
    }
}
