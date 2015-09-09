package com.oracle.graal.microbenchmarks.graal.util;

import java.lang.reflect.*;
import java.util.*;

import jdk.internal.jvmci.meta.*;

import com.oracle.graal.graph.*;
import com.oracle.graal.graphbuilderconf.*;
import com.oracle.graal.graphbuilderconf.GraphBuilderConfiguration.Plugins;
import com.oracle.graal.java.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.phases.*;
import com.oracle.graal.phases.tiers.*;

public class GraalUtil {

    public static Method getMethod(Class<?> declaringClass, String name, Class<?>... parameterTypes) {
        try {
            if (parameterTypes == null) {
                Method found = null;
                for (Method m : declaringClass.getDeclaredMethods()) {
                    if (m.getName().equals(name)) {
                        if (found != null) {
                            throw new RuntimeException("more than one method named " + name + " in " + declaringClass);
                        }
                        found = m;
                    }
                }
                if (found == null) {
                    throw new NoSuchMethodException(declaringClass.getName() + "." + name);
                }
                return found;
            } else {
                return declaringClass.getDeclaredMethod(name, parameterTypes);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Gets the first {@link MethodSpec} annotation encountered in the class hierarchy terminated by
     * {@code startClass}.
     */
    public static MethodSpec getMethodSpec(Class<?> startClass) {
        Class<?> c = startClass;
        while (c != null) {
            MethodSpec methodSpec = c.getAnnotation(MethodSpec.class);
            if (methodSpec != null) {
                return methodSpec;
            }
            c = c.getSuperclass();
        }
        throw new RuntimeException("Could not find class annotated with " + MethodSpec.class.getSimpleName() + " in hierarchy of " + startClass);
    }

    /**
     * Gets the method specified by the first {@link MethodSpec} annotation encountered in the class
     * hierarchy terminated by {@code startClass}.
     */
    public static Method getMethodFromMethodSpec(Class<?> startClass) {
        MethodSpec methodSpec = getMethodSpec(startClass);
        Class<?> declaringClass = methodSpec.declaringClass();
        if (declaringClass == MethodSpec.class) {
            declaringClass = startClass;
        }
        String name = methodSpec.name();
        Class<?>[] parameters = methodSpec.parameters();
        if (parameters.length == 1 && parameters[0] == void.class) {
            parameters = null;
        }
        return getMethod(declaringClass, name, parameters);
    }

    /**
     * Gets the graph for the method specified by the first {@link MethodSpec} annotation
     * encountered in the class hierarchy terminated by {@code startClass}.
     */
    public static StructuredGraph getGraphFromMethodSpec(Class<?> startClass) {
        MethodSpec methodSpec = getMethodSpec(startClass);
        Class<?> declaringClass = methodSpec.declaringClass();
        if (declaringClass == MethodSpec.class) {
            declaringClass = startClass;
        }
        String name = methodSpec.name();
        Class<?>[] parameters = methodSpec.parameters();
        if (parameters.length == 1 && parameters[0] == void.class) {
            parameters = null;
        }
        return getGraph(declaringClass, name, parameters);
    }

    public static StructuredGraph getGraph(Class<?> declaringClass, String name, Class<?>... parameterTypes) {
        return getGraph(getMethod(declaringClass, name, parameterTypes));
    }

    public static StructuredGraph getGraph(Method method) {
        GraalState graal = new GraalState();
        ResolvedJavaMethod javaMethod = graal.metaAccess.lookupJavaMethod(method);
        return getGraph(graal, javaMethod);
    }

    public static StructuredGraph getGraph(GraalState graal, ResolvedJavaMethod javaMethod) {
        StructuredGraph graph = new StructuredGraph(javaMethod, StructuredGraph.AllowAssumptions.YES);
        PhaseSuite<HighTierContext> graphBuilderSuite = new PhaseSuite<>();
        MetaAccessProvider metaAccess = graal.providers.getMetaAccess();
        graphBuilderSuite.appendPhase(new GraphBuilderPhase(GraphBuilderConfiguration.getDefault(new Plugins(new InvocationPlugins(metaAccess)))));
        graphBuilderSuite.apply(graph, new HighTierContext(graal.providers, graphBuilderSuite, OptimisticOptimizations.ALL));
        return graph;
    }

    public static Node[] getNodes(StructuredGraph graph) {
        List<Node> nodeList = graph.getNodes().snapshot();
        return nodeList.toArray(new Node[nodeList.size()]);
    }
}
