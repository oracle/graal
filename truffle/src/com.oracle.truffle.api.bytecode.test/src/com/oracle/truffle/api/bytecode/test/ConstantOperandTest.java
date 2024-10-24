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
import static org.junit.Assert.assertThrows;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.bytecode.BytecodeConfig;
import com.oracle.truffle.api.bytecode.BytecodeLocal;
import com.oracle.truffle.api.bytecode.BytecodeNode;
import com.oracle.truffle.api.bytecode.BytecodeParser;
import com.oracle.truffle.api.bytecode.BytecodeRootNode;
import com.oracle.truffle.api.bytecode.ConstantOperand;
import com.oracle.truffle.api.bytecode.ContinuationResult;
import com.oracle.truffle.api.bytecode.EpilogExceptional;
import com.oracle.truffle.api.bytecode.EpilogReturn;
import com.oracle.truffle.api.bytecode.GenerateBytecode;
import com.oracle.truffle.api.bytecode.GenerateBytecodeTestVariants;
import com.oracle.truffle.api.bytecode.GenerateBytecodeTestVariants.Variant;
import com.oracle.truffle.api.bytecode.Instrumentation;
import com.oracle.truffle.api.bytecode.LocalAccessor;
import com.oracle.truffle.api.bytecode.MaterializedLocalAccessor;
import com.oracle.truffle.api.bytecode.Operation;
import com.oracle.truffle.api.bytecode.Prolog;
import com.oracle.truffle.api.bytecode.test.ConstantOperandTestRootNode.ReplaceValue;
import com.oracle.truffle.api.bytecode.test.error_tests.ExpectError;
import com.oracle.truffle.api.bytecode.test.error_tests.ExpectWarning;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.exception.AbstractTruffleException;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;

@RunWith(Parameterized.class)
public class ConstantOperandTest {
    private static final BytecodeDSLTestLanguage LANGUAGE = null;

    @Parameters(name = "{0}")
    public static List<Class<? extends ConstantOperandTestRootNode>> getParameters() {
        return List.of(ConstantOperandTestRootNodeCached.class, ConstantOperandTestRootNodeUncached.class);
    }

    @Parameter(0) public Class<? extends ConstantOperandTestRootNode> interpreterClass;

    @SuppressWarnings("unchecked")
    private <T extends ConstantOperandTestRootNodeBuilder> ConstantOperandTestRootNode parse(BytecodeParser<? extends T> parser) {
        return ConstantOperandTestRootNodeBuilder.invokeCreate(interpreterClass, LANGUAGE, BytecodeConfig.DEFAULT, parser).getNode(0);
    }

    @Test
    public void testBasicConstant() {
        ConstantOperandTestRootNode root = parse(b -> {
            b.beginRoot();
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
            b.beginRoot();
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
            b.beginRoot();
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
            b.beginRoot();
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
            b.beginRoot();
            b.beginReturn();
            b.beginReplaceValue();
            b.emitLoadArgument(0);
            b.endReplaceValue(42);
            b.endReturn();
            b.endRoot();
        });
        assertEquals(123, root.getCallTarget().call(123));

        root.getRootNodes().update(ConstantOperandTestRootNodeBuilder.invokeNewConfigBuilder(interpreterClass).addInstrumentation(ReplaceValue.class).build());
        assertEquals(42, root.getCallTarget().call(123));
    }

    @Test
    public void testInstrumentationWithConstantAndYield() {
        /**
         * Regression test: instrumentation constants should be emitted even when instrumentation is
         * disabled, otherwise the layout of the constant pool changes. We expect the layout to be
         * the same in order to update continuation locations after reparsing.
         */
        ConstantOperandTestRootNode root = parse(b -> {
            b.beginRoot();
            b.beginYield();
            b.beginReplaceValue();
            b.emitLoadConstant(42);
            b.endReplaceValue(123);
            b.endYield();
            b.endRoot();
        });
        ContinuationResult cont = (ContinuationResult) root.getCallTarget().call();
        assertEquals(42, cont.getResult());

        root.getRootNodes().update(ConstantOperandTestRootNodeBuilder.invokeNewConfigBuilder(interpreterClass).addInstrumentation(ReplaceValue.class).build());
        cont = (ContinuationResult) root.getCallTarget().call();
        assertEquals(123, cont.getResult());
    }

    @Test
    public void testConstantWithFallback() {
        ConstantOperandTestRootNode root = parse(b -> {
            b.beginRoot();
            b.beginReturn();
            b.beginCheckValue(42);
            b.emitLoadArgument(0);
            b.endCheckValue();
            b.endReturn();
            b.endRoot();
        });
        assertEquals(true, root.getCallTarget().call(42));
        assertEquals(false, root.getCallTarget().call(43));
        assertEquals(false, root.getCallTarget().call("foo"));
    }

    @Test
    public void testLocalAccessor() {
        ConstantOperandTestRootNode root = parse(b -> {
            b.beginRoot();
            BytecodeLocal local = b.createLocal();
            b.beginSetCheckValue(42, local);
            b.emitLoadArgument(0);
            b.endSetCheckValue();

            b.beginReturn();
            b.emitLoadLocal(local);
            b.endReturn();
            b.endRoot();
        });
        assertEquals(true, root.getCallTarget().call(42));
        assertEquals(false, root.getCallTarget().call(43));
        assertEquals(false, root.getCallTarget().call("foo"));
    }

    @Test
    public void testConstantOperandsInProlog() {
        ConstantOperandsInPrologTestRootNode root = ConstantOperandsInPrologTestRootNodeGen.create(LANGUAGE, BytecodeConfig.DEFAULT, b -> {
            b.beginRoot("foo");
            b.beginReturn();
            b.emitLoadConstant(42L);
            b.endReturn();
            b.endRoot(1);
        }).getNode(0);
        assertEquals(42L, root.getCallTarget().call());
        assertEquals(List.of("foo", 1), root.prologEvents);

        ConstantOperandsInPrologTestRootNode root2 = ConstantOperandsInPrologTestRootNodeGen.create(LANGUAGE, BytecodeConfig.DEFAULT, b -> {
            b.beginRoot("bar");
            b.beginReturn();
            b.emitLoadConstant(42L);
            b.endReturn();
            b.endRoot(5);
        }).getNode(0);
        assertEquals(42L, root2.getCallTarget().call());
        assertEquals(List.of("bar", 5), root2.prologEvents);
    }

    @Test
    public void testPrologNull() {
        ConstantOperandsInPrologTestRootNodeGen.create(LANGUAGE, BytecodeConfig.DEFAULT, b -> {
            assertThrows(IllegalArgumentException.class, () -> b.beginRoot(null));
            b.beginRoot("foo");
            assertThrows(IllegalArgumentException.class, () -> b.emitLoadConstant(null));
            b.endRoot(0);
        }).getNode(0);
    }

    @Test
    public void testConstantNulls() {
        parse(b -> {
            b.beginRoot();
            assertThrows(IllegalArgumentException.class, () -> b.emitLoadConstant(null));
            assertThrows(IllegalArgumentException.class, () -> b.beginGetAttrWithDefault(null));
            assertThrows(IllegalArgumentException.class, () -> b.endGetAttrWithDefault(null));
            b.endRoot();
        });
    }

    @Test
    public void testConstantOperandsInPrologNestedRoot() {
        List<ConstantOperandsInPrologTestRootNode> roots = ConstantOperandsInPrologTestRootNodeGen.create(LANGUAGE, BytecodeConfig.DEFAULT, b -> {
            b.beginRoot("foo");

            b.beginRoot("bar");
            b.beginReturn();
            b.emitLoadConstant(234L);
            b.endReturn();
            b.endRoot(123);

            b.beginReturn();
            b.emitLoadConstant(42L);
            b.endReturn();
            b.endRoot(1);
        }).getNodes();

        ConstantOperandsInPrologTestRootNode foo = roots.get(0);
        assertEquals(42L, foo.getCallTarget().call());
        assertEquals(List.of("foo", 1), foo.prologEvents);

        ConstantOperandsInPrologTestRootNode bar = roots.get(1);
        assertEquals(234L, bar.getCallTarget().call());
        assertEquals(List.of("bar", 123), bar.prologEvents);
    }

}

@GenerateBytecodeTestVariants({
                @Variant(suffix = "Cached", configuration = @GenerateBytecode(languageClass = BytecodeDSLTestLanguage.class, enableYield = true, enableUncachedInterpreter = false)),
                @Variant(suffix = "Uncached", configuration = @GenerateBytecode(languageClass = BytecodeDSLTestLanguage.class, enableYield = true, enableUncachedInterpreter = true))
})

abstract class ConstantOperandTestRootNode extends RootNode implements BytecodeRootNode {

    protected ConstantOperandTestRootNode(BytecodeDSLTestLanguage language, FrameDescriptor frameDescriptor) {
        super(language, frameDescriptor);
    }

    @Operation
    @ConstantOperand(name = "dividend", type = int.class, javadoc = "The value to be divided")
    public static final class DivConstantDividend {
        @Specialization
        public static int doInts(int constantOperand, int dynamicOperand) {
            return constantOperand / dynamicOperand;
        }
    }

    @Operation
    @ConstantOperand(name = "divisor", type = int.class, javadoc = "The value to divide by", specifyAtEnd = true)
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

    @Operation
    @ConstantOperand(type = int.class)
    @SuppressWarnings("unused")
    public static final class CheckValue {
        @Specialization(guards = "arg == constantOperand")
        public static boolean doMatch(int constantOperand, int arg) {
            return true;
        }

        @Fallback
        public static boolean doNoMatch(int constantOperand, Object arg) {
            return false;
        }
    }

    @Operation
    @ConstantOperand(type = int.class)
    @ConstantOperand(type = LocalAccessor.class)
    @SuppressWarnings("unused")
    public static final class SetCheckValue {
        @Specialization(guards = "arg == constantOperand")
        public static void doMatch(VirtualFrame frame, int constantOperand, LocalAccessor setter, int arg,
                        @Bind BytecodeNode bytecode,
                        @Bind("$bytecodeIndex") int bci) {
            setter.setBoolean(bytecode, frame, true);
        }

        @Fallback
        public static void doNoMatch(VirtualFrame frame, int constantOperand, LocalAccessor setter, Object arg,
                        @Bind BytecodeNode bytecode,
                        @Bind("$bytecodeIndex") int bci) {
            setter.setBoolean(bytecode, frame, false);
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
abstract class ConstantOperandsInPrologTestRootNode extends RootNode implements BytecodeRootNode {
    public final List<Object> prologEvents = new ArrayList<>();

    protected ConstantOperandsInPrologTestRootNode(BytecodeDSLTestLanguage language, FrameDescriptor frameDescriptor) {
        super(language, frameDescriptor);
    }

    @Prolog
    @ConstantOperand(type = String.class)
    @ConstantOperand(type = int.class, specifyAtEnd = true)
    public static final class PrologOperation {
        @Specialization
        @TruffleBoundary
        public static void doVoid(String name, int number, @Bind ConstantOperandsInPrologTestRootNode root) {
            root.prologEvents.add(name);
            root.prologEvents.add(number);
        }
    }

}

@GenerateBytecode(languageClass = BytecodeDSLTestLanguage.class)
@SuppressWarnings("unused")
abstract class ConstantOperandErrorRootNode extends RootNode implements BytecodeRootNode {
    protected ConstantOperandErrorRootNode(BytecodeDSLTestLanguage language, FrameDescriptor frameDescriptor) {
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

        @ExpectError("Error calculating operation signature: all specializations must have the same number of operands.")
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
                        @ExpectError("Constant operand parameter must have type double.") String const2) {
        }

        @Specialization
        public static void doOperation2(VirtualFrame frame,
                        @ExpectError("Constant operand parameter must have type int.") double const1,
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
                        @ExpectError("Constant operand parameter must have type String.") int const2,
                        Object dynamic1,
                        double const3,
                        @ExpectError("Constant operand parameter must have type int[].") double const4) {
        }

        @Specialization
        public static void doOperation2(VirtualFrame frame,
                        @ExpectError("Constant operand parameter must have type int.") double const1,
                        String const2,
                        Object dynamic1,
                        @ExpectError("Constant operand parameter must have type double.") String const3,
                        int[] const4) {
        }
    }

    @ExpectError("Nodes cannot be used as constant operands.")
    @Operation
    @ConstantOperand(type = Node.class)
    public static final class NodeConstant {
        @Specialization
        public static void doNode(Node n) {
        }
    }

    // No error expected.
    @Operation
    @ConstantOperand(type = RootNode.class)
    public static final class RootNodeConstant {
        @Specialization
        public static void doNode(RootNode n) {
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

    @Operation
    @ExpectError("Constant operands with non-zero dimensions are not supported.")
    @ConstantOperand(type = int[].class, dimensions = 1)
    public static final class UnsupportedDimensions {
        @Specialization
        public static void doOperation(VirtualFrame frame, int[] consts) {
        }
    }

    @Operation
    @ExpectError("MaterializedLocalAccessor cannot be used because materialized local accesses are disabled.%")
    @ConstantOperand(type = MaterializedLocalAccessor.class)
    public static final class UnsupportedMaterializedLocalAccessor {
        @Specialization
        public static void doOperation(VirtualFrame frame, MaterializedLocalAccessor accessor) {
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

    /**
     * Regression test: using the name "returnValue" for a constant conflicted with an internal
     * parameter name. Operations with operands named "returnValue" should be permitted without
     * issue. Operand names that cannot be Java identifiers are disallowed.
     */
    @Operation
    @ConstantOperand(type = Object.class, name = "returnValue")
    public static final class OperationWithPermittedConstantName {
        @Specialization
        public static void doObject(@SuppressWarnings("unused") Object constant) {
            // do nothing
        }
    }

    @Operation
    @ExpectWarning({
                    "Invalid constant operand name \" \". Operand name must be a valid Java identifier.",
                    "Invalid constant operand name \"4abc\". Operand name must be a valid Java identifier.",
                    "Invalid constant operand name \"returnValue#\". Operand name must be a valid Java identifier.",
    })
    @ConstantOperand(type = Object.class, name = " ")
    @ConstantOperand(type = Object.class, name = "4abc")
    @ConstantOperand(type = Object.class, name = "returnValue#")
    public static final class OperationWithForbiddenConstantName {
        @Specialization
        @SuppressWarnings("unused")
        public static void doObject(Object const1, Object const2, Object const3) {
            // do nothing
        }

    }
}
