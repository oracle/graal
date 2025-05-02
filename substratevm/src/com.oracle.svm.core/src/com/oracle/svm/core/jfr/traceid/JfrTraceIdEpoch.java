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

package com.oracle.svm.core.jfr.traceid;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.thread.Target_java_lang_VirtualThread;
import com.oracle.svm.core.thread.VMOperation;

import jdk.graal.compiler.api.replacements.Fold;

/**
 * Class holding the current JFR epoch. JFR uses an epoch system to safely separate constant pool
 * entries between adjacent chunks. Used to get the current or previous epoch and switch from one
 * epoch to another across an uninterruptible safepoint operation.
 */
public class JfrTraceIdEpoch {
    private static final long EPOCH_0_BIT = 0b01;
    private static final long EPOCH_1_BIT = 0b10;

    /**
     * Start the epoch id at 1, so that we can inject fields into JDK classes that store the epoch
     * id (see for example {@link Target_java_lang_VirtualThread#jfrEpochId}). This avoids problems
     * with uninitialized injected fields that have the value 0 by default.
     */
    private long epochId = 1;

    @Fold
    public static JfrTraceIdEpoch getInstance() {
        return ImageSingletons.lookup(JfrTraceIdEpoch.class);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public JfrTraceIdEpoch() {
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public void changeEpoch() {
        assert VMOperation.isInProgressAtSafepoint();
        epochId++;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    long thisEpochBit() {
        return getEpoch() ? EPOCH_1_BIT : EPOCH_0_BIT;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    long previousEpochBit() {
        return getEpoch() ? EPOCH_0_BIT : EPOCH_1_BIT;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public boolean currentEpoch() {
        return getEpoch();
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public boolean previousEpoch() {
        return !getEpoch();
    }

    @Uninterruptible(reason = "Prevent epoch from changing.", callerMustBe = true)
    public long currentEpochId() {
        return epochId;
    }

    @Uninterruptible(reason = "Prevent epoch from changing.", callerMustBe = true)
    private boolean getEpoch() {
        return (epochId & 1) == 0;
    }
}
