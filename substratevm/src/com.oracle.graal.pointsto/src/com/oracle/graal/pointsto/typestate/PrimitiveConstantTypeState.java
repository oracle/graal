/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Objects;

import com.oracle.graal.pointsto.PointsToAnalysis;

/**
 * Represents a primitive constant that is propagated through the type flow graph. Instances for
 * corresponding primitive values are accessible via a factory method
 * {@link TypeState#forPrimitiveConstant }.
 */
public final class PrimitiveConstantTypeState extends PrimitiveTypeState {

    private static final int CACHE_SIZE = 16;

    private static final PrimitiveConstantTypeState[] CACHE = new PrimitiveConstantTypeState[CACHE_SIZE];

    static {
        for (int i = 0; i < CACHE_SIZE; i++) {
            CACHE[i] = new PrimitiveConstantTypeState(i);
        }
    }

    public static void registerCachedTypeStates(PointsToAnalysis bb) {
        for (var typeState : CACHE) {
            PointsToStats.registerTypeState(bb, typeState);
        }
    }

    private final long value;

    public static TypeState forValue(PointsToAnalysis bb, long value) {
        if (value >= 0 && value < CACHE_SIZE) {
            return CACHE[(int) value];
        }
        return PointsToStats.registerTypeState(bb, new PrimitiveConstantTypeState(value));
    }

    private PrimitiveConstantTypeState(long value) {
        this.value = value;
    }

    public long getValue() {
        return value;
    }

    @Override
    public boolean canBeTrue() {
        return value != 0;
    }

    @Override
    public boolean canBeFalse() {
        return value == 0;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        PrimitiveConstantTypeState that = (PrimitiveConstantTypeState) o;
        return value == that.value;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), value);
    }

    @Override
    public String toString() {
        return "PrimitiveConstantTypeState(" + value + ")";
    }
}
