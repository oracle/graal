/*
 * Copyright (c) 2014, 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.jtt.except;

import static java.lang.classfile.ClassFile.ACC_PUBLIC;
import static java.lang.constant.ConstantDescs.CD_Object;
import static java.lang.constant.ConstantDescs.CD_int;
import static java.lang.constant.ConstantDescs.INIT_NAME;
import static java.lang.constant.ConstantDescs.MTD_void;

import java.lang.classfile.ClassFile;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;

import org.junit.BeforeClass;
import org.junit.Test;

import jdk.graal.compiler.core.test.CustomizedBytecodePattern;
import jdk.graal.compiler.jtt.JTTTest;

public class UntrustedInterfaces extends JTTTest {

    public interface CallBack {
        int callBack(TestInterface ti);
    }

    private interface TestInterface {
        int method();
    }

    /**
     * What a GoodPill would look like.
     *
     * <pre>
     * private static final class GoodPill extends Pill {
     *     public void setField() {
     *         field = new TestConstant();
     *     }
     *
     *     public void setStaticField() {
     *         staticField = new TestConstant();
     *     }
     *
     *     public int callMe(CallBack callback) {
     *         return callback.callBack(new TestConstant());
     *     }
     *
     *     public TestInterface get() {
     *         return new TestConstant();
     *     }
     * }
     *
     * private static final class TestConstant implements TestInterface {
     *     public int method() {
     *         return 42;
     *     }
     * }
     * </pre>
     */
    public abstract static class Pill {
        public static TestInterface staticField;
        public TestInterface field;

        public abstract void setField();

        public abstract void setStaticField();

        public abstract int callMe(CallBack callback);

        public abstract TestInterface get();
    }

    public int callBack(TestInterface list) {
        return list.method();
    }

    public int staticFieldInvoke(Pill pill) {
        pill.setStaticField();
        return Pill.staticField.method();
    }

    public int fieldInvoke(Pill pill) {
        pill.setField();
        return pill.field.method();
    }

    public int argumentInvoke(Pill pill) {
        return pill.callMe(ti -> ti.method());
    }

    public int returnInvoke(Pill pill) {
        return pill.get().method();
    }

    @SuppressWarnings("cast")
    public boolean staticFieldInstanceof(Pill pill) {
        pill.setStaticField();
        return Pill.staticField instanceof TestInterface;
    }

    @SuppressWarnings("cast")
    public boolean fieldInstanceof(Pill pill) {
        pill.setField();
        return pill.field instanceof TestInterface;
    }

    @SuppressWarnings("cast")
    public int argumentInstanceof(Pill pill) {
        return pill.callMe(ti -> ti instanceof TestInterface ? 42 : 24);
    }

    @SuppressWarnings("cast")
    public boolean returnInstanceof(Pill pill) {
        return pill.get() instanceof TestInterface;
    }

    public TestInterface staticFieldCheckcast(Pill pill) {
        pill.setStaticField();
        return TestInterface.class.cast(Pill.staticField);
    }

    public TestInterface fieldCheckcast(Pill pill) {
        pill.setField();
        return TestInterface.class.cast(pill.field);
    }

    public int argumentCheckcast(Pill pill) {
        return pill.callMe(ti -> TestInterface.class.cast(ti).method());
    }

    public TestInterface returnCheckcast(Pill pill) {
        return TestInterface.class.cast(pill.get());
    }

    private static Pill poisonPill;

    // Checkstyle: stop
    @BeforeClass
    public static void setUp() throws Exception {
        poisonPill = (Pill) new PoisonLoader().getClass(PoisonLoader.POISON_IMPL_NAME).getDeclaredConstructor().newInstance();
    }

    // Checkstyle: resume

    @Test
    public void testStaticField0() {
        runTest("staticFieldInvoke", poisonPill);
    }

    @Test
    public void testStaticField1() {
        runTest("staticFieldInstanceof", poisonPill);
    }

    @Test
    public void testStaticField2() {
        runTest("staticFieldCheckcast", poisonPill);
    }

    @Test
    public void testField0() {
        runTest("fieldInvoke", poisonPill);
    }

    @Test
    public void testField1() {
        runTest("fieldInstanceof", poisonPill);
    }

    @Test
    public void testField2() {
        runTest("fieldCheckcast", poisonPill);
    }

    @Test
    public void testArgument0() {
        runTest("argumentInvoke", poisonPill);
    }

    @Test
    public void testArgument1() {
        runTest("argumentInstanceof", poisonPill);
    }

    @Test
    public void testArgument2() {
        runTest("argumentCheckcast", poisonPill);
    }

    @Test
    public void testReturn0() {
        runTest("returnInvoke", poisonPill);
    }

    @Test
    public void testReturn1() {
        runTest("returnInstanceof", poisonPill);
    }

    @Test
    public void testReturn2() {
        runTest("returnCheckcast", poisonPill);
    }

    private static final class PoisonLoader implements CustomizedBytecodePattern {

        public static final String POISON_IMPL_NAME = "jdk.graal.compiler.jtt.except.PoisonPill";

        @Override
        public byte[] generateClass(String className) {
            ClassDesc classPill = cd(Pill.class);
            ClassDesc classTestInterface = cd(TestInterface.class);
            // @formatter:off
            return ClassFile.of().build(ClassDesc.of(className), classBuilder -> classBuilder
                            .withSuperclass(classPill)
                            .withMethodBody(INIT_NAME, MTD_void, ACC_PUBLIC, b -> b
                                            .aload(0)
                                            .invokespecial(classPill, INIT_NAME, MTD_void)
                                            .return_())
                            .withMethodBody("setField", MTD_void, ACC_PUBLIC, b -> b
                                            .aload(0)
                                            .new_(CD_Object)
                                            .dup()
                                            .invokespecial(CD_Object, INIT_NAME, MTD_void)
                                            .putfield(classPill, "field", classTestInterface)
                                            .return_())
                            .withMethodBody("setStaticField", MTD_void, ACC_PUBLIC, b -> b
                                            .new_(CD_Object)
                                            .dup()
                                            .invokespecial(CD_Object, INIT_NAME, MTD_void)
                                            .putstatic(classPill, "staticField", classTestInterface)
                                            .return_())
                            .withMethodBody("callMe", MethodTypeDesc.of(CD_int, cd(CallBack.class)), ACC_PUBLIC, b -> b
                                            .aload(1)
                                            .new_(CD_Object)
                                            .dup()
                                            .invokespecial(CD_Object, INIT_NAME, MTD_void)
                                            .invokeinterface(cd(CallBack.class), "callBack", MethodTypeDesc.of(CD_int, classTestInterface))
                                            .ireturn())
                            .withMethodBody("get", MethodTypeDesc.of(classTestInterface), ACC_PUBLIC, b -> b
                                            .new_(CD_Object)
                                            .dup()
                                            .invokespecial(CD_Object, INIT_NAME, MTD_void)
                                            .areturn()));
            // @formatter:on
        }
    }
}
