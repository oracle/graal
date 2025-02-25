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
import static java.lang.classfile.ClassFile.ACC_PUBLIC;
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
import static jdk.graal.compiler.core.test.CustomizedBytecodePattern.ACC_PUBLIC_STATIC;
import static jdk.graal.compiler.core.test.CustomizedBytecodePattern.MD_VOID;
import static jdk.graal.compiler.test.SubprocessUtil.getVMCommandLine;
import static jdk.graal.compiler.test.SubprocessUtil.withoutDebuggerArguments;

import java.lang.classfile.Annotation;
import java.lang.classfile.ClassFile;
import java.lang.classfile.attribute.ExceptionsAttribute;
import java.lang.classfile.attribute.RuntimeVisibleAnnotationsAttribute;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.junit.Test;

import jdk.graal.compiler.core.test.GraalCompilerTest;
import jdk.graal.compiler.test.SubprocessUtil;
import jdk.graal.compiler.test.SubprocessUtil.Subprocess;

public class InvokerSignatureMismatchTest extends GraalCompilerTest {

    @SuppressWarnings("try")
    @Test
    public void test() throws Throwable {
        List<String> args = withoutDebuggerArguments(getVMCommandLine());
        try (TemporaryDirectory temp = new TemporaryDirectory(getClass().getSimpleName())) {
            args.add("--class-path=" + temp);
            args.add("--patch-module=java.base=" + temp);
            args.add("-XX:-TieredCompilation");
            args.add("-XX:+UnlockExperimentalVMOptions");
            args.add("-XX:+EnableJVMCI");
            args.add("-XX:+UseJVMCICompiler");

            Path invokeDir = Files.createDirectories(temp.path.resolve(Paths.get("java", "lang", "invoke")));
            Files.write(temp.path.resolve("ISMTest.class"), generateISMTest());
            Files.write(invokeDir.resolve("MethodHandleHelper.class"), generateMethodHandleHelper());

            args.add("ISMTest");
            Subprocess proc = SubprocessUtil.java(args);
            if (proc.exitCode != 0) {
                throw new AssertionError(proc.toString());
            }
        }
    }

    private static byte[] generateMethodHandleHelper() {
        return ClassFile.of().build(ClassDesc.of("java.lang.invoke.MethodHandleHelper"), classBuilder -> classBuilder
                        .withMethod("internalMemberName", MethodTypeDesc.of(CD_Object, CD_MethodHandle), ACC_PUBLIC_STATIC, methodBuilder -> methodBuilder
                                        .with(ExceptionsAttribute.ofSymbols(CD_Exception))
                                        .withCode(codeBuilder -> codeBuilder
                                                        .aload(0)
                                                        .invokevirtual(CD_MethodHandle, "internalMemberName", MethodTypeDesc.of(ClassDesc.of("java.lang.invoke.MemberName")))
                                                        .areturn()))
                        .withMethod("linkToStatic", MethodTypeDesc.of(CD_int, CD_float, CD_Object), ACC_PUBLIC_STATIC, methodBuilder -> methodBuilder
                                        .with(ExceptionsAttribute.ofSymbols(CD_Exception))
                                        .withCode(codeBuilder -> codeBuilder
                                                        .fload(0)
                                                        .aload(1)
                                                        .invokestatic(CD_MethodHandle, "linkToStatic", MethodTypeDesc.of(CD_int, CD_float, CD_Object))
                                                        .ireturn()))
                        .withMethod("invokeBasicI", MethodTypeDesc.of(CD_int, CD_MethodHandle, CD_float), ACC_PUBLIC_STATIC, methodBuilder -> methodBuilder
                                        .with(ExceptionsAttribute.ofSymbols(CD_Exception))
                                        .withCode(codeBuilder -> codeBuilder
                                                        .aload(0)
                                                        .fload(1)
                                                        .invokevirtual(CD_MethodHandle, "invokeBasic", MethodTypeDesc.of(CD_int, CD_float))
                                                        .ireturn())));
    }

    private static byte[] generateISMTest() {
        ClassDesc thisClass = ClassDesc.of("ISMTest");
        ClassDesc methodHandleHelper = ClassDesc.of("java.lang.invoke.MethodHandleHelper");

        return ClassFile.of().build(thisClass, classBuilder -> classBuilder
                        .withField("INT_MH", CD_MethodHandle, fieldBuilder -> fieldBuilder
                                        .withFlags(ACC_FINAL | ACC_STATIC)
                                        .with(RuntimeVisibleAnnotationsAttribute.of(Annotation.of(ClassDesc.of("jdk.internal.vm.annotation.Stable")))))
                        .withMethod("<clinit>", MD_VOID, ACC_STATIC, methodBuilder -> methodBuilder
                                        .withCode(codeBuilder -> codeBuilder
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
                                        .withCode(codeBuilder -> codeBuilder
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
                                        .withCode(codeBuilder -> codeBuilder
                                                        .getstatic(thisClass, "INT_MH", CD_MethodHandle)
                                                        .iload(0)
                                                        .i2f()
                                                        .invokestatic(methodHandleHelper, "invokeBasicI", MethodTypeDesc.of(CD_int, CD_MethodHandle, CD_float))
                                                        .ireturn()))
                        .withMethod("bodyI", MethodTypeDesc.of(CD_int, CD_int), ACC_PUBLIC_STATIC, methodBuilder -> methodBuilder
                                        .withCode(codeBuilder -> codeBuilder
                                                        .iload(0)
                                                        .sipush(1023)
                                                        .iand()
                                                        .ireturn()))
                        .withMethod("main", MethodTypeDesc.of(CD_void, CD_String.arrayType()), ACC_PUBLIC_STATIC, methodBuilder -> methodBuilder
                                        .withCode(codeBuilder -> codeBuilder
                                                        .sipush(100)
                                                        .invokestatic(thisClass, "mainLink", MethodTypeDesc.of(CD_int, CD_int))
                                                        .pop()
                                                        .sipush(100)
                                                        .invokestatic(thisClass, "mainInvoke", MethodTypeDesc.of(CD_int, CD_int))
                                                        .pop()
                                                        .return_())));
    }
}
