package com.oracle.truffle.api.operation.test;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.operation.GenerateOperations;
import com.oracle.truffle.api.operation.Operation;
import com.oracle.truffle.api.operation.OperationConfig;
import com.oracle.truffle.api.operation.OperationLocal;
import com.oracle.truffle.api.operation.OperationNodes;
import com.oracle.truffle.api.operation.OperationParser;
import com.oracle.truffle.api.operation.OperationRootNode;
import com.oracle.truffle.api.operation.Variadic;

public class OperationGetLocalsTest {
    public static OperationLocal makeLocal(List<String> names, OperationNodeWithLocalIntrospectionGen.Builder b, String name) {
        names.add(name);
        return b.createLocal();
    }

    public static OperationNodeWithLocalIntrospection parseNode(OperationParser<OperationNodeWithLocalIntrospectionGen.Builder> builder) {
        OperationNodes<OperationNodeWithLocalIntrospection> nodes = OperationNodeWithLocalIntrospectionGen.create(OperationConfig.DEFAULT, builder);
        return nodes.getNodes().get(nodes.getNodes().size() - 1);
    }

    @Test
    public void testSimple() {
        /*
         * foo = 42 bar = arg0 return getLocals()
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
}

@GenerateOperations(languageClass = TestOperationsLanguage.class)
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

        protected static boolean callTargetMatches(CallTarget left, CallTarget right) {
            return left == right;
        }
    }
}
