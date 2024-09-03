/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.api.test.polyglot;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;

import org.junit.Assert;
import org.junit.Test;

import com.oracle.truffle.api.test.TestAPIAccessor;
import com.oracle.truffle.tck.tests.TruffleTestAssumptions;

import sun.misc.Unsafe;

public class ObjectSizeCalculationBaseMethodsTest {

    static final sun.misc.Unsafe UNSAFE = getUnsafe();

    private static Unsafe getUnsafe() {
        try {
            return Unsafe.getUnsafe();
        } catch (SecurityException e) {
        }
        try {
            Field theUnsafeInstance = Unsafe.class.getDeclaredField("theUnsafe");
            theUnsafeInstance.setAccessible(true);
            return (Unsafe) theUnsafeInstance.get(Unsafe.class);
        } catch (Exception e) {
            throw new RuntimeException("exception while trying to get Unsafe.theUnsafe via reflection:", e);
        }
    }

    static class InstanceSizeTest {
        private static String staticString;

        private final int i;
        private final int j;
        private final int k;
        private final String s;

        InstanceSizeTest(int i, int j, int k, String s) {
            this.i = i;
            this.j = j;
            this.k = k;
            this.s = s;
        }

        @Override
        public String toString() {
            return staticString +
                            i + ", " +
                            j + ", " +
                            k + ", " +
                            s;
        }
    }

    static class InstanceSizeTest2 {
        private static String staticString;

        private final int i;
        private final int j;
        private final int k;
        private final String s;
        private final String t;
        private final String u;

        InstanceSizeTest2(int i, int j, int k, String s, String t, String u) {
            this.i = i;
            this.j = j;
            this.k = k;
            this.s = s;
            this.t = t;
            this.u = u;
        }

        @Override
        public String toString() {
            return staticString +
                            i + ", " +
                            j + ", " +
                            k + ", " +
                            s + " " +
                            t + " " +
                            u;
        }
    }

    @Test
    public void testUseTestClasses() {
        InstanceSizeTest.staticString = "ONE:";
        InstanceSizeTest2.staticString = "TWO:";
        Object ist = new InstanceSizeTest(1, 2, 3, "Jarmil");
        Object ist2 = new InstanceSizeTest2(1, 2, 3, "We", "Are", "Brothers");
        Assert.assertEquals("ONE:1, 2, 3, Jarmil", ist.toString());
        Assert.assertEquals("TWO:1, 2, 3, We Are Brothers", ist2.toString());
    }

    @Test
    public void testBaseInstanceSize() {
        TruffleTestAssumptions.assumeOptimizingRuntime();

        Assert.assertTrue(TestAPIAccessor.runtimeAccess().getBaseInstanceSize(InstanceSizeTest.class) > 16);
        Assert.assertTrue(TestAPIAccessor.runtimeAccess().getBaseInstanceSize(InstanceSizeTest2.class) > 16);
    }

    @Test
    public void testResolvedFields() {
        TruffleTestAssumptions.assumeOptimizingRuntime();
        Object ist = new InstanceSizeTest(1, 2, 3, "Jarmil");
        Object ist2 = new InstanceSizeTest2(1, 2, 3, "We", "Are", "Brothers");
        int[] fieldOffsets = TestAPIAccessor.runtimeAccess().getFieldOffsets(ist.getClass(), false, true);
        Assert.assertEquals(1, fieldOffsets.length);
        Assert.assertEquals("Jarmil", UNSAFE.getObject(ist, fieldOffsets[0]));
        int[] fieldOffsets2 = TestAPIAccessor.runtimeAccess().getFieldOffsets(ist2.getClass(), false, true);
        Assert.assertEquals(3, fieldOffsets2.length);
        Assert.assertEquals("We", UNSAFE.getObject(ist2, fieldOffsets2[0]));
        Assert.assertEquals("Are", UNSAFE.getObject(ist2, fieldOffsets2[1]));
        Assert.assertEquals("Brothers", UNSAFE.getObject(ist2, fieldOffsets2[2]));
    }

    static class MyWeakReference extends WeakReference<String> {
        String referent;

        MyWeakReference(String referent, ReferenceQueue<? super String> q) {
            super(referent, q);
            this.referent = "secret" + referent;
        }
    }

    @Test
    public void testReferenceFields() {
        TruffleTestAssumptions.assumeOptimizingRuntime();

        String stringReferent = "stringReferent";
        MyWeakReference ref = new MyWeakReference(stringReferent, new ReferenceQueue<>());
        Assert.assertEquals(stringReferent, ref.get());
        int[] fieldOffsets = TestAPIAccessor.runtimeAccess().getFieldOffsets(ref.getClass(), false, true);
        Assert.assertEquals(1, fieldOffsets.length);
        Assert.assertEquals("secret" + stringReferent, UNSAFE.getObject(ref, fieldOffsets[0]));
    }
}
