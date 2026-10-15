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

import java.io.IOException;
import java.nio.ByteBuffer;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.ProcessProperties;
import org.graalvm.nativeimage.c.type.CLongPointer;

import com.oracle.svm.shared.singletons.traits.BuiltinTraits.AllAccess;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.SingleLayer;
import com.oracle.svm.shared.singletons.traits.SingletonLayeredInstallationKind.InitialLayerOnly;
import com.oracle.svm.shared.singletons.traits.SingletonTraits;
import com.oracle.svm.shared.util.BasedOnJDKFile;

import sun.nio.ch.DirectBuffer;

@SingletonTraits(access = AllAccess.class, layeredCallbacks = SingleLayer.class, layeredInstallationKind = InitialLayerOnly.class)
public class PerfDataSupportImpl implements PerfDataSupport {
    @Platforms(Platform.HOSTED_ONLY.class)
    PerfDataSupportImpl() {
    }

    /// Attaches to local performance data or maps an external process's backing file.
    @Override
    @BasedOnJDKFile("https://github.com/graalvm/labs-openjdk/blob/jdk-25+3/src/hotspot/os/posix/perfMemory_posix.cpp#L1303-L1312")
    public ByteBuffer attach(int lvmid) throws IOException {
        if (lvmid != 0 && lvmid != ProcessProperties.getProcessID()) {
            return attachToOtherProcess(lvmid);
        }

        waitForPerformanceDataInitialization();
        return ImageSingletons.lookup(PerfMemory.class).createByteBuffer();
    }

    /// Maps another JVM's file-backed performance data through the platform provider.
    /// HotSpot and Native Images that export file-backed data use the same JVMStat format.
    private static ByteBuffer attachToOtherProcess(int lvmid) throws IOException {
        if (!ImageSingletons.contains(ExternalPerfDataProvider.class)) {
            throw new IllegalArgumentException("Attaching to the performance data of another Java virtual machine is not supported on this platform.");
        }
        return ImageSingletons.lookup(ExternalPerfDataProvider.class).attach(lvmid);
    }

    /// Releases an external mapping while leaving the local performance data memory attached.
    /// Local buffers must not acquire an unmapping cleaner: their memory belongs to `PerfMemory`.
    /// External mappings have a cleaner that unmaps once, even when detach is called repeatedly.
    @Override
    @BasedOnJDKFile("https://github.com/graalvm/labs-openjdk/blob/jdk-25+3/src/hotspot/os/posix/perfMemory_posix.cpp#L1330-L1341")
    public void detach(ByteBuffer bb) {
        // The cleaner also releases external mappings whose callers omit detach.
        var cleaner = ((DirectBuffer) bb).cleaner();
        if (cleaner != null) {
            cleaner.clean();
        }
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
