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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
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
    private volatile JNIMembers jniMembers;

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
            Map<String, JavaMethodDesc> methodMap = new LinkedHashMap<>();
            Map<String, JavaMethodDesc> staticMethodMap = new LinkedHashMap<>();
            Map<String, Field> fieldMap = new LinkedHashMap<>();
            Map<String, Field> staticFieldMap = new LinkedHashMap<>();
            JavaMethodDesc ctor = null;

            if (Modifier.isPublic(type.getModifiers())) {
                for (Method m : type.getMethods()) {
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
                boolean inheritedPublicInaccessibleFields = false;
                for (Field f : type.getFields()) {
                    if (!Modifier.isStatic(f.getModifiers())) {
                        if (f.getDeclaringClass() == type) {
                            assert !fieldMap.containsKey(f.getName());
                            fieldMap.put(f.getName(), f);
                        } else {
                            if (Modifier.isPublic(f.getDeclaringClass().getModifiers())) {
                                inheritedPublicInstanceFields = true;
                            } else {
                                inheritedPublicInaccessibleFields = true;
                            }
                        }
                    } else {
                        // do not inherit static fields
                        if (f.getDeclaringClass() == type) {
                            staticFieldMap.put(f.getName(), f);
                        }
                    }
                }
                if (inheritedPublicInstanceFields) {
                    collectPublicInstanceFields(type, fieldMap, inheritedPublicInaccessibleFields);
                }
            } else {
                // If the class is not public, look for inherited public methods.
                collectPublicMethods(type, methodMap, staticMethodMap);

                if (!type.isInterface()) {
                    collectPublicInstanceFields(type, fieldMap, true);
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
            boolean isPublicType = Modifier.isPublic(type.getModifiers());
            boolean allMethodsPublic = true;
            if (isPublicType) {
                for (Method m : type.getMethods()) {
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

        private static void putMethod(Method m, Map<String, JavaMethodDesc> methodMap, Map<String, JavaMethodDesc> staticMethodMap) {
            SingleMethodDesc method = SingleMethodDesc.unreflect(m);
            Map<String, JavaMethodDesc> map = Modifier.isStatic(m.getModifiers()) ? staticMethodMap : methodMap;
            map.merge(m.getName(), method, MERGE);
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

        private static void collectPublicInstanceFields(Class<?> type, Map<String, Field> fieldMap, boolean mayHaveInaccessibleFields) {
            Set<String> fieldNames = new HashSet<>();
            for (Class<?> superclass = type; superclass != null && superclass != Object.class; superclass = superclass.getSuperclass()) {
                boolean inheritedPublicInstanceFields = false;
                for (Field f : superclass.getFields()) {
                    if (Modifier.isStatic(f.getModifiers())) {
                        continue;
                    }
                    if (f.getDeclaringClass() != superclass) {
                        if (Modifier.isPublic(f.getDeclaringClass().getModifiers())) {
                            inheritedPublicInstanceFields = true;
                        }
                        continue;
                    }
                    // a public field in a non-public class hides fields further up the hierarchy
                    if (mayHaveInaccessibleFields && !fieldNames.add(f.getName())) {
                        continue;
                    }
                    if (Modifier.isPublic(f.getDeclaringClass().getModifiers())) {
                        fieldMap.putIfAbsent(f.getName(), f);
                    } else {
                        assert mayHaveInaccessibleFields;
                    }
                }
                if (!inheritedPublicInstanceFields) {
                    break;
                }
            }
        }
    }

    private static class JNIMembers {
        final Map<String, JavaMethodDesc> methods;
        final Map<String, JavaMethodDesc> staticMethods;

        JNIMembers(Members members) {
            this.methods = collectJNINamedMethods(members.methods);
            this.staticMethods = collectJNINamedMethods(members.staticMethods);
        }

        private static Map<String, JavaMethodDesc> collectJNINamedMethods(Map<String, JavaMethodDesc> methods) {
            Map<String, JavaMethodDesc> jniMethods = new LinkedHashMap<>();
            for (JavaMethodDesc method : methods.values()) {
                for (JavaMethodDesc m : method.getOverloads()) {
                    if (m instanceof SingleMethodDesc.ConcreteMethod) {
                        jniMethods.put(JavaInteropReflect.jniName(((SingleMethodDesc.ConcreteMethod) m).getReflectionMethod()), m);
                    }
                }
            }
            return jniMethods;
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

    private JNIMembers getJNIMembers() {
        JNIMembers m = jniMembers;
        if (m == null) {
            synchronized (this) {
                m = jniMembers;
                if (m == null) {
                    jniMembers = m = new JNIMembers(getMembers());
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

    public JavaMethodDesc lookupMethod(String name, boolean onlyStatic) {
        return onlyStatic ? lookupStaticMethod(name) : lookupMethod(name);
    }

    public JavaMethodDesc lookupMethodByJNIName(String jniName, boolean onlyStatic) {
        return onlyStatic ? getJNIMembers().staticMethods.get(jniName) : getJNIMembers().methods.get(jniName);
    }

    public Collection<String> getMethodNames(boolean onlyStatic, boolean includeInternal) {
        Map<String, JavaMethodDesc> methods = onlyStatic ? getMembers().staticMethods : getMembers().methods;
        if (includeInternal || onlyStatic) {
            return Collections.unmodifiableCollection(methods.keySet());
        } else {
            Collection<String> methodNames = new ArrayList<>(methods.size());
            for (Map.Entry<String, JavaMethodDesc> entry : methods.entrySet()) {
                if (!entry.getValue().isInternal()) {
                    methodNames.add(entry.getKey());
                }
            }
            return methodNames;
        }
    }

    public Collection<String> getJNIMethodNames(boolean onlyStatic) {
        return Collections.unmodifiableCollection((onlyStatic ? getJNIMembers().staticMethods : getJNIMembers().methods).keySet());
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

    public Field lookupField(String name, boolean onlyStatic) {
        return onlyStatic ? lookupStaticField(name) : lookupField(name);
    }

    public Collection<String> getFieldNames(boolean onlyStatic) {
        return Collections.unmodifiableCollection((onlyStatic ? getMembers().staticFields : getMembers().fields).keySet());
    }

    @Override
    public String toString() {
        return "JavaClass[" + type.getCanonicalName() + "]";
    }
}
