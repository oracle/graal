/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.graal.api.meta;

import java.lang.reflect.*;
import java.util.*;
import java.util.function.*;
import java.util.stream.*;

import com.oracle.graal.api.meta.MethodIdHolder.MethodIdAllocator;

/**
 * A map whose keys are {@link MethodIdHolder}s. This data structure can be used for mapping
 * identifiers to methods without requiring eager resolution of the latter (e.g., to
 * {@link ResolvedJavaMethod}s) and has retrieval as fast as array indexing. The constraints on
 * using such a map are:
 * <ul>
 * <li>at most one value can be added for any key</li>
 * <li>no more entries can be added after the first {@linkplain #get(MethodIdHolder) retrieval}</li>
 * </ul>
 *
 * @param <V> the type of the values in the map
 */
public class MethodIdMap<V> {

    /**
     * Key for a method.
     */
    public static class MethodKey<T> {
        final boolean isStatic;
        final Class<?> declaringClass;
        final String name;
        final Class<?>[] argumentTypes;
        final T value;
        int id;

        MethodKey(T data, boolean isStatic, Class<?> declaringClass, String name, Class<?>... argumentTypes) {
            assert isStatic || argumentTypes[0] == declaringClass;
            this.value = data;
            this.isStatic = isStatic;
            this.declaringClass = declaringClass;
            this.name = name;
            this.argumentTypes = argumentTypes;
            assert resolveJava() != null;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof MethodKey) {
                MethodKey<?> that = (MethodKey<?>) obj;
                boolean res = this.name.equals(that.name) && this.declaringClass.equals(that.declaringClass) && Arrays.equals(this.argumentTypes, that.argumentTypes);
                assert !res || this.isStatic == that.isStatic;
                return res;
            }
            return false;
        }

        public int getDeclaredParameterCount() {
            return isStatic ? argumentTypes.length : argumentTypes.length - 1;
        }

        @Override
        public int hashCode() {
            // Replay compilation mandates use of stable hash codes
            return declaringClass.getName().hashCode() ^ name.hashCode();
        }

        private MethodIdHolder resolve(MetaAccessProvider metaAccess) {
            return (MethodIdHolder) metaAccess.lookupJavaMethod(resolveJava());
        }

        private Executable resolveJava() {
            try {
                Executable res;
                Class<?>[] parameterTypes = isStatic ? argumentTypes : Arrays.copyOfRange(argumentTypes, 1, argumentTypes.length);
                if (name.equals("<init>")) {
                    res = declaringClass.getDeclaredConstructor(parameterTypes);
                } else {
                    res = declaringClass.getDeclaredMethod(name, parameterTypes);
                }
                assert Modifier.isStatic(res.getModifiers()) == isStatic;
                return res;
            } catch (NoSuchMethodException | SecurityException e) {
                throw new InternalError(e);
            }
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder(declaringClass.getName()).append('.').append(name).append('(');
            for (Class<?> p : argumentTypes) {
                if (sb.charAt(sb.length() - 1) != '(') {
                    sb.append(", ");
                }
                sb.append(p.getSimpleName());
            }
            return sb.append(')').toString();
        }
    }

    private final MetaAccessProvider metaAccess;

    /**
     * Initial list of entries.
     */
    private final List<MethodKey<V>> registrations;

    /**
     * Entry array that is initialized upon first call to {@link #get(MethodIdHolder)}.
     *
     * Note: this must be volatile since double-checked locking is used to initialize it
     */
    private volatile V[] entries;

    /**
     * The minimum {@linkplain MethodIdHolder#getMethodId() id} for a key in this map.
     */
    private int minId = Integer.MAX_VALUE;

    public MethodIdMap(MetaAccessProvider metaAccess) {
        this.metaAccess = metaAccess;
        this.registrations = new ArrayList<>(INITIAL_CAPACITY);
    }

    private static final int INITIAL_CAPACITY = 64;

    /**
     * Adds an entry to this map for a specified method.
     *
     * @param value value to be associated with the specified method
     * @param isStatic specifies if the method is static
     * @param declaringClass the class declaring the method
     * @param name the name of the method
     * @param argumentTypes the argument types of the method. Element 0 of this array must be
     *            {@code declaringClass} iff the method is non-static.
     * @return an object representing the method
     */
    public MethodKey<V> put(V value, boolean isStatic, Class<?> declaringClass, String name, Class<?>... argumentTypes) {
        assert isStatic || argumentTypes[0] == declaringClass;
        MethodKey<V> methodKey = new MethodKey<>(value, isStatic, declaringClass, name, argumentTypes);
        assert entries == null : "registration is closed";
        assert !registrations.contains(methodKey) : "a value is already registered for " + methodKey;
        registrations.add(methodKey);
        return methodKey;
    }

    @SuppressWarnings("unchecked")
    protected V[] allocateEntries(int length) {
        return (V[]) new Object[length];
    }

    /**
     * Determines if a method denoted by a given {@link MethodKey} is in this map.
     */
    public boolean containsKey(MethodKey<V> key) {
        return registrations.contains(key);
    }

    public V get(MethodIdHolder method) {
        if (entries == null) {
            createEntries();
        }

        int id = method.getMethodId();
        int index = id - minId;
        return index >= 0 && index < entries.length ? entries[index] : null;
    }

    public void createEntries() {
        // 'assignIds' synchronizes on a global lock which ensures thread safe
        // allocation of identifiers across all MethodIdHolder objects
        MethodIdHolder.assignIds(new Consumer<MethodIdAllocator>() {

            public void accept(MethodIdAllocator idAllocator) {
                if (entries == null) {
                    if (registrations.isEmpty()) {
                        entries = allocateEntries(0);
                    } else {
                        int max = Integer.MIN_VALUE;
                        for (MethodKey<V> methodKey : registrations) {
                            MethodIdHolder m = methodKey.resolve(metaAccess);
                            int id = idAllocator.assignId(m);
                            if (id < minId) {
                                minId = id;
                            }
                            if (id > max) {
                                max = id;
                            }
                            methodKey.id = id;
                        }

                        int length = (max - minId) + 1;
                        entries = allocateEntries(length);
                        for (MethodKey<V> m : registrations) {
                            int index = m.id - minId;
                            entries[index] = m.value;
                        }
                    }
                }
            }
        });
    }

    @Override
    public String toString() {
        return registrations.stream().map(MethodKey::toString).collect(Collectors.joining(", "));
    }

    public MetaAccessProvider getMetaAccess() {
        return metaAccess;
    }

    public int size() {
        return registrations.size();
    }
}
