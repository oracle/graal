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
package com.oracle.svm.core.jvmstat;

/**
 * Provides a typesafe enumeration for the Variability attribute for instrumentation objects.
 */
public enum PerfVariability {

    /*
     * The enumeration values for this typesafe enumeration must be kept in synchronization with the
     * Variability enum in the perfData.hpp file in the HotSpot source base.
     */

    /**
     * An invalid Variablity value.
     */
    INVALID("Invalid", 0),

    /**
     * Variability attribute representing Constant counters.
     */
    CONSTANT("Constant", 1),

    /**
     * Variability attribute representing a Monotonically changing counters.
     */
    MONOTONIC("Monotonic", 2),

    /**
     * Variability attribute representing Variable counters.
     */
    VARIABLE("Variable", 3);

    private final String name;
    private final int value;

    /**
     * Returns a string describing this Variability attribute.
     *
     * @return String - a descriptive string for this enum.
     */
    @Override
    public String toString() {
        return name;
    }

    /**
     * Returns the integer representation of this Variability attribute.
     *
     * @return int - an integer representation of this Variability attribute.
     */
    public int intValue() {
        return value;
    }

    public static PerfVariability fromInt(int value) {
        switch (value) {
            case 1:
                return CONSTANT;
            case 2:
                return MONOTONIC;
            case 3:
                return VARIABLE;
            default:
                return INVALID;
        }
    }

    PerfVariability(String name, int value) {
        this.name = name;
        this.value = value;
    }
}
