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

import java.io.IOException;
import java.lang.module.FindException;
import java.lang.module.ModuleDescriptor;
import java.lang.module.ModuleFinder;
import java.lang.module.ModuleReader;
import java.lang.module.ModuleReference;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URI;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.graalvm.nativeimage.ImageInfo;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

import com.oracle.svm.core.jdk.RuntimeBootModuleLayerSupport;
import com.oracle.svm.core.properties.RuntimeSystemPropertyParser;

/// Regression tests for `libjvm` launcher option handling in native unit tests.
///
/// This class covers two failure modes:
///
/// - runtime system-property parsing must only consume JVM-level module options, and
/// - boot-layer module resolution failures must remain ordinary launcher/user errors instead of
/// internal VM errors.
public class LibJVMLauncherOptionTest {

    private static final String MISSING_MODULE_NAME = "com.oracle.svm.test.missing.module";
    private static final String TEST_EXPORTS_SOURCE_MODULE = "java.logging";
    private static final String TEST_EXPORTS_TARGET_MODULE = "java.base";
    private static final String TEST_EXPORTS_PACKAGE = "sun.util.logging.internal";

    /// Verifies that runtime parsing consumes only the JVM-level `--module-path=...` and
    /// `--add-modules=...`, `--add-exports=...`, and `--add-opens=...` forms, leaving
    /// launcher-handled variants untouched.
    @Test
    public void launcherModuleOptionsOnlyParseJvmForms() {
        assumeLibJVMNativeImage();

        Map<String, String> previousValues = rememberProperties(
                        RuntimeBootModuleLayerSupport.MODULE_PATH_PROPERTY,
                        RuntimeBootModuleLayerSupport.ADD_MODULES_PROPERTY_PREFIX + "0",
                        RuntimeBootModuleLayerSupport.ADD_MODULES_PROPERTY_PREFIX + "1",
                        RuntimeBootModuleLayerSupport.ADD_EXPORTS_PROPERTY_PREFIX + "0",
                        RuntimeBootModuleLayerSupport.ADD_EXPORTS_PROPERTY_PREFIX + "1",
                        RuntimeBootModuleLayerSupport.ADD_OPENS_PROPERTY_PREFIX + "0",
                        RuntimeBootModuleLayerSupport.ADD_OPENS_PROPERTY_PREFIX + "1",
                        "libjvm.launcher.option.test");
        try {
            clearProperties(previousValues.keySet());

            String[] remainingArgs = RuntimeSystemPropertyParser.parse(
                            new String[]{
                                            "--module-path=mods-dir",
                                            "-p", "short-mods-dir",
                                            "--module-path", "long-mods-dir",
                                            "--add-modules=gamma",
                                            "--add-modules", "alpha,beta",
                                            "--add-exports=java.base/jdk.internal.misc=ALL-UNNAMED",
                                            "--add-exports", "java.base/sun.nio.ch=ALL-UNNAMED",
                                            "--add-opens=java.base/java.lang=ALL-UNNAMED",
                                            "--add-opens", "java.base/java.io=ALL-UNNAMED",
                                            "-Dlibjvm.launcher.option.test=value",
                                            "user-arg"
                            },
                            "-XX:", "-G:");

            Assert.assertArrayEquals(new String[]{
                            "-p", "short-mods-dir",
                            "--module-path", "long-mods-dir",
                            "--add-modules", "alpha,beta",
                            "--add-exports", "java.base/sun.nio.ch=ALL-UNNAMED",
                            "--add-opens", "java.base/java.io=ALL-UNNAMED",
                            "user-arg"
            }, remainingArgs);
            Assert.assertEquals("mods-dir", System.getProperty(RuntimeBootModuleLayerSupport.MODULE_PATH_PROPERTY));
            Assert.assertEquals("gamma", System.getProperty(RuntimeBootModuleLayerSupport.ADD_MODULES_PROPERTY_PREFIX + "0"));
            Assert.assertNull(System.getProperty(RuntimeBootModuleLayerSupport.ADD_MODULES_PROPERTY_PREFIX + "1"));
            Assert.assertEquals("java.base/jdk.internal.misc=ALL-UNNAMED", System.getProperty(RuntimeBootModuleLayerSupport.ADD_EXPORTS_PROPERTY_PREFIX + "0"));
            Assert.assertNull(System.getProperty(RuntimeBootModuleLayerSupport.ADD_EXPORTS_PROPERTY_PREFIX + "1"));
            Assert.assertEquals("java.base/java.lang=ALL-UNNAMED", System.getProperty(RuntimeBootModuleLayerSupport.ADD_OPENS_PROPERTY_PREFIX + "0"));
            Assert.assertNull(System.getProperty(RuntimeBootModuleLayerSupport.ADD_OPENS_PROPERTY_PREFIX + "1"));
            Assert.assertEquals("value", System.getProperty("libjvm.launcher.option.test"));
        } finally {
            restoreProperties(previousValues);
        }
    }

    /// Verifies that `--module` and `-m` are left for the Java launcher instead of being consumed
    /// by runtime system-property parsing.
    @Test
    public void launcherMainModuleOptionsPassThrough() {
        assumeLibJVMNativeImage();

        String previousMainModule = System.getProperty(RuntimeBootModuleLayerSupport.MAIN_MODULE_PROPERTY);
        try {
            System.clearProperty(RuntimeBootModuleLayerSupport.MAIN_MODULE_PROPERTY);

            String[] remainingArgs = RuntimeSystemPropertyParser.parse(
                            new String[]{
                                            "--module=app.long/com.example.LongMain",
                                            "-m=app.short/com.example.ShortMain",
                                            "user-arg"
                            },
                            "-XX:", "-G:");

            Assert.assertArrayEquals(new String[]{"--module=app.long/com.example.LongMain", "-m=app.short/com.example.ShortMain", "user-arg"}, remainingArgs);
            Assert.assertNull(System.getProperty(RuntimeBootModuleLayerSupport.MAIN_MODULE_PROPERTY));
        } finally {
            restoreSystemProperty(RuntimeBootModuleLayerSupport.MAIN_MODULE_PROPERTY, previousMainModule);
        }
    }

    /// Verifies that unresolved runtime boot-layer modules escape as a normal `FindException`
    /// instead of being wrapped as an internal VM failure.
    @Test
    public void bootLayerResolutionErrorsStayUserVisible() throws Exception {
        assumeLibJVMNativeImage();

        String previousMainModule = System.getProperty(RuntimeBootModuleLayerSupport.MAIN_MODULE_PROPERTY);
        try {
            System.setProperty(RuntimeBootModuleLayerSupport.MAIN_MODULE_PROPERTY, MISSING_MODULE_NAME);

            Throwable failure = Assert.assertThrows(InvocationTargetException.class, LibJVMLauncherOptionTest::invokeRuntimeBootLayerInitialize).getCause();
            Assert.assertTrue(failure instanceof FindException);
            Assert.assertFalse(failure.getClass().getName().endsWith("VMError"));
            Assert.assertTrue(failure.getMessage().contains(MISSING_MODULE_NAME));
            Assert.assertTrue(failure.getMessage().contains("not found"));
        } finally {
            restoreSystemProperty(RuntimeBootModuleLayerSupport.MAIN_MODULE_PROPERTY, previousMainModule);
        }
    }

    /// Verifies that boot-layer root selection expands `ALL-MODULE-PATH`, `ALL-DEFAULT`, and
    /// `ALL-SYSTEM`, skips empty list entries, and keeps module roots unique.
    @Test
    public void bootLayerRootModuleComputationExpandsAndDeduplicates() throws Exception {
        assumeLibJVMNativeImage();

        Map<String, String> previousValues = rememberProperties(
                        RuntimeBootModuleLayerSupport.MAIN_MODULE_PROPERTY,
                        RuntimeBootModuleLayerSupport.ADD_MODULES_PROPERTY_PREFIX + "0",
                        RuntimeBootModuleLayerSupport.ADD_MODULES_PROPERTY_PREFIX + "1");
        try {
            clearProperties(previousValues.keySet());
            System.setProperty(RuntimeBootModuleLayerSupport.MAIN_MODULE_PROPERTY, "main.module");
            System.setProperty(RuntimeBootModuleLayerSupport.ADD_MODULES_PROPERTY_PREFIX + "0", "alpha,,ALL-MODULE-PATH");
            System.setProperty(RuntimeBootModuleLayerSupport.ADD_MODULES_PROPERTY_PREFIX + "1", "ALL-DEFAULT,beta,ALL-SYSTEM,alpha");

            Set<String> roots = getRootModules(
                            new OrderedModuleFinder(
                                            TestModuleReference.apiModule("sys.api", "sys.api"),
                                            TestModuleReference.module("sys.internal")),
                            new OrderedModuleFinder(
                                            TestModuleReference.module("alpha"),
                                            TestModuleReference.module("beta"),
                                            TestModuleReference.module("gamma")));
            Assert.assertEquals(new LinkedHashSet<>(Arrays.asList("main.module", "alpha", "beta", "gamma", "sys.api", "sys.internal")), roots);
        } finally {
            restoreProperties(previousValues);
        }
    }

    /// Verifies that preserved runtime `--add-exports` and `--add-opens` options are replayed
    /// onto the runtime boot layer that Crema consults for access checks.
    @Test
    public void runtimeExtraExportsAndOpensApplyToBootLayer() throws Exception {
        assumeLibJVMNativeImage();

        Map<String, String> previousValues = rememberProperties(
                        RuntimeBootModuleLayerSupport.MODULE_PATH_PROPERTY,
                        RuntimeBootModuleLayerSupport.MAIN_MODULE_PROPERTY,
                        RuntimeBootModuleLayerSupport.ADD_MODULES_PROPERTY_PREFIX + "0",
                        RuntimeBootModuleLayerSupport.ADD_MODULES_PROPERTY_PREFIX + "1",
                        RuntimeBootModuleLayerSupport.ADD_EXPORTS_PROPERTY_PREFIX + "0",
                        RuntimeBootModuleLayerSupport.ADD_EXPORTS_PROPERTY_PREFIX + "1",
                        RuntimeBootModuleLayerSupport.ADD_OPENS_PROPERTY_PREFIX + "0",
                        RuntimeBootModuleLayerSupport.ADD_OPENS_PROPERTY_PREFIX + "1");
        try {
            clearProperties(previousValues.keySet());
            System.setProperty(RuntimeBootModuleLayerSupport.ADD_EXPORTS_PROPERTY_PREFIX + "0",
                            TEST_EXPORTS_SOURCE_MODULE + "/" + TEST_EXPORTS_PACKAGE + "=" + TEST_EXPORTS_TARGET_MODULE + ",ALL-UNNAMED");
            System.setProperty(RuntimeBootModuleLayerSupport.ADD_OPENS_PROPERTY_PREFIX + "0",
                            TEST_EXPORTS_SOURCE_MODULE + "/" + TEST_EXPORTS_PACKAGE + "=" + TEST_EXPORTS_TARGET_MODULE + ",ALL-UNNAMED");

            ModuleLayer layer = ModuleLayer.boot();
            Module sourceModule = layer.findModule(TEST_EXPORTS_SOURCE_MODULE).orElseThrow();
            Module targetModule = layer.findModule(TEST_EXPORTS_TARGET_MODULE).orElseThrow();
            Module unnamedModule = LibJVMLauncherOptionTest.class.getModule();

            Assert.assertFalse(sourceModule.isExported(TEST_EXPORTS_PACKAGE, targetModule));
            Assert.assertFalse(sourceModule.isExported(TEST_EXPORTS_PACKAGE, unnamedModule));
            Assert.assertFalse(sourceModule.isOpen(TEST_EXPORTS_PACKAGE, targetModule));
            Assert.assertFalse(sourceModule.isOpen(TEST_EXPORTS_PACKAGE, unnamedModule));

            invokeRuntimeBootLayerInitialize();

            Assert.assertTrue(sourceModule.isExported(TEST_EXPORTS_PACKAGE, targetModule));
            Assert.assertTrue(sourceModule.isExported(TEST_EXPORTS_PACKAGE, unnamedModule));
            Assert.assertTrue(sourceModule.isOpen(TEST_EXPORTS_PACKAGE, targetModule));
            Assert.assertTrue(sourceModule.isOpen(TEST_EXPORTS_PACKAGE, unnamedModule));
        } finally {
            restoreProperties(previousValues);
        }
    }

    private static void assumeLibJVMNativeImage() {
        Assume.assumeTrue(ImageInfo.inImageCode());
    }

    private static void invokeRuntimeBootLayerInitialize() throws Exception {
        Method method = RuntimeBootModuleLayerSupport.class.getDeclaredMethod("initialize");
        method.setAccessible(true);
        method.invoke(null);
    }

    @SuppressWarnings("unchecked")
    private static Set<String> getRootModules(ModuleFinder systemModuleFinder, ModuleFinder modulePathFinder) throws Exception {
        Method method = RuntimeBootModuleLayerSupport.class.getDeclaredMethod("getRootModules", ModuleFinder.class, ModuleFinder.class);
        method.setAccessible(true);
        return (Set<String>) method.invoke(null, systemModuleFinder, modulePathFinder);
    }

    private static Map<String, String> rememberProperties(String... keys) {
        Map<String, String> properties = new java.util.LinkedHashMap<>();
        for (String key : keys) {
            properties.put(key, System.getProperty(key));
        }
        return properties;
    }

    private static void clearProperties(Iterable<String> keys) {
        for (String key : keys) {
            System.clearProperty(key);
        }
    }

    private static void restoreProperties(Map<String, String> properties) {
        for (Map.Entry<String, String> entry : properties.entrySet()) {
            restoreSystemProperty(entry.getKey(), entry.getValue());
        }
    }

    private static void restoreSystemProperty(String key, String value) {
        if (value == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, value);
        }
    }

    private static final class OrderedModuleFinder implements ModuleFinder {
        private final Set<ModuleReference> modules;

        private OrderedModuleFinder(ModuleReference... moduleReferences) {
            modules = Arrays.stream(moduleReferences).collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        }

        @Override
        public Optional<ModuleReference> find(String name) {
            return modules.stream().filter(module -> module.descriptor().name().equals(name)).findFirst();
        }

        @Override
        public Set<ModuleReference> findAll() {
            return modules;
        }
    }

    private static final class TestModuleReference extends ModuleReference {
        private TestModuleReference(ModuleDescriptor descriptor) {
            super(descriptor, (URI) null);
        }

        private static TestModuleReference module(String moduleName) {
            return new TestModuleReference(ModuleDescriptor.newModule(moduleName).build());
        }

        private static TestModuleReference apiModule(String moduleName, String exportedPackage) {
            return new TestModuleReference(ModuleDescriptor.newModule(moduleName).exports(exportedPackage).build());
        }

        @Override
        public ModuleReader open() throws IOException {
            throw new UnsupportedOperationException();
        }
    }
}
