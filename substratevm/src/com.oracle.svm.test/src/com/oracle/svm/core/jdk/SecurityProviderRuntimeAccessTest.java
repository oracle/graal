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
package com.oracle.svm.core.jdk;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.lang.module.Configuration;
import java.lang.module.ModuleFinder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.Provider;
import java.util.Set;

import javax.tools.ToolProvider;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.oracle.svm.shared.security.ProviderConstruction;

public class SecurityProviderRuntimeAccessTest {

    @Rule public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void nullConfiguredProviderReportsActionableError() {
        SecurityException error = assertThrows(SecurityException.class,
                        () -> SecurityProviderRuntimeAccess.validateConfiguredProvider("expected", NullReturningProvider.class.getName(),
                                        NullReturningProvider.class.getName(), null));

        assertTrue(error.getMessage().contains("expected"));
        assertTrue(error.getMessage().contains(NullReturningProvider.class.getName()));
        assertTrue(error.getMessage().contains("returned null"));
    }

    @Test
    public void renamedConfiguredProviderReportsActionableError() {
        Provider candidate = new ConstructorProvider();
        SecurityException error = assertThrows(SecurityException.class,
                        () -> SecurityProviderRuntimeAccess.validateConfiguredProvider("expected", ConstructorProvider.class.getName(),
                                        ConstructorProvider.class.getName(), candidate));

        assertTrue(error.getMessage().contains("expected"));
        assertTrue(error.getMessage().contains(ConstructorProvider.class.getName()));
        assertTrue(error.getMessage().contains(CONSTRUCTOR_PROVIDER_NAME));
    }

    @Test
    public void constructionFailureReportsActionableError() {
        SecurityException error = assertThrows(SecurityException.class,
                        () -> SecurityProviderRuntimeAccess.loadRegisteredConfiguredProvider("expected", ThrowingConstructorProvider.class.getName(),
                                        ThrowingConstructorProvider.class.getName()));

        assertTrue(error.getMessage().contains("expected"));
        assertTrue(error.getMessage().contains(ThrowingConstructorProvider.class.getName()));
        assertTrue(error.getMessage().contains("could not be constructed"));
    }

    @Test
    public void classPathProviderMethodDoesNotOverrideConstructor() throws ReflectiveOperationException {
        Provider provider = SecurityProviderRuntimeAccess.constructProvider(ConstructorProvider.class);

        assertEquals(CONSTRUCTOR_PROVIDER_NAME, provider.getName());
        assertSame(ConstructorProvider.class, provider.getClass());
    }

    @Test
    public void explicitModuleProviderMethodOverridesConstructor() throws Exception {
        Path testDirectory = temporaryFolder.newFolder("explicit-provider-module").toPath();
        Path sourceDirectory = testDirectory.resolve("src");
        Path packageDirectory = sourceDirectory.resolve("test/provider");
        Path classesDirectory = testDirectory.resolve("classes");
        Files.createDirectories(packageDirectory);
        Files.createDirectories(classesDirectory);

        Path moduleInfo = sourceDirectory.resolve("module-info.java");
        Files.writeString(moduleInfo, """
                        module test.provider.module {
                            exports test.provider;
                        }
                        """);
        Path providerSource = packageDirectory.resolve("ModularProvider.java");
        Files.writeString(providerSource, "package test." + "provider;\n" + """
                        import java.security.Provider;

                        public final class ModularProvider extends Provider {
                            public ModularProvider() {
                                this(false);
                            }

                            private ModularProvider(boolean factory) {
                                super(factory ? "factory-provider" : "constructor-provider", "1.0", "Test provider");
                            }

                            public static Provider provider() {
                                return new ModularProvider(true);
                            }
                        }
                        """);

        int compilationResult = ToolProvider.getSystemJavaCompiler().run(null, null, null,
                        "-d", classesDirectory.toString(), moduleInfo.toString(), providerSource.toString());
        assertEquals(0, compilationResult);

        ModuleFinder finder = ModuleFinder.of(classesDirectory);
        Configuration configuration = ModuleLayer.boot().configuration().resolve(finder, ModuleFinder.of(), Set.of("test.provider.module"));
        ModuleLayer layer = ModuleLayer.boot().defineModulesWithOneLoader(configuration, ClassLoader.getSystemClassLoader());
        Class<?> providerClass = layer.findLoader("test.provider.module").loadClass("test.provider.ModularProvider");

        assertTrue(ProviderConstruction.isInExplicitModule(providerClass));
        Provider provider = SecurityProviderRuntimeAccess.constructProvider(providerClass);
        assertEquals("factory-provider", provider.getName());
    }

    @Test
    public void explicitModuleFactoryCanDifferFromImplementationClass() throws Exception {
        Path testDirectory = temporaryFolder.newFolder("explicit-provider-factory-module").toPath();
        Path sourceDirectory = testDirectory.resolve("src");
        Path packageDirectory = sourceDirectory.resolve("test/provider");
        Path classesDirectory = testDirectory.resolve("classes");
        Files.createDirectories(packageDirectory);
        Files.createDirectories(classesDirectory);

        Path moduleInfo = sourceDirectory.resolve("module-info.java");
        Files.writeString(moduleInfo, """
                        module test.provider.factory.module {
                            exports test.provider;
                            provides java.security.Provider with test.provider.ProviderFactory;
                        }
                        """);
        Path providerSource = packageDirectory.resolve("ProviderFactory.java");
        Files.writeString(providerSource, "package test." + "provider;\n" + """
                        import java.security.Provider;

                        public abstract class ProviderFactory {
                            public static Provider provider() {
                                return new HiddenProvider();
                            }
                        }

                        final class HiddenProvider extends Provider {
                            HiddenProvider() {
                                super("factory-only-provider", "1.0", "Factory-only test provider");
                            }
                        }
                        """);

        int compilationResult = ToolProvider.getSystemJavaCompiler().run(null, null, null,
                        "-d", classesDirectory.toString(), moduleInfo.toString(), providerSource.toString());
        assertEquals(0, compilationResult);

        ModuleFinder finder = ModuleFinder.of(classesDirectory);
        Configuration configuration = ModuleLayer.boot().configuration().resolve(finder, ModuleFinder.of(), Set.of("test.provider.factory.module"));
        ModuleLayer layer = ModuleLayer.boot().defineModulesWithOneLoader(configuration, ClassLoader.getSystemClassLoader());
        ClassLoader moduleLoader = layer.findLoader("test.provider.factory.module");
        Class<?> factoryClass = moduleLoader.loadClass("test.provider.ProviderFactory");
        Class<?> implementationClass = moduleLoader.loadClass("test.provider.HiddenProvider");

        assertFalse(Provider.class.isAssignableFrom(factoryClass));
        assertTrue(java.lang.reflect.Modifier.isAbstract(factoryClass.getModifiers()));
        assertFalse(java.lang.reflect.Modifier.isPublic(implementationClass.getModifiers()));
        assertTrue(ProviderConstruction.isQualifyingProviderMethod(factoryClass.getDeclaredMethod("provider")));
        assertTrue(ProviderConstruction.isProviderImplementationClass(implementationClass));

        Provider provider = SecurityProviderRuntimeAccess.constructProvider(factoryClass);
        assertEquals("factory-only-provider", provider.getName());
        assertSame(implementationClass, provider.getClass());
        assertSame(provider, SecurityProviderRuntimeAccess.validateConfiguredProvider(provider.getName(), implementationClass.getName(), factoryClass.getName(), provider));
    }

    @Test
    public void nonPublicConstructionMembersDoNotQualify() throws NoSuchMethodException {
        assertTrue(ProviderConstruction.isProviderImplementationClass(NonPublicProvider.class));
        assertFalse(ProviderConstruction.isProviderConstructorClass(NonPublicProvider.class));
        assertFalse(ProviderConstruction.isQualifyingConstructor(PrivateConstructorProvider.class.getDeclaredConstructor()));
        assertFalse(ProviderConstruction.isQualifyingProviderMethod(PrivateConstructorProvider.class.getDeclaredMethod("provider")));
    }

    private static final String CONSTRUCTOR_PROVIDER_NAME = "constructor-provider";

    public static final class ConstructorProvider extends Provider {
        private static final long serialVersionUID = 1L;

        public ConstructorProvider() {
            super(CONSTRUCTOR_PROVIDER_NAME, "1.0", "Provider created by its constructor");
        }

        public static Provider provider() {
            return new NullReturningProvider();
        }
    }

    public static final class NullReturningProvider extends Provider {
        private static final long serialVersionUID = 1L;

        public NullReturningProvider() {
            super("factory-provider", "1.0", "Provider created by a class-path provider method");
        }
    }

    public static final class ThrowingConstructorProvider extends Provider {
        private static final long serialVersionUID = 1L;

        public ThrowingConstructorProvider() {
            super("throwing-provider", "1.0", "Provider whose constructor fails");
            throw new IllegalStateException("construction failed");
        }
    }

    static final class NonPublicProvider extends Provider {
        private static final long serialVersionUID = 1L;

        NonPublicProvider() {
            super("non-public-provider", "1.0", "Non-public provider class");
        }
    }

    public static final class PrivateConstructorProvider extends Provider {
        private static final long serialVersionUID = 1L;

        private PrivateConstructorProvider() {
            super("private-constructor-provider", "1.0", "Provider with private construction members");
        }

        @SuppressWarnings("unused")
        private static Provider provider() {
            return new PrivateConstructorProvider();
        }
    }
}
