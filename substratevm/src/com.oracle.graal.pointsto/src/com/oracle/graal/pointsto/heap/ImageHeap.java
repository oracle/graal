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
package com.oracle.graal.pointsto.heap;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.util.AnalysisFuture;

import jdk.vm.ci.meta.JavaConstant;

public class ImageHeap {

    /** Map the original object *and* the replaced object to the HeapObject snapshot. */
    protected ConcurrentHashMap<JavaConstant, AnalysisFuture<ImageHeapObject>> heapObjects;
    /** Store a mapping from types to all scanned objects. */
    protected Map<AnalysisType, Set<ImageHeapObject>> typesToObjects;

    public ImageHeap() {
        heapObjects = new ConcurrentHashMap<>();
        typesToObjects = new ConcurrentHashMap<>();
    }

    public AnalysisFuture<ImageHeapObject> getTask(JavaConstant constant) {
        return heapObjects.get(constant);
    }

    public AnalysisFuture<ImageHeapObject> addTask(JavaConstant constant, AnalysisFuture<ImageHeapObject> object) {
        return heapObjects.putIfAbsent(constant, object);
    }

    public Set<ImageHeapObject> getObjects(AnalysisType type) {
        return typesToObjects.getOrDefault(type, Collections.emptySet());
    }

    public boolean add(AnalysisType type, ImageHeapObject heapObj) {
        Set<ImageHeapObject> objectSet = typesToObjects.computeIfAbsent(type, t -> ConcurrentHashMap.newKeySet());
        return objectSet.add(heapObj);
    }
}
