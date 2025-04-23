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
package com.oracle.graal.pointsto.typestate;

import java.util.Collections;
import java.util.Iterator;
import java.util.Objects;

import com.oracle.graal.pointsto.BigBang;
import com.oracle.graal.pointsto.PointsToAnalysis;
import com.oracle.graal.pointsto.flow.context.object.AnalysisObject;
import com.oracle.graal.pointsto.heap.ImageHeapRelocatableConstant;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.util.AnalysisError;

import jdk.vm.ci.meta.JavaConstant;

/**
 * Models a {@link JavaConstant} flowing through the data flow graph. As soon as this constant is
 * merged with any other object (even of the same type) it is erased and the state falls back to a
 * {@link SingleTypeState}.
 */
public class ConstantTypeState extends SingleTypeState {
    /** The constant object of this type state. */
    protected final JavaConstant constant;

    /** Creates a new type state from incoming objects. */
    public ConstantTypeState(PointsToAnalysis bb, AnalysisType type, JavaConstant constant) {
        super(false, type);
        assert !bb.analysisPolicy().isContextSensitiveAnalysis() : "The ConstantTypeState is indented to be used with a context insensitive analysis.";
        assert !(constant instanceof ImageHeapRelocatableConstant) : "relocatable constants have an unknown state and should not be represented by a constant type state: " + constant;
        this.constant = Objects.requireNonNull(constant);
    }

    /** Create a type state with the same content and a reversed canBeNull value. */
    protected ConstantTypeState(boolean canBeNull, ConstantTypeState other) {
        super(canBeNull, other);
        this.constant = other.constant;
    }

    public JavaConstant getConstant() {
        return constant;
    }

    @Override
    public TypeState forCanBeNull(PointsToAnalysis bb, boolean stateCanBeNull) {
        if (stateCanBeNull == this.canBeNull()) {
            return this;
        } else {
            return PointsToStats.registerTypeState(bb, new ConstantTypeState(stateCanBeNull, this));
        }
    }

    /**
     * Although this state wraps a {@link JavaConstant}, from the point of view of data flow
     * modeling in the context-insensitive analysis it is indistinguishable from the
     * context-insensitive analysis object corresponding to its type. More concretely this means
     * that it shares the same field and array flows, i.e., it doesn't track field and array
     * load/stores separately. This allows us to propagate constants through the type flows as
     * values, without changing the shape of the data flow graphs, thus avoiding significant
     * overhead.
     */
    private AnalysisObject getAnalysisObject() {
        return type.getContextInsensitiveAnalysisObject();
    }

    @Override
    public Iterator<AnalysisObject> objectsIterator(BigBang bb) {
        return singletonIterator(getAnalysisObject());
    }

    @Override
    protected Iterator<AnalysisObject> objectsIterator(AnalysisType t) {
        return type.equals(t) ? singletonIterator(getAnalysisObject()) : Collections.emptyIterator();
    }

    @Override
    public void noteMerge(PointsToAnalysis bb) {
        AnalysisError.shouldNotReachHere("ConstantTypeState doesn't support merging. It is indented to be used with a context insensitive analysis.");
    }

    @Override
    public JavaConstant asConstant() {
        return constant;
    }

    @Override
    public int hashCode() {
        return constant.hashCode() * 31;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj instanceof ConstantTypeState) {
            ConstantTypeState that = (ConstantTypeState) obj;
            return this.canBeNull == that.canBeNull && this.merged == that.merged &&
                            this.exactType().equals(that.exactType()) && Objects.equals(this.constant, that.constant);
        }
        return false;
    }

    @Override
    public String toString() {
        return "ConstantObject<" + constant.toString() + ">";
    }
}
