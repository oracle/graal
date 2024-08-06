/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

package jdk.graal.compiler.core.test;

import static jdk.internal.misc.Unsafe.ARRAY_BOOLEAN_BASE_OFFSET;
import static jdk.internal.misc.Unsafe.ARRAY_BYTE_BASE_OFFSET;
import static jdk.internal.misc.Unsafe.ARRAY_CHAR_BASE_OFFSET;
import static jdk.internal.misc.Unsafe.ARRAY_DOUBLE_BASE_OFFSET;
import static jdk.internal.misc.Unsafe.ARRAY_FLOAT_BASE_OFFSET;
import static jdk.internal.misc.Unsafe.ARRAY_INT_BASE_OFFSET;
import static jdk.internal.misc.Unsafe.ARRAY_LONG_BASE_OFFSET;
import static jdk.internal.misc.Unsafe.ARRAY_OBJECT_BASE_OFFSET;
import static jdk.internal.misc.Unsafe.ARRAY_SHORT_BASE_OFFSET;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import jdk.graal.compiler.api.replacements.SnippetReflectionProvider;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.StructuredGraph.AllowAssumptions;
import jdk.graal.compiler.nodes.StructuredGraph.Builder;
import jdk.graal.compiler.phases.PhaseSuite;
import jdk.graal.compiler.phases.tiers.HighTierContext;
import jdk.graal.compiler.test.AddExports;
import org.junit.Assert;

import jdk.internal.misc.Unsafe;
import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.code.InvalidInstalledCodeException;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * This is based on {@code test/hotspot/jtreg/compiler/unsafe/UnsafeGetStableArrayElement.java} and
 * differs in that it only asserts the behavior of Graal with respect to reading an element from a
 * {@code Stable} array via Unsafe.
 */
@AddExports("java.base/jdk.internal.misc")
public class UnsafeGetStableArrayElement extends GraalCompilerTest {
    // @formatter:off
    private static final List<Object> StableArrays = new ArrayList<>();
    private static <T> T register(T t) {  StableArrays.add(t); return t; }

    // These are treated as @Stable fields thanks to the applyStable method
    static final boolean[] STABLE_BOOLEAN_ARRAY = register(new boolean[16]);
    static final    byte[]    STABLE_BYTE_ARRAY = register(new    byte[16]);
    static final   short[]   STABLE_SHORT_ARRAY = register(new   short[8]);
    static final    char[]    STABLE_CHAR_ARRAY = register(new    char[8]);
    static final     int[]     STABLE_INT_ARRAY = register(new     int[4]);
    static final    long[]    STABLE_LONG_ARRAY = register(new    long[2]);
    static final   float[]   STABLE_FLOAT_ARRAY = register(new   float[4]);
    static final  double[]  STABLE_DOUBLE_ARRAY = register(new  double[2]);
    static final  Object[]  STABLE_OBJECT_ARRAY = register(new  Object[4]);

    static {
        // Tests will set/reset the first element of those arrays.
        // Place a canary on the second element to catch issues involving reading more than the first element.
        STABLE_BYTE_ARRAY[1] = 0x10;
        STABLE_SHORT_ARRAY[1] = 0x10;
        STABLE_CHAR_ARRAY[1] = 0x10;
        STABLE_BOOLEAN_ARRAY[1] = true;
        Setter.reset();

        // Ensure boxing caches are initialized
        Byte.valueOf((byte)0);
        Short.valueOf((short)0);
        Character.valueOf('0');
        Integer.valueOf(0);
        Long.valueOf(0);
        Float.valueOf(0.0F);
        Double.valueOf(0.0F);
    }
    static final Unsafe U = Unsafe.getUnsafe();

    static class Setter {
        private static void setZ(boolean defaultVal) { STABLE_BOOLEAN_ARRAY[0] = defaultVal ? false : Test.nonDefaultZ(); }
        private static void setB(boolean defaultVal) { STABLE_BYTE_ARRAY[0]    = defaultVal ?     0 : Test.nonDefaultB(); }
        private static void setS(boolean defaultVal) { STABLE_SHORT_ARRAY[0]   = defaultVal ?     0 : Test.nonDefaultS(); }
        private static void setC(boolean defaultVal) { STABLE_CHAR_ARRAY[0]    = defaultVal ?     0 : Test.nonDefaultC(); }
        private static void setI(boolean defaultVal) { STABLE_INT_ARRAY[0]     = defaultVal ?     0 : Test.nonDefaultI(); }
        private static void setJ(boolean defaultVal) { STABLE_LONG_ARRAY[0]    = defaultVal ?     0 : Test.nonDefaultJ(); }
        private static void setF(boolean defaultVal) { STABLE_FLOAT_ARRAY[0]   = defaultVal ?     0 : Test.nonDefaultF(); }
        private static void setD(boolean defaultVal) { STABLE_DOUBLE_ARRAY[0]  = defaultVal ?     0 : Test.nonDefaultD(); }
        private static void setL(boolean defaultVal) { STABLE_OBJECT_ARRAY[0]  = defaultVal ?  null :       new Object(); }

        static void reset() {
            setZ(false);
            setB(false);
            setS(false);
            setC(false);
            setI(false);
            setJ(false);
            setF(false);
            setD(false);
            setL(false);
        }
    }

    static class Test {
        static void changeZ() { Setter.setZ(true); }
        static void changeB() { Setter.setB(true); }
        static void changeS() { Setter.setS(true); }
        static void changeC() { Setter.setC(true); }
        static void changeI() { Setter.setI(true); }
        static void changeJ() { Setter.setJ(true); }
        static void changeF() { Setter.setF(true); }
        static void changeD() { Setter.setD(true); }
        static void changeL() { Setter.setL(true); }

        static boolean nonDefaultZ() { return true; }
        // the integer values are selected to make sure sign/zero-extension behaviour is tested.
        static byte    nonDefaultB() { return -1; }
        static short   nonDefaultS() { return -1; }
        static char    nonDefaultC() { return Character.MAX_VALUE; }
        static int     nonDefaultI() { return -1; }
        static long    nonDefaultJ() { return -1; }
        static float   nonDefaultF() { return Float.MAX_VALUE; }
        static double  nonDefaultD() { return Double.MAX_VALUE; }

        static boolean testZ_Z() { return U.getBoolean(STABLE_BOOLEAN_ARRAY, ARRAY_BOOLEAN_BASE_OFFSET); }
        static byte    testZ_B() { return U.getByte(   STABLE_BOOLEAN_ARRAY, ARRAY_BOOLEAN_BASE_OFFSET); }
        static short   testZ_S() { return U.getShort(  STABLE_BOOLEAN_ARRAY, ARRAY_BOOLEAN_BASE_OFFSET); }
        static char    testZ_C() { return U.getChar(   STABLE_BOOLEAN_ARRAY, ARRAY_BOOLEAN_BASE_OFFSET); }
        static int     testZ_I() { return U.getInt(    STABLE_BOOLEAN_ARRAY, ARRAY_BOOLEAN_BASE_OFFSET); }
        static long    testZ_J() { return U.getLong(   STABLE_BOOLEAN_ARRAY, ARRAY_BOOLEAN_BASE_OFFSET); }
        static float   testZ_F() { return U.getFloat(  STABLE_BOOLEAN_ARRAY, ARRAY_BOOLEAN_BASE_OFFSET); }
        static double  testZ_D() { return U.getDouble( STABLE_BOOLEAN_ARRAY, ARRAY_BOOLEAN_BASE_OFFSET); }

        static boolean testB_Z() { return U.getBoolean(STABLE_BYTE_ARRAY, ARRAY_BYTE_BASE_OFFSET); }
        static byte    testB_B() { return U.getByte(   STABLE_BYTE_ARRAY, ARRAY_BYTE_BASE_OFFSET); }
        static short   testB_S() { return U.getShort(  STABLE_BYTE_ARRAY, ARRAY_BYTE_BASE_OFFSET); }
        static char    testB_C() { return U.getChar(   STABLE_BYTE_ARRAY, ARRAY_BYTE_BASE_OFFSET); }
        static int     testB_I() { return U.getInt(    STABLE_BYTE_ARRAY, ARRAY_BYTE_BASE_OFFSET); }
        static long    testB_J() { return U.getLong(   STABLE_BYTE_ARRAY, ARRAY_BYTE_BASE_OFFSET); }
        static float   testB_F() { return U.getFloat(  STABLE_BYTE_ARRAY, ARRAY_BYTE_BASE_OFFSET); }
        static double  testB_D() { return U.getDouble( STABLE_BYTE_ARRAY, ARRAY_BYTE_BASE_OFFSET); }
        static int     testB_Bw() { return U.getByte(   STABLE_BYTE_ARRAY, ARRAY_BYTE_BASE_OFFSET); }

        static boolean testS_Z() { return U.getBoolean(STABLE_SHORT_ARRAY, ARRAY_SHORT_BASE_OFFSET); }
        static byte    testS_B() { return U.getByte(   STABLE_SHORT_ARRAY, ARRAY_SHORT_BASE_OFFSET); }
        static short   testS_S() { return U.getShort(  STABLE_SHORT_ARRAY, ARRAY_SHORT_BASE_OFFSET); }
        static char    testS_C() { return U.getChar(   STABLE_SHORT_ARRAY, ARRAY_SHORT_BASE_OFFSET); }
        static int     testS_I() { return U.getInt(    STABLE_SHORT_ARRAY, ARRAY_SHORT_BASE_OFFSET); }
        static long    testS_J() { return U.getLong(   STABLE_SHORT_ARRAY, ARRAY_SHORT_BASE_OFFSET); }
        static float   testS_F() { return U.getFloat(  STABLE_SHORT_ARRAY, ARRAY_SHORT_BASE_OFFSET); }
        static double  testS_D() { return U.getDouble( STABLE_SHORT_ARRAY, ARRAY_SHORT_BASE_OFFSET); }
        static int     testS_Sw() { return U.getShort(  STABLE_SHORT_ARRAY, ARRAY_SHORT_BASE_OFFSET); }

        static boolean testC_Z() { return U.getBoolean(STABLE_CHAR_ARRAY, ARRAY_CHAR_BASE_OFFSET); }
        static byte    testC_B() { return U.getByte(   STABLE_CHAR_ARRAY, ARRAY_CHAR_BASE_OFFSET); }
        static short   testC_S() { return U.getShort(  STABLE_CHAR_ARRAY, ARRAY_CHAR_BASE_OFFSET); }
        static char    testC_C() { return U.getChar(   STABLE_CHAR_ARRAY, ARRAY_CHAR_BASE_OFFSET); }
        static int     testC_I() { return U.getInt(    STABLE_CHAR_ARRAY, ARRAY_CHAR_BASE_OFFSET); }
        static long    testC_J() { return U.getLong(   STABLE_CHAR_ARRAY, ARRAY_CHAR_BASE_OFFSET); }
        static float   testC_F() { return U.getFloat(  STABLE_CHAR_ARRAY, ARRAY_CHAR_BASE_OFFSET); }
        static double  testC_D() { return U.getDouble( STABLE_CHAR_ARRAY, ARRAY_CHAR_BASE_OFFSET); }
        static int     testC_Cw() { return U.getChar(   STABLE_CHAR_ARRAY, ARRAY_CHAR_BASE_OFFSET); }

        static boolean testI_Z() { return U.getBoolean(STABLE_INT_ARRAY, ARRAY_INT_BASE_OFFSET); }
        static byte    testI_B() { return U.getByte(   STABLE_INT_ARRAY, ARRAY_INT_BASE_OFFSET); }
        static short   testI_S() { return U.getShort(  STABLE_INT_ARRAY, ARRAY_INT_BASE_OFFSET); }
        static char    testI_C() { return U.getChar(   STABLE_INT_ARRAY, ARRAY_INT_BASE_OFFSET); }
        static int     testI_I() { return U.getInt(    STABLE_INT_ARRAY, ARRAY_INT_BASE_OFFSET); }
        static long    testI_J() { return U.getLong(   STABLE_INT_ARRAY, ARRAY_INT_BASE_OFFSET); }
        static float   testI_F() { return U.getFloat(  STABLE_INT_ARRAY, ARRAY_INT_BASE_OFFSET); }
        static double  testI_D() { return U.getDouble( STABLE_INT_ARRAY, ARRAY_INT_BASE_OFFSET); }

        static boolean testJ_Z() { return U.getBoolean(STABLE_LONG_ARRAY, ARRAY_LONG_BASE_OFFSET); }
        static byte    testJ_B() { return U.getByte(   STABLE_LONG_ARRAY, ARRAY_LONG_BASE_OFFSET); }
        static short   testJ_S() { return U.getShort(  STABLE_LONG_ARRAY, ARRAY_LONG_BASE_OFFSET); }
        static char    testJ_C() { return U.getChar(   STABLE_LONG_ARRAY, ARRAY_LONG_BASE_OFFSET); }
        static int     testJ_I() { return U.getInt(    STABLE_LONG_ARRAY, ARRAY_LONG_BASE_OFFSET); }
        static long    testJ_J() { return U.getLong(   STABLE_LONG_ARRAY, ARRAY_LONG_BASE_OFFSET); }
        static float   testJ_F() { return U.getFloat(  STABLE_LONG_ARRAY, ARRAY_LONG_BASE_OFFSET); }
        static double  testJ_D() { return U.getDouble( STABLE_LONG_ARRAY, ARRAY_LONG_BASE_OFFSET); }

        static boolean testF_Z() { return U.getBoolean(STABLE_FLOAT_ARRAY, ARRAY_FLOAT_BASE_OFFSET); }
        static byte    testF_B() { return U.getByte(   STABLE_FLOAT_ARRAY, ARRAY_FLOAT_BASE_OFFSET); }
        static short   testF_S() { return U.getShort(  STABLE_FLOAT_ARRAY, ARRAY_FLOAT_BASE_OFFSET); }
        static char    testF_C() { return U.getChar(   STABLE_FLOAT_ARRAY, ARRAY_FLOAT_BASE_OFFSET); }
        static int     testF_I() { return U.getInt(    STABLE_FLOAT_ARRAY, ARRAY_FLOAT_BASE_OFFSET); }
        static long    testF_J() { return U.getLong(   STABLE_FLOAT_ARRAY, ARRAY_FLOAT_BASE_OFFSET); }
        static float   testF_F() { return U.getFloat(  STABLE_FLOAT_ARRAY, ARRAY_FLOAT_BASE_OFFSET); }
        static double  testF_D() { return U.getDouble( STABLE_FLOAT_ARRAY, ARRAY_FLOAT_BASE_OFFSET); }

        static boolean testD_Z() { return U.getBoolean(STABLE_DOUBLE_ARRAY, ARRAY_DOUBLE_BASE_OFFSET); }
        static byte    testD_B() { return U.getByte(   STABLE_DOUBLE_ARRAY, ARRAY_DOUBLE_BASE_OFFSET); }
        static short   testD_S() { return U.getShort(  STABLE_DOUBLE_ARRAY, ARRAY_DOUBLE_BASE_OFFSET); }
        static char    testD_C() { return U.getChar(   STABLE_DOUBLE_ARRAY, ARRAY_DOUBLE_BASE_OFFSET); }
        static int     testD_I() { return U.getInt(    STABLE_DOUBLE_ARRAY, ARRAY_DOUBLE_BASE_OFFSET); }
        static long    testD_J() { return U.getLong(   STABLE_DOUBLE_ARRAY, ARRAY_DOUBLE_BASE_OFFSET); }
        static float   testD_F() { return U.getFloat(  STABLE_DOUBLE_ARRAY, ARRAY_DOUBLE_BASE_OFFSET); }
        static double  testD_D() { return U.getDouble( STABLE_DOUBLE_ARRAY, ARRAY_DOUBLE_BASE_OFFSET); }

        @SuppressWarnings("removal")
        static Object  testL_L() { return U.getReference(STABLE_OBJECT_ARRAY, ARRAY_OBJECT_BASE_OFFSET); }
        static boolean testL_Z() { return U.getBoolean(STABLE_OBJECT_ARRAY, ARRAY_OBJECT_BASE_OFFSET); }
        static byte    testL_B() { return U.getByte(   STABLE_OBJECT_ARRAY, ARRAY_OBJECT_BASE_OFFSET); }
        static short   testL_S() { return U.getShort(  STABLE_OBJECT_ARRAY, ARRAY_OBJECT_BASE_OFFSET); }
        static char    testL_C() { return U.getChar(   STABLE_OBJECT_ARRAY, ARRAY_OBJECT_BASE_OFFSET); }
        static int     testL_I() { return U.getInt(    STABLE_OBJECT_ARRAY, ARRAY_OBJECT_BASE_OFFSET); }
        static long    testL_J() { return U.getLong(   STABLE_OBJECT_ARRAY, ARRAY_OBJECT_BASE_OFFSET); }
        static float   testL_F() { return U.getFloat(  STABLE_OBJECT_ARRAY, ARRAY_OBJECT_BASE_OFFSET); }
        static double  testL_D() { return U.getDouble( STABLE_OBJECT_ARRAY, ARRAY_OBJECT_BASE_OFFSET); }

        static short   testS_U() { return U.getShortUnaligned(STABLE_SHORT_ARRAY, ARRAY_SHORT_BASE_OFFSET + 1); }
        static char    testC_U() { return U.getCharUnaligned(  STABLE_CHAR_ARRAY,  ARRAY_CHAR_BASE_OFFSET + 1); }
        static int     testI_U() { return U.getIntUnaligned(    STABLE_INT_ARRAY,   ARRAY_INT_BASE_OFFSET + 1); }
        static long    testJ_U() { return U.getLongUnaligned(  STABLE_LONG_ARRAY,  ARRAY_LONG_BASE_OFFSET + 1); }
    }
    // @formatter:on

    void run(Callable<?> c) throws Exception {
        run(c, null, null);
    }

    @Override
    protected StructuredGraph parse(Builder builder, PhaseSuite<HighTierContext> graphBuilderSuite) {
        StructuredGraph graph = super.parse(builder, graphBuilderSuite);
        applyStable(graph);
        return graph;
    }

    /**
     * Finds each {@link ConstantNode} in {@code graph} that wraps one of the {@link #StableArrays}
     * and replaces it with {@link ConstantNode} with a
     * {@linkplain ConstantNode#getStableDimension() stable dimension} of 1.
     */
    private void applyStable(StructuredGraph graph) {
        if (graph.method().getDeclaringClass().toJavaName().equals(Test.class.getName())) {
            SnippetReflectionProvider snippetReflection = getSnippetReflection();
            for (ConstantNode cn : graph.getNodes().filter(ConstantNode.class)) {
                JavaConstant javaConstant = (JavaConstant) cn.getValue();
                Object obj = snippetReflection.asObject(Object.class, javaConstant);
                if (StableArrays.contains(obj)) {
                    ConstantNode stableConstant = ConstantNode.forConstant(javaConstant, 1, cn.isDefaultStable(), getMetaAccess());
                    graph.unique(stableConstant);
                    cn.replace(graph, stableConstant);
                    break;
                }
            }
        }
    }

    private static void assertEQ(CompiledMethod compiledMethod, Object left, Object right) {
        Assert.assertEquals(String.valueOf(compiledMethod.method), left, right);
    }

    private static void assertNE(CompiledMethod compiledMethod, Object left, Object right) {
        Assert.assertNotEquals(String.valueOf(compiledMethod.method), left, right);
    }

    static class CompiledMethod {
        final ResolvedJavaMethod method;
        final InstalledCode code;

        CompiledMethod(ResolvedJavaMethod method, InstalledCode code) {
            this.method = method;
            this.code = code;
        }

        Object call() throws InvalidInstalledCodeException {
            return this.code.executeVarargs();
        }
    }

    /**
     * Compile the method called by the lambda wrapped by {@code c}.
     */
    private CompiledMethod compile(Callable<?> c) {
        ResolvedJavaMethod m = getResolvedJavaMethod(c.getClass(), "call");
        StructuredGraph graph = parseEager(m, AllowAssumptions.NO);
        List<ResolvedJavaMethod> invokedMethods = StreamSupport.stream(graph.getInvokes().spliterator(), false).map((inv) -> inv.getTargetMethod()).collect(Collectors.toList());
        Assert.assertEquals(String.valueOf(invokedMethods), 1, invokedMethods.size());
        ResolvedJavaMethod invokedMethod = invokedMethods.get(0);
        return new CompiledMethod(invokedMethod, getCode(invokedMethods.get(0)));
    }

    void run(Callable<?> c, Runnable sameResultAction, Runnable changeResultAction) throws Exception {
        Object first = c.call();
        CompiledMethod cm = compile(c);

        if (sameResultAction != null) {
            sameResultAction.run();
            assertEQ(cm, first, cm.call());
        }

        if (changeResultAction != null) {
            changeResultAction.run();
            assertNE(cm, first, cm.call());
            assertEQ(cm, cm.call(), cm.call());
        }
    }

    /**
     * Tests this sequence:
     *
     * <ol>
     * <li>{@code res1 = c()}</li>
     * <li>Compile c.</li>
     * <li>Change stable array element read by c.</li>
     * <li>{@code res2 = c()}</li>
     * <li>{@code assert Objects.equals(res1, res2)}</li>
     * </ol>
     *
     * That is, tests that compiling a method with an unsafe read of a stable array element folds
     * the element value into the compiled code.
     *
     * @param c a handle to one of the methods in {@link Test}
     * @param setDefaultAction a method that when invoked will change the value of the array element
     *            read by {@code c}
     */
    void testMatched(Callable<?> c, Runnable setDefaultAction) throws Exception {
        run(c, setDefaultAction, null);
        Setter.reset();
    }

    /**
     * Similar to {@link #testMatched(Callable, Runnable)} with the additional check that the value
     * read from the array is {@code nonDefaultValue}.
     *
     * This is meant to be used for sub-int types to catch cases where more bytes than necessary are
     * read from the stable array.
     *
     * The return type of {@code c} should be {@code int} to catch issues since sub-int return types
     * apply masking.
     */
    void testMatchedSubInt(Callable<?> c, Runnable setDefaultAction, int nonDefaultValue) throws Exception {
        Object first = c.call();
        Assert.assertEquals(nonDefaultValue, first);
        CompiledMethod cm = compile(c);

        if (setDefaultAction != null) {
            setDefaultAction.run();
            assertEQ(cm, first, cm.call());
        }

        Setter.reset();
    }

    /**
     * Tests this sequence:
     *
     * <ol>
     * <li>{@code res1 = c()}</li>
     * <li>Compile c.</li>
     * <li>Change stable array element read by c.</li>
     * <li>{@code res2 = c()}</li>
     * <li>{@code assert !Objects.equals(res1, res2)}</li>
     * </ol>
     *
     * That is, tests that compiling a method with an unsafe read of a stable array element does not
     * fold the element value into the compiled code.
     *
     * @param c a handle to one of the methods in {@link Test}
     * @param setDefaultAction a method that when invoked will change the value of the array element
     *            read by {@code c}
     */
    void testMismatched(Callable<?> c, Runnable setDefaultAction) throws Exception {
        run(c, null, setDefaultAction);
        Setter.reset();
    }

    @org.junit.Test
    public void test1() throws Exception {
        testMatched(Test::testZ_Z, Test::changeZ);
    }

    @org.junit.Test
    public void test2() throws Exception {
        testMatched(Test::testZ_B, Test::changeZ);
    }

    @org.junit.Test
    public void test3() throws Exception {
        testMatched(Test::testZ_S, Test::changeZ);
    }

    @org.junit.Test
    public void test4() throws Exception {
        testMatched(Test::testZ_C, Test::changeZ);
    }

    @org.junit.Test
    public void test5() throws Exception {
        testMatched(Test::testZ_I, Test::changeZ);
    }

    @org.junit.Test
    public void test6() throws Exception {
        testMatched(Test::testZ_J, Test::changeZ);
    }

    @org.junit.Test
    public void test7() throws Exception {
        testMatched(Test::testZ_F, Test::changeZ);
    }

    @org.junit.Test
    public void test8() throws Exception {
        testMatched(Test::testZ_D, Test::changeZ);
    }

    @org.junit.Test
    public void test9() throws Exception {
        testMatched(Test::testB_Z, Test::changeB);
    }

    @org.junit.Test
    public void test10() throws Exception {
        testMatched(Test::testB_B, Test::changeB);
    }

    @org.junit.Test
    public void test11() throws Exception {
        testMatched(Test::testB_S, Test::changeB);
    }

    @org.junit.Test
    public void test12() throws Exception {
        testMatched(Test::testB_C, Test::changeB);
    }

    @org.junit.Test
    public void test13() throws Exception {
        testMatched(Test::testB_I, Test::changeB);
    }

    @org.junit.Test
    public void test14() throws Exception {
        testMatched(Test::testB_J, Test::changeB);
    }

    @org.junit.Test
    public void test15() throws Exception {
        testMatched(Test::testB_F, Test::changeB);
    }

    @org.junit.Test
    public void test16() throws Exception {
        testMatched(Test::testB_D, Test::changeB);
    }

    @org.junit.Test
    public void test17() throws Exception {
        testMatchedSubInt(Test::testB_Bw, Test::changeB, Test.nonDefaultB());
    }

    @org.junit.Test
    public void test18() throws Exception {
        testMatched(Test::testS_Z, Test::changeS);
    }

    @org.junit.Test
    public void test19() throws Exception {
        testMatched(Test::testS_B, Test::changeS);
    }

    @org.junit.Test
    public void test20() throws Exception {
        testMatched(Test::testS_S, Test::changeS);
    }

    @org.junit.Test
    public void test21() throws Exception {
        testMatched(Test::testS_C, Test::changeS);
    }

    @org.junit.Test
    public void test22() throws Exception {
        testMatched(Test::testS_I, Test::changeS);
    }

    @org.junit.Test
    public void test23() throws Exception {
        testMatched(Test::testS_J, Test::changeS);
    }

    @org.junit.Test
    public void test24() throws Exception {
        testMatched(Test::testS_F, Test::changeS);
    }

    @org.junit.Test
    public void test25() throws Exception {
        testMatched(Test::testS_D, Test::changeS);
    }

    @org.junit.Test
    public void test26() throws Exception {
        testMatchedSubInt(Test::testS_Sw, Test::changeS, Test.nonDefaultS());
    }

    @org.junit.Test
    public void test27() throws Exception {
        testMatched(Test::testC_Z, Test::changeC);
    }

    @org.junit.Test
    public void test28() throws Exception {
        testMatched(Test::testC_B, Test::changeC);
    }

    @org.junit.Test
    public void test29() throws Exception {
        testMatched(Test::testC_S, Test::changeC);
    }

    @org.junit.Test
    public void test30() throws Exception {
        testMatched(Test::testC_C, Test::changeC);
    }

    @org.junit.Test
    public void test31() throws Exception {
        testMatched(Test::testC_I, Test::changeC);
    }

    @org.junit.Test
    public void test32() throws Exception {
        testMatched(Test::testC_J, Test::changeC);
    }

    @org.junit.Test
    public void test33() throws Exception {
        testMatched(Test::testC_F, Test::changeC);
    }

    @org.junit.Test
    public void test34() throws Exception {
        testMatched(Test::testC_D, Test::changeC);
    }

    @org.junit.Test
    public void test35() throws Exception {
        testMatchedSubInt(Test::testC_Cw, Test::changeC, Test.nonDefaultC());
    }

    @org.junit.Test
    public void test36() throws Exception {
        testMatched(Test::testI_Z, Test::changeI);
    }

    @org.junit.Test
    public void test37() throws Exception {
        testMatched(Test::testI_B, Test::changeI);
    }

    @org.junit.Test
    public void test38() throws Exception {
        testMatched(Test::testI_S, Test::changeI);
    }

    @org.junit.Test
    public void test39() throws Exception {
        testMatched(Test::testI_C, Test::changeI);
    }

    @org.junit.Test
    public void test40() throws Exception {
        testMatched(Test::testI_I, Test::changeI);
    }

    @org.junit.Test
    public void test41() throws Exception {
        testMatched(Test::testI_J, Test::changeI);
    }

    @org.junit.Test
    public void test42() throws Exception {
        testMatched(Test::testI_F, Test::changeI);
    }

    @org.junit.Test
    public void test43() throws Exception {
        testMatched(Test::testI_D, Test::changeI);
    }

    @org.junit.Test
    public void test44() throws Exception {
        testMatched(Test::testJ_Z, Test::changeJ);
    }

    @org.junit.Test
    public void test45() throws Exception {
        testMatched(Test::testJ_B, Test::changeJ);
    }

    @org.junit.Test
    public void test46() throws Exception {
        testMatched(Test::testJ_S, Test::changeJ);
    }

    @org.junit.Test
    public void test47() throws Exception {
        testMatched(Test::testJ_C, Test::changeJ);
    }

    @org.junit.Test
    public void test48() throws Exception {
        testMatched(Test::testJ_I, Test::changeJ);
    }

    @org.junit.Test
    public void test49() throws Exception {
        testMatched(Test::testJ_J, Test::changeJ);
    }

    @org.junit.Test
    public void test50() throws Exception {
        testMatched(Test::testJ_F, Test::changeJ);
    }

    @org.junit.Test
    public void test51() throws Exception {
        testMatched(Test::testJ_D, Test::changeJ);
    }

    @org.junit.Test
    public void test52() throws Exception {
        testMatched(Test::testF_Z, Test::changeF);
    }

    @org.junit.Test
    public void test53() throws Exception {
        testMatched(Test::testF_B, Test::changeF);
    }

    @org.junit.Test
    public void test54() throws Exception {
        testMatched(Test::testF_S, Test::changeF);
    }

    @org.junit.Test
    public void test55() throws Exception {
        testMatched(Test::testF_C, Test::changeF);
    }

    @org.junit.Test
    public void test56() throws Exception {
        testMatched(Test::testF_I, Test::changeF);
    }

    @org.junit.Test
    public void test57() throws Exception {
        testMatched(Test::testF_J, Test::changeF);
    }

    @org.junit.Test
    public void test58() throws Exception {
        testMatched(Test::testF_F, Test::changeF);
    }

    @org.junit.Test
    public void test59() throws Exception {
        testMatched(Test::testF_D, Test::changeF);
    }

    @org.junit.Test
    public void test60() throws Exception {
        testMatched(Test::testD_Z, Test::changeD);
    }

    @org.junit.Test
    public void test61() throws Exception {
        testMatched(Test::testD_B, Test::changeD);
    }

    @org.junit.Test
    public void test62() throws Exception {
        testMatched(Test::testD_S, Test::changeD);
    }

    @org.junit.Test
    public void test63() throws Exception {
        testMatched(Test::testD_C, Test::changeD);
    }

    @org.junit.Test
    public void test64() throws Exception {
        testMatched(Test::testD_I, Test::changeD);
    }

    @org.junit.Test
    public void test65() throws Exception {
        testMatched(Test::testD_J, Test::changeD);
    }

    @org.junit.Test
    public void test66() throws Exception {
        testMatched(Test::testD_F, Test::changeD);
    }

    @org.junit.Test
    public void test67() throws Exception {
        testMatched(Test::testD_D, Test::changeD);
    }

    @org.junit.Test
    public void test68() throws Exception {
        testMatched(Test::testL_L, Test::changeL);
    }

    @org.junit.Test
    public void test69() throws Exception {
        testMismatched(Test::testL_J, Test::changeL); // long & double are always as large as an OOP
    }

    @org.junit.Test
    public void test70() throws Exception {
        testMismatched(Test::testL_D, Test::changeL);
    }

    @org.junit.Test
    public void test71() throws Exception {
        testMismatched(Test::testS_U, Test::changeS);
    }

    @org.junit.Test
    public void test72() throws Exception {
        testMismatched(Test::testC_U, Test::changeC);
    }

    @org.junit.Test
    public void test73() throws Exception {
        testMismatched(Test::testI_U, Test::changeI);
    }

    @org.junit.Test
    public void test74() throws Exception {
        testMismatched(Test::testJ_U, Test::changeJ);
    }
}
