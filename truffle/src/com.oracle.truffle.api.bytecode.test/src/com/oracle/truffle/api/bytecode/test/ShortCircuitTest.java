package com.oracle.truffle.api.bytecode.test;

import static org.junit.Assert.assertEquals;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.bytecode.GenerateBytecode;
import com.oracle.truffle.api.bytecode.GenerateBytecodeTestVariants;
import com.oracle.truffle.api.bytecode.Operation;
import com.oracle.truffle.api.bytecode.BytecodeConfig;
import com.oracle.truffle.api.bytecode.BytecodeNodes;
import com.oracle.truffle.api.bytecode.BytecodeParser;
import com.oracle.truffle.api.bytecode.OperationProxy;
import com.oracle.truffle.api.bytecode.BytecodeRootNode;
import com.oracle.truffle.api.bytecode.ShortCircuitOperation;
import com.oracle.truffle.api.bytecode.GenerateBytecodeTestVariants.Variant;
import com.oracle.truffle.api.bytecode.test.example.OperationsExampleLanguage;
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

    @SuppressWarnings("unchecked")
    public static <T extends BytecodeNodeWithShortCircuitBuilder> BytecodeNodes<BytecodeNodeWithShortCircuit> createNodes(
                    Class<? extends BytecodeNodeWithShortCircuit> interpreterClass,
                    BytecodeConfig config,
                    BytecodeParser<T> builder) {
        try {
            Method create = interpreterClass.getMethod("create", BytecodeConfig.class, BytecodeParser.class);
            return (BytecodeNodes<BytecodeNodeWithShortCircuit>) create.invoke(null, config, builder);
        } catch (InvocationTargetException e) {
            // Exceptions thrown by the invoked method can be rethrown as runtime exceptions that
            // get caught by the test harness.
            throw new IllegalStateException(e.getCause());
        } catch (Exception e) {
            // Other exceptions (e.g., NoSuchMethodError) likely indicate a bad reflective call.
            throw new AssertionError("Encountered exception during reflective call: " + e.getMessage());
        }
    }

    public static <T extends BytecodeNodeWithShortCircuitBuilder> BytecodeNodeWithShortCircuit parseNode(Class<? extends BytecodeNodeWithShortCircuit> interpreterClass,
                    BytecodeParser<T> builder) {
        BytecodeNodes<BytecodeNodeWithShortCircuit> nodes = createNodes(interpreterClass, BytecodeConfig.DEFAULT, builder);
        return nodes.getNodes().get(nodes.getNodes().size() - 1);
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
                @Variant(suffix = "Base", configuration = @GenerateBytecode(languageClass = OperationsExampleLanguage.class)),
                @Variant(suffix = "WithBE", configuration = @GenerateBytecode(languageClass = OperationsExampleLanguage.class, boxingEliminationTypes = {boolean.class, int.class}))
})
@OperationProxy(value = BooleanConverterOperationProxy.class)
/**
 * Note how different boolean converters are used. The converter need not be declared as
 * an @Operation or @OperationProxy, but if so, it should validate like an implicit @Operation.
 *
 * Also note that converters can be repeated without introducing duplicate operations.
 */
@ShortCircuitOperation(name = "ObjectAnd", continueWhen = true, booleanConverter = BytecodeNodeWithShortCircuit.BooleanConverterOperation.class)
@ShortCircuitOperation(name = "ObjectOr", continueWhen = false, booleanConverter = BooleanConverterOperationProxy.class)
@ShortCircuitOperation(name = "BoolAnd", continueWhen = true, booleanConverter = BytecodeNodeWithShortCircuit.BooleanConverterNonOperation.class, returnConvertedValue = true)
@ShortCircuitOperation(name = "BoolOr", continueWhen = false, booleanConverter = BytecodeNodeWithShortCircuit.BooleanConverterNonOperation.class, returnConvertedValue = true)
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
