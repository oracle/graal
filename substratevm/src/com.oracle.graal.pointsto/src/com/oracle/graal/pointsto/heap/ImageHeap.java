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

/**
 * The heap snapshot. It stores all object snapshots and provides methods to access and update them.
 */
public class ImageHeap {

    /**
     * Map the original object *and* the replaced object to the same snapshot. The value is either a
     * not-yet-executed {@link AnalysisFuture} of {@link ImageHeapConstant} or its results, an
     * {@link ImageHeapConstant}.
     */
    private final ConcurrentHashMap<JavaConstant, /* ImageHeapObject */ Object> heapObjects;
    /** Store a mapping from types to object snapshots. */
    private final Map<AnalysisType, Set<ImageHeapConstant>> typesToObjects;

    /*
     * Note on the idea of merging the heapObjects and typesToObjects maps:
     * 
     * - heapObjects maps both the original and the replaced JavaConstant objects to the
     * corresponding ImageHeapObject (which also wraps the replaced JavaConstant).
     * 
     * - typesToObjects maps the AnalysisType of the replaced objects to the collection of
     * corresponding ImageHeapObject
     * 
     * - If we were to combine the two into a Map<AnalysisType, Map<JavaConstant, Object>> which
     * type do we use as a key? That would be the type of the JavaConstant object, but that doesn't
     * always match with the type of the ImageHeapObject in case of object replacers that change
     * type. This can lead to issues when trying to iterate objects of a specific type, e.g., to
     * walk its fields. We could use the type of the replaced object wrapped in the ImageHeapObject,
     * but then we wouldn't have a direct way to get from the original JavaConstant to the
     * corresponding ImageHeapObject. Which means that we would need to run the replacers *before*
     * checking if we already have a registered ImageHeapObject task (in
     * ImageHeapScanner.getOrCreateConstantReachableTask()), so may end up running the replacers
     * more often, otherwise we can end up with duplicated ImageHeapObject snapshots.
     */

    public ImageHeap() {
        heapObjects = new ConcurrentHashMap<>();
        typesToObjects = new ConcurrentHashMap<>();
    }

    /** Record the future computing the snapshot or its result. */
    public Object getTask(JavaConstant constant) {
        return heapObjects.get(constant);
    }

    /** Record the future computing the snapshot in the heap. */
    public Object setTask(JavaConstant constant, AnalysisFuture<ImageHeapConstant> task) {
        return heapObjects.putIfAbsent(constant, task);
    }

    /** Record the snapshot in the heap. */
    public void setValue(JavaConstant constant, ImageHeapConstant value) {
        heapObjects.put(constant, value);
    }

    public Set<ImageHeapConstant> getObjects(AnalysisType type) {
        return typesToObjects.getOrDefault(type, Collections.emptySet());
    }

    public boolean add(AnalysisType type, ImageHeapConstant heapObj) {
        Set<ImageHeapConstant> objectSet = typesToObjects.computeIfAbsent(type, t -> ConcurrentHashMap.newKeySet());
        return objectSet.add(heapObj);
    }
}
