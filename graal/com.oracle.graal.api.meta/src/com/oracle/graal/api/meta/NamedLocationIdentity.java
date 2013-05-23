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
 * A {@link LocationIdentity} with a name.
 */
public class NamedLocationIdentity implements LocationIdentity {

    protected final String name;

    /**
     * Creates a named unique location identity for read and write operations.
     * 
     * @param name the name of the new location identity
     */
    public NamedLocationIdentity(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return name;
    }

    /**
     * Returns the named location identity for an array of the given element kind. Array accesses of
     * the same kind must have the same location identity unless an alias analysis guarantees that
     * two distinct arrays are accessed.
     */
    public static LocationIdentity getArrayLocation(Kind elementKind) {
        return ARRAY_LOCATIONS.get(elementKind);
    }

    private static final EnumMap<Kind, LocationIdentity> ARRAY_LOCATIONS = initArrayLocations();

    private static EnumMap<Kind, LocationIdentity> initArrayLocations() {
        EnumMap<Kind, LocationIdentity> result = new EnumMap<>(Kind.class);
        for (Kind kind : Kind.values()) {
            result.put(kind, new NamedLocationIdentity("Array: " + kind.getJavaName()));
        }
        return result;
    }
}
