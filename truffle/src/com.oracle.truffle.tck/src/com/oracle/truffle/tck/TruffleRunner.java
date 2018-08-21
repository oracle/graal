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

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.graalvm.polyglot.Context;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runner.RunWith;
import org.junit.runner.Runner;
import org.junit.runners.BlockJUnit4ClassRunner;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;
import org.junit.runners.Parameterized.UseParametersRunnerFactory;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.Statement;
import org.junit.runners.parameterized.BlockJUnit4ClassRunnerWithParameters;
import org.junit.runners.parameterized.ParametersRunnerFactory;
import org.junit.runners.parameterized.TestWithParameters;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.tck.TruffleRunner.Inject;
import com.oracle.truffle.tck.TruffleRunner.RunWithPolyglotRule;
import com.oracle.truffle.tck.TruffleRunner.Warmup;
import com.oracle.truffle.tck.TruffleTestInvoker.TruffleTestClass;
import org.junit.runners.model.TestClass;

/**
 * JUnit test runner for unit testing Truffle AST interpreters.
 * <p>
 * A test using {@link TruffleRunner} consists of 2 parts, a Truffle AST to be tested, and a test
 * method that drives the test, provides input argument values and validates the result.
 *
 * <h4>Writing a test AST</h4>
 *
 * The Truffle AST to be tested is written as a {@link RootNode} subclass, for example:
 * <p>
 * {@codesnippet TruffleRunnerSnippets#TestExecuteNode}
 *
 * <h4>Writing a test method</h4>
 *
 * The test method is a normal method annotated with {@link Test}. It may have one or more arguments
 * of type {@link CallTarget} that are annotated with {@link Inject}. The {@link Inject} annotation
 * specifies a {@link RootNode} subclass that is the root of a test AST, and the
 * {@link TruffleRunner} will create one {@link CallTarget} for each of these test ASTs. The test
 * method can then execute the AST by calling the {@link CallTarget#call} method.
 * <p>
 * Typically a test method will prepare some arguments, and then do a single call to
 * {@link CallTarget#call}. Then it should verify the result by inspecting the return value and
 * checking the expected side effects of the test code.
 * <p>
 * {@codesnippet TruffleRunnerSnippets#ExampleTest}
 *
 * <h4>Running a test in the polyglot engine</h4>
 *
 * If a test should be run in the context of a polyglot engine, {@link RunWithPolyglotRule} can be
 * used.
 * <p>
 * {@codesnippet TruffleRunnerSnippets#RunWithPolyglotRule}
 *
 * @see Warmup warmup iterations and compilation
 * @see ParametersFactory parameterized Truffle AST tests
 *
 * @since 0.25
 */
public final class TruffleRunner extends BlockJUnit4ClassRunner {

    private static final TruffleTestInvoker<?> truffleTestInvoker = TruffleTestInvoker.create();

    /**
     * A parameter annotated with {@link Inject} specifies the {@link RootNode} of the test AST.
     *
     * @see TruffleRunner
     *
     * @since 0.25
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.PARAMETER)
    public @interface Inject {

        /**
         * Defines the {@link RootNode root node} of the Truffle tree that should be tested.
         *
         * @since 0.25
         */
        Class<? extends RootNode> value();
    }

    /**
     * A test method can be annotated with {@link Warmup} to specify how many warmup iterations of a
     * test should be done before the Truffle tree is compiled. If this annotation is missing, the
     * default value of 3 is used.
     * <p>
     * {@codesnippet TruffleRunnerSnippets#warmupTest}
     * <p>
     * In this example, the test code will in total be run 6 times. The first 5 iterations are
     * warmup. The {@link CallTarget#call} invocation will run in the interpreter, simply calling
     * the {@link RootNode#execute} method. This allows the AST to specialize itself before it is
     * compiled.
     * <p>
     * After warmup, the resulting specialized AST is compiled, and in the final iteration the
     * {@link CallTarget} represents the resulting compiled code.
     *
     * @see TruffleRunner
     *
     * @since 0.25
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    public @interface Warmup {

        /**
         * The number of warmup iterations to run before a test is compiled.
         *
         * @since 0.25
         */
        int value();
    }

    /**
     * {@link ParametersRunnerFactory} for testing Truffle AST interpreters using
     * {@link Parameterized} unit tests. To use the parameters for constructing the test AST, the
     * test {@link RootNode} constructor may take the test class as single argument, or
     * alternatively the test {@link RootNode} can be a non-static inner class of the test class.
     * <p>
     * {@codesnippet TruffleRunnerSnippets#ParameterizedTest}
     *
     * @see TruffleRunner
     *
     * @since 0.25
     */
    public static final class ParametersFactory implements ParametersRunnerFactory {

        /**
         * Should not be called directly. To use this class, annotate your test class with
         * {@code @Parameterized.UseParametersRunnerFactory(TruffleRunner.ParametersFactory.class)}.
         *
         * @see TruffleRunner
         *
         * @since 0.25
         */
        public ParametersFactory() {
        }

        /**
         * Internal method used by the JUnit framework. Do not call directly.
         *
         * @since 0.25
         */
        @Override
        public Runner createRunnerForTestWithParameters(TestWithParameters test) throws InitializationError {
            return new ParameterizedRunner(test);
        }
    }

    /**
     * JUnit rule to run the tests in the context of a polyglot engine. This can be used as a
     * {@link ClassRule} or as a {@link Rule}.
     * <p>
     * If used as {@link ClassRule}, a single context is created for all unit tests in this class,
     * and all tests (and also other methods like {@link BeforeClass}, {@link Before} {@link After}
     * and {@link AfterClass}) are executed in this context.
     * <p>
     * If used as {@link Rule}, a new context is created for each unit test. The {@link Before} and
     * {@link After} actions are also executed in this context. No context is available in the
     * {@link BeforeClass} and {@link AfterClass} methods.
     *
     * @since 0.27
     */
    public static final class RunWithPolyglotRule implements TestRule {

        Context.Builder contextBuilder;

        Context context = null;
        Env testEnv = null;

        /**
         * @since 0.27
         */
        public RunWithPolyglotRule() {
            this(Context.newBuilder().allowAllAccess(true));
        }

        /**
         * @param contextBuilder a custom context builder
         * @since 1.0
         */
        public RunWithPolyglotRule(Context.Builder contextBuilder) {
            this.contextBuilder = contextBuilder;
        }

        /**
         * Internal method used by the JUnit framework. Do not call directly.
         *
         * @since 0.27
         */
        @Override
        public Statement apply(Statement stmt, Description description) {
            return TruffleTestInvoker.withTruffleContext(this, stmt);
        }

        /**
         * Get the current {@link Context}. This should only be called from code that is executed by
         * the {@link TruffleRunner}. In particular, this method can not be called from static
         * initializers and constructors of test classes. Use {@link Before} or {@link BeforeClass}
         * methods instead, or put the initialization code into the constructor of the
         * {@link RootNode} of the test.
         *
         * @since 0.27
         */
        public Context getPolyglotContext() {
            assert context != null;
            return context;
        }

        /**
         * Get an environment to access the polyglot engine using interop. This can be used to run
         * setup tasks, and to do mock interop access into the polyglot engine. This should only be
         * called from code that is executed by the {@link TruffleRunner}. In particular, this
         * method can not be called from static initializers and constructors of test classes. Use
         * {@link Before} or {@link BeforeClass} methods instead, or put the initialization code
         * into the constructor of the {@link RootNode} of the test.
         *
         * @since 0.27
         */
        public Env getTruffleTestEnv() {
            assert testEnv != null;
            return testEnv;
        }
    }

    private static final class ParameterizedRunner extends BlockJUnit4ClassRunnerWithParameters {

        ParameterizedRunner(TestWithParameters test) throws InitializationError {
            super(test);
        }

        @Override
        protected Statement methodInvoker(FrameworkMethod method, Object test) {
            Statement ret = truffleTestInvoker.createStatement(getName(), method, test);
            if (ret == null) {
                ret = super.methodInvoker(method, test);
            }
            return ret;
        }

        @Override
        protected void validateTestMethods(List<Throwable> errors) {
            TruffleTestInvoker.validateTestMethods(getTestClass(), errors);
        }

        /**
         * Internal method used by the JUnit framework. Do not call directly.
         *
         * @since 0.27
         */
        @Override
        protected TestClass createTestClass(Class<?> testClass) {
            return new TruffleTestClass(testClass);
        }
    }

    /**
     * Should not be called directly. To use this class, annotate your test class with
     * {@code @RunWith(TruffleRunner.class)}.
     *
     * @see TruffleRunner
     *
     * @since 0.25
     */
    public TruffleRunner(Class<?> klass) throws InitializationError {
        super(klass);
    }

    /**
     * Internal method used by the JUnit framework. Do not call directly.
     *
     * @since 0.25
     */
    @Override
    protected Statement methodInvoker(FrameworkMethod method, Object test) {
        Statement ret = truffleTestInvoker.createStatement(testName(method), method, test);
        if (ret == null) {
            ret = super.methodInvoker(method, test);
        }
        return ret;
    }

    /**
     * Internal method used by the JUnit framework. Do not call directly.
     *
     * @since 0.25
     */
    @Override
    protected void validateTestMethods(List<Throwable> errors) {
        TruffleTestInvoker.validateTestMethods(getTestClass(), errors);
    }

    /**
     * Internal method used by the JUnit framework. Do not call directly.
     *
     * @since 0.27
     */
    @Override
    protected TestClass createTestClass(Class<?> testClass) {
        return new TruffleTestClass(testClass);
    }
}

class TruffleRunnerSnippets {

    // Checkstyle: stop
    // BEGIN: TruffleRunnerSnippets#TestExecuteNode
    public class TestExecuteNode extends RootNode {

        @Child Node executeNode;

        public TestExecuteNode() {
            super(null);
            executeNode = Message.EXECUTE.createNode();
        }

        @Override
        public Object execute(VirtualFrame frame) {
            TruffleObject obj = (TruffleObject) frame.getArguments()[0];
            try {
                return ForeignAccess.sendExecute(executeNode, obj);
            } catch (InteropException ex) {
                CompilerDirectives.transferToInterpreter();
                Assert.fail(ex.getMessage());
                return null;
            }
        }
    }
    // END: TruffleRunnerSnippets#TestExecuteNode
    // Checkstyle: resume

    private static TruffleObject prepareArgumentValue() {
        return null;
    }

    private static Object expectedRetValue() {
        return null;
    }

    // BEGIN: TruffleRunnerSnippets#ExampleTest
    @RunWith(TruffleRunner.class)
    public class ExampleTest {

        @Test
        public void executeTest(@Inject(TestExecuteNode.class) CallTarget target) {
            TruffleObject receiver = prepareArgumentValue();
            Object ret = target.call(receiver);
            Assert.assertEquals(expectedRetValue(), ret);
        }
    }
    // END: TruffleRunnerSnippets#ExampleTest

    // BEGIN: TruffleRunnerSnippets#warmupTest
    @Test
    @Warmup(5)
    public void warmupTest(@Inject(TestExecuteNode.class) CallTarget target) {
        TruffleObject receiver = prepareArgumentValue();
        Object ret = target.call(receiver);
        Assert.assertEquals(expectedRetValue(), ret);
    }
    // END: TruffleRunnerSnippets#warmupTest

    // Checkstyle: stop
    // BEGIN: TruffleRunnerSnippets#ParameterizedTest
    @RunWith(Parameterized.class)
    @UseParametersRunnerFactory(TruffleRunner.ParametersFactory.class)
    public static class ParameterizedTest {

        @Parameters(name = "{0}, {1}")
        public static Collection<Object[]> data() {
            ArrayList<Object[]> ret = new ArrayList<>();
            ret.add(new Object[]{5, "test"});
            ret.add(new Object[]{-3, "asdf"});
            return ret;
        }

        @Parameter(0) int intParam;
        @Parameter(1) String stringParam;

        public class TestConstArgNode extends RootNode {

            private final int iArg;
            private final String sArg;

            @Child Node executeNode;

            public TestConstArgNode() {
                super(null);
                this.iArg = intParam;
                this.sArg = stringParam;
            }

            @Override
            public Object execute(VirtualFrame frame) {
                TruffleObject obj = (TruffleObject) frame.getArguments()[0];
                try {
                    return ForeignAccess.sendExecute(executeNode, obj, iArg, sArg);
                } catch (InteropException ex) {
                    CompilerDirectives.transferToInterpreter();
                    Assert.fail(ex.getMessage());
                    return null;
                }
            }
        }

        @Test
        public void constArg(@Inject(TestConstArgNode.class) CallTarget target) {
            TruffleObject arg = prepareArgumentValue();
            Object ret = target.call(arg);
            Assert.assertEquals(expectedRetValue(), ret);
        }
    }
    // END: TruffleRunnerSnippets#ParameterizedTest
    // Checkstyle: resume

    // Checkstyle: stop
    // BEGIN: TruffleRunnerSnippets#RunWithPolyglotRule
    @RunWith(TruffleRunner.class)
    public static class PolyglotTest {

        @ClassRule RunWithPolyglotRule runWithPolyglot = new RunWithPolyglotRule();

        private static Object prepared;

        @BeforeClass
        public void prepare() {
            prepared = runWithPolyglot.getTruffleTestEnv().importSymbol("...");
        }

        @Test
        public void executeTest(@Inject(TestExecuteNode.class) CallTarget target) {
            Object ret = target.call(prepared);
            Assert.assertEquals(expectedRetValue(), ret);
        }
    }
    // END: TruffleRunnerSnippets#RunWithPolyglotRule
    // Checkstyle: resume
}
