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
package com.oracle.graal.pointsto.flow.context.bytecode;

import com.oracle.graal.pointsto.flow.context.object.AnalysisObject;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.typestate.TypeState;
import com.oracle.graal.pointsto.util.AnalysisError;

/**
 * This is a special iterator for the type state. It iterates over analysis types and objects in
 * tandem doing a single pass over the objects array. It relies on the fact that the types and
 * objects are sorted by ID. It is meant for situations where the types need some pre-processing or
 * checking before processing their respective objects, e.g., as in virtual method resolution for
 * InvokeTypeFlow. It those situations it avoids iterating over the types first and then searching
 * for the range of objects corresponding to that type. When only objects, or only types, or only
 * objects of a certain type need to be iterated use the other provided iterators. A correct use of
 * this iterator is as follows:
 * 
 * <pre>
 * TypesObjectsIterator toi = state.getTypesObjectsIterator();
 * while (toi.hasNextType()) {
 *     AnalysisType t = toi.nextType();
 *     if (processObjectsOf(t)) {
 *         while (toi.hasNextObject(t)) {
 *             AnalysisObject o = toi.nextObject(t);
 *             // process object here
 *         }
 *     } else {
 *         toi.skipObjects(t);
 *     }
 * }
 * </pre>
 *
 * If we wanted to optimize {@link TypesObjectsIterator#skipObjects(AnalysisType)} we could keep an
 * int[] array in {@link ContextSensitiveMultiTypeState}, of size
 * {@link ContextSensitiveMultiTypeState#typesCount()} and indexed with
 * {@link TypesObjectsIterator#typeIdx}, that stores the beginning index of the next type partition
 * in {@link ContextSensitiveMultiTypeState#objects}, however profiling doesn't raise any red flags
 * for skipObjects (yet).
 */
final class TypesObjectsIterator {
    private final int typesCount;
    private final AnalysisObject[] objects;
    private int typeIdx = 0;
    private int objectIdx = 0;
    private int memoizedTypeIdx = 0;
    private int memoizedObjectIdx = 0;

    TypesObjectsIterator(TypeState state) {
        typesCount = state.typesCount();
        objects = objectsArray(state);
    }

    private static AnalysisObject[] objectsArray(TypeState state) {
        if (state.isEmpty() || state.isNull()) {
            return AnalysisObject.EMPTY_ARRAY;
        }
        if (state instanceof ContextSensitiveSingleTypeState) {
            return ((ContextSensitiveSingleTypeState) state).objects;
        }
        if (state instanceof ContextSensitiveMultiTypeState) {
            return ((ContextSensitiveMultiTypeState) state).objects;
        }
        throw AnalysisError.shouldNotReachHereUnexpectedInput(state);
    }

    /**
     * Returns true if there is a next type in the objects array, i.e., there are objects of a type
     * other than the current type.
     */
    public boolean hasNextType() {
        return typeIdx < typesCount;
    }

    /**
     * Returns true if there are more objects of the current type.
     */
    public boolean hasNextObject(AnalysisType type) {
        return objectIdx < objects.length && objects[objectIdx].getTypeId() == type.getId();
    }

    /**
     * Skip next objects of the given type. It assumes the objectIdx is already advanced to that
     * type's partition.
     */
    public void skipObjects(AnalysisType type) {
        while (this.hasNextObject(type)) {
            // skip the rest of the objects of the same type
            objectIdx++;
        }
    }

    /**
     * Gets the next type.
     */
    public AnalysisType nextType() {
        /* Check that there is a next type. */
        assert hasNextType() : typeIdx;
        /* Increment the type index. */
        typeIdx++;
        /* Return the type at the objectIdx. */
        return objects[objectIdx].type();
    }

    /**
     * Gets the next object.
     */
    public AnalysisObject nextObject(AnalysisType type) {
        /* Check that there is a next object of the desired type. */
        assert hasNextObject(type) : type;
        /* Return the next object and increment objectIdx. */
        return objects[objectIdx++];
    }

    /**
     * Memoize type and object indexes. This can be used to remember the beginning of a type's
     * partition during iteration.
     */
    public void memoizePosition() {
        memoizedTypeIdx = typeIdx;
        memoizedObjectIdx = objectIdx;
    }

    /** Reset type and object indexes to the memoized position. */
    public void reset() {
        typeIdx = memoizedTypeIdx;
        objectIdx = memoizedObjectIdx;
    }
}
