/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.jdk;

import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.heap.PhysicalMemory;
import com.oracle.svm.core.util.VMError;
import java.lang.management.ManagementFactory;
import javax.management.ObjectName;

//Checkstyle: stop
import sun.management.Util;
//Checkstyle: resume

public abstract class SubstrateOperatingSystemMXBean implements com.sun.management.OperatingSystemMXBean {

    @Override
    public ObjectName getObjectName() {
        return Util.newObjectName(ManagementFactory.OPERATING_SYSTEM_MXBEAN_NAME);
    }

    @Override
    public String getName() {
        return System.getProperty("os.name");
    }

    @Override
    public String getArch() {
        return SubstrateUtil.getArchitectureName();
    }

    @Override
    public String getVersion() {
        return System.getProperty("os.version");
    }

    @Override
    public int getAvailableProcessors() {
        return Runtime.getRuntime().availableProcessors();
    }

    @Override
    public long getTotalPhysicalMemorySize() {
        return PhysicalMemory.size().rawValue();
    }

    @Override
    public double getSystemLoadAverage() {
        return -1;
    }

    private static final String MSG = "OperatingSystemMXBean methods";

    @Override
    public long getCommittedVirtualMemorySize() {
        throw VMError.unsupportedFeature(MSG);
    }

    @Override
    public long getTotalSwapSpaceSize() {
        throw VMError.unsupportedFeature(MSG);
    }

    @Override
    public long getFreeSwapSpaceSize() {
        throw VMError.unsupportedFeature(MSG);
    }

    @Override
    public long getProcessCpuTime() {
        throw VMError.unsupportedFeature(MSG);
    }

    @Override
    public long getFreePhysicalMemorySize() {
        throw VMError.unsupportedFeature(MSG);
    }

    @Override
    public double getSystemCpuLoad() {
        throw VMError.unsupportedFeature(MSG);
    }

    @Override
    public double getProcessCpuLoad() {
        throw VMError.unsupportedFeature(MSG);
    }
}
