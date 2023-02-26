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
 * Provides a typesafe enumeration for describing units of measurement attribute for instrumentation
 * objects.
 */
public enum PerfUnit {

    /*
     * The enumeration values for this typesafe enumeration must be kept in synchronization with the
     * Units enum in the perfData.hpp file in the HotSpot source base.
     */

    /**
     * An Invalid Units value.
     */
    INVALID("Invalid", 0),

    /**
     * Units attribute representing unit-less quantities.
     */
    NONE("None", 1),

    /**
     * Units attribute representing Bytes.
     */
    BYTES("Bytes", 2),

    /**
     * Units attribute representing Ticks.
     */
    TICKS("Ticks", 3),

    /**
     * Units attribute representing a count of events.
     */
    EVENTS("Events", 4),

    /**
     * Units attribute representing String data. Although not really a unit of measure, this Units
     * value serves to distinguish String instrumentation objects from instrumentation objects of
     * other types.
     */
    STRING("String", 5),

    /**
     * Units attribute representing Hertz (frequency).
     */
    HERTZ("Hertz", 6);

    private final String name;
    private final int value;

    /**
     * Returns a string describing this Unit of measurement attribute.
     *
     * @return String - a descriptive string for this enum.
     */
    @Override
    public String toString() {
        return name;
    }

    /**
     * Returns the integer representation of this Units attribute.
     *
     * @return int - an integer representation of this Units attribute.
     */
    public int intValue() {
        return value;
    }

    public static PerfUnit fromInt(int value) {
        switch (value) {
            case 1:
                return NONE;
            case 2:
                return BYTES;
            case 3:
                return TICKS;
            case 4:
                return EVENTS;
            case 5:
                return STRING;
            case 6:
                return HERTZ;
            default:
                return INVALID;
        }
    }

    PerfUnit(String name, int value) {
        this.name = name;
        this.value = value;
    }
}
