/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.graal.hotspot.libgraal;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import jdk.graal.compiler.debug.GraalError;
import jdk.internal.module.Modules;

/**
 * This is a copy of SVM internal classes such as {@code com.oracle.svm.util.ReflectionUtil} and
 * {@code com.oracle.svm.util.ModuleSupport}.
 */
public final class LibGraalUtil {

    private LibGraalUtil() {
    }

    //
    // com.oracle.svm.util.ClassUtil methods
    //

    /**
     * Alternative to {@link Class#getSimpleName} that does not probe an enclosing class or method,
     * which can fail when they cannot be loaded.
     *
     * Note the differences to {@link Class#getName} and {@link Class#getSimpleName} (which might
     * actually be preferable):
     *
     * <pre>
     * Class.getName()                              Class.getSimpleName()   ClassUtil.getUnqualifiedName()
     * ---------------------------------------------------------------------------------------------------
     * int                                          int                     int
     * java.lang.String                             String                  String
     * [Ljava.lang.String;                          String[]                String[]
     * java.util.HashMap$EntrySet                   EntrySet                HashMap$EntrySet
     * com.example.ClassWithAnonymousInnerClass$1   ""                      ClassWithAnonymousInnerClass$1
     * </pre>
     */
    public static String getUnqualifiedName(Class<?> clazz) {
        String name = clazz.getTypeName();
        return name.substring(name.lastIndexOf('.') + 1); // strip the package name
    }

    //
    // com.oracle.svm.util.ReflectionUtil methods
    //

    /**
     * Ensure that this class is allowed to call setAccessible for an element of the provided
     * declaring class.
     */
    private static void openModule(Class<?> declaringClass) {
        accessModuleByClass(Access.OPEN, LibGraalUtil.class, declaringClass);
    }

    public static Class<?> lookupClass(String className) {
        return lookupClass(false, className);
    }

    public static Class<?> lookupClass(boolean optional, String className) {
        return lookupClass(optional, className, LibGraalUtil.class.getClassLoader());
    }

    public static Class<?> lookupClass(boolean optional, String className, ClassLoader loader) {
        try {
            return Class.forName(className, false, loader);
        } catch (ClassNotFoundException ex) {
            if (optional) {
                return null;
            }
            throw new GraalError(ex);
        }
    }

    public static Method lookupMethod(Class<?> declaringClass, String methodName, Class<?>... parameterTypes) {
        return lookupMethod(false, declaringClass, methodName, parameterTypes);
    }

    public static Method lookupMethod(boolean optional, Class<?> declaringClass, String methodName, Class<?>... parameterTypes) {
        try {
            Method result = declaringClass.getDeclaredMethod(methodName, parameterTypes);
            openModule(declaringClass);
            result.setAccessible(true);
            return result;
        } catch (ReflectiveOperationException ex) {
            if (optional) {
                return null;
            }
            throw new GraalError(ex);
        }
    }

    public static <T> Constructor<T> lookupConstructor(Class<T> declaringClass, Class<?>... parameterTypes) {
        return lookupConstructor(false, declaringClass, parameterTypes);
    }

    public static <T> Constructor<T> lookupConstructor(boolean optional, Class<T> declaringClass, Class<?>... parameterTypes) {
        try {
            Constructor<T> result = declaringClass.getDeclaredConstructor(parameterTypes);
            openModule(declaringClass);
            result.setAccessible(true);
            return result;
        } catch (ReflectiveOperationException ex) {
            if (optional) {
                return null;
            }
            throw new GraalError(ex);
        }
    }

    public static <T> T newInstance(Class<T> declaringClass) {
        try {
            return lookupConstructor(declaringClass).newInstance();
        } catch (ReflectiveOperationException ex) {
            throw new GraalError(ex);
        }
    }

    public static Field lookupField(Class<?> declaringClass, String fieldName) {
        return lookupField(false, declaringClass, fieldName);
    }

    private static final Method fieldGetDeclaredFields0 = lookupMethod(Class.class, "getDeclaredFields0", boolean.class);

    public static Field lookupField(boolean optional, Class<?> declaringClass, String fieldName) {
        try {
            Field result = declaringClass.getDeclaredField(fieldName);
            openModule(declaringClass);
            result.setAccessible(true);
            return result;
        } catch (ReflectiveOperationException ex) {
            /* Try to get hidden field */
            try {
                Field[] allFields = (Field[]) fieldGetDeclaredFields0.invoke(declaringClass, false);
                for (Field field : allFields) {
                    if (field.getName().equals(fieldName)) {
                        openModule(declaringClass);
                        field.setAccessible(true);
                        return field;
                    }
                }
            } catch (ReflectiveOperationException e) {
                // ignore
            }
            if (optional) {
                return null;
            }
            throw new GraalError(ex);
        }
    }

    @SuppressWarnings("unchecked")
    public static <T> T readField(Class<?> declaringClass, String fieldName, Object receiver) {
        try {
            return (T) lookupField(declaringClass, fieldName).get(receiver);
        } catch (ReflectiveOperationException ex) {
            throw new GraalError(ex);
        }
    }

    //
    // com.oracle.svm.util.ModuleSupport methods
    //

    @Platforms(Platform.HOSTED_ONLY.class)
    public enum Access {
        OPEN {
            @Override
            void giveAccess(Module accessingModule, Module declaringModule, String packageName) {
                if (accessingModule != null) {
                    if (declaringModule.isOpen(packageName, accessingModule)) {
                        return;
                    }
                    Modules.addOpens(declaringModule, packageName, accessingModule);
                } else {
                    if (declaringModule.isOpen(packageName)) {
                        return;
                    }
                    Modules.addOpensToAllUnnamed(declaringModule, packageName);
                }
            }
        },
        EXPORT {
            @Override
            void giveAccess(Module accessingModule, Module declaringModule, String packageName) {
                if (accessingModule != null) {
                    if (declaringModule.isExported(packageName, accessingModule)) {
                        return;
                    }
                    Modules.addExports(declaringModule, packageName, accessingModule);
                } else {
                    if (declaringModule.isExported(packageName)) {
                        return;
                    }
                    Modules.addExportsToAllUnnamed(declaringModule, packageName);
                }
            }
        };

        abstract void giveAccess(Module accessingModule, Module declaringModule, String packageName);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public static void accessModuleByClass(Access access, Class<?> accessingClass, Class<?> declaringClass) {
        accessModuleByClass(access, accessingClass, declaringClass.getModule(), declaringClass.getPackageName());
    }

    /**
     * Open or export packages {@code packageNames} in the module named {@code moduleName} to module
     * of given {@code accessingClass}. If {@code accessingClass} is null packages are opened or
     * exported to ALL-UNNAMED. If no packages are given, all packages of the module are opened or
     * exported.
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    public static void accessPackagesToClass(Access access, Class<?> accessingClass, boolean optional, String moduleName, String... packageNames) {
        Objects.requireNonNull(moduleName);
        Optional<Module> module = ModuleLayer.boot().findModule(moduleName);
        if (module.isEmpty()) {
            if (optional) {
                return;
            }
            String accessor = accessingClass != null ? "class " + accessingClass.getTypeName() : "ALL-UNNAMED";
            String message = access.name().toLowerCase() + " of packages from module " + moduleName + " to " +
                            accessor + " failed. No module named " + moduleName + " in boot layer.";
            throw new GraalError(message);
        }
        Module declaringModule = module.get();
        Objects.requireNonNull(packageNames);
        Set<String> packages = packageNames.length > 0 ? Set.of(packageNames) : declaringModule.getPackages();
        for (String packageName : packages) {
            accessModuleByClass(access, accessingClass, declaringModule, packageName);
        }
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public static void accessModuleByClass(Access access, Class<?> accessingClass, Module declaringModule, String packageName) {
        Module namedAccessingModule = null;
        if (accessingClass != null) {
            Module accessingModule = accessingClass.getModule();
            if (accessingModule.isNamed()) {
                namedAccessingModule = accessingModule;
            }
        }
        access.giveAccess(namedAccessingModule, declaringModule, packageName);
    }
}
