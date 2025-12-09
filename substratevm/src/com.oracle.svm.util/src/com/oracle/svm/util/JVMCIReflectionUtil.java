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
import java.util.stream.Stream;

import com.oracle.graal.vmaccess.ResolvedJavaModule;
import com.oracle.graal.vmaccess.ResolvedJavaModuleLayer;
import com.oracle.graal.vmaccess.ResolvedJavaPackage;

import jdk.graal.compiler.debug.GraalError;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ModifiersProvider;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.Signature;

/**
 * This class contains utility methods for commonly used reflection functionality based on JVMCI
 * reflection (i.e. {@code jdk.vm.ci.meta}) as opposed to core reflection (i.e.
 * {@code java.lang.reflect}).
 */
public final class JVMCIReflectionUtil {

    /**
     * Gets the method declared by {@code declaringClass} uniquely identified by {@code name} and
     * {@code parameterTypes}. Like {@link Class#getDeclaredMethod(String, Class...)}, this does not
     * consider super classes or interfaces.
     *
     * @param optional when {@code false}, an exception is thrown if the method does not exist
     * @param declaringClass the class in which to look up the method
     * @param name the name of the method to look up
     * @param parameterTypes the parameter types of the method to look up
     * @return the {@link ResolvedJavaMethod} object representing the requested method or
     *         {@code null} if no such method exists and {@code optional} is {@code true}
     * @throws GraalError if there is a method named {@code name} with a parameter whose type name
     *             matches the name of the corresponding entry {@code p} in {@code parameterTypes}
     *             but whose value is not {@link Object#equals(Object)} to {@code p} (because it is
     *             unresolved or is resolved by a different class loader)
     * @throws NoSuchMethodError if {@code optional} is {@code false} and there is no method
     *             declared by {@code declaringClass} that is uniquely identified by {@code name}
     *             and {@code parameterTypes}
     */
    public static ResolvedJavaMethod getUniqueDeclaredMethod(boolean optional, ResolvedJavaType declaringClass, String name, ResolvedJavaType... parameterTypes) {
        var result = findUniqueMethod(optional, declaringClass, declaringClass.getDeclaredMethods(false), name, parameterTypes);
        if (!optional && result == null) {
            throw new NoSuchMethodError("%s.%s(%s)".formatted(
                            declaringClass.toClassName(),
                            name,
                            Arrays.stream(parameterTypes).map(ResolvedJavaType::toClassName).collect(Collectors.joining(", "))));
        }
        return result;
    }

    /**
     * Shortcut for
     * {@link #getUniqueDeclaredMethod(boolean, ResolvedJavaType, String, ResolvedJavaType...)} that
     * passes {@code false} for {@code optional}.
     */
    public static ResolvedJavaMethod getUniqueDeclaredMethod(ResolvedJavaType declaringClass, String name, ResolvedJavaType... parameterTypes) {
        return getUniqueDeclaredMethod(false, declaringClass, name, parameterTypes);
    }

    /**
     * Shortcut for
     * {@link #getUniqueDeclaredMethod(boolean, ResolvedJavaType, String, ResolvedJavaType...)} that
     * converts the {@link Class} parameters to {@link ResolvedJavaType} using the provided
     * {@link MetaAccessProvider}.
     */
    public static ResolvedJavaMethod getUniqueDeclaredMethod(boolean optional, MetaAccessProvider metaAccess, ResolvedJavaType declaringClass, String name, Class<?>... parameterTypes) {
        var parameterJavaTypes = Arrays.stream(parameterTypes).map(metaAccess::lookupJavaType).toArray(ResolvedJavaType[]::new);
        return getUniqueDeclaredMethod(optional, declaringClass, name, parameterJavaTypes);
    }

    /**
     * Shortcut for
     * {@link #getUniqueDeclaredMethod(boolean, MetaAccessProvider, ResolvedJavaType, String, Class...)}
     * with {@code optional} set to {@code false}.
     */
    public static ResolvedJavaMethod getUniqueDeclaredMethod(MetaAccessProvider metaAccess, ResolvedJavaType declaringClass, String name, Class<?>... parameterTypes) {
        return getUniqueDeclaredMethod(false, metaAccess, declaringClass, name, parameterTypes);
    }

    /**
     * Gets the constructor declared by {@code declaringClass} uniquely identified by
     * {@code parameterTypes}. Like {@link Class#getDeclaredConstructor(Class...)}, this does not
     * consider super classes.
     *
     * @param optional when {@code false}, an exception is thrown if the constructor does not exist
     * @param declaringClass the class in which to look up the constructor
     * @param parameterTypes the parameter types of the constructor to look up
     * @return the {@link ResolvedJavaMethod} object representing the requested constructor or
     *         {@code null} if no such constructor exists and {@code optional} is {@code true}
     * @throws GraalError if there is a constructor with a parameter whose type name matches the
     *             name of the corresponding entry {@code p} in {@code parameterTypes} but whose
     *             value is not {@link Object#equals(Object)} to {@code p} (because it is unresolved
     *             or is resolved by a different class loader)
     * @throws NoSuchMethodError if {@code optional} is {@code false} and there is no constructor
     *             declared by {@code declaringClass} that is uniquely identified by
     *             {@code parameterTypes}
     */
    public static ResolvedJavaMethod getDeclaredConstructor(boolean optional, ResolvedJavaType declaringClass, ResolvedJavaType... parameterTypes) {
        String name = "<init>";
        var result = findUniqueMethod(optional, declaringClass, declaringClass.getDeclaredConstructors(false), name, parameterTypes);
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

    private static boolean parameterTypesEqual(ResolvedJavaType[] parameterTypes, Signature sig, ResolvedJavaType declaringClass) {
        if (sig.getParameterCount(false) != parameterTypes.length) {
            return false;
        }
        for (int i = 0; i < parameterTypes.length; i++) {
            ResolvedJavaType p1 = parameterTypes[i];
            JavaType p2 = sig.getParameterType(i, declaringClass);
            if (!p1.getName().equals(p2.getName())) {
                return false;
            }
            if (!p1.equals(p2)) {
                // Handles case of p2 being unresolved or being resolved
                // but with a different class loader.
                throw new GraalError("Parameter %d has matching type name (%s) but type objects are not equal: p1=%s, p2=%s", i, p2.toClassName(), p1, p2);
            }
        }
        return true;
    }

    private static ResolvedJavaMethod findUniqueMethod(boolean optional, ResolvedJavaType declaringClass, ResolvedJavaMethod[] methods, String name, ResolvedJavaType... parameterTypes) {
        ResolvedJavaMethod res = null;
        for (ResolvedJavaMethod m : methods) {
            if (!m.getName().equals(name)) {
                continue;
            }
            // ignore receiver type for comparison
            Signature sig = m.getSignature();
            if (!parameterTypesEqual(parameterTypes, sig, declaringClass)) {
                continue;
            }
            if (res == null) {
                res = m;
            } else {
                if (!optional) {
                    if (m.isConstructor()) {
                        throw new NoSuchMethodError("More than one constructor with parameters %s is declared by %s".formatted(res.format("(%P)"), declaringClass.toClassName()));
                    } else {
                        throw new NoSuchMethodError("More than one method named %s with signature %s is declared by %s".formatted(name, res.format("(%P)"), declaringClass.toClassName()));
                    }
                }
                return null;
            }
        }
        return res;
    }

    /**
     * Gets the field declared by {@code declaringClass} uniquely identified by {@code fieldName}.
     * Like {@link Class#getDeclaredField(String)}, this does not consider super classes or
     * interfaces. Unlike {@link Class#getDeclaredField(String)}, it includes
     * {@linkplain ResolvedJavaField#isInternal() internal} fields.
     *
     * @param optional when {@code true}, an exception is thrown if a field is not uniquely
     *            identified by {@code fieldName}
     * @param declaringClass the class in which to look up the field
     * @param fieldName the name of the field to look up
     * @return the {@link ResolvedJavaField} object or {@code null} if no such unique field exists
     *         and {@code optional} is {@code true}
     * @throws NoSuchFieldError if no field is uniquely identified by {@code fieldName} in
     *             {@code declaringClass} and {@code optional} is {@code false}
     */
    public static ResolvedJavaField getUniqueDeclaredField(boolean optional, ResolvedJavaType declaringClass, String fieldName) {
        ResolvedJavaField[][] allFields = {declaringClass.getStaticFields(), declaringClass.getInstanceFields(false)};
        ResolvedJavaField found = null;
        for (ResolvedJavaField[] fields : allFields) {
            for (ResolvedJavaField field : fields) {
                if (field.getName().equals(fieldName)) {
                    if (found != null) {
                        throw new NoSuchFieldError("More than one field named %s in %s".formatted(fieldName, declaringClass.toClassName()));
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
     * Shortcut for {@link #getUniqueDeclaredField(boolean, ResolvedJavaType, String)} with
     * {@code optional} set to {@code false}.
     */
    public static ResolvedJavaField getUniqueDeclaredField(ResolvedJavaType declaringClass, String fieldName) {
        return getUniqueDeclaredField(false, declaringClass, fieldName);
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
     * <p>
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

    /**
     * Returns a stream of the packages defined to the boot loader. See
     * {@code jdk.internal.loader.BootLoader#packages()}.
     */
    public static Stream<ResolvedJavaPackage> bootLoaderPackages() {
        return JVMCIReflectionUtilFallback.bootLoaderPackages();
    }

    /**
     * Returns the boot layer. See {@link java.lang.ModuleLayer#boot()}.
     */
    public static ResolvedJavaModuleLayer bootModuleLayer() {
        return JVMCIReflectionUtilFallback.bootModuleLayer();
    }

    /**
     * Reads the value of the non-static field named {@code fieldName} in the declaring class of the
     * object represented by {@code receiver}.
     *
     * @param receiver the instance object from which the field value will be read
     * @param fieldName name of the field to read
     * @throws NoSuchFieldError if no field is uniquely identified by {@code fieldName} in
     *             {@code declaringClass}
     * @throws IllegalArgumentException if {@code !receiver.getJavaKind().isObject()} or the named
     *             field is static
     * @throws NullPointerException if {@code receiver == null} or {@code receiver} represents
     *             {@link JavaConstant#isNull() null}
     */
    public static JavaConstant readInstanceField(JavaConstant receiver, String fieldName) {
        if (!receiver.getJavaKind().isObject()) {
            throw new IllegalArgumentException("Not an object: " + receiver);
        }
        if (receiver.isNull()) {
            throw new NullPointerException();
        }
        MetaAccessProvider metaAccess = GraalAccess.getOriginalProviders().getMetaAccess();
        ResolvedJavaType declaringClass = metaAccess.lookupJavaType(receiver);
        ResolvedJavaField field = JVMCIReflectionUtil.getUniqueDeclaredField(false, declaringClass, fieldName);
        if (field.isStatic()) {
            throw new IllegalArgumentException(fieldName + " is static");
        }
        return GraalAccess.getOriginalProviders().getConstantReflection().readFieldValue(field, receiver);
    }

    /**
     * Reads the value of the static field {@code fieldName} declared by {@code declaringClass}.
     *
     * @throws NoSuchFieldError if no field is uniquely identified by {@code fieldName} in
     *             {@code declaringClass}
     * @throws IllegalArgumentException if the named field is not static
     */
    public static JavaConstant readStaticField(ResolvedJavaType declaringClass, String fieldName) {
        ResolvedJavaField field = JVMCIReflectionUtil.getUniqueDeclaredField(false, declaringClass, fieldName);
        if (!field.isStatic()) {
            throw new IllegalArgumentException(fieldName + " is not static");
        }
        return GraalAccess.getOriginalProviders().getConstantReflection().readFieldValue(field, null);
    }
}
