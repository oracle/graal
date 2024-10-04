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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.bytecode.BytecodeConfig;
import com.oracle.truffle.api.bytecode.BytecodeLocal;
import com.oracle.truffle.api.bytecode.BytecodeNode;
import com.oracle.truffle.api.bytecode.BytecodeParser;
import com.oracle.truffle.api.bytecode.BytecodeRootNode;
import com.oracle.truffle.api.bytecode.BytecodeRootNodes;
import com.oracle.truffle.api.bytecode.ConstantOperand;
import com.oracle.truffle.api.bytecode.ContinuationResult;
import com.oracle.truffle.api.bytecode.ContinuationRootNode;
import com.oracle.truffle.api.bytecode.GenerateBytecode;
import com.oracle.truffle.api.bytecode.GenerateBytecodeTestVariants;
import com.oracle.truffle.api.bytecode.GenerateBytecodeTestVariants.Variant;
import com.oracle.truffle.api.bytecode.LocalAccessor;
import com.oracle.truffle.api.bytecode.LocalRangeAccessor;
import com.oracle.truffle.api.bytecode.Operation;
import com.oracle.truffle.api.bytecode.Variadic;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameInstance;
import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.api.frame.FrameSlotTypeException;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.nodes.UnexpectedResultException;

@RunWith(Parameterized.class)
public class LocalHelpersTest {
    @Parameters(name = "{0}")
    public static List<Class<? extends BytecodeNodeWithLocalIntrospection>> getInterpreterClasses() {
        return List.of(BytecodeNodeWithLocalIntrospectionBase.class,
                        BytecodeNodeWithLocalIntrospectionBaseDefault.class,
                        BytecodeNodeWithLocalIntrospectionWithBEObjectDefault.class,
                        BytecodeNodeWithLocalIntrospectionWithBENullDefault.class,
                        BytecodeNodeWithLocalIntrospectionWithBEIllegal.class);
    }

    @Parameter(0) public Class<? extends BytecodeNodeWithLocalIntrospection> interpreterClass;

    public static BytecodeLocal makeLocal(BytecodeNodeWithLocalIntrospectionBuilder b, String name) {
        return b.createLocal(name, null);
    }

    public static <T extends BytecodeNodeWithLocalIntrospectionBuilder> BytecodeNodeWithLocalIntrospection parseNode(Class<? extends BytecodeNodeWithLocalIntrospection> interpreterClass,
                    BytecodeParser<T> builder) {
        BytecodeRootNodes<BytecodeNodeWithLocalIntrospection> nodes = BytecodeNodeWithLocalIntrospectionBuilder.invokeCreate((Class<? extends BytecodeNodeWithLocalIntrospection>) interpreterClass,
                        null, BytecodeConfig.DEFAULT, builder);
        return nodes.getNode(0);
    }

    private Object getLocalDefaultValue() {
        if (interpreterClass == BytecodeNodeWithLocalIntrospectionBaseDefault.class || interpreterClass == BytecodeNodeWithLocalIntrospectionWithBEObjectDefault.class) {
            return BytecodeNodeWithLocalIntrospection.DEFAULT;
        }
        if (interpreterClass == BytecodeNodeWithLocalIntrospectionWithBENullDefault.class) {
            return null;
        }
        throw new AssertionError();
    }

    private boolean hasLocalDefaultValue() {
        return interpreterClass == BytecodeNodeWithLocalIntrospectionBaseDefault.class || interpreterClass == BytecodeNodeWithLocalIntrospectionWithBEObjectDefault.class ||
                        interpreterClass == BytecodeNodeWithLocalIntrospectionWithBENullDefault.class;
    }

    public <T extends BytecodeNodeWithLocalIntrospectionBuilder> BytecodeNodeWithLocalIntrospection parseNode(BytecodeParser<T> builder) {
        return parseNode(interpreterClass, builder);
    }

    @Test
    public void testGetLocalSimple() {
        /* @formatter:off
         *
         * foo = 42
         * bar = arg0
         * return getLocal(arg1)
         *
         * @formatter:on
         */
        BytecodeNodeWithLocalIntrospection root = parseNode(b -> {
            b.beginRoot();

            b.beginBlock();
            BytecodeLocal foo = makeLocal(b, "foo");
            BytecodeLocal bar = makeLocal(b, "bar");

            b.beginStoreLocal(foo);
            b.emitLoadConstant(42);
            b.endStoreLocal();

            b.beginStoreLocal(bar);
            b.emitLoadArgument(0);
            b.endStoreLocal();

            b.beginIfThenElse();

            b.beginSame();
            b.emitLoadArgument(1);
            b.emitLoadConstant(0);
            b.endSame();
            b.beginReturn();
            b.emitGetLocal(foo.getLocalOffset());
            b.endReturn();
            b.beginReturn();
            b.emitLoadArgument(0);
            b.endReturn();
            b.endIfThenElse();

            b.endBlock();

            b.endRoot();
        });

        assertEquals(42, root.getCallTarget().call(123, 0));
        assertEquals(123, root.getCallTarget().call(123, 1));
    }

    @Test
    public void testGetLocalRangeAccessor() {

        BytecodeNodeWithLocalIntrospection root = parseNode(b -> {
            b.beginRoot();

            b.beginBlock();
            BytecodeLocal v0 = makeLocal(b, "v0");
            BytecodeLocal v1 = makeLocal(b, "v1");
            BytecodeLocal[] locals = new BytecodeLocal[]{v0, v1};

            for (int offset = 0; offset < 2; offset++) {
                b.beginStoreLocal(locals[offset]);
                b.emitLoadConstant(true);
                b.endStoreLocal();
                b.emitGetLocalRangeAccessor(locals, FrameSlotKind.Boolean, offset);

                b.beginStoreLocal(locals[offset]);
                b.emitLoadConstant((byte) 2);
                b.endStoreLocal();
                b.emitGetLocalRangeAccessor(locals, FrameSlotKind.Byte, offset);

                b.beginStoreLocal(locals[offset]);
                b.emitLoadConstant(42);
                b.endStoreLocal();
                b.emitGetLocalRangeAccessor(locals, FrameSlotKind.Int, offset);

                b.beginStoreLocal(locals[offset]);
                b.emitLoadConstant(42L);
                b.endStoreLocal();
                b.emitGetLocalRangeAccessor(locals, FrameSlotKind.Long, offset);

                b.beginStoreLocal(locals[offset]);
                b.emitLoadConstant(3.14f);
                b.endStoreLocal();
                b.emitGetLocalRangeAccessor(locals, FrameSlotKind.Float, offset);

                b.beginStoreLocal(locals[offset]);
                b.emitLoadConstant(4.0d);
                b.endStoreLocal();
                b.emitGetLocalRangeAccessor(locals, FrameSlotKind.Double, offset);
            }

            b.endBlock();

            b.endRoot();
        });

        root.getCallTarget().call();
    }

    @Test
    public void testSetLocalRangeAccessor() {
        /* @formatter:off
         *
         * foo = true
         * getLocalTagged(BOOLEAN, 0)
         * foo = (byte) 2
         * getLocalTagged(BYTE, 0)
         * ...
         * foo = "hello"
         * return getLocalTagged(OBJECT, 0)
         *
         * @formatter:on
         */
        BytecodeNodeWithLocalIntrospection root = parseNode(b -> {
            b.beginRoot();

            b.beginBlock();
            BytecodeLocal v0 = makeLocal(b, "v0");
            BytecodeLocal v1 = makeLocal(b, "v1");
            BytecodeLocal[] locals = new BytecodeLocal[]{v0, v1};

            for (int offset = 0; offset < 2; offset++) {

                b.beginSetLocalRangeAccessor(locals, FrameSlotKind.Boolean, offset);
                b.emitLoadConstant(true);
                b.endSetLocalRangeAccessor();
                b.emitGetLocalRangeAccessor(locals, FrameSlotKind.Boolean, offset);

                b.beginSetLocalRangeAccessor(locals, FrameSlotKind.Byte, offset);
                b.emitLoadConstant((byte) 2);
                b.endSetLocalRangeAccessor();
                b.emitGetLocalRangeAccessor(locals, FrameSlotKind.Byte, offset);

                b.beginSetLocalRangeAccessor(locals, FrameSlotKind.Int, offset);
                b.emitLoadConstant(42);
                b.endSetLocalRangeAccessor();
                b.emitGetLocalRangeAccessor(locals, FrameSlotKind.Int, offset);

                b.beginSetLocalRangeAccessor(locals, FrameSlotKind.Long, offset);
                b.emitLoadConstant(42L);
                b.endSetLocalRangeAccessor();
                b.emitGetLocalRangeAccessor(locals, FrameSlotKind.Long, offset);

                b.beginSetLocalRangeAccessor(locals, FrameSlotKind.Float, offset);
                b.emitLoadConstant(3.14f);
                b.endSetLocalRangeAccessor();
                b.emitGetLocalRangeAccessor(locals, FrameSlotKind.Float, offset);

                b.beginSetLocalRangeAccessor(locals, FrameSlotKind.Double, offset);
                b.emitLoadConstant(4.0d);
                b.endSetLocalRangeAccessor();
                b.emitGetLocalRangeAccessor(locals, FrameSlotKind.Double, offset);

                b.beginSetLocalRangeAccessor(locals, FrameSlotKind.Object, offset);
                b.emitLoadConstant("hello");
                b.endSetLocalRangeAccessor();

            }
            b.endBlock();

            b.endRoot();
        });

        root.getCallTarget().call();
    }

    @Test
    public void testGetLocalAccessor() {
        /* @formatter:off
         *
         * foo = true
         * getLocalTagged(BOOLEAN, 0)
         * foo = (byte) 2
         * getLocalTagged(BYTE, 0)
         * ...
         * foo = "hello"
         * return getLocalTagged(OBJECT, 0)
         *
         * @formatter:on
         */
        BytecodeNodeWithLocalIntrospection root = parseNode(b -> {
            b.beginRoot();

            b.beginBlock();
            BytecodeLocal foo = makeLocal(b, "foo");

            b.beginStoreLocal(foo);
            b.emitLoadConstant(true);
            b.endStoreLocal();
            b.emitGetLocalAccessor(foo, FrameSlotKind.Boolean);

            b.beginStoreLocal(foo);
            b.emitLoadConstant((byte) 2);
            b.endStoreLocal();
            b.emitGetLocalAccessor(foo, FrameSlotKind.Byte);

            b.beginStoreLocal(foo);
            b.emitLoadConstant(42);
            b.endStoreLocal();
            b.emitGetLocalAccessor(foo, FrameSlotKind.Int);

            b.beginStoreLocal(foo);
            b.emitLoadConstant(42L);
            b.endStoreLocal();
            b.emitGetLocalAccessor(foo, FrameSlotKind.Long);

            b.beginStoreLocal(foo);
            b.emitLoadConstant(3.14f);
            b.endStoreLocal();
            b.emitGetLocalAccessor(foo, FrameSlotKind.Float);

            b.beginStoreLocal(foo);
            b.emitLoadConstant(4.0d);
            b.endStoreLocal();
            b.emitGetLocalAccessor(foo, FrameSlotKind.Double);

            b.beginStoreLocal(foo);
            b.emitLoadConstant("hello");
            b.endStoreLocal();

            b.beginReturn();
            b.emitGetLocalAccessor(foo, FrameSlotKind.Object);
            b.endReturn();

            b.endBlock();

            b.endRoot();
        });

        assertEquals("hello", root.getCallTarget().call());
    }

    @Test
    public void testSetLocalAccessor() {
        /* @formatter:off
         *
         * foo = true
         * getLocalTagged(BOOLEAN, 0)
         * foo = (byte) 2
         * getLocalTagged(BYTE, 0)
         * ...
         * foo = "hello"
         * return getLocalTagged(OBJECT, 0)
         *
         * @formatter:on
         */
        BytecodeNodeWithLocalIntrospection root = parseNode(b -> {
            b.beginRoot();

            b.beginBlock();
            BytecodeLocal foo = makeLocal(b, "foo");

            b.beginSetLocalAccessor(foo, FrameSlotKind.Boolean);
            b.emitLoadConstant(true);
            b.endSetLocalAccessor();
            b.emitGetLocalAccessor(foo, FrameSlotKind.Boolean);

            b.beginSetLocalAccessor(foo, FrameSlotKind.Byte);
            b.emitLoadConstant((byte) 2);
            b.endSetLocalAccessor();
            b.emitGetLocalAccessor(foo, FrameSlotKind.Byte);

            b.beginSetLocalAccessor(foo, FrameSlotKind.Int);
            b.emitLoadConstant(42);
            b.endSetLocalAccessor();
            b.emitGetLocalAccessor(foo, FrameSlotKind.Int);

            b.beginSetLocalAccessor(foo, FrameSlotKind.Long);
            b.emitLoadConstant(42L);
            b.endSetLocalAccessor();
            b.emitGetLocalAccessor(foo, FrameSlotKind.Long);

            b.beginSetLocalAccessor(foo, FrameSlotKind.Float);
            b.emitLoadConstant(3.14f);
            b.endSetLocalAccessor();
            b.emitGetLocalAccessor(foo, FrameSlotKind.Float);

            b.beginSetLocalAccessor(foo, FrameSlotKind.Double);
            b.emitLoadConstant(4.0d);
            b.endSetLocalAccessor();
            b.emitGetLocalAccessor(foo, FrameSlotKind.Double);

            b.beginSetLocalAccessor(foo, FrameSlotKind.Object);
            b.emitLoadConstant("hello");
            b.endSetLocalAccessor();

            b.beginReturn();
            b.emitGetLocalAccessor(foo, FrameSlotKind.Object);
            b.endReturn();

            b.endBlock();

            b.endRoot();
        });

        assertEquals("hello", root.getCallTarget().call());
    }

    @Test
    public void testGetLocalUsingBytecodeLocalIndex() {
        /* @formatter:off
         *
         * foo = 42
         * bar = arg1
         * return getLocal(reservedLocalIndex)
         *
         * @formatter:on
         */
        BytecodeNodeWithLocalIntrospection root = parseNode(b -> {
            b.beginRoot();

            b.beginBlock();
            BytecodeLocal foo = makeLocal(b, "foo");
            BytecodeLocal bar = makeLocal(b, "bar");

            b.beginStoreLocal(foo);
            b.emitLoadConstant(42);
            b.endStoreLocal();

            b.beginStoreLocal(bar);
            b.emitLoadArgument(1);
            b.endStoreLocal();

            b.beginReturn();
            b.emitGetLocalUsingBytecodeLocalIndex();
            b.endReturn();

            b.endBlock();

            BytecodeNodeWithLocalIntrospection rootNode = b.endRoot();
            rootNode.reservedLocalIndex = bar.getLocalOffset();
        });

        assertEquals(42, root.getCallTarget().call(123, 42));
        assertEquals(1024, root.getCallTarget().call(123, 1024));
    }

    @Test
    public void testGetLocalsSimple() {
        /* @formatter:off
         *
         * foo = 42
         * bar = arg0
         * return getLocals()
         *
         * @formatter:on
         */
        BytecodeNodeWithLocalIntrospection root = parseNode(b -> {
            b.beginRoot();

            b.beginBlock();
            BytecodeLocal foo = makeLocal(b, "foo");
            BytecodeLocal bar = makeLocal(b, "bar");

            b.beginStoreLocal(foo);
            b.emitLoadConstant(42);
            b.endStoreLocal();

            b.beginStoreLocal(bar);
            b.emitLoadArgument(0);
            b.endStoreLocal();

            b.beginReturn();
            b.emitGetLocals();
            b.endReturn();

            b.endBlock();

            b.endRoot();
        });

        assertEquals(Map.of("foo", 42, "bar", 123), root.getCallTarget().call(123));
    }

    @Test
    public void testGetLocalsNestedRootNode() {
        /* @formatter:off
         *
         * foo = 42
         * bar = 123
         *
         * def nested(a0) {
         *   baz = 1337
         *   qux = a0
         *   return getLocals()
         * }
         *
         * if (arg0) {
         *   return getLocals()
         * else {
         *   return nested(4321)
         * }
         *
         * @formatter:on
         */
        BytecodeNodeWithLocalIntrospection root = parseNode(b -> {
            b.beginRoot();

            b.beginBlock();
            BytecodeLocal foo = makeLocal(b, "foo");
            BytecodeLocal bar = makeLocal(b, "bar");

            b.beginStoreLocal(foo);
            b.emitLoadConstant(42);
            b.endStoreLocal();

            b.beginStoreLocal(bar);
            b.emitLoadConstant(123);
            b.endStoreLocal();

            b.beginRoot();
            b.beginBlock();
            BytecodeLocal baz = makeLocal(b, "baz");
            BytecodeLocal qux = makeLocal(b, "qux");

            b.beginStoreLocal(baz);
            b.emitLoadConstant(1337);
            b.endStoreLocal();

            b.beginStoreLocal(qux);
            b.emitLoadArgument(0);
            b.endStoreLocal();

            b.beginReturn();
            b.emitGetLocals();
            b.endReturn();

            b.endBlock();
            BytecodeNodeWithLocalIntrospection nested = b.endRoot();

            b.beginIfThenElse();

            b.emitLoadArgument(0);

            b.beginReturn();
            b.emitGetLocals();
            b.endReturn();

            b.beginReturn();
            b.beginInvoke();
            b.emitLoadConstant(nested);
            b.emitLoadConstant(4321);
            b.endInvoke();
            b.endReturn();

            b.endIfThenElse();

            b.endBlock();

            b.endRoot();
        });

        assertEquals(Map.of("foo", 42, "bar", 123), root.getCallTarget().call(true));
        assertEquals(Map.of("baz", 1337, "qux", 4321), root.getCallTarget().call(false));
    }

    @Test
    public void testGetLocalsContinuation() {
        /* @formatter:off
         *
         * def bar() {
         *   y = yield 0
         *   if (y) {
         *     x = 42
         *   } else {
         *     x = 123
         *   }
         *   getLocals()
         * }
         *
         * def foo(arg0) {
         *   c = bar()
         *   continue(c, arg0)
         * }
         *
         * @formatter:on
         */
        BytecodeNodeWithLocalIntrospection bar = parseNode(b -> {
            b.beginRoot();
            b.beginBlock();
            BytecodeLocal x = makeLocal(b, "x");
            BytecodeLocal y = makeLocal(b, "y");

            b.beginStoreLocal(y);
            b.beginYield();
            b.emitLoadConstant(0);
            b.endYield();
            b.endStoreLocal();

            b.beginIfThenElse();

            b.emitLoadLocal(y);

            b.beginStoreLocal(x);
            b.emitLoadConstant(42);
            b.endStoreLocal();

            b.beginStoreLocal(x);
            b.emitLoadConstant(123);
            b.endStoreLocal();

            b.endIfThenElse();

            b.beginReturn();
            b.emitGetLocals();
            b.endReturn();

            b.endBlock();
            b.endRoot();
        });

        BytecodeNodeWithLocalIntrospection foo = parseNode(b -> {
            b.beginRoot();
            b.beginBlock();
            BytecodeLocal c = b.createLocal();

            b.beginStoreLocal(c);
            b.beginInvoke();
            b.emitLoadConstant(bar);
            b.endInvoke();
            b.endStoreLocal();

            b.beginReturn();
            b.beginContinue();
            b.emitLoadLocal(c);
            b.emitLoadArgument(0);
            b.endContinue();
            b.endReturn();

            b.endBlock();
            b.endRoot();
        });

        assertEquals(Map.of("x", 42, "y", true), foo.getCallTarget().call(true));
        assertEquals(Map.of("x", 123, "y", false), foo.getCallTarget().call(false));
    }

    @Test
    public void testSetLocalSimple() {
        /* @formatter:off
         *
         * foo = 42
         * bar = 123
         * setLocal(arg0, arg1)
         * return makePair(foo, bar)
         *
         * @formatter:on
         */
        BytecodeNodeWithLocalIntrospection root = parseNode(b -> {
            b.beginRoot();

            b.beginBlock();
            BytecodeLocal foo = makeLocal(b, "foo");
            BytecodeLocal bar = makeLocal(b, "bar");

            b.beginStoreLocal(foo);
            b.emitLoadConstant(42L);
            b.endStoreLocal();

            b.beginStoreLocal(bar);
            b.emitLoadConstant(123L);
            b.endStoreLocal();

            b.beginSetLocal();
            b.emitLoadArgument(0);
            b.emitLoadArgument(1);
            b.endSetLocal();

            b.beginReturn();
            b.beginMakePair();
            b.emitLoadLocal(foo);
            b.emitLoadLocal(bar);
            b.endMakePair();
            b.endReturn();

            b.endBlock();

            b.endRoot();
        });

        assertEquals(new Pair(777L, 123L), root.getCallTarget().call(0, 777L));
        assertEquals(new Pair(42L, 777L), root.getCallTarget().call(1, 777L));
        // If BE enabled, local reads should succeed even if the type changes.
        assertEquals(new Pair(true, 123L), root.getCallTarget().call(0, true));
        assertEquals(new Pair(42L, false), root.getCallTarget().call(1, false));
        assertEquals(new Pair("dog", 123L), root.getCallTarget().call(0, "dog"));
        assertEquals(new Pair(42L, "cat"), root.getCallTarget().call(1, "cat"));
    }

    @Test
    public void testSetLocalUsingBytecodeLocalIndex() {
        /* @formatter:off
         *
         * foo = 42
         * bar = 123
         * setLocal(reservedLocalIndex, arg0)
         * return makePair(foo, bar)
         *
         * @formatter:on
         */
        BytecodeNodeWithLocalIntrospection root = parseNode(b -> {
            b.beginRoot();

            b.beginBlock();
            BytecodeLocal foo = makeLocal(b, "foo");
            BytecodeLocal bar = makeLocal(b, "bar");

            b.beginStoreLocal(foo);
            b.emitLoadConstant(42L);
            b.endStoreLocal();

            b.beginStoreLocal(bar);
            b.emitLoadConstant(123L);
            b.endStoreLocal();

            b.beginSetLocalUsingBytecodeLocalIndex();
            b.emitLoadArgument(0);
            b.endSetLocalUsingBytecodeLocalIndex();

            b.beginReturn();
            b.beginMakePair();
            b.emitLoadLocal(foo);
            b.emitLoadLocal(bar);
            b.endMakePair();
            b.endReturn();

            b.endBlock();

            BytecodeNodeWithLocalIntrospection rootNode = b.endRoot();
            rootNode.reservedLocalIndex = bar.getLocalOffset();
        });

        assertEquals(new Pair(42L, 777L), root.getCallTarget().call(777L));
        // If BE enabled, local reads should succeed even if the type changes.
        assertEquals(new Pair(42L, false), root.getCallTarget().call(false));
        assertEquals(new Pair(42L, "cat"), root.getCallTarget().call("cat"));
    }

    @Test
    public void testGetLocalsSimpleStacktrace() {
        /* @formatter:off
         *
         * def bar() {
         *   y = 42
         *   z = "hello"
         *   <trace>
         * }
         *
         * def foo() {
         *   x = 123
         * }
         *
         * @formatter:on
         */
        CallTarget collectFrames = new RootNode(null) {
            @Override
            public Object execute(VirtualFrame frame) {
                List<FrameInstance> frames = new ArrayList<>();
                Truffle.getRuntime().iterateFrames(f -> {
                    frames.add(f);
                    return null;
                });
                return frames;
            }
        }.getCallTarget();

        BytecodeNodeWithLocalIntrospection bar = parseNode(b -> {
            b.beginRoot();

            b.beginBlock();

            BytecodeLocal y = makeLocal(b, "y");
            b.beginStoreLocal(y);
            b.emitLoadConstant(42);
            b.endStoreLocal();

            BytecodeLocal z = makeLocal(b, "z");
            b.beginStoreLocal(z);
            b.emitLoadConstant("hello");
            b.endStoreLocal();

            b.beginReturn();
            b.beginInvoke();
            b.emitLoadConstant(collectFrames);
            b.endInvoke();
            b.endReturn();

            b.endBlock();

            b.endRoot();
        });

        BytecodeNodeWithLocalIntrospection foo = parseNode(b -> {
            b.beginRoot();

            b.beginBlock();
            BytecodeLocal x = makeLocal(b, "x");

            b.beginStoreLocal(x);
            b.emitLoadConstant(123);
            b.endStoreLocal();

            b.beginReturn();
            b.beginInvoke();
            b.emitLoadConstant(bar);
            b.endInvoke();
            b.endReturn();

            b.endBlock();

            b.endRoot();
        });

        Object result = foo.getCallTarget().call();
        assertTrue(result instanceof List<?>);

        @SuppressWarnings("unchecked")
        List<FrameInstance> frames = (List<FrameInstance>) result;
        assertEquals(3, frames.size());

        // <anon>
        assertNull(BytecodeNode.getLocalValues(frames.get(0)));

        // bar
        Object[] barLocals = BytecodeNode.getLocalValues(frames.get(1));
        assertArrayEquals(new Object[]{42, "hello"}, barLocals);
        Object[] barLocalNames = BytecodeNode.getLocalNames(frames.get(1));
        assertArrayEquals(new Object[]{"y", "z"}, barLocalNames);
        BytecodeNode.setLocalValues(frames.get(1), new Object[]{-42, "goodbye"});
        assertArrayEquals(new Object[]{-42, "goodbye"}, BytecodeNode.getLocalValues(frames.get(1)));

        // foo
        Object[] fooLocals = BytecodeNode.getLocalValues(frames.get(2));
        assertArrayEquals(new Object[]{123}, fooLocals);
        Object[] fooLocalNames = BytecodeNode.getLocalNames(frames.get(2));
        assertArrayEquals(new Object[]{"x"}, fooLocalNames);
        BytecodeNode.setLocalValues(frames.get(2), new Object[]{456});
        assertArrayEquals(new Object[]{456}, BytecodeNode.getLocalValues(frames.get(2)));
    }

    @Test
    public void testGetLocalsContinuationStacktrace() {
        /* @formatter:off
         *
         * def bar() {
         *   y = yield 0
         *   <trace>
         * }
         *
         * def foo() {
         *   x = 123
         *   continue(bar(), 42)
         * }
         *
         * @formatter:on
         */
        CallTarget collectFrames = new RootNode(null) {
            @Override
            public Object execute(VirtualFrame frame) {
                List<Object[]> frames = new ArrayList<>();
                Truffle.getRuntime().iterateFrames(f -> {
                    frames.add(BytecodeNode.getLocalValues(f));
                    return null;
                });
                return frames;
            }
        }.getCallTarget();

        BytecodeNodeWithLocalIntrospection bar = parseNode(b -> {
            b.beginRoot();

            BytecodeLocal y = makeLocal(b, "y");

            b.beginStoreLocal(y);
            b.beginYield();
            b.emitLoadConstant(0);
            b.endYield();
            b.endStoreLocal();

            b.beginReturn();
            b.beginInvoke();
            b.emitLoadConstant(collectFrames);
            b.endInvoke();
            b.endReturn();

            b.endRoot();
        });

        BytecodeNodeWithLocalIntrospection foo = parseNode(b -> {
            b.beginRoot();
            BytecodeLocal x = makeLocal(b, "x");

            b.beginStoreLocal(x);
            b.emitLoadConstant(123);
            b.endStoreLocal();

            b.beginReturn();
            b.beginContinue();

            b.beginInvoke();
            b.emitLoadConstant(bar);
            b.endInvoke();

            b.emitLoadConstant(42);

            b.endContinue();
            b.endReturn();

            b.endRoot();
        });

        Object result = foo.getCallTarget().call();
        assertTrue(result instanceof List<?>);

        @SuppressWarnings("unchecked")
        List<Object[]> frames = (List<Object[]>) result;
        assertEquals(3, frames.size());

        // <anon>
        assertNull(frames.get(0));

        // bar
        Object[] barLocals = frames.get(1);
        assertArrayEquals(new Object[]{42}, barLocals);

        // foo
        Object[] fooLocals = frames.get(2);
        assertArrayEquals(new Object[]{123}, fooLocals);
    }

    @Test
    public void testGetLocalNamesAndInfos() {
        Object fooInfo = new Object();
        Object bazInfo = new Object();
        BytecodeNodeWithLocalIntrospection root = parseNode(b -> {
            b.beginRoot();

            b.beginBlock();

            b.createLocal("foo", fooInfo);
            b.createLocal("bar", null);
            b.createLocal(null, bazInfo);

            b.beginReturn();
            b.emitLoadConstant(42);
            b.endReturn();

            b.endBlock();

            b.endRoot();
        });

        assertArrayEquals(new Object[]{"foo", "bar", null}, root.getBytecodeNode().getLocalNames(0));
        assertArrayEquals(new Object[]{fooInfo, null, bazInfo}, root.getBytecodeNode().getLocalInfos(0));
    }

    @Test
    public void testGetLocalDefaultOrIllegalGetLocal() {
        // @formatter:off
        // // B0
        // result;
        // {
        //   var l0;
        //   if (arg0) {
        //     result = getLocal(l0)
        //   } else {
        //     l0 = 42L
        //   }
        // }
        // {
        //   var l1;
        //   result = getLocal(l1);
        // }
        // return result
        // @formatter:on

        BytecodeNodeWithLocalIntrospection root = parseNode(b -> {
            b.beginRoot();

            BytecodeLocal result = makeLocal(b, "result");
            b.beginBlock();
            BytecodeLocal l = makeLocal(b, "l0");
            b.beginIfThenElse();
            b.emitLoadArgument(0);
            b.beginStoreLocal(result);
            b.emitGetLocal(l.getLocalOffset());
            b.endStoreLocal();
            b.beginStoreLocal(l);
            b.emitLoadConstant(42);
            b.endStoreLocal();
            b.endIfThenElse();
            b.endBlock();

            b.beginBlock();
            l = makeLocal(b, "l1");
            b.beginStoreLocal(result);
            b.emitGetLocal(l.getLocalOffset());
            b.endStoreLocal();
            b.endBlock();

            b.beginReturn();
            b.emitLoadLocal(result);
            b.endReturn();

            b.endRoot();
        });

        if (hasLocalDefaultValue()) {
            Object defaultLocal = getLocalDefaultValue();
            assertSame(defaultLocal, root.getCallTarget().call(true));
            assertSame(defaultLocal, root.getCallTarget().call(false));
            root.getBytecodeNode().setUncachedThreshold(0);
            assertSame(defaultLocal, root.getCallTarget().call(true));
            assertSame(defaultLocal, root.getCallTarget().call(false));
        } else {
            // Illegal returns null for getLocal
            assertNull(root.getCallTarget().call(true));
            assertNull(root.getCallTarget().call(false));
            root.getBytecodeNode().setUncachedThreshold(0);
            assertNull(root.getCallTarget().call(true));
            assertNull(root.getCallTarget().call(false));
        }
    }

    @Test
    public void testGetAccessorDefaultOrIllegal() {
        // @formatter:off
        // // B0
        // result;
        // {
        //   var l0;
        //   if (arg0) {
        //     result = getLocal(l0)
        //   } else {
        //     l0 = 42L
        //   }
        // }
        // {
        //   var l1;
        //   result = getLocal(l1);
        // }
        // return result
        // @formatter:on

        BytecodeNodeWithLocalIntrospection root = parseNode(b -> {
            b.beginRoot();

            BytecodeLocal result = makeLocal(b, "result");
            b.beginBlock();
            BytecodeLocal l = makeLocal(b, "l0");
            b.beginIfThenElse();
            b.emitLoadArgument(0);
            b.beginStoreLocal(result);
            b.emitGetLocalAccessor(l, FrameSlotKind.Object);
            b.endStoreLocal();
            b.beginStoreLocal(l);
            b.emitLoadConstant(42);
            b.endStoreLocal();
            b.endIfThenElse();
            b.endBlock();

            b.beginBlock();
            l = makeLocal(b, "l1");
            b.beginStoreLocal(result);
            b.emitGetLocalAccessor(l, FrameSlotKind.Object);
            b.endStoreLocal();
            b.endBlock();

            b.beginReturn();
            b.emitLoadLocal(result);
            b.endReturn();

            b.endRoot();
        });

        if (hasLocalDefaultValue()) {
            Object defaultLocal = getLocalDefaultValue();
            assertSame(defaultLocal, root.getCallTarget().call(true));
            assertSame(defaultLocal, root.getCallTarget().call(false));
            root.getBytecodeNode().setUncachedThreshold(0);
            assertSame(defaultLocal, root.getCallTarget().call(true));
            assertSame(defaultLocal, root.getCallTarget().call(false));
        } else {
            // Illegal returns FrameSlotTypeException

            assertThrows(FrameSlotTypeException.class, () -> {
                root.getCallTarget().call(false);
            });
            assertThrows(FrameSlotTypeException.class, () -> {
                root.getCallTarget().call(true);
            });
            root.getBytecodeNode().setUncachedThreshold(0);
            assertThrows(FrameSlotTypeException.class, () -> {
                root.getCallTarget().call(false);
            });
            assertThrows(FrameSlotTypeException.class, () -> {
                root.getCallTarget().call(true);
            });
        }
    }

}

@GenerateBytecodeTestVariants({
                @Variant(suffix = "Base", configuration = @GenerateBytecode(languageClass = BytecodeDSLTestLanguage.class, //
                                enableYield = true)),
                @Variant(suffix = "BaseDefault", configuration = @GenerateBytecode(languageClass = BytecodeDSLTestLanguage.class, //
                                enableYield = true, //
                                defaultLocalValue = "DEFAULT")),
                @Variant(suffix = "WithBEIllegal", configuration = @GenerateBytecode(languageClass = BytecodeDSLTestLanguage.class, //
                                enableQuickening = true, //
                                enableUncachedInterpreter = true, //
                                boxingEliminationTypes = {boolean.class, long.class}, //
                                enableYield = true)),
                @Variant(suffix = "WithBEObjectDefault", configuration = @GenerateBytecode(languageClass = BytecodeDSLTestLanguage.class, //
                                enableQuickening = true, //
                                boxingEliminationTypes = {boolean.class, long.class}, //
                                enableUncachedInterpreter = true, //
                                defaultLocalValue = "resolveDefault()", //
                                enableYield = true)),
                @Variant(suffix = "WithBENullDefault", configuration = @GenerateBytecode(languageClass = BytecodeDSLTestLanguage.class, //
                                enableQuickening = true, //
                                boxingEliminationTypes = {boolean.class, long.class}, //
                                enableUncachedInterpreter = true, //
                                defaultLocalValue = "null", //
                                enableYield = true))
})
abstract class BytecodeNodeWithLocalIntrospection extends DebugBytecodeRootNode implements BytecodeRootNode {
    public int reservedLocalIndex = -1;

    static final Object DEFAULT = new Object();

    static Object resolveDefault() {
        CompilerAsserts.neverPartOfCompilation("Must be cached and not triggered during compilation.");
        return DEFAULT;
    }

    protected BytecodeNodeWithLocalIntrospection(BytecodeDSLTestLanguage language, FrameDescriptor frameDescriptor) {
        super(language, frameDescriptor);
    }

    @Operation
    public static final class GetLocals {
        @Specialization
        public static Map<String, Object> getLocals(VirtualFrame frame,
                        @Bind BytecodeNode node,
                        @Bind("$bytecodeIndex") int bci) {
            Object[] locals = node.getLocalValues(bci, frame);
            return makeMap(node.getLocalNames(bci), locals);
        }

        @TruffleBoundary
        private static Map<String, Object> makeMap(Object[] names, Object[] values) {
            assert names.length == values.length;
            Map<String, Object> result = new HashMap<>();
            for (int i = 0; i < names.length; i++) {
                result.put((String) names[i], values[i]);
            }
            return result;
        }
    }

    @Operation
    @ConstantOperand(type = int.class)
    public static final class GetLocal {
        @Specialization
        public static Object perform(VirtualFrame frame, int i,
                        @Bind BytecodeNode node,
                        @Bind("$bytecodeIndex") int bci) {
            return node.getLocalValue(bci, frame, i);
        }
    }

    @Operation
    @ConstantOperand(type = LocalRangeAccessor.class)
    @ConstantOperand(type = FrameSlotKind.class)
    @ConstantOperand(type = int.class)
    public static final class GetLocalRangeAccessor {
        @Specialization
        public static Object perform(VirtualFrame frame, LocalRangeAccessor accessor, FrameSlotKind kind, int offset,
                        @Bind BytecodeNode node) {
            try {
                switch (kind) {
                    case Boolean:
                        return accessor.getBoolean(node, frame, offset);
                    case Byte:
                        return accessor.getByte(node, frame, offset);
                    case Int:
                        return accessor.getInt(node, frame, offset);
                    case Long:
                        return accessor.getLong(node, frame, offset);
                    case Double:
                        return accessor.getDouble(node, frame, offset);
                    case Float:
                        return accessor.getFloat(node, frame, offset);
                    case Object:
                        return accessor.getObject(node, frame, offset);
                    default:
                        throw CompilerDirectives.shouldNotReachHere();
                }
            } catch (UnexpectedResultException e) {
                throw CompilerDirectives.shouldNotReachHere(e);
            }
        }
    }

    @Operation
    @ConstantOperand(type = LocalAccessor.class)
    @ConstantOperand(type = FrameSlotKind.class)
    public static final class GetLocalAccessor {
        @Specialization
        public static Object perform(VirtualFrame frame, LocalAccessor accessor, FrameSlotKind kind,
                        @Bind BytecodeNode node) {
            try {
                switch (kind) {
                    case Boolean:
                        return accessor.getBoolean(node, frame);
                    case Byte:
                        return accessor.getByte(node, frame);
                    case Int:
                        return accessor.getInt(node, frame);
                    case Long:
                        return accessor.getLong(node, frame);
                    case Double:
                        return accessor.getDouble(node, frame);
                    case Float:
                        return accessor.getFloat(node, frame);
                    case Object:
                        return accessor.getObject(node, frame);
                    default:
                        throw CompilerDirectives.shouldNotReachHere();
                }
            } catch (UnexpectedResultException e) {
                throw CompilerDirectives.shouldNotReachHere(e);
            }
        }
    }

    @Operation
    @ConstantOperand(type = LocalRangeAccessor.class)
    @ConstantOperand(type = FrameSlotKind.class)
    @ConstantOperand(type = int.class)
    public static final class SetLocalRangeAccessor {
        @Specialization
        public static Object doDefault(VirtualFrame frame, LocalRangeAccessor accessor, FrameSlotKind kind, int offset, Object value,
                        @Bind BytecodeNode node) {
            switch (kind) {
                case Boolean:
                    accessor.setBoolean(node, frame, offset, (boolean) value);
                    break;
                case Byte:
                    accessor.setByte(node, frame, offset, (byte) value);
                    break;
                case Int:
                    accessor.setInt(node, frame, offset, (int) value);
                    break;
                case Long:
                    accessor.setLong(node, frame, offset, (long) value);
                    break;
                case Double:
                    accessor.setDouble(node, frame, offset, (double) value);
                    break;
                case Float:
                    accessor.setFloat(node, frame, offset, (float) value);
                    break;
                case Object:
                    accessor.setObject(node, frame, offset, value);
                    break;
                default:
                    throw CompilerDirectives.shouldNotReachHere();
            }
            return value;
        }
    }

    @Operation
    @ConstantOperand(type = LocalAccessor.class)
    @ConstantOperand(type = FrameSlotKind.class)
    public static final class SetLocalAccessor {
        @Specialization
        public static Object doDefault(VirtualFrame frame, LocalAccessor accessor, FrameSlotKind kind, Object value,
                        @Bind BytecodeNode node) {
            switch (kind) {
                case Boolean:
                    accessor.setBoolean(node, frame, (boolean) value);
                    break;
                case Byte:
                    accessor.setByte(node, frame, (byte) value);
                    break;
                case Int:
                    accessor.setInt(node, frame, (int) value);
                    break;
                case Long:
                    accessor.setLong(node, frame, (long) value);
                    break;
                case Double:
                    accessor.setDouble(node, frame, (double) value);
                    break;
                case Float:
                    accessor.setFloat(node, frame, (float) value);
                    break;
                case Object:
                    accessor.setObject(node, frame, value);
                    break;
                default:
                    throw CompilerDirectives.shouldNotReachHere();
            }
            return value;
        }
    }

    @Operation
    public static final class GetLocalUsingBytecodeLocalIndex {
        @Specialization
        public static Object perform(VirtualFrame frame,
                        @Bind BytecodeNodeWithLocalIntrospection root,
                        @Bind BytecodeNode node,
                        @Bind("$bytecodeIndex") int bci) {
            assert root.reservedLocalIndex != -1;
            return node.getLocalValue(bci, frame, root.reservedLocalIndex);
        }
    }

    @Operation
    public static final class SetLocal {
        @Specialization
        public static void perform(VirtualFrame frame, int i, Object value,
                        @Bind BytecodeNode node,
                        @Bind("$bytecodeIndex") int bci) {
            node.setLocalValue(bci, frame, i, value);
        }
    }

    @Operation
    public static final class Same {
        @Specialization
        public static boolean doDefault(int a, int b) {
            return a == b;
        }
    }

    @Operation
    public static final class SetLocalUsingBytecodeLocalIndex {
        @Specialization
        public static void perform(VirtualFrame frame, Object value,
                        @Bind BytecodeNodeWithLocalIntrospection root,
                        @Bind BytecodeNode node,
                        @Bind("$bytecodeIndex") int bci) {
            assert root.reservedLocalIndex != -1;
            node.setLocalValue(bci, frame, root.reservedLocalIndex, value);
        }
    }

    @Operation
    public static final class Invoke {
        @Specialization(guards = {"callTargetMatches(root.getCallTarget(), callNode.getCallTarget())"}, limit = "1")
        public static Object doCached(@SuppressWarnings("unused") BytecodeNodeWithLocalIntrospection root, @Variadic Object[] args,
                        @Cached("create(root.getCallTarget())") DirectCallNode callNode) {
            return callNode.call(args);
        }

        @Specialization(replaces = {"doCached"})
        public static Object doUncached(BytecodeNodeWithLocalIntrospection root, @Variadic Object[] args, @Shared @Cached IndirectCallNode callNode) {
            return callNode.call(root.getCallTarget(), args);
        }

        @Specialization(guards = {"callTargetMatches(callTarget, callNode.getCallTarget())"}, limit = "1")
        public static Object doCallTarget(@SuppressWarnings("unused") CallTarget callTarget, @Variadic Object[] args, @Cached("create(callTarget)") DirectCallNode callNode) {
            return callNode.call(args);
        }

        @Specialization(replaces = {"doCallTarget"})
        public static Object doCallTargetUncached(CallTarget callTarget, @Variadic Object[] args, @Shared @Cached IndirectCallNode callNode) {
            return callNode.call(callTarget, args);
        }

        protected static boolean callTargetMatches(CallTarget left, CallTarget right) {
            return left == right;
        }
    }

    @Operation
    public static final class ContinueNode {
        public static final int LIMIT = 3;

        @SuppressWarnings("unused")
        @Specialization(guards = {"result.getContinuationRootNode() == rootNode"}, limit = "LIMIT")
        public static Object invokeDirect(ContinuationResult result, Object value,
                        @Cached(value = "result.getContinuationRootNode()") ContinuationRootNode rootNode,
                        @Cached(value = "create(rootNode.getCallTarget())") DirectCallNode callNode) {
            return callNode.call(result.getFrame(), value);
        }

        @Specialization(replaces = "invokeDirect")
        public static Object invokeIndirect(ContinuationResult result, Object value,
                        @Cached IndirectCallNode callNode) {
            return callNode.call(result.getContinuationCallTarget(), result.getFrame(), value);
        }
    }

    @Operation
    public static final class MakePair {
        @Specialization
        public static Pair doMakePair(Object left, Object right) {
            return new Pair(left, right);
        }
    }
}

record Pair(Object left, Object right) {
}
