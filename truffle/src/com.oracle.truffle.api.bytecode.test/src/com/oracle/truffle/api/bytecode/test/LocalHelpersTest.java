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
import static org.junit.Assert.assertTrue;
import static com.oracle.truffle.api.bytecode.test.BytecodeNodeWithLocalIntrospection.GetLocalTagged;

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
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage;
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
import com.oracle.truffle.api.bytecode.Operation;
import com.oracle.truffle.api.bytecode.Variadic;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameInstance;
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
                        BytecodeNodeWithLocalIntrospectionWithBoxingElimination.class,
                        BytecodeNodeWithLocalIntrospectionWithUncached.class);
    }

    @Parameter(0) public Class<? extends BytecodeNodeWithLocalIntrospection> interpreterClass;

    public static BytecodeLocal makeLocal(BytecodeNodeWithLocalIntrospectionBuilder b, String name) {
        return b.createLocal(name, null);
    }

    public static <T extends BytecodeNodeWithLocalIntrospectionBuilder> BytecodeNodeWithLocalIntrospection parseNode(Class<? extends BytecodeNodeWithLocalIntrospection> interpreterClass,
                    BytecodeParser<T> builder) {
        BytecodeRootNodes<BytecodeNodeWithLocalIntrospection> nodes = BytecodeNodeWithLocalIntrospectionBuilder.invokeCreate((Class<? extends BytecodeNodeWithLocalIntrospection>) interpreterClass,
                        BytecodeConfig.DEFAULT, builder);
        return nodes.getNode(0);
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
            b.beginRoot(null);

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
            b.beginGetLocal();
            b.emitLoadArgument(1);
            b.endGetLocal();
            b.endReturn();

            b.endBlock();

            b.endRoot();
        });

        assertEquals(42, root.getCallTarget().call(123, 0));
        assertEquals(123, root.getCallTarget().call(123, 1));
    }

    @Test
    public void testGetLocalTagged() {
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
            b.beginRoot(null);

            b.beginBlock();
            BytecodeLocal foo = makeLocal(b, "foo");

            b.beginStoreLocal(foo);
            b.emitLoadConstant(true);
            b.endStoreLocal();
            b.emitGetLocalTagged(GetLocalTagged.BOOLEAN, 0);

            b.beginStoreLocal(foo);
            b.emitLoadConstant((byte) 2);
            b.endStoreLocal();
            b.emitGetLocalTagged(GetLocalTagged.BYTE, 0);

            b.beginStoreLocal(foo);
            b.emitLoadConstant((short) 42);
            b.endStoreLocal();
            b.emitGetLocalTagged(GetLocalTagged.SHORT, 0);

            b.beginStoreLocal(foo);
            b.emitLoadConstant(42);
            b.endStoreLocal();
            b.emitGetLocalTagged(GetLocalTagged.INT, 0);

            b.beginStoreLocal(foo);
            b.emitLoadConstant(42L);
            b.endStoreLocal();
            b.emitGetLocalTagged(GetLocalTagged.LONG, 0);

            b.beginStoreLocal(foo);
            b.emitLoadConstant(3.14f);
            b.endStoreLocal();
            b.emitGetLocalTagged(GetLocalTagged.FLOAT, 0);

            b.beginStoreLocal(foo);
            b.emitLoadConstant(4.0d);
            b.endStoreLocal();
            b.emitGetLocalTagged(GetLocalTagged.DOUBLE, 0);

            b.beginStoreLocal(foo);
            b.emitLoadConstant("hello");
            b.endStoreLocal();

            b.beginReturn();
            b.emitGetLocalTagged(GetLocalTagged.OBJECT, 0);
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
            b.beginRoot(null);

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
            b.beginRoot(null);

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
            b.beginRoot(null);

            b.beginBlock();
            BytecodeLocal foo = makeLocal(b, "foo");
            BytecodeLocal bar = makeLocal(b, "bar");

            b.beginStoreLocal(foo);
            b.emitLoadConstant(42);
            b.endStoreLocal();

            b.beginStoreLocal(bar);
            b.emitLoadConstant(123);
            b.endStoreLocal();

            b.beginRoot(null);
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
            b.beginRoot(null);
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
            b.beginRoot(null);
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
            b.beginRoot(null);

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
            b.beginRoot(null);

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
            b.beginRoot(null);

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
            b.beginRoot(null);

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
            b.beginRoot(null);

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
            b.beginRoot(null);
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
            b.beginRoot(null);

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
}

@GenerateBytecodeTestVariants({
                @Variant(suffix = "Base", configuration = @GenerateBytecode(languageClass = BytecodeDSLTestLanguage.class, enableYield = true)),
                @Variant(suffix = "WithBoxingElimination", configuration = @GenerateBytecode(languageClass = BytecodeDSLTestLanguage.class, enableQuickening = true, boxingEliminationTypes = {
                                boolean.class, long.class}, enableYield = true)),
                @Variant(suffix = "WithUncached", configuration = @GenerateBytecode(languageClass = BytecodeDSLTestLanguage.class, enableYield = true, enableUncachedInterpreter = true))
})
abstract class BytecodeNodeWithLocalIntrospection extends DebugBytecodeRootNode implements BytecodeRootNode {
    public int reservedLocalIndex = -1;

    protected BytecodeNodeWithLocalIntrospection(TruffleLanguage<?> language, FrameDescriptor frameDescriptor) {
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
    public static final class GetLocal {
        @Specialization
        public static Object perform(VirtualFrame frame, int i,
                        @Bind BytecodeNode node,
                        @Bind("$bytecodeIndex") int bci) {
            return node.getLocalValue(bci, frame, i);
        }
    }

    @Operation
    @ConstantOperand(name = "kind", type = int.class)
    @ConstantOperand(name = "i", type = int.class)
    public static final class GetLocalTagged {
        public static final int BOOLEAN = 0;
        public static final int BYTE = 1;
        public static final int SHORT = 2;
        public static final int INT = 3;
        public static final int LONG = 4;
        public static final int FLOAT = 5;
        public static final int DOUBLE = 6;
        public static final int OBJECT = 7;

        @Specialization
        public static Object perform(VirtualFrame frame, int kind, int i,
                        @Bind BytecodeNode node,
                        @Bind("$bytecodeIndex") int bci) {
            if (kind == OBJECT) {
                return node.getLocalValue(bci, frame, i);
            }

            try {
                switch (kind) {
                    case BOOLEAN:
                        return node.getLocalValueBoolean(bci, frame, i);
                    case BYTE:
                        return node.getLocalValueByte(bci, frame, i);
                    case SHORT:
                        return node.getLocalValueShort(bci, frame, i);
                    case INT:
                        return node.getLocalValueInt(bci, frame, i);
                    case LONG:
                        return node.getLocalValueLong(bci, frame, i);
                    case DOUBLE:
                        return node.getLocalValueDouble(bci, frame, i);
                    case FLOAT:
                        return node.getLocalValueFloat(bci, frame, i);
                    default:
                        throw CompilerDirectives.shouldNotReachHere();

                }
            } catch (UnexpectedResultException ex) {
                return node.getLocalValue(bci, frame, i);
            }

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
