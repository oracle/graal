/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.jni;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.nfi.types.NativeSimpleType;

public class NativeEnv {

    static final Map<Class<?>, NativeSimpleType> classToNative = buildClassToNative();

    static Map<Class<?>, NativeSimpleType> buildClassToNative() {
        Map<Class<?>, NativeSimpleType> map = new HashMap<>();
        map.put(boolean.class, NativeSimpleType.UINT8);
        map.put(byte.class, NativeSimpleType.SINT8);
        map.put(short.class, NativeSimpleType.SINT16);
        map.put(char.class, NativeSimpleType.UINT16);
        map.put(int.class, NativeSimpleType.SINT32);
        map.put(float.class, NativeSimpleType.FLOAT);
        map.put(long.class, NativeSimpleType.SINT64);
        map.put(double.class, NativeSimpleType.DOUBLE);
        map.put(void.class, NativeSimpleType.VOID);
        map.put(String.class, NativeSimpleType.STRING);
        return Collections.unmodifiableMap(map);
    }

    public static long unwrapPointer(Object nativePointer) {
        try {
            return ForeignAccess.sendAsPointer(Message.AS_POINTER.createNode(), (TruffleObject) nativePointer);
        } catch (UnsupportedMessageException e) {
            throw new RuntimeException(e);
        }
    }

    public static NativeSimpleType classToType(Class<?> clazz, boolean javaToNative) {
        return classToNative.getOrDefault(clazz, javaToNative ? NativeSimpleType.OBJECT_OR_NULL : NativeSimpleType.OBJECT);
    }

}
