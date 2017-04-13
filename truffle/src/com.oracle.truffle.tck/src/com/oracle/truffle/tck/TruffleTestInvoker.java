/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.oracle.truffle.tck;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.impl.TVMCI;
import com.oracle.truffle.api.nodes.RootNode;
import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import org.junit.Test;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.Statement;
import org.junit.runners.model.TestClass;
import com.oracle.truffle.tck.TruffleRunner.Inject;

final class TruffleTestInvoker<T extends CallTarget> extends TVMCI.TestAccessor<T> {

    static TruffleTestInvoker<?> create() {
        TVMCI.Test<?> testTvmci = Truffle.getRuntime().getCapability(TVMCI.Test.class);
        return new TruffleTestInvoker<>(testTvmci);
    }

    private TruffleTestInvoker(TVMCI.Test<T> testTvmci) {
        super(testTvmci);
    }

    private static int getWarmupIterations(FrameworkMethod method) {
        TruffleRunner.Warmup warmup = method.getAnnotation(TruffleRunner.Warmup.class);
        if (warmup != null) {
            return warmup.value();
        } else {
            return 3;
        }
    }

    private static RootNode[] createTestRootNodes(TestClass testClass, FrameworkMethod testMethod, Object test) {
        int paramCount = testMethod.getMethod().getParameterCount();
        if (paramCount == 0) {
            // non-truffle test
            return null;
        }

        RootNode[] testNodes = new RootNode[paramCount];

        for (int i = 0; i < paramCount; i++) {
            Inject testRootNode = findRootNodeAnnotation(testMethod.getMethod().getParameterAnnotations()[i]);
            Function<Object, RootNode> cons = getNodeConstructor(testRootNode, testClass);
            testNodes[i] = cons.apply(test);
        }

        return testNodes;
    }

    Statement createStatement(String testName, TestClass testClass, FrameworkMethod method, Object test) {
        final RootNode[] testNodes = createTestRootNodes(testClass, method, test);
        if (testNodes == null) {
            return null;
        }

        final int warmupIterations = getWarmupIterations(method);

        return new Statement() {

            @Override
            public void evaluate() throws Throwable {
                ArrayList<T> callTargets = new ArrayList<>(testNodes.length);
                for (RootNode testNode : testNodes) {
                    callTargets.add(createTestCallTarget(testName, testNode));
                }

                Object[] args = callTargets.toArray();
                for (int i = 0; i < warmupIterations; i++) {
                    method.invokeExplosively(test, args);
                }

                for (T callTarget : callTargets) {
                    finishWarmup(callTarget);
                }
                method.invokeExplosively(test, args);
            }
        };
    }

    private static Inject findRootNodeAnnotation(Annotation[] annotations) {
        for (Annotation a : annotations) {
            if (a instanceof Inject) {
                return (Inject) a;
            }
        }
        return null;
    }

    private static Function<Object, RootNode> getNodeConstructor(Inject annotation, TestClass testClass) {
        Class<? extends RootNode> nodeClass = annotation.value();
        try {
            Constructor<? extends RootNode> cons = nodeClass.getConstructor(testClass.getJavaClass());
            return (obj) -> {
                try {
                    return cons.newInstance(obj);
                } catch (IllegalAccessException | IllegalArgumentException | InstantiationException | InvocationTargetException ex) {
                    throw new AssertionError(ex);
                }
            };
        } catch (NoSuchMethodException e) {
            try {
                Constructor<? extends RootNode> cons = nodeClass.getConstructor();
                return (obj) -> {
                    try {
                        return cons.newInstance();
                    } catch (IllegalAccessException | IllegalArgumentException | InstantiationException | InvocationTargetException ex) {
                        throw new AssertionError(ex);
                    }
                };
            } catch (NoSuchMethodException ex) {
                return null;
            }
        }
    }

    static void validateTestMethods(TestClass testClass, List<Throwable> errors) {
        List<FrameworkMethod> methods = testClass.getAnnotatedMethods(Test.class);
        for (FrameworkMethod method : methods) {
            method.validatePublicVoid(false, errors);

            Annotation[][] parameterAnnotations = method.getMethod().getParameterAnnotations();
            Class<?>[] parameterTypes = method.getMethod().getParameterTypes();
            for (int i = 0; i < parameterTypes.length; i++) {
                if (parameterTypes[i] == CallTarget.class) {
                    TruffleRunner.Inject testRootNode = findRootNodeAnnotation(parameterAnnotations[i]);
                    if (testRootNode == null) {
                        errors.add(new Exception("CallTarget parameter of test method " + method.getName() + " should have @Inject annotation"));
                    } else {
                        if (getNodeConstructor(testRootNode, testClass) == null) {
                            errors.add(new Exception("Node " + testRootNode.value().getName() + " should have a default constructor or a constructor taking a " + testClass.getName()));
                        }
                    }
                } else {
                    errors.add(new Exception("Invalid parameter type " + parameterTypes[i].getSimpleName() + " on test method " + method.getName()));
                }
            }
        }
    }
}
