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

import com.oracle.truffle.api.dsl.*;
import com.oracle.truffle.api.dsl.test.TypeSystemTest.*;

public class TypeSystemErrorsTest {

    @TypeSystem({int.class, boolean.class})
    public static class Types0 {

    }

    @ExpectError("Invalid type order. The type(s) [java.lang.String] are inherited from a earlier defined type java.lang.CharSequence.")
    @TypeSystem({CharSequence.class, String.class})
    public static class Types1 {

    }

    @TypeSystem({int.class, boolean.class})
    public static class Types2 {

        @TypeCast
        @ExpectError("The provided return type \"String\" does not match expected return type \"int\".%")
        String asInteger(Object value) {
            return (String) value;
        }

    }

    @TypeSystem({int.class, boolean.class})
    public static class Types3 {

        @TypeCast
        @ExpectError("The provided return type \"boolean\" does not match expected return type \"int\".%")
        boolean asInteger(Object value) {
            return (boolean) value;
        }

    }

    @TypeSystemReference(Types0.class)
    @NodeChild
    @ExpectError("The @TypeSystem of the node and the @TypeSystem of the @NodeChild does not match. Types0 != SimpleTypes. ")
    abstract static class ErrorNode1 extends ValueNode {

    }

}
