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

import java.io.IOException;
import java.lang.module.Configuration;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Scanner;
import java.util.Set;
import java.util.stream.Collectors;

public class Main {
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

        String helloAppModuleResourceContents;
        String sameResourcePathName = "resource-file.txt";

        // Test Module.getResourceAsStream(String)
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
        try (Scanner s = new Scanner(Greeter.class.getResourceAsStream("/" + sameResourcePathName))) {
            assert helloLibModuleResourceContents.equals(s.nextLine()) : "Class.getResourceAsStream(String) result differs from Module.getResourceAsStream(String) result";
        }
    }
}
