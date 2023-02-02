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

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.espresso.ffi.nfi.NativeUtils;
import com.oracle.truffle.espresso.jni.JniEnv;

/**
 * Uses the obtained offsets regarding the structure that stores offset information to build the
 * mapping in Java.
 * 
 * This prevents expensive back-and-forth movement between java and native world, and illustrates
 * how the wrappers are used.
 */
public class JavaMemberOffsetGetter implements MemberOffsetGetter {
    private final Map<String, Long> memberInfos;

    public JavaMemberOffsetGetter(JniEnv jni, TruffleObject memberInfo, Structs structs) {
        this.memberInfos = buildInfos(jni, structs, memberInfo);
    }

    private static Map<String, Long> buildInfos(JniEnv jni, Structs structs, TruffleObject info) {
        Map<String, Long> map = new HashMap<>();
        InteropLibrary library = InteropLibrary.getUncached();
        assert !library.isNull(info);
        TruffleObject current = NativeUtils.dereferencePointerPointer(library, info);
        while (!library.isNull(current)) {
            MemberInfo.MemberInfoWrapper wrapper = structs.memberInfo.wrap(jni, current);
            long offset = wrapper.offset();
            String str = NativeUtils.interopPointerToString(wrapper.id());
            map.put(str, offset);
            current = wrapper.next();
        }
        return Collections.unmodifiableMap(map);
    }

    @Override
    public long getInfo(String structName) {
        if (memberInfos.containsKey(structName)) {
            return memberInfos.get(structName);
        }
        return -1;
    }

}
