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
package com.oracle.truffle.espresso.shared.meta;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import com.oracle.truffle.espresso.classfile.descriptors.Name;
import com.oracle.truffle.espresso.classfile.descriptors.Signature;
import com.oracle.truffle.espresso.classfile.descriptors.Symbol;
import com.oracle.truffle.espresso.classfile.descriptors.Type;
import com.oracle.truffle.espresso.classfile.descriptors.TypeSymbols;

/**
 * This class manages {@link java.lang.invoke.MethodHandle} & {@link java.lang.invoke.VarHandle}
 * polymorphic methods. It creates and records instantiations of signature polymorphic methods every
 * time a new signature is seen. This is the only place that keeps track of these, instantiated
 * signature polymorphic methods are not part of a class' declared methods.
 */
public final class MethodHandleIntrinsics<C extends TypeAccess<C, M, F>, M extends MethodAccess<C, M, F>, F extends FieldAccess<C, M, F>> {
    private final ConcurrentHashMap<MethodKey, M> intrinsics = new ConcurrentHashMap<>();

    public <R extends RuntimeAccess<C, M, F>> M findIntrinsic(M m, Symbol<Signature> signature, R runtimeAccess) {
        assert m.isDeclaredSignaturePolymorphic();
        SignaturePolymorphicIntrinsic iid = SignaturePolymorphicIntrinsic.getId(m);
        Symbol<Signature> sig = signature;
        if (iid.isStaticSignaturePolymorphic()) {
            sig = runtimeAccess.getSymbolPool().getSignatures().toBasic(signature, true);
        }
        MethodKey key = new MethodKey(m, sig);
        M method = intrinsics.get(key);
        if (method != null) {
            return method;
        }
        method = m.createSignaturePolymorphicIntrinsic(key.signature);
        M previous = intrinsics.putIfAbsent(key, method);
        if (previous != null) {
            return previous;
        }
        return method;
    }

    private static final class MethodKey {
        private final Symbol<Type> clazz;
        private final Symbol<Name> methodName;
        private final Symbol<Signature> signature;
        private final boolean isStatic;
        private final int hash;

        MethodKey(MethodAccess<?, ?, ?> m, Symbol<Signature> signature) {
            this(m.getDeclaringClass().getSymbolicType(), m.getSymbolicName(), signature, m.isStatic());
        }

        MethodKey(Symbol<Type> clazz, Symbol<Name> methodName, Symbol<Signature> signature, boolean isStatic) {
            assert clazz != null && methodName != null && signature != null;
            this.clazz = clazz;
            this.methodName = methodName;
            this.signature = signature;
            this.isStatic = isStatic;
            this.hash = Objects.hash(clazz, methodName, signature, isStatic);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null || getClass() != obj.getClass()) {
                return false;
            }
            MethodKey other = (MethodKey) obj;
            return clazz == other.clazz &&
                            methodName == other.methodName &&
                            signature == other.signature &&
                            isStatic == other.isStatic;
        }

        @Override
        public int hashCode() {
            return hash;
        }

        @Override
        public String toString() {
            return (isStatic ? "static " : "") + TypeSymbols.binaryName(clazz) + "#" + methodName + signature;
        }
    }
}
