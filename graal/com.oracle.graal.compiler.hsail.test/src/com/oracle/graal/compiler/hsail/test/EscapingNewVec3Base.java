/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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
 * Base class for Vec3 object allocation tests.
 */

public class EscapingNewVec3Base extends EscapingNewBase {

    float[] inArray = new float[NUM];
    @Result Vec3[] myOutArray = new Vec3[NUM];

    @Override
    void setupArrays() {
        super.setupArrays();
        for (int i = 0; i < NUM; i++) {
            inArray[i] = i;
            myOutArray[i] = null;
        }
    }

    public void run(int gid) {
        float inval = inArray[gid];
        // allocate and store in Object array
        outArray[gid] = new Vec3(inval + 1.1f, inval + 2.1f, inval + 3.1f);
        // allocate and store in Vec3 array
        myOutArray[gid] = new Vec3(inval + 4.1f, inval + 5.1f, inval + 6.1f);
    }
}
