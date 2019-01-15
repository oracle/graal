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

package com.oracle.truffle.espresso.substitutions;

import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.impl.MethodInfo;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.StaticObject;
import com.oracle.truffle.espresso.runtime.StaticObjectArray;
import sun.misc.Perf;

import java.nio.ByteBuffer;

@EspressoSubstitutions
public class Target_sun_misc_Perf {

    static class ByteUtils {
        private static ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);

        public static byte[] longToBytes(long x) {
            buffer.putLong(0, x);
            return buffer.array();
        }

        public static long bytesToLong(byte[] bytes) {
            buffer.put(bytes, 0, bytes.length);
            buffer.flip();// need flip
            return buffer.getLong();
        }
    }

    private final static Perf hostPerf = Perf.getPerf();

    @Substitution(hasReceiver = true)
    public static long highResCounter(@SuppressWarnings("unused") Object self) {
        return hostPerf.highResCounter();
    }

    @Substitution(hasReceiver = true)
    public static long highResFrequency(@SuppressWarnings("unused") Object self) {
        return hostPerf.highResFrequency();
    }

    @SuppressWarnings("unused")
    @Substitution(hasReceiver = true)
    public static @Type(ByteBuffer.class) StaticObject createLong(Object self, @Type(String.class) StaticObject var1, int var2, int var3, long var4) {
        EspressoContext context = EspressoLanguage.getCurrentContext();
        MethodInfo wrap = context.getRegistries().resolveWithBootClassLoader(context.getTypeDescriptors().make("Ljava/nio/ByteBuffer;")).findDeclaredMethod("wrap", ByteBuffer.class, byte[].class);
        return (StaticObject) wrap.getCallTarget().call(StaticObjectArray.wrap(ByteUtils.longToBytes(var4)));
    }

    @Substitution
    public static void registerNatives() {
        // nop
    }
}
