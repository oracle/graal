/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2026, 2026, IBM Inc. All rights reserved.
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
package com.oracle.svm.test;

import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;

import org.graalvm.nativeimage.ImageInfo;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.hosted.RuntimeClassInitialization;
import org.graalvm.nativeimage.hosted.RuntimeReflection;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

import java.lang.classfile.ClassFile;
import java.lang.constant.ClassDesc;
import java.lang.constant.DirectMethodHandleDesc;
import java.lang.constant.DynamicCallSiteDesc;
import java.lang.constant.DynamicConstantDesc;
import java.lang.constant.MethodHandleDesc;
import java.lang.constant.MethodTypeDesc;

import com.oracle.svm.hosted.FeatureImpl;

import static java.lang.constant.ConstantDescs.CD_CallSite;
import static java.lang.constant.ConstantDescs.CD_Class;
import static java.lang.constant.ConstantDescs.CD_MethodHandles_Lookup;
import static java.lang.constant.ConstantDescs.CD_MethodType;
import static java.lang.constant.ConstantDescs.CD_Object;
import static java.lang.constant.ConstantDescs.CD_String;

/**
 * Tests the {@code registerBuildTimeIndyIncludeList} and {@code registerBuildTimeCondyIncludeList}
 * APIs by generating a class with ASM that uses custom bootstrap methods for invokedynamic and
 * constantdynamic, registering those bootstrap methods via the Feature API, and verifying that they
 * are resolved at build time.
 */
public class BootstrapMethodTest {

    static Class<?> generatedClass;
    static boolean indyBootstrapCalledAtBuildTime;
    static boolean condyBootstrapCalledAtBuildTime;
    static boolean runtimeIndyBootstrapCalledAtBuildTime;
    static boolean runtimeCondyBootstrapCalledAtBuildTime;

    @SuppressWarnings("unused")
    public static CallSite myIndyBootstrap(MethodHandles.Lookup lookup, String name, MethodType type) {
        if (ImageInfo.inImageBuildtimeCode()) {
            indyBootstrapCalledAtBuildTime = true;
        }
        MethodHandle mh = MethodHandles.constant(String.class, "indy-resolved");
        return new ConstantCallSite(mh.asType(type));
    }

    @SuppressWarnings("unused")
    public static Object myCondyBootstrap(MethodHandles.Lookup lookup, String name, Class<?> type) {
        if (ImageInfo.inImageBuildtimeCode()) {
            condyBootstrapCalledAtBuildTime = true;
        }
        return "condy-resolved";
    }

    @SuppressWarnings("unused")
    public static CallSite myRuntimeIndyBootstrap(MethodHandles.Lookup lookup, String name, MethodType type) {
        if (ImageInfo.inImageBuildtimeCode()) {
            runtimeIndyBootstrapCalledAtBuildTime = true;
        }
        MethodHandle mh = MethodHandles.constant(String.class, "runtime-indy-resolved");
        return new ConstantCallSite(mh.asType(type));
    }

    @SuppressWarnings("unused")
    public static Object myRuntimeCondyBootstrap(MethodHandles.Lookup lookup, String name, Class<?> type) {
        if (ImageInfo.inImageBuildtimeCode()) {
            runtimeCondyBootstrapCalledAtBuildTime = true;
        }
        return "runtime-condy-resolved";
    }

    public static class TestFeature implements Feature {
        @Override
        public void duringSetup(DuringSetupAccess access) {
            try {
                Method indyBsm = BootstrapMethodTest.class.getDeclaredMethod("myIndyBootstrap",
                                MethodHandles.Lookup.class, String.class, MethodType.class);
                Method condyBsm = BootstrapMethodTest.class.getDeclaredMethod("myCondyBootstrap",
                                MethodHandles.Lookup.class, String.class, Class.class);

                FeatureImpl.DuringSetupAccessImpl impl = (FeatureImpl.DuringSetupAccessImpl) access;
                impl.registerBuildTimeIndyIncludeList(indyBsm);
                impl.registerBuildTimeCondyIncludeList(condyBsm);
            } catch (NoSuchMethodException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public void beforeAnalysis(BeforeAnalysisAccess access) {
            RuntimeClassInitialization.initializeAtBuildTime(BootstrapMethodTest.class);

            byte[] classBytes = generateClassBytes();
            try {
                MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(
                                BootstrapMethodTest.class, MethodHandles.lookup());
                generatedClass = lookup.defineClass(classBytes);

                RuntimeReflection.register(generatedClass);
                for (Method m : generatedClass.getDeclaredMethods()) {
                    RuntimeReflection.register(m);
                }
            } catch (IllegalAccessException e) {
                throw new RuntimeException("Failed to define generated class", e);
            }
        }
    }

    static byte[] generateClassBytes() {
        ClassDesc testClassDesc = ClassDesc.of("com.oracle.svm.test.BootstrapMethodTest");
        ClassDesc generatedClassDesc = ClassDesc.of("com.oracle.svm.test.BootstrapMethodTestGenerated");

        MethodTypeDesc indyBsmDesc = MethodTypeDesc.of(CD_CallSite, CD_MethodHandles_Lookup, CD_String, CD_MethodType);
        DirectMethodHandleDesc indyBsmHandle = MethodHandleDesc.ofMethod(
                        DirectMethodHandleDesc.Kind.STATIC, testClassDesc, "myIndyBootstrap", indyBsmDesc);

        MethodTypeDesc condyBsmDesc = MethodTypeDesc.of(CD_Object, CD_MethodHandles_Lookup, CD_String, CD_Class);
        DirectMethodHandleDesc condyBsmHandle = MethodHandleDesc.ofMethod(
                        DirectMethodHandleDesc.Kind.STATIC, testClassDesc, "myCondyBootstrap", condyBsmDesc);

        DirectMethodHandleDesc runtimeIndyBsmHandle = MethodHandleDesc.ofMethod(
                        DirectMethodHandleDesc.Kind.STATIC, testClassDesc, "myRuntimeIndyBootstrap", indyBsmDesc);

        DirectMethodHandleDesc runtimeCondyBsmHandle = MethodHandleDesc.ofMethod(
                        DirectMethodHandleDesc.Kind.STATIC, testClassDesc, "myRuntimeCondyBootstrap", condyBsmDesc);

        MethodTypeDesc stringReturnDesc = MethodTypeDesc.of(CD_String);

        DynamicCallSiteDesc indyCallSite = DynamicCallSiteDesc.of(indyBsmHandle, "getValue", stringReturnDesc);
        DynamicConstantDesc<?> condyConst = DynamicConstantDesc.ofNamed(condyBsmHandle, "myConst", CD_String);
        DynamicCallSiteDesc runtimeIndyCallSite = DynamicCallSiteDesc.of(runtimeIndyBsmHandle, "getValue", stringReturnDesc);
        DynamicConstantDesc<?> runtimeCondyConst = DynamicConstantDesc.ofNamed(runtimeCondyBsmHandle, "myRuntimeConst", CD_String);

        return ClassFile.of().build(generatedClassDesc, cb -> {
            cb.withVersion(ClassFile.JAVA_21_VERSION, 0);
            cb.withSuperclass(CD_Object);
            cb.withFlags(ClassFile.ACC_PUBLIC | ClassFile.ACC_SUPER);

            cb.withMethod("callIndy", stringReturnDesc, ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC,
                            mb -> mb.withCode(code -> {
                                code.invokedynamic(indyCallSite);
                                code.areturn();
                            }));

            cb.withMethod("loadCondy", stringReturnDesc, ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC,
                            mb -> mb.withCode(code -> {
                                code.ldc(condyConst);
                                code.areturn();
                            }));

            cb.withMethod("callRuntimeIndy", stringReturnDesc, ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC,
                            mb -> mb.withCode(code -> {
                                code.invokedynamic(runtimeIndyCallSite);
                                code.areturn();
                            }));

            cb.withMethod("loadRuntimeCondy", stringReturnDesc, ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC,
                            mb -> mb.withCode(code -> {
                                code.ldc(runtimeCondyConst);
                                code.areturn();
                            }));
        });
    }

    @Test
    public void testIndyResolvedAtBuildTime() throws Exception {
        Assume.assumeTrue("Test only runs in native image", ImageInfo.inImageCode());
        Assert.assertNotNull("Generated class should be defined at build time", generatedClass);
        Assert.assertTrue("Indy bootstrap should have been called at build time",
                        indyBootstrapCalledAtBuildTime);

        Method callIndy = generatedClass.getDeclaredMethod("callIndy");
        String result = (String) callIndy.invoke(null);
        Assert.assertEquals("indy-resolved", result);
    }

    @Test
    public void testCondyResolvedAtBuildTime() throws Exception {
        Assume.assumeTrue("Test only runs in native image", ImageInfo.inImageCode());
        Assert.assertNotNull("Generated class should be defined at build time", generatedClass);
        Assert.assertTrue("Condy bootstrap should have been called at build time",
                        condyBootstrapCalledAtBuildTime);

        Method loadCondy = generatedClass.getDeclaredMethod("loadCondy");
        String result = (String) loadCondy.invoke(null);
        Assert.assertEquals("condy-resolved", result);
    }

    @Test
    public void testRuntimeIndyNotResolvedAtBuildTime() throws Exception {
        Assume.assumeTrue("Test only runs in native image", ImageInfo.inImageCode());
        Assert.assertNotNull("Generated class should be defined at build time", generatedClass);
        Assert.assertFalse("Runtime indy bootstrap should NOT have been called at build time",
                        runtimeIndyBootstrapCalledAtBuildTime);

        Method callRuntimeIndy = generatedClass.getDeclaredMethod("callRuntimeIndy");
        String result = (String) callRuntimeIndy.invoke(null);
        Assert.assertEquals("runtime-indy-resolved", result);
    }

    @Test
    public void testRuntimeCondyNotResolvedAtBuildTime() throws Exception {
        Assume.assumeTrue("Test only runs in native image", ImageInfo.inImageCode());
        Assert.assertNotNull("Generated class should be defined at build time", generatedClass);
        Assert.assertFalse("Runtime condy bootstrap should NOT have been called at build time",
                        runtimeCondyBootstrapCalledAtBuildTime);

        Method loadRuntimeCondy = generatedClass.getDeclaredMethod("loadRuntimeCondy");
        String result = (String) loadRuntimeCondy.invoke(null);
        Assert.assertEquals("runtime-condy-resolved", result);
    }
}
