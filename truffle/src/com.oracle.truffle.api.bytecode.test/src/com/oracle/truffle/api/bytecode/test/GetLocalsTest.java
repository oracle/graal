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
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
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
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.nodes.RootNode;

@RunWith(Parameterized.class)
public class GetLocalsTest {
    @Parameters(name = "{0}")
    public static List<Class<? extends BytecodeNodeWithLocalIntrospection>> getInterpreterClasses() {
        return List.of(BytecodeNodeWithLocalIntrospectionBase.class, BytecodeNodeWithLocalIntrospectionWithUncached.class);
    }

    @Parameter(0) public Class<? extends BytecodeNodeWithLocalIntrospection> interpreterClass;

    public static BytecodeLocal makeLocal(List<String> names, BytecodeNodeWithLocalIntrospectionBuilder b, String name) {
        names.add(name);
        return b.createLocal();
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
        return nodes.getNodes().get(nodes.getNodes().size() - 1);
    }

    public <T extends BytecodeNodeWithLocalIntrospectionBuilder> BytecodeNodeWithLocalIntrospection parseNode(BytecodeParser<T> builder) {
        return parseNode(interpreterClass, builder);
    }

    @Test
    public void testSimple() {
        /* @formatter:off
         *
         * foo = 42
         * bar = arg0
         * return getLocals()
         *
         * @formatter:on
         */
        List<String> names = new ArrayList<>();

        BytecodeNodeWithLocalIntrospection root = parseNode(b -> {
            b.beginRoot(null);

            b.beginBlock();
            BytecodeLocal foo = makeLocal(names, b, "foo");
            BytecodeLocal bar = makeLocal(names, b, "bar");

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
        root.setLocalNames(names.toArray(String[]::new));

        assertEquals(Map.of("foo", 42, "bar", 123), root.getCallTarget().call(123));
    }

    @Test
    public void testNestedRootNode() {
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
        List<String> names = new ArrayList<>();

        BytecodeNodeWithLocalIntrospection root = parseNode(b -> {
            b.beginRoot(null);

            b.beginBlock();
            BytecodeLocal foo = makeLocal(names, b, "foo");
            BytecodeLocal bar = makeLocal(names, b, "bar");

            b.beginStoreLocal(foo);
            b.emitLoadConstant(42);
            b.endStoreLocal();

            b.beginStoreLocal(bar);
            b.emitLoadConstant(123);
            b.endStoreLocal();

            List<String> nestedNames = new ArrayList<>();
            b.beginRoot(null);
            b.beginBlock();
            BytecodeLocal baz = makeLocal(nestedNames, b, "baz");
            BytecodeLocal qux = makeLocal(nestedNames, b, "qux");

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
            nested.setLocalNames(nestedNames.toArray(String[]::new));

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
        root.setLocalNames(names.toArray(String[]::new));

        assertEquals(Map.of("foo", 42, "bar", 123), root.getCallTarget().call(true));
        assertEquals(Map.of("baz", 1337, "qux", 4321), root.getCallTarget().call(false));
    }

    @Test
    public void testContinuation() {
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
        List<String> names = new ArrayList<>();

        BytecodeNodeWithLocalIntrospection bar = parseNode(b -> {
            b.beginRoot(null);
            b.beginBlock();
            BytecodeLocal x = makeLocal(names, b, "x");
            BytecodeLocal y = makeLocal(names, b, "y");

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
        bar.setLocalNames(names.toArray(String[]::new));

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
    public void testSimpleStacktrace() {
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

        List<String> barNames = new ArrayList<>();
        BytecodeNodeWithLocalIntrospection bar = parseNode(b -> {
            b.beginRoot(null);

            b.beginBlock();
            BytecodeLocal y = makeLocal(barNames, b, "y");

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
        bar.setLocalNames(barNames.toArray(String[]::new));

        List<String> fooNames = new ArrayList<>();
        BytecodeNodeWithLocalIntrospection foo = parseNode(b -> {
            b.beginRoot(null);

            b.beginBlock();
            BytecodeLocal x = makeLocal(fooNames, b, "x");

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
        foo.setLocalNames(fooNames.toArray(String[]::new));

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
    public void testContinuationStacktrace() {
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

        List<String> barNames = new ArrayList<>();
        BytecodeNodeWithLocalIntrospection bar = parseNode(b -> {
            b.beginRoot(null);

            b.beginBlock();
            BytecodeLocal y = makeLocal(barNames, b, "y");

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
        bar.setLocalNames(barNames.toArray(String[]::new));

        List<String> fooNames = new ArrayList<>();
        BytecodeNodeWithLocalIntrospection foo = parseNode(b -> {
            b.beginRoot(null);

            b.beginBlock();
            BytecodeLocal x = makeLocal(fooNames, b, "x");

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
        foo.setLocalNames(fooNames.toArray(String[]::new));

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
}

@GenerateBytecodeTestVariants({
                @Variant(suffix = "Base", configuration = @GenerateBytecode(languageClass = BytecodeDSLExampleLanguage.class, enableYield = true)),
                @Variant(suffix = "WithUncached", configuration = @GenerateBytecode(languageClass = BytecodeDSLExampleLanguage.class, enableYield = true, enableUncachedInterpreter = true))
})
@OperationProxy(value = ContinuationResult.ContinueNode.class, name = "Continue")
abstract class BytecodeNodeWithLocalIntrospection extends RootNode implements BytecodeRootNode {
    @CompilationFinal(dimensions = 1) String[] localNames;

    protected BytecodeNodeWithLocalIntrospection(TruffleLanguage<?> language, FrameDescriptor frameDescriptor) {
        super(language, frameDescriptor);
    }

    public void setLocalNames(String[] localNames) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        this.localNames = localNames;
    }

    @Operation
    public static final class GetLocals {
        @Specialization
        public static Map<String, Object> getLocals(VirtualFrame frame, @Bind("$root") BytecodeNodeWithLocalIntrospection bytecodeRootNode) {
            Object[] locals = bytecodeRootNode.getLocals(frame);
            return makeMap(bytecodeRootNode.localNames, locals);
        }

        @TruffleBoundary
        private static Map<String, Object> makeMap(String[] names, Object[] values) {
            assert names.length == values.length;
            Map<String, Object> result = new HashMap<>();
            for (int i = 0; i < names.length; i++) {
                result.put(names[i], values[i]);
            }
            return result;
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
}
