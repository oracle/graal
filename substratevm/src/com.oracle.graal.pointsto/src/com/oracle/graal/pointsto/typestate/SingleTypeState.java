/*
 * Copyright (c) 2013, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.pointsto.typestate;

import java.util.Collections;
import java.util.Iterator;

import com.oracle.graal.pointsto.BigBang;
import com.oracle.graal.pointsto.PointsToAnalysis;
import com.oracle.graal.pointsto.flow.context.object.AnalysisObject;
import com.oracle.graal.pointsto.meta.AnalysisType;

public class SingleTypeState extends TypeState {
    protected final AnalysisType type;
    /** Can this type state represent the null value? */
    protected final boolean canBeNull;
    /** Has this type state been merged with the all-instantiated type state? */
    protected boolean merged;

    /** Creates a new type state from incoming objects. */
    public SingleTypeState(boolean canBeNull, AnalysisType type) {
        this.type = type;
        this.canBeNull = canBeNull;
        this.merged = false;
    }

    /** Create a type state with the same content and a reversed canBeNull value. */
    protected SingleTypeState(boolean canBeNull, SingleTypeState other) {
        this.type = other.type;
        this.canBeNull = canBeNull;
        this.merged = other.merged;
    }

    @Override
    public final int typesCount() {
        return 1;
    }

    @Override
    public int objectsCount() {
        return 1;
    }

    @Override
    public final boolean canBeNull() {
        return canBeNull;
    }

    @Override
    public final AnalysisType exactType() {
        return type;
    }

    @Override
    public final boolean containsType(AnalysisType exactType) {
        return type.equals(exactType);
    }

    @Override
    protected final Iterator<AnalysisType> typesIterator(BigBang bb) {
        return singletonIterator(type);
    }

    @Override
    public Iterator<AnalysisObject> objectsIterator(BigBang bb) {
        return singletonIterator(type.getContextInsensitiveAnalysisObject());
    }

    @Override
    protected Iterator<AnalysisObject> objectsIterator(AnalysisType t) {
        return type.equals(t) ? singletonIterator(type.getContextInsensitiveAnalysisObject()) : Collections.emptyIterator();
    }

    protected static <T> Iterator<T> singletonIterator(T object) {
        return new Iterator<>() {
            boolean hasNext = true;

            @Override
            public boolean hasNext() {
                return hasNext;
            }

            @Override
            public T next() {
                hasNext = false;
                return object;
            }
        };
    }

    @Override
    public TypeState forCanBeNull(PointsToAnalysis bb, boolean stateCanBeNull) {
        if (stateCanBeNull == this.canBeNull()) {
            return this;
        } else {
            return PointsToStats.registerTypeState(bb, new SingleTypeState(stateCanBeNull, this));
        }
    }

    /** Note that the objects of this type state have been merged. */
    @Override
    public void noteMerge(PointsToAnalysis bb) {
        assert bb.analysisPolicy().isMergingEnabled() : "policy mismatch";

        if (!merged) {
            type.getContextInsensitiveAnalysisObject().noteMerge(bb);
            merged = true;
        }
    }

    @Override
    public boolean isMerged() {
        return merged;
    }

    @Override
    public int hashCode() {
        int result = 1;
        result = 31 * result + type.hashCode();
        result = 31 * result + (canBeNull ? 1 : 0);
        return result;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        SingleTypeState that = (SingleTypeState) o;
        return this.canBeNull == that.canBeNull && this.exactType().equals(that.exactType());
    }

    @Override
    public String toString() {
        return "SingleType<" + type.getName() + ", " + (canBeNull ? "null," : "") + ">";
    }

}
