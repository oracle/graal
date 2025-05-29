/*
 * Copyright (c) 2020, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.vm.structs;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.espresso.classfile.perf.DebugCloseable;
import com.oracle.truffle.espresso.classfile.perf.DebugTimer;
import com.oracle.truffle.espresso.ffi.Callback;
import com.oracle.truffle.espresso.ffi.NativeSignature;
import com.oracle.truffle.espresso.ffi.NativeType;
import com.oracle.truffle.espresso.ffi.Pointer;
import com.oracle.truffle.espresso.ffi.RawPointer;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.runtime.EspressoContext;

public final class StructsAccess {
    private static final DebugTimer STRUCTS_TIMER = DebugTimer.create("native struct creation");

    private StructsAccess() {
    }

    public static Structs getStructs(EspressoContext context, TruffleObject mokapotLibrary) {
        TruffleObject initializeStructs = context.getNativeAccess().lookupAndBindSymbol(mokapotLibrary,
                        "initializeStructs",
                        NativeSignature.create(NativeType.VOID, NativeType.POINTER));
        TruffleObject lookupMemberOffset = context.getNativeAccess().lookupAndBindSymbol(mokapotLibrary,
                        "lookupMemberOffset",
                        NativeSignature.create(NativeType.LONG, NativeType.POINTER, NativeType.POINTER));
        return initializeStructs(context, initializeStructs, lookupMemberOffset);
    }

    @SuppressWarnings("try")
    private static Structs initializeStructs(EspressoContext context, TruffleObject initializeStructs, TruffleObject lookupMemberOffset) {
        try (DebugCloseable timer = STRUCTS_TIMER.scope(context.getTimers())) {
            Structs[] box = new Structs[1];
            Callback doInitStructs = new Callback(1, new Callback.Function() {
                @Override
                @CompilerDirectives.TruffleBoundary
                public Object call(Object... args) {
                    TruffleObject memberInfoPtr = (TruffleObject) args[0];
                    box[0] = new Structs(context.getHandles(), memberInfoPtr, lookupMemberOffset);
                    return RawPointer.nullInstance();
                }
            });
            /*
             * Go down to native to initialize the data structure storing the offsets of used
             * structs (The memberInfoPtr seen in the callback). This will get back to java code
             * once the data structure is created. Once we get out of the native call, the structure
             * is freed and cannot be used anymore.
             */
            @Pointer
            TruffleObject closure = context.getNativeAccess().createNativeClosure(doInitStructs, NativeSignature.create(NativeType.VOID, NativeType.POINTER));
            try {
                InteropLibrary.getUncached().execute(initializeStructs, closure);
            } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
                throw EspressoError.shouldNotReachHere();
            }

            return box[0];
        }
    }
}
