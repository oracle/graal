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

package com.oracle.truffle.espresso.intrinsics;

import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.StaticObject;
import com.oracle.truffle.espresso.runtime.StaticObjectArray;

import java.io.IOException;
import java.util.Arrays;
import java.util.function.IntFunction;

@EspressoIntrinsics
public class Target_java_lang_Runtime {
    // TODO(peterssen): This a hack to be able to spawn processes without going down to UNIXProcess.
    @Intrinsic(hasReceiver = true)
    public static @Type(Process.class) Object exec(@SuppressWarnings("unused") StaticObject self, @Type(String[].class) StaticObject cmdarray) {
        StaticObject[] wrapped = ((StaticObjectArray) cmdarray).unwrap();
        String[] hostArgs = new String[wrapped.length];
        Arrays.setAll(hostArgs, new IntFunction<String>() {
            @Override
            public String apply(int i) {
                return Meta.toHostString(wrapped[i]);
            }
        });
        try {
            Runtime.getRuntime().exec(hostArgs);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return StaticObject.NULL;
    }
}
