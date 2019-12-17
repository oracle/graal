/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.jni.NativeEnv;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.JavaKind;
import com.oracle.truffle.nfi.spi.types.NativeSimpleType;

public final class Utils {

    public static NativeSimpleType kindToType(JavaKind kind) {
        switch (kind) {
            case Boolean:
                return NativeSimpleType.SINT8; // ?
            case Short:
                return NativeSimpleType.SINT16;
            case Char:
                return NativeSimpleType.SINT16;
            case Long:
                return NativeSimpleType.SINT64;
            case Float:
                return NativeSimpleType.FLOAT;
            case Double:
                return NativeSimpleType.DOUBLE;
            case Int:
                return NativeSimpleType.SINT32;
            case Byte:
                return NativeSimpleType.SINT8;
            case Void:
                return NativeSimpleType.VOID;
            case Object:
                // TODO(peterssen): We don't want Interop null passed verbatim to native, but native
                // NULL instead.
                return NativeEnv.word();
            default:
                throw EspressoError.shouldNotReachHere();
        }
    }

    public static List<Path> parsePaths(String paths) {
        List<Path> list = new ArrayList<>();
        for (String p : paths.split(File.pathSeparator)) {
            list.add(Paths.get(p));
        }
        return list;
    }

    public static String stringify(List<Path> paths) {
        StringJoiner joiner = new StringJoiner(File.pathSeparator);
        for (Path p : paths) {
            joiner.add(p.toString());
        }
        return joiner.toString();
    }
}
