/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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

import java.util.concurrent.atomic.*;

import org.junit.*;

import sun.misc.*;

import com.oracle.graal.compiler.hsail.test.infra.*;
import com.oracle.graal.debug.*;

/**
 * Tests {@link AtomicReference#getAndSet(Object)} which indirectly tests
 * {@link Unsafe#compareAndSwapObject(Object, long, Object, Object)}. The latter requires special
 * handling if compressed oops are enabled.
 */
public class AtomicReferenceGetAndSetTest extends GraalKernelTester {

    static final int NUM = 20;
    @Result public int[] followedCount = new int[NUM];
    public MyObj[] inArray = new MyObj[NUM];
    AtomicReference<MyObj> atomicRef = new AtomicReference<>();

    public static class MyObj {
        public int val;
        public boolean[] followedBy = new boolean[NUM + 1];

        MyObj(int n) {
            val = n;
        }
    }

    void setupArrays() {
        for (int i = 0; i < NUM; i++) {
            inArray[i] = new MyObj(i + 1);
        }
        atomicRef.set(new MyObj(0)); // initial value
    }

    private static final boolean DEBUG = false;

    @Override
    public void runTest() {
        setupArrays();

        dispatchMethodKernel(NUM);

        // make a fake followedBy for the final object
        MyObj finalObj = atomicRef.get();
        finalObj.followedBy[0] = true;

        // When the kernel is done, compute the number of true bits in each followedBy array;
        for (int i = 0; i < NUM; i++) {
            MyObj obj = inArray[i];
            int count = 0;
            for (int j = 0; j < NUM + 1; j++) {
                boolean b = obj.followedBy[j];
                if (b) {
                    count++;
                    if (DEBUG) {
                        TTY.println("obj " + obj.val + " was followed by " + j);
                    }
                }

            }
            followedCount[i] = count;
        }
    }

    public void run(int gid) {
        MyObj newObj = inArray[gid];
        MyObj oldObj = atomicRef.getAndSet(newObj);
        oldObj.followedBy[newObj.val] = true;
    }

    @Test
    public void test() {
        testGeneratedHsail();
    }
}
