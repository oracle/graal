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
package com.oracle.truffle.espresso.libs;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * A class to guarantee consistency when encoding and decoding bytes to strings between the guest
 * and host world.
 *
 * The guest gets its charSet for encoding and decoding from System.getProperty("sun.jnu.encoding").
 * We substitute this with the charSet specified in this class. Then on the host side, we just
 * simply use the same charSet.
 */
public class JNU {
    private static final Charset charSet = StandardCharsets.UTF_8;

    public static Charset getCharSet() {
        return charSet;
    }

    public static String getString(byte[] arr, int index, int length) {
        return new String(arr, index, length, charSet);
    }
}
