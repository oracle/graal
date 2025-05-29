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
package com.oracle.svm.hosted.thread;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.oracle.svm.core.imagelayer.ImageLayerBuildingSupport;
import com.oracle.svm.core.layeredimagesingleton.ImageSingletonLoader;
import com.oracle.svm.core.layeredimagesingleton.ImageSingletonWriter;
import com.oracle.svm.core.layeredimagesingleton.LayeredImageSingleton;
import com.oracle.svm.core.threadlocal.FastThreadLocal;
import com.oracle.svm.core.threadlocal.VMThreadLocalInfo;
import com.oracle.svm.core.threadlocal.VMThreadLocalInfos;
import com.oracle.svm.core.threadlocal.VMThreadLocalSupport;
import com.oracle.svm.core.util.VMError;

import jdk.graal.compiler.debug.Assertions;

/**
 * When building layered images we assume all used thread locals will be seen within the base layer.
 * Subsequent layers then need to be assigned the same offset. In addition, we match based on the
 * name assigned to ThreadLocals, so this must be unique.
 *
 * Note it is possible to relax this constraint of only allowing thread locals to be defined in the
 * initial layer. However, doing so will require adjusting {@link VMThreadLocalInfos} to be a
 * multi-layered singleton and also {@link VMThreadLocalSupport} to likely be an application layer
 * only image singleton.
 */
public class LayeredVMThreadLocalCollector extends VMThreadLocalCollector implements LayeredImageSingleton {

    record ThreadInfo(int size, int offset) {

    }

    final Map<String, ThreadInfo> threadLocalAssignmentMap;
    private final boolean initialLayer;
    private int nextOffset;

    public LayeredVMThreadLocalCollector() {
        this(null, -1);
    }

    private LayeredVMThreadLocalCollector(Map<String, ThreadInfo> threadLocalAssignmentMap, int nextOffset) {
        super(true);

        this.threadLocalAssignmentMap = threadLocalAssignmentMap;
        initialLayer = ImageLayerBuildingSupport.buildingInitialLayer();
        this.nextOffset = nextOffset;
    }

    @Override
    public Object apply(Object source) {
        /*
         * Make sure all names have been assigned in the prior layers
         */
        if (!initialLayer) {
            if (source instanceof FastThreadLocal threadLocal) {
                var name = threadLocal.getName();
                VMError.guarantee(threadLocalAssignmentMap.containsKey(name), "Found thread local which was not created in the initial layer %s", name);
            }
        }
        return super.apply(source);
    }

    @Override
    public int sortAndAssignOffsets() {
        if (initialLayer) {
            assert nextOffset == -1 : nextOffset;

            nextOffset = super.sortAndAssignOffsets();
        } else {
            assert nextOffset != -1;

            for (VMThreadLocalInfo info : threadLocals.values()) {
                var assignment = threadLocalAssignmentMap.get(info.name);
                info.offset = assignment.offset();
                assert assignment.size() == calculateSize(info) : Assertions.errorMessage("Mismatch in computed size: ", assignment.size(), calculateSize(info), info.name);
                info.sizeInBytes = assignment.size();
            }
        }

        return nextOffset;
    }

    @Override
    public int getOffset(FastThreadLocal threadLocal) {
        if (initialLayer) {
            return super.getOffset(threadLocal);
        } else {
            return threadLocalAssignmentMap.get(threadLocal.getName()).offset();
        }
    }

    @Override
    public PersistFlags preparePersist(ImageSingletonWriter writer) {
        /*
         * Store the (name, offset, size) tuple of all thread locals.
         */
        List<String> threadLocalNames = new ArrayList<>();
        List<Integer> threadLocalOffsets = new ArrayList<>();
        List<Integer> threadLocalSizes = new ArrayList<>();
        if (initialLayer) {
            for (var threadLocal : getSortedThreadLocalInfos()) {
                threadLocalNames.add(threadLocal.name);
                threadLocalOffsets.add(threadLocal.offset);
                threadLocalSizes.add(threadLocal.sizeInBytes);
            }
        } else {
            for (var entry : threadLocalAssignmentMap.entrySet()) {
                threadLocalNames.add(entry.getKey());
                threadLocalOffsets.add(entry.getValue().offset());
                threadLocalSizes.add(entry.getValue().size());
            }
        }

        writer.writeStringList("threadLocalNames", threadLocalNames);
        writer.writeIntList("threadLocalOffsets", threadLocalOffsets);
        writer.writeIntList("threadLocalSizes", threadLocalSizes);

        /*
         * Note while it is not strictly necessary to store nextOffset at the moment, if in the
         * future we allow multiple layers to define thread locals then this information will need
         * to be propagated.
         */
        writer.writeInt("nextOffset", nextOffset);
        return PersistFlags.CREATE;
    }

    @SuppressWarnings("unused")
    public static Object createFromLoader(ImageSingletonLoader loader) {
        /*
         * Load the (name, offset, size) tuple of all thread locals.
         */
        HashMap<String, ThreadInfo> threadLocalAssignmentMap = new HashMap<>();
        Iterator<String> threadLocalNames = loader.readStringList("threadLocalNames").iterator();
        Iterator<Integer> threadLocalOffsets = loader.readIntList("threadLocalOffsets").iterator();
        Iterator<Integer> threadLocalSizes = loader.readIntList("threadLocalSizes").iterator();

        while (threadLocalNames.hasNext()) {
            String threadLocalName = threadLocalNames.next();
            int threadLocalOffset = threadLocalOffsets.next();
            int threadLocalSize = threadLocalSizes.next();

            var previous = threadLocalAssignmentMap.put(threadLocalName, new ThreadInfo(threadLocalSize, threadLocalOffset));
            assert previous == null : previous;
        }

        return new LayeredVMThreadLocalCollector(Map.copyOf(threadLocalAssignmentMap), loader.readInt("nextOffset"));
    }
}
