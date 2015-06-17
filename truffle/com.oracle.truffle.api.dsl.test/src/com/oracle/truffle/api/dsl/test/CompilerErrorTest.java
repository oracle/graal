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

public class CompilerErrorTest {

    abstract static class Visiblity01 extends ValueNode {

        @Specialization
        @SuppressWarnings("static-method")
        @ExpectError("Method annotated with @Specialization must not be private.")
        private Object s() {
            return null;
        }

    }

    @ExpectError("Classes containing a @Specialization annotation must not be private.")
    private abstract static class Visiblity02 extends ValueNode {

        @Specialization
        public Object s() {
            return null;
        }

    }

    // assert no error
    @ExpectError({})
    private abstract static class Visiblity03 extends ValueNode {

    }

}
