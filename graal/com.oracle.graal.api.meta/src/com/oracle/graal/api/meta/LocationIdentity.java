/*
 * Copyright (c) 2011, 2012, Oracle and/or its affiliates. All rights reserved.
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

import java.util.*;

/**
 * Marker interface for location identities. Apart from the special values {@link #ANY_LOCATION} and
 * {@link #FINAL_LOCATION}, a different location identity of two memory accesses guarantees that the
 * two accesses do not interfere.
 *
 * Clients of {@link LocationIdentity} must use {@link #equals(Object)}, not {@code ==}, when
 * comparing two {@link LocationIdentity} values for equality. Likewise, they must not use
 * {@link IdentityHashMap}s with {@link LocationIdentity} values as keys.
 */
public interface LocationIdentity extends TrustedInterface {

    /**
     * Denotes any location. A write to such a location kills all values in a memory map during an
     * analysis of memory accesses. A read from this location cannot be moved or coalesced with
     * other reads because its interaction with other reads is not known.
     */
    LocationIdentity ANY_LOCATION = NamedLocationIdentity.mutable("ANY_LOCATION");

    /**
     * Denotes the location of a value that is guaranteed to be unchanging.
     */
    LocationIdentity FINAL_LOCATION = NamedLocationIdentity.immutable("FINAL_LOCATION");

    /**
     * Denotes the location of the length field of a Java array.
     */
    LocationIdentity ARRAY_LENGTH_LOCATION = NamedLocationIdentity.immutable("[].length");

    /**
     * Denotes a location is unchanging in all cases. Not that this is different than the Java
     * notion of final which only requires definite assignment.
     */
    default boolean isImmutable() {
        return false;
    }

    default boolean isMutable() {
        return !isImmutable();
    }
}
