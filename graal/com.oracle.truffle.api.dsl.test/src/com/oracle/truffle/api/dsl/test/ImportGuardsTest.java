/*
 * Copyright (c) 2012, 2013, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.dsl.test;

import static com.oracle.truffle.api.dsl.test.TestHelper.*;

import org.junit.*;

import com.oracle.truffle.api.dsl.*;
import com.oracle.truffle.api.dsl.test.ImportGuardsTestFactory.ImportGuards6Factory;
import com.oracle.truffle.api.dsl.test.TypeSystemTest.ValueNode;

public class ImportGuardsTest {

    @ImportGuards(Imports0.class)
    @NodeChild("a")
    static class ImportGuards0 extends ValueNode {

        @Specialization(guards = "staticGuard")
        int f0(int a) {
            return a;
        }
    }

    @NodeChild("a")
    @ImportGuards(Imports0.class)
    static class ImportGuards1 extends ValueNode {

        @ExpectError("No compatible guard with method name 'nonStaticGuard' found.")
        @Specialization(guards = "nonStaticGuard")
        int f1(int a) {
            return a;
        }

        @ExpectError("No compatible guard with method name 'protectedGuard' found.")
        @Specialization(guards = "protectedGuard")
        int f2(int a) {
            return a;
        }

        @ExpectError("No compatible guard with method name 'packageGuard' found.")
        @Specialization(guards = "packageGuard")
        int f3(int a) {
            return a;
        }

        @ExpectError("No compatible guard with method name 'privateGuard' found.")
        @Specialization(guards = "privateGuard")
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
    @ImportGuards(Imports1.class)
    static class ImportGuards2 extends ValueNode {

        int do1(int a) {
            return a;
        }
    }

    static class Imports1 {

    }

    @ExpectError("The specified import guard class 'com.oracle.truffle.api.dsl.test.ImportGuardsTest.Imports2' must be public.")
    @NodeChild("a")
    @ImportGuards(Imports2.class)
    static class ImportGuards3 extends ValueNode {

        int do1(int a) {
            return a;
        }
    }

    @ExpectError("The specified import guard class 'boolean' is not a declared type.")
    @NodeChild("a")
    @ImportGuards(boolean.class)
    static class ImportGuards4 extends ValueNode {

        int do1(int a) {
            return a;
        }
    }

    private static class Imports2 {

    }

    @ExpectError("At least import guard classes must be specified.")
    @NodeChild("a")
    @ImportGuards({})
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

    @ImportGuards(Imports0.class)
    @NodeChild("a")
    static class ImportGuards6 extends ValueNode {

        static boolean staticGuard(int a) {
            return a == 1;
        }

        @Specialization(guards = "staticGuard")
        int f0(int a) {
            return a;
        }
    }

}
