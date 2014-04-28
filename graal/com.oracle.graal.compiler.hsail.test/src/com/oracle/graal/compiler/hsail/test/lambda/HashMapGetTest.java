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

package com.oracle.graal.compiler.hsail.test.lambda;

import static com.oracle.graal.debug.Debug.*;

import com.oracle.graal.compiler.hsail.test.infra.GraalKernelTester;
import com.oracle.graal.debug.*;

import java.util.HashMap;

import org.junit.Test;

/**
 * Tests calling HashMap.get().
 */
public class HashMapGetTest extends GraalKernelTester {

    static final int NUM = 20;

    static class MyObj {
        public MyObj(int id) {
            this.id = id;
        }

        int id;

        public int getId() {
            return id;
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof MyObj)) {
                return false;
            }
            MyObj othobj = (MyObj) other;
            return (othobj.id == this.id);
        }

        @Override
        public int hashCode() {
            return 43 * (id % 7);
        }

    }

    @Result public MyObj[] outArray = new MyObj[NUM];
    MyObj[] inArray = new MyObj[NUM];
    public HashMap<MyObj, MyObj> inMap = new HashMap<>();

    void setupArrays() {
        for (int i = 0; i < NUM; i++) {
            MyObj myobj = new MyObj(i);
            inMap.put(myobj, new MyObj(i * 3));
            inArray[NUM - 1 - i] = myobj;
            outArray[i] = null;
        }
    }

    @Override
    public void runTest() {
        setupArrays();

        dispatchLambdaKernel(NUM, (gid) -> {
            outArray[gid] = inMap.get(inArray[gid]);
        });
    }

    // ForeignCall to Invoke#Direct#get
    // not inlining HashMapGetTest.lambda$38@15: java.util.HashMap.get(Object):Object (20 bytes): no
    // type profile exists
    @Test(expected = com.oracle.graal.compiler.common.GraalInternalError.class)
    public void test() {
        try (DebugConfigScope s = disableIntercept()) {
            testGeneratedHsail();
        }
    }

    @Test(expected = com.oracle.graal.compiler.common.GraalInternalError.class)
    public void testUsingLambdaMethod() {
        try (DebugConfigScope s = disableIntercept()) {
            testGeneratedHsailUsingLambdaMethod();
        }
    }

}
