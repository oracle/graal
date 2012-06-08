/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.hotspot.ri;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.hotspot.*;

/**
 * Implementation of RiType for unresolved HotSpot classes.
 */
public class HotSpotTypeUnresolved extends HotSpotType {

    private static final long serialVersionUID = -2320936267633521314L;
    public final String simpleName;
    public final int dimensions;

    /**
     * Creates a new unresolved type for a specified type descriptor.
     */
    public HotSpotTypeUnresolved(String name) {
        assert name.length() > 0 : "name cannot be empty";

        int dims = 0;
        int startIndex = 0;
        while (name.charAt(startIndex) == '[') {
            startIndex++;
            dims++;
        }

        // Decode name if necessary.
        if (name.charAt(name.length() - 1) == ';') {
            assert name.charAt(startIndex) == 'L';
            this.simpleName = name.substring(startIndex + 1, name.length() - 1);
            this.name = name;
        } else {
            this.simpleName = name;
            this.name = getFullName(name, dims);
        }

        this.dimensions = dims;
    }

    public HotSpotTypeUnresolved(String name, int dimensions) {
        assert dimensions >= 0;
        this.simpleName = name;
        this.dimensions = dimensions;
        this.name = getFullName(name, dimensions);
    }

    private static String getFullName(String name, int dimensions) {
        StringBuilder str = new StringBuilder(name.length() + dimensions + 2);
        for (int i = 0; i < dimensions; i++) {
            str.append('[');
        }
        str.append('L').append(name).append(';');
        return str.toString();
    }

    @Override
    public RiType componentType() {
        assert dimensions > 0 : "no array class" + name();
        return new HotSpotTypeUnresolved(simpleName, dimensions - 1);
    }

    @Override
    public RiType arrayOf() {
        return new HotSpotTypeUnresolved(simpleName, dimensions + 1);
    }

    @Override
    public RiKind kind() {
        return RiKind.Object;
    }

    @Override
    public int hashCode() {
        return simpleName.hashCode();
    }

    @Override
    public boolean equals(Object o) {
        return o == this;
    }

    @Override
    public String toString() {
        return "HotSpotType<" + simpleName + ", unresolved>";
    }

    @Override
    public RiKind getRepresentationKind(RiType.Representation r) {
        return RiKind.Object;
    }

    @Override
    public RiResolvedType resolve(RiResolvedType accessingClass) {
        return (RiResolvedType) HotSpotGraalRuntime.getInstance().lookupType(name, (HotSpotTypeResolved) accessingClass, true);
    }

    @Override
    public HotSpotKlassOop klassOop() {
        throw GraalInternalError.shouldNotReachHere("HotSpotTypeUnresolved.klassOop");
    }
}
