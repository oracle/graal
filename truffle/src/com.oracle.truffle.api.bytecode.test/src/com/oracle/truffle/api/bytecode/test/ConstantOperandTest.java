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

import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.bytecode.BytecodeConfig;
import com.oracle.truffle.api.bytecode.BytecodeParser;
import com.oracle.truffle.api.bytecode.BytecodeRootNode;
import com.oracle.truffle.api.bytecode.ConstantOperand;
import com.oracle.truffle.api.bytecode.EpilogExceptional;
import com.oracle.truffle.api.bytecode.EpilogReturn;
import com.oracle.truffle.api.bytecode.GenerateBytecode;
import com.oracle.truffle.api.bytecode.Instrumentation;
import com.oracle.truffle.api.bytecode.Operation;
import com.oracle.truffle.api.bytecode.test.ConstantOperandTestRootNode.ReplaceValue;
import com.oracle.truffle.api.bytecode.test.error_tests.ExpectError;
import com.oracle.truffle.api.bytecode.test.error_tests.ExpectWarning;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.exception.AbstractTruffleException;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.RootNode;

public class ConstantOperandTest {
    private static final BytecodeDSLTestLanguage LANGUAGE = null;

    private static ConstantOperandTestRootNode parse(BytecodeParser<ConstantOperandTestRootNodeGen.Builder> parser) {
        return ConstantOperandTestRootNodeGen.create(BytecodeConfig.DEFAULT, parser).getNode(0);
    }

    @Test
    public void testBasicConstant() {
        ConstantOperandTestRootNode root = parse(b -> {
            b.beginRoot(LANGUAGE);
            b.beginReturn();
            b.beginDivConstantDividend(84);
            b.emitLoadArgument(0);
            b.endDivConstantDividend();
            b.endReturn();
            b.endRoot();
        });

        assertEquals(42, root.getCallTarget().call(2));
    }

    @Test
    public void testBasicConstantAtEnd() {
        ConstantOperandTestRootNode root = parse(b -> {
            b.beginRoot(LANGUAGE);
            b.beginReturn();
            b.beginDivConstantDivisor();
            b.emitLoadArgument(0);
            b.endDivConstantDivisor(2);
            b.endReturn();
            b.endRoot();
        });

        assertEquals(42, root.getCallTarget().call(84));
    }

    @Test
    public void testBasicConstantAtBeginAndEnd() {
        ConstantOperandTestRootNode root = parse(b -> {
            b.beginRoot(LANGUAGE);
            b.beginReturn();
            b.beginGetAttrWithDefault("foo");
            b.emitLoadArgument(0);
            b.endGetAttrWithDefault("bar");
            b.endReturn();
            b.endRoot();
        });

        assertEquals("bar", root.getCallTarget().call(new HashMap<>()));
        assertEquals("baz", root.getCallTarget().call(Map.of("foo", "baz")));
    }

    @Test
    public void testBasicConstantEmit() {
        ConstantOperandTestRootNode root = parse(b -> {
            b.beginRoot(LANGUAGE);
            b.beginReturn();
            b.emitIntConstant(42);
            b.endReturn();
            b.endRoot();
        });

        assertEquals(42, root.getCallTarget().call());
    }

    @Test
    public void testInstrumentationWithConstant() {
        ConstantOperandTestRootNode root = parse(b -> {
            b.beginRoot(LANGUAGE);
            b.beginReturn();
            b.beginReplaceValue();
            b.emitLoadArgument(0);
            b.endReplaceValue(42);
            b.endReturn();
            b.endRoot();
        });
        assertEquals(123, root.getCallTarget().call(123));

        root.getRootNodes().update(ConstantOperandTestRootNodeGen.newConfigBuilder().addInstrumentation(ReplaceValue.class).build());
        assertEquals(42, root.getCallTarget().call(123));
    }

}

@GenerateBytecode(languageClass = BytecodeDSLTestLanguage.class)
abstract class ConstantOperandTestRootNode extends RootNode implements BytecodeRootNode {

    protected ConstantOperandTestRootNode(TruffleLanguage<?> language, FrameDescriptor frameDescriptor) {
        super(language, frameDescriptor);
    }

    @Operation
    @ConstantOperand(type = int.class)
    public static final class DivConstantDividend {
        @Specialization
        public static int doInts(int constantOperand, int dynamicOperand) {
            return constantOperand / dynamicOperand;
        }
    }

    @Operation
    @ConstantOperand(type = int.class, specifyAtEnd = true)
    public static final class DivConstantDivisor {
        @Specialization
        public static int doInts(int dynamicOperand, int constantOperand) {
            return dynamicOperand / constantOperand;
        }
    }

    @Operation
    @ConstantOperand(type = String.class)
    @ConstantOperand(type = Object.class, specifyAtEnd = true)
    public static final class GetAttrWithDefault {
        @SuppressWarnings("unchecked")
        @TruffleBoundary
        @Specialization
        public static Object doGetAttr(String key, Object map, Object defaultValue) {
            return ((Map<String, Object>) map).getOrDefault(key, defaultValue);
        }
    }

    @Operation
    @ConstantOperand(type = int.class)
    public static final class IntConstant {
        @Specialization
        public static int doInt(int constantOperand) {
            return constantOperand;
        }
    }

    @Instrumentation
    @ConstantOperand(type = int.class, specifyAtEnd = true)
    public static final class ReplaceValue {
        @Specialization
        public static int doInt(@SuppressWarnings("unused") Object ignored, int replacement) {
            return replacement;
        }
    }

}

@GenerateBytecode(languageClass = BytecodeDSLTestLanguage.class)
@SuppressWarnings("unused")
abstract class ConstantOperandErrorRootNode extends RootNode implements BytecodeRootNode {
    protected ConstantOperandErrorRootNode(TruffleLanguage<?> language, FrameDescriptor frameDescriptor) {
        super(language, frameDescriptor);
    }

    @Operation
    @ConstantOperand(type = int.class)
    public static final class NotEnoughBeginOperands1 {
        @ExpectError("Specialization should declare at least 1 operand (one for each ConstantOperand).")
        @Specialization
        public static void doOperation(VirtualFrame frame) {
        }
    }

    @Operation
    @ConstantOperand(type = int.class)
    @ConstantOperand(type = double.class)
    public static final class NotEnoughBeginOperands2 {
        @ExpectError("Specialization should declare at least 2 operands (one for each ConstantOperand).")
        @Specialization
        public static void doOperation(VirtualFrame frame, int const1) {
        }
    }

    @Operation
    @ConstantOperand(type = int.class, specifyAtEnd = true)
    public static final class NotEnoughEndOperands1 {
        @ExpectError("Specialization should declare at least 1 operand (one for each ConstantOperand).")
        @Specialization
        public static void doOperation(VirtualFrame frame) {
        }
    }

    @Operation
    @ConstantOperand(type = int.class, specifyAtEnd = true)
    @ConstantOperand(type = double.class, specifyAtEnd = true)
    public static final class NotEnoughEndOperands2 {
        @ExpectError("Specialization should declare at least 2 operands (one for each ConstantOperand).")
        @Specialization
        public static void doOperation(VirtualFrame frame, int const1) {
        }
    }

    @Operation
    @ConstantOperand(type = int.class)
    @ConstantOperand(type = int.class, specifyAtEnd = true)
    public static final class NotEnoughBeginOrEndOperands {
        @ExpectError("Specialization should declare at least 2 operands (one for each ConstantOperand).")
        @Specialization
        public static void doOperation(VirtualFrame frame, int const1) {
        }
    }

    @Operation
    @ConstantOperand(type = int.class)
    @ConstantOperand(type = int.class, specifyAtEnd = true)
    public static final class DifferentDynamicArgumentCount {
        @Specialization
        public static void doOperation(VirtualFrame frame, int const1, Object dynamic1, int const2) {
        }

        @ExpectError("Error calculating operation signature: all specializations must have the same number of value arguments.")
        @Specialization
        public static void doOperation2(VirtualFrame frame, int const1, Object dynamic1, Object dynamic2, int const2) {
        }
    }

    @Operation
    @ConstantOperand(type = int.class)
    @ConstantOperand(type = double.class, specifyAtEnd = true)
    public static final class IncompatibleOperandType1 {
        @Specialization
        public static void doOperation(VirtualFrame frame,
                        int const1,
                        Object dynamic1,
                        @ExpectError("Parameter type String incompatible with constant operand type double.") String const2) {
        }

        @Specialization
        public static void doOperation2(VirtualFrame frame,
                        @ExpectError("Parameter type double incompatible with constant operand type int.") double const1,
                        Object dynamic1,
                        double const2) {
        }
    }

    @Operation
    @ConstantOperand(type = int.class)
    @ConstantOperand(type = String.class)
    @ConstantOperand(type = double.class, specifyAtEnd = true)
    @ConstantOperand(type = int[].class, specifyAtEnd = true)
    public static final class IncompatibleOperandType2 {
        @Specialization
        public static void doOperation(VirtualFrame frame,
                        int const1,
                        @ExpectError("Parameter type int incompatible with constant operand type String.") int const2,
                        Object dynamic1,
                        double const3,
                        @ExpectError("Parameter type double incompatible with constant operand type int[].") double const4) {
        }

        @Specialization
        public static void doOperation2(VirtualFrame frame,
                        @ExpectError("Parameter type double incompatible with constant operand type int.") double const1,
                        String const2,
                        Object dynamic1,
                        @ExpectError("Parameter type String incompatible with constant operand type double.") String const3,
                        int[] const4) {
        }
    }

    @ExpectError("An @EpilogReturn operation cannot declare constant operands.")
    @EpilogReturn
    @ConstantOperand(type = int.class)
    public static final class ConstantOperandInEpilogReturn {
        @Specialization
        public static Object doEpilog(VirtualFrame frame, int const1, Object returnValue) {
            return returnValue;
        }
    }

    @ExpectError("An @EpilogExceptional operation cannot declare constant operands.")
    @EpilogExceptional
    @ConstantOperand(type = int.class)
    public static final class ConstantOperandInEpilogExceptional {
        @Specialization
        public static void doEpilog(VirtualFrame frame, int const1, AbstractTruffleException ate) {
        }
    }

    // warnings

    @ExpectWarning({
                    "Specializations use multiple different names for this operand ([foo, bar, baz]). It is recommended to use the same name in each specialization or to explicitly provide a name for the operand.",
                    "Specializations use multiple different names for this operand ([a, b, c]). It is recommended to use the same name in each specialization or to explicitly provide a name for the operand."
    })
    @Operation
    @ConstantOperand(type = int.class)
    @ConstantOperand(type = int.class, specifyAtEnd = true)
    public static final class AmbiguousInferredParameterName {
        @Specialization
        public static void doOperation(VirtualFrame frame, int foo, int dynamic1, int a) {
        }

        @Specialization
        public static void doOperation2(VirtualFrame frame, int bar, String dynamic1, int b) {
        }

        @Specialization
        public static void doOperation3(VirtualFrame frame, int baz, Object dynamic1, int c) {
        }
    }

    @ExpectWarning("The specifyAtEnd attribute is unnecessary. This operation does not take any dynamic operands, so all operands will be provided to a single emitExplicitSpecifyAtEndTrue method.")
    @Operation
    @ConstantOperand(type = int.class, specifyAtEnd = true)
    public static final class ExplicitSpecifyAtEndTrue {
        @Specialization
        public static void doOperation(VirtualFrame frame, int const1) {
        }
    }

    @ExpectWarning("The specifyAtEnd attribute is unnecessary. This operation does not take any dynamic operands, so all operands will be provided to a single emitExplicitSpecifyAtEndFalse method.")
    @Operation
    @ConstantOperand(type = int.class, specifyAtEnd = false)
    public static final class ExplicitSpecifyAtEndFalse {
        @Specialization
        public static void doOperation(VirtualFrame frame, int const1) {
        }
    }

    @ExpectWarning("The specifyAtEnd attribute is unnecessary. This operation does not take any dynamic operands, so all operands will be provided to a single emitExplicitSpecifyAtEndInstrumentation method.")
    @Instrumentation
    @ConstantOperand(type = int.class, specifyAtEnd = true)
    public static final class ExplicitSpecifyAtEndInstrumentation {
        @Specialization
        public static void doOperation(VirtualFrame frame, int const1) {
        }
    }
}
