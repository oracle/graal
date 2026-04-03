/*
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.svm.hosted.jdk.localization;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;
import java.util.ListResourceBundle;
import java.util.Locale;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.concurrent.ConcurrentHashMap;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.EconomicSet;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.dynamicaccess.AccessCondition;
import org.graalvm.nativeimage.hosted.RuntimeResourceAccess;
import org.graalvm.nativeimage.impl.APIDeprecationSupport;
import org.graalvm.nativeimage.impl.ImageSingletonsSupport;
import org.graalvm.nativeimage.impl.RuntimeResourceSupport;
import org.graalvm.nativeimage.impl.TypeReachabilityCondition;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.oracle.svm.core.configure.RuntimeDynamicAccessMetadata;
import com.oracle.svm.core.jdk.localization.LocalizationSupport;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.hosted.InternalResourceAccess;
import com.oracle.svm.shared.option.HostedOptionValues;

import jdk.graal.compiler.options.OptionKey;
import jdk.graal.compiler.options.OptionValues;

public class LocalizationFeatureTest {
    private static boolean installedImageSingletonsSupport;

    @BeforeClass
    public static void installImageSingletonsSupport() {
        installedImageSingletonsSupport = TestImageSingletonsSupport.installIfMissing();
    }

    @AfterClass
    public static void restoreImageSingletonsSupport() {
        if (installedImageSingletonsSupport) {
            TestImageSingletonsSupport.uninstallForTests();
        }
    }

    @Test
    public void allUnnamedModuleSelectorDoesNotRequireResolution() {
        String moduleName = LocalizationFeature.validateBundleModuleName(LocalizationFeature.ALL_UNNAMED_MODULE, "com.example.Messages", ignored -> {
            throw new AssertionError("ALL-UNNAMED should not be resolved as a named module.");
        });
        Assert.assertEquals(LocalizationFeature.ALL_UNNAMED_MODULE, moduleName);
    }

    @Test
    public void missingNamedModuleIsRejected() {
        UserError.UserException error = Assert.assertThrows(UserError.UserException.class,
                        () -> LocalizationFeature.validateBundleModuleName(
                                        "missing.module",
                                        "com.example.Messages",
                                        ignored -> Optional.empty()));
        Assert.assertTrue(error.getMessage().contains("missing.module"));
        Assert.assertTrue(error.getMessage().contains("com.example.Messages"));
    }

    @Test
    public void allUnnamedBundleRegistrationsRemainDistinctFromUnqualifiedLookups() {
        LocalizationSupport support = new LocalizationSupport(EconomicSet.create(), StandardCharsets.UTF_8);
        support.registerBundleLookup(AccessCondition.unconditional(), LocalizationFeature.ALL_UNNAMED_MODULE, "com.example.Messages");

        EconomicMap<String, RuntimeDynamicAccessMetadata> registeredBundles = registeredBundles(support);
        Assert.assertNull(registeredBundles.get("com.example.Messages"));
        Assert.assertNotNull(registeredBundles.get(LocalizationFeature.ALL_UNNAMED_MODULE + ":com.example.Messages"));
    }

    @Test
    public void namedBundleRegistrationsRemainModuleQualified() {
        LocalizationSupport support = new LocalizationSupport(EconomicSet.create(), StandardCharsets.UTF_8);
        Module javaBase = Object.class.getModule();
        support.registerBundleLookup(AccessCondition.unconditional(), javaBase.getName(), "com.example.Messages");

        EconomicMap<String, RuntimeDynamicAccessMetadata> registeredBundles = registeredBundles(support);
        Assert.assertNotNull(registeredBundles.get(javaBase.getName() + ":com.example.Messages"));
        Assert.assertNull(registeredBundles.get("com.example.Messages"));
    }

    @Test
    public void allUnnamedBundleRegistrationsOnlySatisfyUnnamedModuleLookups() {
        LocalizationSupport support = new LocalizationSupport(EconomicSet.create(), StandardCharsets.UTF_8);
        support.registerBundleLookup(AccessCondition.unconditional(), LocalizationFeature.ALL_UNNAMED_MODULE, "com.example.Messages");

        ResourceBundle.Control control = ResourceBundle.Control.getControl(ResourceBundle.Control.FORMAT_DEFAULT);
        Module unnamedModule = LocalizationFeatureTest.class.getClassLoader().getUnnamedModule();

        Assert.assertTrue(support.isRegisteredBundleLookup(unnamedModule, "com.example.Messages", Locale.ROOT, control));
        Assert.assertFalse(support.isRegisteredBundleLookup(Object.class.getModule(), "com.example.Messages", Locale.ROOT, control));
    }

    @Test
    public void runtimeResourceAccessUsesAllUnnamedForUnnamedModules() {
        RecordingRuntimeResourceSupport runtimeResources = recordingRuntimeResourceSupport();
        runtimeResources.reset();

        RuntimeResourceAccess.addResourceBundle(LocalizationFeatureTest.class.getModule(), "com.example.Messages", new Locale[]{Locale.ROOT});

        Assert.assertEquals(LocalizationFeature.ALL_UNNAMED_MODULE + ":com.example.Messages", runtimeResources.registeredBundleName);
        Assert.assertEquals(List.of(Locale.ROOT), runtimeResources.registeredLocales);
    }

    @Test
    public void resourceAccessBundleRegistrationUsesAllUnnamedForClasspathBundles() {
        RecordingRuntimeResourceSupport runtimeResources = recordingRuntimeResourceSupport();
        runtimeResources.reset();

        ResourceBundle bundle = ResourceBundle.getBundle(ClasspathBundle.class.getName(), Locale.ROOT, LocalizationFeatureTest.class.getClassLoader());
        InternalResourceAccess.singleton().registerResourceBundle(AccessCondition.unconditional(), bundle);

        Assert.assertEquals(ClasspathBundle.class.getName(), bundle.getBaseBundleName());
        Assert.assertEquals(LocalizationFeature.ALL_UNNAMED_MODULE + ":" + ClasspathBundle.class.getName(), runtimeResources.registeredBundleName);
        Assert.assertNull(runtimeResources.registeredLocales);
    }

    @Test
    public void namedModuleMismatchIsRejectedForBundleClass() {
        UserError.UserException error = Assert.assertThrows(UserError.UserException.class,
                        () -> LocalizationFeature.validateBundleClassModule("java.logging", "com.example.Messages", "java.lang.String", String.class));
        Assert.assertTrue(error.getMessage().contains("java.logging"));
        Assert.assertTrue(error.getMessage().contains("java.base"));
        Assert.assertTrue(error.getMessage().contains("java.lang.String"));
    }

    @Test
    public void allUnnamedSelectorRejectsNamedBundleClasses() {
        UserError.UserException error = Assert.assertThrows(UserError.UserException.class,
                        () -> LocalizationFeature.validateBundleClassModule(LocalizationFeature.ALL_UNNAMED_MODULE, "com.example.Messages", "java.lang.String", String.class));
        Assert.assertTrue(error.getMessage().contains(LocalizationFeature.ALL_UNNAMED_MODULE));
        Assert.assertTrue(error.getMessage().contains("java.base"));
    }

    @Test
    public void runtimeCheckedConditionsAreRequiredForBundleLookupMetadata() {
        LocalizationSupport support = new LocalizationSupport(EconomicSet.create(), StandardCharsets.UTF_8);
        TypeReachabilityCondition condition = TypeReachabilityCondition.create(String.class, false);

        Throwable error = Assert.assertThrows(Throwable.class,
                        () -> support.registerBundleLookup(condition, "java.base", "com.example.Messages"));
        Assert.assertTrue(error.getMessage().contains("runtime conditions"));
    }

    @SuppressWarnings("unchecked")
    private static EconomicMap<String, RuntimeDynamicAccessMetadata> registeredBundles(LocalizationSupport support) {
        try {
            Field field = LocalizationSupport.class.getDeclaredField("registeredBundles");
            field.setAccessible(true);
            return (EconomicMap<String, RuntimeDynamicAccessMetadata>) field.get(support);
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError(ex);
        }
    }

    @SuppressWarnings("unchecked")
    private static RecordingRuntimeResourceSupport recordingRuntimeResourceSupport() {
        return (RecordingRuntimeResourceSupport) ImageSingletons.lookup(RuntimeResourceSupport.class);
    }

    public static final class ClasspathBundle extends ListResourceBundle {
        @Override
        protected Object[][] getContents() {
            return new Object[][]{
                            {"key", "value"}
            };
        }
    }

    private static final class RecordingRuntimeResourceSupport implements RuntimeResourceSupport<AccessCondition> {
        private String registeredBundleName;
        private List<Locale> registeredLocales;

        void reset() {
            registeredBundleName = null;
            registeredLocales = null;
        }

        @Override
        public void addResources(AccessCondition condition, String pattern, Object origin) {
            throw new AssertionError("Unused function.");
        }

        @Override
        public void addGlob(AccessCondition condition, String module, String glob, Object origin) {
            throw new AssertionError("Unused function.");
        }

        @Override
        public void ignoreResources(AccessCondition condition, String pattern, Object origin) {
            throw new AssertionError("Unused function.");
        }

        @Override
        public void addResourceBundles(AccessCondition condition, boolean preserved, String name) {
            registeredBundleName = name;
            registeredLocales = null;
        }

        @Override
        public void addResourceBundles(AccessCondition condition, String basename, Collection<Locale> locales) {
            registeredBundleName = basename;
            registeredLocales = List.copyOf(locales);
        }

        @Override
        public void addCondition(AccessCondition condition, Module module, String resourcePath) {
            throw new AssertionError("Unused function.");
        }

        @Override
        public void addResourceEntry(Module module, String resourcePath, Object origin) {
            throw new AssertionError("Unused function.");
        }

        @Override
        public void injectResource(Module module, String resourcePath, byte[] resourceContent, Object origin) {
            throw new AssertionError("Unused function.");
        }
    }

    private static final class TestImageSingletonsSupport extends ImageSingletonsSupport {
        private final ConcurrentHashMap<Class<?>, Object> singletons = new ConcurrentHashMap<>();

        static boolean installIfMissing() {
            if (isInstalled()) {
                return false;
            }
            TestImageSingletonsSupport support = new TestImageSingletonsSupport();
            EconomicMap<OptionKey<?>, Object> values = OptionValues.newOptionMap();
            support.add(HostedOptionValues.class, new HostedOptionValues(values));
            support.add(APIDeprecationSupport.class, new APIDeprecationSupport(false));
            addTestSingleton(support, RuntimeResourceSupport.class, new RecordingRuntimeResourceSupport());
            installSupport(support);
            return true;
        }

        @SuppressWarnings({"rawtypes", "unchecked"})
        private static void addTestSingleton(TestImageSingletonsSupport support, Class<?> key, Object value) {
            support.add((Class) key, value);
        }

        static void uninstallForTests() {
            try {
                Field supportField = ImageSingletonsSupport.class.getDeclaredField("support");
                supportField.setAccessible(true);
                supportField.set(null, null);
            } catch (ReflectiveOperationException ex) {
                throw new AssertionError(ex);
            }
        }

        @Override
        public <T> void add(Class<T> key, T value) {
            singletons.put(key, value);
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T lookup(Class<T> key) {
            return (T) singletons.get(key);
        }

        @Override
        public boolean contains(Class<?> key) {
            return singletons.containsKey(key);
        }
    }
}
