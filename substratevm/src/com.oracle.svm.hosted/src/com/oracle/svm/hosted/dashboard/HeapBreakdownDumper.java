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
package com.oracle.svm.hosted.dashboard;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import com.oracle.svm.hosted.dashboard.DashboardDumpFeature.Dict;
import org.graalvm.nativeimage.hosted.Feature;

import com.oracle.svm.hosted.FeatureImpl;
import com.oracle.svm.hosted.image.NativeImageHeap;

class HeapBreakdownDumper {
    private class Statistics {
        long size = 0;
        long count = 0;
    }

    Dict dump(Feature.AfterHeapLayoutAccess access) {
        // Create the size histogram.
        FeatureImpl.AfterHeapLayoutAccessImpl config = (FeatureImpl.AfterHeapLayoutAccessImpl) access;
        NativeImageHeap heap = config.getHeap();
        final HashMap<String, Statistics> sizes = new HashMap<>();
        for (NativeImageHeap.ObjectInfo info : heap.getObjects()) {
            final String className = info.getClazz().getName();
            Statistics stats = sizes.get(className);
            if (stats == null) {
                stats = new Statistics();
                sizes.put(className, stats);
            }
            stats.size += info.getSize();
            stats.count += 1;
        }

        // Create JSON data.
        ArrayList<Object> classInfos = new ArrayList<>();
        for (Map.Entry<String, Statistics> entry : sizes.entrySet()) {
            final String className = entry.getKey();
            final Statistics stats = entry.getValue();
            Dict classInfo = new Dict();
            classInfo.insert("name", className);
            classInfo.insert("size", stats.size);
            classInfo.insert("count", stats.count);
            classInfos.add(classInfo);
        }
        Dict root = new Dict();
        root.insert("heap-size", classInfos);
        return root;
    }
}
