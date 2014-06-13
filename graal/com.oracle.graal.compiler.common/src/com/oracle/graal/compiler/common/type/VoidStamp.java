/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.compiler.common.type;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.common.*;
import com.oracle.graal.compiler.common.spi.*;

/**
 * Singleton stamp representing the value of type {@code void}.
 */
public final class VoidStamp extends Stamp {

    private VoidStamp() {
    }

    @Override
    public Stamp unrestricted() {
        return this;
    }

    @Override
    public Kind getStackKind() {
        return Kind.Void;
    }

    @Override
    public LIRKind getLIRKind(LIRKindTool tool) {
        throw GraalInternalError.shouldNotReachHere("void stamp has no value");
    }

    @Override
    public ResolvedJavaType javaType(MetaAccessProvider metaAccess) {
        return metaAccess.lookupJavaType(Void.TYPE);
    }

    @Override
    public String toString() {
        return "void";
    }

    @Override
    public boolean alwaysDistinct(Stamp other) {
        return this != other;
    }

    @Override
    public Stamp meet(Stamp other) {
        if (other instanceof IllegalStamp) {
            return other.join(this);
        }
        if (this == other) {
            return this;
        }
        return StampFactory.illegal(Kind.Illegal);
    }

    @Override
    public Stamp join(Stamp other) {
        if (other instanceof IllegalStamp) {
            return other.join(this);
        }
        if (this == other) {
            return this;
        }
        return StampFactory.illegal(Kind.Illegal);
    }

    @Override
    public boolean isCompatible(Stamp stamp) {
        return this == stamp;
    }

    @Override
    public Stamp illegal() {
        // there is no illegal void stamp
        return this;
    }

    @Override
    public boolean isLegal() {
        return true;
    }

    @Override
    public Stamp constant(Constant c, MetaAccessProvider meta) {
        throw GraalInternalError.shouldNotReachHere("void stamp has no value");
    }

    private static VoidStamp instance = new VoidStamp();

    static VoidStamp getInstance() {
        return instance;
    }
}
