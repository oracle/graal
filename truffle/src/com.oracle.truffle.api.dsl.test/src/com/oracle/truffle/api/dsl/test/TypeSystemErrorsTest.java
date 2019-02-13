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

import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.TypeCast;
import com.oracle.truffle.api.dsl.TypeCheck;
import com.oracle.truffle.api.dsl.TypeSystem;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.dsl.test.TypeSystemErrorsTest.Types1.Type1;
import com.oracle.truffle.api.dsl.test.TypeSystemTest.ValueNode;

public class TypeSystemErrorsTest {

    @TypeSystem({int.class, boolean.class})
    public static class ErrorTypes0 {

    }

    @ExpectError("Invalid type order. The type(s) [java.lang.String] are inherited from a earlier defined type java.lang.CharSequence.")
    @TypeSystem({CharSequence.class, String.class})
    public static class ErrorTypes1 {

    }

    public static class Types1 {
        public static class Type1 {
        }
    }

    public static class Types2 {
        public static class Type1 {
        }
    }

    // verify boxed type overlay
    @ExpectError("Two types result in the same boxed name: Type1.")
    @TypeSystem({Type1.class, com.oracle.truffle.api.dsl.test.TypeSystemErrorsTest.Types2.Type1.class})
    public static class ErrorTypes2 {

    }

    public static class Types3 {
        public static class Object {
        }
    }

    // verify Object name cannot appear
    @ExpectError("Two types result in the same boxed name: Object.")
    @TypeSystem({com.oracle.truffle.api.dsl.test.TypeSystemErrorsTest.Types3.Object.class})
    public static class ErrorTypes3 {
    }

    public static class Types4 {
        public static class Integer {
        }
    }

    // verify int boxed name
    @ExpectError("Two types result in the same boxed name: Integer.")
    @TypeSystem({int.class, com.oracle.truffle.api.dsl.test.TypeSystemErrorsTest.Types4.Integer.class})
    public static class ErrorTypes4 {
    }

    @TypeSystemReference(ErrorTypes0.class)
    @NodeChild
    @ExpectError("The @TypeSystem of the node and the @TypeSystem of the @NodeChild does not match. Types0 != SimpleTypes. ")
    abstract static class ErrorNode1 extends ValueNode {
    }

    @TypeSystem({int.class})
    public static class CastError1 {
        @TypeCast(int.class)
        @ExpectError("The provided return type \"String\" does not match expected return type \"int\".%")
        public static String asInteger(Object value) {
            return (String) value;
        }
    }

    @TypeSystem({int.class})
    public static class CastError2 {
        @TypeCast(int.class)
        @ExpectError("The provided return type \"boolean\" does not match expected return type \"int\".%")
        public static boolean asInteger(Object value) {
            return (boolean) value;
        }
    }

    @TypeSystem({int.class})
    public static class CastError4 {
        @ExpectError("@TypeCast annotated method asInt must be public and static.")
        @TypeCast(int.class)
        public int asInt(Object value) {
            return (int) value;
        }
    }

    @TypeSystem({int.class})
    public static class CastError5 {
        @ExpectError("@TypeCast annotated method asInt must be public and static.")
        @TypeCast(int.class)
        static int asInt(Object value) {
            return (int) value;
        }
    }

    @TypeSystem({int.class})
    public static class CheckError2 {
        @ExpectError("@TypeCheck annotated method isInt must be public and static.")
        @TypeCheck(int.class)
        public boolean isInt(Object value) {
            return value instanceof Integer;
        }
    }

    @TypeSystem({int.class})
    public static class CheckError3 {
        @ExpectError("@TypeCheck annotated method isInt must be public and static.")
        @TypeCheck(int.class)
        static boolean isInt(Object value) {
            return value instanceof Integer;
        }
    }

}
