/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.debugger.jdwp;

public class ObjectIds {

    // TODO(Gregersen) - is this good enough as unique ID?
    // What would be a good alternative?
    // System.identityHashcode will probably have collisions
    public static byte[] getID(Object object) {
        return calculateId(System.identityHashCode(object));
    }

    private static byte[] calculateId(int identity) {
        byte[] b = new byte[8];
        long l = identity;
        b[7] = (byte) (l);
        l >>>= 8;
        b[6] = (byte) (l);
        l >>>= 8;
        b[5] = (byte) (l);
        l >>>= 8;
        b[4] = (byte) (l);
        l >>>= 8;
        b[3] = (byte) (l);
        l >>>= 8;
        b[2] = (byte) (l);
        l >>>= 8;
        b[1] = (byte) (l);
        l >>>= 8;
        b[0] = (byte) (l);
        return b;
    }
}
