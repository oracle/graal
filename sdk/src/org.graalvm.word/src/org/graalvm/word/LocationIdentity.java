/*
 * Copyright (c) 2011, 2019, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.word;

// JaCoCo Exclude

/**
 * Marker interface for location identities. A different location identity of two memory accesses
 * guarantees that the two accesses do not interfere.
 * <p>
 * Clients of {@link LocationIdentity} must use {@link #equals(Object)}, not {@code ==}, when
 * comparing two {@link LocationIdentity} values for equality. Likewise, they must not use
 * {@link java.util.IdentityHashMap}s with {@link LocationIdentity} values as keys.
 *
 * @since 19.0
 */
public abstract class LocationIdentity {

    private static final class AnyLocationIdentity extends LocationIdentity {
        @Override
        public boolean isImmutable() {
            return false;
        }

        @Override
        public String toString() {
            return "ANY_LOCATION";
        }
    }

    private static final class InitLocationIdentity extends LocationIdentity {
        @Override
        public boolean isImmutable() {
            return true;
        }

        @Override
        public String toString() {
            return "INIT_LOCATION";
        }
    }

    /**
     * Creates a new location identity. Subclasses are responsible to provide proper implementations
     * of {@link #equals} and {@link #hashCode}.
     *
     * @since 19.0
     */
    protected LocationIdentity() {
    }

    /**
     * Indicates that the given location is the union of all possible mutable locations. A write to
     * such a location kill all reads from mutable locations and a read from this location is killed
     * by any write (except for initialization writes).
     *
     * @since 19.0
     */
    public static final LocationIdentity ANY_LOCATION = new AnyLocationIdentity();

    /**
     * Location only allowed to be used for writes. Indicates that a completely new memory location
     * is written. Kills no read. The previous value at the given location must be either
     * uninitialized or null. Writes to this location do not need a GC pre-barrier.
     *
     * @since 19.0
     */
    public static final LocationIdentity INIT_LOCATION = new InitLocationIdentity();

    /**
     * Indicates that the given location is the union of all possible mutable locations. A write to
     * such a location kill all reads from mutable locations and a read from this location is killed
     * by any write (except for initialization writes).
     *
     * @since 19.0
     */
    public static LocationIdentity any() {
        return ANY_LOCATION;
    }

    /**
     * Location only allowed to be used for writes. Indicates that a completely new memory location
     * is written. Kills no read. The previous value at the given location must be either
     * uninitialized or null. Writes to this location do not need a GC pre-barrier.
     *
     * @since 19.0
     */
    public static LocationIdentity init() {
        return INIT_LOCATION;
    }

    /**
     * Denotes a location is unchanging in all cases. Not that this is different than the Java
     * notion of final which only requires definite assignment.
     *
     * @since 19.0
     */
    public abstract boolean isImmutable();

    /**
     * The inversion of {@link #isImmutable}.
     *
     * @since 19.0
     */
    public final boolean isMutable() {
        return !isImmutable();
    }

    /**
     * Returns true if this location identity is {@link #any}.
     *
     * @since 19.0
     */
    public final boolean isAny() {
        return this == ANY_LOCATION;
    }

    /**
     * Returns true if this location identity is {@link #init}.
     *
     * @since 19.0
     */
    public final boolean isInit() {
        return this == INIT_LOCATION;
    }

    /**
     * Returns true if this location identity is not {@link #any}.
     *
     * @since 19.0
     */
    public final boolean isSingle() {
        return this != ANY_LOCATION;
    }

    /**
     * Returns true if the memory slice denoted by this location identity may overlap with the
     * provided other location identity.
     *
     * @since 19.0
     */
    public final boolean overlaps(LocationIdentity other) {
        return isAny() || other.isAny() || this.equals(other);
    }
}
