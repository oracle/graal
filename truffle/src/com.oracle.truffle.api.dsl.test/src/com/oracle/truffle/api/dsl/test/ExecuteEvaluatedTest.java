/*
 * Copyright (c) 2012, 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.api.dsl.test;

import static com.oracle.truffle.api.dsl.test.TestHelper.getSlowPathCount;
import static com.oracle.truffle.api.dsl.test.TestHelper.instrumentSlowPath;
import static org.junit.Assert.assertEquals;

import org.junit.Assert;
import org.junit.Test;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.dsl.ImplicitCast;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeChildren;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystem;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.dsl.test.ExecuteEvaluatedTestFactory.DoubleEvaluatedNodeFactory;
import com.oracle.truffle.api.dsl.test.ExecuteEvaluatedTestFactory.EvaluatedNodeFactory;
import com.oracle.truffle.api.dsl.test.ExecuteEvaluatedTestFactory.TestEvaluatedGenerationFactory;
import com.oracle.truffle.api.dsl.test.ExecuteEvaluatedTestFactory.TestEvaluatedImplicitCast0NodeGen;
import com.oracle.truffle.api.dsl.test.ExecuteEvaluatedTestFactory.TestEvaluatedImplicitCast1NodeGen;
import com.oracle.truffle.api.dsl.test.ExecuteEvaluatedTestFactory.TestEvaluatedImplicitCast2NodeGen;
import com.oracle.truffle.api.dsl.test.ExecuteEvaluatedTestFactory.TestEvaluatedVarArgs0Factory;
import com.oracle.truffle.api.dsl.test.ExecuteEvaluatedTestFactory.TestEvaluatedVarArgs1Factory;
import com.oracle.truffle.api.dsl.test.ExecuteEvaluatedTestFactory.TestEvaluatedVarArgs2Factory;
import com.oracle.truffle.api.dsl.test.ExecuteEvaluatedTestFactory.TestEvaluatedVarArgs3Factory;
import com.oracle.truffle.api.dsl.test.ExecuteEvaluatedTestFactory.TestEvaluatedVarArgs4Factory;
import com.oracle.truffle.api.dsl.test.ExecuteEvaluatedTestFactory.TestEvaluatedVarArgs5Factory;
import com.oracle.truffle.api.dsl.test.ExecuteEvaluatedTestFactory.UseDoubleEvaluated1NodeFactory;
import com.oracle.truffle.api.dsl.test.ExecuteEvaluatedTestFactory.UseDoubleEvaluated2NodeFactory;
import com.oracle.truffle.api.dsl.test.ExecuteEvaluatedTestFactory.UseEvaluatedNodeFactory;
import com.oracle.truffle.api.dsl.test.TypeSystemTest.ArgumentNode;
import com.oracle.truffle.api.dsl.test.TypeSystemTest.ChildrenNode;
import com.oracle.truffle.api.dsl.test.TypeSystemTest.TestRootNode;
import com.oracle.truffle.api.dsl.test.TypeSystemTest.ValueNode;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.UnexpectedResultException;

public class ExecuteEvaluatedTest {

    @Test
    public void testSingleEvaluated() {
        ArgumentNode arg0 = new ArgumentNode(0);
        CallTarget callTarget = TestHelper.createCallTarget(UseEvaluatedNodeFactory.create(arg0, EvaluatedNodeFactory.create(null)));

        assertEquals(43, callTarget.call(new Object[]{42}));
        assertEquals(1, arg0.getInvocationCount());
    }

    @NodeChild("exp")
    abstract static class EvaluatedNode extends ValueNode {

        @Specialization
        int doExecuteWith(int exp) {
            return exp + 1;
        }

        public abstract Object executeEvaluated(VirtualFrame frame, Object targetValue);

        public abstract int executeIntEvaluated(VirtualFrame frame, Object targetValue) throws UnexpectedResultException;
    }

    @NodeChildren({@NodeChild("exp0"), @NodeChild(value = "exp1", type = EvaluatedNode.class, executeWith = "exp0")})
    abstract static class UseEvaluatedNode extends ValueNode {

        @Specialization
        int call(int exp0, int exp1) {
            Assert.assertEquals(exp0 + 1, exp1);
            return exp1;
        }
    }

    @Test
    public void testDoubleEvaluated1() {
        ArgumentNode arg0 = new ArgumentNode(0);
        ArgumentNode arg1 = new ArgumentNode(1);
        CallTarget callTarget = TestHelper.createCallTarget(UseDoubleEvaluated1NodeFactory.create(arg0, arg1, DoubleEvaluatedNodeFactory.create(null, null)));

        assertEquals(42, callTarget.call(new Object[]{43, 1}));
        assertEquals(1, arg0.getInvocationCount());
        assertEquals(1, arg1.getInvocationCount());
    }

    @NodeChildren({@NodeChild("exp0"), @NodeChild("exp1")})
    abstract static class DoubleEvaluatedNode extends ValueNode {

        @Specialization
        int doExecuteWith(int exp0, int exp1) {
            return exp0 - exp1;
        }

        public abstract Object executeEvaluated(VirtualFrame frame, Object exp0, Object exp1);

        public abstract int executeIntEvaluated(VirtualFrame frame, Object exp0, Object exp1) throws UnexpectedResultException;
    }

    @NodeChildren({@NodeChild("exp0"), @NodeChild("exp1"), @NodeChild(value = "exp2", type = DoubleEvaluatedNode.class, executeWith = {"exp0", "exp1"})})
    abstract static class UseDoubleEvaluated1Node extends ValueNode {

        @Specialization
        int call(int exp0, int exp1, int exp2) {
            Assert.assertEquals(exp0 - exp1, exp2);
            return exp2;
        }
    }

    @Test
    public void testDoubleEvaluated2() {
        ArgumentNode arg0 = new ArgumentNode(0);
        ArgumentNode arg1 = new ArgumentNode(1);
        CallTarget callTarget = TestHelper.createCallTarget(UseDoubleEvaluated2NodeFactory.create(arg0, arg1, DoubleEvaluatedNodeFactory.create(null, null)));

        assertEquals(42, callTarget.call(new Object[]{1, 43}));
        assertEquals(1, arg0.getInvocationCount());
        assertEquals(1, arg1.getInvocationCount());
    }

    @NodeChildren({@NodeChild("exp0"), @NodeChild("exp1"), @NodeChild(value = "exp2", type = DoubleEvaluatedNode.class, executeWith = {"exp1", "exp0"})})
    abstract static class UseDoubleEvaluated2Node extends ValueNode {

        @Specialization
        int call(int exp0, int exp1, int exp2) {
            Assert.assertEquals(exp1 - exp0, exp2);
            return exp2;
        }
    }

    @Test
    public void testEvaluatedGeneration() throws UnexpectedResultException {
        TestRootNode<TestEvaluatedGeneration> root = TestHelper.createRoot(TestEvaluatedGenerationFactory.getInstance());

        assertEquals(42, root.getNode().executeEvaluated1(null, 42));
        assertEquals(42, root.getNode().executeEvaluated2(null, 42));
        assertEquals(42, root.getNode().executeEvaluated3(null, 42));
        assertEquals(42, root.getNode().executeEvaluated4(null, 42));
    }

    @NodeChildren({@NodeChild("exp0")})
    abstract static class TestEvaluatedGeneration extends ValueNode {

        public abstract Object executeEvaluated1(VirtualFrame frame, Object value);

        public abstract Object executeEvaluated2(VirtualFrame frame, int value);

        public abstract int executeEvaluated3(VirtualFrame frame, Object value) throws UnexpectedResultException;

        public abstract int executeEvaluated4(VirtualFrame frame, int value) throws UnexpectedResultException;

        @Specialization
        int call(int exp0) {
            return exp0;
        }
    }

    @Test
    public void test0VarArgs1() {
        TestRootNode<TestEvaluatedVarArgs0> root = TestHelper.createRoot(TestEvaluatedVarArgs0Factory.getInstance());
        assertEquals(42, root.getNode().execute1(null));
    }

    abstract static class TestEvaluatedVarArgs0 extends ChildrenNode {

        @Override
        public final Object execute(VirtualFrame frame) {
            return execute1(frame);
        }

        public abstract Object execute1(VirtualFrame frame, Object... value);

        @Specialization
        int call() {
            return 42;
        }
    }

    @Test
    public void test1VarArgs1() {
        TestRootNode<TestEvaluatedVarArgs1> root = TestHelper.createRoot(TestEvaluatedVarArgs1Factory.getInstance());
        assertEquals(42, root.getNode().execute1(null, 42));
    }

    @Test(expected = Throwable.class)
    public void test1VarArgs2() {
        TestRootNode<TestEvaluatedVarArgs2> root = TestHelper.createRoot(TestEvaluatedVarArgs2Factory.getInstance());
        root.getNode().execute1(null);
    }

    abstract static class TestEvaluatedVarArgs1 extends ChildrenNode {

        public abstract Object execute1(VirtualFrame frame, Object... value);

        @Specialization
        int call(int exp0) {
            return exp0;
        }
    }

    @Test
    public void test2VarArgs1() {
        TestRootNode<TestEvaluatedVarArgs2> root = TestHelper.createRoot(TestEvaluatedVarArgs2Factory.getInstance());
        assertEquals(42, root.getNode().execute1(null, 21, 21));
    }

    @Test(expected = Throwable.class)
    public void test2VarArgs2() {
        TestRootNode<TestEvaluatedVarArgs2> root = TestHelper.createRoot(TestEvaluatedVarArgs2Factory.getInstance());
        root.getNode().execute1(null, 42);
    }

    @Test(expected = Throwable.class)
    public void test2VarArgs3() {
        TestRootNode<TestEvaluatedVarArgs2> root = TestHelper.createRoot(TestEvaluatedVarArgs2Factory.getInstance());
        root.getNode().execute1(null);
    }

    abstract static class TestEvaluatedVarArgs2 extends ChildrenNode {

        public abstract Object execute1(VirtualFrame frame, Object... value);

        @Specialization
        int call(int exp0, int exp1) {
            return exp0 + exp1;
        }
    }

    @Test
    public void test3VarArgs1() {
        TestRootNode<TestEvaluatedVarArgs3> root = TestHelper.createRoot(TestEvaluatedVarArgs3Factory.getInstance());
        assertEquals(42, root.getNode().execute1(null, 42));
    }

    @NodeChild
    abstract static class TestEvaluatedVarArgs3 extends ValueNode {

        public abstract Object execute1(VirtualFrame frame, Object... value);

        @Specialization
        int call(int exp0) {
            return exp0;
        }
    }

    @Test
    public void test4VarArgs1() {
        TestRootNode<TestEvaluatedVarArgs4> root = TestHelper.createRoot(TestEvaluatedVarArgs4Factory.getInstance());
        assertEquals(42, root.getNode().execute1(null, 21, 21));
    }

    @NodeChildren({@NodeChild, @NodeChild})
    abstract static class TestEvaluatedVarArgs4 extends ValueNode {

        public abstract Object execute1(VirtualFrame frame, Object... value);

        @Specialization
        int call(int exp0, int exp1) {
            return exp0 + exp1;
        }
    }

    @Test
    public void test5VarArgs1() {
        TestRootNode<TestEvaluatedVarArgs5> root = TestHelper.createRoot(TestEvaluatedVarArgs5Factory.getInstance());
        assertEquals(42, root.getNode().execute1(null));
    }

    abstract static class TestEvaluatedVarArgs5 extends ValueNode {

        @Override
        public final Object execute(VirtualFrame frame) {
            return execute1(frame);
        }

        public abstract Object execute1(VirtualFrame frame, Object... value);

        @Specialization
        int call() {
            return 42;
        }
    }

    /*
     * Failed test where execute parameter Object[] was cased using (Object) which led to a compile
     * error.
     */
    abstract static class TestExecuteWithObjectArg extends Node {

        public abstract Object execute(VirtualFrame frame, Object[] args);

        @Specialization
        public Object test(@SuppressWarnings("unused") final Object[] args) {
            return null;
        }
    }

    @Test
    public void testEvaluatedImplicitCast0() {
        TestEvaluatedImplicitCast0Node node = TestEvaluatedImplicitCast0NodeGen.create();
        instrumentSlowPath(node);

        assertEquals(0, getSlowPathCount(node));
        node.execute("a", 0);
        assertEquals(1, getSlowPathCount(node));
        node.execute("b", 0);
        assertEquals(1, getSlowPathCount(node));
        node.execute(42, 0);
        assertEquals(2, getSlowPathCount(node));
        node.execute(43, 0);
        assertEquals(2, getSlowPathCount(node));
    }

    @TypeSystem
    public static class TestEvaluatedImplicitCast0TypeSystem {

        @ImplicitCast
        public static int toInt(short s) {
            return s;
        }

    }

    @TypeSystemReference(TestEvaluatedImplicitCast0TypeSystem.class)
    @SuppressWarnings("unused")
    abstract static class TestEvaluatedImplicitCast0Node extends Node {

        public abstract Object execute(Object receiver, int intValue);

        @Specialization
        public int doInt(String receiver, int intValue) {
            return intValue;
        }

        @Specialization
        public double doInt(Number receiver, int intValue) {
            return intValue;
        }

        /*
         * Avoid locking optimization to trigger.
         */
        @Specialization(replaces = "doInt")
        public double doInt2(Void receiver, int intValue) {
            return 42;
        }

    }

    @Test
    public void testEvaluatedImplicitCast1() {
        TestEvaluatedImplicitCast1Node node = TestEvaluatedImplicitCast1NodeGen.create();
        instrumentSlowPath(node);

        assertEquals(0, getSlowPathCount(node));
        node.execute("a", (short) 0);
        node.execute("a", (short) 0);
        assertEquals(1, getSlowPathCount(node));
        node.execute("b", 0);
        node.execute("b", 0);
        assertEquals(2, getSlowPathCount(node));
        node.execute(42, (short) 0);
        node.execute(42, (short) 0);
        assertEquals(3, getSlowPathCount(node));
        node.execute(43, 0);
        node.execute(43, 0);
        assertEquals(3, getSlowPathCount(node));
    }

    @TypeSystem
    public static class TestEvaluatedImplicitCast1TypeSystem {

        @ImplicitCast
        public static int toInt(short s) {
            return s;
        }

    }

    @TypeSystemReference(TestEvaluatedImplicitCast1TypeSystem.class)
    @SuppressWarnings("unused")
    abstract static class TestEvaluatedImplicitCast1Node extends Node {

        public abstract Object execute(Object receiver, Object intValue);

        @Specialization
        public int doInt(String receiver, int intValue) {
            return intValue;
        }

        @Specialization
        public double doInt(Number receiver, int intValue) {
            return intValue;
        }

        /*
         * Avoid locking optimization to trigger.
         */
        @Specialization(replaces = "doInt")
        public double doInt2(Void receiver, int intValue) {
            return 42;
        }

    }

    @Test
    public void testEvaluatedImplicitCast2() {
        TestEvaluatedImplicitCast2Node node = TestEvaluatedImplicitCast2NodeGen.create();
        instrumentSlowPath(node);

        assertEquals(0, getSlowPathCount(node));
        node.execute("a", (short) 0);
        node.execute("a", (short) 0);
        assertEquals(1, getSlowPathCount(node));
        node.execute("b", 0);
        node.execute("b", 0);
        assertEquals(2, getSlowPathCount(node));
        node.execute(42, (short) 0);
        node.execute(42, (short) 0);
        assertEquals(3, getSlowPathCount(node));
        node.execute("c", 0);
        node.execute("c", 0);
        assertEquals(3, getSlowPathCount(node));
    }

    @TypeSystem
    public static class TestEvaluatedImplicitCast2TypeSystem {

        @ImplicitCast
        public static int toInt(short s) {
            return s;
        }

    }

    @TypeSystemReference(TestEvaluatedImplicitCast2TypeSystem.class)
    @SuppressWarnings("unused")
    abstract static class TestEvaluatedImplicitCast2Node extends Node {

        public abstract Object execute(Object receiver, short intValue);

        public abstract Object execute(Object receiver, int intValue);

        @Specialization
        public String doInt(String receiver, int intValue) {
            return "int";
        }

        @Specialization
        public String doShort(Number receiver, short intValue) {
            return "short";
        }

        /*
         * Avoid locking optimization to trigger.
         */
        @Specialization(replaces = "doInt")
        public double doInt2(Void receiver, int intValue) {
            return 42;
        }
    }

}
