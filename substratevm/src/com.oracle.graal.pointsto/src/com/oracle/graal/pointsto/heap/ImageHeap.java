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
import com.oracle.graal.pointsto.util.AnalysisError;
import com.oracle.graal.pointsto.util.AnalysisFuture;

import jdk.graal.compiler.core.common.type.CompressibleConstant;
import jdk.vm.ci.meta.JavaConstant;

/**
 * The heap snapshot. It stores all object snapshots and provides methods to access and update them.
 */
public class ImageHeap {

    /**
     * Map the original object *and* the replaced object to the same snapshot. The value is either a
     * not-yet-executed {@link AnalysisFuture} of {@link ImageHeapConstant} or its results, an
     * {@link ImageHeapConstant}. Not all objects in this cache are reachable.
     */
    private final ConcurrentHashMap<JavaConstant, /* ImageHeapConstant */ Object> objectsCache;
    /** Store a mapping from types to object snapshots. */
    private final Map<AnalysisType, Set<ImageHeapConstant>> reachableObjects;

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
        objectsCache = new ConcurrentHashMap<>();
        reachableObjects = new ConcurrentHashMap<>();
    }

    /** Get the constant snapshot from the cache. */
    public Object getSnapshot(JavaConstant constant) {
        JavaConstant uncompressed = CompressibleConstant.uncompress(constant);
        if (uncompressed instanceof ImageHeapConstant imageHeapConstant) {
            /*
             * A base layer constant was in the objectsCache from the base image. It might not have
             * been put in the objectsCache of the extension image yet.
             */
            assert imageHeapConstant.getHostedObject() == null || imageHeapConstant.isInBaseLayer() || objectsCache.get(imageHeapConstant.getHostedObject()).equals(imageHeapConstant);
            return imageHeapConstant;
        }
        return objectsCache.get(uncompressed);
    }

    /** Record the future computing the snapshot in the heap. */
    public Object setTask(JavaConstant constant, AnalysisFuture<ImageHeapConstant> task) {
        assert !(constant instanceof ImageHeapConstant) : constant;
        return objectsCache.putIfAbsent(CompressibleConstant.uncompress(constant), task);
    }

    /** Record the snapshot in the heap. */
    public void setValue(JavaConstant constant, ImageHeapConstant value) {
        assert !(constant instanceof ImageHeapConstant) : constant;
        Object previous = objectsCache.put(CompressibleConstant.uncompress(constant), value);
        AnalysisError.guarantee(!(previous instanceof ImageHeapConstant) || previous == value, "An ImageHeapConstant: %s is already registered for hosted JavaConstant: %s.", previous, constant);
    }

    public Set<ImageHeapConstant> getReachableObjects(AnalysisType type) {
        return reachableObjects.getOrDefault(type, Collections.emptySet());
    }

    public Map<AnalysisType, Set<ImageHeapConstant>> getReachableObjects() {
        return reachableObjects;
    }

    public boolean addReachableObject(AnalysisType type, ImageHeapConstant heapObj) {
        assert heapObj.isReachable() : heapObj;
        Set<ImageHeapConstant> objectSet = reachableObjects.computeIfAbsent(type, t -> ConcurrentHashMap.newKeySet());
        return objectSet.add(heapObj);
    }
}
