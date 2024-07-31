/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.bytecode.BytecodeConfig;
import com.oracle.truffle.api.bytecode.BytecodeRootNodes;
import com.oracle.truffle.api.bytecode.BytecodeParser;
import com.oracle.truffle.api.bytecode.BytecodeRootNode;
import com.oracle.truffle.api.bytecode.GenerateBytecode;
import com.oracle.truffle.api.bytecode.GenerateBytecodeTestVariants;
import com.oracle.truffle.api.bytecode.GenerateBytecodeTestVariants.Variant;
import com.oracle.truffle.api.bytecode.Operation;
import com.oracle.truffle.api.bytecode.OperationProxy;
import com.oracle.truffle.api.bytecode.ShortCircuitOperation;
import com.oracle.truffle.api.bytecode.ShortCircuitOperation.Operator;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;

@RunWith(Parameterized.class)
public class ShortCircuitTest {
    @Parameters(name = "{0}")
    public static List<Class<? extends BytecodeNodeWithShortCircuit>> getInterpreterClasses() {
        return List.of(BytecodeNodeWithShortCircuitBase.class, BytecodeNodeWithShortCircuitWithBE.class);
    }

    @Parameter(0) public Class<? extends BytecodeNodeWithShortCircuit> interpreterClass;

    public static <T extends BytecodeNodeWithShortCircuitBuilder> BytecodeNodeWithShortCircuit parseNode(Class<? extends BytecodeNodeWithShortCircuit> interpreterClass,
                    BytecodeParser<T> builder) {
        BytecodeRootNodes<BytecodeNodeWithShortCircuit> nodes = BytecodeNodeWithShortCircuitBuilder.invokeCreate((Class<? extends BytecodeNodeWithShortCircuit>) interpreterClass,
                        BytecodeConfig.DEFAULT, builder);
        return nodes.getNode(nodes.count() - 1);
    }

    public <T extends BytecodeNodeWithShortCircuitBuilder> BytecodeNodeWithShortCircuit parseNode(BytecodeParser<T> builder) {
        return parseNode(interpreterClass, builder);
    }

    @Test
    public void testObjectAnd() {
        Object foo = new Object();

        // foo -> foo
        BytecodeNodeWithShortCircuit root = parseNode(b -> {
            b.beginRoot(null);
            b.beginReturn();
            b.beginObjectAnd();
            b.emitLoadConstant(foo);
            b.endObjectAnd();
            b.endReturn();
            b.endRoot();
        });
        assertEquals(foo, root.getCallTarget().call());

        // 0 -> 0
        root = parseNode(b -> {
            b.beginRoot(null);
            b.beginReturn();
            b.beginObjectAnd();
            b.emitLoadConstant(0);
            b.endObjectAnd();
            b.endReturn();
            b.endRoot();
        });
        assertEquals(0, root.getCallTarget().call());

        // true && 123 && foo -> foo
        root = parseNode(b -> {
            b.beginRoot(null);
            b.beginReturn();
            b.beginObjectAnd();
            b.emitLoadConstant(true);
            b.emitLoadConstant(123);
            b.emitLoadConstant(foo);
            b.endObjectAnd();
            b.endReturn();
            b.endRoot();
        });
        assertEquals(foo, root.getCallTarget().call());

        // true && 0 && foo -> 0
        root = parseNode(b -> {
            b.beginRoot(null);
            b.beginReturn();
            b.beginObjectAnd();
            b.emitLoadConstant(true);
            b.emitLoadConstant(0);
            b.emitLoadConstant(foo);
            b.endObjectAnd();
            b.endReturn();
            b.endRoot();
        });
        assertEquals(0, root.getCallTarget().call());
    }

    @Test
    public void testBooleanAnd() {
        Object foo = new Object();

        // foo -> true
        BytecodeNodeWithShortCircuit root = parseNode(b -> {
            b.beginRoot(null);
            b.beginReturn();
            b.beginBoolAnd();
            b.emitLoadConstant(foo);
            b.endBoolAnd();
            b.endReturn();
            b.endRoot();
        });
        assertEquals(true, root.getCallTarget().call());

        // 0 -> false
        root = parseNode(b -> {
            b.beginRoot(null);
            b.beginReturn();
            b.beginBoolAnd();
            b.emitLoadConstant(0);
            b.endBoolAnd();
            b.endReturn();
            b.endRoot();
        });
        assertEquals(false, root.getCallTarget().call());

        // true && 123 && foo -> true
        root = parseNode(b -> {
            b.beginRoot(null);
            b.beginReturn();
            b.beginBoolAnd();
            b.emitLoadConstant(true);
            b.emitLoadConstant(123);
            b.emitLoadConstant(foo);
            b.endBoolAnd();
            b.endReturn();
            b.endRoot();
        });
        assertEquals(true, root.getCallTarget().call());

        // true && 0 && foo -> false
        root = parseNode(b -> {
            b.beginRoot(null);
            b.beginReturn();
            b.beginBoolAnd();
            b.emitLoadConstant(true);
            b.emitLoadConstant(0);
            b.emitLoadConstant(foo);
            b.endBoolAnd();
            b.endReturn();
            b.endRoot();
        });
        assertEquals(false, root.getCallTarget().call());
    }

    @Test
    public void testObjectOr() {
        Object foo = new Object();

        // foo -> foo
        BytecodeNodeWithShortCircuit root = parseNode(b -> {
            b.beginRoot(null);
            b.beginReturn();
            b.beginObjectOr();
            b.emitLoadConstant(foo);
            b.endObjectOr();
            b.endReturn();
            b.endRoot();
        });
        assertEquals(foo, root.getCallTarget().call());

        // 0 -> 0
        root = parseNode(b -> {
            b.beginRoot(null);
            b.beginReturn();
            b.beginObjectOr();
            b.emitLoadConstant(0);
            b.endObjectOr();
            b.endReturn();
            b.endRoot();
        });
        assertEquals(0, root.getCallTarget().call());

        // false || 0 || foo -> foo
        root = parseNode(b -> {
            b.beginRoot(null);
            b.beginReturn();
            b.beginObjectOr();
            b.emitLoadConstant(false);
            b.emitLoadConstant(0);
            b.emitLoadConstant(foo);
            b.endObjectOr();
            b.endReturn();
            b.endRoot();
        });
        assertEquals(foo, root.getCallTarget().call());

        // false || 123 || foo -> 123
        root = parseNode(b -> {
            b.beginRoot(null);
            b.beginReturn();
            b.beginObjectOr();
            b.emitLoadConstant(false);
            b.emitLoadConstant(123);
            b.emitLoadConstant(foo);
            b.endObjectOr();
            b.endReturn();
            b.endRoot();
        });
        assertEquals(123, root.getCallTarget().call());
    }

    @Test
    public void testBooleanOr() {
        Object foo = new Object();

        // foo -> true
        BytecodeNodeWithShortCircuit root = parseNode(b -> {
            b.beginRoot(null);
            b.beginReturn();
            b.beginBoolOr();
            b.emitLoadConstant(foo);
            b.endBoolOr();
            b.endReturn();
            b.endRoot();
        });
        assertEquals(true, root.getCallTarget().call());

        // 0 -> false
        root = parseNode(b -> {
            b.beginRoot(null);
            b.beginReturn();
            b.beginBoolOr();
            b.emitLoadConstant(0);
            b.endBoolOr();
            b.endReturn();
            b.endRoot();
        });
        assertEquals(false, root.getCallTarget().call());

        // false || 0 || foo -> true
        root = parseNode(b -> {
            b.beginRoot(null);
            b.beginReturn();
            b.beginBoolOr();
            b.emitLoadConstant(false);
            b.emitLoadConstant(0);
            b.emitLoadConstant(foo);
            b.endBoolOr();
            b.endReturn();
            b.endRoot();
        });
        assertEquals(true, root.getCallTarget().call());

        // false || 123 || foo -> true
        root = parseNode(b -> {
            b.beginRoot(null);
            b.beginReturn();
            b.beginBoolOr();
            b.emitLoadConstant(false);
            b.emitLoadConstant(123);
            b.emitLoadConstant(foo);
            b.endBoolOr();
            b.endReturn();
            b.endRoot();
        });
        assertEquals(true, root.getCallTarget().call());
    }

}

@GenerateBytecodeTestVariants({
                @Variant(suffix = "Base", configuration = @GenerateBytecode(languageClass = BytecodeDSLTestLanguage.class)),
                @Variant(suffix = "WithBE", configuration = @GenerateBytecode(languageClass = BytecodeDSLTestLanguage.class, enableQuickening = true, boxingEliminationTypes = {boolean.class,
                                int.class}))
})
@OperationProxy(value = BooleanConverterOperationProxy.class, javadoc = "Converts a value to its boolean truthy value.")
/**
 * Note how different boolean converters are used. The converter need not be declared as
 * an @Operation or @OperationProxy, but if so, it should validate like an implicit @Operation.
 *
 * Also note that converters can be repeated without introducing duplicate operations.
 */
@ShortCircuitOperation(name = "ObjectAnd", operator = Operator.AND_RETURN_VALUE, booleanConverter = BytecodeNodeWithShortCircuit.BooleanConverterOperation.class)
@ShortCircuitOperation(name = "ObjectOr", operator = Operator.OR_RETURN_VALUE, booleanConverter = BooleanConverterOperationProxy.class)
@ShortCircuitOperation(name = "BoolAnd", operator = Operator.AND_RETURN_CONVERTED, booleanConverter = BytecodeNodeWithShortCircuit.BooleanConverterNonOperation.class)
@ShortCircuitOperation(name = "BoolOr", operator = Operator.OR_RETURN_CONVERTED, booleanConverter = BytecodeNodeWithShortCircuit.BooleanConverterNonOperation.class)
abstract class BytecodeNodeWithShortCircuit extends RootNode implements BytecodeRootNode {
    protected BytecodeNodeWithShortCircuit(TruffleLanguage<?> language, FrameDescriptor frameDescriptor) {
        super(language, frameDescriptor);
    }

    @Operation
    public static final class BooleanConverterOperation {
        @Specialization
        public static boolean fromInt(int x) {
            return x != 0;
        }

        @Specialization
        public static boolean fromBoolean(boolean b) {
            return b;
        }

        @Specialization
        public static boolean fromObject(Object o) {
            return o != null;
        }
    }

    public static final class BooleanConverterNonOperation {
        @Specialization
        public static boolean fromInt(int x) {
            return x != 0;
        }

        @Specialization
        public static boolean fromBoolean(boolean b) {
            return b;
        }

        @Specialization
        public static boolean fromObject(Object o) {
            return o != null;
        }
    }
}

@SuppressWarnings("truffle-inlining")
@OperationProxy.Proxyable
abstract class BooleanConverterOperationProxy extends Node {
    public abstract boolean execute(Object o);

    @Specialization
    public static boolean fromInt(int x) {
        return x != 0;
    }

    @Specialization
    public static boolean fromBoolean(boolean b) {
        return b;
    }

    @Specialization
    public static boolean fromObject(Object o) {
        return o != null;
    }
}
