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
 * <code>
 * TypesObjectsIterator toi = state.getTypesObjectsIterator();
 * <p>
 * while(toi.hasNextType()) {
 * AnalysisType t = toi.nextType();
 * // use type here
 * <p>
 * while(toi.hasNextObject(t)) {
 * AnalysisObject o = toi.nextObject(t);
 * // use object here
 * }
 * }
 * </code>
 */
final class TypesObjectsIterator {
    private final int typesCount;
    private final AnalysisObject[] objects;
    private int typeIdx = 0;
    private int objectIdx = 0;

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
        throw AnalysisError.shouldNotReachHere();
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
     * Gets the next type.
     */
    public AnalysisType nextType() {
        /* Check that there is a next type. */
        assert hasNextType();
        /* Increment the type index. */
        typeIdx++;
        /* Return the type at the 'objectIdx. */
        return objects[objectIdx].type();
    }

    /**
     * Gets the next object.
     */
    public AnalysisObject nextObject(AnalysisType type) {
        /* Check that there is a next object of the desired type. */
        assert hasNextObject(type);
        /* Return the next object and increment objectIdx. */
        return objects[objectIdx++];
    }
}
