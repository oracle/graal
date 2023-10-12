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

public class BytecodeDSLExampleCommon {
    /**
     * Creates a root node using the given parameters.
     *
     * In order to parameterize tests over multiple different interpreter configurations
     * ("variants"), we take the specific interpreterClass as input. Since interpreters are
     * instantiated using a static {@code create} method, we must invoke this method using
     * reflection.
     */
    @SuppressWarnings("unchecked")
    public static <T extends BytecodeDSLExampleBuilder> BytecodeNodes<BytecodeDSLExample> createNodes(Class<? extends BytecodeDSLExample> interpreterClass, BytecodeConfig config,
                    BytecodeParser<T> builder) {
        try {
            Method create = interpreterClass.getMethod("create", BytecodeConfig.class, BytecodeParser.class);
            return (BytecodeNodes<BytecodeDSLExample>) create.invoke(null, config, builder);
        } catch (InvocationTargetException e) {
            // Exceptions thrown by the invoked method can be rethrown as runtime exceptions that
            // get caught by the test harness.
            throw new IllegalStateException(e.getCause());
        } catch (Exception e) {
            // Other exceptions (e.g., NoSuchMethodError) likely indicate a bad reflective call.
            throw new AssertionError("Encountered exception during reflective call: " + e.getMessage());
        }
    }

    public static <T extends BytecodeDSLExampleBuilder> RootCallTarget parse(Class<? extends BytecodeDSLExample> interpreterClass, String rootName, BytecodeParser<T> builder) {
        BytecodeRootNode rootNode = parseNode(interpreterClass, rootName, builder);
        return ((RootNode) rootNode).getCallTarget();
    }

    public static <T extends BytecodeDSLExampleBuilder> BytecodeDSLExample parseNode(Class<? extends BytecodeDSLExample> interpreterClass, String rootName, BytecodeParser<T> builder) {
        BytecodeNodes<BytecodeDSLExample> nodes = BytecodeDSLExampleCommon.createNodes(interpreterClass, BytecodeConfig.DEFAULT, builder);
        BytecodeDSLExample op = nodes.getNodes().get(nodes.getNodes().size() - 1);
        op.setName(rootName);
        return op;
    }

    public static <T extends BytecodeDSLExampleBuilder> BytecodeDSLExample parseNodeWithSource(Class<? extends BytecodeDSLExample> interpreterClass, String rootName, BytecodeParser<T> builder) {
        BytecodeNodes<BytecodeDSLExample> nodes = BytecodeDSLExampleCommon.createNodes(interpreterClass, BytecodeConfig.WITH_SOURCE, builder);
        BytecodeDSLExample op = nodes.getNodes().get(nodes.getNodes().size() - 1);
        op.setName(rootName);
        return op;
    }

    public static List<Class<? extends BytecodeDSLExample>> allInterpreters() {
        return List.of(BytecodeDSLExampleBase.class, BytecodeDSLExampleUnsafe.class, BytecodeDSLExampleWithUncached.class, BytecodeDSLExampleWithBE.class, BytecodeDSLExampleWithOptimizations.class,
                        BytecodeDSLExampleProduction.class);
    }

    public static boolean hasBE(Class<? extends BytecodeDSLExample> c) {
        return c == BytecodeDSLExampleWithBE.class || c == BytecodeDSLExampleProduction.class;
    }

}
