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

import java.io.IOException;
import java.lang.classfile.ClassFile;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.lang.reflect.Method;
import java.net.URL;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.hosted.RuntimeReflection;
import org.junit.Assert;
import org.junit.Test;

/**
 * Tests boot-loader package visibility for classes loaded through {@code -Xbootclasspath/a:} after
 * image startup.
 */
@NativeImageBuildArgs({
                "-H:+UnlockExperimentalVMOptions",
                "-H:+RuntimeClassLoading",
                "--add-opens=java.base/jdk.internal.loader=ALL-UNNAMED",
                "--enable-url-protocols=jar",
                "--initialize-at-run-time=jdk.internal.loader.ClassLoaders",
                "--features=com.oracle.svm.test.GetPackageRuntimeClassLoadingTest$TestFeature"
})
public class GetPackageRuntimeClassLoadingTest {
    private static final String EXPLODED_BOOT_APPEND_PACKAGE_NAME = "bootappend";
    private static final String EXPLODED_BOOT_APPEND_CLASS_NAME = EXPLODED_BOOT_APPEND_PACKAGE_NAME + ".BootAppendPackageClass";
    private static final String JAR_BOOT_APPEND_PACKAGE_NAME = "bootappendjar";
    private static final String JAR_BOOT_APPEND_CLASS_NAME = JAR_BOOT_APPEND_PACKAGE_NAME + ".BootAppendJarPackageClass";
    private static final String FAILED_BOOT_APPEND_PACKAGE_NAME = "bootappendfailed";
    private static final String FAILED_BOOT_APPEND_CLASS_NAME = FAILED_BOOT_APPEND_PACKAGE_NAME + ".BootAppendFailedPackageClass";
    private static final String MISMATCHED_BOOT_APPEND_CLASS_NAME = "bootappendmismatch.BootAppendFailedPackageClass";
    private static final String BOOT_APPEND_MARKER = "loaded from boot append path";

    /**
     * Registers JDK internals that the test uses to append boot class path entries at run time.
     */
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
        Assert.assertNull(Package.getPackage(EXPLODED_BOOT_APPEND_PACKAGE_NAME));

        byte[] classBytes = generateBootAppendClassBytes(EXPLODED_BOOT_APPEND_CLASS_NAME);
        Path bootAppendRoot = Files.createTempDirectory("gr36066-boot-append");
        Path packageDir = Files.createDirectory(bootAppendRoot.resolve(EXPLODED_BOOT_APPEND_PACKAGE_NAME));
        Files.write(packageDir.resolve("BootAppendPackageClass.class"), classBytes);

        appendBootClassPath(bootAppendRoot);
        Assert.assertNull(Package.getPackage(EXPLODED_BOOT_APPEND_PACKAGE_NAME));
        Class<?> clazz = Class.forName(EXPLODED_BOOT_APPEND_CLASS_NAME, true, null);

        assertBootAppendClassLoaded(clazz);
        Assert.assertNotNull(Package.getPackage(EXPLODED_BOOT_APPEND_PACKAGE_NAME));
        assertPackageVisibleToGetPackages(EXPLODED_BOOT_APPEND_PACKAGE_NAME);
        deleteRecursively(bootAppendRoot);
    }

    /**
     * Checks that package lookup is not dependent on explicit directory entries in boot-append JARs.
     */
    @SuppressWarnings("deprecation")
    @Test
    public void testGetPackageForRuntimeDefinedBootJarClass() throws Exception {
        Assert.assertNull(Package.getPackage(JAR_BOOT_APPEND_PACKAGE_NAME));

        byte[] classBytes = generateBootAppendClassBytes(JAR_BOOT_APPEND_CLASS_NAME);
        Path bootAppendJar = Files.createTempFile("gr36066-boot-append", ".jar");
        try (JarOutputStream jarOutput = new JarOutputStream(Files.newOutputStream(bootAppendJar))) {
            /* Do not add a package directory entry; only the class entry should be present. */
            jarOutput.putNextEntry(new JarEntry(JAR_BOOT_APPEND_CLASS_NAME.replace('.', '/') + ".class"));
            jarOutput.write(classBytes);
            jarOutput.closeEntry();
        }

        appendBootClassPath(bootAppendJar);
        Assert.assertNull(Package.getPackage(JAR_BOOT_APPEND_PACKAGE_NAME));
        Class<?> clazz = Class.forName(JAR_BOOT_APPEND_CLASS_NAME, true, null);

        assertBootAppendClassLoaded(clazz);
        Assert.assertNotNull(Package.getPackage(JAR_BOOT_APPEND_PACKAGE_NAME));
        assertPackageVisibleToGetPackages(JAR_BOOT_APPEND_PACKAGE_NAME);

        try {
            Files.deleteIfExists(bootAppendJar);
        } catch (FileSystemException e) {
            /*
             * A boot-append JAR can stay open in the boot loader's URLClassPath until process
             * shutdown. Windows rejects deleting such files while they are still open.
             */
            bootAppendJar.toFile().deleteOnExit();
        }
    }

    /**
     * Checks that a failed boot-append class definition does not make its package observable.
     */
    @SuppressWarnings("deprecation")
    @Test
    public void testFailedRuntimeDefinedBootClassDoesNotDefinePackage() throws Exception {
        Assert.assertNull(Package.getPackage(FAILED_BOOT_APPEND_PACKAGE_NAME));

        byte[] classBytes = generateBootAppendClassBytes(MISMATCHED_BOOT_APPEND_CLASS_NAME);
        Path bootAppendRoot = Files.createTempDirectory("gr36066-boot-append-failed");
        Path packageDir = Files.createDirectory(bootAppendRoot.resolve(FAILED_BOOT_APPEND_PACKAGE_NAME));
        Files.write(packageDir.resolve("BootAppendFailedPackageClass.class"), classBytes);

        appendBootClassPath(bootAppendRoot);
        Assert.assertThrows(NoClassDefFoundError.class, () -> Class.forName(FAILED_BOOT_APPEND_CLASS_NAME, true, null));
        Assert.assertNull(Package.getPackage(FAILED_BOOT_APPEND_PACKAGE_NAME));
        deleteRecursively(bootAppendRoot);
    }

    /**
     * Generates a minimal public class whose static marker method proves the loaded class identity.
     */
    private static byte[] generateBootAppendClassBytes(String className) {
        ClassDesc bootAppendClass = ClassDesc.of(className);
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

    /**
     * Verifies that `clazz` came from the boot loader and exposes the generated marker method.
     */
    private static void assertBootAppendClassLoaded(Class<?> clazz) throws ReflectiveOperationException {
        Assert.assertNotNull(clazz);
        Assert.assertNull(clazz.getClassLoader());
        Assert.assertEquals(BOOT_APPEND_MARKER, clazz.getMethod("marker").invoke(null));
    }

    /**
     * Verifies that package enumeration includes a package defined by a runtime-loaded boot class.
     */
    @SuppressWarnings("deprecation")
    private static void assertPackageVisibleToGetPackages(String packageName) {
        Assert.assertTrue(Arrays.stream(Package.getPackages()).anyMatch(p -> p.getName().equals(packageName)));
    }

    /**
     * Appends `entry` to the boot loader's runtime class path using the JDK loader API.
     */
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

    /**
     * Initializes the boot loader URL class path when this native-image runtime did not create one.
     */
    private static void initializeBootClassPathIfNeeded(Object bootLoader) throws ReflectiveOperationException {
        var ucpField = bootLoader.getClass().getSuperclass().getDeclaredField("ucp");
        ucpField.setAccessible(true);
        if (ucpField.get(bootLoader) == null) {
            Class<?> urlClassPathClass = Class.forName("jdk.internal.loader.URLClassPath");
            Object ucp = urlClassPathClass.getDeclaredConstructor(URL[].class).newInstance((Object) new URL[0]);
            ucpField.set(bootLoader, ucp);
        }
    }

    /**
     * Recursively deletes {@code path}.
     */
    private static void deleteRecursively(Path path) throws IOException {
        try (var paths = Files.walk(path)) {
            for (Path entry : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(entry);
            }
        }
    }
}
