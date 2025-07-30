/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.runtime.panama;

import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.ffi.NativeType;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;

//@formatter:off eclipse 4.14.0 seems to choke on this
public record VMStorage(byte type,
                        short segmentMaskOrSize,
                        int indexOrOffset) {
    // see jdk.internal.foreign.abi.StubLocations

    public StorageType type(Platform platform) {
        return platform.getStorageType(type());
    }

    public StubLocation getStubLocation(Platform platform) {
        assert type(platform).isPlaceholder();
        return StubLocation.get(indexOrOffset);
    }

    public NativeType asNativeType(Platform platform, Klass klass) {
        return type(platform).asNativeType(segmentMaskOrSize, klass);
    }

    public enum StubLocation {
        TARGET_ADDRESS,
        RETURN_BUFFER,
        CAPTURED_STATE_BUFFER;

        public static StubLocation get(int id) {
            return switch (id) {
                case 0 -> TARGET_ADDRESS;
                case 1 -> RETURN_BUFFER;
                case 2 -> CAPTURED_STATE_BUFFER;
                default -> throw EspressoError.unimplemented("Unknown id: " + id);
            };
        }
    }

    public static VMStorage fromGuest(StaticObject guestVmStorage, Meta meta) {
        if (StaticObject.isNull(guestVmStorage)) {
            return null;
        }
        return new VMStorage(
                meta.jdk_internal_foreign_abi_VMStorage_type.getByte(guestVmStorage),
                meta.jdk_internal_foreign_abi_VMStorage_segmentMaskOrSize.getShort(guestVmStorage),
                meta.jdk_internal_foreign_abi_VMStorage_indexOrOffset.getInt(guestVmStorage));
    }

    public static VMStorage[] fromGuestArray(StaticObject guestVmStorageArray, Meta meta) {
        EspressoLanguage language = meta.getLanguage();
        int length = guestVmStorageArray.length(language);
        VMStorage[] result = new VMStorage[length];
        for (int i = 0; i < length; i++) {
            StaticObject guestVmStorage = guestVmStorageArray.get(language, i);
            result[i] = fromGuest(guestVmStorage, meta);
        }
        return result;
    }
}
