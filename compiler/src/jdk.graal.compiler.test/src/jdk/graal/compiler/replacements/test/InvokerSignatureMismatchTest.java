/*
 * Copyright (c) 2019, 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.replacements.test;

import static java.lang.classfile.ClassFile.ACC_FINAL;
import static java.lang.classfile.ClassFile.ACC_STATIC;
import static java.lang.constant.ConstantDescs.CD_Class;
import static java.lang.constant.ConstantDescs.CD_Exception;
import static java.lang.constant.ConstantDescs.CD_Integer;
import static java.lang.constant.ConstantDescs.CD_MethodHandle;
import static java.lang.constant.ConstantDescs.CD_MethodHandles;
import static java.lang.constant.ConstantDescs.CD_MethodHandles_Lookup;
import static java.lang.constant.ConstantDescs.CD_MethodType;
import static java.lang.constant.ConstantDescs.CD_Object;
import static java.lang.constant.ConstantDescs.CD_String;
import static java.lang.constant.ConstantDescs.CD_float;
import static java.lang.constant.ConstantDescs.CD_int;
import static java.lang.constant.ConstantDescs.CD_void;
import static java.lang.constant.ConstantDescs.CLASS_INIT_NAME;
import static java.lang.constant.ConstantDescs.MTD_void;

import java.lang.classfile.Annotation;
import java.lang.classfile.ClassFile;
import java.lang.classfile.attribute.ExceptionsAttribute;
import java.lang.classfile.attribute.RuntimeVisibleAnnotationsAttribute;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.reflect.Field;

import org.junit.Test;

import jdk.graal.compiler.core.test.CustomizedBytecodePattern;
import jdk.graal.compiler.core.test.GraalCompilerTest;
import jdk.graal.compiler.test.AddExports;

@AddExports({"java.base/java.lang", "java.base/java.lang.invoke"})
public class InvokerSignatureMismatchTest extends GraalCompilerTest {

    static String helperClassName = "java.lang.invoke.MethodHandleHelper";

    @Test
    public void testInvokeSignatureMismatch() throws Exception {
        Field trustedLookupField = Lookup.class.getDeclaredField("IMPL_LOOKUP");
        trustedLookupField.setAccessible(true);
        Lookup trustedLookup = (Lookup) trustedLookupField.get(null);

        new MethodHandleHelperGen().lookupClass(trustedLookup.in(MethodHandles.class), helperClassName);
        Class<?> testClass = new ISMTestGen().getClass(InvokerSignatureMismatchTest.class.getName() + "$" + "ISMTest");
        test(getResolvedJavaMethod(testClass, "main"), null, new Object[]{new String[0]});
    }

    static class MethodHandleHelperGen implements CustomizedBytecodePattern {
        @Override
        public byte[] generateClass(String className) {
            // @formatter:off
            return ClassFile.of().build(ClassDesc.of(className), classBuilder -> classBuilder
                            .withMethod("internalMemberName", MethodTypeDesc.of(CD_Object, CD_MethodHandle), ACC_PUBLIC_STATIC, methodBuilder -> methodBuilder
                                            .with(ExceptionsAttribute.ofSymbols(CD_Exception))
                                            .withCode(b -> b
                                                            .aload(0)
                                                            .invokevirtual(CD_MethodHandle, "internalMemberName", MethodTypeDesc.of(ClassDesc.of("java.lang.invoke.MemberName")))
                                                            .areturn()))
                            .withMethod("linkToStatic", MethodTypeDesc.of(CD_int, CD_float, CD_Object), ACC_PUBLIC_STATIC, methodBuilder -> methodBuilder
                                            .with(ExceptionsAttribute.ofSymbols(CD_Exception))
                                            .withCode(b -> b
                                                            .fload(0)
                                                            .aload(1)
                                                            .invokestatic(CD_MethodHandle, "linkToStatic", MethodTypeDesc.of(CD_int, CD_float, CD_Object))
                                                            .ireturn()))
                            .withMethod("invokeBasicI", MethodTypeDesc.of(CD_int, CD_MethodHandle, CD_float), ACC_PUBLIC_STATIC, methodBuilder -> methodBuilder
                                            .with(ExceptionsAttribute.ofSymbols(CD_Exception))
                                            .withCode(b -> b
                                                            .aload(0)
                                                            .fload(1)
                                                            .invokevirtual(CD_MethodHandle, "invokeBasic", MethodTypeDesc.of(CD_int, CD_float))
                                                            .ireturn())));
            // @formatter:on
        }
    }

    static class ISMTestGen implements CustomizedBytecodePattern {
        @Override
        public byte[] generateClass(String className) {
            ClassDesc thisClass = ClassDesc.of(className);
            ClassDesc methodHandleHelper = ClassDesc.of(helperClassName);

            // @formatter:off
            return ClassFile.of().build(thisClass, classBuilder -> classBuilder
                            .withField("INT_MH", CD_MethodHandle, fieldBuilder -> fieldBuilder
                                            .withFlags(ACC_FINAL | ACC_STATIC)
                                            .with(RuntimeVisibleAnnotationsAttribute.of(Annotation.of(ClassDesc.of("jdk.internal.vm.annotation.Stable")))))
                            .withMethod(CLASS_INIT_NAME, MTD_void, ACC_STATIC, methodBuilder -> methodBuilder
                                            .withCode(b -> b
                                                            .aconst_null()
                                                            .astore(0)
                                                            .invokestatic(CD_MethodHandles, "lookup", MethodTypeDesc.of(CD_MethodHandles_Lookup))
                                                            .ldc(thisClass)
                                                            .ldc("bodyI")
                                                            .getstatic(CD_Integer, "TYPE", CD_Class)
                                                            .getstatic(CD_Integer, "TYPE", CD_Class)
                                                            .invokestatic(CD_MethodType, "methodType", MethodTypeDesc.of(CD_MethodType, CD_Class, CD_Class))
                                                            .invokevirtual(CD_MethodHandles_Lookup, "findStatic", MethodTypeDesc.of(CD_MethodHandle, CD_Class, CD_String, CD_MethodType))
                                                            .putstatic(thisClass, "INT_MH", CD_MethodHandle)
                                                            .return_()))
                            .withMethod("mainLink", MethodTypeDesc.of(CD_int, CD_int), ACC_PUBLIC_STATIC, methodBuilder -> methodBuilder
                                            .with(ExceptionsAttribute.ofSymbols(CD_Exception))
                                            .withCode(b -> b
                                                            .getstatic(thisClass, "INT_MH", CD_MethodHandle)
                                                            .invokestatic(methodHandleHelper, "internalMemberName", MethodTypeDesc.of(CD_Object, CD_MethodHandle))
                                                            .astore(1)
                                                            .iload(0)
                                                            .i2f()
                                                            .aload(1)
                                                            .invokestatic(methodHandleHelper, "linkToStatic", MethodTypeDesc.of(CD_int, CD_float, CD_Object))
                                                            .ireturn()))
                            .withMethod("mainInvoke", MethodTypeDesc.of(CD_int, CD_int), ACC_PUBLIC_STATIC, methodBuilder -> methodBuilder
                                            .with(ExceptionsAttribute.ofSymbols(CD_Exception))
                                            .withCode(b -> b
                                                            .getstatic(thisClass, "INT_MH", CD_MethodHandle)
                                                            .iload(0)
                                                            .i2f()
                                                            .invokestatic(methodHandleHelper, "invokeBasicI", MethodTypeDesc.of(CD_int, CD_MethodHandle, CD_float))
                                                            .ireturn()))
                            .withMethod("bodyI", MethodTypeDesc.of(CD_int, CD_int), ACC_PUBLIC_STATIC, methodBuilder -> methodBuilder
                                            .withCode(b -> b
                                                            .iload(0)
                                                            .sipush(1023)
                                                            .iand()
                                                            .ireturn()))
                            .withMethod("main", MethodTypeDesc.of(CD_void, CD_String.arrayType()), ACC_PUBLIC_STATIC, methodBuilder -> methodBuilder
                                            .withCode(b -> b
                                                            .sipush(100)
                                                            .invokestatic(thisClass, "mainLink", MethodTypeDesc.of(CD_int, CD_int))
                                                            .pop()
                                                            .sipush(100)
                                                            .invokestatic(thisClass, "mainInvoke", MethodTypeDesc.of(CD_int, CD_int))
                                                            .pop()
                                                            .return_())));
            // @formatter:on
        }
    }
}
