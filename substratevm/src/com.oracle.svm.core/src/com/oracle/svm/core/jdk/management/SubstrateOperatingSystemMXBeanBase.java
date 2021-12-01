/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.jdk.management;

import com.oracle.svm.core.util.VMError;

/**
 * Base class for defining methods introduced after JDK 11 by JDK-8226575.
 *
 * Putting these in a class that does not implement {@link com.sun.management.OperatingSystemMXBean}
 * avoids javac errors related these methods being annotated by {@link Override}.
 */
public abstract class SubstrateOperatingSystemMXBeanBase {
    static final String MSG = "OperatingSystemMXBean methods";

    // Will be removed after [GR-20166] is implemented.
    public double getCpuLoad() {
        throw VMError.unsupportedFeature(MSG);
    }

    public abstract long getTotalPhysicalMemorySize();

    public long getTotalMemorySize() {
        return getTotalPhysicalMemorySize();
    }

    public long getFreeMemorySize() {
        throw VMError.unsupportedFeature(MSG);
    }
}
