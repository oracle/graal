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
package com.oracle.graal.hotspot.meta;

import static com.oracle.graal.hotspot.HotSpotGraalRuntime.*;

import com.oracle.graal.api.meta.*;

/**
 * Implementation of {@link JavaType} for unresolved HotSpot classes.
 */
public class HotSpotUnresolvedJavaType extends HotSpotJavaType {

    private static final long serialVersionUID = -2320936267633521314L;
    public final String simpleName;
    public final int dimensions;

    public HotSpotUnresolvedJavaType(String name, String simpleName, int dimensions) {
        super(name);
        assert dimensions >= 0;
        this.simpleName = simpleName;
        this.dimensions = dimensions;
    }

    public static String getFullName(String name, int dimensions) {
        StringBuilder str = new StringBuilder(name.length() + dimensions + 2);
        for (int i = 0; i < dimensions; i++) {
            str.append('[');
        }
        str.append('L').append(name).append(';');
        return str.toString();
    }

    @Override
    public JavaType getComponentType() {
        assert dimensions > 0 : "no array class" + getName();
        String name = getFullName(getName(), dimensions - 1);
        return new HotSpotUnresolvedJavaType(name, simpleName, dimensions - 1);
    }

    @Override
    public JavaType getArrayClass() {
        String name = getFullName(getName(), dimensions + 1);
        return new HotSpotUnresolvedJavaType(name, simpleName, dimensions + 1);
    }

    @Override
    public Kind getKind() {
        return Kind.Object;
    }

    @Override
    public int hashCode() {
        return simpleName.hashCode();
    }

    @Override
    public boolean equals(Object o) {
        HotSpotUnresolvedJavaType that = (HotSpotUnresolvedJavaType) o;
        return this.simpleName.equals(that.simpleName) && this.dimensions == that.dimensions;
    }

    @Override
    public String toString() {
        return "HotSpotType<" + simpleName + ", unresolved>";
    }

    @Override
    public ResolvedJavaType resolve(ResolvedJavaType accessingClass) {
        return (ResolvedJavaType) runtime().lookupType(getName(), (HotSpotResolvedObjectType) accessingClass, true);
    }
}
