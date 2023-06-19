/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.libgraal.jni.annotation;

final class Utilities {

    private Utilities() {
    }

    static void encodeType(Class<?> type, StringBuilder buf) {
        String desc;
        if (type == boolean.class) {
            desc = "Z";
        } else if (type == byte.class) {
            desc = "B";
        } else if (type == char.class) {
            desc = "C";
        } else if (type == short.class) {
            desc = "S";
        } else if (type == int.class) {
            desc = "I";
        } else if (type == long.class) {
            desc = "J";
        } else if (type == float.class) {
            desc = "F";
        } else if (type == double.class) {
            desc = "D";
        } else if (type == void.class) {
            desc = "V";
        } else if (type.isArray()) {
            buf.append('[');
            encodeType(type.getComponentType(), buf);
            return;
        } else {
            desc = "L" + type.getName().replace('.', '/') + ";";
        }
        buf.append(desc);
    }
}
