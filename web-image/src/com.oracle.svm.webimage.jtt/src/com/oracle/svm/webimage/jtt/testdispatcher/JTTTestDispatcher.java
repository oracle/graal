/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.webimage.jtt.testdispatcher;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import org.graalvm.nativeimage.hosted.RuntimeReflection;

import com.oracle.svm.util.ReflectionUtil;

import jdk.vm.ci.common.JVMCIError;

public abstract class JTTTestDispatcher {
    public static void runClass(String className, String[] args) throws ReflectiveOperationException {
        runMethod(className, "main", args);
    }

    public static void runMethod(String className, String methodName, String[] args) throws ReflectiveOperationException {
        Class<?> clazz = Class.forName(className);
        Method m = clazz.getMethod(methodName, String[].class);
        JVMCIError.guarantee(Modifier.isStatic(m.getModifiers()), "Entry method must be static");
        m.invoke(null, new Object[]{args});
    }

    public static void registerReflectionClasses(Class<?>[] classes) {
        RuntimeReflection.registerForReflectiveInstantiation(classes);
        RuntimeReflection.register(classes);
        for (Class<?> c : classes) {
            Method m;
            try {
                m = ReflectionUtil.lookupMethod(c, "main", String[].class);
            } catch (ReflectionUtil.ReflectionUtilError ex) {
                throw JVMCIError.shouldNotReachHere(ex);
            }

            RuntimeReflection.register(m);
        }
    }

    public static void registerAllDeclaredMethods(Class<?>[] classes) {
        RuntimeReflection.registerForReflectiveInstantiation(classes);
        RuntimeReflection.register(classes);
        for (Class<?> c : classes) {
            for (Method m : c.getDeclaredMethods()) {
                RuntimeReflection.register(m);
            }

        }
    }

    protected static boolean checkClass(Class<?> clazz, String className) {
        return clazz.getName().equals(className);
    }
}
