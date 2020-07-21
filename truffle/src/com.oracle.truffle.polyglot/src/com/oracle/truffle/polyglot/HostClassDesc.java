/*
 * Copyright (c) 2016, 2020, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.polyglot.HostMethodDesc.OverloadedMethod;
import com.oracle.truffle.polyglot.HostMethodDesc.SingleMethod;

final class HostClassDesc {
    @TruffleBoundary
    static HostClassDesc forClass(PolyglotEngineImpl impl, Class<?> clazz) {
        return impl.getHostClassCache().forClass(clazz);
    }

    private final Class<?> type;
    private volatile Object members;
    private volatile JNIMembers jniMembers;
    private final boolean allowsImplementation;

    HostClassDesc(HostClassCache cache, Class<?> type) {
        this.members = cache;
        this.type = type;
        if (type.isInterface()) {
            this.allowsImplementation = cache.allowsImplementation(type);
        } else {
            this.allowsImplementation = false;
        }
    }

    public boolean isAllowsImplementation() {
        return allowsImplementation;
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

        Members(HostClassCache hostAccess, Class<?> type) {
            Map<String, HostMethodDesc> methodMap = new LinkedHashMap<>();
            Map<String, HostMethodDesc> staticMethodMap = new LinkedHashMap<>();
            Map<String, HostFieldDesc> fieldMap = new LinkedHashMap<>();
            Map<String, HostFieldDesc> staticFieldMap = new LinkedHashMap<>();
            HostMethodDesc functionalInterfaceMethod = null;

            collectPublicMethods(hostAccess, type, methodMap, staticMethodMap);
            collectPublicFields(hostAccess, type, fieldMap, staticFieldMap);

            HostMethodDesc ctor = collectPublicConstructors(hostAccess, type);

            if (!Modifier.isInterface(type.getModifiers()) && !Modifier.isAbstract(type.getModifiers())) {
                String functionalInterfaceMethodName = findFunctionalInterfaceMethodName(type);
                if (functionalInterfaceMethodName != null) {
                    functionalInterfaceMethod = methodMap.get(functionalInterfaceMethodName);
                }
            }

            this.methods = methodMap;
            this.staticMethods = staticMethodMap;
            this.constructor = ctor;
            this.fields = fieldMap;
            this.staticFields = staticFieldMap;
            this.functionalMethod = functionalInterfaceMethod;
        }

        private static boolean isClassAccessible(Class<?> declaringClass, HostClassCache hostAccess) {
            return Modifier.isPublic(declaringClass.getModifiers()) && EngineAccessor.JDKSERVICES.verifyModuleVisibility(hostAccess.getUnnamedModule(), declaringClass);
        }

        private static HostMethodDesc collectPublicConstructors(HostClassCache hostAccess, Class<?> type) {
            HostMethodDesc ctor = null;
            if (isClassAccessible(type, hostAccess)) {
                for (Constructor<?> c : type.getConstructors()) {
                    if (!hostAccess.allowsAccess(c)) {
                        continue;
                    }
                    SingleMethod overload = SingleMethod.unreflect(c);
                    ctor = ctor == null ? overload : merge(ctor, overload);
                }
            }
            return ctor;
        }

        private static void collectPublicMethods(HostClassCache hostAccess, Class<?> type, Map<String, HostMethodDesc> methodMap, Map<String, HostMethodDesc> staticMethodMap) {
            collectPublicMethods(hostAccess, type, methodMap, staticMethodMap, new HashSet<>(), type);
        }

        private static void collectPublicMethods(HostClassCache hostAccess, Class<?> type, Map<String, HostMethodDesc> methodMap, Map<String, HostMethodDesc> staticMethodMap, Set<Object> visited,
                        Class<?> startType) {
            boolean isPublicType = isClassAccessible(type, hostAccess) && !Proxy.isProxyClass(type);
            boolean allMethodsPublic = true;
            List<Method> bridgeMethods = null;
            if (isPublicType) {
                for (Method m : type.getMethods()) {
                    Class<?> declaringClass = m.getDeclaringClass();
                    if (Modifier.isStatic(m.getModifiers()) && (declaringClass != startType && Modifier.isInterface(declaringClass.getModifiers()))) {
                        // do not inherit static interface methods
                        continue;
                    } else if (!isClassAccessible(declaringClass, hostAccess)) {
                        /*
                         * If a public method is declared in a non-public superclass, there should
                         * be a public bridge method in this class that provides access to it.
                         *
                         * In some more elaborate class hierarchies, or if the method is declared in
                         * an interface (i.e. a default method), no bridge method is generated, so
                         * search the whole inheritance hierarchy for accessible methods.
                         */
                        allMethodsPublic = false;
                        continue;
                    } else if (m.isBridge()) {
                        /*
                         * Bridge methods for varargs methods generated by javac may not have the
                         * varargs modifier, so we must not use the bridge method in that case since
                         * it would be then treated as non-varargs.
                         *
                         * As a workaround, stash away all bridge methods and only consider them at
                         * the end if no equivalent public non-bridge method was found.
                         */
                        allMethodsPublic = false;
                        if (bridgeMethods == null) {
                            bridgeMethods = new ArrayList<>();
                        }
                        bridgeMethods.add(m);
                        continue;
                    }
                    if (visited.add(methodInfo(m))) {
                        putMethod(hostAccess, m, methodMap, staticMethodMap);
                    }
                }
            }
            /*
             * Look for inherited public methods if the class/interface is not public or if we have
             * seen a public method declared in a non-public class (see above).
             */
            if (!isPublicType || !allMethodsPublic) {
                if (type.getSuperclass() != null) {
                    collectPublicMethods(hostAccess, type.getSuperclass(), methodMap, staticMethodMap, visited, startType);
                }
                for (Class<?> intf : type.getInterfaces()) {
                    if (visited.add(intf)) {
                        collectPublicMethods(hostAccess, intf, methodMap, staticMethodMap, visited, startType);
                    }
                }
            }
            // Add bridge methods for public methods inherited from non-public superclasses.
            if (bridgeMethods != null && !bridgeMethods.isEmpty()) {
                for (Method m : bridgeMethods) {
                    if (visited.add(methodInfo(m))) {
                        putMethod(hostAccess, m, methodMap, staticMethodMap);
                    }
                }
            }
        }

        private static Object methodInfo(Method m) {
            class MethodInfo {
                private final boolean isStatic = Modifier.isStatic(m.getModifiers());
                private final String name = m.getName();
                private final Class<?>[] parameterTypes = m.getParameterTypes();

                @Override
                public boolean equals(Object obj) {
                    if (obj instanceof MethodInfo) {
                        MethodInfo other = (MethodInfo) obj;
                        return isStatic == other.isStatic && name.equals(other.name) && Arrays.equals(parameterTypes, other.parameterTypes);
                    } else {
                        return false;
                    }
                }

                @Override
                public int hashCode() {
                    final int prime = 31;
                    int result = 1;
                    result = prime * result + (isStatic ? 1 : 0);
                    result = prime * result + name.hashCode();
                    result = prime * result + Arrays.hashCode(parameterTypes);
                    return result;
                }
            }
            return new MethodInfo();
        }

        private static void putMethod(HostClassCache hostAccess, Method m, Map<String, HostMethodDesc> methodMap, Map<String, HostMethodDesc> staticMethodMap) {
            if (!hostAccess.allowsAccess(m)) {
                return;
            }
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

        private static void collectPublicFields(HostClassCache hostAccess, Class<?> type, Map<String, HostFieldDesc> fieldMap, Map<String, HostFieldDesc> staticFieldMap) {
            if (isClassAccessible(type, hostAccess)) {
                boolean inheritedPublicInstanceFields = false;
                boolean inheritedPublicInaccessibleFields = false;
                for (Field f : type.getFields()) {
                    if (!Modifier.isStatic(f.getModifiers())) {
                        if (f.getDeclaringClass() == type) {
                            assert !fieldMap.containsKey(f.getName());
                            if (hostAccess.allowsAccess(f)) {
                                fieldMap.put(f.getName(), HostFieldDesc.unreflect(f));
                            }
                        } else {
                            if (isClassAccessible(f.getDeclaringClass(), hostAccess)) {
                                inheritedPublicInstanceFields = true;
                            } else {
                                inheritedPublicInaccessibleFields = true;
                            }
                        }
                    } else {
                        // do not inherit static fields
                        if (f.getDeclaringClass() == type && hostAccess.allowsAccess(f)) {
                            staticFieldMap.put(f.getName(), HostFieldDesc.unreflect(f));
                        }
                    }
                }
                if (inheritedPublicInstanceFields) {
                    collectPublicInstanceFields(hostAccess, type, fieldMap, inheritedPublicInaccessibleFields);
                }
            } else {
                if (!Modifier.isInterface(type.getModifiers())) {
                    collectPublicInstanceFields(hostAccess, type, fieldMap, true);
                }
            }
        }

        private static void collectPublicInstanceFields(HostClassCache hostAccess, Class<?> type, Map<String, HostFieldDesc> fieldMap, boolean mayHaveInaccessibleFields) {
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
                    if (isClassAccessible(f.getDeclaringClass(), hostAccess)) {
                        if (hostAccess.allowsAccess(f)) {
                            fieldMap.putIfAbsent(f.getName(), HostFieldDesc.unreflect(f));
                        }
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
        Object m = members;
        if (!(m instanceof Members)) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            synchronized (this) {
                m = members;
                if (!(m instanceof Members)) {
                    members = m = new Members((HostClassCache) m, type);
                }
            }
        }
        return (Members) m;
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
