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
package com.oracle.max.graal.hotspot;

import com.sun.cri.ci.*;
import com.sun.cri.ri.*;

/**
 * Implementation of RiType for unresolved HotSpot classes.
 */
public class HotSpotTypeUnresolved extends HotSpotType {

    public final String simpleName;
    public final int dimensions;

    /**
     * Creates a new unresolved type for a specified type descriptor.
     */
    public HotSpotTypeUnresolved(Compiler compiler, String name) {
        super(compiler);
        assert name.length() > 0 : "name cannot be empty";

        int dimensions = 0;
        // Decode name if necessary.
        if (name.charAt(name.length() - 1) == ';') {
            int startIndex = 0;
            while (name.charAt(startIndex) == '[') {
                startIndex++;
                dimensions++;
            }
            assert name.charAt(startIndex) == 'L';
            this.simpleName = name.substring(startIndex + 1, name.length() - 1);
            this.name = name;
        } else {
            this.simpleName = name;
            this.name = getFullName(name, dimensions);
        }

        this.dimensions = dimensions;
    }

    public HotSpotTypeUnresolved(Compiler compiler, String name, int dimensions) {
        super(compiler);
        assert dimensions >= 0;
        this.simpleName = name;
        this.dimensions = dimensions;
        this.name = getFullName(name, dimensions);
    }

    private String getFullName(String name, int dimensions) {
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
        return new HotSpotTypeUnresolved(compiler, simpleName, dimensions - 1);
    }

    @Override
    public RiType arrayOf() {
        return new HotSpotTypeUnresolved(compiler, simpleName, dimensions + 1);
    }

    @Override
    public CiKind kind(boolean architecture) {
        return CiKind.Object;
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
    public CiKind getRepresentationKind(RiType.Representation r) {
        return CiKind.Object;
    }
}
