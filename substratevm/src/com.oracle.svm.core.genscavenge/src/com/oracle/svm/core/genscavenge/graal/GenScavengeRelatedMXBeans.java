/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.genscavenge.graal;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.GCRelatedMXBeans;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.genscavenge.CompleteGarbageCollectorMXBean;
import com.oracle.svm.core.genscavenge.EpsilonGarbageCollectorMXBean;
import com.oracle.svm.core.genscavenge.GenScavengeMemoryPoolMXBeans;
import com.oracle.svm.core.genscavenge.HeapImplMemoryMXBean;
import com.oracle.svm.core.genscavenge.IncrementalGarbageCollectorMXBean;
import com.sun.management.GarbageCollectorMXBean;

public final class GenScavengeRelatedMXBeans extends GCRelatedMXBeans {
    @Platforms(Platform.HOSTED_ONLY.class)
    public GenScavengeRelatedMXBeans(GenScavengeMemoryPoolMXBeans memoryPoolMXBeans) {
        List<GarbageCollectorMXBean> garbageCollectors;
        if (SubstrateOptions.useEpsilonGC()) {
            garbageCollectors = List.of(new EpsilonGarbageCollectorMXBean());
        } else {
            garbageCollectors = Arrays.asList(new IncrementalGarbageCollectorMXBean(), new CompleteGarbageCollectorMXBean());
        }

        beans.addSingleton(java.lang.management.MemoryMXBean.class, new HeapImplMemoryMXBean());
        beans.addList(java.lang.management.MemoryPoolMXBean.class, Arrays.asList(memoryPoolMXBeans.getMXBeans()));
        beans.addList(java.lang.management.BufferPoolMXBean.class, Collections.emptyList());
        beans.addList(com.sun.management.GarbageCollectorMXBean.class, garbageCollectors);
    }
}
