/*
 * Copyright (c) 2009, 2012, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.graal.compiler.hsail.test;

/**
 * A simple 3 element Vector object used in some junit tests.
 */
public class Vec3 {

    public Vec3(float x, float y, float z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public float x;
    public float y;
    public float z;

    public static Vec3 add(Vec3 a, Vec3 b) {
        return new Vec3(a.x + b.x, a.y + b.y, a.z + b.z);
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof Vec3)) {
            return false;
        }
        Vec3 oth = (Vec3) other;
        return (oth.x == x && oth.y == y && oth.z == z);
    }

    @Override
    public String toString() {
        return ("Vec3[" + x + ", " + y + ", " + z + "]");
    }

    @Override
    public int hashCode() {
        return (int) (x + y + z);
    }

}
