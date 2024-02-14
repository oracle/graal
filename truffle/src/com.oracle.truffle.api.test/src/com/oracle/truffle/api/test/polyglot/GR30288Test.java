/*
 * Copyright (c) 2021, 2024, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.test.ReflectionUtils;
import org.graalvm.nativeimage.ImageInfo;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;
import org.junit.AfterClass;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;

import com.oracle.truffle.api.test.CompileImmediatelyCheck;
import com.oracle.truffle.tck.tests.TruffleTestAssumptions;

import java.lang.ref.ReferenceQueue;
import java.util.Map;

/*
 * Please note that any OOME exceptions when running this test indicate memory leaks in Truffle.
 * This test requires -Xmx32M to reliably fail.
 */
public class GR30288Test {

    @BeforeClass
    public static void runWithWeakEncapsulationOnly() {
        TruffleTestAssumptions.assumeWeakEncapsulation();
    }

    @Test
    public void test() throws Exception {
        Assume.assumeFalse(CompileImmediatelyCheck.isCompileImmediately());

        // it is important that a new host access is created each time for this test to fail
        for (int i = 0; i < 10000; i++) {
            HostAccess access = HostAccess.newBuilder().allowPublicAccess(true).build();

            try (Context context = Context.newBuilder().allowHostAccess(access).build()) {
                /*
                 * We use several classes to hit the problem earlier.
                 */
                for (Object value : TEST_VALUES) {
                    Value v = context.asValue(value);

                    // materialize all member access
                    v.getMemberKeys().size();
                }
            }
        }
    }

    /***
     * Cleans up leaked {@link ClassValue} values on HotSpot after the test. Although the
     * {@link ClassValue} instances in the {@code HostClassCache} are released, their values are
     * still strongly reachable from the {@code Class#classValueMap} field. In order to release them
     * you need to actively use {@code ClassValueMap}. You need to populate the
     * {@code ClassValueMap#cacheLoadLimit} values. The {@code ClassValueMap#cacheLoadLimit} can be
     * quite high, so we clean the {@code ClassValueMap} using a reflection. The
     * {@code ClassValueMap} holds {@link ClassValue} values in three places.
     * <ol>
     * <li>{@code ClassValueMap.Entry#value}</li>
     * <li>Constant folding cache {@code ClassValueMap#cacheArray}</li>
     * <li>Enqueued {@code ClassValueMap.Entry}s in the {@code ClassValueMap}
     * {@link ReferenceQueue}</li>
     * </ol>
     * We need to clear all these three references.
     */
    @AfterClass
    public static void tearDown() throws Exception {
        if (!ImageInfo.inImageRuntimeCode()) {
            // Perform GC to process weak references.
            System.gc();
            for (Object value : TEST_VALUES) {
                // Clean the constants folding cache
                Map<?, ?> classValueMap = (Map<?, ?>) ReflectionUtils.getField(value.getClass(), "classValueMap");
                if (classValueMap != null) {
                    ReflectionUtils.invoke(classValueMap, "removeStaleEntries");
                    // Clean the ClassValueMap entries with weakly reachable ClassValues from both
                    // the map and the reference queue.
                    for (int i = 0; i < 10 && classValueMap.size() > 0; i++) {
                        // Give a GC a chance to enqueue references into a reference queue.
                        Thread.sleep(100);
                    }
                }
            }
        }
    }

    static class BaseClass {

        public void m0() {
        }

        public void m1() {
        }

        public void m2() {
        }

        public void m3() {
        }

        public void m4() {
        }

        public void m5() {
        }

        public void m6() {
        }

        public void m7() {
        }

        public void m8() {
        }

        public void m9() {
        }

        public void m10() {
        }

        public void m11() {
        }

        public void m12() {
        }

        public void m13() {
        }

        public void m14() {
        }

        public void m15() {
        }

        public void m16() {
        }

        public void m17() {
        }

        public void m18() {
        }

        public void m19() {
        }

        public void m20() {
        }

        public void m21() {
        }

    }

    public static class TestClass1 extends BaseClass {
    }

    public static class TestClass2 extends BaseClass {

    }

    public static class TestClass3 extends BaseClass {

    }

    public static class TestClass4 extends BaseClass {

    }

    public static class TestClass5 extends BaseClass {

    }

    public static class TestClass6 extends BaseClass {

    }

    public static class TestClass7 extends BaseClass {

    }

    private static final BaseClass[] TEST_VALUES = new BaseClass[]{
                    new TestClass1(),
                    new TestClass2(),
                    new TestClass3(),
                    new TestClass4(),
                    new TestClass5(),
                    new TestClass6(),
                    new TestClass7(),
    };

}
