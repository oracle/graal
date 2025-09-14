/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.bytecode.test;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import com.oracle.truffle.api.bytecode.BytecodeConfig;
import com.oracle.truffle.api.bytecode.BytecodeParser;
import com.oracle.truffle.api.bytecode.BytecodeRootNode;
import com.oracle.truffle.api.bytecode.BytecodeRootNodes;
import com.oracle.truffle.api.bytecode.GenerateBytecode;
import com.oracle.truffle.api.bytecode.Operation;
import com.oracle.truffle.api.bytecode.test.error_tests.ExpectError;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.FrameDescriptor;

/**
 * Test for inheriting classes of operations to share code between them.
 */
public class InheritanceTest {

    private static final BytecodeDSLTestLanguage LANGUAGE = null;

    private static InheritanceTestRootNode parse(BytecodeParser<InheritanceTestRootNodeGen.Builder> builder) {
        BytecodeRootNodes<InheritanceTestRootNode> nodes = InheritanceTestRootNodeGen.create(LANGUAGE, BytecodeConfig.DEFAULT, builder);
        return nodes.getNode(0);
    }

    @Test
    public void testSubclassSpecializationsOrder() {
        InheritanceTestRootNode root = parse(b -> {
            b.beginRoot();
            b.beginReturn();
            b.beginSubClass();
            b.emitLoadArgument(0);
            b.endSubClass();
            b.endReturn();
            b.endRoot();
        });

        assertEquals("BaseBaseClass.s0", root.getCallTarget().call(3));
        assertEquals("BaseClass.s0", root.getCallTarget().call(2));
        assertEquals("SubClass.s0", root.getCallTarget().call(1));
    }

    @GenerateBytecode(//
                    languageClass = BytecodeDSLTestLanguage.class)
    abstract static class InheritanceTestRootNode extends DebugBytecodeRootNode implements BytecodeRootNode {

        protected InheritanceTestRootNode(BytecodeDSLTestLanguage language, FrameDescriptor frameDescriptor) {
            super(language, frameDescriptor);
        }

        static class BaseBaseClass {
            // using guards for subclasses are possible
            // this allows to make the different only in their guard
            @Specialization(guards = "guard3(v)")
            // we intentionally use the same specialization method name here
            // for all specializations to test referencing them properly in the generated code
            public static Object s0(@SuppressWarnings("unused") int v) {
                return "BaseBaseClass.s0";
            }

            static boolean guard2(int v) {
                return v >= 2;
            }
        }

        static class BaseClass extends BaseBaseClass {
            // make sure we can bind methods in base classes correctly
            @Specialization(guards = "guard2(v)")
            public static Object s0(int v) {
                return "BaseClass.s0";
            }

            static boolean guard3(int v) {
                return v >= 3;
            }
        }

        @Operation
        public static final class SubClass extends BaseClass {
            @Specialization(guards = "v >= 1")
            public static Object s0(int v) {
                return "SubClass.s0";
            }
        }

    }

    static class BaseBaseClass {
        @Specialization(guards = "v >= 1")
        public static Object s0(int v) {
            return v;
        }

        static boolean guard1(int v) {
            return v >= 2;
        }
    }

    @GenerateBytecode(//
                    languageClass = BytecodeDSLTestLanguage.class)
    abstract static class InheritanceError1Node extends DebugBytecodeRootNode implements BytecodeRootNode {

        protected InheritanceError1Node(BytecodeDSLTestLanguage language, FrameDescriptor frameDescriptor) {
            super(language, frameDescriptor);
        }

        static class BaseClass extends BaseBaseClass {
            @Specialization(guards = "guard1(v)")
            public static Object s0(int v) {
                return v;
            }

            static boolean guard1(int v) {
                return v >= 2;
            }
        }

        @ExpectError("All super types of operation classes must be declared as static nested classes of the operation root node. " +
                        "Modify the super class 'BaseBaseClass' to be an inner class of type 'InheritanceError1Node' to resolve this or use @OperationProxy instead.")
        @Operation
        public static final class SubClass extends BaseClass {
            @Specialization(guards = "v >= 3")
            public static Object s0(int v) {
                return v;
            }
        }

    }

    @GenerateBytecode(//
                    languageClass = BytecodeDSLTestLanguage.class)
    abstract static class InheritanceError2Node extends DebugBytecodeRootNode implements BytecodeRootNode {

        protected InheritanceError2Node(BytecodeDSLTestLanguage language, FrameDescriptor frameDescriptor) {
            super(language, frameDescriptor);
        }

        static class BaseBaseClass {

            @ExpectError("Operation class or super class must not contain non-static members.")
            void foo() {
            }

        }

        static class BaseClass extends BaseBaseClass {
            @Specialization(guards = "guard1(v)") // bind in base-cass
            public static Object s0(int v) {
                return v;
            }

            static boolean guard1(int v) {
                return v >= 2;
            }
        }

        @Operation
        public static final class SubClass extends BaseClass {
            @Specialization(guards = "v >= 3")
            public static Object s0(int v) {
                return v;
            }

            @ExpectError("Operation class or super class must not contain non-static members.")
            @Override
            void foo() {
            }

        }

        @GenerateBytecode(//
                        languageClass = BytecodeDSLTestLanguage.class)
        abstract static class InheritanceError3Node extends DebugBytecodeRootNode implements BytecodeRootNode {

            protected InheritanceError3Node(BytecodeDSLTestLanguage language, FrameDescriptor frameDescriptor) {
                super(language, frameDescriptor);
            }

            @ExpectError("Operation class or super class must not be declared private. Remove the private modifier to make it visible.")
            private static class BaseClass {
                @Specialization(guards = "guard1(v)") // bind in base-cass
                public static Object s0(int v) {
                    return v;
                }

                static boolean guard1(int v) {
                    return v >= 2;
                }
            }

            @Operation
            public static final class SubClass extends BaseClass {

            }
        }

    }

}
