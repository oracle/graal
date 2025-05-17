/*
 * Copyright (c) 2018, 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.classfile.ClassFile;
import java.lang.classfile.CodeBuilder;
import java.lang.classfile.TypeKind;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.DirectMethodHandleDesc.Kind;
import java.lang.constant.DynamicConstantDesc;
import java.lang.constant.MethodHandleDesc;
import java.lang.constant.MethodTypeDesc;
import java.lang.invoke.MethodHandles;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.Assume;
import org.junit.Test;

import jdk.vm.ci.meta.ConstantPool;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Tests support for Dynamic constants.
 *
 * @see "https://openjdk.java.net/jeps/309"
 * @see "https://bugs.openjdk.java.net/browse/JDK-8177279"
 */
public class DynamicConstantTest extends GraalCompilerTest {
    /**
     * Map of test class generators keyed by internal class name.
     */
    private final Map<String, TestGenerator> generators = new LinkedHashMap<>();

    private void add(TestGenerator gen) {
        generators.put(gen.taregtClassName.replace('.', '/'), gen);
    }

    enum CondyType {
        /**
         * Condy whose bootstrap method is one of the {@code TestDynamicConstant.get<type>BSM()}
         * methods.
         */
        CALL_DIRECT_BSM,

        /**
         * Condy whose bootstrap method is {@code ConstantBootstraps.invoke} that invokes one of the
         * {@code TestDynamicConstant.get<type>()} methods.
         */
        CALL_INDIRECT_BSM,

        /**
         * Condy whose bootstrap method is {@code ConstantBootstraps.invoke} that invokes one of the
         * {@code TestDynamicConstant.get<type>(<type> p1, <type> p2)} methods with args that are
         * condys themselves.
         */
        CALL_INDIRECT_WITH_ARGS_BSM
    }

    /**
     * Generates a class with a static {@code run} method that returns a value loaded from
     * CONSTANT_Dynamic constant pool entry.
     */
    static class TestGenerator implements CustomizedBytecodePattern {
        /**
         * Type of value returned by the generated {@code run} method.
         */
        final ClassDesc type;

        /**
         * Type of condy used to produce the returned value.
         */
        final CondyType condyType;

        /**
         * Base name of the static {@code TestDynamicConstant.get<type>} method(s) invoked from
         * condys in the generated class.
         */
        final String getter;

        /**
         * Name of the generated class.
         */
        final String taregtClassName;

        TestGenerator(Class<?> type, CondyType condyType) {
            String typeName = type.getSimpleName();
            this.type = cd(type);
            this.condyType = condyType;
            this.getter = "get" + typeName.substring(0, 1).toUpperCase() + typeName.substring(1);
            this.taregtClassName = DynamicConstantTest.class.getName() + "$" + typeName + '_' + condyType;
        }

        @Override
        public byte[] generateClass(String className) {
            // @formatter:off
            return ClassFile.of().build(ClassDesc.of(className), classBuilder -> classBuilder
                            .withMethodBody("run", MethodTypeDesc.of(type), ACC_PUBLIC_STATIC, this::generate));
            // @formatter:on
        }

        private void generate(CodeBuilder b) {
            // @formatter:off
            // Object ConstantBootstraps.invoke(MethodHandles.Lookup lookup, String name, Class<?> type, MethodHandle handle, Object... args)
            ClassDesc outerClass = cd(DynamicConstantTest.class);
            String desc = type.descriptorString();

            if (condyType == CondyType.CALL_DIRECT_BSM) {
                // Example: int DynamicConstantTest.getIntBSM(MethodHandles.Lookup l, String name,
                // Class<?> type)
                String sig = "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/Class;)" + desc;
                var condy = DynamicConstantDesc.ofNamed(MethodHandleDesc.of(Kind.STATIC, outerClass, getter + "BSM", sig), "const", type);
                b.ldc(condy).return_(TypeKind.from(type));
            } else if (condyType == CondyType.CALL_INDIRECT_BSM) {
                // Example: int DynamicConstantTest.getInt()
                var condy = DynamicConstantDesc.ofNamed(ConstantDescs.BSM_INVOKE, "const", type, MethodHandleDesc.of(Kind.STATIC, outerClass, getter, "()" + desc));
                b.ldc(condy).return_(TypeKind.from(type));
            } else {
                assert condyType == CondyType.CALL_INDIRECT_WITH_ARGS_BSM;
                // Example: int DynamicConstantTest.getInt()
                var condy1 = DynamicConstantDesc.ofNamed(ConstantDescs.BSM_INVOKE, "const1", type, MethodHandleDesc.of(Kind.STATIC, outerClass, getter, "()" + desc));
                // Example: int DynamicConstantTest.getInt(int v1, int v2)
                var condy2 = DynamicConstantDesc.ofNamed(ConstantDescs.BSM_INVOKE, "const2", type, MethodHandleDesc.of(Kind.STATIC, outerClass, getter, "(" + desc + desc + ")" + desc), condy1,
                                condy1);
                b.ldc(condy2).return_(TypeKind.from(type));
            }
            // @formatter:on
        }
    }

    public DynamicConstantTest() {
        Class<?>[] types = {
                        boolean.class,
                        byte.class,
                        short.class,
                        char.class,
                        int.class,
                        float.class,
                        long.class,
                        double.class,
                        String.class,
                        List.class
        };
        for (Class<?> c : types) {
            for (CondyType condyType : CondyType.values()) {
                add(new TestGenerator(c, condyType));
            }
        }
    }

    // @formatter:off
    @SuppressWarnings("unused") public static boolean getBooleanBSM(MethodHandles.Lookup l, String name, Class<?> type) { return true; }
    @SuppressWarnings("unused") public static char    getCharBSM   (MethodHandles.Lookup l, String name, Class<?> type) { return '*'; }
    @SuppressWarnings("unused") public static short   getShortBSM  (MethodHandles.Lookup l, String name, Class<?> type) { return Short.MAX_VALUE; }
    @SuppressWarnings("unused") public static byte    getByteBSM   (MethodHandles.Lookup l, String name, Class<?> type) { return Byte.MAX_VALUE; }
    @SuppressWarnings("unused") public static int     getIntBSM    (MethodHandles.Lookup l, String name, Class<?> type) { return Integer.MAX_VALUE; }
    @SuppressWarnings("unused") public static float   getFloatBSM  (MethodHandles.Lookup l, String name, Class<?> type) { return Float.MAX_VALUE; }
    @SuppressWarnings("unused") public static long    getLongBSM   (MethodHandles.Lookup l, String name, Class<?> type) { return Long.MAX_VALUE; }
    @SuppressWarnings("unused") public static double  getDoubleBSM (MethodHandles.Lookup l, String name, Class<?> type) { return Double.MAX_VALUE; }
    @SuppressWarnings("unused") public static String  getStringBSM (MethodHandles.Lookup l, String name, Class<?> type) { return "a string"; }
    @SuppressWarnings("unused") public static List<?> getListBSM   (MethodHandles.Lookup l, String name, Class<?> type) { return Arrays.asList("element"); }


    public static boolean getBoolean() { return true; }
    public static char    getChar   () { return '*'; }
    public static short   getShort  () { return Short.MAX_VALUE; }
    public static byte    getByte   () { return Byte.MAX_VALUE; }
    public static int     getInt    () { return Integer.MAX_VALUE; }
    public static float   getFloat  () { return Float.MAX_VALUE; }
    public static long    getLong   () { return Long.MAX_VALUE; }
    public static double  getDouble () { return Double.MAX_VALUE; }
    public static String  getString () { return "a string"; }
    public static List<?> getList   () { return Arrays.asList("element"); }

    public static boolean getBoolean(boolean v1, boolean v2) { return v1 || v2; }
    public static char    getChar   (char v1, char v2)       { return (char)(v1 ^ v2); }
    public static short   getShort  (short v1, short v2)     { return (short)(v1 ^ v2); }
    public static byte    getByte   (byte v1,   byte v2)     { return (byte)(v1 ^ v2); }
    public static int     getInt    (int v1, int v2)         { return v1 ^ v2; }
    public static float   getFloat  (float v1, float v2)     { return v1 * v2; }
    public static long    getLong   (long v1, long v2)       { return v1 ^ v2; }
    public static double  getDouble (double v1, double v2)   { return v1 * v2; }
    public static String  getString (String v1, String v2)   { return v1 + v2; }
    public static List<?> getList   (List<?> v1, List<?> v2) { return Arrays.asList(v1, v2); }
    // @formatter:on

    private static final boolean VERBOSE = Boolean.getBoolean(DynamicConstantTest.class.getSimpleName() + ".verbose");

    @Test
    public void test() throws Throwable {
        boolean jvmciCompatibilityChecked = false;
        for (TestGenerator e : generators.values()) {
            Class<?> testClass = e.getClass(e.taregtClassName);
            ResolvedJavaMethod run = getResolvedJavaMethod(testClass, "run");
            if (!jvmciCompatibilityChecked) {
                checkJVMCICompatibility(run);
                jvmciCompatibilityChecked = true;
            }
            Result actual = executeActual(run, null);
            if (VERBOSE) {
                System.out.println(run.format("%H.%n(%p)") + " -> " + actual);
            }
            test(run, null);
        }
    }

    private static void checkJVMCICompatibility(ResolvedJavaMethod run) {
        ConstantPool cp = run.getConstantPool();
        for (int i = 0; i < cp.length(); i++) {
            try {
                cp.lookupConstant(i);
            } catch (Throwable t) {
                Assume.assumeFalse("running on JVMCI that does not support CONSTANT_Dynamic", String.valueOf(t).contains("Unknown JvmConstant tag 17"));
            }
        }
    }
}
