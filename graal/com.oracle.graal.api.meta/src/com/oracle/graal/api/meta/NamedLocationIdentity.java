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

import com.oracle.graal.api.meta.Kind.FormatWithToString;

/**
 * A {@link LocationIdentity} with a name.
 */
public final class NamedLocationIdentity extends LocationIdentity implements FormatWithToString {

    /**
     * Map for asserting all {@link NamedLocationIdentity} instances have a unique name.
     */
    static class DB {
        private static final HashMap<String, NamedLocationIdentity> map = new HashMap<>();

        static boolean checkUnique(NamedLocationIdentity identity) {
            NamedLocationIdentity oldValue = map.put(identity.name, identity);
            if (oldValue != null) {
                throw new AssertionError("identity " + identity + " already exists");
            }
            return true;
        }
    }

    protected final String name;

    private NamedLocationIdentity(String name, boolean immutable) {
        super(immutable);
        this.name = name;
    }

    /**
     * Creates a named unique location identity for read and write operations against mutable
     * memory.
     *
     * @param name the name of the new location identity
     */
    public static NamedLocationIdentity mutable(String name) {
        return create(name, false);
    }

    /**
     * Creates a named unique location identity for read operations against immutable memory.
     * Immutable memory will never have a visible write in the graph, which is more restictive than
     * Java final.
     *
     * @param name the name of the new location identity
     */
    public static NamedLocationIdentity immutable(String name) {
        return create(name, true);
    }

    /**
     * Creates a named unique location identity for read and write operations.
     *
     * @param name the name of the new location identity
     * @param immutable true if the location is immutable
     */
    private static NamedLocationIdentity create(String name, boolean immutable) {
        NamedLocationIdentity id = new NamedLocationIdentity(name, immutable);
        assert DB.checkUnique(id);
        return id;
    }

    @Override
    public String toString() {
        return name + (isImmutable() ? ":immutable" : ":mutable");
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
            result.put(kind, NamedLocationIdentity.mutable("Array: " + kind.getJavaName()));
        }
        return result;
    }
}
