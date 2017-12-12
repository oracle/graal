/*
 * Copyright (c) 2013, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.util;

import static com.oracle.svm.core.util.VMError.shouldNotReachHere;
import static com.oracle.svm.core.util.VMError.unimplemented;

import java.nio.ByteBuffer;

import jdk.vm.ci.meta.JavaConstant;

public class PrimitiveWriteUtils {

    public static void writePrimitive(ByteBuffer bb, int index, JavaConstant con) {
        switch (con.getJavaKind()) {
            case Boolean:
                bb.put(index, (byte) con.asInt());
                break;
            case Byte:
                bb.put(index, (byte) con.asInt());
                break;
            case Char:
                bb.putChar(index, (char) con.asInt());
                break;
            case Short:
                bb.putShort(index, (short) con.asInt());
                break;
            case Int:
                bb.putInt(index, con.asInt());
                break;
            case Long:
                bb.putLong(index, con.asLong());
                break;
            case Float:
                bb.putFloat(index, con.asFloat());
                break;
            case Double:
                bb.putDouble(index, con.asDouble());
                break;
            default:
                throw shouldNotReachHere(con.getJavaKind().toString());
        }
    }

    public static void writePointer(ByteBuffer bb, int index, long pointerValue, int pointerSize) {
        if (pointerSize == 8) {
            bb.putLong(index, pointerValue);
        } else {
            throw unimplemented();
        }
    }
}
