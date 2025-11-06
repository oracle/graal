package com.oracle.svm.hosted.analysis.ai.checker.optimize;

import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.graph.Node;
import com.oracle.svm.hosted.analysis.ai.log.AbstractInterpretationLogger;
import com.oracle.svm.hosted.analysis.ai.log.LoggerVerbosity;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;

/**
 * Helper utilities for graph mutation. Implementations that perform real graph
 * modifications should be added here. This file contains a conservative stub
 * that attempts various reflective insertion strategies to make facts visible
 * in StructuredGraph dumps.
 */
public final class GraphMutationHelpers {

    private GraphMutationHelpers() {
    }

    public static boolean insertAssertInvoke(StructuredGraph graph, Node targetNode, long constVal) {
        AbstractInterpretationLogger logger = AbstractInterpretationLogger.getInstance();
        logger.log("Request to insert assert for node: " + (targetNode == null ? "NULL" : targetNode.toString()) + " = " + constVal, LoggerVerbosity.CHECKER);

        if (graph == null) return false;

        try {
            ConstantNode c;
            if (constVal >= Integer.MIN_VALUE && constVal <= Integer.MAX_VALUE) {
                c = ConstantNode.forInt((int) constVal);
            } else {
                logger.log("GraphMutationHelpers: constant value out of int range; skipping insertion.", LoggerVerbosity.CHECKER_WARN);
                return false;
            }

            // Strategy 1: try any single-arg method, invoke with our ConstantNode
            Method[] methods = graph.getClass().getMethods();
            for (Method m : methods) {
                try {
                    Class<?>[] params = m.getParameterTypes();
                    if (params.length == 1) {
                        // Try invoking even if param type is Object or superclass; wrap in try/catch
                        try {
                            m.setAccessible(true);
                            m.invoke(graph, c);
                            logger.log("GraphMutationHelpers: inserted ConstantNode via method: " + m.getName(), LoggerVerbosity.CHECKER);
                            return true;
                        } catch (Throwable ex) {
                            // ignore and continue
                        }
                    }
                } catch (Throwable ex) {
                    // ignore reflection errors on this method
                }
            }

            // Strategy 2: try zero-arg methods that return a collection-like object and call add(c)
            for (Method m : methods) {
                try {
                    if (m.getParameterCount() != 0) continue;
                    Object ret;
                    try {
                        m.setAccessible(true);
                        ret = m.invoke(graph);
                    } catch (Throwable ex) {
                        continue;
                    }
                    if (ret == null) continue;
                    if (ret instanceof Collection) {
                        try {
                            @SuppressWarnings("unchecked")
                            Collection<Object> coll = (Collection<Object>) ret;
                            coll.add(c);
                            logger.log("GraphMutationHelpers: added ConstantNode to collection returned by: " + m.getName(), LoggerVerbosity.CHECKER);
                            return true;
                        } catch (Throwable ex) {
                            // ignore
                        }
                    } else {
                        // Try to find an add method on the returned object
                        Method[] retMethods = ret.getClass().getMethods();
                        for (Method rm : retMethods) {
                            if (rm.getName().equals("add") && rm.getParameterCount() == 1) {
                                try {
                                    rm.setAccessible(true);
                                    rm.invoke(ret, c);
                                    logger.log("GraphMutationHelpers: added ConstantNode via add() on object returned by: " + m.getName(), LoggerVerbosity.CHECKER);
                                    return true;
                                } catch (Throwable ex) {
                                    // ignore
                                }
                            }
                        }
                    }
                } catch (Throwable ex) {
                    // ignore
                }
            }

            // Strategy 3: inspect fields that are collections and try to add
            Field[] fields = graph.getClass().getFields();
            for (Field f : fields) {
                try {
                    Object v = f.get(graph);
                    if (v == null) continue;
                    if (v instanceof Collection) {
                        try {
                            @SuppressWarnings("unchecked")
                            Collection<Object> coll = (Collection<Object>) v;
                            coll.add(c);
                            logger.log("GraphMutationHelpers: added ConstantNode to collection field: " + f.getName(), LoggerVerbosity.CHECKER);
                            return true;
                        } catch (Throwable ex) {
                            // ignore
                        }
                    }
                    Method[] vm = v.getClass().getMethods();
                    for (Method vmM : vm) {
                        if (vmM.getName().equals("add") && vmM.getParameterCount() == 1) {
                            try {
                                vmM.setAccessible(true);
                                vmM.invoke(v, c);
                                logger.log("GraphMutationHelpers: added ConstantNode via add() on field: " + f.getName(), LoggerVerbosity.CHECKER);
                                return true;
                            } catch (Throwable ex) {
                                // ignore
                            }
                        }
                    }
                } catch (Throwable ex) {
                    // ignore
                }
            }

            logger.log("GraphMutationHelpers: no reflective insertion succeeded; consider implementing version-specific insertion.", LoggerVerbosity.CHECKER_WARN);
            return false;
        } catch (Throwable ex) {
            logger.log("GraphMutationHelpers failed to insert assertion: " + ex.getMessage(), LoggerVerbosity.CHECKER_ERR);
            return false;
        }
    }
}
