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

import static com.oracle.truffle.espresso.ffi.memory.NativeMemory.MemoryAllocationException;

import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.espresso.ffi.NativeAccess;
import com.oracle.truffle.espresso.ffi.RawPointer;
import com.oracle.truffle.espresso.ffi.memory.NativeMemory;
import com.oracle.truffle.espresso.jni.JNIHandles;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.EspressoContext;

/**
 * Commodity class that stores native structs sizes, along with member offsets. See documentation
 * for {@link StructWrapper}.
 */
public abstract class StructStorage<T extends StructWrapper> {
    protected final long structSize;

    public StructStorage(long structSize) {
        this.structSize = structSize;
    }

    public abstract T wrap(JNIHandles handles, NativeMemory nativeMemory, TruffleObject structPtr);

    public T allocate(NativeAccess nativeAccess, JNIHandles handles) {
        try {
            TruffleObject pointer = RawPointer.create(nativeAccess.nativeMemory().allocateMemory(structSize));
            return wrap(handles, nativeAccess.nativeMemory(), pointer);
        } catch (MemoryAllocationException e) {
            /*
             * This should be very rare as the maximum allocation size would need to be exceeded by
             * the "structsize", or we would run out of memory. Thus, the expensive
             * EspressoContext.get operation is reasonable.
             */
            EspressoContext context = EspressoContext.get(null);
            Meta meta = context.getMeta();
            throw meta.throwExceptionWithMessage(meta.java_lang_OutOfMemoryError, e.getMessage(), context);
        }
    }

    public long structSize() {
        return structSize;
    }
}
