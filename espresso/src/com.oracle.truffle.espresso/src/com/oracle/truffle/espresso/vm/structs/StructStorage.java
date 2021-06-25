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

import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.espresso.ffi.NativeAccess;
import com.oracle.truffle.espresso.jni.JniEnv;

/**
 * Commodity class that stores native structs sizes, along with member offsets. See documentation
 * for {@link StructWrapper}.
 */
public abstract class StructStorage<T extends StructWrapper> {
    protected final long structSize;

    public StructStorage(long structSize) {
        this.structSize = structSize;
    }

    public abstract T wrap(JniEnv jni, TruffleObject structPtr);

    public T allocate(NativeAccess nativeAccess, JniEnv jni) {
        TruffleObject pointer = nativeAccess.allocateMemory(structSize);
        return wrap(jni, pointer);
    }

    public long structSize() {
        return structSize;
    }
}
