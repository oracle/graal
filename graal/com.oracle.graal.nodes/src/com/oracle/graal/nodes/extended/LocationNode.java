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
package com.oracle.graal.nodes.extended;

import java.util.*;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.graph.Node.ValueNumberable;
import com.oracle.graal.nodes.calc.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.type.*;

/**
 * A location for a memory access in terms of the kind of value accessed and how to access it. All
 * locations have the form [base + location], where base is a node and location is defined by
 * subclasses of the {@link LocationNode}.
 */
public abstract class LocationNode extends FloatingNode implements LIRLowerable, ValueNumberable {

    /**
     * Marker interface for location identities. Apart from the special values {@link #ANY_LOCATION}
     * and {@link #FINAL_LOCATION}, a different location identity of two memory accesses guarantees
     * that the two accesses do not interfere.
     */
    public interface LocationIdentity {
    }

    /**
     * Denotes any location. A write to such a location kills all values in a memory map during an
     * analysis of memory accesses in a graph. A read from this location cannot be moved or
     * coalesced with other reads because its interaction with other reads is not known.
     */
    public static final LocationIdentity ANY_LOCATION = createLocation("ANY_LOCATION");

    /**
     * Denotes the location of a value that is guaranteed to be final.
     */
    public static final LocationIdentity FINAL_LOCATION = createLocation("FINAL_LOCATION");

    /**
     * Creates a new unique location identity for read and write operations.
     * 
     * @param name the name of the new location identity, for debugging purposes
     * @return the new location identity
     */
    public static LocationIdentity createLocation(final String name) {
        return new LocationIdentity() {

            @Override
            public String toString() {
                return name;
            }
        };
    }

    /**
     * Returns the location identity for an array of the given element kind. Array accesses of the
     * same kind must have the same location identity unless an alias analysis guarantees that two
     * distinct arrays are accessed.
     */
    public static LocationIdentity getArrayLocation(Kind elementKind) {
        return ARRAY_LOCATIONS.get(elementKind);
    }

    private static final EnumMap<Kind, LocationIdentity> ARRAY_LOCATIONS = initArrayLocations();

    private static EnumMap<Kind, LocationIdentity> initArrayLocations() {
        EnumMap<Kind, LocationIdentity> result = new EnumMap<>(Kind.class);
        for (Kind kind : Kind.values()) {
            result.put(kind, createLocation("Array: " + kind.getJavaName()));
        }
        return result;
    }

    /**
     * Marker interface for locations in snippets.
     */
    public interface Location {
    }

    protected LocationNode(Stamp stamp) {
        super(stamp);
    }

    /**
     * Returns the kind of the accessed memory value.
     */
    public abstract Kind getValueKind();

    /**
     * Returns the identity of the accessed memory location.
     */
    public abstract LocationIdentity getLocationIdentity();

    @Override
    public final void generate(LIRGeneratorTool generator) {
        // nothing to do...
    }

    public abstract Value generateAddress(LIRGeneratorTool gen, Value base);
}
