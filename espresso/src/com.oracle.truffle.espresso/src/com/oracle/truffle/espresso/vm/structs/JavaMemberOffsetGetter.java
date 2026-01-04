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

import static com.oracle.truffle.espresso.ffi.memory.NativeMemory.IllegalMemoryAccessException;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.espresso.ffi.memory.NativeMemory;
import com.oracle.truffle.espresso.ffi.nfi.NativeUtils;
import com.oracle.truffle.espresso.jni.JNIHandles;
import com.oracle.truffle.espresso.meta.EspressoError;

/**
 * Uses the obtained offsets regarding the structure that stores offset information to build the
 * mapping in Java.
 * 
 * This prevents expensive back-and-forth movement between java and native world, and illustrates
 * how the wrappers are used.
 */
public class JavaMemberOffsetGetter implements MemberOffsetGetter {
    private final Map<String, Long> memberInfos;

    public JavaMemberOffsetGetter(JNIHandles handles, NativeMemory nativeMemory, TruffleObject memberInfo, Structs structs) {
        this.memberInfos = buildInfos(handles, nativeMemory, structs, memberInfo);
    }

    private static Map<String, Long> buildInfos(JNIHandles handles, NativeMemory nativeMemory, Structs structs, TruffleObject info) {
        Map<String, Long> map = new HashMap<>();
        InteropLibrary library = InteropLibrary.getUncached();
        assert !library.isNull(info);
        try {
            TruffleObject current = NativeUtils.dereferencePointerPointer(library, info, nativeMemory);
            while (!library.isNull(current)) {
                MemberInfo.MemberInfoWrapper wrapper = structs.memberInfo.wrap(handles, nativeMemory, current);
                long offset = wrapper.offset();
                String str = NativeUtils.interopPointerToString(wrapper.id(), nativeMemory);
                map.put(str, offset);
                current = wrapper.next();
            }
            return Collections.unmodifiableMap(map);
        } catch (IllegalMemoryAccessException e) {
            /*
             * We should not reach here as we are in control of the arguments and the struct set up
             * process. Thus, there should be no illegal memory accesses.
             */
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw EspressoError.shouldNotReachHere(e);
        }
    }

    @Override
    public long getInfo(String structName) {
        if (memberInfos.containsKey(structName)) {
            return memberInfos.get(structName);
        }
        return -1;
    }

}
