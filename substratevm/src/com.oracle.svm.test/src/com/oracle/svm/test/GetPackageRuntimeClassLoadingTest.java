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
package com.oracle.svm.test;

import static java.lang.classfile.ClassFile.ACC_PUBLIC;
import static java.lang.classfile.ClassFile.ACC_STATIC;
import static java.lang.constant.ConstantDescs.CD_Object;
import static java.lang.constant.ConstantDescs.CD_String;
import static java.lang.constant.ConstantDescs.INIT_NAME;
import static java.lang.constant.ConstantDescs.MTD_void;

import java.lang.classfile.ClassFile;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.lang.reflect.Method;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;

import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.hosted.RuntimeReflection;
import org.junit.Assert;
import org.junit.Test;

@NativeImageBuildArgs({
                "-H:+RuntimeClassLoading",
                "--add-opens=java.base/jdk.internal.loader=ALL-UNNAMED",
                "--initialize-at-run-time=jdk.internal.loader.ClassLoaders",
                "--features=com.oracle.svm.test.GetPackageRuntimeClassLoadingTest$TestFeature"
})
public class GetPackageRuntimeClassLoadingTest {
    private static final String BOOT_APPEND_CLASS_NAME = "bootappend.BootAppendPackageClass";
    private static final String BOOT_APPEND_MARKER = "loaded from boot append path";

    public static class TestFeature implements Feature {
        @Override
        public void beforeAnalysis(BeforeAnalysisAccess access) {
            try {
                Class<?> classLoaders = access.findClassByName("jdk.internal.loader.ClassLoaders");
                Class<?> builtinClassLoader = access.findClassByName("jdk.internal.loader.BuiltinClassLoader");
                Class<?> urlClassPath = access.findClassByName("jdk.internal.loader.URLClassPath");
                RuntimeReflection.register(classLoaders.getDeclaredMethod("bootLoader"));
                RuntimeReflection.register(builtinClassLoader.getDeclaredMethod("appendClassPath", String.class));
                RuntimeReflection.register(builtinClassLoader.getDeclaredField("ucp"));
                RuntimeReflection.register(urlClassPath.getDeclaredConstructor(URL[].class));
            } catch (NoSuchFieldException | NoSuchMethodException e) {
                throw new AssertionError(e);
            }
        }
    }

    /**
     * Checks that packages become defined when runtime class loading loads a boot-append class.
     */
    @SuppressWarnings("deprecation")
    @Test
    public void testGetPackageForRuntimeDefinedBootClass() throws Exception {
        Assert.assertNull(Package.getPackage("bootappend"));

        byte[] classBytes = generateBootAppendClassBytes();
        Path bootAppendRoot = Files.createTempDirectory("gr36066-boot-append");
        Path packageDir = Files.createDirectory(bootAppendRoot.resolve("bootappend"));
        Files.write(packageDir.resolve("BootAppendPackageClass.class"), classBytes);

        appendBootClassPath(bootAppendRoot);
        Class<?> clazz = Class.forName(BOOT_APPEND_CLASS_NAME, true, null);

        Assert.assertNotNull(clazz);
        Assert.assertNull(clazz.getClassLoader());
        Assert.assertEquals(BOOT_APPEND_MARKER, clazz.getMethod("marker").invoke(null));
        Assert.assertNotNull(Package.getPackage("bootappend"));
    }

    private static byte[] generateBootAppendClassBytes() {
        ClassDesc bootAppendClass = ClassDesc.of(BOOT_APPEND_CLASS_NAME);
        // @formatter:off
        return ClassFile.of().build(bootAppendClass, classBuilder -> classBuilder
                        .withMethodBody(INIT_NAME, MTD_void, ACC_PUBLIC, b -> b
                                        .aload(0)
                                        .invokespecial(CD_Object, INIT_NAME, MTD_void)
                                        .return_())
                        .withMethodBody("marker", MethodTypeDesc.of(CD_String), ACC_PUBLIC | ACC_STATIC, b -> b
                                        .ldc(BOOT_APPEND_MARKER)
                                        .areturn()));
        // @formatter:on
    }

    private static void appendBootClassPath(Path entry) throws ReflectiveOperationException {
        Class<?> classLoadersClass = Class.forName("jdk.internal.loader.ClassLoaders");
        Method bootLoaderMethod = classLoadersClass.getDeclaredMethod("bootLoader");
        bootLoaderMethod.setAccessible(true);
        Object bootLoader = bootLoaderMethod.invoke(null);
        initializeBootClassPathIfNeeded(bootLoader);

        Method appendClassPathMethod = bootLoader.getClass().getSuperclass().getDeclaredMethod("appendClassPath", String.class);
        appendClassPathMethod.setAccessible(true);
        appendClassPathMethod.invoke(bootLoader, entry.toString());
    }

    private static void initializeBootClassPathIfNeeded(Object bootLoader) throws ReflectiveOperationException {
        var ucpField = bootLoader.getClass().getSuperclass().getDeclaredField("ucp");
        ucpField.setAccessible(true);
        if (ucpField.get(bootLoader) == null) {
            Class<?> urlClassPathClass = Class.forName("jdk.internal.loader.URLClassPath");
            Object ucp = urlClassPathClass.getDeclaredConstructor(URL[].class).newInstance((Object) new URL[0]);
            ucpField.set(bootLoader, ucp);
        }
    }
}
