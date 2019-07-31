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

import java.nio.ByteBuffer;

import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.espresso.runtime.StaticObject;

import sun.misc.Perf;

@EspressoSubstitutions
public final class Target_sun_misc_Perf {

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
    public static @Host(ByteBuffer.class) StaticObject createLong(Object self, @Host(String.class) StaticObject name, int variability, int units, long value,
                    @GuestCall DirectCallNode ByteBuffer_wrap) {
        return (StaticObject) ByteBuffer_wrap.call(StaticObject.wrap(ByteUtils.longToBytes(value)));
    }

    @Substitution
    public static void registerNatives() {
        /* nop */
    }
}
