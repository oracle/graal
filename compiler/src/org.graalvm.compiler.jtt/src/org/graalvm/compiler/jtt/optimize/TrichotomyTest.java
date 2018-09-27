/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.jtt.optimize;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.List;

import org.graalvm.compiler.jtt.JTTTest;
import org.graalvm.compiler.nodes.IfNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.calc.ConditionalNode;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runner.notification.RunNotifier;
import org.junit.runners.BlockJUnit4ClassRunner;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.Statement;

@RunWith(TrichotomyTest.Runner.class)
public class TrichotomyTest extends JTTTest {

    @Test
    @Ignore
    public void dummy() {
        // needed for mx unittest
    }

    public static int compare1(int a, int b) {
        return (a < b) ? -1 : (a == b) ? 0 : 1;
    }

    public static int compare2(int a, int b) {
        return (a < b) ? -1 : (a <= b) ? 0 : 1;
    }

    public static int compare3(int a, int b) {
        return (a < b) ? -1 : (a > b) ? 1 : 0;
    }

    public static int compare4(int a, int b) {
        return (a > b) ? 1 : (a < b) ? -1 : 0;
    }

    public static int compare5(int a, int b) {
        return (a > b) ? 1 : (a == b) ? 0 : -1;
    }

    public static int compare6(int a, int b) {
        return (a > b) ? 1 : (a >= b) ? 0 : -1;
    }

    public static int compare7(int a, int b) {
        return (a == b) ? 0 : (a < b) ? -1 : 1;
    }

    public static int compare8(int a, int b) {
        return (a == b) ? 0 : (a <= b) ? -1 : 1;
    }

    public static int compare9(int a, int b) {
        return (a == b) ? 0 : (a > b) ? 1 : -1;
    }

    public static int compare10(int a, int b) {
        return (a == b) ? 0 : (a >= b) ? 1 : -1;
    }

    public static int compare11(int a, int b) {
        return (a >= b) ? 1 : (a > b) ? 2 : -1;
    }

    public static int compare12(int a, int b) {
        return (a <= b) ? 1 : (a < b) ? 2 : -1;
    }

    public static int compare13(int a, int b) {
        return (a == b) ? 1 : (a == b) ? 2 : -1;
    }

    public static int compare14(int a, int b) {
        return (a != b) ? 1 : (a < b) ? 2 : -1;
    }

    @TestCase(op = Operation.SMALLER)
    public static boolean testSmaller1(int a, int b) {
        return compare1(a, b) == -1;
    }

    @TestCase(op = Operation.SMALLER)
    public static boolean testSmaller2(int a, int b) {
        return compare1(a, b) < 0;
    }

    @TestCase(op = Operation.SMALLER)
    public static boolean testSmaller3(int a, int b) {
        return compare1(a, b) <= -1;
    }

    @TestCase(op = Operation.SMALLER)
    public static boolean testSmaller4(int a, int b) {
        return compare2(a, b) == -1;
    }

    @TestCase(op = Operation.SMALLER)
    public static boolean testSmaller5(int a, int b) {
        return compare2(a, b) < 0;
    }

    @TestCase(op = Operation.SMALLER)
    public static boolean testSmaller6(int a, int b) {
        return compare2(a, b) <= -1;
    }

    @TestCase(op = Operation.SMALLER)
    public static boolean testSmaller7(int a, int b) {
        return compare3(a, b) == -1;
    }

    @TestCase(op = Operation.SMALLER)
    public static boolean testSmaller8(int a, int b) {
        return compare3(a, b) < 0;
    }

    @TestCase(op = Operation.SMALLER)
    public static boolean testSmaller9(int a, int b) {
        return compare3(a, b) <= -1;
    }

    @TestCase(op = Operation.SMALLER)
    public static boolean testSmaller10(int a, int b) {
        return compare4(a, b) == -1;
    }

    @TestCase(op = Operation.SMALLER)
    public static boolean testSmaller11(int a, int b) {
        return compare4(a, b) < 0;
    }

    @TestCase(op = Operation.SMALLER)
    public static boolean testSmaller12(int a, int b) {
        return compare4(a, b) <= -1;
    }

    @TestCase(op = Operation.SMALLER)
    public static boolean testSmaller13(int a, int b) {
        return compare5(a, b) == -1;
    }

    @TestCase(op = Operation.SMALLER)
    public static boolean testSmaller14(int a, int b) {
        return compare5(a, b) < 0;
    }

    @TestCase(op = Operation.SMALLER)
    public static boolean testSmaller15(int a, int b) {
        return compare5(a, b) <= -1;
    }

    @TestCase(op = Operation.SMALLER)
    public static boolean testSmaller16(int a, int b) {
        return compare6(a, b) == -1;
    }

    @TestCase(op = Operation.SMALLER)
    public static boolean testSmaller17(int a, int b) {
        return compare6(a, b) < 0;
    }

    @TestCase(op = Operation.SMALLER)
    public static boolean testSmaller18(int a, int b) {
        return compare6(a, b) <= -1;
    }

    @TestCase(op = Operation.SMALLER)
    public static boolean testSmaller19(int a, int b) {
        return compare7(a, b) == -1;
    }

    @TestCase(op = Operation.SMALLER)
    public static boolean testSmaller20(int a, int b) {
        return compare7(a, b) < 0;
    }

    @TestCase(op = Operation.SMALLER)
    public static boolean testSmaller21(int a, int b) {
        return compare7(a, b) <= -1;
    }

    @TestCase(op = Operation.SMALLER)
    public static boolean testSmaller22(int a, int b) {
        return compare8(a, b) == -1;
    }

    @TestCase(op = Operation.SMALLER)
    public static boolean testSmaller23(int a, int b) {
        return compare8(a, b) < 0;
    }

    @TestCase(op = Operation.SMALLER)
    public static boolean testSmaller24(int a, int b) {
        return compare8(a, b) <= -1;
    }

    @TestCase(op = Operation.SMALLER)
    public static boolean testSmaller25(int a, int b) {
        return compare9(a, b) == -1;
    }

    @TestCase(op = Operation.SMALLER)
    public static boolean testSmaller26(int a, int b) {
        return compare9(a, b) < 0;
    }

    @TestCase(op = Operation.SMALLER)
    public static boolean testSmaller27(int a, int b) {
        return compare9(a, b) <= -1;
    }

    @TestCase(op = Operation.SMALLER)
    public static boolean testSmaller28(int a, int b) {
        return compare10(a, b) == -1;
    }

    @TestCase(op = Operation.SMALLER)
    public static boolean testSmaller29(int a, int b) {
        return compare10(a, b) < 0;
    }

    @TestCase(op = Operation.SMALLER)
    public static boolean testSmaller30(int a, int b) {
        return compare10(a, b) <= -1;
    }

    @TestCase(op = Operation.SMALLER_EQUAL)
    public static boolean testSmallerEqual1(int a, int b) {
        return compare1(a, b) <= 0;
    }

    @TestCase(op = Operation.SMALLER_EQUAL)
    public static boolean testSmallerEqual2(int a, int b) {
        return compare2(a, b) <= 0;
    }

    @TestCase(op = Operation.SMALLER_EQUAL)
    public static boolean testSmallerEqual3(int a, int b) {
        return compare3(a, b) <= 0;
    }

    @TestCase(op = Operation.SMALLER_EQUAL)
    public static boolean testSmallerEqual4(int a, int b) {
        return compare4(a, b) <= 0;
    }

    @TestCase(op = Operation.SMALLER_EQUAL)
    public static boolean testSmallerEqual5(int a, int b) {
        return compare5(a, b) <= 0;
    }

    @TestCase(op = Operation.SMALLER_EQUAL)
    public static boolean testSmallerEqual6(int a, int b) {
        return compare6(a, b) <= 0;
    }

    @TestCase(op = Operation.SMALLER_EQUAL)
    public static boolean testSmallerEqual7(int a, int b) {
        return compare7(a, b) <= 0;
    }

    @TestCase(op = Operation.SMALLER_EQUAL)
    public static boolean testSmallerEqual8(int a, int b) {
        return compare8(a, b) <= 0;
    }

    @TestCase(op = Operation.SMALLER_EQUAL)
    public static boolean testSmallerEqual9(int a, int b) {
        return compare9(a, b) <= 0;
    }

    @TestCase(op = Operation.SMALLER_EQUAL)
    public static boolean testSmallerEqual10(int a, int b) {
        return compare10(a, b) <= 0;
    }

    @TestCase(op = Operation.EQUAL)
    public static boolean testEqual1(int a, int b) {
        return compare1(a, b) == 0;
    }

    @TestCase(op = Operation.EQUAL)
    public static boolean testEqual2(int a, int b) {
        return compare2(a, b) == 0;
    }

    @TestCase(op = Operation.EQUAL)
    public static boolean testEqual3(int a, int b) {
        return compare3(a, b) == 0;
    }

    @TestCase(op = Operation.EQUAL)
    public static boolean testEqual4(int a, int b) {
        return compare4(a, b) == 0;
    }

    @TestCase(op = Operation.EQUAL)
    public static boolean testEqual5(int a, int b) {
        return compare5(a, b) == 0;
    }

    @TestCase(op = Operation.EQUAL)
    public static boolean testEqual6(int a, int b) {
        return compare6(a, b) == 0;
    }

    @TestCase(op = Operation.EQUAL)
    public static boolean testEqual7(int a, int b) {
        return compare7(a, b) == 0;
    }

    @TestCase(op = Operation.EQUAL)
    public static boolean testEqual8(int a, int b) {
        return compare8(a, b) == 0;
    }

    @TestCase(op = Operation.EQUAL)
    public static boolean testEqual9(int a, int b) {
        return compare9(a, b) == 0;
    }

    @TestCase(op = Operation.EQUAL)
    public static boolean testEqual10(int a, int b) {
        return compare10(a, b) == 0;
    }

    @TestCase(op = Operation.GREATER_EQUAL)
    public static boolean testGreaterEqual1(int a, int b) {
        return compare1(a, b) >= 0;
    }

    @TestCase(op = Operation.GREATER_EQUAL)
    public static boolean testGreaterEqual2(int a, int b) {
        return compare2(a, b) >= 0;
    }

    @TestCase(op = Operation.GREATER_EQUAL)
    public static boolean testGreaterEqual3(int a, int b) {
        return compare3(a, b) >= 0;
    }

    @TestCase(op = Operation.GREATER_EQUAL)
    public static boolean testGreaterEqual4(int a, int b) {
        return compare4(a, b) >= 0;
    }

    @TestCase(op = Operation.GREATER_EQUAL)
    public static boolean testGreaterEqual5(int a, int b) {
        return compare5(a, b) >= 0;
    }

    @TestCase(op = Operation.GREATER_EQUAL)
    public static boolean testGreaterEqual6(int a, int b) {
        return compare6(a, b) >= 0;
    }

    @TestCase(op = Operation.GREATER_EQUAL)
    public static boolean testGreaterEqual7(int a, int b) {
        return compare7(a, b) >= 0;
    }

    @TestCase(op = Operation.GREATER_EQUAL)
    public static boolean testGreaterEqual8(int a, int b) {
        return compare8(a, b) >= 0;
    }

    @TestCase(op = Operation.GREATER_EQUAL)
    public static boolean testGreaterEqual9(int a, int b) {
        return compare9(a, b) >= 0;
    }

    @TestCase(op = Operation.GREATER_EQUAL)
    public static boolean testGreaterEqual10(int a, int b) {
        return compare10(a, b) >= 0;
    }

    @TestCase(op = Operation.GREATER)
    public static boolean testGreater1(int a, int b) {
        return compare1(a, b) == 1;
    }

    @TestCase(op = Operation.GREATER)
    public static boolean testGreater2(int a, int b) {
        return compare1(a, b) > 0;
    }

    @TestCase(op = Operation.GREATER)
    public static boolean testGreater3(int a, int b) {
        return compare1(a, b) >= 1;
    }

    @TestCase(op = Operation.GREATER)
    public static boolean testGreater4(int a, int b) {
        return compare2(a, b) == 1;
    }

    @TestCase(op = Operation.GREATER)
    public static boolean testGreater5(int a, int b) {
        return compare2(a, b) > 0;
    }

    @TestCase(op = Operation.GREATER)
    public static boolean testGreater6(int a, int b) {
        return compare2(a, b) >= 1;
    }

    @TestCase(op = Operation.GREATER)
    public static boolean testGreater7(int a, int b) {
        return compare3(a, b) == 1;
    }

    @TestCase(op = Operation.GREATER)
    public static boolean testGreater8(int a, int b) {
        return compare3(a, b) > 0;
    }

    @TestCase(op = Operation.GREATER)
    public static boolean testGreater9(int a, int b) {
        return compare3(a, b) >= 1;
    }

    @TestCase(op = Operation.GREATER)
    public static boolean testGreater10(int a, int b) {
        return compare4(a, b) == 1;
    }

    @TestCase(op = Operation.GREATER)
    public static boolean testGreater11(int a, int b) {
        return compare4(a, b) > 0;
    }

    @TestCase(op = Operation.GREATER)
    public static boolean testGreater12(int a, int b) {
        return compare4(a, b) >= 1;
    }

    @TestCase(op = Operation.GREATER)
    public static boolean testGreater13(int a, int b) {
        return compare5(a, b) == 1;
    }

    @TestCase(op = Operation.GREATER)
    public static boolean testGreater14(int a, int b) {
        return compare5(a, b) > 0;
    }

    @TestCase(op = Operation.GREATER)
    public static boolean testGreater15(int a, int b) {
        return compare5(a, b) >= 1;
    }

    @TestCase(op = Operation.GREATER)
    public static boolean testGreater16(int a, int b) {
        return compare6(a, b) == 1;
    }

    @TestCase(op = Operation.GREATER)
    public static boolean testGreater17(int a, int b) {
        return compare6(a, b) > 0;
    }

    @TestCase(op = Operation.GREATER)
    public static boolean testGreater18(int a, int b) {
        return compare6(a, b) >= 1;
    }

    @TestCase(op = Operation.GREATER)
    public static boolean testGreater19(int a, int b) {
        return compare7(a, b) == 1;
    }

    @TestCase(op = Operation.GREATER)
    public static boolean testGreater20(int a, int b) {
        return compare7(a, b) > 0;
    }

    @TestCase(op = Operation.GREATER)
    public static boolean testGreater21(int a, int b) {
        return compare7(a, b) >= 1;
    }

    @TestCase(op = Operation.GREATER)
    public static boolean testGreater22(int a, int b) {
        return compare8(a, b) == 1;
    }

    @TestCase(op = Operation.GREATER)
    public static boolean testGreater23(int a, int b) {
        return compare8(a, b) > 0;
    }

    @TestCase(op = Operation.GREATER)
    public static boolean testGreater24(int a, int b) {
        return compare8(a, b) >= 1;
    }

    @TestCase(op = Operation.GREATER)
    public static boolean testGreater25(int a, int b) {
        return compare9(a, b) == 1;
    }

    @TestCase(op = Operation.GREATER)
    public static boolean testGreater26(int a, int b) {
        return compare9(a, b) > 0;
    }

    @TestCase(op = Operation.GREATER)
    public static boolean testGreater27(int a, int b) {
        return compare9(a, b) >= 1;
    }

    @TestCase(op = Operation.GREATER)
    public static boolean testGreater28(int a, int b) {
        return compare10(a, b) == 1;
    }

    @TestCase(op = Operation.GREATER)
    public static boolean testGreater29(int a, int b) {
        return compare10(a, b) > 0;
    }

    @TestCase(op = Operation.GREATER)
    public static boolean testGreater30(int a, int b) {
        return compare10(a, b) >= 1;
    }

    @TestCase(op = Operation.ALWAYS_FALSE)
    public static boolean testAlwaysFalse1(int a, int b) {
        return compare11(a, b) == 2;
    }

    @TestCase(op = Operation.ALWAYS_FALSE)
    public static boolean testAlwaysFalse2(int a, int b) {
        return compare11(a, b) > 1;
    }

    @TestCase(op = Operation.ALWAYS_FALSE)
    public static boolean testAlwaysFalse3(int a, int b) {
        return compare11(a, b) >= 2;
    }

    @TestCase(op = Operation.ALWAYS_FALSE)
    public static boolean testAlwaysFalse4(int a, int b) {
        return compare12(a, b) == 2;
    }

    @TestCase(op = Operation.ALWAYS_FALSE)
    public static boolean testAlwaysFalse5(int a, int b) {
        return compare12(a, b) > 1;
    }

    @TestCase(op = Operation.ALWAYS_FALSE)
    public static boolean testAlwaysFalse6(int a, int b) {
        return compare12(a, b) >= 2;
    }

    @TestCase(op = Operation.ALWAYS_FALSE)
    public static boolean testAlwaysFalse7(int a, int b) {
        return compare13(a, b) == 2;
    }

    @TestCase(op = Operation.ALWAYS_FALSE)
    public static boolean testAlwaysFalse8(int a, int b) {
        return compare13(a, b) > 1;
    }

    @TestCase(op = Operation.ALWAYS_FALSE)
    public static boolean testAlwaysFalse9(int a, int b) {
        return compare13(a, b) >= 2;
    }

    @TestCase(op = Operation.ALWAYS_FALSE, ignoreFold = true)
    public static boolean testAlwaysFalse10(int a, int b) {
        return compare14(a, b) == 2;
    }

    @TestCase(op = Operation.ALWAYS_FALSE, ignoreFold = true)
    public static boolean testAlwaysFalse11(int a, int b) {
        return compare14(a, b) > 1;
    }

    @TestCase(op = Operation.ALWAYS_FALSE, ignoreFold = true)
    public static boolean testAlwaysFalse12(int a, int b) {
        return compare14(a, b) >= 2;
    }

    enum Operation {
        SMALLER,
        SMALLER_EQUAL,
        EQUAL,
        GREATER_EQUAL,
        GREATER,
        ALWAYS_FALSE
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @interface TestCase {
        Operation op();

        boolean ignoreFold() default false;
    }

    private static void runTest(TrichotomyTest self, FrameworkMethod method) {
        String name = method.getName();
        TestCase test = method.getAnnotation(TestCase.class);
        Operation op = test.op();
        Result result = self.test(name, 0, 0);
        Assert.assertEquals(result.returnValue, (op == Operation.EQUAL || op == Operation.SMALLER_EQUAL || op == Operation.GREATER_EQUAL) ? true : false);
        result = self.test(name, 0, 1);
        Assert.assertEquals(result.returnValue, (op == Operation.SMALLER || op == Operation.SMALLER_EQUAL) ? true : false);
        result = self.test(name, 1, 0);
        Assert.assertEquals(result.returnValue, (op == Operation.GREATER || op == Operation.GREATER_EQUAL) ? true : false);
        StructuredGraph graph = self.getFinalGraph(name);
        if (!test.ignoreFold()) {
            Assert.assertTrue("Too many ConditionalNodes", graph.getNodes().filter(ConditionalNode.class).count() <= 1);
            Assert.assertTrue("Unexpected IfNodes", graph.getNodes().filter(IfNode.class).isEmpty());
        }
    }

    public static class Runner extends BlockJUnit4ClassRunner {
        public Runner(Class<?> klass) throws InitializationError {
            super(klass);
        }

        @Override
        protected List<FrameworkMethod> computeTestMethods() {
            return getTestClass().getAnnotatedMethods(TestCase.class);
        }

        @Override
        protected void runChild(FrameworkMethod method, RunNotifier notifier) {
            super.runChild(method, notifier);
        }

        @Override
        protected Statement methodInvoker(FrameworkMethod method, Object test) {
            return new Statement() {
                @Override
                public void evaluate() throws Throwable {
                    runTest((TrichotomyTest) test, method);
                }
            };
        }
    }
}
