/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.interop.java;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

final class JavaClassDesc {
    private static final ClassValue<JavaClassDesc> CACHED_DESCS = new ClassValue<JavaClassDesc>() {
        @Override
        protected JavaClassDesc computeValue(Class<?> type) {
            return new JavaClassDesc(type);
        }
    };

    @TruffleBoundary
    static JavaClassDesc forClass(Class<?> clazz) {
        return CACHED_DESCS.get(clazz);
    }

    private final Class<?> type;
    private volatile Members members;

    JavaClassDesc(Class<?> type) {
        this.type = type;
    }

    public Class<?> getType() {
        return type;
    }

    private static class Members {
        final Map<String, JavaMethodDesc> methods;
        final Map<String, JavaMethodDesc> staticMethods;
        final JavaMethodDesc constructor;
        final Map<String, Field> fields;
        final Map<String, Field> staticFields;

        private static final BiFunction<JavaMethodDesc, JavaMethodDesc, JavaMethodDesc> MERGE = new BiFunction<JavaMethodDesc, JavaMethodDesc, JavaMethodDesc>() {
            @Override
            public JavaMethodDesc apply(JavaMethodDesc m1, JavaMethodDesc m2) {
                return merge(m1, m2);
            }
        };

        Members(Class<?> type) {
            Map<String, JavaMethodDesc> methodMap = new HashMap<>();
            Map<String, JavaMethodDesc> staticMethodMap = new HashMap<>();
            Map<String, Field> fieldMap = new HashMap<>();
            Map<String, Field> staticFieldMap = new HashMap<>();
            JavaMethodDesc ctor = null;

            if (Modifier.isPublic(type.getModifiers())) {
                for (Method m : type.getMethods()) {
                    if (m.getDeclaringClass() == Object.class) {
                        continue;
                    }

                    if (!Modifier.isPublic(m.getDeclaringClass().getModifiers())) {
                        /*
                         * If a method is declared in a non-public direct superclass, there should
                         * be a public bridge method in this class that provides access to it.
                         *
                         * In some more elaborate class hierarchies, or if the method is declared in
                         * an interface (i.e. a default method), no bridge method is generated, so
                         * search the whole inheritance hierarchy for accessible methods.
                         */
                        methodMap.clear();
                        staticMethodMap.clear();
                        collectPublicMethods(type, methodMap, staticMethodMap);
                        break;
                    }
                    putMethod(m, methodMap, staticMethodMap);
                }

                boolean inheritedPublicInstanceFields = false;
                for (Field f : type.getFields()) {
                    if (!Modifier.isPublic(f.getDeclaringClass().getModifiers())) {
                        continue;
                    }
                    if (!Modifier.isStatic(f.getModifiers())) {
                        if (f.getDeclaringClass() == type) {
                            assert !fieldMap.containsKey(f.getName());
                            fieldMap.put(f.getName(), f);
                        } else {
                            inheritedPublicInstanceFields = true;
                        }
                    } else {
                        // do not inherit static fields
                        if (f.getDeclaringClass() == type) {
                            staticFieldMap.put(f.getName(), f);
                        }
                    }
                }
                if (inheritedPublicInstanceFields) {
                    // collect inherited instance fields that are not shadowed
                    collectPublicInstanceFields(type, fieldMap);
                }
            } else {
                // If the class is not public, look for inherited public methods.
                collectPublicMethods(type, methodMap, staticMethodMap);

                if (!type.isInterface()) {
                    collectPublicInstanceFields(type, fieldMap);
                }
            }

            if (Modifier.isPublic(type.getModifiers())) {
                for (Constructor<?> c : type.getConstructors()) {
                    if (c.getDeclaringClass() == Object.class) {
                        continue;
                    }
                    SingleMethodDesc overload = SingleMethodDesc.unreflect(c);
                    ctor = ctor == null ? overload : merge(ctor, overload);
                }

            }

            this.methods = methodMap;
            this.staticMethods = staticMethodMap;
            this.constructor = ctor;
            this.fields = fieldMap;
            this.staticFields = staticFieldMap;
        }

        private static void collectPublicMethods(Class<?> type, Map<String, JavaMethodDesc> methodMap, Map<String, JavaMethodDesc> staticMethodMap) {
            collectPublicMethods(type, methodMap, staticMethodMap, new HashSet<>(), type);
        }

        private static void collectPublicMethods(Class<?> type, Map<String, JavaMethodDesc> methodMap, Map<String, JavaMethodDesc> staticMethodMap, Set<Object> visited, Class<?> startType) {
            if (type == Object.class) {
                return;
            }
            boolean isPublicType = Modifier.isPublic(type.getModifiers());
            boolean allMethodsPublic = true;
            if (isPublicType) {
                for (Method m : type.getMethods()) {
                    if (m.getDeclaringClass() == Object.class) {
                        continue;
                    }

                    if (!Modifier.isPublic(m.getDeclaringClass().getModifiers())) {
                        allMethodsPublic = false;
                        continue;
                    } else if (Modifier.isStatic(m.getModifiers()) && (m.getDeclaringClass() != startType && m.getDeclaringClass().isInterface())) {
                        // do not inherit static interface methods
                        continue;
                    }

                    if (visited.add(methodInfo(m))) {
                        putMethod(m, methodMap, staticMethodMap);
                    }
                }
            }
            if (!isPublicType || !allMethodsPublic) {
                if (type.getSuperclass() != null) {
                    collectPublicMethods(type.getSuperclass(), methodMap, staticMethodMap, visited, startType);
                }
                for (Class<?> intf : type.getInterfaces()) {
                    if (visited.add(intf)) {
                        collectPublicMethods(intf, methodMap, staticMethodMap, visited, startType);
                    }
                }
            }
        }

        private static Object methodInfo(Method m) {
            class MethodInfo {
                private final String name = m.getName();
                private final Class<?>[] parameterTypes = m.getParameterTypes();

                @Override
                public boolean equals(Object obj) {
                    return obj instanceof MethodInfo && name.equals(((MethodInfo) obj).name) && Arrays.equals(parameterTypes, ((MethodInfo) obj).parameterTypes);
                }

                @Override
                public int hashCode() {
                    final int prime = 31;
                    int result = 1;
                    result = prime * result + name.hashCode();
                    result = prime * result + Arrays.hashCode(parameterTypes);
                    return result;
                }
            }
            return new MethodInfo();
        }

        private static SingleMethodDesc putMethod(Method m, Map<String, JavaMethodDesc> methodMap, Map<String, JavaMethodDesc> staticMethodMap) {
            SingleMethodDesc method = SingleMethodDesc.unreflect(m);
            if (Modifier.isStatic(m.getModifiers())) {
                staticMethodMap.merge(m.getName(), method, MERGE);
            } else {
                methodMap.merge(m.getName(), method, MERGE);
            }
            return method;
        }

        static JavaMethodDesc merge(JavaMethodDesc existing, JavaMethodDesc other) {
            assert other instanceof SingleMethodDesc;
            if (existing instanceof SingleMethodDesc) {
                return new OverloadedMethodDesc(new SingleMethodDesc[]{(SingleMethodDesc) existing, (SingleMethodDesc) other});
            } else {
                SingleMethodDesc[] oldOverloads = ((OverloadedMethodDesc) existing).getOverloads();
                SingleMethodDesc[] newOverloads = Arrays.copyOf(oldOverloads, oldOverloads.length + 1);
                newOverloads[oldOverloads.length] = (SingleMethodDesc) other;
                return new OverloadedMethodDesc(newOverloads);
            }
        }

        private static void collectPublicInstanceFields(Class<?> type, Map<String, Field> fieldMap) {
            Set<String> fieldNames = new HashSet<>();
            for (Class<?> superclass = type; superclass != null && superclass != Object.class; superclass = superclass.getSuperclass()) {
                for (Field f : superclass.getDeclaredFields()) {
                    if (Modifier.isStatic(f.getModifiers())) {
                        continue;
                    }
                    if (fieldNames.add(f.getName())) {
                        if (Modifier.isPublic(f.getModifiers()) && Modifier.isPublic(f.getDeclaringClass().getModifiers())) {
                            fieldMap.putIfAbsent(f.getName(), f);
                        }
                    }
                }
            }
        }
    }

    private Members getMembers() {
        Members m = members;
        if (m == null) {
            synchronized (this) {
                m = members;
                if (m == null) {
                    members = m = new Members(type);
                }
            }
        }
        return m;
    }

    /**
     * Looks up a public non-static method in this class.
     *
     * @param name method name
     * @return method descriptor or {@code null} if there is no such method
     */
    public JavaMethodDesc lookupMethod(String name) {
        return getMembers().methods.get(name);
    }

    /**
     * Looks up a public static method in this class.
     *
     * @param name method name
     * @return method descriptor or {@code null} if there is no such method
     */
    public JavaMethodDesc lookupStaticMethod(String name) {
        return getMembers().staticMethods.get(name);
    }

    /**
     * Looks up public constructor in this class.
     *
     * @return method descriptor or {@code null} if there is no public constructor
     */
    public JavaMethodDesc lookupConstructor() {
        return getMembers().constructor;
    }

    /**
     * Looks up a public non-static field in this class.
     *
     * @param name field name
     * @return field or {@code null} if there is no such field
     */
    public Field lookupField(String name) {
        return getMembers().fields.get(name);
    }

    /**
     * Looks up a public static field in this class.
     *
     * @param name field name
     * @return field or {@code null} if there is no such field
     */
    public Field lookupStaticField(String name) {
        return getMembers().staticFields.get(name);
    }

    @Override
    public String toString() {
        return "JavaClass[" + type.getCanonicalName() + "]";
    }
}
