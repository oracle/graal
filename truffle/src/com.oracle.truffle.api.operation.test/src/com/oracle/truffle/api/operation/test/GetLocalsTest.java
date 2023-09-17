package com.oracle.truffle.api.operation.test;

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
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameInstance;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.operation.ContinuationResult;
import com.oracle.truffle.api.operation.GenerateOperations;
import com.oracle.truffle.api.operation.GenerateOperationsTestVariants;
import com.oracle.truffle.api.operation.GenerateOperationsTestVariants.Variant;
import com.oracle.truffle.api.operation.test.example.OperationsExampleLanguage;
import com.oracle.truffle.api.operation.Operation;
import com.oracle.truffle.api.operation.OperationConfig;
import com.oracle.truffle.api.operation.OperationLocal;
import com.oracle.truffle.api.operation.OperationNodes;
import com.oracle.truffle.api.operation.OperationParser;
import com.oracle.truffle.api.operation.OperationProxy;
import com.oracle.truffle.api.operation.OperationRootNode;
import com.oracle.truffle.api.operation.Variadic;

@RunWith(Parameterized.class)
public class GetLocalsTest {
    @Parameters(name = "{0}")
    public static List<Class<? extends OperationNodeWithLocalIntrospection>> getInterpreterClasses() {
        return List.of(OperationNodeWithLocalIntrospectionBase.class, OperationNodeWithLocalIntrospectionWithBaseline.class);
    }

    @Parameter(0) public Class<? extends OperationNodeWithLocalIntrospection> interpreterClass;

    public static OperationLocal makeLocal(List<String> names, OperationNodeWithLocalIntrospectionBuilder b, String name) {
        names.add(name);
        return b.createLocal();
    }

    @SuppressWarnings("unchecked")
    public static <T extends OperationNodeWithLocalIntrospectionBuilder> OperationNodes<OperationNodeWithLocalIntrospection> createNodes(
                    Class<? extends OperationNodeWithLocalIntrospection> interpreterClass,
                    OperationConfig config,
                    OperationParser<T> builder) {
        try {
            Method create = interpreterClass.getMethod("create", OperationConfig.class, OperationParser.class);
            return (OperationNodes<OperationNodeWithLocalIntrospection>) create.invoke(null, config, builder);
        } catch (InvocationTargetException e) {
            // Exceptions thrown by the invoked method can be rethrown as runtime exceptions that
            // get caught by the test harness.
            throw new IllegalStateException(e.getCause());
        } catch (Exception e) {
            // Other exceptions (e.g., NoSuchMethodError) likely indicate a bad reflective call.
            throw new AssertionError("Encountered exception during reflective call: " + e.getMessage());
        }
    }

    public static <T extends OperationNodeWithLocalIntrospectionBuilder> OperationNodeWithLocalIntrospection parseNode(Class<? extends OperationNodeWithLocalIntrospection> interpreterClass,
                    OperationParser<T> builder) {
        OperationNodes<OperationNodeWithLocalIntrospection> nodes = createNodes(interpreterClass, OperationConfig.DEFAULT, builder);
        return nodes.getNodes().get(nodes.getNodes().size() - 1);
    }

    public <T extends OperationNodeWithLocalIntrospectionBuilder> OperationNodeWithLocalIntrospection parseNode(OperationParser<T> builder) {
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

        OperationNodeWithLocalIntrospection root = parseNode(b -> {
            b.beginRoot(null);

            b.beginBlock();
            OperationLocal foo = makeLocal(names, b, "foo");
            OperationLocal bar = makeLocal(names, b, "bar");

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

        OperationNodeWithLocalIntrospection root = parseNode(b -> {
            b.beginRoot(null);

            b.beginBlock();
            OperationLocal foo = makeLocal(names, b, "foo");
            OperationLocal bar = makeLocal(names, b, "bar");

            b.beginStoreLocal(foo);
            b.emitLoadConstant(42);
            b.endStoreLocal();

            b.beginStoreLocal(bar);
            b.emitLoadConstant(123);
            b.endStoreLocal();

            List<String> nestedNames = new ArrayList<>();
            b.beginRoot(null);
            b.beginBlock();
            OperationLocal baz = makeLocal(nestedNames, b, "baz");
            OperationLocal qux = makeLocal(nestedNames, b, "qux");

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
            OperationNodeWithLocalIntrospection nested = b.endRoot();
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

        OperationNodeWithLocalIntrospection bar = parseNode(b -> {
            b.beginRoot(null);
            b.beginBlock();
            OperationLocal x = makeLocal(names, b, "x");
            OperationLocal y = makeLocal(names, b, "y");

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

        OperationNodeWithLocalIntrospection foo = parseNode(b -> {
            b.beginRoot(null);
            b.beginBlock();
            OperationLocal c = b.createLocal();

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
        OperationNodeWithLocalIntrospection bar = parseNode(b -> {
            b.beginRoot(null);

            b.beginBlock();
            OperationLocal y = makeLocal(barNames, b, "y");

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
        OperationNodeWithLocalIntrospection foo = parseNode(b -> {
            b.beginRoot(null);

            b.beginBlock();
            OperationLocal x = makeLocal(fooNames, b, "x");

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
        assertNull(OperationRootNode.getLocals(frames.get(0)));

        // bar
        Object[] barLocals = OperationRootNode.getLocals(frames.get(1));
        assertArrayEquals(new Object[]{42}, barLocals);

        // foo
        Object[] fooLocals = OperationRootNode.getLocals(frames.get(2));
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
        OperationNodeWithLocalIntrospection bar = parseNode(b -> {
            b.beginRoot(null);

            b.beginBlock();
            OperationLocal y = makeLocal(barNames, b, "y");

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
        OperationNodeWithLocalIntrospection foo = parseNode(b -> {
            b.beginRoot(null);

            b.beginBlock();
            OperationLocal x = makeLocal(fooNames, b, "x");

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
        assertNull(OperationRootNode.getLocals(frames.get(0)));

        // bar
        Object[] barLocals = OperationRootNode.getLocals(frames.get(1));
        assertArrayEquals(new Object[]{42}, barLocals);

        // foo
        Object[] fooLocals = OperationRootNode.getLocals(frames.get(2));
        assertArrayEquals(new Object[]{123}, fooLocals);
    }
}

@GenerateOperationsTestVariants({
                @Variant(suffix = "Base", configuration = @GenerateOperations(languageClass = OperationsExampleLanguage.class, enableYield = true)),
                @Variant(suffix = "WithBaseline", configuration = @GenerateOperations(languageClass = OperationsExampleLanguage.class, enableYield = true, enableBaselineInterpreter = true))
})
@OperationProxy(value = ContinuationResult.ContinueNode.class, name = "Continue")
abstract class OperationNodeWithLocalIntrospection extends RootNode implements OperationRootNode {
    @CompilationFinal(dimensions = 1) String[] localNames;

    protected OperationNodeWithLocalIntrospection(TruffleLanguage<?> language, FrameDescriptor frameDescriptor) {
        super(language, frameDescriptor);
    }

    public void setLocalNames(String[] localNames) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        this.localNames = localNames;
    }

    @Operation
    public static final class GetLocals {
        @Specialization
        public static Map<String, Object> getLocals(VirtualFrame frame, @Bind("$root") Node rootNode) {
            OperationNodeWithLocalIntrospection operationRootNode = (OperationNodeWithLocalIntrospection) rootNode;
            Object[] locals = operationRootNode.getLocals(frame);
            return makeMap(operationRootNode.localNames, locals);
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
        public static Object doCached(@SuppressWarnings("unused") OperationNodeWithLocalIntrospection root, @Variadic Object[] args,
                        @Cached("create(root.getCallTarget())") DirectCallNode callNode) {
            return callNode.call(args);
        }

        @Specialization(replaces = {"doCached"})
        public static Object doUncached(OperationNodeWithLocalIntrospection root, @Variadic Object[] args, @Cached IndirectCallNode callNode) {
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
