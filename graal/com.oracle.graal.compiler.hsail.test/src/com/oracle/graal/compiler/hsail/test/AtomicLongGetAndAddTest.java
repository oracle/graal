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

import java.util.*;
import java.util.concurrent.atomic.*;

import org.junit.*;

import com.oracle.graal.compiler.hsail.test.infra.*;

/**
 * Tests {@link AtomicLong#getAndAdd(long)} which tests HSAIL atomic_add codegen.
 */
public class AtomicLongGetAndAddTest extends GraalKernelTester {

    static final int NUM = 20;
    @Result public long[] outArray = new long[NUM];
    AtomicLong atomicLong = new AtomicLong();

    void setupArrays() {
        for (int i = 0; i < NUM; i++) {
            outArray[i] = -i;
        }
    }

    @Override
    public void runTest() {
        setupArrays();

        dispatchMethodKernel(NUM);

        // note: the actual order of entries in outArray is not predictable
        // thus we sort before we compare results
        Arrays.sort(outArray);
    }

    public void run(int gid) {
        outArray[gid] = atomicLong.getAndAdd(0x7);
    }

    @Test
    public void test() {
        testGeneratedHsail();
    }
}
