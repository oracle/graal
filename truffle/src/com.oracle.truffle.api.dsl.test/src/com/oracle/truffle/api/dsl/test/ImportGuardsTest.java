/*
 * Copyright (c) 2012, 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.dsl.test;

import static com.oracle.truffle.api.dsl.test.TestHelper.array;
import static com.oracle.truffle.api.dsl.test.TestHelper.assertRuns;

import org.junit.Test;

import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.test.ImportGuardsTestFactory.ImportGuards6Factory;
import com.oracle.truffle.api.dsl.test.TypeSystemTest.ValueNode;

public class ImportGuardsTest {

    @ImportStatic(Imports0.class)
    @NodeChild("a")
    static class ImportGuards0 extends ValueNode {

        @Specialization(guards = "staticGuard(a)")
        int f0(int a) {
            return a;
        }

        @Specialization(guards = "protectedGuard(a)")
        int f2(int a) {
            return a;
        }

        @Specialization(guards = "packageGuard(a)")
        int f3(int a) {
            return a;
        }

    }

    @NodeChild("a")
    @ImportStatic(Imports0.class)
    static class ImportGuards1 extends ValueNode {

        @ExpectError("Error parsing expression 'nonStaticGuard(a)': The method nonStaticGuard is undefined for the enclosing scope.")
        @Specialization(guards = "nonStaticGuard(a)")
        int f1(int a) {
            return a;
        }

        @ExpectError("Error parsing expression 'privateGuard(a)': The method privateGuard is undefined for the enclosing scope.")
        @Specialization(guards = "privateGuard(a)")
        int f4(int a) {
            return a;
        }
    }

    public static class Imports0 {
        public static boolean staticGuard(int a) {
            return a == 0;
        }

        public boolean nonStaticGuard(int a) {
            return a == 0;
        }

        protected static boolean protectedGuard(int a) {
            return a == 0;
        }

        static boolean packageGuard(int a) {
            return a == 0;
        }

        @SuppressWarnings("unused")
        private static boolean privateGuard(int a) {
            return a == 0;
        }

    }

    @ExpectError("The specified import guard class 'com.oracle.truffle.api.dsl.test.ImportGuardsTest.Imports1' must be public.")
    @NodeChild("a")
    @ImportStatic(Imports1.class)
    static class ImportGuards2 extends ValueNode {

        int do1(int a) {
            return a;
        }
    }

    static class Imports1 {

    }

    @ExpectError("The specified import guard class 'com.oracle.truffle.api.dsl.test.ImportGuardsTest.Imports2' must be public.")
    @NodeChild("a")
    @ImportStatic(Imports2.class)
    static class ImportGuards3 extends ValueNode {

        int do1(int a) {
            return a;
        }
    }

    @ExpectError("The specified import guard class 'boolean' is not a declared type.")
    @NodeChild("a")
    @ImportStatic(boolean.class)
    static class ImportGuards4 extends ValueNode {

        int do1(int a) {
            return a;
        }
    }

    private static class Imports2 {

    }

    @ExpectError("At least import guard classes must be specified.")
    @NodeChild("a")
    @ImportStatic({})
    static class ImportGuards5 extends ValueNode {

        int do1(int a) {
            return a;
        }
    }

    @Test
    public void testImportGuards6() {
        // should use the guar declared in the node instead of the imported one.
        assertRuns(ImportGuards6Factory.getInstance(), //
                        array(1, 1), //
                        array(1, 1));
    }

    @ImportStatic(Imports0.class)
    @NodeChild("a")
    static class ImportGuards6 extends ValueNode {

        static boolean staticGuard(int a) {
            return a == 1;
        }

        @Specialization(guards = "staticGuard(a)")
        int f0(int a) {
            return a;
        }
    }

}
