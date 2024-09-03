/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.test.debug.helper;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.nio.file.Paths;

public class ClassLoaderTest {

    public static Class<?> testClass;
    public static Class<?> testClass2;
    public static Object testObj;
    public static Object testObj2;
    public static Method instanceMethod;
    public static Method instanceMethod2;

    static {
        try {
            Path path = Paths.get(System.getProperty("svm.test.missing.classes"));
            URL[] urls = new URL[]{path.toUri().toURL()};
            try (URLClassLoader classLoader = new URLClassLoader("testClassLoader", urls, ClassLoaderTest.class.getClassLoader())) {
                testClass = classLoader.loadClass("com.oracle.svm.test.missing.classes.TestClass");
                testObj = testClass.getConstructor().newInstance();
                instanceMethod = testClass.getDeclaredMethod("instanceMethod");
            } catch (IOException | ClassNotFoundException | NoSuchMethodException | InstantiationException | IllegalAccessException | InvocationTargetException e) {
                throw new RuntimeException(e);
            }
            // load the same class from two different classLoaders
            try (URLClassLoader classLoader = new URLClassLoader(urls, ClassLoaderTest.class.getClassLoader())) {
                testClass2 = classLoader.loadClass("com.oracle.svm.test.missing.classes.TestClass");
                testObj2 = testClass2.getConstructor().newInstance();
                instanceMethod2 = testClass2.getDeclaredMethod("instanceMethod");
            } catch (IOException | ClassNotFoundException | NoSuchMethodException | InstantiationException | IllegalAccessException | InvocationTargetException e) {
                throw new RuntimeException(e);
            }
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
    }

    public static void main(String[] args) {
        try {
            System.out.println(instanceMethod.invoke(testObj));
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
        try {
            System.out.println(instanceMethod2.invoke(testObj2));
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }
}
