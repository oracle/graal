/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.lambda;

import static java.lang.constant.ConstantDescs.CD_CallSite;
import static java.lang.constant.ConstantDescs.CD_MethodHandles_Lookup;
import static java.lang.constant.ConstantDescs.CD_MethodType;
import static java.lang.constant.ConstantDescs.CD_Object;
import static java.lang.constant.ConstantDescs.CD_String;
import static java.lang.constant.ConstantDescs.CD_void;

import java.lang.classfile.ClassFile;
import java.lang.constant.ClassDesc;
import java.lang.constant.DirectMethodHandleDesc;
import java.lang.constant.DynamicCallSiteDesc;
import java.lang.constant.MethodHandleDesc;
import java.lang.constant.MethodTypeDesc;
import java.lang.invoke.CallSite;
import java.lang.invoke.LambdaMetafactory;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import org.junit.Assert;
import org.junit.Test;

public class LambdaParserTest {

    @Test
    public void unresolvedInvokeDynamicSiteUsesFallback() throws Throwable {
        Class<?> hostClass = MethodHandles.lookup().defineClass(generateHostClass());
        MethodHandles.Lookup hostLookup = MethodHandles.privateLookupIn(hostClass, MethodHandles.lookup());
        MethodHandle target = hostLookup.findStatic(hostClass, "target", MethodType.methodType(void.class));
        CallSite callSite = LambdaMetafactory.metafactory(hostLookup, "run", MethodType.methodType(Runnable.class), MethodType.methodType(void.class), target,
                        MethodType.methodType(void.class));
        Runnable lambda = (Runnable) callSite.getTarget().invokeExact();

        Assert.assertNull(LambdaParser.findLambdaCaptureSite(lambda.getClass()));
    }

    private static byte[] generateHostClass() {
        ClassDesc hostClass = ClassDesc.of("com.oracle.svm.hosted.lambda.LambdaParserUnresolvedIndyHost");
        ClassDesc missingBootstrapClass = ClassDesc.of("com.oracle.svm.hosted.lambda.MissingBootstrap");
        MethodTypeDesc bootstrapMethodType = MethodTypeDesc.of(CD_CallSite, CD_MethodHandles_Lookup, CD_String, CD_MethodType);
        DirectMethodHandleDesc bootstrapMethod = MethodHandleDesc.ofMethod(DirectMethodHandleDesc.Kind.STATIC, missingBootstrapClass, "bootstrap", bootstrapMethodType);
        DynamicCallSiteDesc unresolvedCallSite = DynamicCallSiteDesc.of(bootstrapMethod, "unresolved", MethodTypeDesc.of(CD_Object));

        return ClassFile.of().build(hostClass, builder -> {
            builder.withVersion(ClassFile.JAVA_21_VERSION, 0);
            builder.withSuperclass(CD_Object);
            builder.withFlags(ClassFile.ACC_PUBLIC | ClassFile.ACC_SUPER);
            builder.withMethod("target", MethodTypeDesc.of(CD_void), ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC,
                            method -> method.withCode(code -> code.return_()));
            builder.withMethod("unresolved", MethodTypeDesc.of(CD_Object), ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC,
                            method -> method.withCode(code -> {
                                code.invokedynamic(unresolvedCallSite);
                                code.areturn();
                            }));
        });
    }
}
