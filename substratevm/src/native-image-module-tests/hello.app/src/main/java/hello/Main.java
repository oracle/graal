/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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
package hello;

import hello.lib.Greeter;

import java.io.File;
import java.io.InputStream;
import java.io.IOException;
import java.lang.module.Configuration;
import java.lang.module.ModuleFinder;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Scanner;
import java.util.Set;
import java.util.stream.Collectors;

import jdk.dynalink.StandardOperation;

public class Main {
    private static final String MISC_MODULE_RESOURCE_NAME = "META-INF/native-image-module-tests/misc-resource.txt";
    private static final String CONDITIONAL_DUPLICATE_MODULE_RESOURCE_NAME = "conditional-duplicate-resource.txt";
    private static final String CONDITIONAL_DUPLICATE_MODULE_RESOURCE_CONTENTS = "Conditionally registered duplicate module resource";
    private static final String APP_MISC_MODULE_RESOURCE_CONTENTS = "Build-time registered misc resource in module moduletests.hello.app";
    private static final String RUNTIME_MISC_MODULE_RESOURCE_CONTENTS = "Runtime module-path misc resource in module moduletests.hello.runtime";

    public static void main(String[] args) throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        failIfAssertionsAreDisabled();

        Module helloAppModule = Main.class.getModule();
        Module helloLibModule = Greeter.class.getModule();
        testModuleObjects(helloAppModule, helloLibModule);

        System.out.println("Basic Module test involving " + helloAppModule + " and " + helloLibModule);
        Greeter.greet();

        System.out.println("Now accessing package that is not exported in " + helloLibModule);
        hello.privateLib.Greeter.greet();

        System.out.println("Now accessing private method from not exported package in " + helloLibModule);
        Method greetMethod = hello.privateLib2.PrivateGreeter.class.getDeclaredMethod("greet");
        greetMethod.setAccessible(true);
        greetMethod.invoke(null);

        testBootLayer(helloAppModule, helloLibModule);

        testResourceAccess(helloAppModule, helloLibModule);
        testRuntimeDefinedModuleLayer();

        /* Use classes from Java module jdk.dynalink (which the builder does NOT depend on) */
        System.out.println("jdk.dynalink.StandardOperation enum values: " + String.join(" ", Arrays.stream(StandardOperation.values()).map(StandardOperation::toString).collect(Collectors.joining(" "))));
    }

    private static void failIfAssertionsAreDisabled() {
        boolean enabled = false;
        assert enabled = true;
        if (!enabled) {
            throw new AssertionError("This example requires that assertions are enabled (-ea)");
        }
    }

    private static void testModuleObjects(Module helloAppModule, Module helloLibModule) {
        assert helloAppModule.getName().equals("moduletests.hello.app");
        assert helloAppModule.isExported(Main.class.getPackageName());
        assert helloAppModule.isNamed();
        assert helloAppModule.getPackages().contains(Main.class.getPackageName());

        assert helloLibModule.getName().equals("moduletests.hello.lib");
        assert helloLibModule.isExported(Greeter.class.getPackageName());
        assert helloLibModule.isNamed();
        assert helloLibModule.getPackages().contains(Greeter.class.getPackageName());

        assert !helloAppModule.isOpen(Main.class.getPackageName(), helloLibModule);
        assert helloLibModule.isOpen(hello.privateLib2.PrivateGreeter.class.getPackageName(), helloAppModule);

        assert helloAppModule.canRead(helloLibModule);
        assert !helloLibModule.canRead(helloAppModule);
    }

    @SuppressWarnings("OptionalGetWithoutIsPresent")
    private static void testBootLayer(Module helloAppModule, Module helloLibModule) {
        System.out.println("Now testing boot module layer");

        ModuleLayer bootLayer = ModuleLayer.boot();

        System.out.println("Now testing boot module layer configuration");
        Configuration cf = bootLayer.configuration();
        assert cf.findModule("java.base").get()
                .reference()
                .descriptor()
                .exports()
                .stream().anyMatch(e -> (e.source().equals("java.lang") && !e.isQualified()));

        System.out.println("Now testing boot module layer module set");
        Set<Module> modules = bootLayer.modules();
        assert modules.contains(Object.class.getModule());
        int uniqueModuleNameCount = modules.stream().map(Module::getName).collect(Collectors.toSet()).size();
        assert modules.size() == uniqueModuleNameCount;

        System.out.println("Now testing if boot module layer contains java.base");
        Module base = Object.class.getModule();
        assert bootLayer.findModule("java.base").get() == base;
        assert base.getLayer() == bootLayer;

        System.out.println("Now testing boot module layer java.base loader");
        assert bootLayer.findModule("java.base").get().getClassLoader() == null;
        assert bootLayer.findLoader("java.base") == null;

        System.out.println("Now testing boot module layer parent");
        assert bootLayer.parents().size() == 1;
        assert bootLayer.parents().get(0) == ModuleLayer.empty();

        System.out.println("Now testing if user modules are part of the boot layer");
        assert ModuleLayer.boot().modules().contains(helloAppModule);
        assert ModuleLayer.boot().modules().contains(helloLibModule);
    }

    private static void testResourceAccess(Module helloAppModule, Module helloLibModule) {
        System.out.println("Now testing resources in modules");

        String jdkModuleResourcePathName = "module-info.class";
        String sameResourcePathName = "resource-file.txt";
        String runtimeModulePathResourcePathName = "runtime-module-path-resource.txt";
        Module javaBaseModule = Object.class.getModule();
        Module dynalinkModule = StandardOperation.class.getModule();

        assert javaBaseModule.getClassLoader() == null;
        assert dynalinkModule.getClassLoader() != null;

        System.out.println("Now testing resource access in platform-loaded module " + dynalinkModule);
        try (InputStream stream = dynalinkModule.getResourceAsStream(jdkModuleResourcePathName)) {
            assert stream != null : "Unable to access resource " + jdkModuleResourcePathName + " from " + dynalinkModule;
        } catch (IOException e) {
            throw new AssertionError("Unable to access resource " + jdkModuleResourcePathName + " from " + dynalinkModule, e);
        }
        assertClassResourceURLAccessible(StandardOperation.class, "/" + jdkModuleResourcePathName);

        System.out.println("Now testing resource access in boot-loaded module " + javaBaseModule);
        try (InputStream stream = javaBaseModule.getResourceAsStream(jdkModuleResourcePathName)) {
            assert stream != null : "Unable to access resource " + jdkModuleResourcePathName + " from " + javaBaseModule;
        } catch (IOException e) {
            throw new AssertionError("Unable to access resource " + jdkModuleResourcePathName + " from " + javaBaseModule, e);
        }
        assertClassResourceURLAccessible(Object.class, "/" + jdkModuleResourcePathName);

        // Test Module.getResourceAsStream(String)
        String helloAppModuleResourceContents;
        try (Scanner s = new Scanner(helloAppModule.getResourceAsStream(sameResourcePathName))) {
            helloAppModuleResourceContents = s.nextLine();
        } catch (IOException e) {
            throw new AssertionError("Unable to access resource " + sameResourcePathName + " from " + helloAppModule);
        }
        String helloLibModuleResourceContents;
        try (Scanner s = new Scanner(helloLibModule.getResourceAsStream(sameResourcePathName))) {
            helloLibModuleResourceContents = s.nextLine();
        } catch (IOException e) {
            throw new AssertionError("Unable to access resource " + sameResourcePathName + " from " + helloLibModule);
        }
        assert !helloAppModuleResourceContents.equals(helloLibModuleResourceContents) : sameResourcePathName + " not recognized as different resources";

        // Test Class.getResourceAsStream(String)
        try (Scanner s = new Scanner(Main.class.getResourceAsStream("/" + sameResourcePathName))) {
            assert helloAppModuleResourceContents.equals(s.nextLine()) : "Class.getResourceAsStream(String) result differs from Module.getResourceAsStream(String) result";
        }
        assertClassResourceURLContents(Main.class, "/" + sameResourcePathName, helloAppModuleResourceContents);
        try (Scanner s = new Scanner(Greeter.class.getResourceAsStream("/" + sameResourcePathName))) {
            assert helloLibModuleResourceContents.equals(s.nextLine()) : "Class.getResourceAsStream(String) result differs from Module.getResourceAsStream(String) result";
        }
        assertClassResourceURLContents(Greeter.class, "/" + sameResourcePathName, helloLibModuleResourceContents);

        testMiscModuleResources(ClassLoader.getSystemClassLoader(), Set.of(APP_MISC_MODULE_RESOURCE_CONTENTS));
        testDuplicateConditionalModuleResource(helloAppModule);
        testRuntimeOnlyModulePathResource(helloLibModule, runtimeModulePathResourcePathName);
    }

    private static void testMiscModuleResources(ClassLoader loader, Set<String> expectedContents) {
        System.out.println("Now testing miscellaneous resource access in modules");

        Set<String> actualContents = new HashSet<>();
        try {
            Enumeration<URL> resources = loader.getResources(MISC_MODULE_RESOURCE_NAME);
            while (resources.hasMoreElements()) {
                URL resource = resources.nextElement();
                try (Scanner s = new Scanner(resource.openStream())) {
                    actualContents.add(s.nextLine());
                }
            }
        } catch (IOException e) {
            throw new AssertionError("Unable to access miscellaneous module resource " + MISC_MODULE_RESOURCE_NAME + " from " + loader, e);
        }
        assert expectedContents.equals(actualContents) : "Unexpected contents for " + MISC_MODULE_RESOURCE_NAME + " from " + loader + ": " + actualContents;
    }

    private static void testDuplicateConditionalModuleResource(Module helloAppModule) {
        if (!isNativeImageRuntime()) {
            return;
        }
        assert ResourceConditionA.class.getName().endsWith("ResourceConditionA");

        try (InputStream stream = helloAppModule.getResourceAsStream(CONDITIONAL_DUPLICATE_MODULE_RESOURCE_NAME)) {
            assert stream == null : CONDITIONAL_DUPLICATE_MODULE_RESOURCE_NAME + " should not be accessible before either condition type is reached";
        } catch (IOException e) {
            throw new AssertionError("Unable to query resource " + CONDITIONAL_DUPLICATE_MODULE_RESOURCE_NAME + " from " + helloAppModule, e);
        }

        ResourceConditionB.reached = true;
        try (InputStream stream = helloAppModule.getResourceAsStream(CONDITIONAL_DUPLICATE_MODULE_RESOURCE_NAME)) {
            assert stream != null : CONDITIONAL_DUPLICATE_MODULE_RESOURCE_NAME + " should be accessible after the second condition type is reached";
            try (Scanner s = new Scanner(stream)) {
                assert CONDITIONAL_DUPLICATE_MODULE_RESOURCE_CONTENTS.equals(s.nextLine()) : "Unexpected contents of " + CONDITIONAL_DUPLICATE_MODULE_RESOURCE_NAME;
            }
        } catch (IOException e) {
            throw new AssertionError("Unable to access resource " + CONDITIONAL_DUPLICATE_MODULE_RESOURCE_NAME + " from " + helloAppModule, e);
        }
    }

    private static void testRuntimeOnlyModulePathResource(Module helloLibModule, String resourcePathName) {
        System.out.println("Now testing runtime module path resource access in " + helloLibModule);
        boolean expectResource = !isNativeImageRuntime() || Boolean.getBoolean("svm.test.expectRuntimeModulePathFallback");
        try (InputStream stream = helloLibModule.getResourceAsStream(resourcePathName)) {
            assert expectResource == (stream != null) : "Unexpected Module.getResourceAsStream(String) result for " + resourcePathName + " from " + helloLibModule;
            if (stream != null) {
                try (Scanner s = new Scanner(stream)) {
                    assert "runtime module path resource".equals(s.nextLine()) : "Unexpected contents of " + resourcePathName;
                }
            }
        } catch (IOException e) {
            throw new AssertionError("Unable to access resource " + resourcePathName + " from " + helloLibModule, e);
        }
    }

    private static void testRuntimeDefinedModuleLayer() {
        if (!Boolean.getBoolean("svm.test.expectRuntimeDefinedModuleLayer")) {
            return;
        }

        String moduleName = "moduletests.hello.runtime";
        System.out.println("Now testing runtime module layer definition for " + moduleName);
        Path[] runtimeModulePath = Arrays.stream(System.getProperty("jdk.module.path").split(File.pathSeparator))
                        .filter(entry -> !entry.isEmpty())
                        .map(Path::of)
                        .toArray(Path[]::new);
        ModuleFinder finder = ModuleFinder.of(runtimeModulePath);
        Configuration configuration = ModuleLayer.boot().configuration().resolve(finder, ModuleFinder.of(), Set.of(moduleName));
        ModuleLayer layer = ModuleLayer.boot().defineModulesWithOneLoader(configuration, ClassLoader.getSystemClassLoader());
        Module module = layer.findModule(moduleName).orElseThrow(() -> new AssertionError("Missing runtime module " + moduleName));
        assert module.getLayer() == layer : module + " not defined in runtime layer";
        ClassLoader runtimeModuleLoader = layer.findLoader(moduleName);
        assert module.getClassLoader() == runtimeModuleLoader : module + " not defined to the runtime layer loader";
        assert runtimeModuleLoader.getParent() == ClassLoader.getSystemClassLoader() : module + " loader does not use the system class loader as parent";
        // This lookup returns a jar: URL for the runtime module-path resource.
        testMiscModuleResources(runtimeModuleLoader, Set.of(APP_MISC_MODULE_RESOURCE_CONTENTS, RUNTIME_MISC_MODULE_RESOURCE_CONTENTS));

        try {
            Class<?> runtimeGreeter = Class.forName("hello.runtime.RuntimeGreeter", true, runtimeModuleLoader);
            Method greet = runtimeGreeter.getDeclaredMethod("greet");
            assert "hello from moduletests.hello.lib using element from java.xml".equals(greet.invoke(null)) : "Unexpected greeting from " + runtimeGreeter;
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            throw new AssertionError("Unable to load class from runtime module " + moduleName, e);
        }
    }

    private static boolean isNativeImageRuntime() {
        return "runtime".equals(System.getProperty("org.graalvm.nativeimage.imagecode"));
    }

    private static void assertClassResourceURLAccessible(Class<?> clazz, String resourcePathName) {
        URL url = clazz.getResource(resourcePathName);
        assert url != null : "Unable to access resource URL " + resourcePathName + " from " + clazz.getModule();
        try (InputStream stream = url.openStream()) {
            assert stream != null : "Unable to open resource URL " + url + " from " + clazz.getModule();
        } catch (IOException e) {
            throw new AssertionError("Unable to open resource URL " + url + " from " + clazz.getModule(), e);
        }
    }

    private static void assertClassResourceURLContents(Class<?> clazz, String resourcePathName, String expectedContents) {
        URL url = clazz.getResource(resourcePathName);
        assert url != null : "Unable to access resource URL " + resourcePathName + " from " + clazz.getModule();
        try (Scanner s = new Scanner(url.openStream())) {
            assert expectedContents.equals(s.nextLine()) : "Class.getResource(String) result differs from Module.getResourceAsStream(String) result";
        } catch (IOException e) {
            throw new AssertionError("Unable to open resource URL " + url + " from " + clazz.getModule(), e);
        }
    }
}
