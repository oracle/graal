/*
 * Copyright (c) 2014, 2015, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package org.graalvm.compiler.replacements.test;

import java.util.function.Function;

import org.graalvm.compiler.api.directives.GraalDirectives;
import org.graalvm.compiler.api.replacements.ClassSubstitution;
import org.graalvm.compiler.api.replacements.MethodSubstitution;
import org.graalvm.compiler.bytecode.BytecodeProvider;
import org.graalvm.compiler.debug.Debug;
import org.graalvm.compiler.debug.DebugConfigScope;
import org.graalvm.compiler.graph.GraalGraphError;
import org.graalvm.compiler.graph.Node.ConstantNodeParameter;
import org.graalvm.compiler.graph.Node.NodeIntrinsic;
import org.graalvm.compiler.nodes.PiNode;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugin.Receiver;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins.Registration;
import org.junit.Assert;
import org.junit.Test;

import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Tests for expected behavior when parsing snippets and intrinsics.
 */
public class ReplacementsParseTest extends ReplacementsTest {

    static final Object THROW_EXCEPTION_MARKER = new Object() {
        @Override
        public String toString() {
            return "THROW_EXCEPTION_MARKER";
        }
    };

    static int copyFirstBody(byte[] left, byte[] right, boolean left2right) {
        if (left2right) {
            byte e = left[0];
            right[0] = e;
            return e;
        } else {
            byte e = right[0];
            left[0] = e;
            return e;
        }
    }

    static int copyFirstL2RBody(byte[] left, byte[] right) {
        byte e = left[0];
        right[0] = e;
        return e;
    }

    static class TestObject {
        static double next(double v) {
            return Math.nextAfter(v, 1.0);
        }

        static double next2(double v) {
            return Math.nextAfter(v, 1.0);
        }

        static double nextAfter(double x, double d) {
            return Math.nextAfter(x, d);
        }

        TestObject() {
            this(null);
        }

        TestObject(Object id) {
            this.id = id;
        }

        final Object id;

        String stringizeId() {
            String res = String.valueOf(id);
            if (res.equals(THROW_EXCEPTION_MARKER.toString())) {
                // Tests exception throwing from partial intrinsification
                throw new RuntimeException("ex: " + id);
            }
            return res;
        }

        static String stringize(Object obj) {
            String res = String.valueOf(obj);
            if (res.equals(THROW_EXCEPTION_MARKER.toString())) {
                // Tests exception throwing from partial intrinsification
                throw new RuntimeException("ex: " + obj);
            }
            return res;
        }

        static String identity(String s) {
            return s;
        }

        /**
         * @see TestObjectSubstitutions#copyFirst(byte[], byte[], boolean)
         */
        static int copyFirst(byte[] left, byte[] right, boolean left2right) {
            return copyFirstBody(left, right, left2right);
        }

        /**
         * @see TestObjectSubstitutions#copyFirstL2R(byte[], byte[])
         */
        static int copyFirstL2R(byte[] left, byte[] right) {
            return copyFirstL2RBody(left, right);
        }
    }

    @ClassSubstitution(TestObject.class)
    static class TestObjectSubstitutions {

        @MethodSubstitution(isStatic = true)
        static double nextAfter(double x, double d) {
            double xx = (x == -0.0 ? 0.0 : x);
            return Math.nextAfter(xx, d);
        }

        /**
         * Tests conditional intrinsification of a static method.
         */
        @MethodSubstitution
        static String stringize(Object obj) {
            if (obj != null && obj.getClass() == String.class) {
                return asNonNullString(obj);
            } else {
                // A recursive call denotes exiting/deoptimizing
                // out of the partial intrinsification to the
                // slow/uncommon case.
                return stringize(obj);
            }
        }

        /**
         * Tests conditional intrinsification of a non-static method.
         */
        @MethodSubstitution(isStatic = false)
        static String stringizeId(TestObject thisObj) {
            if (thisObj.id != null && thisObj.id.getClass() == String.class) {
                return asNonNullString(thisObj.id);
            } else {
                // A recursive call denotes exiting/deoptimizing
                // out of the partial intrinsification to the
                // slow/uncommon case.
                return outOfLinePartialIntrinsification(thisObj);
            }
        }

        static String outOfLinePartialIntrinsification(TestObject thisObj) {
            return stringizeId(thisObj);
        }

        public static String asNonNullString(Object object) {
            return asNonNullStringIntrinsic(object, String.class, true, true);
        }

        @NodeIntrinsic(PiNode.class)
        private static native String asNonNullStringIntrinsic(Object object, @ConstantNodeParameter Class<?> toType, @ConstantNodeParameter boolean exactType, @ConstantNodeParameter boolean nonNull);

        /**
         * An valid intrinsic as the frame state associated with the merge should prevent the frame
         * states associated with the array stores from being associated with subsequent
         * deoptimizing nodes.
         */
        @MethodSubstitution
        static int copyFirst(byte[] left, byte[] right, boolean left2right) {
            return copyFirstBody(left, right, left2right);
        }

        /**
         * An invalid intrinsic as the frame state associated with the array assignment can leak out
         * to subsequent deoptimizing nodes.
         */
        @MethodSubstitution
        static int copyFirstL2R(byte[] left, byte[] right) {
            return copyFirstL2RBody(left, right);
        }

        /**
         * Tests that non-capturing lambdas are folded away.
         */
        @MethodSubstitution
        static String identity(String value) {
            return apply(s -> s, value);
        }

        private static String apply(Function<String, String> f, String value) {
            return f.apply(value);
        }
    }

    @Override
    protected void registerInvocationPlugins(InvocationPlugins invocationPlugins) {
        BytecodeProvider replacementBytecodeProvider = getSystemClassLoaderBytecodeProvider();
        Registration r = new Registration(invocationPlugins, TestObject.class, replacementBytecodeProvider);
        new PluginFactory_ReplacementsParseTest().registerPlugins(invocationPlugins, null);
        r.registerMethodSubstitution(TestObjectSubstitutions.class, "nextAfter", double.class, double.class);
        r.registerMethodSubstitution(TestObjectSubstitutions.class, "stringize", Object.class);
        r.registerMethodSubstitution(TestObjectSubstitutions.class, "stringizeId", Receiver.class);
        r.registerMethodSubstitution(TestObjectSubstitutions.class, "copyFirst", byte[].class, byte[].class, boolean.class);
        r.registerMethodSubstitution(TestObjectSubstitutions.class, "copyFirstL2R", byte[].class, byte[].class);

        if (replacementBytecodeProvider.supportsInvokedynamic()) {
            r.registerMethodSubstitution(TestObjectSubstitutions.class, "identity", String.class);
        }
        super.registerInvocationPlugins(invocationPlugins);
    }

    /**
     * Ensure that calling the original method from the substitution binds correctly.
     */
    @Test
    public void test1() {
        test("test1Snippet", 1.0);
    }

    public double test1Snippet(double d) {
        return TestObject.next(d);
    }

    /**
     * Ensure that calling the substitution method binds to the original method properly.
     */
    @Test
    public void test2() {
        test("test2Snippet", 1.0);
    }

    public double test2Snippet(double d) {
        return TestObject.next2(d);
    }

    /**
     * Ensure that substitution methods with assertions in them don't complain when the exception
     * constructor is deleted.
     */

    @Test
    public void testNextAfter() {
        double[] inArray = new double[1024];
        double[] outArray = new double[1024];
        for (int i = 0; i < inArray.length; i++) {
            inArray[i] = -0.0;
        }
        test("doNextAfter", inArray, outArray);
    }

    public void doNextAfter(double[] outArray, double[] inArray) {
        for (int i = 0; i < inArray.length; i++) {
            double direction = (i & 1) == 0 ? Double.POSITIVE_INFINITY : -Double.NEGATIVE_INFINITY;
            outArray[i] = TestObject.nextAfter(inArray[i], direction);
        }
    }

    @Test
    public void testCallStringize() {
        test("callStringize", "a string");
        test("callStringize", THROW_EXCEPTION_MARKER);
        test("callStringize", Boolean.TRUE);
    }

    @Test
    public void testCallStringizeId() {
        test("callStringizeId", new TestObject("a string"));
        test("callStringizeId", new TestObject(THROW_EXCEPTION_MARKER));
        test("callStringizeId", new TestObject(Boolean.TRUE));
    }

    public static Object callStringize(Object obj) {
        return TestObject.stringize(obj);
    }

    public static Object callStringizeId(TestObject testObj) {
        return indirect(testObj);
    }

    @BytecodeParserNeverInline
    private static String indirect(TestObject testObj) {
        return testObj.stringizeId();
    }

    @Test
    public void testRootCompileStringize() {
        ResolvedJavaMethod method = getResolvedJavaMethod(TestObject.class, "stringize");
        test(method, null, "a string");
        test(method, null, Boolean.TRUE);
        test(method, null, THROW_EXCEPTION_MARKER);
    }

    @Test
    public void testLambda() {
        test("callLambda", (String) null);
        test("callLambda", "a string");
    }

    public static String callLambda(String value) {
        return TestObject.identity(value);
    }

    public static int callCopyFirst(byte[] in, byte[] out, boolean left2right) {
        int res = TestObject.copyFirst(in, out, left2right);
        if (res == 17) {
            // A node after the intrinsic that needs a frame state.
            GraalDirectives.deoptimize();
        }
        return res;
    }

    public static int callCopyFirstL2R(byte[] in, byte[] out) {
        int res = TestObject.copyFirstL2R(in, out);
        if (res == 17) {
            // A node after the intrinsic that needs a frame state.
            GraalDirectives.deoptimize();
        }
        return res;
    }

    @Test
    public void testCallCopyFirst() {
        byte[] in = {0, 1, 2, 3, 4};
        byte[] out = new byte[in.length];
        test("callCopyFirst", in, out, true);
        test("callCopyFirst", in, out, false);
    }

    @SuppressWarnings("try")
    @Test
    public void testCallCopyFirstL2R() {
        byte[] in = {0, 1, 2, 3, 4};
        byte[] out = new byte[in.length];
        try {
            try (DebugConfigScope s = Debug.setConfig(Debug.silentConfig())) {
                test("callCopyFirstL2R", in, out);
            }
        } catch (GraalGraphError e) {
            Assert.assertTrue(e.getMessage().startsWith("Invalid frame state"));
        }
    }
}
