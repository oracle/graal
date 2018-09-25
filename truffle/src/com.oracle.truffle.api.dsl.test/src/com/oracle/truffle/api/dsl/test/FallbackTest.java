/*
 * Copyright (c) 2012, 2018, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.truffle.api.dsl.test.TestHelper.array;
import static com.oracle.truffle.api.dsl.test.TestHelper.assertRuns;
import static com.oracle.truffle.api.dsl.test.TestHelper.createRoot;
import static com.oracle.truffle.api.dsl.test.TestHelper.executeWith;

import org.junit.Assert;
import org.junit.Test;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.ImplicitCast;
import com.oracle.truffle.api.dsl.Introspectable;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystem;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.dsl.UnsupportedSpecializationException;
import com.oracle.truffle.api.dsl.test.FallbackTestFactory.Fallback1Factory;
import com.oracle.truffle.api.dsl.test.FallbackTestFactory.Fallback2Factory;
import com.oracle.truffle.api.dsl.test.FallbackTestFactory.Fallback3Factory;
import com.oracle.truffle.api.dsl.test.FallbackTestFactory.Fallback4Factory;
import com.oracle.truffle.api.dsl.test.FallbackTestFactory.Fallback6Factory;
import com.oracle.truffle.api.dsl.test.FallbackTestFactory.Fallback7Factory;
import com.oracle.truffle.api.dsl.test.FallbackTestFactory.Fallback8NodeGen;
import com.oracle.truffle.api.dsl.test.FallbackTestFactory.Fallback9NodeGen;
import com.oracle.truffle.api.dsl.test.FallbackTestFactory.FallbackWithAssumptionArrayNodeGen;
import com.oracle.truffle.api.dsl.test.FallbackTestFactory.FallbackWithAssumptionNodeGen;
import com.oracle.truffle.api.dsl.test.FallbackTestFactory.ImplicitCastInFallbackNodeGen;
import com.oracle.truffle.api.dsl.test.TypeSystemTest.TestRootNode;
import com.oracle.truffle.api.dsl.test.TypeSystemTest.ValueNode;
import com.oracle.truffle.api.dsl.test.examples.ExampleTypes;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeUtil;

public class FallbackTest {

    private static final Object UNKNOWN_OBJECT = new Object() {
    };

    @Test
    public void testFallback1() {
        assertRuns(Fallback1Factory.getInstance(), //
                        array(42, UNKNOWN_OBJECT), //
                        array("(int)", "(fallback)"));
    }

    /**
     * Test with fallback handler defined.
     */
    @SuppressWarnings("unused")
    @NodeChild("a")
    abstract static class Fallback1 extends ValueNode {

        @Override
        public abstract String executeString(VirtualFrame frame);

        @Specialization
        String f1(int a) {
            return "(int)";
        }

        @Fallback
        String f2(Object a) {
            return "(fallback)";
        }
    }

    @Test
    public void testFallback2() {
        assertRuns(Fallback2Factory.getInstance(), //
                        array(42, UNKNOWN_OBJECT), //
                        array("(int)", UnsupportedSpecializationException.class));
    }

    /**
     * Test without fallback handler defined.
     */
    @SuppressWarnings("unused")
    @NodeChild("a")
    abstract static class Fallback2 extends ValueNode {

        @Specialization
        String f1(int a) {
            return "(int)";
        }

    }

    @Test
    public void testFallback3() {
        assertRuns(Fallback3Factory.getInstance(), //
                        array(42, 43, UNKNOWN_OBJECT, "somestring"), //
                        array("(int)", "(int)", "(object)", "(object)"));
    }

    /**
     * Test without fallback handler and unreachable.
     */
    @SuppressWarnings("unused")
    @NodeChild("a")
    abstract static class Fallback3 extends ValueNode {

        @Specialization
        String f1(int a) {
            return "(int)";
        }

        @Specialization(guards = "notInt(a)")
        String f2(Object a) {
            return "(object)";
        }

        boolean notInt(Object value) {
            return !(value instanceof Integer);
        }

    }

    /**
     * Tests the contents of the {@link UnsupportedSpecializationException} contents in polymorphic
     * nodes.
     */
    @Test
    public void testFallback4() {
        TestRootNode<Fallback4> node = createRoot(Fallback4Factory.getInstance());

        Assert.assertEquals("(int)", executeWith(node, 1));
        Assert.assertEquals("(boolean)", executeWith(node, true));
        try {
            executeWith(node, UNKNOWN_OBJECT);
            Assert.fail();
        } catch (UnsupportedSpecializationException e) {
            Assert.assertEquals(node.getNode(), e.getNode());
            Assert.assertArrayEquals(NodeUtil.findNodeChildren(node.getNode()).subList(0, 1).toArray(new Node[0]), e.getSuppliedNodes());
            Assert.assertArrayEquals(new Object[]{UNKNOWN_OBJECT}, e.getSuppliedValues());
        }
    }

    /**
     * Test without fallback handler and unreachable.
     */
    @SuppressWarnings("unused")
    @NodeChild("a")
    abstract static class Fallback4 extends ValueNode {

        @Specialization
        String f1(int a) {
            return "(int)";
        }

        @Specialization
        String f2(boolean a) {
            return "(boolean)";
        }
    }

    /**
     * Tests the contents of the {@link UnsupportedSpecializationException} contents in monomorphic
     * nodes.
     */
    @Test
    public void testFallback5() {
        TestRootNode<Fallback4> node = createRoot(Fallback4Factory.getInstance());

        Assert.assertEquals("(int)", executeWith(node, 1));
        try {
            executeWith(node, UNKNOWN_OBJECT);
            Assert.fail();
        } catch (UnsupportedSpecializationException e) {
            Assert.assertEquals(node.getNode(), e.getNode());
            Assert.assertArrayEquals(NodeUtil.findNodeChildren(node.getNode()).subList(0, 1).toArray(new Node[0]), e.getSuppliedNodes());
            Assert.assertArrayEquals(new Object[]{UNKNOWN_OBJECT}, e.getSuppliedValues());
        }
    }

    // test without fallback handler and unreachable
    @SuppressWarnings("unused")
    @NodeChild("a")
    abstract static class Fallback5 extends ValueNode {

        @Specialization
        String f1(int a) {
            return "(int)";
        }
    }

    @Test
    public void testFallback6() {
        TestRootNode<Fallback6> node = createRoot(Fallback6Factory.getInstance());
        Assert.assertEquals(2, executeWith(node, 1));
        try {
            Assert.assertEquals(2, executeWith(node, "foobar"));
            Assert.fail();
        } catch (FallbackException e) {
        }

        Assert.assertEquals((long) Integer.MAX_VALUE + (long) Integer.MAX_VALUE, executeWith(node, Integer.MAX_VALUE));
        try {
            executeWith(node, "foobar");
            Assert.fail();
        } catch (FallbackException e) {
        }
    }

    @SuppressWarnings("serial")
    private static class FallbackException extends RuntimeException {
    }

    @NodeChild("a")
    abstract static class Fallback6 extends ValueNode {

        @Specialization(rewriteOn = ArithmeticException.class)
        int f1(int a) throws ArithmeticException {
            return Math.addExact(a, a);
        }

        @Specialization
        long f2(int a) {
            return (long) a + (long) a;
        }

        @Specialization
        boolean f3(boolean a) {
            return a;
        }

        @Fallback
        Object f2(@SuppressWarnings("unused") Object a) {
            throw new FallbackException();
        }
    }

    @Test
    public void testFallback7() {
        TestRootNode<Fallback7> node = createRoot(Fallback7Factory.getInstance());
        Assert.assertEquals(2, executeWith(node, 1));
        Assert.assertEquals(2, executeWith(node, "asdf"));
        Assert.assertEquals(2, executeWith(node, "asdf"));
    }

    @NodeChild("a")
    @SuppressWarnings("unused")
    abstract static class Fallback7 extends ValueNode {

        public abstract Object execute(VirtualFrame frame, Object arg);

        protected boolean guard(int value) {
            return true;
        }

        @Specialization(guards = {"guard(arg)"})
        protected static int access(int arg) {
            return 2;
        }

        @Fallback
        protected static Object access(Object arg) {
            return 2;
        }

    }

    @Test
    public void testFallback8() {
        Fallback8 node = Fallback8NodeGen.create();
        node.execute(1L);
        Assert.assertEquals(0, node.s0count);
        Assert.assertEquals(0, node.s1count);
        Assert.assertEquals(1, node.guard0count);
        Assert.assertEquals(1, node.guard1count);
        Assert.assertEquals(1, node.fcount);
        node.execute(1L);
        Assert.assertEquals(0, node.s0count);
        Assert.assertEquals(0, node.s1count);
        Assert.assertEquals(2, node.guard0count);
        Assert.assertEquals(2, node.guard1count);
        Assert.assertEquals(2, node.fcount);

        node = Fallback8NodeGen.create();
        node.execute(1L);
        Assert.assertEquals(0, node.s0count);
        Assert.assertEquals(0, node.s1count);
        Assert.assertEquals(1, node.guard0count);
        Assert.assertEquals(1, node.guard1count);
        Assert.assertEquals(1, node.fcount);
        node.execute(1);
        Assert.assertEquals(1, node.s0count);
        Assert.assertEquals(0, node.s1count);
        Assert.assertEquals(3, node.guard0count);
        Assert.assertEquals(1, node.guard1count);
        Assert.assertEquals(1, node.fcount);
        node.execute(1L);
        Assert.assertEquals(1, node.s0count);
        Assert.assertEquals(0, node.s1count);
        Assert.assertEquals(4, node.guard0count);
        Assert.assertEquals(2, node.guard1count);
        Assert.assertEquals(2, node.fcount);
    }

    @TypeSystemReference(ExampleTypes.class)
    abstract static class Fallback8 extends Node {

        private int s0count;
        private int s1count;
        private int guard0count;
        private int guard1count;
        private int fcount;

        public abstract Object execute(Object arg);

        @Specialization(guards = "guard0(arg)")
        protected Object s0(Object arg) {
            s0count++;
            return arg;
        }

        @Specialization(guards = "guard1(arg)")
        protected Object s1(Object arg) {
            s1count++;
            return arg;
        }

        protected boolean guard0(Object arg) {
            guard0count++;
            return arg instanceof Integer;
        }

        protected boolean guard1(Object arg) {
            guard1count++;
            return arg instanceof String;
        }

        @Fallback
        protected Object f(Object arg) {
            fcount++;
            return arg;
        }

    }

    @Test
    public void testFallback9() {
        Fallback9 node = Fallback9NodeGen.create();
        Assert.assertEquals(0, node.s0count);
        Assert.assertEquals(0, node.fcount);
        node.execute(1);
        Assert.assertEquals(1, node.s0count);
        Assert.assertEquals(0, node.fcount);
        node.execute("");
        Assert.assertEquals(1, node.s0count);
        Assert.assertEquals(1, node.fcount);

        /*
         * The fallback is now active and the new implicit casted type (double) must use the
         * specialization instead of the fall back case even if double is not an active implicit
         * cast type.
         */
        node.execute(1d);
        Assert.assertEquals(2, node.s0count);
        Assert.assertEquals(1, node.fcount);
    }

    @TypeSystemReference(ExampleTypes.class)
    abstract static class Fallback9 extends Node {

        public abstract Object execute(Object arg);

        int s0count = 0;
        int fcount = 0;

        @Specialization
        protected Object s0(double arg) {
            s0count++;
            return arg;
        }

        @Fallback
        protected Object f(Object arg) {
            fcount++;
            return arg;
        }

    }

    /*
     * Tests that fallback behavior with cached guards that do not have a generic case.
     */
    @TypeSystemReference(ExampleTypes.class)
    @Introspectable
    @SuppressWarnings("unused")
    public abstract static class Fallback10 extends Node {

        public abstract String execute(Object left, Object right);

        @Specialization(limit = "2", guards = {"left == cachedLeft", "right == cachedRight"})
        protected String s1(
                        int left,
                        int right,
                        @Cached("left") int cachedLeft,
                        @Cached("right") int cachedRight) {
            return "s0";
        }

        @Fallback
        @ExpectError(CachedReachableFallbackTest.CACHED_GUARD_FALLBACK_ERROR)
        @TruffleBoundary
        protected String f0(Object left, Object right) {
            return "f0";
        }

    }

    @Test
    public void testFallbackWithAssumption() {
        FallbackWithAssumption node1 = FallbackWithAssumptionNodeGen.create();
        Assert.assertEquals("s0", node1.execute(0));
        node1.assumption.invalidate();
        Assert.assertEquals("f0", node1.execute(0));

        FallbackWithAssumption node2 = FallbackWithAssumptionNodeGen.create();
        node2.assumption.invalidate();
        Assert.assertEquals("f0", node2.execute(0)); // executeAndSpecialize
        Assert.assertEquals("f0", node2.execute(0)); // execute the fallbackGuard

        FallbackWithAssumption node3 = FallbackWithAssumptionNodeGen.create();
        Assert.assertEquals("f0", node3.execute(3.14));
        Assert.assertEquals("f0", node3.execute(3.14));
        Assert.assertEquals("s0", node3.execute(0));

        FallbackWithAssumption node4 = FallbackWithAssumptionNodeGen.create();
        Assert.assertEquals("f0", node4.execute(3.14));
        Assert.assertEquals("f0", node4.execute(3.14));
        node4.assumption.invalidate();
        Assert.assertEquals("f0", node4.execute(0));
        Assert.assertEquals("f0", node4.execute(0));
    }

    @TypeSystemReference(ExampleTypes.class)
    @Introspectable
    @SuppressWarnings("unused")
    public abstract static class FallbackWithAssumption extends Node {

        protected Assumption assumption = Truffle.getRuntime().createAssumption();

        public abstract String execute(Object n);

        @Specialization(assumptions = "getAssumption()")
        protected String s0(int n) {
            return "s0";
        }

        @Fallback
        protected String f0(Object n) {
            return "f0";
        }

        protected Assumption getAssumption() {
            return assumption;
        }

    }

    @Test
    public void testFallbackWithAssumptionArray() {
        FallbackWithAssumptionArray node1 = FallbackWithAssumptionArrayNodeGen.create();
        Assert.assertEquals("s0", node1.execute(0));
        node1.assumptions[0].invalidate();
        Assert.assertEquals("f0", node1.execute(0));

        FallbackWithAssumptionArray node2 = FallbackWithAssumptionArrayNodeGen.create();
        node2.assumptions[0].invalidate();
        Assert.assertEquals("f0", node2.execute(0)); // executeAndSpecialize
        Assert.assertEquals("f0", node2.execute(0)); // execute the fallbackGuard

        FallbackWithAssumptionArray node3 = FallbackWithAssumptionArrayNodeGen.create();
        Assert.assertEquals("f0", node3.execute(3.14));
        Assert.assertEquals("f0", node3.execute(3.14));

        FallbackWithAssumptionArray node4 = FallbackWithAssumptionArrayNodeGen.create();
        Assert.assertEquals("f0", node4.execute(3.14));
        Assert.assertEquals("f0", node4.execute(3.14));
        node4.assumptions[0].invalidate();
        Assert.assertEquals("f0", node4.execute(0));
        Assert.assertEquals("f0", node4.execute(0));
    }

    @TypeSystemReference(ExampleTypes.class)
    @Introspectable
    @SuppressWarnings("unused")
    public abstract static class FallbackWithAssumptionArray extends Node {

        protected Assumption[] assumptions = new Assumption[]{Truffle.getRuntime().createAssumption()};

        public abstract String execute(Object n);

        @Specialization(assumptions = "getAssumptions()")
        protected String s0(int n) {
            return "s0";
        }

        @Fallback
        protected String f0(Object n) {
            return "f0";
        }

        protected Assumption[] getAssumptions() {
            return assumptions;
        }

    }

    @TypeSystemReference(ExampleTypes.class)
    @SuppressWarnings("unused")
    public abstract static class FallbackFrame extends Node {

        public abstract String execute(VirtualFrame frame, Object n);

        @Specialization(guards = "n == cachedN", limit = "3")
        protected String s0(VirtualFrame frame, int n, @Cached("n") int cachedN) {
            return "s0";
        }

        @Specialization
        protected String s1(VirtualFrame frame, int n) {
            return "s1";
        }

        @Fallback
        protected String f0(Object n) {
            return "f0";
        }

    }

    @TypeSystem
    public static class ImplicitCastInFallbackTypeSystem {

        @ImplicitCast
        public static ImplicitValue implicitCast(int value) {
            return new ImplicitValue(value);
        }

    }

    @Test
    public void testImplicitCastInFallback() {
        ImplicitCastInFallbackNode node;

        // test s0 first
        node = ImplicitCastInFallbackNodeGen.create();
        Assert.assertEquals("s0", node.execute(42));
        Assert.assertEquals("s0", node.execute(new ImplicitValue(42)));
        Assert.assertEquals("fallback", node.execute("42"));

        // test fallback first
        node = ImplicitCastInFallbackNodeGen.create();
        Assert.assertEquals("fallback", node.execute("42"));
        Assert.assertEquals("s0", node.execute(42));
        Assert.assertEquals("s0", node.execute(new ImplicitValue(42)));

    }

    static class ImplicitValue {

        public final int value;

        ImplicitValue(int value) {
            this.value = value;
        }

    }

    @TypeSystemReference(ImplicitCastInFallbackTypeSystem.class)
    @SuppressWarnings("unused")
    public abstract static class ImplicitCastInFallbackNode extends Node {

        public abstract String execute(Object n);

        // if the implicitly casted value is used
        @Specialization(guards = "type.value == 42")
        protected String s0(ImplicitValue type) {
            return "s0";
        }

        @Fallback
        protected String f0(Object n) {
            return "fallback";
        }

    }

}
