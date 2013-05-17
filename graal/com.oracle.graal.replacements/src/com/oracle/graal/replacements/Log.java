/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.graal.replacements;

import java.io.*;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.graph.Node.ConstantNodeParameter;
import com.oracle.graal.graph.Node.NodeIntrinsic;
import com.oracle.graal.nodes.extended.*;

//JaCoCo Exclude

/**
 * Provides {@link PrintStream}-like logging facility.
 */
public final class Log {

    public static final ForeignCallDescriptor LOG_PRIMITIVE = new ForeignCallDescriptor("logPrimitive", void.class, int.class, long.class, boolean.class);
    public static final ForeignCallDescriptor LOG_OBJECT = new ForeignCallDescriptor("logObject", void.class, Object.class, int.class);
    public static final ForeignCallDescriptor LOG_PRINTF = new ForeignCallDescriptor("logPrintf", void.class, Object.class, long.class, long.class, long.class);

    // Note: Must be kept in sync with constants in graalRuntime.hpp
    private static final int LOG_OBJECT_NEWLINE = 0x01;
    private static final int LOG_OBJECT_STRING = 0x02;
    private static final int LOG_OBJECT_ADDRESS = 0x04;

    @NodeIntrinsic(ForeignCallNode.class)
    private static native void log(@ConstantNodeParameter ForeignCallDescriptor logObject, Object object, int flags);

    @NodeIntrinsic(ForeignCallNode.class)
    private static native void log(@ConstantNodeParameter ForeignCallDescriptor logPrimitive, int typeChar, long value, boolean newline);

    @NodeIntrinsic(ForeignCallNode.class)
    private static native void printf(@ConstantNodeParameter ForeignCallDescriptor logPrintf, String format, long v1, long v2, long v3);

    public static void print(boolean value) {
        log(LOG_PRIMITIVE, Kind.Boolean.getTypeChar(), value ? 1L : 0L, false);
    }

    public static void print(byte value) {
        log(LOG_PRIMITIVE, Kind.Byte.getTypeChar(), value, false);
    }

    public static void print(char value) {
        log(LOG_PRIMITIVE, Kind.Char.getTypeChar(), value, false);
    }

    public static void print(short value) {
        log(LOG_PRIMITIVE, Kind.Short.getTypeChar(), value, false);
    }

    public static void print(int value) {
        log(LOG_PRIMITIVE, Kind.Int.getTypeChar(), value, false);
    }

    public static void print(long value) {
        log(LOG_PRIMITIVE, Kind.Long.getTypeChar(), value, false);
    }

    /**
     * Prints a formatted string to the log stream.
     * 
     * @param format a C style printf format value that can contain at most one conversion specifier
     *            (i.e., a sequence of characters starting with '%').
     * @param value the value associated with the conversion specifier
     */
    public static void printf(String format, long value) {
        printf(LOG_PRINTF, format, value, 0L, 0L);
    }

    public static void printf(String format, long v1, long v2) {
        printf(LOG_PRINTF, format, v1, v2, 0L);
    }

    public static void printf(String format, long v1, long v2, long v3) {
        printf(LOG_PRINTF, format, v1, v2, v3);
    }

    public static void print(float value) {
        if (Float.isNaN(value)) {
            print("NaN");
        } else if (value == Float.POSITIVE_INFINITY) {
            print("Infinity");
        } else if (value == Float.NEGATIVE_INFINITY) {
            print("-Infinity");
        } else {
            log(LOG_PRIMITIVE, Kind.Float.getTypeChar(), Float.floatToRawIntBits(value), false);
        }
    }

    public static void print(double value) {
        if (Double.isNaN(value)) {
            print("NaN");
        } else if (value == Double.POSITIVE_INFINITY) {
            print("Infinity");
        } else if (value == Double.NEGATIVE_INFINITY) {
            print("-Infinity");
        } else {
            log(LOG_PRIMITIVE, Kind.Double.getTypeChar(), Double.doubleToRawLongBits(value), false);
        }
    }

    public static void print(String value) {
        log(LOG_OBJECT, value, LOG_OBJECT_STRING);
    }

    public static void printAddress(Object o) {
        log(LOG_OBJECT, o, LOG_OBJECT_ADDRESS);
    }

    public static void printObject(Object o) {
        log(LOG_OBJECT, o, 0);
    }

    public static void println(boolean value) {
        log(LOG_PRIMITIVE, Kind.Boolean.getTypeChar(), value ? 1L : 0L, true);
    }

    public static void println(byte value) {
        log(LOG_PRIMITIVE, Kind.Byte.getTypeChar(), value, true);
    }

    public static void println(char value) {
        log(LOG_PRIMITIVE, Kind.Char.getTypeChar(), value, true);
    }

    public static void println(short value) {
        log(LOG_PRIMITIVE, Kind.Short.getTypeChar(), value, true);
    }

    public static void println(int value) {
        log(LOG_PRIMITIVE, Kind.Int.getTypeChar(), value, true);
    }

    public static void println(long value) {
        log(LOG_PRIMITIVE, Kind.Long.getTypeChar(), value, true);
    }

    public static void println(float value) {
        if (Float.isNaN(value)) {
            println("NaN");
        } else if (value == Float.POSITIVE_INFINITY) {
            println("Infinity");
        } else if (value == Float.NEGATIVE_INFINITY) {
            println("-Infinity");
        } else {
            log(LOG_PRIMITIVE, Kind.Float.getTypeChar(), Float.floatToRawIntBits(value), true);
        }
    }

    public static void println(double value) {
        if (Double.isNaN(value)) {
            println("NaN");
        } else if (value == Double.POSITIVE_INFINITY) {
            println("Infinity");
        } else if (value == Double.NEGATIVE_INFINITY) {
            println("-Infinity");
        } else {
            log(LOG_PRIMITIVE, Kind.Double.getTypeChar(), Double.doubleToRawLongBits(value), true);
        }
    }

    public static void println(String value) {
        log(LOG_OBJECT, value, LOG_OBJECT_NEWLINE | LOG_OBJECT_STRING);
    }

    public static void printlnAddress(Object o) {
        log(LOG_OBJECT, o, LOG_OBJECT_NEWLINE | LOG_OBJECT_ADDRESS);
    }

    public static void printlnObject(Object o) {
        log(LOG_OBJECT, o, LOG_OBJECT_NEWLINE);
    }

    public static void println() {
        println("");
    }
}
