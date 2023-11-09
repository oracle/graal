/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.lir;

import jdk.graal.compiler.debug.Assertions;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.StackSlot;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.ValueKind;

/**
 * Represents a value that is a re-interpretation of another value's bits. Such a cast value
 * therefore occupies the same machine location (such as a {@link Register} or {@link StackSlot}) as
 * the underlying value, but has a different kind. The target kind may be smaller, but not larger,
 * than the underlying value's kind.
 *
 * Spilling a cast value means spilling the underlying value. The cast value and the underlying
 * value share the same stack frame offset. Reloading cast values therefore works correctly on
 * little-endian machines: For example, consider spilling a 16-bit cast of the 32-bit value
 * 0x44332211. The full value will be spilled, and the reloaded value will be 0x2211, which is what
 * is intended. A big-endian target would need to adjust the frame offset.
 */
public class CastValue extends AllocatableValue {

    private final AllocatableValue underlyingValue;

    /**
     * Constructs a cast from the {@code underlyingValue} to {@code kind}. If
     * {@code underlyingValue} is itself a cast, all such cast layers are stripped away, and the new
     * cast will refer directly to the innermost non-cast value.
     */
    public CastValue(ValueKind<?> kind, AllocatableValue underlyingValue) {
        super(kind);
        AllocatableValue inner = underlyingValue;
        while (inner instanceof CastValue) {
            assert kind.getPlatformKind().getSizeInBytes() <= inner.getPlatformKind().getSizeInBytes() : Assertions.errorMessageContext("kind", kind, "inner", inner);
            inner = ((CastValue) inner).underlyingValue;
        }
        assert kind.getPlatformKind().getSizeInBytes() <= inner.getPlatformKind().getSizeInBytes() : "can't cast " + inner + " " + inner.getPlatformKind() + " to larger kind " +
                        kind.getPlatformKind();
        this.underlyingValue = inner;
    }

    public AllocatableValue underlyingValue() {
        return underlyingValue;
    }

    @Override
    public String toString() {
        return "reinterpret: " + underlyingValue + " as: " + this.getValueKind();
    }

    @Override
    public int hashCode() {
        return 47 * super.hashCode() + underlyingValue.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof CastValue) {
            CastValue other = (CastValue) obj;
            return super.equals(other) && underlyingValue.equals(other.underlyingValue);
        }
        return false;
    }
}
