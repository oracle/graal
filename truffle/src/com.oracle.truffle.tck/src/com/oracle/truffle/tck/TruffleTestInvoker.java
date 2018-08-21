/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.graalvm.polyglot.Context;
import org.junit.Test;
import org.junit.runners.model.FrameworkField;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.Statement;
import org.junit.runners.model.TestClass;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.impl.TVMCI;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.tck.TruffleRunner.Inject;
import com.oracle.truffle.tck.TruffleRunner.RunWithPolyglotRule;

final class TruffleTestInvoker<T extends CallTarget> extends TVMCI.TestAccessor<T> {

    static TruffleTestInvoker<?> create() {
        TVMCI.Test<?> testTvmci = Truffle.getRuntime().getCapability(TVMCI.Test.class);
        return new TruffleTestInvoker<>(testTvmci);
    }

    @TruffleLanguage.Registration(id = "truffletestinvoker", name = "truffletestinvoker", version = "")
    public static class TruffleTestInvokerLanguage extends TruffleLanguage<Env> {

        @Override
        protected Env createContext(Env env) {
            return env;
        }

        @Override
        protected boolean isObjectOfLanguage(Object object) {
            return object instanceof TestStatement;
        }

        @Override
        protected void initializeContext(Env context) throws Exception {
            context.exportSymbol("env", context.asGuestValue(context));
        }

    }

    private static class TestStatement extends Statement {

        private final RunWithPolyglotRule rule;
        private final Statement stmt;

        TestStatement(RunWithPolyglotRule rule, Statement stmt) {
            this.rule = rule;
            this.stmt = stmt;
        }

        @Override
        public void evaluate() throws Throwable {
            Context prevContext = rule.context;
            try (Context context = rule.contextBuilder.build()) {
                rule.context = context;

                context.initialize("truffletestinvoker");
                context.enter();
                Env prevEnv = rule.testEnv;
                try {
                    rule.testEnv = context.getPolyglotBindings().getMember("env").asHostObject();
                    stmt.evaluate();
                } catch (Throwable t) {
                    throw t;
                } finally {
                    rule.testEnv = prevEnv;
                    context.leave();
                }
            } finally {
                rule.context = prevContext;
            }
        }

    }

    private TruffleTestInvoker(TVMCI.Test<T> testTvmci) {
        super(testTvmci);
    }

    private interface NodeConstructor extends Function<Object, RootNode> {
    }

    static class TruffleTestClass extends TestClass {

        TruffleTestClass(Class<?> cls) {
            super(cls);
        }

        @Override
        protected void scanAnnotatedMembers(Map<Class<? extends Annotation>, List<FrameworkMethod>> methodsForAnnotations,
                        Map<Class<? extends Annotation>, List<FrameworkField>> fieldsForAnnotations) {
            super.scanAnnotatedMembers(methodsForAnnotations, fieldsForAnnotations);

            for (List<FrameworkMethod> methods : methodsForAnnotations.values()) {
                methods.replaceAll(m -> new TruffleFrameworkMethod(this, m.getMethod()));
            }
        }
    }

    private static class TruffleFrameworkMethod extends FrameworkMethod {

        private final int warmupIterations;
        private final NodeConstructor[] nodeConstructors;

        TruffleFrameworkMethod(TestClass testClass, Method method) {
            super(method);

            int paramCount = method.getParameterCount();
            if (paramCount == 0) {
                // non-truffle test
                nodeConstructors = null;
            } else {
                nodeConstructors = new NodeConstructor[paramCount];

                for (int i = 0; i < paramCount; i++) {
                    Inject testRootNode = findRootNodeAnnotation(method.getParameterAnnotations()[i]);
                    nodeConstructors[i] = getNodeConstructor(testRootNode, testClass);
                }
            }

            TruffleRunner.Warmup warmup = method.getAnnotation(TruffleRunner.Warmup.class);
            if (warmup != null) {
                warmupIterations = warmup.value();
            } else {
                warmupIterations = 3;
            }
        }

        RootNode[] createTestRootNodes(Object test) {
            if (nodeConstructors == null) {
                // non-truffle test
                return null;
            }

            RootNode[] ret = new RootNode[nodeConstructors.length];
            for (int i = 0; i < ret.length; i++) {
                ret[i] = nodeConstructors[i].apply(test);
            }
            return ret;
        }
    }

    Statement createStatement(String testName, FrameworkMethod method, Object test) {
        final TruffleFrameworkMethod truffleMethod = (TruffleFrameworkMethod) method;
        final RootNode[] testNodes = truffleMethod.createTestRootNodes(test);
        if (testNodes == null) {
            return null;
        }

        return new Statement() {

            @Override
            public void evaluate() throws Throwable {
                ArrayList<T> callTargets = new ArrayList<>(testNodes.length);
                for (RootNode testNode : testNodes) {
                    callTargets.add(createTestCallTarget(testNode));
                }

                Object[] args = callTargets.toArray();
                for (int i = 0; i < truffleMethod.warmupIterations; i++) {
                    truffleMethod.invokeExplosively(test, args);
                }

                for (T callTarget : callTargets) {
                    finishWarmup(callTarget, testName);
                }
                truffleMethod.invokeExplosively(test, args);
            }
        };
    }

    static Statement withTruffleContext(RunWithPolyglotRule rule, Statement stmt) {
        return new TestStatement(rule, stmt);
    }

    private static Inject findRootNodeAnnotation(Annotation[] annotations) {
        for (Annotation a : annotations) {
            if (a instanceof Inject) {
                return (Inject) a;
            }
        }
        return null;
    }

    private static NodeConstructor getNodeConstructor(Inject annotation, TestClass testClass) {
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
