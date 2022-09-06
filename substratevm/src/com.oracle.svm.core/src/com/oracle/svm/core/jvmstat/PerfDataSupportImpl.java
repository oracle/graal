/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.jvmstat;

import java.nio.ByteBuffer;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.ProcessProperties;
import org.graalvm.nativeimage.c.type.CLongPointer;

public class PerfDataSupportImpl implements PerfDataSupport {
    @Platforms(Platform.HOSTED_ONLY.class)
    PerfDataSupportImpl() {
    }

    @Override
    public ByteBuffer attach(int lvmid) {
        if (lvmid != 0 && lvmid != ProcessProperties.getProcessID()) {
            throw new IllegalArgumentException("Attaching to the performance data of another Java virtual machine is not supported at the moment.");
        }

        waitForPerformanceDataInitialization();
        return ImageSingletons.lookup(PerfMemory.class).createByteBuffer();
    }

    @Override
    public void detach(ByteBuffer bb) {
        // Nothing to do as we only support attaching to the current process.
    }

    @Override
    public long highResCounter() {
        // No need to wait for the performance data initialization.
        PerfManager perfManager = ImageSingletons.lookup(PerfManager.class);
        return perfManager.elapsedTicks();
    }

    @Override
    public long highResFrequency() {
        return 1L * 1000 * 1000 * 1000;
    }

    @Override
    public ByteBuffer createLong(String name, int variability, int units, long value) {
        PerfUnit unit = PerfUnit.fromInt(units);
        if (unit != PerfUnit.NONE && unit != PerfUnit.BYTES && unit != PerfUnit.TICKS && unit != PerfUnit.EVENTS && unit != PerfUnit.HERTZ) {
            throw new IllegalArgumentException("Unexpected units value: " + units);
        }
        PerfVariability var = PerfVariability.fromInt(variability);
        if (var != PerfVariability.CONSTANT && var != PerfVariability.MONOTONIC && var != PerfVariability.VARIABLE) {
            throw new IllegalArgumentException("Unexpected variability value: " + variability);
        }

        waitForPerformanceDataInitialization();
        PerfDirectMemoryLong result = new PerfDirectMemoryLong(name, unit);
        return result.allocate(var, value);
    }

    @Override
    public ByteBuffer createByteArray(String name, int variability, int units, byte[] value, int maxLength) {
        PerfUnit unit = PerfUnit.fromInt(units);
        if (unit != PerfUnit.STRING) {
            throw new IllegalArgumentException("Unexpected units value: " + units);
        }

        PerfVariability var = PerfVariability.fromInt(variability);
        int actualMaxLength;
        if (var == PerfVariability.CONSTANT) {
            actualMaxLength = value.length;
        } else if (var == PerfVariability.VARIABLE) {
            actualMaxLength = maxLength;
        } else {
            throw new IllegalArgumentException("Unexpected variability value: " + variability);
        }

        waitForPerformanceDataInitialization();
        PerfDirectMemoryString result = new PerfDirectMemoryString(name, unit);
        return result.allocate(var, value, actualMaxLength);
    }

    @Override
    public CLongPointer getLong(String name) {
        PerfManager perfManager = ImageSingletons.lookup(PerfManager.class);
        return perfManager.getLongPerfEntry(name);
    }

    @Override
    public boolean hasLong(String name) {
        PerfManager perfManager = ImageSingletons.lookup(PerfManager.class);
        return perfManager.hasLongPerfEntry(name);
    }

    private static void waitForPerformanceDataInitialization() {
        ImageSingletons.lookup(PerfManager.class).waitForInitialization();
    }
}
