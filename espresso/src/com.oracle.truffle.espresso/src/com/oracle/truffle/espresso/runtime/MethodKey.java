/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.runtime;

import java.util.Objects;

import com.oracle.truffle.espresso.classfile.descriptors.Name;
import com.oracle.truffle.espresso.classfile.descriptors.Signature;
import com.oracle.truffle.espresso.classfile.descriptors.Symbol;
import com.oracle.truffle.espresso.classfile.descriptors.Type;
import com.oracle.truffle.espresso.classfile.descriptors.TypeSymbols;
import com.oracle.truffle.espresso.impl.Method;

public final class MethodKey {
    private final Symbol<Type> clazz;
    private final Symbol<Name> methodName;
    private final Symbol<Signature> signature;
    private final boolean isStatic;
    private final int hash;

    public MethodKey(Method m) {
        this(m, m.getRawSignature());
    }

    public MethodKey(Method m, Symbol<Signature> signature) {
        this(m.getDeclaringKlass().getType(), m.getName(), signature, m.isStatic());
    }

    public MethodKey(Symbol<Type> clazz, Symbol<Name> methodName, Symbol<Signature> signature, boolean isStatic) {
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

    public Symbol<Type> getHolderType() {
        return clazz;
    }

    public Symbol<Name> getName() {
        return methodName;
    }

    public Symbol<Signature> getSignature() {
        return signature;
    }

    public boolean isStatic() {
        return isStatic;
    }
}
