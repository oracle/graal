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

import static com.oracle.truffle.api.dsl.test.examples.ExampleNode.createArguments;

import java.lang.reflect.Field;
import java.util.concurrent.locks.ReentrantLock;

import org.junit.Assert;
import org.junit.Test;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.ImplicitCast;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeChildren;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystem;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.dsl.UnsupportedSpecializationException;
import com.oracle.truffle.api.dsl.test.ImplicitCastTestFactory.ExecuteChildWithImplicitCast1NodeGen;
import com.oracle.truffle.api.dsl.test.ImplicitCastTestFactory.ImplicitCast0NodeFactory;
import com.oracle.truffle.api.dsl.test.ImplicitCastTestFactory.ImplicitCast1NodeFactory;
import com.oracle.truffle.api.dsl.test.ImplicitCastTestFactory.ImplicitCast2NodeFactory;
import com.oracle.truffle.api.dsl.test.ImplicitCastTestFactory.ImplicitCast3NodeGen;
import com.oracle.truffle.api.dsl.test.ImplicitCastTestFactory.ImplicitCast4NodeFactory;
import com.oracle.truffle.api.dsl.test.ImplicitCastTestFactory.ImplicitCast5NodeFactory;
import com.oracle.truffle.api.dsl.test.ImplicitCastTestFactory.ImplicitCastExecuteNodeGen;
import com.oracle.truffle.api.dsl.test.ImplicitCastTestFactory.StringEquals1NodeGen;
import com.oracle.truffle.api.dsl.test.ImplicitCastTestFactory.StringEquals2NodeGen;
import com.oracle.truffle.api.dsl.test.ImplicitCastTestFactory.StringEquals3NodeGen;
import com.oracle.truffle.api.dsl.test.ImplicitCastTestFactory.TestImplicitCastWithCacheNodeGen;
import com.oracle.truffle.api.dsl.test.ImplicitCastTestFactory.ThirtyThreeBitsNodeGen;
import com.oracle.truffle.api.dsl.test.ImplicitCastTestFactory.ThirtyTwoBitsNodeGen;
import com.oracle.truffle.api.dsl.test.TypeSystemTest.TestRootNode;
import com.oracle.truffle.api.dsl.test.TypeSystemTest.ValueNode;
import com.oracle.truffle.api.dsl.test.examples.ExampleNode;
import com.oracle.truffle.api.dsl.test.examples.ExampleNode.ExampleArgumentNode;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.UnexpectedResultException;

public class ImplicitCastTest {

    @TypeSystem({int.class, String.class, boolean.class})
    static class ImplicitCast0Types {

        @ImplicitCast
        static boolean castInt(int intvalue) {
            return intvalue == 1 ? true : false;
        }

        @ImplicitCast
        static boolean castString(String strvalue) {
            return strvalue.equals("1");
        }

    }

    private static int charSequenceCast;

    @TypeSystem
    static class ImplicitCast1Types {

        @ImplicitCast
        static CharSequence castCharSequence(String strvalue) {
            charSequenceCast++;
            return strvalue;
        }

    }

    @TypeSystemReference(ImplicitCast0Types.class)
    @NodeChild(value = "operand", type = ImplicitCast0Node.class)
    abstract static class ImplicitCast0Node extends ValueNode {

        public abstract Object executeEvaluated(VirtualFrame frame, Object value2);

        @Specialization
        public String op1(String value) {
            return value;
        }

        @Specialization
        public boolean op1(boolean value) {
            return value;
        }

    }

    @TypeSystemReference(ImplicitCast0Types.class)
    abstract static class ImplicitCastTypedExecuteNode extends Node {

        public abstract Object execute(int value);

        // TODO: this should not be an error
        @ExpectError("Method signature (boolean) does not match to the expected signature: %")
        @Specialization
        public boolean op1(boolean value) {
            return value;
        }
    }

    @Test
    public void testImplicitCast0() {
        ImplicitCast0Node node = ImplicitCast0NodeFactory.create(null);
        TestRootNode<ImplicitCast0Node> root = new TestRootNode<>(node);
        root.adoptChildren();
        Assert.assertEquals("2", root.getNode().executeEvaluated(null, "2"));
        Assert.assertEquals(true, root.getNode().executeEvaluated(null, 1));
        Assert.assertEquals("1", root.getNode().executeEvaluated(null, "1"));
        Assert.assertEquals(true, root.getNode().executeEvaluated(null, 1));
        Assert.assertEquals(true, root.getNode().executeEvaluated(null, true));
    }

    @TypeSystemReference(ImplicitCast0Types.class)
    @NodeChild(value = "operand", type = ImplicitCast1Node.class)
    abstract static class ImplicitCast1Node extends ValueNode {

        public abstract Object executeEvaluated(VirtualFrame frame, Object operand);

        @Specialization
        public String op0(String value) {
            return value;
        }

        @Specialization(rewriteOn = RuntimeException.class)
        public boolean op1(@SuppressWarnings("unused") boolean value) throws RuntimeException {
            throw new RuntimeException();
        }

        @Specialization(replaces = "op1")
        public boolean op2(boolean value) {
            return value;
        }

    }

    @Test
    public void testImplicitCast1() {
        ImplicitCast1Node node = ImplicitCast1NodeFactory.create(null);
        TestRootNode<ImplicitCast1Node> root = new TestRootNode<>(node);
        root.adoptChildren();
        Assert.assertEquals("2", root.getNode().executeEvaluated(null, "2"));
        Assert.assertEquals(true, root.getNode().executeEvaluated(null, 1));
        Assert.assertEquals("1", root.getNode().executeEvaluated(null, "1"));
        Assert.assertEquals(true, root.getNode().executeEvaluated(null, 1));
        Assert.assertEquals(true, root.getNode().executeEvaluated(null, true));
    }

    @TypeSystemReference(ImplicitCast0Types.class)
    @NodeChildren({@NodeChild(value = "operand0", type = ImplicitCast2Node.class), @NodeChild(value = "operand1", type = ImplicitCast2Node.class, executeWith = "operand0")})
    // TODO temporary workaround
    abstract static class ImplicitCast2Node extends ValueNode {

        @Specialization
        public String op0(String v0, String v1) {
            return v0 + v1;
        }

        @SuppressWarnings("unused")
        @Specialization(rewriteOn = RuntimeException.class)
        public boolean op1(boolean v0, boolean v1) throws RuntimeException {
            throw new RuntimeException();
        }

        @Specialization(replaces = "op1")
        public boolean op2(boolean v0, boolean v1) {
            return v0 && v1;
        }

        public abstract Object executeEvaluated(VirtualFrame frame, Object v1);

        public abstract Object executeEvaluated(VirtualFrame frame, Object v1, Object v2);

        public abstract Object executeEvaluated(VirtualFrame frame, boolean v1, boolean v2);

    }

    @Test
    public void testImplicitCast2() {
        ImplicitCast2Node node = ImplicitCast2NodeFactory.create(null, null);
        TestRootNode<ImplicitCast2Node> root = new TestRootNode<>(node);
        root.adoptChildren();
        Assert.assertEquals("42", root.getNode().executeEvaluated(null, "4", "2"));
        Assert.assertEquals(true, root.getNode().executeEvaluated(null, 1, 1));
        Assert.assertEquals("42", root.getNode().executeEvaluated(null, "4", "2"));
        Assert.assertEquals(true, root.getNode().executeEvaluated(null, 1, 1));
        Assert.assertEquals(true, root.getNode().executeEvaluated(null, true, true));
    }

    @TypeSystemReference(ImplicitCast1Types.class)
    abstract static class ImplicitCast3Node extends Node {

        @Specialization
        public CharSequence op0(CharSequence v0, @SuppressWarnings("unused") CharSequence v1) {
            return v0;
        }

        public abstract Object executeEvaluated(CharSequence v1, CharSequence v2);

    }

    @Test
    public void testImplicitCast3() {
        ImplicitCast3Node node = ImplicitCast3NodeGen.create();
        CharSequence seq1 = "foo";
        CharSequence seq2 = "bar";
        charSequenceCast = 0;
        node.executeEvaluated(seq1, seq2);
        Assert.assertEquals(0, charSequenceCast);
    }

    @TypeSystem
    static class ImplicitCast3Types {

        @ImplicitCast
        static long castLong(int intValue) {
            return intValue;
        }

        @ImplicitCast
        static long castLong(boolean intValue) {
            return intValue ? 1 : 0;
        }

    }

    @NodeChild
    @TypeSystemReference(ImplicitCast3Types.class)
    abstract static class ImplicitCast4Node extends ValueNode {

        @Specialization(guards = "value != 1")
        public int doInt(int value) {
            return value;
        }

        @Specialization(guards = "value != 42")
        public long doLong(long value) {
            return -value;
        }

        protected abstract Object executeEvaluated(Object operand);

    }

    @NodeChildren({@NodeChild, @NodeChild})
    @TypeSystemReference(ImplicitCast3Types.class)
    @SuppressWarnings("unused")
    abstract static class ImplicitCast5Node extends ValueNode {

        @Specialization(guards = "value != 1")
        public int doInt(int a, int value) {
            return value;
        }

        @Specialization(guards = "value != 42")
        public long doLong(long a, long value) {
            return -value;
        }

    }

    static class Test4Input extends ValueNode {

        int n = 0;

        @Override
        public Object execute(VirtualFrame frame) {
            n++;
            if (n == 1) {
                return 1;
            } else if (n == 2) {
                return 42;
            } else {
                throw new AssertionError();
            }
        }

    }

    @Test
    public void testUseUncastedValuesForSlowPath1() {
        ImplicitCast4Node node = ImplicitCast4NodeFactory.create(new Test4Input());
        Assert.assertEquals(-1L, node.execute(null));
        Assert.assertEquals(42, node.execute(null));
    }

    @Test
    public void testUseUncastedValuesForSlowPath2() {
        ImplicitCast5Node node = ImplicitCast5NodeFactory.create(new Test4Input(), new Test4Input());
        Assert.assertEquals(-1L, node.execute(null));
        Assert.assertEquals(42, node.execute(null));
    }

    @TypeSystem({String.class, boolean.class})
    static class ImplicitCastError1 {

        @ImplicitCast
        @ExpectError("Target type and source type of an @ImplicitCast must not be the same type.")
        static String castInvalid(@SuppressWarnings("unused") String value) {
            throw new AssertionError();
        }

    }

    @TypeSystem({String.class, boolean.class})
    static class ImplicitCastError2 {

        @ImplicitCast
        @ExpectError("Target type and source type of an @ImplicitCast must not be the same type.")
        static String castInvalid(@SuppressWarnings("unused") String value) {
            throw new AssertionError();
        }

    }

    @TypeSystem
    static class ImplicitCast2Types {
        @ImplicitCast
        static String castString(CharSequence str) {
            return str.toString();
        }
    }

    @Test
    public void testStringEquals1() {
        StringEquals1Node node = StringEquals1NodeGen.create();
        Assert.assertTrue(node.executeBoolean("foo", "foo"));
        Assert.assertFalse(node.executeBoolean("foo", "bar"));
    }

    @TypeSystemReference(ImplicitCast2Types.class)
    abstract static class StringEquals1Node extends Node {
        protected abstract boolean executeBoolean(String arg1, String arg2);

        @SuppressWarnings("unused")
        @Specialization(guards = {"cachedArg1.equals(arg1)", "cachedArg2.equals(arg2)"}, limit = "1")
        protected static boolean doCached(String arg1, String arg2,
                        @Cached("arg1") String cachedArg1,
                        @Cached("arg2") String cachedArg2,
                        @Cached("arg1.equals(arg2)") boolean result) {
            return result;
        }

        @Specialization
        protected static boolean doUncached(String arg1, String arg2) {
            return arg1.equals(arg2);
        }
    }

    @Test
    public void testStringEquals2() {
        StringEquals2Node node = StringEquals2NodeGen.create();
        Assert.assertTrue(node.executeBoolean("foo", "foo"));
        Assert.assertFalse(node.executeBoolean("foo", "bar"));
    }

    @TypeSystemReference(ImplicitCast2Types.class)
    abstract static class StringEquals2Node extends Node {
        protected abstract boolean executeBoolean(CharSequence arg1, CharSequence arg2);

        @SuppressWarnings("unused")
        @Specialization(guards = {"cachedArg1.equals(arg1)", "cachedArg2.equals(arg2)"}, limit = "2")
        protected static boolean doCached(String arg1, String arg2,
                        @Cached("arg1") String cachedArg1,
                        @Cached("arg2") String cachedArg2,
                        @Cached("arg1.equals(arg2)") boolean result) {
            return result;
        }

        @Specialization
        protected static boolean doUncached(String arg1, String arg2) {
            return arg1.equals(arg2);
        }
    }

    @Test
    public void testStringEquals3() {
        StringEquals3Node node = StringEquals3NodeGen.create();
        Assert.assertTrue(node.executeBoolean("foo"));
        try {
            Assert.assertTrue(node.executeBoolean("bar"));
            Assert.fail();
        } catch (UnsupportedSpecializationException e) {
        }
    }

    @TypeSystemReference(ImplicitCast2Types.class)
    abstract static class StringEquals3Node extends Node {
        protected abstract boolean executeBoolean(CharSequence arg1);

        @SuppressWarnings("unused")
        @Specialization(guards = {"cachedArg1.equals(arg1)"}, limit = "1")
        protected static boolean doCached(String arg1,
                        @Cached("arg1") String cachedArg1,
                        @Cached("arg1.equals(arg1)") boolean result) {
            return result;
        }

    }

    @TypeSystem
    static class ImplicitCast4Types {
        @ImplicitCast
        static long castLong(int value) {
            return value;
        }
    }

    @Test
    public void testExecuteChildWithImplicitCast1() throws UnexpectedResultException {
        ExecuteChildWithImplicitCast1Node node = ExecuteChildWithImplicitCast1NodeGen.create(createArguments(1));
        /*
         * if executeLong is used for the initial execution of the node and the uninitialized case
         * is not checked then this executeLong method might return 0L instead of 2L. This test
         * verifies that this particular case does not happen.
         */
        Assert.assertEquals(2L, node.executeLong(Truffle.getRuntime().createVirtualFrame(new Object[]{2L}, new FrameDescriptor())));
    }

    @TypeSystemReference(ImplicitCast4Types.class)
    public abstract static class ExecuteChildWithImplicitCast1Node extends ExampleNode {

        @Specialization
        public long sleep(long duration) {
            return duration;
        }

    }

    @Test
    public void testImplicitCastExecute() {
        CallTarget target = ExampleNode.createTarget(ImplicitCastExecuteNodeGen.create(ExampleNode.createArguments(2)));
        Assert.assertEquals("s1", target.call(1, 2D));
        Assert.assertEquals("s0", target.call(1, 1));

        target = ExampleNode.createTarget(ImplicitCastExecuteNodeGen.create(ExampleNode.createArguments(2)));
        Assert.assertEquals("s0", target.call(1, 1));
        Assert.assertEquals("s1", target.call(1, 2D));

        target = ExampleNode.createTarget(ImplicitCastExecuteNodeGen.create(ExampleNode.createArguments(2)));
        Assert.assertEquals("s0", target.call(1, 1));
        Assert.assertEquals("s2", target.call(1, 2L));

        target = ExampleNode.createTarget(ImplicitCastExecuteNodeGen.create(ExampleNode.createArguments(2)));
        Assert.assertEquals("s2", target.call(1, 2L));
        Assert.assertEquals("s0", target.call(1, 1));
    }

    @TypeSystem
    public static class TS {
        @ImplicitCast
        public static int promoteToInt(byte value) {
            return value;
        }

        @ImplicitCast
        public static int promoteToInt(short value) {
            return value;
        }

        @ImplicitCast
        public static long promoteToLong(byte value) {
            return value;
        }

        @ImplicitCast
        public static long promoteToLong(short value) {
            return value;
        }

        @ImplicitCast
        public static long promoteToLong(int value) {
            return value;
        }

        @ImplicitCast
        public static double promoteToDouble(float value) {
            return value;
        }
    }

    @TypeSystemReference(TS.class)
    @SuppressWarnings("unused")
    public abstract static class ImplicitCastExecuteNode extends ExampleNode {

        @Specialization
        public String s0(int a, int b) {
            return "s0";
        }

        @Specialization
        public String s1(long a, double b) {
            return "s1";
        }

        @Specialization
        public String s2(long a, long b) {
            return "s2";
        }

    }

    @Test
    public void testImplicitCastExecute2() {
        ExampleArgumentNode[] args = ExampleNode.createArguments(2);
        CallTarget target = ExampleNode.createTarget(ImplicitCastExecuteNodeGen.create(args));

        Assert.assertEquals("s2", target.call(1L, 1L));
        Assert.assertEquals(0, args[0].longInvocationCount);
        Assert.assertEquals(0, args[1].longInvocationCount);
        Assert.assertEquals(1, args[0].genericInvocationCount);
        Assert.assertEquals(1, args[1].genericInvocationCount);

        Assert.assertEquals("s2", target.call(1L, 1L));

        Assert.assertEquals(1, args[0].longInvocationCount);
        Assert.assertEquals(1, args[1].longInvocationCount);
        Assert.assertEquals(2, args[0].genericInvocationCount);
        Assert.assertEquals(2, args[1].genericInvocationCount);

        Assert.assertEquals("s2", target.call(1L, 1L));

        Assert.assertEquals(2, args[0].longInvocationCount);
        Assert.assertEquals(2, args[1].longInvocationCount);
        Assert.assertEquals(3, args[0].genericInvocationCount);
        Assert.assertEquals(3, args[1].genericInvocationCount);

        Assert.assertEquals(0, args[0].doubleInvocationCount);
        Assert.assertEquals(0, args[1].doubleInvocationCount);
        Assert.assertEquals(0, args[0].intInvocationCount);
        Assert.assertEquals(0, args[1].intInvocationCount);

    }

    @Test
    public void testImplicitCastWithCache() {
        TestImplicitCastWithCacheNode node = TestImplicitCastWithCacheNodeGen.create();

        Assert.assertEquals(0, node.specializeCalls);

        ConcreteString concrete = new ConcreteString();
        node.execute("a", true);
        Assert.assertEquals(1, node.specializeCalls);
        node.execute(concrete, true);
        Assert.assertEquals(2, node.specializeCalls);
        node.execute(concrete, true);
        node.execute(concrete, true);
        node.execute(concrete, true);

        // ensure we stabilize
        Assert.assertEquals(2, node.specializeCalls);
    }

    interface AbstractString {
    }

    static class ConcreteString implements AbstractString {
    }

    @TypeSystem
    static class TestTypeSystem {
        @ImplicitCast
        public static AbstractString toAbstractStringVector(@SuppressWarnings("unused") String vector) {
            return new ConcreteString();
        }
    }

    @TypeSystemReference(TestTypeSystem.class)
    abstract static class TestImplicitCastWithCacheNode extends Node {

        int specializeCalls;

        public abstract int execute(Object arg, boolean flag);

        @Specialization(guards = {"specializeCall(flag)", "cachedFlag == flag"})
        @SuppressWarnings("unused")
        protected static int test(AbstractString arg, boolean flag,
                        @Cached("flag") boolean cachedFlag) {
            return flag ? 100 : -100;
        }

        boolean specializeCall(@SuppressWarnings("unused") boolean flag) {
            ReentrantLock lock = (ReentrantLock) getLock();
            if (lock.isHeldByCurrentThread()) {
                // the lock is held for guards executed in executeAndSpecialize
                specializeCalls++;
            }
            return true;
        }

    }

    @Test
    public void test33Bits() throws NoSuchFieldException, SecurityException {
        ExampleNode node = ThirtyThreeBitsNodeGen.create(null);
        Field stateField = node.getClass().getDeclaredField("state_");
        Assert.assertEquals(long.class, stateField.getType());
    }

    @Test
    public void test32Bits() throws NoSuchFieldException, SecurityException {
        ExampleNode node = ThirtyTwoBitsNodeGen.create(null);
        Field stateField = node.getClass().getDeclaredField("state_");
        Assert.assertEquals(int.class, stateField.getType());
    }

    @TypeSystem
    public static class FourBitImplicitCastTS {
        @ImplicitCast
        public static Number castNumber(Byte value) {
            return value;
        }

        @ImplicitCast
        public static Number castNumber(Short value) {
            return value;
        }

        @ImplicitCast
        public static Number castNumber(Integer value) {
            return value;
        }
    }

    /*
     * Requires 33 state bits: 1 bit for the specialization + 8 * 4 bits for the Number parameters
     * (3 bits for the implicit casts to Number + 1 bit for Number itself).
     */
    @TypeSystemReference(FourBitImplicitCastTS.class)
    @SuppressWarnings("unused")
    public abstract static class ThirtyThreeBitsNode extends ExampleNode {
        @Specialization
        public String s0(Number a, Number b, Number c, Number d, Number e, Number f, Number g, Number h) {
            return "s0";
        }
    }

    /*
     * Requires 32 state bits: 4 bits for the specializations + 7 * 4 bits for the Number parameters
     * (3 bits for the implicit casts to Number + 1 bit for Number itself).
     */
    @TypeSystemReference(FourBitImplicitCastTS.class)
    @SuppressWarnings("unused")
    public abstract static class ThirtyTwoBitsNode extends ExampleNode {
        @Specialization
        public String s0(Number a, Number b, Number c, Number d, Number e, Number f, Number g) {
            return "s0";
        }

        @Specialization
        public String s1(String a, String b, String c, String d, String e, String f, String g) {
            return "s1";
        }

        @Specialization
        public String s2(Boolean a, Boolean b, Boolean c, Boolean d, Boolean e, Boolean f, Boolean g) {
            return "s2";
        }

        @Specialization
        public String s3(Character a, Character b, Character c, Character d, Character e, Character f, Character g) {
            return "s3";
        }
    }
}
