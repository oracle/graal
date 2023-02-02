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
package com.oracle.svm.core.genscavenge.jvmstat;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.genscavenge.GCImpl;
import com.oracle.svm.core.genscavenge.HeapImpl;
import com.oracle.svm.core.genscavenge.jvmstat.SerialGCPerfData.PerfDataGeneration;
import com.oracle.svm.core.genscavenge.jvmstat.SerialGCPerfData.SpacePerfData;
import com.oracle.svm.core.jvmstat.PerfDataHolder;

public class EpsilonGCPerfData implements PerfDataHolder {
    private final SpacePerfData space;
    private final PerfDataGeneration generation;

    @Platforms(Platform.HOSTED_ONLY.class)
    public EpsilonGCPerfData() {
        space = new SpacePerfData(1, 0);
        generation = new PerfDataGeneration(1, new SpacePerfData[]{space});
    }

    @Override
    public void allocate() {
        generation.allocate("Heap");
        space.allocate("Heap");
    }

    @Override
    public void update() {
        long maxCapacity = GCImpl.getPolicy().getMaximumHeapSize().rawValue();
        long usedBytes = HeapImpl.getHeapImpl().getAccounting().getEdenUsedBytes().rawValue();

        space.used.setValue(usedBytes);
        generation.capacity.setValue(usedBytes);
        generation.maxCapacity.setValue(maxCapacity);
    }
}
