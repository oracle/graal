/*
 * Copyright (c) 2015, 2025, Oracle and/or its affiliates. All rights reserved.
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

import static java.lang.classfile.ClassFile.ACC_PRIVATE;
import static java.lang.classfile.ClassFile.ACC_PUBLIC;
import static java.lang.constant.ConstantDescs.CD_Object;
import static java.lang.constant.ConstantDescs.CD_int;
import static java.lang.constant.ConstantDescs.INIT_NAME;
import static java.lang.constant.ConstantDescs.MTD_void;

import java.lang.classfile.ClassFile;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import org.junit.Test;

import jdk.graal.compiler.code.CompilationResult;
import jdk.graal.compiler.debug.DebugContext;
import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public final class InterfaceMethodHandleTest extends GraalCompilerTest implements CustomizedBytecodePattern {
    private static final MethodHandle INTERFACE_HANDLE_M;
    private static final MethodHandle INTERFACE_HANDLE_M2;

    public interface I {
        int m();

        int m2(int a, int b, int c, int d, int e, int f, int g, int h, int i, int j);
    }

    static class A implements I {
        @Override
        public int m() {
            return 0;
        }

        @Override
        public int m2(int a, int b, int c, int d, int e, int f, int g, int h, int i, int j) {
            return 1;
        }

    }

    static class M2Thrower implements I {
        @Override
        public int m() {
            return 0;
        }

        @Override
        public int m2(int a, int b, int c, int d, int e, int f, int g, int h, int i, int j) {
            throw new InternalError();
        }

    }

    static {
        try {
            MethodType type = MethodType.fromMethodDescriptorString("()I", I.class.getClassLoader());
            INTERFACE_HANDLE_M = MethodHandles.lookup().findVirtual(I.class, "m", type);
            MethodType type2 = MethodType.fromMethodDescriptorString("(IIIIIIIIII)I", I.class.getClassLoader());
            INTERFACE_HANDLE_M2 = MethodHandles.lookup().findVirtual(I.class, "m2", type2);
        } catch (IllegalAccessException | NoSuchMethodException e) {
            throw new RuntimeException("unable to initialize method handle", e);
        }
    }

    public static Object invokeInterfaceHandle(I o) throws Throwable {
        return (int) INTERFACE_HANDLE_M.invokeExact(o);
    }

    @Test
    public void testInvokeInterface01() {
        test("invokeInterfaceHandle", new A());

    }

    @Test
    public void testInvokeInterface02() throws Exception {
        test("invokeInterfaceHandle", getClass(NAME).getDeclaredConstructor().newInstance());
    }

    public static Object invokeInterfaceHandle2(I o, int a, int b, int c, int d, int e, int f, int g, int h, int i, int j) throws Throwable {
        return (int) INTERFACE_HANDLE_M2.invokeExact(o, a, b, c, d, e, f, g, h, i, j);
    }

    @Override
    protected InstalledCode addMethod(DebugContext debug, ResolvedJavaMethod method, CompilationResult compResult) {
        if (method.getDeclaringClass().equals(getMetaAccess().lookupJavaType(M2Thrower.class))) {
            // Make sure M2Thrower.m2 is invoked from normal code
            return getBackend().createDefaultInstalledCode(debug, method, compResult);
        }
        return super.addMethod(debug, method, compResult);
    }

    /**
     * Try to exercise a mixed calling sequence with regular JIT code calling a method handle that
     * can't be inlined with an implementation compiled by Graal that throws an exception.
     */
    @Test
    public void testInvokeInterface03() throws Throwable {
        A goodInstance = new A();
        I badInstance = new M2Thrower();
        getCode(getMetaAccess().lookupJavaMethod(getMethod(M2Thrower.class, "m2")));
        for (int x = 0; x < 1000; x++) {
            final int limit = 20000;
            for (int i = 0; i <= limit; i++) {
                try {
                    invokeInterfaceHandle2(i < limit - 1 ? goodInstance : badInstance, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
                } catch (InternalError e) {

                }
            }
        }
    }

    private static final String BASENAME = InterfaceMethodHandleTest.class.getName();
    private static final String NAME = BASENAME + "_B";

    @Override
    public byte[] generateClass(String className) {
        // @formatter:off
        return ClassFile.of().build(ClassDesc.of(className), classBuilder -> classBuilder
                        .withInterfaceSymbols(cd(I.class))
                        .withMethodBody(INIT_NAME, MTD_void, ACC_PUBLIC, b -> b
                                        .aload(0)
                                        .invokespecial(CD_Object, INIT_NAME, MTD_void)
                                        .return_())
                        .withMethodBody("m", MethodTypeDesc.of(CD_int), ACC_PRIVATE, b -> b
                                        .iconst_0()
                                        .ireturn())
                        .withMethodBody("m2", MethodTypeDesc.of(CD_int, CD_int, CD_int, CD_int, CD_int, CD_int, CD_int, CD_int, CD_int, CD_int, CD_int), ACC_PRIVATE, b -> b
                                        .iconst_0()
                                        .ireturn()));
        // @formatter:on
    }
}
