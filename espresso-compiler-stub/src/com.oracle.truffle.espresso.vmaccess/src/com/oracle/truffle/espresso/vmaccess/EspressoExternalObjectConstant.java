/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.vmaccess;

import org.graalvm.polyglot.Value;

import com.oracle.truffle.espresso.jvmci.meta.EspressoResolvedObjectType;

import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;

final class EspressoExternalObjectConstant implements JavaConstant {
    private final EspressoExternalVMAccess access;
    private final org.graalvm.polyglot.Value value;

    EspressoExternalObjectConstant(EspressoExternalVMAccess access, org.graalvm.polyglot.Value value) {
        assert !value.isNull();
        this.access = access;
        this.value = value;
    }

    @Override
    public JavaKind getJavaKind() {
        return JavaKind.Object;
    }

    @Override
    public boolean isNull() {
        return false;
    }

    @Override
    public boolean isDefaultForKind() {
        return false;
    }

    @Override
    public Object asBoxedPrimitive() {
        throw new IllegalArgumentException();
    }

    @Override
    public int asInt() {
        throw new IllegalArgumentException();
    }

    @Override
    public boolean asBoolean() {
        throw new IllegalArgumentException();
    }

    @Override
    public long asLong() {
        throw new IllegalArgumentException();
    }

    @Override
    public float asFloat() {
        throw new IllegalArgumentException();
    }

    @Override
    public double asDouble() {
        throw new IllegalArgumentException();
    }

    @Override
    public String toValueString() {
        return "Instance<" + getType().toJavaName() + ">";
    }

    public EspressoResolvedObjectType getType() {
        Value cls = value.getMetaObject().getMember("class");
        return (EspressoResolvedObjectType) EspressoExternalConstantReflectionProvider.classAsType(cls, access);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        EspressoExternalObjectConstant that = (EspressoExternalObjectConstant) o;
        return value.equals(that.value);
    }

    @Override
    public int hashCode() {
        return guestHashCode();
    }

    int guestHashCode() {
        return value.hashCode();
    }

    org.graalvm.polyglot.Value getValue() {
        return value;
    }
}
