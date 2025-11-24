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
package com.oracle.svm.util;

import java.lang.reflect.Method;
import java.net.URL;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import jdk.graal.compiler.debug.GraalError;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ModifiersProvider;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * This class contains utility methods for commonly used reflection functionality based on JVMCI
 * reflection (i.e. {@code jdk.vm.ci.meta}) as opposed to core reflection (i.e.
 * {@code java.lang.reflect}).
 */
public final class JVMCIReflectionUtil {

    /**
     * Gets the method declared by {@code declaringClass} named {@code name}. Like
     * {@link Class#getDeclaredMethod(String, Class...)}, this does not consider super classes or
     * interfaces.
     *
     * @param optional when {@code true}, an exception will be thrown if the method does not exist
     * @param declaringClass the class in which to look up the method
     * @param name the name of the method to look up
     * @param parameterTypes the parameter types of the method to look up
     * @return the resolved Java method object or {@code null} if no such method exits and
     *         {@code optional} is {@code true}
     * @throws GraalError if multiple methods with the same name and signature exist in the
     *             declaring class
     * @throws NoSuchMethodError if no such method exists and {@code optional} is {@code false}
     */
    public static ResolvedJavaMethod getDeclaredMethod(boolean optional, ResolvedJavaType declaringClass, String name, ResolvedJavaType... parameterTypes) {
        var result = findMethod(declaringClass, declaringClass.getDeclaredMethods(false), name, parameterTypes);
        if (!optional && result == null) {
            throw new NoSuchMethodError("No method found for %s.%s(%s)".formatted(
                            declaringClass.toClassName(),
                            name,
                            Arrays.stream(parameterTypes).map(ResolvedJavaType::toClassName).collect(Collectors.joining(", "))));
        }
        return result;
    }

    /**
     * Shortcut for
     * {@link #getDeclaredMethod(boolean, ResolvedJavaType, String, ResolvedJavaType...)} that
     * converts the {@link Class} parameters to {@link ResolvedJavaType} using the provided
     * {@link MetaAccessProvider}.
     */
    public static ResolvedJavaMethod getDeclaredMethod(boolean optional, MetaAccessProvider metaAccess, ResolvedJavaType declaringClass, String name, Class<?>... parameterTypes) {
        var parameterJavaTypes = Arrays.stream(parameterTypes).map(metaAccess::lookupJavaType).toArray(ResolvedJavaType[]::new);
        return getDeclaredMethod(optional, declaringClass, name, parameterJavaTypes);
    }

    /**
     * Shortcut for
     * {@link #getDeclaredMethod(boolean, MetaAccessProvider, ResolvedJavaType, String, Class...)}
     * with {@code optional} set to {@code false}.
     */
    public static ResolvedJavaMethod getDeclaredMethod(MetaAccessProvider metaAccess, ResolvedJavaType declaringClass, String name, Class<?>... parameterTypes) {
        return getDeclaredMethod(false, metaAccess, declaringClass, name, parameterTypes);
    }

    /**
     * Gets the constructor declared by {@code declaringClass}. Like
     * {@link Class#getDeclaredConstructor(Class...)}, this does not consider super classes.
     *
     * @param optional when {@code true}, an exception will be thrown if the method does not exist
     * @param declaringClass the class in which to look up the constructor
     * @param parameterTypes the parameter types of the constructor to look up
     * @return the {@linkplain ResolvedJavaMethod resolved Java method} object representing the
     *         requested constructor or {@code null} if no such constructor exits and
     *         {@code optional} is {@code true}
     * @throws GraalError if multiple constructors with the same name and signature exist in the
     *             declaring class
     * @throws NoSuchMethodError if no such constructor exists and {@code optional} is {@code false}
     */
    public static ResolvedJavaMethod getDeclaredConstructor(boolean optional, ResolvedJavaType declaringClass, ResolvedJavaType... parameterTypes) {
        String name = "<init>";
        var result = findMethod(declaringClass, declaringClass.getDeclaredConstructors(false), name, parameterTypes);
        if (!optional && result == null) {
            throw new NoSuchMethodError("No constructor found for %s.%s(%s)".formatted(
                            declaringClass.toClassName(),
                            name,
                            Arrays.stream(parameterTypes).map(ResolvedJavaType::toClassName).collect(Collectors.joining(", "))));
        }
        return result;
    }

    /**
     * Shortcut for {@link #getDeclaredConstructor(boolean, ResolvedJavaType, ResolvedJavaType...)}
     * that converts the {@link Class} parameters to {@link ResolvedJavaType} using the provided
     * {@link MetaAccessProvider}.
     */
    public static ResolvedJavaMethod getDeclaredConstructor(boolean optional, MetaAccessProvider metaAccess, ResolvedJavaType declaringClass, Class<?>... parameterTypes) {
        var parameterJavaTypes = Arrays.stream(parameterTypes).map(metaAccess::lookupJavaType).toArray(ResolvedJavaType[]::new);
        return getDeclaredConstructor(optional, declaringClass, parameterJavaTypes);
    }

    /**
     * Shortcut for
     * {@link #getDeclaredConstructor(boolean, MetaAccessProvider, ResolvedJavaType, Class...)} with
     * {@code optional} set to {@code false}.
     */
    public static ResolvedJavaMethod getDeclaredConstructor(MetaAccessProvider metaAccess, ResolvedJavaType declaringClass, Class<?>... parameterTypes) {
        return getDeclaredConstructor(false, metaAccess, declaringClass, parameterTypes);
    }

    /**
     * Gets the constructors declared by {@code declaringClass}. Like
     * {@link Class#getConstructors()}, this only returns public constructors and does not consider
     * super classes.
     */
    public static ResolvedJavaMethod[] getConstructors(ResolvedJavaType declaringClass) {
        return Arrays.stream(declaringClass.getDeclaredConstructors(false)).filter(ModifiersProvider::isPublic).toArray(ResolvedJavaMethod[]::new);
    }

    private static ResolvedJavaMethod findMethod(ResolvedJavaType declaringClass, ResolvedJavaMethod[] methods, String name, ResolvedJavaType... parameterTypes) {
        ResolvedJavaMethod res = null;
        for (ResolvedJavaMethod m : methods) {
            if (!m.getName().equals(name)) {
                continue;
            }
            // ignore receiver type for comparison
            JavaType[] parameterList = m.getSignature().toParameterTypes(null);
            if (!Arrays.equals(parameterTypes, parameterList)) {
                continue;
            }
            if (res == null) {
                res = m;
            } else {
                throw new GraalError("More than one method with signature %s in %s", res.format("%H.%n(%p)"), declaringClass.toClassName());
            }
        }
        return res;
    }

    /**
     * Gets the field declared by {@code declaringClass} named {@code fieldName}. Like
     * {@link Class#getDeclaredField(String)}, this does not consider super classes or interfaces.
     * Unlike {@link Class#getDeclaredField(String)}, it does include
     * {@linkplain ResolvedJavaField#isInternal() internal} fields.
     *
     * @param optional when {@code true}, an exception will be thrown if the method does not exist
     * @param declaringClass the class in which to look up the field
     * @param fieldName the name of the field to look up
     * @return the resolved Java field object or {@code null} if no such field exists and
     *         {@code optional} is {@code true}
     * @throws NoSuchFieldError if no field with the specified name exists in the declaring class
     *             and {@code optional} is {@code false}
     * @throws GraalError if multiple fields with the same name exist in the declaring class
     */
    public static ResolvedJavaField getDeclaredField(boolean optional, ResolvedJavaType declaringClass, String fieldName) {
        ResolvedJavaField[][] allFields = {declaringClass.getStaticFields(), declaringClass.getInstanceFields(false)};
        ResolvedJavaField found = null;
        for (ResolvedJavaField[] fields : allFields) {
            for (ResolvedJavaField field : fields) {
                if (field.getName().equals(fieldName)) {
                    if (found != null) {
                        throw new GraalError("More than one field named %s in %s", fieldName, declaringClass.toClassName());
                    }
                    found = field;
                }
            }
        }
        if (!optional && found == null) {
            throw new NoSuchFieldError(declaringClass.toClassName() + "." + fieldName);
        }
        return found;
    }

    /**
     * Shortcut for {@link #getDeclaredField(boolean, ResolvedJavaType, String)} with
     * {@code optional} set to {@code false}.
     */
    public static ResolvedJavaField getDeclaredField(ResolvedJavaType declaringClass, String fieldName) {
        return getDeclaredField(false, declaringClass, fieldName);
    }

    /**
     * Returns a list containing all fields present within this type, including
     * {@linkplain ResolvedJavaField#isInternal() internal} fields. The returned List is
     * unmodifiable; calls to any mutator method will always cause
     * {@code UnsupportedOperationException} to be thrown.
     */
    public static List<ResolvedJavaField> getAllFields(ResolvedJavaType declaringClass) {
        ResolvedJavaField[] staticFields = declaringClass.getStaticFields();
        ResolvedJavaField[] instanceFields = declaringClass.getInstanceFields(false);
        ResolvedJavaField[] allFields = new ResolvedJavaField[staticFields.length + instanceFields.length];
        System.arraycopy(staticFields, 0, allFields, 0, staticFields.length);
        System.arraycopy(instanceFields, 0, allFields, staticFields.length, instanceFields.length);
        return Collections.unmodifiableList(Arrays.asList(allFields));
    }

    /**
     * Gets the package name for a {@link ResolvedJavaType}. This is the same as calling
     * {@link Class#getPackageName()} on the underlying class.
     * <p>
     * Implementation derived from {@link Class#getPackageName()}.
     */
    public static String getPackageName(ResolvedJavaType type) {
        ResolvedJavaType c = type.isArray() ? type.getElementalType() : type;
        if (c.isPrimitive()) {
            return "java.lang";
        }
        String cn = c.toClassName();
        int dot = cn.lastIndexOf('.');
        return (dot != -1) ? cn.substring(0, dot).intern() : "";
    }

    /**
     * Gets the package enclosing {@code type} or null if {@code type} represents an array type, a
     * primitive type or void.
     */
    public static ResolvedJavaPackage getPackage(ResolvedJavaType type) {
        return JVMCIReflectionUtilFallback.getPackage(type);
    }

    /**
     * Gets the return type for a {@link ResolvedJavaMethod}. This is the same as calling
     * {@link Method#getReturnType()} on the underlying method.
     *
     * @throws GraalError if the return type is not a {@link ResolvedJavaType}
     */
    public static ResolvedJavaType getResolvedReturnType(ResolvedJavaMethod m) {
        JavaType returnType = m.getSignature().getReturnType(m.getDeclaringClass());
        if (returnType instanceof ResolvedJavaType resolvedJavaType) {
            return resolvedJavaType;
        }
        throw new GraalError("Method does not have a resolved return type: %s", m.format("%H.%n(%p)"));
    }

    /**
     * Gets the type name for a {@link ResolvedJavaType}. This is the same as calling
     * {@link Class#getTypeName()} on the underlying class.
     * <p>
     * Implementation derived from {@link Class#getTypeName()}.
     */
    public static String getTypeName(ResolvedJavaType type) {
        if (type.isArray()) {
            try {
                ResolvedJavaType cl = type;
                int dimensions = 0;
                do {
                    dimensions++;
                    cl = cl.getComponentType();
                } while (cl.isArray());
                return cl.toClassName().concat("[]".repeat(dimensions));
            } catch (Throwable e) {
                /* FALLTHRU */
            }
        }
        return type.toClassName();
    }

    public static ResolvedJavaModule getModule(ResolvedJavaType declaringClass) {
        return JVMCIReflectionUtilFallback.getModule(declaringClass);
    }

    /**
     * Returns the <em>origin</em> associated with this {@link ResolvedJavaType}.
     *
     * This is not yet properly implemented as it falls back to the original class (GR-71068).
     *
     * @return the location (URL), or {@code null} if no URL was supplied during construction.
     */
    public static URL getOrigin(ResolvedJavaType type) {
        return JVMCIReflectionUtilFallback.getOrigin(type);
    }

    /**
     * Counts the number of superclasses as returned by {@link Class#getSuperclass()}.
     * {@link java.lang.Object} and all primitive types are at depth 0 and all interfaces are at
     * depth 1.
     */
    public static int countSuperclasses(ResolvedJavaType type) {
        Objects.requireNonNull(type, "Must accept a non-null class argument");
        int depth = 0;
        for (var cur = type.getSuperclass(); cur != null; cur = cur.getSuperclass()) {
            depth += 1;
        }
        return depth;
    }
}
