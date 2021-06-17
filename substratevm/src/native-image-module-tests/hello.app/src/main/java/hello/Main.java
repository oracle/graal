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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class Main {
    public static void main(String[] args) throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        Module helloAppModule = Main.class.getModule();
        assert helloAppModule.getName().equals("moduletests.hello.app");
        assert helloAppModule.isExported(Main.class.getPackageName());
        assert helloAppModule.isNamed();
        assert helloAppModule.getPackages().contains(Main.class.getPackageName());

        Module helloLibModule = Greeter.class.getModule();
        assert helloLibModule.getName().equals("moduletests.hello.lib");
        assert helloLibModule.isExported(Greeter.class.getPackageName());
        assert helloLibModule.isNamed();
        assert helloLibModule.getPackages().contains(Greeter.class.getPackageName());

        assert !helloAppModule.isOpen(Main.class.getPackageName(), helloLibModule);
        assert helloLibModule.isOpen(hello.privateLib2.PrivateGreeter.class.getPackageName(), helloAppModule);

        assert helloAppModule.canRead(helloLibModule);
        assert !helloLibModule.canRead(helloAppModule);

        System.out.println("Now testing if user modules are part of the boot layer");
        assert ModuleLayer.boot().modules().contains(helloAppModule);
        assert ModuleLayer.boot().modules().contains(helloLibModule);

        System.out.println("Basic Module test involving " + helloAppModule + " and " + helloLibModule);
        Greeter.greet();

        System.out.println("Now accessing package that is not exported in " + helloLibModule);
        hello.privateLib.Greeter.greet();

        System.out.println("Now accessing private method from not exported package in " + helloLibModule);
        Method greetMethod = hello.privateLib2.PrivateGreeter.class.getDeclaredMethod("greet");
        greetMethod.setAccessible(true);
        greetMethod.invoke(null);
    }
}
