/*
 * Copyright (c) 2011, 2014, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.graal.nodes.spi.*;

/**
 * A stamp is the basis for a type system over the nodes in a graph.
 */
public abstract class Stamp {

    protected Stamp() {
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
     * Gets a Java {@link Kind} that can be used to store a value of this stamp on the Java bytecode
     * stack. Returns {@link Kind#Illegal} if a value of this stamp can not be stored on the
     * bytecode stack.
     */
    public abstract Kind getStackKind();

    /**
     * Gets a platform dependent {@link PlatformKind} that can be used to store a value of this
     * stamp.
     */
    public abstract PlatformKind getPlatformKind(LIRTypeTool tool);

    /**
     * Returns the union of this stamp and the given stamp. Typically used to create stamps for
     * {@link ValuePhiNode}s.
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
     * Returns a stamp of the same kind, but allowing the full value range of the kind.
     */
    public abstract Stamp unrestricted();

    /**
     * Returns an illegal stamp that has the same kind, but no valid values.
     */
    public Stamp illegal() {
        return StampFactory.illegal(getStackKind());
    }

    /**
     * Test whether two stamps have the same base type.
     */
    public abstract boolean isCompatible(Stamp other);

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
