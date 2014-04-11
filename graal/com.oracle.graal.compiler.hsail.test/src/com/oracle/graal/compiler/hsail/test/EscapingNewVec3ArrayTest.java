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

import org.junit.Test;

/**
 * Tests allocation of an array of Vec3 objects per workitem.
 */

public class EscapingNewVec3ArrayTest extends EscapingNewBase {

    public void run(int gid) {
        int size = gid + 1;
        Vec3[] vec3ary = new Vec3[size];
        for (int i = 0; i < vec3ary.length; i++) {
            vec3ary[i] = new Vec3(size + i + 1.1f, size + i + 2.2f, size + i + 3.3f);
        }
        outArray[gid] = vec3ary;
    }

    private static final boolean DEBUG = Boolean.getBoolean("hsail.debug");

    @Override
    public void runTest() {
        super.runTest();
        if (DEBUG) {
            System.out.println("dumping results");
            for (int i = 0; i < NUM; i++) {
                Vec3[] ary = (Vec3[]) outArray[i];
                System.out.print("ary len " + ary.length + ":  ");
                for (Vec3 val : ary) {
                    System.out.print(val + ", ");
                }
                System.out.println();
            }
        }
    }

    @Test
    public void test() {
        testGeneratedHsail();
    }
}
