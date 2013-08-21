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
package com.oracle.graal.nodes.type;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.nodes.*;

/**
 * A stamp is the basis for a type system over the nodes in a graph.
 */
public abstract class Stamp {

    private final Kind kind;

    protected Stamp(Kind kind) {
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }

    /**
     * Returns the type of the stamp, guaranteed to be non-null. In some cases, this requires the
     * lookup of class meta data, therefore the {@link MetaAccessProvider} is mandatory.
     */
    public abstract ResolvedJavaType javaType(MetaAccessProvider metaAccess);

    public boolean alwaysDistinct(Stamp other) {
        return join(other) instanceof IllegalStamp;
    }

    /**
     * Returns the union of this stamp and the given stamp. Typically used to create stamps for
     * {@link PhiNode}s.
     * 
     * @param other The stamp that will enlarge this stamp.
     * @return The union of this stamp and the given stamp.
     */
    public abstract Stamp meet(Stamp other);

    /**
     * Returns the intersection of this stamp and the given stamp.
     * 
     * @param other The stamp that will tighten this stamp.
     * @return The intersection of this stamp and the given stamp.
     */
    public abstract Stamp join(Stamp other);

    /**
     * If this stamp represents a single value, the methods returns this single value. It returns
     * null otherwise.
     * 
     * @return the constant corresponding to the single value of this stamp and null if this stamp
     *         can represent less or more than one value.
     */
    public Constant asConstant() {
        return null;
    }
}
