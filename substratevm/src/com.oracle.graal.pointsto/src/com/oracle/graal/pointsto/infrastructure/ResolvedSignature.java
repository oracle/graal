/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.pointsto.infrastructure;

import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.Signature;

/**
 * A straightforward implementation of {@link Signature} where all parameter types and the return
 * type are {@link ResolvedJavaType}. The generic type allows to further improve type safety of
 * usages.
 *
 * In a regular {@link Signature}, looking up a
 * {@link Signature#getParameterType(int, ResolvedJavaType) parameter type} or the
 * {@link Signature#getReturnType(ResolvedJavaType) return type} requires to provide the "accessing
 * class" for type resolution. Since in this implementation all types are always pre-resolved, these
 * parameters are ignored. In addition, methods are offered that allow parameter type and return
 * type lookup without providing the accessing class.
 */
public final class ResolvedSignature<T extends ResolvedJavaType> implements Signature {

    public static <T extends ResolvedJavaType> ResolvedSignature<T> fromArray(T[] parameterTypes, T returnType) {
        return new ResolvedSignature<>(List.of(parameterTypes), returnType);
    }

    public static <T extends ResolvedJavaType> ResolvedSignature<T> fromList(List<T> parameterTypes, T returnType) {
        return new ResolvedSignature<>(List.copyOf(parameterTypes), returnType);
    }

    public static ResolvedSignature<ResolvedJavaType> fromKinds(JavaKind[] parameterKinds, JavaKind returnKind, MetaAccessProvider metaAccess) {
        return new ResolvedSignature<>(
                        Arrays.stream(parameterKinds).map(kind -> resolveType(kind, metaAccess)).collect(Collectors.toUnmodifiableList()),
                        resolveType(returnKind, metaAccess));
    }

    private static ResolvedJavaType resolveType(JavaKind kind, MetaAccessProvider metaAccess) {
        return metaAccess.lookupJavaType(kind.isObject() ? Object.class : kind.toJavaClass());
    }

    public static ResolvedSignature<ResolvedJavaType> fromMethodType(MethodType mt, MetaAccessProvider metaAccess) {
        return new ResolvedSignature<>(
                        Arrays.stream(mt.parameterArray()).map(metaAccess::lookupJavaType).collect(Collectors.toUnmodifiableList()),
                        metaAccess.lookupJavaType(mt.returnType()));
    }

    private final List<T> parameterTypes;
    private final T returnType;

    private ResolvedSignature(List<T> parameterTypes, T returnType) {
        /*
         * All factory methods must pass in an immutable list for the parameter types, so that the
         * list can be safely passes out again as the parameter list. Unfortunately, there is no way
         * to assert that here.
         */
        this.parameterTypes = parameterTypes;
        this.returnType = returnType;
    }

    @Override
    public int getParameterCount(boolean withReceiver) {
        return parameterTypes.size() + (withReceiver ? 1 : 0);
    }

    /*
     * Use the version without the accessingClass when calling methods directly on
     * ResolvedSignature.
     */
    @Deprecated
    @Override
    public T getParameterType(int index, ResolvedJavaType accessingClass) {
        return getParameterType(index);
    }

    public T getParameterType(int index) {
        return parameterTypes.get(index);
    }

    /*
     * Use the version without the accessingClass when calling methods directly on
     * ResolvedSignature.
     */
    @Deprecated
    @Override
    public T getReturnType(ResolvedJavaType accessingClass) {
        return getReturnType();
    }

    public T getReturnType() {
        return returnType;
    }

    /**
     * A type-safe version of {@link Signature#toParameterTypes}.
     *
     * @return An unmodifiable list with all parameter types, including the receiverType if it is
     *         non-null.
     */
    public List<T> toParameterList(T receiverType) {
        if (receiverType == null) {
            /* parameterTypes is always an unmodifiable list, so we can return it directly. */
            return parameterTypes;
        }
        List<T> withReceiver = new ArrayList<>(parameterTypes.size() + 1);
        withReceiver.add(receiverType);
        withReceiver.addAll(parameterTypes);
        return Collections.unmodifiableList(withReceiver);
    }

    @Override
    public boolean equals(Object obj) {
        return this == obj || (obj instanceof ResolvedSignature<?> other && Objects.equals(parameterTypes, other.parameterTypes) && Objects.equals(returnType, other.returnType));
    }

    @Override
    public int hashCode() {
        return Objects.hash(parameterTypes, returnType);
    }

    @Override
    public String toString() {
        return toMethodDescriptor();
    }
}
