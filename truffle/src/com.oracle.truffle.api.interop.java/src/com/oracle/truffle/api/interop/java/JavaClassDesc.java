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
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;

final class JavaClassDesc {
    private static final ClassValue<JavaClassDesc> CACHED_DESCS = new ClassValue<JavaClassDesc>() {
        @Override
        protected JavaClassDesc computeValue(Class<?> type) {
            return new JavaClassDesc(type);
        }
    };

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

        private static final BiFunction<JavaMethodDesc, JavaMethodDesc, JavaMethodDesc> MERGE = new BiFunction<JavaMethodDesc, JavaMethodDesc, JavaMethodDesc>() {
            public JavaMethodDesc apply(JavaMethodDesc m1, JavaMethodDesc m2) {
                return merge(m1, m2);
            }
        };

        Members(Class<?> type) {
            Map<String, JavaMethodDesc> methodMap = new HashMap<>();
            Map<String, JavaMethodDesc> staticMethodMap = new HashMap<>();
            JavaMethodDesc ctor = null;

            if (Modifier.isPublic(type.getModifiers())) {
                for (Method m : type.getMethods()) {
                    if (m.getDeclaringClass() == Object.class) {
                        continue;
                    }

                    if (!Modifier.isPublic(m.getDeclaringClass().getModifiers())) {
                        // if the declaring class is not public, there should be a public bridge
                        // method available that we can use instead.
                        continue;
                    }
                    SingleMethodDesc method = SingleMethodDesc.unreflect(m);

                    methodMap.merge(m.getName(), method, MERGE);
                    if (Modifier.isStatic(m.getModifiers())) {
                        staticMethodMap.merge(m.getName(), method, MERGE);
                    }
                }
            } else {
                // If the class is not public, look for inherited public methods.
                collectPublicSuperMethods(type, methodMap, staticMethodMap, new HashSet<>());
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
        }

        private static void collectPublicSuperMethods(Class<?> type, Map<String, JavaMethodDesc> methodMap, Map<String, JavaMethodDesc> staticMethodMap, Set<Object> visited) {
            if (type == Object.class) {
                return;
            }
            if (Modifier.isPublic(type.getModifiers())) {
                for (Method m : type.getMethods()) {
                    if (m.getDeclaringClass() == Object.class) {
                        continue;
                    }

                    if (!Modifier.isPublic(m.getDeclaringClass().getModifiers())) {
                        continue;
                    } else if (Modifier.isStatic(m.getModifiers()) && m.getDeclaringClass().isInterface()) {
                        // do not inherit static interface methods
                        continue;
                    }

                    if (visited.add(getSignature(m))) {
                        SingleMethodDesc method = SingleMethodDesc.unreflect(m);

                        methodMap.merge(m.getName(), method, MERGE);
                        if (Modifier.isStatic(m.getModifiers())) {
                            staticMethodMap.merge(m.getName(), method, MERGE);
                        }
                    }
                }
            } else {
                if (type.getSuperclass() != null) {
                    collectPublicSuperMethods(type.getSuperclass(), methodMap, staticMethodMap, visited);
                }
                for (Class<?> intf : type.getInterfaces()) {
                    collectPublicSuperMethods(intf, methodMap, staticMethodMap, visited);
                }
            }
        }

        private static Object getSignature(Method m) {
            class Signature {
                private final String name = m.getName();
                private final Class<?>[] parameterTypes = m.getParameterTypes();

                @Override
                public boolean equals(Object obj) {
                    return obj instanceof Signature && name.equals(((Signature) obj).name) && Arrays.equals(parameterTypes, ((Signature) obj).parameterTypes);
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
            return new Signature();
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
     * Looks up a public method in this class.
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

    @Override
    public String toString() {
        return "JavaClass[" + type.getCanonicalName() + "]";
    }
}
