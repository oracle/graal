package com.oracle.truffle.api.bytecode.test.example;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.bytecode.BytecodeConfig;
import com.oracle.truffle.api.bytecode.BytecodeNodes;
import com.oracle.truffle.api.bytecode.BytecodeParser;
import com.oracle.truffle.api.bytecode.BytecodeRootNode;
import com.oracle.truffle.api.nodes.RootNode;

public class OperationsExampleCommon {
    /**
     * Creates a root node using the given parameters.
     *
     * In order to parameterize tests over multiple different interpreter configurations
     * ("variants"), we take the specific interpreterClass as input. Since interpreters are
     * instantiated using a static {@code create} method, we must invoke this method using
     * reflection.
     */
    @SuppressWarnings("unchecked")
    public static <T extends OperationsExampleBuilder> BytecodeNodes<OperationsExample> createNodes(Class<? extends OperationsExample> interpreterClass, BytecodeConfig config,
                    BytecodeParser<T> builder) {
        try {
            Method create = interpreterClass.getMethod("create", BytecodeConfig.class, BytecodeParser.class);
            return (BytecodeNodes<OperationsExample>) create.invoke(null, config, builder);
        } catch (InvocationTargetException e) {
            // Exceptions thrown by the invoked method can be rethrown as runtime exceptions that
            // get caught by the test harness.
            throw new IllegalStateException(e.getCause());
        } catch (Exception e) {
            // Other exceptions (e.g., NoSuchMethodError) likely indicate a bad reflective call.
            throw new AssertionError("Encountered exception during reflective call: " + e.getMessage());
        }
    }

    public static <T extends OperationsExampleBuilder> RootCallTarget parse(Class<? extends OperationsExample> interpreterClass, String rootName, BytecodeParser<T> builder) {
        BytecodeRootNode operationsNode = parseNode(interpreterClass, rootName, builder);
        return ((RootNode) operationsNode).getCallTarget();
    }

    public static <T extends OperationsExampleBuilder> OperationsExample parseNode(Class<? extends OperationsExample> interpreterClass, String rootName, BytecodeParser<T> builder) {
        BytecodeNodes<OperationsExample> nodes = OperationsExampleCommon.createNodes(interpreterClass, BytecodeConfig.DEFAULT, builder);
        OperationsExample op = nodes.getNodes().get(nodes.getNodes().size() - 1);
        op.setName(rootName);
        return op;
    }

    public static <T extends OperationsExampleBuilder> OperationsExample parseNodeWithSource(Class<? extends OperationsExample> interpreterClass, String rootName, BytecodeParser<T> builder) {
        BytecodeNodes<OperationsExample> nodes = OperationsExampleCommon.createNodes(interpreterClass, BytecodeConfig.WITH_SOURCE, builder);
        OperationsExample op = nodes.getNodes().get(nodes.getNodes().size() - 1);
        op.setName(rootName);
        return op;
    }

    public static List<Class<? extends OperationsExample>> allInterpreters() {
        return List.of(OperationsExampleBase.class, OperationsExampleUnsafe.class, OperationsExampleWithUncached.class, OperationsExampleWithBE.class, OperationsExampleWithOptimizations.class,
                        OperationsExampleProduction.class);
    }

    public static boolean hasBE(Class<? extends OperationsExample> c) {
        return c == OperationsExampleWithBE.class || c == OperationsExampleProduction.class;
    }

}
