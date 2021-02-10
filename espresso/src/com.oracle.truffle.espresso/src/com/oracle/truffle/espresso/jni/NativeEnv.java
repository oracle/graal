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
package com.oracle.truffle.espresso.jni;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.espresso._native.Buffer;
import com.oracle.truffle.espresso._native.NativeType;
import com.oracle.truffle.espresso._native.Pointer;
import com.oracle.truffle.espresso._native.RawPointer;
import com.oracle.truffle.espresso._native.TruffleByteBuffer;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.JavaKind;
import com.oracle.truffle.espresso.runtime.StaticObject;
import com.oracle.truffle.nfi.spi.types.NativeSimpleType;

public abstract class NativeEnv {

    protected final Set<@Pointer TruffleObject> nativeClosures = Collections.newSetFromMap(new IdentityHashMap<>());

    protected static Object defaultValue(String returnType) {
        if (returnType.equals("boolean")) {
            return false;
        }
        if (returnType.equals("byte")) {
            return (byte) 0;
        }
        if (returnType.equals("char")) {
            return (char) 0;
        }
        if (returnType.equals("short")) {
            return (short) 0;
        }
        if (returnType.equals("int")) {
            return 0;
        }
        if (returnType.equals("float")) {
            return 0.0F;
        }
        if (returnType.equals("double")) {
            return 0.0;
        }
        if (returnType.equals("long")) {
            return 0L;
        }
        if (returnType.equals("StaticObject")) {
            return 0L; // NULL handle
        }
        return StaticObject.NULL;
    }

}
