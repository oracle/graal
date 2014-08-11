/*
 * Copyright (c) 2012, 2012, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.dsl.*;
import com.oracle.truffle.api.dsl.test.TypeSystemTest.ValueNode;

public class InsertBeforeTest {

    @NodeChild("a")
    static class InsertBefore1Base extends ValueNode {

        boolean g1(int a) {
            return a == 1;
        }

        boolean g2(int a) {
            return a == 2;
        }

        @Specialization(guards = "g1")
        int f1(int a) {
            return a;
        }

        @Specialization(guards = "g2")
        int f3(int a) {
            return a;
        }

    }

    @NodeChild("a")
    static class InsertBefore1T1 extends InsertBefore1Base {

        @Specialization
        int f0(int a) {
            return a;
        }

    }

    @NodeChild("a")
    static class InsertBefore1T2 extends InsertBefore1Base {

        boolean g0(int a) {
            return a == 0;
        }

        @Specialization(guards = "g0", insertBefore = "f1")
        int f0(int a) {
            return a;
        }

    }

    @NodeChild("a")
    static class InsertBefore1T3 extends InsertBefore1Base {

        boolean g0(int a) {
            return a == 0;
        }

        @Specialization(guards = "g0", insertBefore = "f3")
        int f0(int a) {
            return a;
        }

    }

    @NodeChild("a")
    @ExpectError({"Element int f3(int)  at annotation @Specialization is erroneous: Specialization is not reachable. It is shadowed by f0(int).",
                    "Element int f1(int)  at annotation @Specialization is erroneous: Specialization is not reachable. It is shadowed by f0(int)."})
    static class InsertBefore1T4 extends InsertBefore1Base {

        boolean g0(int a) {
            return a == 0;
        }

        @Specialization(insertBefore = "f1")
        int f0(int a) {
            return a;
        }

    }

    @NodeChild("a")
    @ExpectError({"Element int f3(int)  at annotation @Specialization is erroneous: Specialization is not reachable. It is shadowed by f0(int)."})
    static class InsertBefore1T5 extends InsertBefore1Base {

        boolean g0(int a) {
            return a == 0;
        }

        @Specialization(insertBefore = "f3")
        int f0(int a) {
            return a;
        }

    }

    @NodeChild("a")
    static class InsertBefore1Error1 extends InsertBefore1Base {

        @ExpectError("Specializations can only be inserted before specializations in superclasses.")
        @Specialization(insertBefore = "f0")
        int f0(int a) {
            return a;
        }

    }

    @NodeChild("a")
    static class InsertBefore1Error2 extends InsertBefore1Base {

        @ExpectError("The referenced specialization 'asdf' could not be found.")
        @Specialization(insertBefore = "asdf")
        int f0(int a) {
            return a;
        }

    }

}
