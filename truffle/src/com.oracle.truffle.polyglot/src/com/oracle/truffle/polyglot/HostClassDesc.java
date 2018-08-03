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
package com.oracle.truffle.polyglot;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.polyglot.HostMethodDesc.OverloadedMethod;
import com.oracle.truffle.polyglot.HostMethodDesc.SingleMethod;

final class HostClassDesc {
    private static final ClassValue<HostClassDesc> CACHED_DESCS = new ClassValue<HostClassDesc>() {
        @Override
        protected HostClassDesc computeValue(Class<?> type) {
            return new HostClassDesc(type);
        }
    };

    @TruffleBoundary
    static HostClassDesc forClass(Class<?> clazz) {
        return CACHED_DESCS.get(clazz);
    }

    private final Class<?> type;
    private volatile Members members;
    private volatile JNIMembers jniMembers;

    HostClassDesc(Class<?> type) {
        this.type = type;
    }

    public Class<?> getType() {
        return type;
    }

    private static class Members {
        final Map<String, HostMethodDesc> methods;
        final Map<String, HostMethodDesc> staticMethods;
        final HostMethodDesc constructor;
        final Map<String, HostFieldDesc> fields;
        final Map<String, HostFieldDesc> staticFields;
        final HostMethodDesc functionalMethod;

        private static final BiFunction<HostMethodDesc, HostMethodDesc, HostMethodDesc> MERGE = new BiFunction<HostMethodDesc, HostMethodDesc, HostMethodDesc>() {
            @Override
            public HostMethodDesc apply(HostMethodDesc m1, HostMethodDesc m2) {
                return merge(m1, m2);
            }
        };

        Members(Class<?> type) {
            Map<String, HostMethodDesc> methodMap = new LinkedHashMap<>();
            Map<String, HostMethodDesc> staticMethodMap = new LinkedHashMap<>();
            Map<String, HostFieldDesc> fieldMap = new LinkedHashMap<>();
            Map<String, HostFieldDesc> staticFieldMap = new LinkedHashMap<>();
            HostMethodDesc ctor = null;
            HostMethodDesc functionalInterfaceMethod = null;

            collectPublicMethods(type, methodMap, staticMethodMap);

            if (Modifier.isPublic(type.getModifiers())) {
                boolean inheritedPublicInstanceFields = false;
                boolean inheritedPublicInaccessibleFields = false;
                for (Field f : type.getFields()) {
                    if (!Modifier.isStatic(f.getModifiers())) {
                        if (f.getDeclaringClass() == type) {
                            assert !fieldMap.containsKey(f.getName());
                            fieldMap.put(f.getName(), HostFieldDesc.unreflect(f));
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
                            staticFieldMap.put(f.getName(), HostFieldDesc.unreflect(f));
                        }
                    }
                }
                if (inheritedPublicInstanceFields) {
                    collectPublicInstanceFields(type, fieldMap, inheritedPublicInaccessibleFields);
                }
            } else {
                if (!Modifier.isInterface(type.getModifiers())) {
                    collectPublicInstanceFields(type, fieldMap, true);
                }
            }

            if (Modifier.isPublic(type.getModifiers())) {
                for (Constructor<?> c : type.getConstructors()) {
                    SingleMethod overload = SingleMethod.unreflect(c);
                    ctor = ctor == null ? overload : merge(ctor, overload);
                }
            }

            if (!Modifier.isInterface(type.getModifiers()) && !Modifier.isAbstract(type.getModifiers())) {
                String functionalInterfaceMethodName = findFunctionalInterfaceMethodName(type);
                if (functionalInterfaceMethodName != null) {
                    functionalInterfaceMethod = methodMap.get(functionalInterfaceMethodName);
                    assert functionalInterfaceMethod != null;
                }
            }

            this.methods = methodMap;
            this.staticMethods = staticMethodMap;
            this.constructor = ctor;
            this.fields = fieldMap;
            this.staticFields = staticFieldMap;
            this.functionalMethod = functionalInterfaceMethod;
        }

        private static void collectPublicMethods(Class<?> type, Map<String, HostMethodDesc> methodMap, Map<String, HostMethodDesc> staticMethodMap) {
            collectPublicMethods(type, methodMap, staticMethodMap, new HashSet<>(), type);
        }

        private static void collectPublicMethods(Class<?> type, Map<String, HostMethodDesc> methodMap, Map<String, HostMethodDesc> staticMethodMap, Set<Object> visited, Class<?> startType) {
            boolean isPublicType = Modifier.isPublic(type.getModifiers()) && !Proxy.isProxyClass(type);
            boolean allMethodsPublic = true;
            if (isPublicType) {
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
                        allMethodsPublic = false;
                        continue;
                    } else if (Modifier.isStatic(m.getModifiers()) && (m.getDeclaringClass() != startType && Modifier.isInterface(m.getDeclaringClass().getModifiers()))) {
                        // do not inherit static interface methods
                        continue;
                    }

                    if (visited.add(methodInfo(m))) {
                        putMethod(m, methodMap, staticMethodMap);
                    }
                }
            }
            /*
             * Look for inherited public methods if the class/interface is not public or if we have
             * seen a public method declared in a non-public class (see above).
             */
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

        private static void putMethod(Method m, Map<String, HostMethodDesc> methodMap, Map<String, HostMethodDesc> staticMethodMap) {
            SingleMethod method = SingleMethod.unreflect(m);
            Map<String, HostMethodDesc> map = Modifier.isStatic(m.getModifiers()) ? staticMethodMap : methodMap;
            map.merge(m.getName(), method, MERGE);
        }

        static HostMethodDesc merge(HostMethodDesc existing, HostMethodDesc other) {
            assert other instanceof SingleMethod;
            if (existing instanceof SingleMethod) {
                return new OverloadedMethod(new SingleMethod[]{(SingleMethod) existing, (SingleMethod) other});
            } else {
                SingleMethod[] oldOverloads = ((OverloadedMethod) existing).getOverloads();
                SingleMethod[] newOverloads = Arrays.copyOf(oldOverloads, oldOverloads.length + 1);
                newOverloads[oldOverloads.length] = (SingleMethod) other;
                return new OverloadedMethod(newOverloads);
            }
        }

        private static void collectPublicInstanceFields(Class<?> type, Map<String, HostFieldDesc> fieldMap, boolean mayHaveInaccessibleFields) {
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
                        fieldMap.putIfAbsent(f.getName(), HostFieldDesc.unreflect(f));
                    } else {
                        assert mayHaveInaccessibleFields;
                    }
                }
                if (!inheritedPublicInstanceFields) {
                    break;
                }
            }
        }

        private static String findFunctionalInterfaceMethodName(Class<?> clazz) {
            for (Class<?> iface : clazz.getInterfaces()) {
                if (Modifier.isPublic(iface.getModifiers()) && iface.isAnnotationPresent(FunctionalInterface.class)) {
                    for (Method m : iface.getMethods()) {
                        if (Modifier.isAbstract(m.getModifiers()) && !isObjectMethodOverride(m)) {
                            return m.getName();
                        }
                    }
                }
            }

            Class<?> superclass = clazz.getSuperclass();
            if (superclass != null && superclass != Object.class) {
                return findFunctionalInterfaceMethodName(superclass);
            }
            return null;
        }
    }

    static boolean isObjectMethodOverride(Method m) {
        return ((m.getParameterCount() == 0 && (m.getName().equals("hashCode") || m.getName().equals("toString"))) ||
                        (m.getParameterCount() == 1 && m.getName().equals("equals") && m.getParameterTypes()[0] == Object.class));
    }

    private static class JNIMembers {
        final Map<String, HostMethodDesc> methods;
        final Map<String, HostMethodDesc> staticMethods;

        JNIMembers(Members members) {
            this.methods = collectJNINamedMethods(members.methods);
            this.staticMethods = collectJNINamedMethods(members.staticMethods);
        }

        private static Map<String, HostMethodDesc> collectJNINamedMethods(Map<String, HostMethodDesc> methods) {
            Map<String, HostMethodDesc> jniMethods = new LinkedHashMap<>();
            for (HostMethodDesc method : methods.values()) {
                if (method.isConstructor()) {
                    continue;
                }
                for (HostMethodDesc m : method.getOverloads()) {
                    assert m.isMethod();
                    jniMethods.put(HostInteropReflect.jniName((Method) ((SingleMethod) m).getReflectionMethod()), m);
                }
            }
            return jniMethods;
        }
    }

    private Members getMembers() {
        Members m = members;
        if (m == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
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
            CompilerDirectives.transferToInterpreterAndInvalidate();
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
    public HostMethodDesc lookupMethod(String name) {
        return getMembers().methods.get(name);
    }

    /**
     * Looks up a public static method in this class.
     *
     * @param name method name
     * @return method descriptor or {@code null} if there is no such method
     */
    public HostMethodDesc lookupStaticMethod(String name) {
        return getMembers().staticMethods.get(name);
    }

    public HostMethodDesc lookupMethod(String name, boolean onlyStatic) {
        return onlyStatic ? lookupStaticMethod(name) : lookupMethod(name);
    }

    public HostMethodDesc lookupMethodByJNIName(String jniName, boolean onlyStatic) {
        return onlyStatic ? getJNIMembers().staticMethods.get(jniName) : getJNIMembers().methods.get(jniName);
    }

    public Collection<String> getMethodNames(boolean onlyStatic, boolean includeInternal) {
        Map<String, HostMethodDesc> methods = onlyStatic ? getMembers().staticMethods : getMembers().methods;
        if (includeInternal || onlyStatic) {
            return Collections.unmodifiableCollection(methods.keySet());
        } else {
            Collection<String> methodNames = new ArrayList<>(methods.size());
            for (Map.Entry<String, HostMethodDesc> entry : methods.entrySet()) {
                if (!entry.getValue().isInternal()) {
                    methodNames.add(entry.getKey());
                }
            }
            return methodNames;
        }
    }

    /**
     * Looks up public constructor in this class.
     *
     * @return method descriptor or {@code null} if there is no public constructor
     */
    public HostMethodDesc lookupConstructor() {
        return getMembers().constructor;
    }

    /**
     * Looks up a public non-static field in this class.
     *
     * @param name field name
     * @return field or {@code null} if there is no such field
     */
    public HostFieldDesc lookupField(String name) {
        return getMembers().fields.get(name);
    }

    /**
     * Looks up a public static field in this class.
     *
     * @param name field name
     * @return field or {@code null} if there is no such field
     */
    public HostFieldDesc lookupStaticField(String name) {
        return getMembers().staticFields.get(name);
    }

    public HostFieldDesc lookupField(String name, boolean onlyStatic) {
        return onlyStatic ? lookupStaticField(name) : lookupField(name);
    }

    public Collection<String> getFieldNames(boolean onlyStatic) {
        return Collections.unmodifiableCollection((onlyStatic ? getMembers().staticFields : getMembers().fields).keySet());
    }

    public HostMethodDesc getFunctionalMethod() {
        return getMembers().functionalMethod;
    }

    @Override
    public String toString() {
        return "JavaClass[" + type.getCanonicalName() + "]";
    }

}
