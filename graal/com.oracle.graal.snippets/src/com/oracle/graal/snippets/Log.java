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
package com.oracle.graal.snippets;

import java.io.*;

import com.oracle.graal.api.code.RuntimeCall.Descriptor;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.graph.Node.ConstantNodeParameter;
import com.oracle.graal.graph.Node.NodeIntrinsic;
import com.oracle.graal.nodes.extended.*;

//JaCoCo Exclude

/**
 * Provides {@link PrintStream}-like logging facility. This should only be used in {@linkplain Snippet snippets}.
 */
public final class Log {

    public static final Descriptor LOG_PRIMITIVE = new Descriptor("logPrimitive", false, Kind.Void, Kind.Int, Kind.Long, Kind.Boolean);
    public static final Descriptor LOG_OBJECT = new Descriptor("logObject", false, Kind.Void, Kind.Object, Kind.Int);
    public static final Descriptor LOG_PRINTF = new Descriptor("logPrintf", false, Kind.Void, Kind.Object, Kind.Long);

    // Note: Must be kept in sync with constants in c1_Runtime1.hpp
    private static final int LOG_OBJECT_NEWLINE = 0x01;
    private static final int LOG_OBJECT_STRING  = 0x02;
    private static final int LOG_OBJECT_ADDRESS = 0x04;

    @NodeIntrinsic(RuntimeCallNode.class)
    private static native void log(@ConstantNodeParameter Descriptor logObject, Object object, int flags);

    @NodeIntrinsic(RuntimeCallNode.class)
    private static native void log(@ConstantNodeParameter Descriptor logPrimitive, int typeChar, long value, boolean newline);

    @NodeIntrinsic(RuntimeCallNode.class)
    private static native void printf(@ConstantNodeParameter Descriptor logPrintf, String format, long value);

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
     * @param format a C style printf format value that can contain at most one conversion specifier (i.e., a sequence
     *            of characters starting with '%').
     */
    public static void printf(String format, long value) {
        printf(LOG_PRINTF, format, value);
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
