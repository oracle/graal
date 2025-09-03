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

package com.oracle.svm.core.graal.nodes;

import java.util.Objects;

import com.oracle.svm.core.hub.DynamicHub;

import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.VMConstant;

/**
 * An {@code TLABObjectHeaderConstant} is a constant representing the content of the header of a
 * newly created object. It is computed from the address of the {@link DynamicHub} and needs
 * patching after compilation and relocations at run-time.
 */
public final class TLABObjectHeaderConstant implements JavaConstant, VMConstant {
    private final JavaConstant hub;
    private final int size;

    public TLABObjectHeaderConstant(JavaConstant hub, int size) {
        assert size == Integer.BYTES || size == Long.BYTES : size;
        this.hub = hub;
        this.size = size;
    }

    public JavaConstant hub() {
        return hub;
    }

    @Override
    public JavaKind getJavaKind() {
        return size == Integer.BYTES ? JavaKind.Int : JavaKind.Long;
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
        return null;
    }

    @Override
    public int asInt() {
        assert getJavaKind() == JavaKind.Int;
        return (int) asLong();
    }

    @Override
    public boolean asBoolean() {
        assert false;
        return false;
    }

    @Override
    public long asLong() {
        // A dummy value that may help us spot the cases when patching is missed. This value is used
        // during code emission so we try to make this value not fit into a smaller type. This is
        // safer just in case the consumer forgets checking that a constant is a PrimitiveConstant
        // when trying to fold a constant into an instruction as an immediate operand.
        return getJavaKind() == JavaKind.Int ? 0xFFFFFFFFDEADDEADL : 0xDEADDEADDEADDEADL;
    }

    @Override
    public float asFloat() {
        assert false;
        return 0;
    }

    @Override
    public double asDouble() {
        assert false;
        return 0;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof TLABObjectHeaderConstant c && hub.equals(c.hub) && size == c.size;
    }

    @Override
    public int hashCode() {
        return Objects.hash(hub, size);
    }

    @Override
    public String toValueString() {
        return "TLAB object header of " + hub().toValueString();
    }

    @Override
    public String toString() {
        return "TLAB object header of " + hub().toString();
    }
}
