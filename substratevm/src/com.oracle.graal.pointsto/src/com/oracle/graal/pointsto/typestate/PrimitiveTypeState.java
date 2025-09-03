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
package com.oracle.graal.pointsto.typestate;

import java.util.Iterator;

import com.oracle.graal.pointsto.BigBang;
import com.oracle.graal.pointsto.PointsToAnalysis;
import com.oracle.graal.pointsto.flow.PrimitiveComparison;
import com.oracle.graal.pointsto.flow.context.object.AnalysisObject;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.util.AnalysisError;

import jdk.graal.compiler.core.common.calc.UnsignedMath;

/**
 * Represents type state for primitive values. These type states can be either concrete constants
 * represented by {@link PrimitiveConstantTypeState} or {@link AnyPrimitiveTypeState} that represent
 * any primitive value and lead to immediate saturation.
 */
public abstract sealed class PrimitiveTypeState extends TypeState permits PrimitiveConstantTypeState, AnyPrimitiveTypeState {

    protected PrimitiveTypeState() {
    }

    @Override
    public final boolean isPrimitive() {
        return true;
    }

    @Override
    public final boolean canBeNull() {
        return false;
    }

    @Override
    public boolean isEmpty() {
        return false;
    }

    public boolean isAnyPrimitive() {
        return this instanceof AnyPrimitiveTypeState;
    }

    public TypeState forUnion(PrimitiveTypeState right) {
        if (this instanceof AnyPrimitiveTypeState || right instanceof AnyPrimitiveTypeState) {
            return AnyPrimitiveTypeState.SINGLETON;
        }
        if (this instanceof PrimitiveConstantTypeState c1 && right instanceof PrimitiveConstantTypeState c2 && c1.getValue() == c2.getValue()) {
            return this;
        }
        return AnyPrimitiveTypeState.SINGLETON;
    }

    public TypeState forEquals(PrimitiveTypeState right) {
        if (this instanceof PrimitiveConstantTypeState thisConstant) {
            if (right instanceof PrimitiveConstantTypeState rightConstant && thisConstant.getValue() != rightConstant.getValue()) {
                return forEmpty();
            }
            return this;
        } else if (this instanceof AnyPrimitiveTypeState) {
            if (right instanceof PrimitiveConstantTypeState) {
                return right;
            } else {
                return this;
            }
        }
        throw AnalysisError.shouldNotReachHere("Combination not covered, this=" + this + ". right=" + right);
    }

    /** Returns a type state filtered with respect to the comparison and right. */
    public TypeState filter(PrimitiveComparison comparison, boolean isUnsigned, PrimitiveTypeState right) {
        if (comparison == PrimitiveComparison.EQ) {
            /*
             * This is the only case where we can improve even if one of the operands is Any, so we
             * handle it first.
             */
            return forEquals(right);
        }
        if (!(this instanceof PrimitiveConstantTypeState leftConstant && right instanceof PrimitiveConstantTypeState rightConstant)) {
            /*
             * Apart from EQ, we cannot handle non-constant case, so always return the original
             * value.
             */
            return this;
        }
        boolean filterToEmpty = switch (comparison) {
            case NEQ -> leftConstant.getValue() == rightConstant.getValue();
            case LT -> isUnsigned ? UnsignedMath.aboveOrEqual(leftConstant.getValue(), rightConstant.getValue()) : leftConstant.getValue() >= rightConstant.getValue();
            case GE -> isUnsigned ? UnsignedMath.belowThan(leftConstant.getValue(), rightConstant.getValue()) : leftConstant.getValue() < rightConstant.getValue();
            case GT -> isUnsigned ? UnsignedMath.belowOrEqual(leftConstant.getValue(), rightConstant.getValue()) : leftConstant.getValue() <= rightConstant.getValue();
            case LE -> isUnsigned ? UnsignedMath.aboveThan(leftConstant.getValue(), rightConstant.getValue()) : leftConstant.getValue() > rightConstant.getValue();
            default -> throw AnalysisError.shouldNotReachHere("Unreachable case in switch.");
        };
        return filterToEmpty ? forEmpty() : this;
    }

    public abstract boolean canBeTrue();

    public abstract boolean canBeFalse();

    @Override
    public abstract String toString();

    @Override
    public abstract boolean equals(Object o);

    @Override
    public int typesCount() {
        throw shouldNotReachHere();
    }

    @Override
    public AnalysisType exactType() {
        throw shouldNotReachHere();
    }

    @Override
    protected Iterator<AnalysisType> typesIterator(BigBang bb) {
        throw shouldNotReachHere();
    }

    @Override
    public boolean containsType(AnalysisType exactType) {
        throw shouldNotReachHere();
    }

    @Override
    public int objectsCount() {
        throw shouldNotReachHere();
    }

    @Override
    protected Iterator<AnalysisObject> objectsIterator(BigBang bb) {
        throw shouldNotReachHere();
    }

    @Override
    protected Iterator<AnalysisObject> objectsIterator(AnalysisType type) {
        throw shouldNotReachHere();
    }

    @Override
    public TypeState forCanBeNull(PointsToAnalysis bb, boolean stateCanBeNull) {
        throw shouldNotReachHere();
    }

    private static RuntimeException shouldNotReachHere() {
        throw AnalysisError.shouldNotReachHere("This method should never be called.");
    }
}
