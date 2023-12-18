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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
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
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.bytecode.BytecodeConfig;
import com.oracle.truffle.api.bytecode.BytecodeLocal;
import com.oracle.truffle.api.bytecode.BytecodeNodes;
import com.oracle.truffle.api.bytecode.BytecodeParser;
import com.oracle.truffle.api.bytecode.BytecodeRootNode;
import com.oracle.truffle.api.bytecode.ContinuationResult;
import com.oracle.truffle.api.bytecode.GenerateBytecode;
import com.oracle.truffle.api.bytecode.GenerateBytecodeTestVariants;
import com.oracle.truffle.api.bytecode.GenerateBytecodeTestVariants.Variant;
import com.oracle.truffle.api.bytecode.Operation;
import com.oracle.truffle.api.bytecode.OperationProxy;
import com.oracle.truffle.api.bytecode.Variadic;
import com.oracle.truffle.api.bytecode.test.example.BytecodeDSLExampleLanguage;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameInstance;
import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.nodes.RootNode;

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
        return b.createLocal(FrameSlotKind.Illegal, name, null);
    }

    @SuppressWarnings("unchecked")
    public static <T extends BytecodeNodeWithLocalIntrospectionBuilder> BytecodeNodes<BytecodeNodeWithLocalIntrospection> createNodes(
                    Class<? extends BytecodeNodeWithLocalIntrospection> interpreterClass,
                    BytecodeConfig config,
                    BytecodeParser<T> builder) {
        try {
            Method create = interpreterClass.getMethod("create", BytecodeConfig.class, BytecodeParser.class);
            return (BytecodeNodes<BytecodeNodeWithLocalIntrospection>) create.invoke(null, config, builder);
        } catch (InvocationTargetException e) {
            // Exceptions thrown by the invoked method can be rethrown as runtime exceptions that
            // get caught by the test harness.
            throw new IllegalStateException(e.getCause());
        } catch (Exception e) {
            // Other exceptions (e.g., NoSuchMethodError) likely indicate a bad reflective call.
            throw new AssertionError("Encountered exception during reflective call: " + e.getMessage());
        }
    }

    public static <T extends BytecodeNodeWithLocalIntrospectionBuilder> BytecodeNodeWithLocalIntrospection parseNode(Class<? extends BytecodeNodeWithLocalIntrospection> interpreterClass,
                    BytecodeParser<T> builder) {
        BytecodeNodes<BytecodeNodeWithLocalIntrospection> nodes = createNodes(interpreterClass, BytecodeConfig.DEFAULT, builder);
        return nodes.getNode(nodes.count() - 1);
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
    public void testGetLocalUsingBytecodeLocalIndex() {
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
            b.emitLoadConstant(0);
            b.endStoreLocal();

            b.beginStoreLocal(bar);
            b.emitLoadArgument(1);
            b.endStoreLocal();

            b.beginReturn();
            b.emitGetLocalUsingBytecodeLocalIndex();
            b.endReturn();

            b.endBlock();

            BytecodeNodeWithLocalIntrospection rootNode = b.endRoot();
            rootNode.reservedLocalIndex = rootNode.getLocalIndex(bar);
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
    public void testGetLocalsSimpleStacktrace() {
        /* @formatter:off
         *
         * def bar() {
         *   y = 42
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
        assertNull(BytecodeRootNode.getLocals(frames.get(0)));

        // bar
        Object[] barLocals = BytecodeRootNode.getLocals(frames.get(1));
        assertArrayEquals(new Object[]{42}, barLocals);

        // foo
        Object[] fooLocals = BytecodeRootNode.getLocals(frames.get(2));
        assertArrayEquals(new Object[]{123}, fooLocals);
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
            b.beginYield();
            b.emitLoadConstant(0);
            b.endYield();
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
            b.beginContinue();

            b.beginInvoke();
            b.emitLoadConstant(bar);
            b.endInvoke();

            b.emitLoadConstant(42);

            b.endContinue();
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
        assertNull(BytecodeRootNode.getLocals(frames.get(0)));

        // bar
        Object[] barLocals = BytecodeRootNode.getLocals(frames.get(1));
        assertArrayEquals(new Object[]{42}, barLocals);

        // foo
        Object[] fooLocals = BytecodeRootNode.getLocals(frames.get(2));
        assertArrayEquals(new Object[]{123}, fooLocals);
    }

    @Test
    public void testGetLocalNamesAndInfos() {
        Object fooInfo = new Object();
        Object bazInfo = new Object();
        BytecodeNodeWithLocalIntrospection root = parseNode(b -> {
            b.beginRoot(null);

            b.beginBlock();

            b.createLocal(FrameSlotKind.Illegal, "foo", fooInfo);
            b.createLocal(FrameSlotKind.Illegal, "bar", null);
            b.createLocal(FrameSlotKind.Illegal, null, bazInfo);

            b.beginReturn();
            b.emitLoadConstant(42);
            b.endReturn();

            b.endBlock();

            b.endRoot();
        });

        assertArrayEquals(new Object[]{"foo", "bar", null}, root.getLocalNames());
        assertArrayEquals(new Object[]{fooInfo, null, bazInfo}, root.getLocalInfos());
    }
}

@GenerateBytecodeTestVariants({
                @Variant(suffix = "Base", configuration = @GenerateBytecode(languageClass = BytecodeDSLExampleLanguage.class, enableYield = true)),
                @Variant(suffix = "WithBoxingElimination", configuration = @GenerateBytecode(languageClass = BytecodeDSLExampleLanguage.class, enableQuickening = true, boxingEliminationTypes = {
                                boolean.class, long.class}, enableYield = true)),
                @Variant(suffix = "WithUncached", configuration = @GenerateBytecode(languageClass = BytecodeDSLExampleLanguage.class, enableYield = true, enableUncachedInterpreter = true))
})
abstract class BytecodeNodeWithLocalIntrospection extends DebugBytecodeRootNode implements BytecodeRootNode {
    // Used for testGetLocalUsingBytecodeLocalIndex
    public int reservedLocalIndex = -1;

    protected BytecodeNodeWithLocalIntrospection(TruffleLanguage<?> language, FrameDescriptor frameDescriptor) {
        super(language, frameDescriptor);
    }

    @Operation
    public static final class GetLocals {
        @Specialization
        public static Map<String, Object> getLocals(VirtualFrame frame, @Bind("$root") BytecodeNodeWithLocalIntrospection bytecodeRootNode) {
            Object[] locals = bytecodeRootNode.getLocals(frame);
            return makeMap(bytecodeRootNode.getLocalNames(), locals);
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
        public static Object perform(VirtualFrame frame, int i, @Bind("$root") BytecodeNodeWithLocalIntrospection bytecodeRootNode) {
            return bytecodeRootNode.getLocal(frame, i);
        }
    }

    @Operation
    public static final class GetLocalUsingBytecodeLocalIndex {
        @Specialization
        public static Object perform(VirtualFrame frame, @Bind("$root") BytecodeNodeWithLocalIntrospection bytecodeRootNode) {
            assert bytecodeRootNode.reservedLocalIndex != -1;
            return bytecodeRootNode.getLocal(frame, bytecodeRootNode.reservedLocalIndex);
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
        public static Object doUncached(BytecodeNodeWithLocalIntrospection root, @Variadic Object[] args, @Cached IndirectCallNode callNode) {
            return callNode.call(root.getCallTarget(), args);
        }

        @Specialization(guards = {"callTargetMatches(callTarget, callNode.getCallTarget())"}, limit = "1")
        public static Object doCallTarget(@SuppressWarnings("unused") CallTarget callTarget, @Variadic Object[] args, @Cached("create(callTarget)") DirectCallNode callNode) {
            return callNode.call(args);
        }

        @Specialization(replaces = {"doCallTarget"})
        public static Object doCallTargetUncached(CallTarget callTarget, @Variadic Object[] args, @Cached IndirectCallNode callNode) {
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
                        @Cached(value = "result.getContinuationRootNode()", inline = false) RootNode rootNode,
                        @Cached(value = "create(rootNode.getCallTarget())", inline = false) DirectCallNode callNode) {
            return callNode.call(result.getFrame(), value);
        }

        @Specialization(replaces = "invokeDirect")
        public static Object invokeIndirect(ContinuationResult result, Object value,
                        @Cached(inline = false) IndirectCallNode callNode) {
            return callNode.call(result.getContinuationCallTarget(), result.getFrame(), value);
        }
    }
}
