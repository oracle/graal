/*
 * Copyright (c) 2013, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.heap;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;

import javax.management.ObjectName;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.SubstrateGCOptions;
import com.oracle.svm.core.code.RuntimeCodeInfoMemory;

import sun.management.Util;

public abstract class AbstractMemoryMXBean extends AbstractMXBean implements MemoryMXBean {

    @Platforms(Platform.HOSTED_ONLY.class)
    public AbstractMemoryMXBean() {
    }

    @Override
    public ObjectName getObjectName() {
        return Util.newObjectName(ManagementFactory.MEMORY_MXBEAN_NAME);
    }

    @Override
    @SuppressWarnings("deprecation")
    public int getObjectPendingFinalizationCount() {
        // SVM does not have any finalization support.
        return 0;
    }

    @Override
    public MemoryUsage getNonHeapMemoryUsage() {
        RuntimeCodeInfoMemory.SizeCounters counters = RuntimeCodeInfoMemory.singleton().getSizeCounters();
        long used = counters.totalSize().rawValue();
        return new MemoryUsage(UNDEFINED_MEMORY_USAGE, used, used, UNDEFINED_MEMORY_USAGE);
    }

    @Override
    public boolean isVerbose() {
        return SubstrateGCOptions.PrintGC.getValue();
    }

    @Override
    public void setVerbose(boolean value) {
        SubstrateGCOptions.PrintGC.update(value);
    }

    @Override
    public void gc() {
        System.gc();
    }
}
