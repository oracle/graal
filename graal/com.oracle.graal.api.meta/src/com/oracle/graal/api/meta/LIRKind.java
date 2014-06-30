/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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

/**
 * Represents the type of values in the LIR. It is composed of a {@link PlatformKind} that gives the
 * low level representation of the value, and a {@link #referenceMask} that describes the location
 * of object references in the value.
 */
public final class LIRKind {

    /**
     * The non-type. This uses {@link #derivedReference}, so it can never be part of an oop map.
     */
    public static final LIRKind Illegal = derivedReference(Kind.Illegal);

    private final PlatformKind platformKind;
    private final int referenceMask;

    private static final int DERIVED_REFERENCE = -1;

    private LIRKind(PlatformKind platformKind, int referenceMask) {
        this.platformKind = platformKind;
        this.referenceMask = referenceMask;
    }

    /**
     * Create a {@link LIRKind} of type {@code platformKind} that contains a primitive value.
     */
    public static LIRKind value(PlatformKind platformKind) {
        assert platformKind != Kind.Object : "Object should always be used as reference type";
        return new LIRKind(platformKind, 0);
    }

    /**
     * Create a {@link LIRKind} of type {@code platformKind} that contains a single tracked oop
     * reference.
     */
    public static LIRKind reference(PlatformKind platformKind) {
        int length = platformKind.getVectorLength();
        assert 0 < length && length < 32 : "vector of " + length + " references not supported";
        return new LIRKind(platformKind, (1 << length) - 1);
    }

    /**
     * Create a {@link LIRKind} of type {@code platformKind} that contains a value that is derived
     * from a reference. Values of this {@link LIRKind} can not be live at safepoints.
     */
    public static LIRKind derivedReference(PlatformKind platformKind) {
        return new LIRKind(platformKind, DERIVED_REFERENCE);
    }

    /**
     * Create a new {@link LIRKind} with the same reference information and a new
     * {@linkplain #getPlatformKind platform kind}. If the new kind is a longer vector than this,
     * the new elements are marked as untracked values.
     */
    public LIRKind changeType(PlatformKind newPlatformKind) {
        if (isDerivedReference()) {
            return derivedReference(newPlatformKind);
        } else if (referenceMask == 0) {
            // value type
            return new LIRKind(newPlatformKind, 0);
        } else {
            // reference type
            int newLength = Math.min(32, newPlatformKind.getVectorLength());
            int newReferenceMask = referenceMask & (0xFFFFFFFF >>> (32 - newLength));
            assert newReferenceMask != DERIVED_REFERENCE;
            return new LIRKind(newPlatformKind, newReferenceMask);
        }
    }

    /**
     * Create a new {@link LIRKind} with a new {@linkplain #getPlatformKind platform kind}. If the
     * new kind is longer than this, the reference positions are repeated to fill the vector.
     */
    public LIRKind repeat(PlatformKind newPlatformKind) {
        if (isDerivedReference()) {
            return derivedReference(newPlatformKind);
        } else if (referenceMask == 0) {
            // value type
            return new LIRKind(newPlatformKind, 0);
        } else {
            // reference type
            int oldLength = platformKind.getVectorLength();
            int newLength = newPlatformKind.getVectorLength();
            assert oldLength <= newLength && newLength < 32 && (newLength % oldLength) == 0;

            // repeat reference mask to fill new kind
            int newReferenceMask = 0;
            for (int i = 0; i < newLength; i += platformKind.getVectorLength()) {
                newReferenceMask |= referenceMask << i;
            }

            assert newReferenceMask != DERIVED_REFERENCE;
            return new LIRKind(newPlatformKind, newReferenceMask);
        }
    }

    /**
     * Create a new {@link LIRKind} with the same type, but marked as containing a derivedReference.
     */
    public LIRKind makeDerivedReference() {
        return new LIRKind(platformKind, DERIVED_REFERENCE);
    }

    /**
     * Get the low level type that is used in code generation.
     */
    public PlatformKind getPlatformKind() {
        return platformKind;
    }

    /**
     * Check whether this value is derived from a reference. If this returns {@code true}, this
     * value must not be live at safepoints.
     */
    public boolean isDerivedReference() {
        return referenceMask == DERIVED_REFERENCE;
    }

    /**
     * Check whether the {@code idx}th part of this value is a reference that must be tracked at
     * safepoints.
     *
     * @param idx The index into the vector if this is a vector kind. Must be 0 if this is a scalar
     *            kind.
     */
    public boolean isReference(int idx) {
        assert 0 <= idx && idx < platformKind.getVectorLength() : "invalid index " + idx + " in " + this;
        return !isDerivedReference() && (referenceMask & 1 << idx) != 0;
    }

    /**
     * Check whether this kind is a value type that doesn't need to be tracked at safepoints.
     */
    public boolean isValue() {
        return referenceMask == 0;
    }

    @Override
    public String toString() {
        if (isValue()) {
            return platformKind.name();
        } else if (isDerivedReference()) {
            return platformKind.name() + "[*]";
        } else {
            StringBuilder ret = new StringBuilder();
            ret.append(platformKind.name());
            ret.append('[');
            for (int i = 0; i < platformKind.getVectorLength(); i++) {
                if (isReference(i)) {
                    ret.append('.');
                } else {
                    ret.append(' ');
                }
            }
            ret.append(']');
            return ret.toString();
        }
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((platformKind == null) ? 0 : platformKind.hashCode());
        result = prime * result + referenceMask;
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof LIRKind)) {
            return false;
        }

        LIRKind other = (LIRKind) obj;
        return platformKind == other.platformKind && referenceMask == other.referenceMask;
    }
}