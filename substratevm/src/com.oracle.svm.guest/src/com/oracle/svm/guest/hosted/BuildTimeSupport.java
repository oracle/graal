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
package com.oracle.svm.guest.hosted;

import java.lang.module.ModuleDescriptor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.type.CCharPointerPointer;

import jdk.internal.misc.MethodFinder;

/**
 * Helpers for build-time logic that is more naturally expressed (and executed) in the guest context
 * as opposed to extensive use of reflection based on {@code jdk.graal.compiler.vmaccess.VMAccess}
 * in the builder context.
 */
@Platforms(Platform.HOSTED_ONLY.class)
public class BuildTimeSupport {

    /**
     * Gets the method that is the application's entry point into a native image. This will be
     * either the {@linkplain #getCMainFunction C-function entry point} or, if that does not exist,
     * the {@linkplain #getJavaMainMethod Java main method}
     *
     * @param className name of the class defining the main entry point method. If empty,
     *            {@code moduleName} must refer to a named module whose descriptor
     *            {@linkplain ModuleDescriptor#mainClass() declared} a main class.
     * @param moduleName name of the module used to resolve {@code className}. If it's empty, then
     *            the image class loader is used.
     * @param methodName name of the main entry point method
     */
    static Method getMainEntryPointMethod(String className, String moduleName, String methodName) throws ClassNotFoundException {
        Class<?> mainClass;
        String nonEmptyClassName = className;
        try {
            ModuleLayer myLayer = BuildTimeSupport.class.getModule().getLayer();
            Module mainModule = null;
            if (!moduleName.isEmpty()) {
                mainModule = myLayer.findModule(moduleName).orElseThrow(() -> new Error("Module %s for main class not found.".formatted(moduleName)));
            }
            if (className.isEmpty()) {
                assert mainModule != null && mainModule.isNamed() : "named main class module required";
                nonEmptyClassName = mainModule.getDescriptor().mainClass()
                                .orElseThrow(() -> new Error("Module %s does not have a ModuleMainClass attribute, use -m <module>/<main-class>".formatted(moduleName)));
            }
            ClassLoader classLoader = BuildTimeSupport.class.getClassLoader();
            if (mainModule == null) {
                mainClass = Class.forName(nonEmptyClassName, false, classLoader);
            } else {
                assert isModuleClassLoader(classLoader, mainModule.getClassLoader()) : "Argument `module` is java.lang.Module from unknown ClassLoader";
                mainClass = Class.forName(mainModule, nonEmptyClassName);
                if (mainClass == null) {
                    throw new ClassNotFoundException(nonEmptyClassName);
                }
            }
        } catch (UnsupportedClassVersionError ex) {
            if (ex.getMessage().contains("compiled by a more recent version of the Java Runtime")) {
                throw new Error("""
                                Unable to load '%s' due to a Java version mismatch.
                                Please take one of the following actions:
                                 1) Recompile the source files for your application using Java %s, then try running native-image again
                                 2) Use a version of native-image corresponding to the version of Java with which you compiled the source files for your application

                                Root cause: %s""".formatted(
                                nonEmptyClassName,
                                Runtime.version().feature(),
                                ex).replace("\n", System.lineSeparator()));
            } else {
                throw new Error(ex.getMessage());
            }
        }

        Method main = getCMainFunction(methodName, mainClass);
        return main != null ? main : getJavaMainMethod(methodName, mainClass);
    }

    /**
     * Looks up {@code declaringClass} for a method named {@code methodName} with the C-level
     * signature for a main function (i.e. {@code (int argc, char** argv)}).
     *
     * @return {@code null} if no such method exists in {@code declaringClass}
     */
    private static Method getCMainFunction(String methodName, Class<?> declaringClass) {
        try {
            return declaringClass.getDeclaredMethod(methodName, int.class, CCharPointerPointer.class);
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    /**
     * Looks up {@code declaringClass} for a Java main method. If {@code methodName} is
     * {@code "main"}, then the result of {@link MethodFinder#findMainMethod(Class)} returned.
     * Otherwise, the {@code public static} method named {@code methodName} with a signature of
     * {@code void (String[])} is returned.
     *
     * @throws Error if the main method cannot be found
     */
    private static Method getJavaMainMethod(String methodName, Class<?> declaringClass) {
        Method javaMainMethod;
        if ("main".equals(methodName)) {
            javaMainMethod = MethodFinder.findMainMethod(declaringClass);
            if (javaMainMethod == null) {
                throw new Error("""
                                Method '%s.%s' is declared as the main entry point but it cannot be found.
                                Make sure that class '%s' is on the classpath and that it declares a non-private
                                '%s()' or '%s(String[])' method.""".formatted(
                                declaringClass.getName(),
                                methodName,
                                declaringClass.getName(),
                                methodName,
                                methodName).replace("\n", " "));
            }
        } else {
            try {
                javaMainMethod = declaringClass.getDeclaredMethod(methodName, String[].class);
                final int mainMethodModifiers = javaMainMethod.getModifiers();
                if (!Modifier.isStatic(mainMethodModifiers)) {
                    throw new Error("Java main method '%s.%s(String[])' is not static.".formatted(declaringClass.getName(), methodName));
                }
                if (!Modifier.isPublic(mainMethodModifiers)) {
                    throw new Error("Java main method '%s.%s(String[])' is not public.".formatted(declaringClass.getName(), methodName));
                }
            } catch (NoSuchMethodException ex) {
                throw new Error("""
                                Method '%s.%s' is declared as the main entry point but it cannot be found.
                                Make sure that class '%s' is on the class path and that it declares a public static
                                '%s(String[])' method .""".formatted(
                                declaringClass.getName(),
                                methodName,
                                declaringClass.getName(),
                                methodName).replace("\n", " "));
            }
        }

        if (javaMainMethod.getReturnType() != void.class) {
            throw new Error("Java main method '%s.%s(%s)' does not have the return type 'void'.".formatted(declaringClass.getName(), methodName,
                            javaMainMethod.getParameterCount() == 1 ? "String[]" : ""));
        }
        return javaMainMethod;
    }

    private static boolean isModuleClassLoader(ClassLoader loader, ClassLoader moduleClassLoader) {
        if (moduleClassLoader == loader) {
            return true;
        } else {
            if (loader == null) {
                return false;
            }
            return isModuleClassLoader(loader.getParent(), moduleClassLoader);
        }
    }
}
