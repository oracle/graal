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

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.espresso.ffi.NativeType;
import com.oracle.truffle.espresso.ffi.Pointer;
import com.oracle.truffle.espresso.ffi.RawPointer;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.runtime.StaticObject;

public abstract class NativeEnv {

    protected final Set<@Pointer TruffleObject> nativeClosures = Collections.newSetFromMap(new IdentityHashMap<>());

    protected static Object defaultValue(NativeType nativeType) {
        // @formatter:off
        switch (nativeType){
            case BOOLEAN : return false;
            case BYTE    : return (byte) 0;
            case CHAR    : return (char) 0;
            case SHORT   : return (short) 0;
            case INT     : return 0;
            case LONG    : return 0L;
            case FLOAT   : return 0F;
            case DOUBLE  : return 0D;
            case POINTER : return RawPointer.nullInstance();
            case VOID    : // fall-through
            case OBJECT  : return StaticObject.NULL;
        }
        // @formatter:on
        CompilerDirectives.transferToInterpreterAndInvalidate();
        throw EspressoError.shouldNotReachHere("Unexpected NativeType: " + nativeType);
    }

}
