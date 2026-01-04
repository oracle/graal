/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.ffi;

import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.espresso.ffi.memory.NativeMemory;

@ExportLibrary(InteropLibrary.class)
public final class RawPointer implements TruffleObject {
    private final long rawPtr;

    private static final RawPointer NULL = new RawPointer(0L);

    public static @Pointer TruffleObject nullInstance() {
        return NULL;
    }

    public RawPointer(long rawPtr) {
        this.rawPtr = rawPtr;
    }

    public static @Pointer TruffleObject create(long ptr) {
        if (ptr == 0L) {
            return NULL;
        }
        return new RawPointer(ptr);
    }

    @SuppressWarnings("static-method")
    @ExportMessage
    boolean isPointer() {
        return true;
    }

    /**
     * There is no guarantee that the address actually corresponds to a host-native-address. It
     * should correspond to one of the following:
     * <ul>
     * <li>A {@link NativeMemory} address</li>
     * <li>A handle, such as in
     * {@link com.oracle.truffle.espresso.vm.VM#JVM_LoadLibrary(String, boolean)}</li>
     * <li>A special value, such as:
     * <ul>
     * <li>The NULL pointer in {@link RawPointer}</li>
     * <li>The sentinelPointer in
     * {@link com.oracle.truffle.espresso.libs.libjvm.impl.LibJVMSubstitutions}</li>
     * </ul>
     * </li>
     * </ul>
     */
    @ExportMessage
    long asPointer() {
        return rawPtr;
    }

    @ExportMessage
    boolean isNull() {
        return rawPtr == 0L;
    }
}
