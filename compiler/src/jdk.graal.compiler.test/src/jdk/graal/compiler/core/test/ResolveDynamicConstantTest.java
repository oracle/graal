/*
 * Copyright (c) 2023, 2025, Oracle and/or its affiliates. All rights reserved.
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

import static java.lang.classfile.ClassFile.ACC_VARARGS;
import static java.lang.constant.ConstantDescs.CD_MethodHandles_Lookup;
import static java.lang.constant.ConstantDescs.CD_Object;
import static java.lang.constant.ConstantDescs.CD_String;
import static java.lang.constant.ConstantDescs.CD_boolean;
import static java.lang.constant.ConstantDescs.CD_double;
import static java.lang.constant.ConstantDescs.CD_int;
import static java.lang.constant.ConstantDescs.CD_void;

import java.io.PrintStream;
import java.lang.classfile.ClassFile;
import java.lang.classfile.Label;
import java.lang.classfile.constantpool.ConstantPoolBuilder;
import java.lang.classfile.constantpool.FieldRefEntry;
import java.lang.constant.ClassDesc;
import java.lang.constant.DirectMethodHandleDesc.Kind;
import java.lang.constant.DynamicConstantDesc;
import java.lang.constant.MethodHandleDesc;
import java.lang.constant.MethodTypeDesc;
import java.time.Clock;
import java.time.Instant;

import org.junit.Assert;
import org.junit.Test;

import jdk.vm.ci.meta.ResolvedJavaMethod;

public class ResolveDynamicConstantTest extends GraalCompilerTest {

    @Test
    public void test00601m001() throws Throwable {
        runTest(new ResolveDynamicConstant00601m001Gen().getClass("test.resolveDynamicConstant00601m001"));
    }

    @Test
    public void test00602m008() throws Throwable {
        runTest(new ResolveDynamicConstant00602m008Gen().getClass("test.resolveDynamicConstant00602m008"));
    }

    public static void main(String[] args) {
        for (int i = 0; i < 10_000_000; i++) {
            Instant expected = Instant.now(Clock.systemUTC());
            Instant test = Instant.now();
            long diff = Math.abs(test.toEpochMilli() - expected.toEpochMilli());
            if (diff >= 100) {
                System.out.printf("%d: %d%n", i, diff);
            }
        }
    }

    static class ResolveDynamicConstant00601m001Gen implements CustomizedBytecodePattern {
        @Override
        public byte[] generateClass(String className) {
            ClassDesc thisClass = ClassDesc.of(className);
            FieldRefEntry field = ConstantPoolBuilder.of().fieldRefEntry(thisClass, "bsmInvocationCount", CD_int);

            // @formatter:off
            return ClassFile.of().build(thisClass, classBuilder -> classBuilder
                            .withField("bsmInvocationCount", CD_int, ACC_PUBLIC_STATIC)
                            .withMethodBody("run", MethodTypeDesc.of(CD_boolean), ACC_PUBLIC_STATIC, b -> {
                                Label labelFalse = b.newLabel();
                                var iconst = DynamicConstantDesc.ofNamed(MethodHandleDesc.of(Kind.STATIC, thisClass, "getConstant",
                                                "(Ljava/lang/invoke/MethodHandles$Lookup;[Ljava/lang/Object;)I"), "constantdynamic", CD_int);

                                b
                                                .getstatic(field)
                                                .ifne(labelFalse)
                                                .ldc(iconst)
                                                .pop()
                                                .getstatic(field)
                                                .ifeq(labelFalse)
                                                .iconst_1()
                                                .ireturn()
                                                .labelBinding(labelFalse)
                                                .iconst_0()
                                                .ireturn();
                            })
                            .withMethodBody("getConstant", MethodTypeDesc.of(CD_int, CD_MethodHandles_Lookup, CD_Object.arrayType()), ACC_PUBLIC_STATIC | ACC_VARARGS, b -> b
                                            .getstatic(field)
                                            .iconst_1()
                                            .iadd()
                                            .putstatic(field)
                                            .iconst_1()
                                            .ireturn())
                            .withMethodBody("main", MethodTypeDesc.of(CD_void, CD_String.arrayType()), ACC_PUBLIC_STATIC, b -> b
                                            .getstatic(cd(System.class), "out", cd(PrintStream.class))
                                            .invokestatic(thisClass, "run", MethodTypeDesc.of(CD_boolean))
                                            .invokevirtual(cd(PrintStream.class), "println", MethodTypeDesc.of(CD_void, CD_boolean))
                                            .return_()));
            // @formatter:on
        }
    }

    static class ResolveDynamicConstant00602m008Gen implements CustomizedBytecodePattern {
        @Override
        public byte[] generateClass(String className) {
            ClassDesc thisClass = ClassDesc.of(className);
            FieldRefEntry field = ConstantPoolBuilder.of().fieldRefEntry(thisClass, "staticBSMInvocationCount", CD_int);

            // @formatter:off
            return ClassFile.of().build(thisClass, classBuilder -> classBuilder
                            .withField("staticBSMInvocationCount", CD_int, ACC_PUBLIC_STATIC)
                            .withMethodBody("run", MethodTypeDesc.of(CD_boolean), ACC_PUBLIC_STATIC, b -> {
                                Label labelFalse = b.newLabel();
                                var dconst = DynamicConstantDesc.ofNamed(MethodHandleDesc.of(Kind.STATIC, thisClass, "getStaticConstant",
                                                "(Ljava/lang/invoke/MethodHandles$Lookup;[Ljava/lang/Object;)D"), "constantdynamic", CD_double);
                                var iconst = DynamicConstantDesc.ofNamed(MethodHandleDesc.of(Kind.STATIC, thisClass, "getConstant",
                                                "(Ljava/lang/invoke/MethodHandles$Lookup;[Ljava/lang/Object;)I"), "constantdynamic", CD_int, dconst);

                                b
                                                .getstatic(field)
                                                .ifne(labelFalse)
                                                .ldc(iconst)
                                                .pop()
                                                .getstatic(field)
                                                .ldc(1)
                                                .if_icmpne(labelFalse)
                                                .iconst_1()
                                                .ireturn()
                                                .labelBinding(labelFalse)
                                                .iconst_0()
                                                .ireturn();
                            })
                            .withMethodBody("getConstant", MethodTypeDesc.of(CD_int, CD_MethodHandles_Lookup, CD_Object.arrayType()), ACC_PUBLIC_STATIC | ACC_VARARGS, b -> b
                                            .iconst_1()
                                            .ireturn())
                            .withMethodBody("getStaticConstant", MethodTypeDesc.of(CD_double, CD_MethodHandles_Lookup, CD_Object.arrayType()), ACC_PUBLIC_STATIC | ACC_VARARGS, b -> b
                                            .getstatic(field)
                                            .iconst_1()
                                            .iadd()
                                            .putstatic(field)
                                            .dconst_1()
                                            .dreturn())
                            .withMethodBody("main", MethodTypeDesc.of(CD_void, CD_String.arrayType()), ACC_PUBLIC_STATIC, b -> b
                                            .getstatic(cd(System.class), "out", cd(PrintStream.class))
                                            .invokestatic(thisClass, "run", MethodTypeDesc.of(CD_boolean))
                                            .invokevirtual(cd(PrintStream.class), "println", MethodTypeDesc.of(CD_void, CD_boolean))
                                            .return_()));
            // @formatter:on
        }
    }

    private void runTest(Class<?> testClass) throws Throwable {
        ResolvedJavaMethod run = getResolvedJavaMethod(testClass, "run");
        Result actual = executeActual(run, null);
        if (actual.exception != null) {
            throw new AssertionError(actual.exception);
        }
        Assert.assertTrue((Boolean) actual.returnValue);
    }
}
