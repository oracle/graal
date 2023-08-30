package com.oracle.truffle.api.operation.test.example;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.operation.OperationConfig;
import com.oracle.truffle.api.operation.OperationNodes;
import com.oracle.truffle.api.operation.OperationParser;
import com.oracle.truffle.api.operation.OperationRootNode;

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
    public static <T extends OperationsExampleBuilder> OperationNodes<OperationsExample> createNodes(Class<? extends OperationsExample> interpreterClass, OperationConfig config,
                    OperationParser<T> builder) {
        try {
            Method create = interpreterClass.getMethod("create", OperationConfig.class, OperationParser.class);
            return (OperationNodes<OperationsExample>) create.invoke(null, config, builder);
        } catch (InvocationTargetException e) {
            // Exceptions thrown by the invoked method can be rethrown as runtime exceptions that
            // get caught by the test harness.
            throw new IllegalStateException(e.getCause());
        } catch (Exception e) {
            // Other exceptions (e.g., NoSuchMethodError) likely indicate a bad reflective call.
            throw new AssertionError("Encountered exception during reflective call: " + e.getMessage());
        }
    }

    public static <T extends OperationsExampleBuilder> RootCallTarget parse(Class<? extends OperationsExample> interpreterClass, String rootName, OperationParser<T> builder) {
        OperationRootNode operationsNode = parseNode(interpreterClass, rootName, builder);
        return ((RootNode) operationsNode).getCallTarget();
    }

    public static <T extends OperationsExampleBuilder> OperationsExample parseNode(Class<? extends OperationsExample> interpreterClass, String rootName, OperationParser<T> builder) {
        OperationNodes<OperationsExample> nodes = OperationsExampleCommon.createNodes(interpreterClass, OperationConfig.DEFAULT, builder);
        OperationsExample op = nodes.getNodes().get(nodes.getNodes().size() - 1);
        op.setName(rootName);
        return op;
    }

    public static <T extends OperationsExampleBuilder> OperationsExample parseNodeWithSource(Class<? extends OperationsExample> interpreterClass, String rootName, OperationParser<T> builder) {
        OperationNodes<OperationsExample> nodes = OperationsExampleCommon.createNodes(interpreterClass, OperationConfig.WITH_SOURCE, builder);
        OperationsExample op = nodes.getNodes().get(nodes.getNodes().size() - 1);
        op.setName(rootName);
        return op;
    }

    public static List<Class<? extends OperationsExample>> allInterpreters() {
        return List.of(OperationsExampleBase.class, OperationsExampleUnsafe.class, OperationsExampleWithBaseline.class, OperationsExampleWithOptimizations.class, OperationsExampleProduction.class);
    }

}
